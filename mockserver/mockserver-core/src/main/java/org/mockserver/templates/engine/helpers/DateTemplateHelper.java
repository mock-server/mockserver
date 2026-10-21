package org.mockserver.templates.engine.helpers;

import org.mockserver.time.TimeService;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * The {@code dates} template helper. The methods that take no date work on the server clock's "now" in
 * UTC (see {@link TimeService}, which the clock-control API can freeze or advance). The others take a
 * date, which is a {@link TemplateDate} from this helper or anything {@link #parse(Object)} reads, and
 * return a {@link TemplateDate} so calls can be chained, such as
 * {@code dates.now("Europe/London").plusHours(1).format("HH:mm")}. Invalid input throws
 * {@link IllegalArgumentException}, which the template engines report as a template failure.
 */
public class DateTemplateHelper {

    public String format(String pattern) {
        if (TemplateDate.isFormatKeyword(pattern)) {
            return now().format(pattern);
        }
        return TemplateDate.formatter(pattern)
            .withZone(ZoneOffset.UTC)
            .format(TimeService.now());
    }

    /**
     * Formats {@code date} with a pattern, or {@code iso}, {@code rfc1123}, {@code epochSeconds} or
     * {@code epochMillis}, in the date's own time zone.
     */
    public String format(Object date, String pattern) {
        return parse(date).format(pattern);
    }

    /**
     * Formats {@code date} with a pattern in the given time zone, such as {@code Europe/London}.
     */
    public String format(Object date, String pattern, String zone) {
        return parse(date).withZone(zone).format(pattern);
    }

    /**
     * Now, in UTC.
     */
    public TemplateDate now() {
        return TemplateDate.of(TimeService.now(), ZoneOffset.UTC);
    }

    /**
     * Now, in the given time zone, such as {@code Europe/London}, {@code +01:00} or {@code UTC}.
     */
    public TemplateDate now(String zone) {
        return TemplateDate.of(TimeService.now(), TemplateDate.zone(zone));
    }

    /**
     * Reads a date: ISO-8601 ({@code 2026-10-21T15:00:00Z}, {@code 2026-10-21T16:00:00+01:00},
     * {@code 2026-10-21T15:00:00} or {@code 2026-10-21}, read as UTC when there is no offset), RFC-1123
     * ({@code Wed, 21 Oct 2026 15:00:00 GMT}), {@code now}, or epoch seconds or millis (a number below
     * 100,000,000,000 is seconds, otherwise millis).
     */
    public TemplateDate parse(Object date) {
        return TemplateDate.parse(date, null, null);
    }

    /**
     * Reads a date written in {@code pattern}, such as {@code dd/MM/yyyy}, or in {@code iso},
     * {@code rfc1123}, {@code epochSeconds} or {@code epochMillis}. A value with no zone or offset is read
     * as UTC.
     */
    public TemplateDate parse(Object date, String pattern) {
        return TemplateDate.parse(date, pattern, null);
    }

    /**
     * As {@link #parse(Object, String)}, but a value with no zone or offset is read in {@code zone}, and
     * the result is in {@code zone}.
     */
    public TemplateDate parse(Object date, String pattern, String zone) {
        return TemplateDate.parse(date, pattern, zone);
    }

    /**
     * {@code date} moved by an offset such as {@code "1 days"}, {@code "-2 hours"} or {@code P1M}; see
     * {@link TemplateDate#offset(String)}.
     */
    public TemplateDate offset(Object date, String offset) {
        return parse(date).offset(offset);
    }

    /**
     * {@code date} truncated, such as to {@code startOfDay}, {@code firstDayOfMonth} or
     * {@code firstDayOfNextYear}; see {@link TemplateDate#truncate(String)}.
     */
    public TemplateDate truncate(Object date, String truncation) {
        return parse(date).truncate(truncation);
    }

    /**
     * {@code date} expressed in another time zone.
     */
    public TemplateDate withZone(Object date, String zone) {
        return parse(date).withZone(zone);
    }

    /**
     * Now in the HTTP date format, such as {@code Wed, 21 Oct 2026 15:00:00 GMT}, for headers such as
     * Expires and Last-Modified.
     */
    public String rfc1123() {
        return TemplateDate.IMF_FIXDATE.format(TimeService.now());
    }

    /**
     * {@code date} in the HTTP date format, always in GMT.
     */
    public String rfc1123(Object date) {
        return parse(date).rfc1123();
    }

    public long epochSeconds(Object date) {
        return parse(date).epochSeconds();
    }

    public long epochMillis(Object date) {
        return parse(date).epochMillis();
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
