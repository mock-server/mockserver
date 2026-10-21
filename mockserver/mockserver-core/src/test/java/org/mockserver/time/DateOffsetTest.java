package org.mockserver.time;

import org.junit.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

public class DateOffsetTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    @Test
    public void shouldGiveAnExactDurationOnlyWhenTheCalendarCannotMatter() {
        assertThat(DateOffset.parse("-2 hours 30 minutes").exactDuration(LONDON), is(Duration.ofMinutes(-90)));
        assertThat(DateOffset.parse("PT90M").exactDuration(LONDON), is(Duration.ofMinutes(90)));
        assertThat(DateOffset.parse("2 weeks 1 days").exactDuration(ZoneOffset.UTC), is(Duration.ofDays(15)));
        assertThat(DateOffset.parse("P3D").exactDuration(ZoneOffset.ofHours(2)), is(Duration.ofDays(3)));
        // a day is not always 24 hours in a zone with daylight saving
        assertThat(DateOffset.parse("1 days").exactDuration(LONDON), is(nullValue()));
        assertThat(DateOffset.parse("P1D").exactDuration(LONDON), is(nullValue()));
        // months and years never have a fixed length
        assertThat(DateOffset.parse("1 months").exactDuration(ZoneOffset.UTC), is(nullValue()));
        assertThat(DateOffset.parse("P1Y").exactDuration(ZoneOffset.UTC), is(nullValue()));
        assertThat(DateOffset.parse("9223372036854775807 hours").exactDuration(ZoneOffset.UTC), is(nullValue()));
    }

    @Test
    public void shouldAddPartsInOrderInTheDateTimesZone() {
        ZonedDateTime beforeClocksChange = ZonedDateTime.parse("2026-10-24T13:00:00+01:00[Europe/London]");
        assertThat(DateOffset.parse("+1 days").addTo(beforeClocksChange).toInstant().toString(), is("2026-10-25T13:00:00Z"));
        assertThat(DateOffset.parse("24 hours").addTo(beforeClocksChange).toInstant().toString(), is("2026-10-25T12:00:00Z"));
        assertThat(DateOffset.parse("1 months, -1 seconds").addTo(ZonedDateTime.parse("2026-01-31T00:00:00Z")).toString(), is("2026-02-27T23:59:59Z"));
    }

    @Test
    public void shouldRejectAnInvalidOffset() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> DateOffset.parse(" ")).getMessage(), is("no date offset given"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> DateOffset.parse("tomorrow")).getMessage(), containsString("invalid date offset \"tomorrow\"; use an amount and unit"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> DateOffset.parse("3 fortnights")).getMessage(), containsString("unknown unit \"fortnights\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> DateOffset.parse("P1X")).getMessage(), containsString("invalid date offset \"P1X\":"));
    }
}
