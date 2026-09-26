package pluginsfix.glowstaffwork.service;

import org.bukkit.entity.Player;
import pluginsfix.glowstaffwork.config.PluginConfig;
import pluginsfix.glowstaffwork.domain.StaffProfile;
import pluginsfix.glowstaffwork.domain.StaffSession;
import pluginsfix.glowstaffwork.domain.TimeFormatter;
import pluginsfix.glowstaffwork.hook.LiteBansHook;
import pluginsfix.glowstaffwork.platform.PlatformScheduler;
import pluginsfix.glowstaffwork.storage.StorageRepository;
import pluginsfix.glowstaffwork.text.Messages;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class StaffWorkService {
    private final StorageRepository repository;
    private final PluginConfig config;
    private final Messages messages;
    private final PlatformScheduler scheduler;
    private final LiteBansHook liteBansHook;
    private final Logger logger;

    private final Map<UUID, StaffSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, StaffProfile> profileCache = new ConcurrentHashMap<>();

    public StaffWorkService(
            StorageRepository repository,
            PluginConfig config,
            Messages messages,
            PlatformScheduler scheduler,
            LiteBansHook liteBansHook,
            Logger logger
    ) {
        this.repository = repository;
        this.config = config;
        this.messages = messages;
        this.scheduler = scheduler;
        this.liteBansHook = liteBansHook;
        this.logger = logger;
    }

    public boolean isOnDuty(UUID uuid) {
        return this.activeSessions.containsKey(uuid);
    }

    public Optional<StaffSession> getActiveSession(UUID uuid) {
        return Optional.ofNullable(this.activeSessions.get(uuid));
    }

    public int getActiveStaffCount() {
        return this.activeSessions.size();
    }

    public CompletableFuture<Boolean> startDuty(Player player) {
        UUID uuid = player.getUniqueId();
        if (isOnDuty(uuid)) {
            this.messages.send(player, "already-on");
            return CompletableFuture.completedFuture(false);
        }

        long now = System.currentTimeMillis();
        StaffSession session = new StaffSession(uuid, player.getName(), now);
        this.activeSessions.put(uuid, session);

        return loadOrCreateProfile(uuid, player.getName()).thenApply(profile -> {
            this.profileCache.put(uuid, profile.withPlayerName(player.getName()));

            Map<String, String> placeholders = Map.of(
                    "player", player.getName(),
                    "uuid", uuid.toString()
            );

            for (String commandTemplate : this.config.commandsOn()) {
                String cmd = replacePlaceholders(commandTemplate, placeholders);
                this.scheduler.executeConsoleCommand(cmd);
            }

            this.messages.send(player, "work-on", placeholders);

            if (this.config.broadcastEnabled()) {
                this.messages.broadcast("broadcast-on", this.config.broadcastPermission(), placeholders);
            }

            return true;
        });
    }

    public CompletableFuture<Boolean> stopDuty(Player player, boolean isQuit) {
        UUID uuid = player.getUniqueId();
        StaffSession session = this.activeSessions.remove(uuid);
        if (session == null) {
            if (!isQuit) {
                this.messages.send(player, "already-off");
            }
            return CompletableFuture.completedFuture(false);
        }

        long now = System.currentTimeMillis();
        long durationSeconds = session.durationSeconds(now);

        return loadOrCreateProfile(uuid, player.getName()).thenCompose(profile -> {
            StaffProfile updated = profile.withPlayerName(player.getName()).withUpdatedSession(durationSeconds, now);
            this.profileCache.put(uuid, updated);

            CompletableFuture<Void> saveProfileFuture = this.repository.saveProfile(updated);
            CompletableFuture<Void> saveSessionFuture = this.repository.recordSession(
                    uuid,
                    player.getName(),
                    session.startTimeEpochMillis(),
                    now,
                    durationSeconds
            );

            return CompletableFuture.allOf(saveProfileFuture, saveSessionFuture).thenApply(ignored -> {
                long currentDayEpoch = LocalDate.now().toEpochDay();
                long todayTimeSeconds = updated.effectiveTodayTime(currentDayEpoch);

                Map<String, String> placeholders = Map.of(
                        "player", player.getName(),
                        "uuid", uuid.toString(),
                        "session_time", TimeFormatter.formatDuration(durationSeconds),
                        "today_time", TimeFormatter.formatDuration(todayTimeSeconds),
                        "total_time", TimeFormatter.formatDuration(updated.totalTimeSeconds()),
                        "duration", String.valueOf(durationSeconds)
                );

                for (String commandTemplate : this.config.commandsOff()) {
                    String cmd = replacePlaceholders(commandTemplate, placeholders);
                    this.scheduler.executeConsoleCommand(cmd);
                }

                if (!isQuit) {
                    this.messages.send(player, "work-off", placeholders);
                    if (this.config.broadcastEnabled()) {
                        this.messages.broadcast("broadcast-off", this.config.broadcastPermission(), placeholders);
                    }
                }

                return true;
            });
        });
    }

    public CompletableFuture<Optional<StaffStatsView>> getStats(UUID uuid, String fallbackName) {
        return loadOrCreateProfile(uuid, fallbackName).thenCompose(profile -> {
            boolean active = isOnDuty(uuid);
            long now = System.currentTimeMillis();
            long currentDayEpoch = LocalDate.now().toEpochDay();

            long sessionDuration = 0L;
            if (active) {
                StaffSession session = this.activeSessions.get(uuid);
                if (session != null) {
                    sessionDuration = session.durationSeconds(now);
                }
            }
            final long currentSessionSec = sessionDuration;
            final long todaySec = profile.effectiveTodayTime(currentDayEpoch) + currentSessionSec;
            final long totalSec = profile.totalTimeSeconds() + currentSessionSec;

            return this.liteBansHook.getPunishments(uuid, profile.playerName()).thenApply(punishments -> Optional.of(new StaffStatsView(
                    profile.uuid(),
                    profile.playerName(),
                    active,
                    currentSessionSec,
                    todaySec,
                    totalSec,
                    profile.totalSessions(),
                    profile.lastSeenEpoch(),
                    punishments.bans(),
                    punishments.mutes(),
                    punishments.kicks(),
                    punishments.warns(),
                    punishments.total()
            )));
        });
    }

    public CompletableFuture<Optional<StaffStatsView>> getStatsByName(String playerName) {
        for (StaffSession session : this.activeSessions.values()) {
            if (session.playerName().equalsIgnoreCase(playerName)) {
                return getStats(session.uuid(), session.playerName());
            }
        }

        return this.repository.loadProfileByName(playerName).thenCompose(opt -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            StaffProfile profile = opt.get();
            this.profileCache.put(profile.uuid(), profile);
            return getStats(profile.uuid(), profile.playerName());
        });
    }

    private CompletableFuture<StaffProfile> loadOrCreateProfile(UUID uuid, String name) {
        StaffProfile cached = this.profileCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return this.repository.loadProfile(uuid).thenApply(opt -> {
            StaffProfile profile = opt.orElseGet(() -> StaffProfile.createNew(uuid, name));
            this.profileCache.put(uuid, profile);
            return profile;
        });
    }

    public void cleanupCache(UUID uuid) {
        this.profileCache.remove(uuid);
    }

    public void flushAllSync() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, StaffSession> entry : this.activeSessions.entrySet()) {
            UUID uuid = entry.getKey();
            StaffSession session = entry.getValue();
            long duration = session.durationSeconds(now);

            StaffProfile profile = this.profileCache.computeIfAbsent(
                    uuid,
                    id -> this.repository.loadProfile(id).join().orElseGet(() -> StaffProfile.createNew(id, session.playerName()))
            );

            StaffProfile updated = profile.withPlayerName(session.playerName()).withUpdatedSession(duration, now);
            this.repository.saveProfileSync(updated);
        }
        this.activeSessions.clear();
        this.profileCache.clear();
    }

    public void startAutoSave() {
        long intervalTicks = this.config.saveIntervalMinutes() * 60L * 20L;
        this.scheduler.runAsyncTimer(() -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, StaffSession> entry : this.activeSessions.entrySet()) {
                UUID uuid = entry.getKey();
                StaffSession session = entry.getValue();
                StaffProfile profile = this.profileCache.get(uuid);
                if (profile != null) {
                    this.repository.saveProfile(profile.withPlayerName(session.playerName()));
                }
            }
        }, intervalTicks, intervalTicks);
    }

    private static String replacePlaceholders(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public record StaffStatsView(
            UUID uuid,
            String playerName,
            boolean isOnDuty,
            long currentSessionSeconds,
            long todaySeconds,
            long totalSeconds,
            int totalSessions,
            long lastSeenEpochMillis,
            long bans,
            long mutes,
            long kicks,
            long warns,
            long totalPunishments
    ) {
    }
}
