package pluginsfix.glowstaffwork.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Messages {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final Plugin plugin;
    private final File messagesFile;
    private final Map<String, List<String>> messageListCache = new HashMap<>();
    private final Map<String, String> stringCache = new HashMap<>();

    public Messages(Plugin plugin) {
        this.plugin = plugin;
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        reload();
    }

    public void reload() {
        if (!this.messagesFile.exists()) {
            this.plugin.saveResource("messages.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(this.messagesFile);
        this.messageListCache.clear();
        this.stringCache.clear();

        for (String key : config.getKeys(true)) {
            if (config.isList(key)) {
                this.messageListCache.put(key, config.getStringList(key));
            } else if (config.isString(key)) {
                this.stringCache.put(key, config.getString(key));
            }
        }
    }

    public String getRawString(String key) {
        return this.stringCache.getOrDefault(key, "");
    }

    public String getFormattedString(String key, Map<String, String> placeholders) {
        String raw = this.stringCache.getOrDefault(key, "");
        if (raw.isEmpty()) {
            return "";
        }
        return colorize(replacePlaceholders(raw, placeholders));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        List<String> lines = this.messageListCache.get(key);
        if (lines == null || lines.isEmpty()) {
            String single = this.stringCache.get(key);
            if (single != null && !single.isEmpty()) {
                executeLine(sender, single, placeholders);
            }
            return;
        }

        for (String line : lines) {
            executeLine(sender, line, placeholders);
        }
    }

    public void broadcast(String key, String permission, Map<String, String> placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permission == null || permission.isEmpty() || player.hasPermission(permission)) {
                send(player, key, placeholders);
            }
        }
        send(Bukkit.getConsoleSender(), key, placeholders);
    }

    private void executeLine(CommandSender sender, String rawLine, Map<String, String> placeholders) {
        String line = replacePlaceholders(rawLine, placeholders);

        if (line.startsWith("[message] ")) {
            String text = line.substring(10);
            Component component = parseComponent(text);
            sender.sendMessage(component);
        } else if (line.startsWith("[actionbar] ")) {
            if (sender instanceof Player player) {
                String text = line.substring(12);
                Component component = parseComponent(text);
                player.sendActionBar(component);
            }
        } else if (line.startsWith("[title] ")) {
            if (sender instanceof Player player) {
                String payload = line.substring(8);
                parseAndShowTitle(player, payload);
            }
        } else if (line.startsWith("[sound] ")) {
            if (sender instanceof Player player) {
                String payload = line.substring(8).trim();
                playSound(player, payload);
            }
        } else {
            Component component = parseComponent(line);
            sender.sendMessage(component);
        }
    }

    private void parseAndShowTitle(Player player, String payload) {
        String[] parts = payload.split(";");
        String titleText = parts.length > 0 ? parts[0].trim() : "";
        String subtitleText = parts.length > 1 ? parts[1].trim() : "";
        int fadeInTicks = parts.length > 2 ? parseIntOrDefault(parts[2].trim(), 10) : 10;
        int stayTicks = parts.length > 3 ? parseIntOrDefault(parts[3].trim(), 40) : 40;
        int fadeOutTicks = parts.length > 4 ? parseIntOrDefault(parts[4].trim(), 10) : 10;

        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeInTicks * 50L),
                Duration.ofMillis(stayTicks * 50L),
                Duration.ofMillis(fadeOutTicks * 50L)
        );

        Title title = Title.title(parseComponent(titleText), parseComponent(subtitleText), times);
        player.showTitle(title);
    }

    private void playSound(Player player, String payload) {
        String[] parts = payload.split(";");
        String soundName = parts[0].trim();
        float volume = parts.length > 1 ? parseFloatOrDefault(parts[1].trim(), 1.0f) : 1.0f;
        float pitch = parts.length > 2 ? parseFloatOrDefault(parts[2].trim(), 1.0f) : 1.0f;

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public static Component parseComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        String colored = colorize(text);
        return SERIALIZER.deserialize(colored).decoration(TextDecoration.ITALIC, false);
    }

    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        String withoutItalic = message.replace("&o", "").replace("§o", "");

        Matcher matcher = HEX_PATTERN.matcher(withoutItalic);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char ch : hex.toCharArray()) {
                replacement.append('§').append(ch);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);

        return buffer.toString().replace('&', '§');
    }

    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (text == null || placeholders == null || placeholders.isEmpty()) {
            return text == null ? "" : text;
        }

        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private int parseIntOrDefault(String text, int defaultValue) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private float parseFloatOrDefault(String text, float defaultValue) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
