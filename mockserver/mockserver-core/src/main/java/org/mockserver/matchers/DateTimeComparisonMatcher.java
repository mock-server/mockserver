package org.mockserver.matchers;

import org.apache.commons.lang3.StringUtils;
import org.mockserver.time.DateOffset;
import org.mockserver.time.DateTruncation;
import org.mockserver.time.StrictDateFormats;
import org.mockserver.time.TimeService;

import java.text.ParsePosition;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Locale;

/**
 * The compiled form of a {@link org.mockserver.model.NottableDateTimeString}: a date-time comparison
 * parsed once, when the matcher value is created, and evaluated per request without re-parsing.
 *
 * <p>The expected value is {@code now}, optionally followed by an offset ({@code now -1 days},
 * {@code now +2 hours}, {@code now PT30M}), or a literal date-time: ISO-8601, RFC-1123 or epoch
 * seconds/millis. {@code now} reads MockServer's controllable clock. A literal expected value is resolved
 * (and truncated) once; {@code now} plus a calendar-free offset is a single {@link Instant#plus}; anything
 * else is one {@link ZonedDateTime} offset / truncation. The actual value is read with a static or
 * pre-compiled strict formatter chosen from the text's shape.
 */
public final class DateTimeComparisonMatcher {

    public enum Operator {
        BEFORE("before"),
        AFTER("after"),
        EQUAL_TO_DATE_TIME("equalToDateTime");

        private static final Operator[] OPERATORS = values();
        private final String fieldName;

        Operator(String fieldName) {
            this.fieldName = fieldName;
        }

        /**
         * The JSON field name, as WireMock names it.
         */
        public String fieldName() {
            return fieldName;
        }

        /**
         * @return the operator with this JSON field name, or {@code null}
         */
        public static Operator forFieldName(String fieldName) {
            for (Operator operator : OPERATORS) {
                if (operator.fieldName.equals(fieldName)) {
                    return operator;
                }
            }
            return null;
        }
    }

    private enum Format {AUTO, ISO, RFC_1123, EPOCH_SECONDS, EPOCH_MILLIS, PATTERN}

    private static final int MAX_LENGTH = 1024;

    private final Operator operator;
    private final ZoneId zone;
    private final Format format;
    private final DateTimeFormatter pattern;
    private final DateTruncation truncateActual;
    // a literal expected value is resolved (and truncated) once; null when relative to now
    private final Instant fixedExpected;
    // set when now + offset is plain Instant arithmetic: no expected truncation and a calendar-free offset
    private final Duration nowExactOffset;
    private final DateOffset nowOffset;
    private final DateTruncation truncateExpected;
    private final boolean applyTruncationLast;

    private DateTimeComparisonMatcher(Operator operator, ZoneId zone, Format format, DateTimeFormatter pattern, DateTruncation truncateActual, Instant fixedExpected, Duration nowExactOffset, DateOffset nowOffset, DateTruncation truncateExpected, boolean applyTruncationLast) {
        this.operator = operator;
        this.zone = zone;
        this.format = format;
        this.pattern = pattern;
        this.truncateActual = truncateActual;
        this.fixedExpected = fixedExpected;
        this.nowExactOffset = nowExactOffset;
        this.nowOffset = nowOffset;
        this.truncateExpected = truncateExpected;
        this.applyTruncationLast = applyTruncationLast;
    }

    /**
     * Parses a date-time comparison.
     *
     * @param actualFormat     how the actual value is written: a {@link DateTimeFormatter} pattern or one of
     *                         {@code iso}, {@code rfc1123}, {@code epochSeconds}, {@code epochMillis}; null reads any of those forms
     * @param truncateExpected a {@link DateTruncation} name, or null
     * @param truncateActual   a {@link DateTruncation} name, or null
     * @param zone             the zone for values without one, for truncation and for calendar offsets; null is UTC
     * @throws IllegalArgumentException saying what is wrong
     */
    public static DateTimeComparisonMatcher compile(Operator operator, String expected, String actualFormat, String truncateExpected, String truncateActual, Boolean applyTruncationLast, String zone) {
        if (operator == null) {
            throw new IllegalArgumentException("a date-time matcher needs one of before, after or equalToDateTime");
        }
        String expectedText = expected == null ? "" : expected.trim();
        if (expectedText.isEmpty()) {
            throw new IllegalArgumentException("no expected date-time; use now, now with an offset such as now -1 days, or a date-time such as 2026-10-21T15:00:00Z");
        }
        for (String value : new String[]{expectedText, actualFormat, truncateExpected, truncateActual, zone}) {
            if (value != null && value.length() > MAX_LENGTH) {
                throw new IllegalArgumentException("a date-time matcher value is at most " + MAX_LENGTH + " characters");
            }
        }
        ZoneId zoneId = zone == null ? ZoneOffset.UTC : zone(zone.trim());
        Format format = Format.AUTO;
        DateTimeFormatter formatter = null;
        if (actualFormat != null) {
            switch (actualFormat.trim().toLowerCase(Locale.ROOT)) {
                case "iso":
                    format = Format.ISO;
                    break;
                case "rfc1123":
                    format = Format.RFC_1123;
                    break;
                case "epochseconds":
                    format = Format.EPOCH_SECONDS;
                    break;
                case "epochmillis":
                    format = Format.EPOCH_MILLIS;
                    break;
                default:
                    format = Format.PATTERN;
                    try {
                        formatter = StrictDateFormats.strictParser(actualFormat);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("invalid actualFormat pattern \"" + actualFormat + "\": " + e.getMessage());
                    }
            }
        }
        DateTruncation expectedTruncation = truncation(truncateExpected);
        DateTruncation actualTruncation = truncation(truncateActual);
        boolean truncateLast = applyTruncationLast != null && applyTruncationLast;

        if (expectedText.regionMatches(true, 0, "now", 0, 3)) {
            String offsetText = expectedText.substring(3).trim();
            DateOffset offset = offsetText.isEmpty() ? null : DateOffset.parse(offsetText);
            Duration exact = expectedTruncation != null ? null : (offset == null ? Duration.ZERO : offset.exactDuration(zoneId));
            return new DateTimeComparisonMatcher(operator, zoneId, format, formatter, actualTruncation, null, exact, offset, expectedTruncation, truncateLast);
        }
        ZonedDateTime literal = parseOrNull(expectedText, Format.AUTO, null, zoneId);
        try {
            if (literal != null && expectedTruncation != null) {
                literal = expectedTruncation.apply(literal.withZoneSameInstant(zoneId));
            }
        } catch (DateTimeException | ArithmeticException e) {
            literal = null;
        }
        if (literal == null) {
            throw new IllegalArgumentException("cannot read expected \"" + expectedText + "\" as a date-time; use now, now with an offset such as now -1 days, ISO-8601 such as 2026-10-21T15:00:00Z, RFC-1123, or epoch seconds or millis");
        }
        return new DateTimeComparisonMatcher(operator, zoneId, format, formatter, actualTruncation, literal.toInstant(), null, null, null, false);
    }

    private static ZoneId zone(String name) {
        try {
            return ZoneId.of(name, ZoneId.SHORT_IDS);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("unknown zone \"" + name + "\"; use a region such as Europe/London, an offset such as +01:00, or UTC");
        }
    }

    private static DateTruncation truncation(String name) {
        if (name == null) {
            return null;
        }
        DateTruncation truncation = DateTruncation.named(name);
        if (truncation == null) {
            throw new IllegalArgumentException("unknown truncation \"" + name + "\"; use " + DateTruncation.NAMES);
        }
        return truncation;
    }

    /**
     * @return whether {@code actual} satisfies the comparison, or {@code null} when it cannot be read as a
     * date-time (or the arithmetic overflows); never throws
     */
    public Boolean compare(String actual) {
        return compare(actual, fixedExpected == null ? TimeService.now() : null);
    }

    /**
     * @param now the current time; only read when the expected value is relative to now
     */
    Boolean compare(String actual, Instant now) {
        if (StringUtils.isBlank(actual)) {
            return null;
        }
        try {
            ZonedDateTime parsed = parse(actual.trim(), format, pattern, zone);
            if (parsed == null) {
                return null;
            }
            Instant actualInstant = truncateActual != null
                ? truncateActual.apply(parsed.withZoneSameInstant(zone)).toInstant()
                : parsed.toInstant();
            int comparison = actualInstant.compareTo(expected(now));
            switch (operator) {
                case BEFORE:
                    return comparison < 0;
                case AFTER:
                    return comparison > 0;
                default:
                    return comparison == 0;
            }
        } catch (DateTimeException | ArithmeticException e) {
            return null;
        }
    }

    private Instant expected(Instant now) {
        if (fixedExpected != null) {
            return fixedExpected;
        }
        if (nowExactOffset != null) {
            return now.plus(nowExactOffset);
        }
        ZonedDateTime expected = now.atZone(zone);
        if (truncateExpected != null && !applyTruncationLast) {
            expected = truncateExpected.apply(expected);
        }
        if (nowOffset != null) {
            expected = nowOffset.addTo(expected);
        }
        if (truncateExpected != null && applyTruncationLast) {
            expected = truncateExpected.apply(expected);
        }
        return expected.toInstant();
    }

    /**
     * Reads a date-time without exception-driven fallback for well-formed input: the form is chosen from
     * the text's shape. A value with no zone or offset is read in {@code zone}.
     *
     * @return the date-time, or {@code null} when the text is not in the expected form
     * @throws DateTimeException when the text has the form but is not a valid date-time
     */
    private static ZonedDateTime parse(String text, Format format, DateTimeFormatter pattern, ZoneId zone) {
        switch (format) {
            case ISO:
                return parseIso(text, zone);
            case RFC_1123:
                return parseRfc1123(text);
            case EPOCH_SECONDS:
                return isEpoch(text) ? Instant.ofEpochSecond(Long.parseLong(text)).atZone(zone) : null;
            case EPOCH_MILLIS:
                return isEpoch(text) ? Instant.ofEpochMilli(Long.parseLong(text)).atZone(zone) : null;
            case PATTERN:
                return hasSyntax(pattern, text) ? resolve(pattern.parse(text), zone) : null;
            default:
                if (isEpoch(text)) {
                    long epoch = Long.parseLong(text);
                    return (Math.abs(epoch) >= StrictDateFormats.EPOCH_MILLIS_THRESHOLD ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch)).atZone(zone);
                }
                char first = text.charAt(0);
                if (Character.isLetter(first)) {
                    return parseRfc1123(text);
                }
                return first >= '0' && first <= '9' || first == '+' || first == '-' ? parseIso(text, zone) : null;
        }
    }

    /**
     * True when {@code text} has the formatter's syntax, checked without throwing: an unrelated value
     * compared against a date-time matcher, such as {@code application/json}, costs no exception. Only text
     * with the right shape but an impossible date (such as 30 February) reaches the throwing parse.
     */
    private static boolean hasSyntax(DateTimeFormatter formatter, String text) {
        ParsePosition position = new ParsePosition(0);
        return formatter.parseUnresolved(text, position) != null && position.getErrorIndex() < 0 && position.getIndex() == text.length();
    }

    private static ZonedDateTime parseRfc1123(String text) {
        return hasSyntax(StrictDateFormats.RFC_1123_STRICT, text) ? ZonedDateTime.parse(text, StrictDateFormats.RFC_1123_STRICT) : null;
    }

    private static ZonedDateTime parseOrNull(String text, Format format, DateTimeFormatter pattern, ZoneId zone) {
        try {
            return parse(text, format, pattern, zone);
        } catch (DateTimeException | ArithmeticException e) {
            return null;
        }
    }

    private static ZonedDateTime parseIso(String text, ZoneId zone) {
        if (text.indexOf('T') < 0 && text.indexOf('t') < 0) {
            return hasSyntax(DateTimeFormatter.ISO_LOCAL_DATE, text) ? LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(zone) : null;
        }
        if (!hasSyntax(DateTimeFormatter.ISO_DATE_TIME, text)) {
            return null;
        }
        TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(text, ZonedDateTime::from, LocalDateTime::from);
        return parsed instanceof ZonedDateTime ? (ZonedDateTime) parsed : ((LocalDateTime) parsed).atZone(zone);
    }

    // an optional sign then 1 to 18 digits, so Long.parseLong cannot overflow
    private static boolean isEpoch(String text) {
        int start = text.charAt(0) == '-' || text.charAt(0) == '+' ? 1 : 0;
        int digits = text.length() - start;
        if (digits < 1 || digits > 18) {
            return false;
        }
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static ZonedDateTime resolve(TemporalAccessor parsed, ZoneId zone) {
        ZoneId parsedZone = parsed.query(TemporalQueries.zone());
        LocalDate date = parsed.query(TemporalQueries.localDate());
        if (date == null && parsed.isSupported(ChronoField.YEAR) && parsed.isSupported(ChronoField.MONTH_OF_YEAR)) {
            date = LocalDate.of(parsed.get(ChronoField.YEAR), parsed.get(ChronoField.MONTH_OF_YEAR), 1);
        }
        if (date == null) {
            return null;
        }
        LocalTime time = parsed.query(TemporalQueries.localTime());
        if (time == null && (parsed.isSupported(ChronoField.CLOCK_HOUR_OF_AMPM) || parsed.isSupported(ChronoField.HOUR_OF_AMPM)
            || parsed.isSupported(ChronoField.HOUR_OF_DAY) || parsed.isSupported(ChronoField.MINUTE_OF_HOUR) || parsed.isSupported(ChronoField.SECOND_OF_MINUTE))) {
            // part of a time that does not resolve, such as a 12-hour clock hour without AM/PM
            return null;
        }
        ZoneId effectiveZone = parsedZone != null ? parsedZone : zone;
        return time != null ? ZonedDateTime.of(date, time, effectiveZone) : date.atStartOfDay(effectiveZone);
    }
}
