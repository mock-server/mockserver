package org.mockserver.time;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A named truncation of a date-time, applied in the date-time's own time zone. Shared by the
 * {@code dates} template helper and the date-time request matchers so both accept the same names.
 */
public enum DateTruncation {
    START_OF_SECOND,
    START_OF_MINUTE,
    START_OF_HOUR,
    START_OF_DAY,
    FIRST_DAY_OF_MONTH,
    LAST_DAY_OF_MONTH,
    FIRST_DAY_OF_NEXT_MONTH,
    FIRST_DAY_OF_YEAR,
    LAST_DAY_OF_YEAR,
    FIRST_DAY_OF_NEXT_YEAR;

    public static final String NAMES = "startOfSecond, startOfMinute, startOfHour, startOfDay, firstDayOfMonth, lastDayOfMonth, firstDayOfNextMonth, firstDayOfYear, lastDayOfYear or firstDayOfNextYear";
    private static final Pattern SEPARATORS = Pattern.compile("[\\s_-]");

    /**
     * Looks a truncation up by name: case, spaces, hyphens and underscores are ignored, so
     * {@code "first day of month"} works, as do WireMock's {@code "first minute of hour"} and
     * {@code "first hour of day"}.
     *
     * @return the truncation, or {@code null} when the name is not one
     */
    public static DateTruncation named(String name) {
        if (name == null) {
            return null;
        }
        switch (SEPARATORS.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("")) {
            case "startofsecond":
                return START_OF_SECOND;
            case "startofminute":
                return START_OF_MINUTE;
            case "startofhour":
            case "firstminuteofhour":
                return START_OF_HOUR;
            case "startofday":
            case "firsthourofday":
                return START_OF_DAY;
            case "firstdayofmonth":
            case "startofmonth":
                return FIRST_DAY_OF_MONTH;
            case "lastdayofmonth":
                return LAST_DAY_OF_MONTH;
            case "firstdayofnextmonth":
            case "startofnextmonth":
                return FIRST_DAY_OF_NEXT_MONTH;
            case "firstdayofyear":
            case "startofyear":
                return FIRST_DAY_OF_YEAR;
            case "lastdayofyear":
                return LAST_DAY_OF_YEAR;
            case "firstdayofnextyear":
            case "startofnextyear":
                return FIRST_DAY_OF_NEXT_YEAR;
            default:
                return null;
        }
    }

    public ZonedDateTime apply(ZonedDateTime value) {
        switch (this) {
            case START_OF_SECOND:
                return value.truncatedTo(ChronoUnit.SECONDS);
            case START_OF_MINUTE:
                return value.truncatedTo(ChronoUnit.MINUTES);
            case START_OF_HOUR:
                return value.truncatedTo(ChronoUnit.HOURS);
            case START_OF_DAY:
                return atStartOf(value, value.toLocalDate());
            case FIRST_DAY_OF_MONTH:
                return atStartOf(value, value.toLocalDate().with(TemporalAdjusters.firstDayOfMonth()));
            case LAST_DAY_OF_MONTH:
                return atStartOf(value, value.toLocalDate().with(TemporalAdjusters.lastDayOfMonth()));
            case FIRST_DAY_OF_NEXT_MONTH:
                return atStartOf(value, value.toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth()));
            case FIRST_DAY_OF_YEAR:
                return atStartOf(value, value.toLocalDate().with(TemporalAdjusters.firstDayOfYear()));
            case LAST_DAY_OF_YEAR:
                return atStartOf(value, value.toLocalDate().with(TemporalAdjusters.lastDayOfYear()));
            case FIRST_DAY_OF_NEXT_YEAR:
                return atStartOf(value, value.toLocalDate().with(TemporalAdjusters.firstDayOfNextYear()));
            default:
                throw new IllegalStateException("unhandled truncation " + this);
        }
    }

    private static ZonedDateTime atStartOf(ZonedDateTime value, LocalDate date) {
        return date.atStartOfDay(value.getZone());
    }
}
