package pluginsfix.glowstaffwork.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeFormatter {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private TimeFormatter() {
    }

    public static String formatDuration(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0 с";
        }

        long days = totalSeconds / 86400;
        long remaining = totalSeconds % 86400;
        long hours = remaining / 3600;
        remaining %= 3600;
        long minutes = remaining / 60;
        long seconds = remaining % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(" д ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append(" ч ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            sb.append(minutes).append(" мин ");
        }
        sb.append(seconds).append(" с");

        return sb.toString().trim();
    }

    public static String formatDate(long epochMillis) {
        if (epochMillis <= 0) {
            return "—";
        }
        return DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }
}
