package pluginsfix.glowstaffwork.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record PluginConfig(
        boolean autoOffOnQuit,
        int saveIntervalMinutes,
        List<String> commandsOn,
        List<String> commandsOff,
        boolean broadcastEnabled,
        String broadcastPermission,
        String databaseType,
        String databaseFile,
        int databasePoolSize,
        int databaseConnectionTimeoutMs
) {
    public static PluginConfig fromYaml(FileConfiguration yaml) {
        boolean autoOffOnQuit = yaml.getBoolean("auto-off-on-quit", true);
        int saveIntervalMinutes = Math.max(1, yaml.getInt("save-interval-minutes", 5));
        List<String> commandsOn = yaml.getStringList("commands.on");
        List<String> commandsOff = yaml.getStringList("commands.off");
        boolean broadcastEnabled = yaml.getBoolean("broadcast.enabled", true);
        String broadcastPermission = yaml.getString("broadcast.permission", "glowstaffwork.staff");
        String databaseType = yaml.getString("database.type", "sqlite");
        String databaseFile = yaml.getString("database.file", "staffwork.db");
        int databasePoolSize = Math.max(1, yaml.getInt("database.pool-size", 4));
        int databaseConnectionTimeoutMs = Math.max(1000, yaml.getInt("database.connection-timeout-ms", 5000));

        return new PluginConfig(
                autoOffOnQuit,
                saveIntervalMinutes,
                commandsOn,
                commandsOff,
                broadcastEnabled,
                broadcastPermission,
                databaseType,
                databaseFile,
                databasePoolSize,
                databaseConnectionTimeoutMs
        );
    }
}
