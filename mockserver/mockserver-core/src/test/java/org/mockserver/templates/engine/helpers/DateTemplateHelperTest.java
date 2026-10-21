package org.mockserver.templates.engine.helpers;

import org.junit.Rule;
import org.junit.Test;
import org.mockserver.time.FixedTime;
import org.mockserver.time.TimeService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThrows;

public class DateTemplateHelperTest {

    private static final String INSTANT = "2026-10-21T15:04:05Z";

    @Rule
    public final FixedTime fixedTime = new FixedTime();

    private final DateTemplateHelper helper = new DateTemplateHelper();

    @Test
    public void shouldFormatWithPattern() {
        String result = helper.format("yyyy-MM-dd");
        assertThat(result, matchesPattern("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    public void shouldPlusSeconds() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.plusSeconds(60);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(greaterThanOrEqualTo(beforeEpoch + 60)));
    }

    @Test
    public void shouldPlusMinutes() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.plusMinutes(5);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(greaterThanOrEqualTo(beforeEpoch + 300)));
    }

    @Test
    public void shouldPlusHours() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.plusHours(1);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(greaterThanOrEqualTo(beforeEpoch + 3600)));
    }

    @Test
    public void shouldPlusDays() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.plusDays(1);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(greaterThanOrEqualTo(beforeEpoch + 86400)));
    }

    @Test
    public void shouldMinusSeconds() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.minusSeconds(60);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(lessThanOrEqualTo(beforeEpoch - 60)));
    }

    @Test
    public void shouldMinusMinutes() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.minusMinutes(5);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(lessThanOrEqualTo(beforeEpoch - 300)));
    }

    @Test
    public void shouldMinusHours() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.minusHours(1);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(lessThanOrEqualTo(beforeEpoch - 3600)));
    }

    @Test
    public void shouldMinusDays() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.minusDays(1);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(lessThanOrEqualTo(beforeEpoch - 86400)));
    }

    @Test
    public void shouldReturnEpochSeconds() {
        long before = TimeService.now().getEpochSecond();
        long result = helper.epochSeconds();
        assertThat(result, is(greaterThanOrEqualTo(before)));
    }

    @Test
    public void shouldReturnEpochMillis() {
        long before = TimeService.now().toEpochMilli();
        long result = helper.epochMillis();
        assertThat(result, is(greaterThanOrEqualTo(before)));
    }

    @Test
    public void shouldReturnEpochSecondsPlus() {
        long before = TimeService.now().getEpochSecond();
        String result = helper.epochSecondsPlus(60);
        long resultEpoch = Long.parseLong(result);
        assertThat(resultEpoch, is(greaterThanOrEqualTo(before + 60)));
    }

    @Test
    public void shouldReturnEpochSecondsMinus() {
        long before = TimeService.now().getEpochSecond();
        String result = helper.epochSecondsMinus(60);
        long resultEpoch = Long.parseLong(result);
        assertThat(resultEpoch, is(lessThanOrEqualTo(before - 60)));
    }

    @Test
    public void shouldReturnIsoInstantFromToString() {
        String result = helper.toString();
        assertThat(result, matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z"));
    }

    @Test
    public void shouldBeRegisteredInTemplateFunctions() {
        Object datesHelper = org.mockserver.templates.engine.TemplateFunctions.BUILT_IN_HELPERS.get("dates");
        assertThat(datesHelper, is(notNullValue()));
        assertThat(datesHelper, instanceOf(DateTemplateHelper.class));
    }

    // ----- backward compatibility of the now-based methods (exact output with the clock pinned) -----

    @Test
    public void shouldKeepNowBasedOutputsUnchanged() {
        Instant now = TimeService.now();
        assertThat(helper.toString(), is(DateTimeFormatter.ISO_INSTANT.format(now)));
        assertThat(helper.format("yyyy-MM-dd HH:mm:ss EEE"), is(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEE", Locale.ENGLISH).withZone(ZoneOffset.UTC).format(now)));
        assertThat(helper.plusHours(1), is(DateTimeFormatter.ISO_INSTANT.format(now.plus(1, ChronoUnit.HOURS))));
        assertThat(helper.minusDays(2), is(DateTimeFormatter.ISO_INSTANT.format(now.minus(2, ChronoUnit.DAYS))));
        assertThat(helper.epochSeconds(), is(now.getEpochSecond()));
        assertThat(helper.epochMillis(), is(now.toEpochMilli()));
        assertThat(helper.epochSecondsPlus(60), is(String.valueOf(now.getEpochSecond() + 60)));
    }

    @Test
    public void shouldUseServerClockForNow() {
        Instant now = TimeService.now();
        assertThat(helper.now().toString(), is(DateTimeFormatter.ISO_INSTANT.format(now)));
        assertThat(helper.now().epochMillis(), is(now.toEpochMilli()));
        assertThat(helper.parse("now").epochMillis(), is(now.toEpochMilli()));
        assertThat(helper.now("Asia/Tokyo").format("yyyy-MM-dd'T'HH:mmXXX"), is(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmXXX").format(now.atZone(ZoneId.of("Asia/Tokyo")))));
        assertThat(helper.rfc1123(), is(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).withZone(ZoneOffset.UTC).format(now)));
    }

    // ----- parsing -----

    @Test
    public void shouldParseIsoForms() {
        assertThat(helper.parse(INSTANT).toString(), is(INSTANT));
        assertThat(helper.parse("2026-10-21T16:04:05+01:00").toString(), is("2026-10-21T16:04:05+01:00"));
        assertThat(helper.parse("2026-10-21T16:04:05+01:00").withZone("UTC").toString(), is(INSTANT));
        assertThat(helper.parse("2026-10-21T15:04:05").toString(), is(INSTANT));
        assertThat(helper.parse("2026-10-21").toString(), is("2026-10-21T00:00:00Z"));
        assertThat(helper.parse("2026-10-21T15:04:05.123Z").epochMillis(), is(Instant.parse("2026-10-21T15:04:05.123Z").toEpochMilli()));
    }

    @Test
    public void shouldParseRfc1123() {
        assertThat(helper.parse("Wed, 21 Oct 2026 15:04:05 GMT").toString(), is(INSTANT));
        assertThat(helper.parse("Thu, 1 Oct 2026 15:04:05 GMT", "rfc1123").toString(), is("2026-10-01T15:04:05Z"));
    }

    @Test
    public void shouldParseEpochSecondsAndMillis() {
        long seconds = Instant.parse(INSTANT).getEpochSecond();
        assertThat(helper.parse(String.valueOf(seconds)).toString(), is(INSTANT));
        assertThat(helper.parse(String.valueOf(seconds * 1000)).toString(), is(INSTANT));
        assertThat(helper.parse(seconds).toString(), is(INSTANT));
        assertThat(helper.parse((int) 1_000_000_000).toString(), is("2001-09-09T01:46:40Z"));
        assertThat(helper.parse((double) (seconds * 1000)).toString(), is(INSTANT));
        assertThat(helper.parse("1000", "epochMillis").toString(), is("1970-01-01T00:00:01Z"));
        assertThat(helper.parse(1000, "epochSeconds").toString(), is("1970-01-01T00:16:40Z"));
    }

    @Test
    public void shouldParseJavaDateTypes() {
        Instant instant = Instant.parse(INSTANT);
        assertThat(helper.parse(instant).toString(), is(INSTANT));
        assertThat(helper.parse(Date.from(instant)).toString(), is(INSTANT));
        assertThat(helper.parse(helper.parse(INSTANT)).toString(), is(INSTANT));
    }

    @Test
    public void shouldParseWithPatternAndZone() {
        assertThat(helper.parse("21/10/2026 15:04", "dd/MM/yyyy HH:mm").toString(), is("2026-10-21T15:04:00Z"));
        assertThat(helper.parse("21/10/2026", "dd/MM/yyyy").toString(), is("2026-10-21T00:00:00Z"));
        assertThat(helper.parse("2026-10", "yyyy-MM").toString(), is("2026-10-01T00:00:00Z"));
        assertThat(helper.parse("21 Oct 2026 16:04 +0100", "dd MMM yyyy HH:mm Z").withZone("UTC").toString(), is("2026-10-21T15:04:00Z"));
        assertThat(helper.parse("21/10/2026 16:04", "dd/MM/yyyy HH:mm", "Europe/London").toString(), is("2026-10-21T16:04:00+01:00"));
        assertThat(helper.parse("21/10/2026 16:04", "dd/MM/yyyy HH:mm", "Europe/London").withZone("UTC").toString(), is("2026-10-21T15:04:00Z"));
        assertThat(helper.parse(INSTANT, "iso", "+02:00").toString(), is("2026-10-21T17:04:05+02:00"));
    }

    // ----- formatting -----

    @Test
    public void shouldFormatAnyDate() {
        assertThat(helper.format(INSTANT, "yyyy-MM-dd"), is("2026-10-21"));
        assertThat(helper.format(INSTANT, "EEEE d MMMM yyyy HH:mm", "America/New_York"), is("Wednesday 21 October 2026 11:04"));
        assertThat(helper.format(INSTANT, "iso"), is(INSTANT));
        assertThat(helper.format(INSTANT, "epochSeconds"), is(String.valueOf(Instant.parse(INSTANT).getEpochSecond())));
        assertThat(helper.format(INSTANT, "epochMillis"), is(String.valueOf(Instant.parse(INSTANT).toEpochMilli())));
        assertThat(helper.format("2026-10-21T16:04:05+01:00", "HH:mm"), is("16:04"));
    }

    @Test
    public void shouldFormatRfc1123WithTwoDigitDayInGmt() {
        assertThat(helper.rfc1123(INSTANT), is("Wed, 21 Oct 2026 15:04:05 GMT"));
        assertThat(helper.rfc1123("2026-10-01T01:00:00+02:00"), is("Wed, 30 Sep 2026 23:00:00 GMT"));
        assertThat(helper.parse("2026-10-01T00:00:00Z").rfc1123(), is("Thu, 01 Oct 2026 00:00:00 GMT"));
        assertThat(helper.format(INSTANT, "rfc1123"), is("Wed, 21 Oct 2026 15:04:05 GMT"));
        assertThat(helper.epochSeconds(INSTANT), is(Instant.parse(INSTANT).getEpochSecond()));
        assertThat(helper.epochMillis(INSTANT), is(Instant.parse(INSTANT).toEpochMilli()));
    }

    // ----- offsets -----

    @Test
    public void shouldOffsetAnyDate() {
        assertThat(helper.offset(INSTANT, "1 days").toString(), is("2026-10-22T15:04:05Z"));
        assertThat(helper.offset(INSTANT, "-2 hours").toString(), is("2026-10-21T13:04:05Z"));
        assertThat(helper.offset(INSTANT, "+1 months 3 days").toString(), is("2026-11-24T15:04:05Z"));
        assertThat(helper.offset(INSTANT, "1 year, -1 second").toString(), is("2027-10-21T15:04:04Z"));
        assertThat(helper.offset(INSTANT, "2 weeks").toString(), is("2026-11-04T15:04:05Z"));
        assertThat(helper.offset(INSTANT, "P1D").toString(), is("2026-10-22T15:04:05Z"));
        assertThat(helper.offset(INSTANT, "-P1M").toString(), is("2026-09-21T15:04:05Z"));
        assertThat(helper.offset(INSTANT, "PT90M").toString(), is("2026-10-21T16:34:05Z"));
        assertThat(helper.offset("2026-01-31T00:00:00Z", "1 months").toString(), is("2026-02-28T00:00:00Z"));
        assertThat(helper.parse(INSTANT).plusMinutes(5).minusSeconds(5).plusYears(1).toString(), is("2027-10-21T15:09:00Z"));
    }

    @Test
    public void shouldOffsetCalendarUnitsInTheDatesZone() {
        // the clocks go back in London on 25 Oct 2026, so one calendar day later is 25 hours later
        assertThat(helper.parse("2026-10-24T12:00:00+01:00").withZone("Europe/London").plusDays(1).toString(), is("2026-10-25T12:00:00Z"));
    }

    // ----- truncation -----

    @Test
    public void shouldTruncate() {
        String date = "2026-10-21T15:04:05.678Z";
        assertThat(helper.truncate(date, "startOfSecond").toString(), is(INSTANT));
        assertThat(helper.truncate(date, "startOfMinute").toString(), is("2026-10-21T15:04:00Z"));
        assertThat(helper.truncate(date, "startOfHour").toString(), is("2026-10-21T15:00:00Z"));
        assertThat(helper.truncate(date, "startOfDay").toString(), is("2026-10-21T00:00:00Z"));
        assertThat(helper.truncate(date, "firstDayOfMonth").toString(), is("2026-10-01T00:00:00Z"));
        assertThat(helper.truncate(date, "lastDayOfMonth").toString(), is("2026-10-31T00:00:00Z"));
        assertThat(helper.truncate(date, "firstDayOfNextMonth").toString(), is("2026-11-01T00:00:00Z"));
        assertThat(helper.truncate(date, "firstDayOfYear").toString(), is("2026-01-01T00:00:00Z"));
        assertThat(helper.truncate(date, "lastDayOfYear").toString(), is("2026-12-31T00:00:00Z"));
        assertThat(helper.truncate(date, "firstDayOfNextYear").toString(), is("2027-01-01T00:00:00Z"));
        assertThat(helper.truncate("2028-02-10T00:00:00Z", "lastDayOfMonth").toString(), is("2028-02-29T00:00:00Z"));
    }

    @Test
    public void shouldTruncateByWireMockNames() {
        assertThat(helper.truncate(INSTANT, "first minute of hour").toString(), is("2026-10-21T15:00:00Z"));
        assertThat(helper.truncate(INSTANT, "first hour of day").toString(), is("2026-10-21T00:00:00Z"));
        assertThat(helper.truncate(INSTANT, "first day of next month").toString(), is("2026-11-01T00:00:00Z"));
        assertThat(helper.truncate(INSTANT, "last day of year").toString(), is("2026-12-31T00:00:00Z"));
    }

    @Test
    public void shouldTruncateInTheDatesZone() {
        // 23:30 UTC on 31 Oct is already 1 Nov in Tokyo
        TemplateDate tokyo = helper.parse("2026-10-31T23:30:00Z").withZone("Asia/Tokyo");
        assertThat(tokyo.startOfDay().toString(), is("2026-11-01T00:00:00+09:00"));
        assertThat(tokyo.firstDayOfMonth().withZone("UTC").toString(), is("2026-10-31T15:00:00Z"));
        assertThat(helper.now().firstDayOfNextMonth().format("dd HH:mm:ss"), is("01 00:00:00"));
    }

    // ----- invalid input -----

    @Test
    public void shouldExplainInvalidInput() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("yesterday")).getMessage(), allOf(containsString("dates.parse"), containsString("\"yesterday\""), containsString("ISO-8601")));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse(null)).getMessage(), containsString("no date given"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("2026-13-01", "yyyy-MM-dd")).getMessage(), containsString("with pattern \"yyyy-MM-dd\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("15:04", "HH:mm")).getMessage(), containsString("has no date"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("abc", "epochSeconds")).getMessage(), containsString("not a number of epoch seconds"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse(1.5)).getMessage(), containsString("not a whole number"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.format(INSTANT, "yyyy-qqqqqq")).getMessage(), containsString("invalid date pattern \"yyyy-qqqqqq\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.now("Mars/Olympus")).getMessage(), containsString("unknown timezone \"Mars/Olympus\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.offset(INSTANT, "tomorrow")).getMessage(), containsString("invalid date offset \"tomorrow\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.offset(INSTANT, "3 fortnights")).getMessage(), containsString("unknown unit \"fortnights\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.truncate(INSTANT, "startOfWeek")).getMessage(), allOf(containsString("unknown date truncation \"startOfWeek\""), containsString("firstDayOfNextYear")));
    }

    @Test
    public void shouldCompareDates() {
        assertThat(helper.parse(INSTANT).isBefore("2026-10-21T15:04:06Z"), is(true));
        assertThat(helper.parse(INSTANT).isAfter("2026-10-21T15:04:06Z"), is(false));
        assertThat(helper.parse(INSTANT), is(helper.parse(INSTANT)));
    }

    @Test
    public void shouldRejectImpossibleOrIncompleteDatesWithAPattern() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("2026-02-30", "yyyy-MM-dd")).getMessage(), allOf(containsString("cannot read \"2026-02-30\" with pattern \"yyyy-MM-dd\""), containsString("Invalid date")));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("31/04/2026", "dd/MM/yyyy")).getMessage(), containsString("cannot read \"31/04/2026\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("2026-10-21 03:04", "yyyy-MM-dd hh:mm")).getMessage(), containsString("no AM/PM marker (a)"));
        assertThat(helper.parse("2026-10-21 03:04 PM", "yyyy-MM-dd hh:mm a").toString(), is("2026-10-21T15:04:00Z"));
        assertThat(helper.parse("2028-02-29", "yyyy-MM-dd").toString(), is("2028-02-29T00:00:00Z"));
        assertThat(helper.parse("year 2026, day 21/10", "'year' yyyy, 'day' dd/MM").toString(), is("2026-10-21T00:00:00Z"));
        assertThat(helper.parse("2026-10-21 15", "uuuu-MM-dd HH").toString(), is("2026-10-21T15:00:00Z"));
    }

    @Test
    public void shouldRejectEmptyDate() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("")).getMessage(), containsString("no date given (the value is empty); use now"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.offset("  ", "1 days")).getMessage(), containsString("no date given"));
    }

    @Test
    public void shouldAcceptOutputKeywordsWhenFormattingNow() {
        assertThat(helper.format("rfc1123"), is(helper.rfc1123()));
        assertThat(helper.format("epochMillis"), is(String.valueOf(TimeService.now().toEpochMilli())));
    }

    @Test
    public void shouldBoundWhatRequestDataCanCache() {
        String longZone = "UTC" + " ".repeat(TemplateDate.MAX_ZONE_LENGTH);
        assertThat(helper.now(longZone).toString(), is(helper.now().toString()));
        assertThat(TemplateDate.ZONES.containsKey(longZone), is(false));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.now("Europe/" + "x".repeat(200))).getMessage(), allOf(containsString("unknown timezone"), containsString("(207 characters)")));

        String longPattern = "'" + "x".repeat(TemplateDate.MAX_CACHED_KEY_LENGTH) + "' yyyy";
        assertThat(helper.format(INSTANT, longPattern), is("x".repeat(TemplateDate.MAX_CACHED_KEY_LENGTH) + " 2026"));
        assertThrows(IllegalArgumentException.class, () -> helper.parse("x", longPattern));
        assertThat(TemplateDate.FORMATTERS.containsKey(longPattern), is(false));
        assertThat(TemplateDate.PARSERS.containsKey(longPattern), is(false));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.format(INSTANT, "y".repeat(TemplateDate.MAX_PATTERN_LENGTH + 1))).getMessage(), containsString("date pattern too long"));

        for (int i = 0; i < TemplateDate.MAX_CACHED + 50; i++) {
            helper.format(INSTANT, "'" + i + "' yyyy");
        }
        assertThat(TemplateDate.FORMATTERS.size(), is(lessThanOrEqualTo(TemplateDate.MAX_CACHED + Runtime.getRuntime().availableProcessors())));
        assertThat(helper.format(INSTANT, "yyyy-MM-dd"), is("2026-10-21"));
    }

    @Test
    public void shouldReadRfc1123StrictlyInEveryForm() {
        assertThat(helper.parse("Wed, 21 Oct 2026 15:04:05 GMT").toString(), is(INSTANT));
        assertThat(helper.parse("21 Oct 2026 15:04:05 GMT").toString(), is(INSTANT));
        assertThat(helper.parse("Thu, 1 Oct 2026 15:04:05 GMT").toString(), is("2026-10-01T15:04:05Z"));
        assertThat(helper.parse("Thu, 01 Oct 2026 15:04:05 +0100").toString(), is("2026-10-01T15:04:05+01:00"));
        assertThat(helper.parse("Sun, 01 Mar 2026 10:00 GMT").toString(), is("2026-03-01T10:00:00Z"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("30 Feb 2026 10:00:00 GMT")).getMessage(), containsString("cannot read \"30 Feb 2026 10:00:00 GMT\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("Sat, 30 Feb 2026 10:00:00 GMT", "rfc1123")).getMessage(), containsString("RFC-1123"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("Mon, 21 Oct 2026 15:04:05 GMT")).getMessage(), containsString("cannot read"));
    }

    @Test
    public void shouldReadIsoStrictly() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("2026-02-30T10:00:00Z")).getMessage(), containsString("cannot read"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("2026-02-30", "iso")).getMessage(), containsString("ISO-8601"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("2026-10-21T24:00:00")).getMessage(), containsString("cannot read"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> helper.parse("99999999999999999999")).getMessage(), containsString("cannot read"));
    }
}
