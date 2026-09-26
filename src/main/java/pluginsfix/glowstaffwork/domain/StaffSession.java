package pluginsfix.glowstaffwork.domain;

import java.util.UUID;

public record StaffSession(
        UUID uuid,
        String playerName,
        long startTimeEpochMillis
) {
    public long durationSeconds(long currentEpochMillis) {
        return Math.max(0L, (currentEpochMillis - this.startTimeEpochMillis) / 1000L);
    }
}
