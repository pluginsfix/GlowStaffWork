package pluginsfix.glowstaffwork.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaffProfileTest {

    @Test
    void createNewInitializesCorrectly() {
        UUID uuid = UUID.randomUUID();
        StaffProfile profile = StaffProfile.createNew(uuid, "Steve");

        assertThat(profile.uuid()).isEqualTo(uuid);
        assertThat(profile.playerName()).isEqualTo("Steve");
        assertThat(profile.totalTimeSeconds()).isZero();
        assertThat(profile.todayTimeSeconds()).isZero();
        assertThat(profile.totalSessions()).isZero();
    }

    @Test
    void withUpdatedSessionIncrementsCorrectly() {
        UUID uuid = UUID.randomUUID();
        StaffProfile profile = StaffProfile.createNew(uuid, "Alex");

        long sessionEnd = System.currentTimeMillis();
        StaffProfile updated = profile.withUpdatedSession(300, sessionEnd);

        assertThat(updated.totalTimeSeconds()).isEqualTo(300);
        assertThat(updated.todayTimeSeconds()).isEqualTo(300);
        assertThat(updated.totalSessions()).isEqualTo(1);
        assertThat(updated.lastSeenEpoch()).isEqualTo(sessionEnd);
    }

    @Test
    void effectiveTodayTimeResetsOnDayChange() {
        UUID uuid = UUID.randomUUID();
        long previousDay = LocalDate.now().toEpochDay() - 1;
        StaffProfile profile = new StaffProfile(uuid, "Alex", 1000, 500, previousDay, 2, 0);

        long currentDay = LocalDate.now().toEpochDay();
        assertThat(profile.effectiveTodayTime(currentDay)).isZero();
    }
}
