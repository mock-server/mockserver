package org.mockserver.time;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed date offset: one or more amounts with a unit, such as {@code "1 days"}, {@code "-2 hours"} or
 * {@code "+1 months 3 days"} (seconds, minutes, hours, days, weeks, months or years, singular or plural),
 * or an ISO-8601 period or duration such as {@code P1D}, {@code -P1M} or {@code PT90M}. Parts are applied
 * in order. Shared by the {@code dates} template helper and the date-time request matchers.
 */
public final class DateOffset {

    private static final Pattern OFFSET_PART = Pattern.compile("([+-]?\\d+)\\s*([a-zA-Z]+)");
    private static final Pattern OFFSET_SPEC = Pattern.compile("\\s*(?:[+-]?\\d+\\s*[a-zA-Z]+[\\s,]*)+");
    private static final int MAX_QUOTED_LENGTH = 100;

    // either an ISO period / duration, or the amount and unit parts, never both
    private final TemporalAmount iso;
    private final long[] amounts;
    private final ChronoUnit[] units;

    private DateOffset(TemporalAmount iso, long[] amounts, ChronoUnit[] units) {
        this.iso = iso;
        this.amounts = amounts;
        this.units = units;
    }

    /**
     * @throws IllegalArgumentException naming the offset when it is blank or not an offset
     */
    public static DateOffset parse(String offset) {
        if (offset == null || offset.trim().isEmpty()) {
            throw new IllegalArgumentException("no date offset given");
        }
        String spec = offset.trim();
        String unsigned = spec.startsWith("-") || spec.startsWith("+") ? spec.substring(1) : spec;
        try {
            if (unsigned.startsWith("P") || unsigned.startsWith("p")) {
                TemporalAmount amount = unsigned.toUpperCase(Locale.ROOT).contains("T") ? Duration.parse(spec) : Period.parse(spec);
                return new DateOffset(amount, null, null);
            }
            if (OFFSET_SPEC.matcher(spec).matches()) {
                List<Long> amounts = new ArrayList<>();
                List<ChronoUnit> units = new ArrayList<>();
                Matcher part = OFFSET_PART.matcher(spec);
                while (part.find()) {
                    amounts.add(Long.parseLong(part.group(1).startsWith("+") ? part.group(1).substring(1) : part.group(1)));
                    units.add(unit(part.group(2), offset));
                }
                long[] amountArray = new long[amounts.size()];
                for (int i = 0; i < amountArray.length; i++) {
                    amountArray[i] = amounts.get(i);
                }
                return new DateOffset(null, amountArray, units.toArray(new ChronoUnit[0]));
            }
        } catch (DateTimeException | ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("invalid date offset " + quote(offset) + ": " + brief(e.getMessage()));
        }
        throw new IllegalArgumentException("invalid date offset " + quote(offset) + "; use an amount and unit such as \"1 days\", \"-2 hours\" or \"+3 months\", or an ISO-8601 period such as P1D or PT1H");
    }

    private static ChronoUnit unit(String unit, String offset) {
        switch (unit.toLowerCase(Locale.ROOT)) {
            case "second":
            case "seconds":
                return ChronoUnit.SECONDS;
            case "minute":
            case "minutes":
                return ChronoUnit.MINUTES;
            case "hour":
            case "hours":
                return ChronoUnit.HOURS;
            case "day":
            case "days":
                return ChronoUnit.DAYS;
            case "week":
            case "weeks":
                return ChronoUnit.WEEKS;
            case "month":
            case "months":
                return ChronoUnit.MONTHS;
            case "year":
            case "years":
                return ChronoUnit.YEARS;
            default:
                throw new IllegalArgumentException("invalid date offset " + quote(offset) + ": unknown unit " + quote(unit) + "; use seconds, minutes, hours, days, weeks, months or years");
        }
    }

    /**
     * @throws DateTimeException   when the result is out of range
     * @throws ArithmeticException when the arithmetic overflows
     */
    public ZonedDateTime addTo(ZonedDateTime value) {
        if (iso != null) {
            return value.plus(iso);
        }
        ZonedDateTime result = value;
        for (int i = 0; i < amounts.length; i++) {
            result = result.plus(amounts[i], units[i]);
        }
        return result;
    }

    /**
     * The offset as a fixed {@link Duration} when adding it in {@code zone} never depends on the calendar,
     * so a caller can add it to an {@link java.time.Instant} directly: true for seconds, minutes and hours,
     * and for days and weeks in a fixed-offset zone such as UTC (no daylight-saving change). Months and
     * years always depend on the calendar.
     *
     * @return the duration, or {@code null} when the offset must be added to a zoned date-time
     */
    public Duration exactDuration(ZoneId zone) {
        boolean fixedZone = zone.getRules().isFixedOffset();
        try {
            if (iso instanceof Duration) {
                return (Duration) iso;
            }
            if (iso instanceof Period) {
                Period period = (Period) iso;
                return fixedZone && period.getYears() == 0 && period.getMonths() == 0 ? Duration.ofDays(period.getDays()) : null;
            }
            Duration total = Duration.ZERO;
            for (int i = 0; i < amounts.length; i++) {
                ChronoUnit unit = units[i];
                if (unit == ChronoUnit.MONTHS || unit == ChronoUnit.YEARS || (!fixedZone && (unit == ChronoUnit.DAYS || unit == ChronoUnit.WEEKS))) {
                    return null;
                }
                total = total.plus(unit == ChronoUnit.WEEKS ? Duration.ofDays(Math.multiplyExact(amounts[i], 7L)) : Duration.of(amounts[i], unit));
            }
            return total;
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private static String quote(String value) {
        return "\"" + brief(value) + "\"";
    }

    private static String brief(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_QUOTED_LENGTH ? value.substring(0, MAX_QUOTED_LENGTH) + "...(" + value.length() + " characters)" : value;
    }
}
