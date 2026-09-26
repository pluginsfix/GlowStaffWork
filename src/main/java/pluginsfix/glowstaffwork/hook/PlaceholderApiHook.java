package pluginsfix.glowstaffwork.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pluginsfix.glowstaffwork.domain.TimeFormatter;
import pluginsfix.glowstaffwork.service.StaffWorkService;
import pluginsfix.glowstaffwork.text.Messages;

import java.util.Collections;

public final class PlaceholderApiHook extends PlaceholderExpansion {
    private final Plugin plugin;
    private final StaffWorkService service;
    private final Messages messages;

    public PlaceholderApiHook(Plugin plugin, StaffWorkService service, Messages messages) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "glowstaffwork";
    }

    @Override
    public @NotNull String getAuthor() {
        return "pluginsfix";
    }

    @Override
    public @NotNull String getVersion() {
        return this.plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String lower = params.toLowerCase();

        if (lower.equals("active_count")) {
            return String.valueOf(this.service.getActiveStaffCount());
        }

        if (player == null) {
            return "";
        }

        boolean onDuty = this.service.isOnDuty(player.getUniqueId());

        switch (lower) {
            case "status" -> {
                return onDuty ? "on" : "off";
            }
            case "status_formatted" -> {
                String key = onDuty ? "status-on" : "status-off";
                return this.messages.getFormattedString(key, Collections.emptyMap());
            }
            case "session_time" -> {
                if (!onDuty) {
                    return this.messages.getFormattedString("status-none", Collections.emptyMap());
                }
                long now = System.currentTimeMillis();
                return this.service.getActiveSession(player.getUniqueId())
                        .map(session -> TimeFormatter.formatDuration(session.durationSeconds(now)))
                        .orElseGet(() -> this.messages.getFormattedString("status-none", Collections.emptyMap()));
            }
            case "today_time" -> {
                return this.service.getStats(player.getUniqueId(), player.getName() != null ? player.getName() : "")
                        .join()
                        .map(stats -> TimeFormatter.formatDuration(stats.todaySeconds()))
                        .orElse("0 с");
            }
            case "total_time" -> {
                return this.service.getStats(player.getUniqueId(), player.getName() != null ? player.getName() : "")
                        .join()
                        .map(stats -> TimeFormatter.formatDuration(stats.totalSeconds()))
                        .orElse("0 с");
            }
            case "sessions" -> {
                return this.service.getStats(player.getUniqueId(), player.getName() != null ? player.getName() : "")
                        .join()
                        .map(stats -> String.valueOf(stats.totalSessions()))
                        .orElse("0");
            }
            case "last_seen" -> {
                return this.service.getStats(player.getUniqueId(), player.getName() != null ? player.getName() : "")
                        .join()
                        .map(stats -> TimeFormatter.formatDate(stats.lastSeenEpochMillis()))
                        .orElse("—");
            }
            default -> {
                return null;
            }
        }
    }
}
