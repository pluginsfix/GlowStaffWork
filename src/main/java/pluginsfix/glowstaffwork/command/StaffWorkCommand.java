package pluginsfix.glowstaffwork.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pluginsfix.glowstaffwork.domain.TimeFormatter;
import pluginsfix.glowstaffwork.service.StaffWorkService;
import pluginsfix.glowstaffwork.text.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class StaffWorkCommand implements CommandExecutor, TabCompleter {
    private final StaffWorkService service;
    private final Messages messages;
    private final Runnable reloadAction;

    public StaffWorkCommand(StaffWorkService service, Messages messages, Runnable reloadAction) {
        this.service = service;
        this.messages = messages;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("glowstaffwork.use")) {
            this.messages.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            this.messages.send(sender, "help");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "on" -> handleOn(sender);
            case "off" -> handleOff(sender);
            case "info" -> handleInfo(sender, args);
            case "reload" -> handleReload(sender);
            default -> this.messages.send(sender, "unknown-subcommand");
        }

        return true;
    }

    private void handleOn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.messages.send(sender, "player-only");
            return;
        }
        this.service.startDuty(player);
    }

    private void handleOff(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.messages.send(sender, "player-only");
            return;
        }
        this.service.stopDuty(player, false);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                this.messages.send(sender, "player-only");
                return;
            }
            sendPlayerStats(sender, player.getName(), this.service.getStats(player.getUniqueId(), player.getName()));
            return;
        }

        if (!sender.hasPermission("glowstaffwork.info.others")) {
            this.messages.send(sender, "no-permission");
            return;
        }

        String targetName = args[1];
        sendPlayerStats(sender, targetName, this.service.getStatsByName(targetName));
    }

    private void sendPlayerStats(
            CommandSender sender,
            String targetName,
            java.util.concurrent.CompletableFuture<java.util.Optional<StaffWorkService.StaffStatsView>> future
    ) {
        future.thenAccept(opt -> {
            if (opt.isEmpty()) {
                this.messages.send(sender, "target-not-found", Map.of("player", targetName));
                return;
            }

            StaffWorkService.StaffStatsView stats = opt.get();
            String statusKey = stats.isOnDuty() ? "status-on" : "status-off";
            String statusFormatted = this.messages.getFormattedString(statusKey, Collections.emptyMap());
            if (statusFormatted.isEmpty()) {
                statusFormatted = this.messages.getFormattedString("status-none", Collections.emptyMap());
            }

            String sessionTime = stats.isOnDuty()
                    ? TimeFormatter.formatDuration(stats.currentSessionSeconds())
                    : this.messages.getFormattedString("status-none", Collections.emptyMap());

            Map<String, String> placeholders = Map.of(
                    "player", stats.playerName(),
                    "status_formatted", statusFormatted,
                    "session_time", sessionTime,
                    "today_time", TimeFormatter.formatDuration(stats.todaySeconds()),
                    "total_time", TimeFormatter.formatDuration(stats.totalSeconds()),
                    "sessions_count", String.valueOf(stats.totalSessions()),
                    "last_seen", TimeFormatter.formatDate(stats.lastSeenEpochMillis())
            );

            this.messages.send(sender, "info", placeholders);
        });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("glowstaffwork.admin")) {
            this.messages.send(sender, "no-permission");
            return;
        }

        this.reloadAction.run();
        this.messages.send(sender, "reloaded");
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("glowstaffwork.use")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if ("on".startsWith(args[0].toLowerCase())) list.add("on");
            if ("off".startsWith(args[0].toLowerCase())) list.add("off");
            if ("info".startsWith(args[0].toLowerCase())) list.add("info");
            if (sender.hasPermission("glowstaffwork.admin") && "reload".startsWith(args[0].toLowerCase())) {
                list.add("reload");
            }
            if ("help".startsWith(args[0].toLowerCase())) list.add("help");
            return list;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("info") && sender.hasPermission("glowstaffwork.info.others")) {
            List<String> players = new ArrayList<>();
            String prefix = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(prefix)) {
                    players.add(player.getName());
                }
            }
            return players;
        }

        return Collections.emptyList();
    }
}
