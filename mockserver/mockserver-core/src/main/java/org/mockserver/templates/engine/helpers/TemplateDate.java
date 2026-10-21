package org.mockserver.templates.engine.helpers;

import org.mockserver.time.DateOffset;
import org.mockserver.time.DateTruncation;
import org.mockserver.time.StrictDateFormats;
import org.mockserver.time.TimeService;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * An immutable date-time in a time zone, returned by the {@code dates} template helper so a template can
 * offset, truncate, convert and format it. {@link #toString()} is ISO-8601, so a template that just prints
 * it gets an ISO timestamp. Invalid input throws {@link IllegalArgumentException} with a message naming the
 * helper, which every template engine reports as a template failure.
 */
public final class TemplateDate {

    /**
     * RFC 1123 / RFC 9110 IMF-fixdate: always a two-digit day and GMT, as HTTP's Expires and Last-Modified
     * headers need. {@link DateTimeFormatter#RFC_1123_DATE_TIME} writes day 1 to 9 with one digit.
     */
    static final DateTimeFormatter IMF_FIXDATE = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter RFC_1123_STRICT = StrictDateFormats.RFC_1123_STRICT;
    private static final long EPOCH_MILLIS_THRESHOLD = StrictDateFormats.EPOCH_MILLIS_THRESHOLD;
    private static final Pattern EPOCH_NUMBER = Pattern.compile("[+-]?\\d{1,19}");
    // Patterns and zones can come from request data, so each cache holds at most MAX_CACHED entries of
    // short keys (emptied when full), and an over-long pattern or zone is refused outright.
    static final int MAX_CACHED = 256;
    static final int MAX_CACHED_KEY_LENGTH = 128;
    static final int MAX_PATTERN_LENGTH = 1024;
    static final int MAX_ZONE_LENGTH = 64;
    static final Map<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();
    static final Map<String, DateTimeFormatter> PARSERS = new ConcurrentHashMap<>();
    static final Map<String, ZoneId> ZONES = new ConcurrentHashMap<>();
    private static final int MAX_QUOTED_LENGTH = 100;

    private final ZonedDateTime value;

    private TemplateDate(ZonedDateTime value) {
        this.value = value;
    }

    static TemplateDate of(Instant instant, ZoneId zone) {
        return new TemplateDate(instant.atZone(zone));
    }

    // ----- parsing -----

    /**
     * Reads {@code input}: a {@link TemplateDate}, an {@link Instant}, a {@link Date}, a number of epoch
     * seconds or millis, or text that is {@code now}, ISO-8601, RFC-1123 or epoch digits; or, when
     * {@code pattern} is given, text in that pattern (or {@code iso}, {@code rfc1123}, {@code epochSeconds},
     * {@code epochMillis}). A value with no zone or offset is read in {@code zone} (UTC when null), and when
     * {@code zone} is given the result is expressed in it.
     */
    static TemplateDate parse(Object input, String pattern, String zoneName) {
        ZoneId zone = zoneName != null ? zone(zoneName) : null;
        ZoneId readZone = zone != null ? zone : ZoneOffset.UTC;
        ZonedDateTime parsed;
        if (input == null) {
            throw new IllegalArgumentException("dates.parse: no date given (the value is null)");
        } else if (input instanceof TemplateDate) {
            parsed = ((TemplateDate) input).value;
        } else if (input instanceof Instant) {
            parsed = ((Instant) input).atZone(readZone);
        } else if (input instanceof Date) {
            parsed = ((Date) input).toInstant().atZone(readZone);
        } else if (input instanceof ZonedDateTime) {
            parsed = (ZonedDateTime) input;
        } else if (input instanceof Number && (pattern == null || isEpochKeyword(pattern))) {
            parsed = fromNumber((Number) input, pattern == null ? null : pattern.trim().toLowerCase(Locale.ROOT), readZone);
        } else {
            String text = String.valueOf(input).trim();
            parsed = pattern == null ? parseText(text, readZone) : parseWithPattern(text, pattern, readZone);
        }
        return new TemplateDate(zone != null ? parsed.withZoneSameInstant(zone) : parsed);
    }

    private static boolean isEpochKeyword(String pattern) {
        String keyword = pattern.trim().toLowerCase(Locale.ROOT);
        return keyword.equals("epochseconds") || keyword.equals("epochmillis");
    }

    private static ZonedDateTime fromNumber(Number number, String unit, ZoneId zone) {
        if ((number instanceof Double || number instanceof Float) && number.doubleValue() != Math.rint(number.doubleValue())) {
            throw new IllegalArgumentException("dates.parse: epoch value " + number + " is not a whole number");
        }
        return fromEpoch(number.longValue(), unit, zone);
    }

    private static ZonedDateTime fromEpoch(long epoch, String unit, ZoneId zone) {
        boolean millis = unit != null ? unit.equals("epochmillis") : Math.abs(epoch) >= EPOCH_MILLIS_THRESHOLD;
        try {
            return (millis ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch)).atZone(zone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("dates.parse: epoch value " + epoch + " is out of range");
        }
    }

    private static ZonedDateTime parseText(String text, ZoneId zone) {
        if (text.isEmpty()) {
            throw new IllegalArgumentException("dates.parse: no date given (the value is empty); use now for the current time");
        }
        if (text.equalsIgnoreCase("now")) {
            return TimeService.now().atZone(zone);
        }
        if (EPOCH_NUMBER.matcher(text).matches()) {
            return fromEpoch(parseLong(text), null, zone);
        }
        ZonedDateTime parsed = parseIso(text, zone);
        if (parsed == null) {
            parsed = parseRfc1123(text);
        }
        if (parsed != null) {
            return parsed;
        }
        throw new IllegalArgumentException("dates.parse: cannot read " + quote(text) + " as a date; expected ISO-8601 (such as 2026-10-21T15:00:00Z, 2026-10-21T16:00:00+01:00 or 2026-10-21), RFC-1123 (such as Wed, 21 Oct 2026 15:00:00 GMT), epoch seconds or epoch millis, or pass the pattern it is written in");
    }

    private static ZonedDateTime parseIso(String text, ZoneId zone) {
        try {
            TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(text, ZonedDateTime::from, LocalDateTime::from);
            return parsed instanceof ZonedDateTime ? (ZonedDateTime) parsed : ((LocalDateTime) parsed).atZone(zone);
        } catch (DateTimeParseException ignore) {
            // not an ISO date-time; try the other forms
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(zone);
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }

    private static ZonedDateTime parseRfc1123(String text) {
        try {
            return ZonedDateTime.parse(text, RFC_1123_STRICT);
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.startsWith("+") ? text.substring(1) : text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("dates.parse: epoch value " + text + " is out of range");
        }
    }

    private static ZonedDateTime parseWithPattern(String text, String pattern, ZoneId zone) {
        String keyword = pattern.trim().toLowerCase(Locale.ROOT);
        switch (keyword) {
            case "epochseconds":
            case "epochmillis":
                if (!EPOCH_NUMBER.matcher(text).matches()) {
                    throw new IllegalArgumentException("dates.parse: " + quote(text) + " is not a number of " + (keyword.equals("epochmillis") ? "epoch millis" : "epoch seconds"));
                }
                return fromEpoch(parseLong(text), keyword, zone);
            case "iso":
                ZonedDateTime iso = parseIso(text, zone);
                if (iso == null) {
                    throw new IllegalArgumentException("dates.parse: cannot read " + quote(text) + " as an ISO-8601 date such as 2026-10-21T15:00:00Z, 2026-10-21T16:00:00+01:00 or 2026-10-21");
                }
                return iso;
            case "rfc1123":
                ZonedDateTime rfc1123 = parseRfc1123(text);
                if (rfc1123 == null) {
                    throw new IllegalArgumentException("dates.parse: cannot read " + quote(text) + " as an RFC-1123 date such as Wed, 21 Oct 2026 15:00:00 GMT");
                }
                return rfc1123;
            default:
                TemporalAccessor parsed;
                try {
                    parsed = parser(pattern).parse(text);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("dates.parse: cannot read " + quote(text) + " with pattern " + quote(pattern) + ": " + brief(e.getMessage()));
                }
                return resolve(parsed, text, pattern, zone);
        }
    }

    private static ZonedDateTime resolve(TemporalAccessor parsed, String text, String pattern, ZoneId zone) {
        try {
            ZoneId parsedZone = parsed.query(TemporalQueries.zone());
            ZoneId effectiveZone = parsedZone != null ? parsedZone : zone;
            LocalDate date = parsed.query(TemporalQueries.localDate());
            if (date == null && parsed.isSupported(ChronoField.YEAR) && parsed.isSupported(ChronoField.MONTH_OF_YEAR)) {
                date = LocalDate.of(parsed.get(ChronoField.YEAR), parsed.get(ChronoField.MONTH_OF_YEAR), 1);
            }
            if (date == null) {
                throw new IllegalArgumentException("dates.parse: pattern " + quote(pattern) + " has no date (year, month and day) in it, so " + quote(text) + " cannot be read as a date");
            }
            LocalTime time = parsed.query(TemporalQueries.localTime());
            if (time == null && (parsed.isSupported(ChronoField.CLOCK_HOUR_OF_AMPM) || parsed.isSupported(ChronoField.HOUR_OF_AMPM))) {
                throw new IllegalArgumentException("dates.parse: pattern " + quote(pattern) + " has a 12-hour clock hour (h or K) but no AM/PM marker (a), so the time in " + quote(text) + " cannot be read; use HH for a 24-hour clock or add a");
            }
            if (time == null && (parsed.isSupported(ChronoField.HOUR_OF_DAY) || parsed.isSupported(ChronoField.MINUTE_OF_HOUR) || parsed.isSupported(ChronoField.SECOND_OF_MINUTE))) {
                throw new IllegalArgumentException("dates.parse: pattern " + quote(pattern) + " has part of a time but not enough to read the time in " + quote(text));
            }
            return time != null ? ZonedDateTime.of(date, time, effectiveZone) : date.atStartOfDay(effectiveZone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("dates.parse: cannot read " + quote(text) + " with pattern " + quote(pattern) + ": " + brief(e.getMessage()));
        }
    }

    static ZoneId zone(String zoneName) {
        String name = zoneName == null ? "" : zoneName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("dates: no timezone given; use a region such as Europe/London, an offset such as +01:00, or UTC");
        }
        ZoneId cached = ZONES.get(name);
        if (cached != null) {
            return cached;
        }
        ZoneId zone;
        try {
            if (name.length() > MAX_ZONE_LENGTH) {
                throw new DateTimeException("too long");
            }
            zone = ZoneId.of(name, ZoneId.SHORT_IDS);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("dates: unknown timezone " + quote(name) + "; use a region such as Europe/London, an offset such as +01:00, or UTC");
        }
        cache(ZONES, name, zone);
        return zone;
    }

    static DateTimeFormatter formatter(String pattern) {
        DateTimeFormatter cached = FORMATTERS.get(checkPattern(pattern));
        if (cached != null) {
            return cached;
        }
        DateTimeFormatter formatter = compile(pattern, pattern);
        cache(FORMATTERS, pattern, formatter);
        return formatter;
    }

    /**
     * A strict parser for {@code pattern}: an impossible date such as 30 February fails instead of being
     * adjusted. Strict resolution needs an era for {@code y} (year of era), so an unquoted {@code y} is read
     * as {@code u} (proleptic year), which is the same for years after 0.
     */
    static DateTimeFormatter parser(String pattern) {
        DateTimeFormatter cached = PARSERS.get(checkPattern(pattern));
        if (cached != null) {
            return cached;
        }
        DateTimeFormatter parser = compile(StrictDateFormats.strictPattern(pattern), pattern).withResolverStyle(ResolverStyle.STRICT);
        cache(PARSERS, pattern, parser);
        return parser;
    }

    private static String checkPattern(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("dates: no date pattern given");
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException("dates: date pattern too long (" + pattern.length() + " characters, at most " + MAX_PATTERN_LENGTH + ")");
        }
        return pattern;
    }

    private static DateTimeFormatter compile(String compiledPattern, String pattern) {
        try {
            return DateTimeFormatter.ofPattern(compiledPattern, Locale.ENGLISH);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("dates: invalid date pattern " + quote(pattern) + ": " + brief(e.getMessage()));
        }
    }

    private static <V> void cache(Map<String, V> cache, String key, V value) {
        if (key.length() <= MAX_CACHED_KEY_LENGTH) {
            if (cache.size() >= MAX_CACHED) {
                cache.clear();
            }
            cache.put(key, value);
        }
    }

    static String quote(String value) {
        return "\"" + brief(value) + "\"";
    }

    private static String brief(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_QUOTED_LENGTH ? value.substring(0, MAX_QUOTED_LENGTH) + "...(" + value.length() + " characters)" : value;
    }

    // ----- offsets -----

    public TemplateDate plusSeconds(long seconds) {
        return new TemplateDate(value.plusSeconds(seconds));
    }

    public TemplateDate plusMinutes(long minutes) {
        return new TemplateDate(value.plusMinutes(minutes));
    }

    public TemplateDate plusHours(long hours) {
        return new TemplateDate(value.plusHours(hours));
    }

    public TemplateDate plusDays(long days) {
        return new TemplateDate(value.plusDays(days));
    }

    public TemplateDate plusWeeks(long weeks) {
        return new TemplateDate(value.plusWeeks(weeks));
    }

    public TemplateDate plusMonths(long months) {
        return new TemplateDate(value.plusMonths(months));
    }

    public TemplateDate plusYears(long years) {
        return new TemplateDate(value.plusYears(years));
    }

    public TemplateDate minusSeconds(long seconds) {
        return new TemplateDate(value.minusSeconds(seconds));
    }

    public TemplateDate minusMinutes(long minutes) {
        return new TemplateDate(value.minusMinutes(minutes));
    }

    public TemplateDate minusHours(long hours) {
        return new TemplateDate(value.minusHours(hours));
    }

    public TemplateDate minusDays(long days) {
        return new TemplateDate(value.minusDays(days));
    }

    public TemplateDate minusWeeks(long weeks) {
        return new TemplateDate(value.minusWeeks(weeks));
    }

    public TemplateDate minusMonths(long months) {
        return new TemplateDate(value.minusMonths(months));
    }

    public TemplateDate minusYears(long years) {
        return new TemplateDate(value.minusYears(years));
    }

    /**
     * Moves the date by an offset: one or more amounts with a unit, such as {@code "1 days"},
     * {@code "-2 hours"} or {@code "+1 months 3 days"} (seconds, minutes, hours, days, weeks, months or
     * years, singular or plural), or an ISO-8601 period or duration such as {@code P1D}, {@code -P1M} or
     * {@code PT90M}.
     */
    public TemplateDate offset(String offset) {
        DateOffset parsed;
        try {
            parsed = DateOffset.parse(offset);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("dates: " + e.getMessage());
        }
        try {
            return new TemplateDate(parsed.addTo(value));
        } catch (DateTimeException | ArithmeticException e) {
            throw new IllegalArgumentException("dates: invalid date offset " + quote(offset) + ": " + brief(e.getMessage()));
        }
    }

    // ----- truncation (in this date's time zone) -----

    public TemplateDate startOfSecond() {
        return new TemplateDate(DateTruncation.START_OF_SECOND.apply(value));
    }

    public TemplateDate startOfMinute() {
        return new TemplateDate(DateTruncation.START_OF_MINUTE.apply(value));
    }

    public TemplateDate startOfHour() {
        return new TemplateDate(DateTruncation.START_OF_HOUR.apply(value));
    }

    public TemplateDate startOfDay() {
        return new TemplateDate(DateTruncation.START_OF_DAY.apply(value));
    }

    public TemplateDate firstDayOfMonth() {
        return new TemplateDate(DateTruncation.FIRST_DAY_OF_MONTH.apply(value));
    }

    public TemplateDate lastDayOfMonth() {
        return new TemplateDate(DateTruncation.LAST_DAY_OF_MONTH.apply(value));
    }

    public TemplateDate firstDayOfNextMonth() {
        return new TemplateDate(DateTruncation.FIRST_DAY_OF_NEXT_MONTH.apply(value));
    }

    public TemplateDate firstDayOfYear() {
        return new TemplateDate(DateTruncation.FIRST_DAY_OF_YEAR.apply(value));
    }

    public TemplateDate lastDayOfYear() {
        return new TemplateDate(DateTruncation.LAST_DAY_OF_YEAR.apply(value));
    }

    public TemplateDate firstDayOfNextYear() {
        return new TemplateDate(DateTruncation.FIRST_DAY_OF_NEXT_YEAR.apply(value));
    }

    /**
     * Truncates by name: one of the truncation methods' names (case, spaces, hyphens and underscores are
     * ignored, so {@code "first day of month"} works), or WireMock's {@code "first minute of hour"} and
     * {@code "first hour of day"}.
     */
    public TemplateDate truncate(String truncation) {
        DateTruncation named = DateTruncation.named(truncation);
        if (named == null) {
            throw new IllegalArgumentException("dates: unknown date truncation " + quote(truncation) + "; use " + DateTruncation.NAMES);
        }
        return new TemplateDate(named.apply(value));
    }

    // ----- zone and output -----

    /**
     * The same instant expressed in another time zone, such as {@code Europe/London}, {@code +01:00} or
     * {@code UTC}.
     */
    public TemplateDate withZone(String zone) {
        return new TemplateDate(value.withZoneSameInstant(zone(zone)));
    }

    /**
     * Formats with a {@link DateTimeFormatter} pattern (English month and day names), or one of
     * {@code iso}, {@code rfc1123}, {@code epochSeconds} or {@code epochMillis}.
     */
    static boolean isFormatKeyword(String pattern) {
        if (pattern == null) {
            return false;
        }
        switch (pattern.trim().toLowerCase(Locale.ROOT)) {
            case "iso":
            case "rfc1123":
            case "epochseconds":
            case "epochmillis":
                return true;
            default:
                return false;
        }
    }

    public String format(String pattern) {
        if (pattern != null) {
            switch (pattern.trim().toLowerCase(Locale.ROOT)) {
                case "iso":
                    return iso();
                case "rfc1123":
                    return rfc1123();
                case "epochseconds":
                    return String.valueOf(epochSeconds());
                case "epochmillis":
                    return String.valueOf(epochMillis());
                default:
                    break;
            }
        }
        return formatter(pattern).format(value);
    }

    /**
     * ISO-8601: {@code 2026-10-21T15:00:00Z} in UTC, otherwise with the offset, such as
     * {@code 2026-10-21T16:00:00+01:00}.
     */
    public String iso() {
        return value.getZone().normalized().equals(ZoneOffset.UTC)
            ? DateTimeFormatter.ISO_INSTANT.format(value)
            : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value);
    }

    /**
     * The HTTP date format (RFC 1123, two-digit day, always GMT), such as
     * {@code Wed, 21 Oct 2026 15:00:00 GMT}, for headers such as Expires and Last-Modified.
     */
    public String rfc1123() {
        return IMF_FIXDATE.format(value);
    }

    public long epochSeconds() {
        return value.toEpochSecond();
    }

    public long epochMillis() {
        return value.toInstant().toEpochMilli();
    }

    public Instant toInstant() {
        return value.toInstant();
    }

    /**
     * True if this date is before {@code other}, which is read as {@code dates.parse} reads it.
     */
    public boolean isBefore(Object other) {
        return value.toInstant().isBefore(parse(other, null, null).toInstant());
    }

    /**
     * True if this date is after {@code other}, which is read as {@code dates.parse} reads it.
     */
    public boolean isAfter(Object other) {
        return value.toInstant().isAfter(parse(other, null, null).toInstant());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TemplateDate && ((TemplateDate) other).value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return iso();
    }
}
