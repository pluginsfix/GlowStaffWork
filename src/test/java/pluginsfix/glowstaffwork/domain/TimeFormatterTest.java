package pluginsfix.glowstaffwork.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeFormatterTest {

    @Test
    void formatDurationZeroOrNegativeReturnsZeroSeconds() {
        assertThat(TimeFormatter.formatDuration(0)).isEqualTo("0 с");
        assertThat(TimeFormatter.formatDuration(-10)).isEqualTo("0 с");
    }

    @Test
    void formatDurationSecondsOnly() {
        assertThat(TimeFormatter.formatDuration(45)).isEqualTo("45 с");
    }

    @Test
    void formatDurationMinutesAndSeconds() {
        assertThat(TimeFormatter.formatDuration(125)).isEqualTo("2 мин 5 с");
    }

    @Test
    void formatDurationHoursMinutesSeconds() {
        assertThat(TimeFormatter.formatDuration(3665)).isEqualTo("1 ч 1 мин 5 с");
    }

    @Test
    void formatDurationDaysHoursMinutesSeconds() {
        assertThat(TimeFormatter.formatDuration(90065)).isEqualTo("1 д 1 ч 1 мин 5 с");
    }

    @Test
    void formatDateZeroReturnsPlaceholder() {
        assertThat(TimeFormatter.formatDate(0)).isEqualTo("—");
    }
}
