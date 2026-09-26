package pluginsfix.glowstaffwork.domain;

import java.time.LocalDate;
import java.util.UUID;

public record StaffProfile(
        UUID uuid,
        String playerName,
        long totalTimeSeconds,
        long todayTimeSeconds,
        long lastDayEpoch,
        int totalSessions,
        long lastSeenEpoch
) {
    public static StaffProfile createNew(UUID uuid, String playerName) {
        long currentDay = LocalDate.now().toEpochDay();
        return new StaffProfile(uuid, playerName, 0L, 0L, currentDay, 0, System.currentTimeMillis());
    }

    public long effectiveTodayTime(long currentDayEpoch) {
        if (this.lastDayEpoch != currentDayEpoch) {
            return 0L;
        }
        return this.todayTimeSeconds;
    }

    public StaffProfile withUpdatedSession(long durationSeconds, long sessionEndEpochMillis) {
        long currentDay = LocalDate.now().toEpochDay();
        long newTodayTime = (this.lastDayEpoch == currentDay ? this.todayTimeSeconds : 0L) + durationSeconds;
        long newTotalTime = this.totalTimeSeconds + durationSeconds;
        int newSessions = this.totalSessions + 1;

        return new StaffProfile(
                this.uuid,
                this.playerName,
                newTotalTime,
                newTodayTime,
                currentDay,
                newSessions,
                sessionEndEpochMillis
        );
    }

    public StaffProfile withPlayerName(String newPlayerName) {
        return new StaffProfile(
                this.uuid,
                newPlayerName,
                this.totalTimeSeconds,
                this.todayTimeSeconds,
                this.lastDayEpoch,
                this.totalSessions,
                this.lastSeenEpoch
        );
    }
}
