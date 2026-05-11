package org.mockserver.templates.engine.helpers;

import org.mockserver.time.TimeService;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class DateTemplateHelper {

    public String format(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
            .withZone(ZoneOffset.UTC)
            .format(TimeService.now());
    }

    public String plusSeconds(long seconds) {
        return DateTimeFormatter.ISO_INSTANT.format(TimeService.now().plus(seconds, ChronoUnit.SECONDS));
    }

    public String plusMinutes(long minutes) {
        return DateTimeFormatter.ISO_INSTANT.format(TimeService.now().plus(minutes, ChronoUnit.MINUTES));
    }

    public String plusHours(long hours) {
        return DateTimeFormatter.ISO_INSTANT.format(TimeService.now().plus(hours, ChronoUnit.HOURS));
    }

    public String plusDays(long days) {
        return DateTimeFormatter.ISO_INSTANT.format(TimeService.now().plus(days, ChronoUnit.DAYS));
    }

    public String minusSeconds(long seconds) {
        return DateTimeFormatter.ISO_INSTANT.format(TimeService.now().minus(seconds, ChronoUnit.SECONDS));
    }

    public String minusMinutes(long minutes) {
        return DateTimeFormatter.ISO_INSTANT.format(TimeService.now().minus(minutes, ChronoUnit.MINUTES));
    }

    public String minusHours(long hours) {
        return DateTimeFormatter.ISO_INSTANT.format(TimeService.now().minus(hours, ChronoUnit.HOURS));
    }

    public String minusDays(long days) {
        return DateTimeFormatter.ISO_INSTANT.format(TimeService.now().minus(days, ChronoUnit.DAYS));
    }

    public long epochSeconds() {
        return TimeService.now().getEpochSecond();
    }

    public long epochMillis() {
        return TimeService.now().toEpochMilli();
    }

    public String epochSecondsPlus(long seconds) {
        return String.valueOf(TimeService.now().plus(seconds, ChronoUnit.SECONDS).getEpochSecond());
    }

    public String epochSecondsMinus(long seconds) {
        return String.valueOf(TimeService.now().minus(seconds, ChronoUnit.SECONDS).getEpochSecond());
    }

    @Override
    public String toString() {
        return DateTimeFormatter.ISO_INSTANT.format(TimeService.now());
    }
}
