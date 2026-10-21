package org.mockserver.matchers;

import org.junit.ClassRule;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.DateTimeComparisonMatcher.Operator;
import org.mockserver.model.NottableDateTimeString;
import org.mockserver.time.FixedTime;
import org.mockserver.time.TimeService;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.NottableString.string;

/**
 * Behavioural tests for date-time matcher values ({@link NottableDateTimeString}) matched through
 * {@link RegexStringMatcher}, as header, cookie, query-string and path-parameter values are.
 */
public class DateTimeComparisonMatcherTest {

    // pins TimeService.now() to FIXED_INSTANT_FOR_TESTS on this thread only, so it is parallel-safe
    @ClassRule
    public static final FixedTime fixedTime = new FixedTime();

    private static final Instant NOW = Instant.parse("2026-10-21T15:04:05Z");

    /**
     * Builds a matcher from a compact test notation, {@code [!]operator:expected[;option=value...]}, so the
     * cases stay readable; the notation exists only in this test.
     */
    static NottableDateTimeString dateTime(String spec) {
        boolean not = spec.startsWith("!");
        String[] parts = (not ? spec.substring(1) : spec).split(";", -1);
        int colon = parts[0].indexOf(':');
        NottableDateTimeString matcher = NottableDateTimeString.dateTime(Operator.forFieldName(parts[0].substring(0, colon)), parts[0].substring(colon + 1), null, null, null, null, null, not);
        for (int i = 1; i < parts.length; i++) {
            String name = parts[i].substring(0, parts[i].indexOf('='));
            String value = parts[i].substring(parts[i].indexOf('=') + 1);
            switch (name) {
                case "actualFormat":
                    matcher = matcher.withActualFormat(value);
                    break;
                case "truncateExpected":
                    matcher = matcher.withTruncateExpected(value);
                    break;
                case "truncateActual":
                    matcher = matcher.withTruncateActual(value);
                    break;
                case "applyTruncationLast":
                    matcher = matcher.withApplyTruncationLast(Boolean.valueOf(value));
                    break;
                case "zone":
                    matcher = matcher.withZone(value);
                    break;
                default:
                    throw new IllegalArgumentException(name);
            }
        }
        return matcher;
    }

    private boolean matches(String expected, String actual) {
        return new RegexStringMatcher(new MockServerLogger(), false).matches(dateTime(expected), string(actual));
    }

    private boolean matchesAt(String spec, String actual, Instant now) {
        NottableDateTimeString matcher = dateTime(spec);
        Boolean result = DateTimeComparisonMatcher.compile(matcher.getOperator(), matcher.getExpected(), matcher.getActualFormat(), matcher.getTruncateExpected(), matcher.getTruncateActual(), matcher.getApplyTruncationLast(), matcher.getZone()).compare(actual, now);
        return result != null && result;
    }

    // ----- literal expected values -----

    @Test
    public void shouldCompareAgainstLiteralIsoInstant() {
        assertThat(matches("before:2026-10-21T15:00:00Z", "2026-10-21T14:59:59Z"), is(true));
        assertThat(matches("before:2026-10-21T15:00:00Z", "2026-10-21T15:00:00Z"), is(false));
        assertThat(matches("after:2026-10-21T15:00:00Z", "2026-10-21T15:00:01Z"), is(true));
        assertThat(matches("after:2026-10-21T15:00:00Z", "2026-10-21T15:00:00Z"), is(false));
        assertThat(matches("equalToDateTime:2026-10-21T15:00:00Z", "2026-10-21T15:00:00Z"), is(true));
        assertThat(matches("equalToDateTime:2026-10-21T15:00:00Z", "2026-10-21T15:00:00.001Z"), is(false));
    }

    @Test
    public void shouldCompareInstantsAcrossDifferentOffsets() {
        assertThat(matches("equalToDateTime:2026-10-21T15:00:00Z", "2026-10-21T16:00:00+01:00"), is(true));
        assertThat(matches("equalToDateTime:2026-10-21T16:00:00+01:00", "Wed, 21 Oct 2026 15:00:00 GMT"), is(true));
        assertThat(matches("before:2026-10-21T15:00:00Z", "2026-10-21T15:30:00+01:00"), is(true));
    }

    @Test
    public void shouldReadRfc1123EpochAndIsoDateActualValues() {
        assertThat(matches("equalToDateTime:2026-10-21T15:00:00Z", "Wed, 21 Oct 2026 15:00:00 GMT"), is(true));
        assertThat(matches("equalToDateTime:2026-10-21T15:00:00Z", "1792594800"), is(true));
        assertThat(matches("equalToDateTime:2026-10-21T15:00:00Z", "1792594800000"), is(true));
        assertThat(matches("equalToDateTime:2026-10-21T00:00:00Z", "2026-10-21"), is(true));
        assertThat(matches("after:1792594800", "2026-10-21T15:00:01Z"), is(true));
    }

    // ----- now and offsets under a controlled clock -----

    @Test
    public void shouldResolveNowFromTheControllableClock() {
        Instant now = TimeService.FIXED_INSTANT_FOR_TESTS;
        assertThat(matches("after:now -1 hours", now.minus(Duration.ofMinutes(30)).toString()), is(true));
        assertThat(matches("after:now -1 hours", now.minus(Duration.ofMinutes(90)).toString()), is(false));
        assertThat(matches("before:now", now.minusSeconds(1).toString()), is(true));
        assertThat(matches("before:now", now.plusSeconds(1).toString()), is(false));
        assertThat(matches("equalToDateTime:now", now.toString()), is(true));
    }

    @Test
    public void shouldApplyNowOffsets() {
        assertThat(matchesAt("after:now +3 days", "2026-10-24T15:04:06Z", NOW), is(true));
        assertThat(matchesAt("after:now +3 days", "2026-10-24T15:04:05Z", NOW), is(false));
        assertThat(matchesAt("before:now-2 hours", "2026-10-21T13:04:04Z", NOW), is(true));
        assertThat(matchesAt("equalToDateTime:now +1 months", "2026-11-21T15:04:05Z", NOW), is(true));
        assertThat(matchesAt("equalToDateTime:now -1 years, 1 seconds", "2025-10-21T15:04:06Z", NOW), is(true));
        assertThat(matchesAt("equalToDateTime:now PT90M", "2026-10-21T16:34:05Z", NOW), is(true));
        assertThat(matchesAt("equalToDateTime:now -P1D", "2026-10-20T15:04:05Z", NOW), is(true));
    }

    @Test
    public void shouldReReadNowOnEveryMatch() {
        DateTimeComparisonMatcher matcher = DateTimeComparisonMatcher.compile(Operator.AFTER, "now -1 minutes", null, null, null, null, null);
        assertThat(matcher.compare("2026-10-21T15:03:30Z", NOW), is(true));
        assertThat(matcher.compare("2026-10-21T15:03:30Z", NOW.plusSeconds(60)), is(false));
    }

    // ----- timezone and daylight saving -----

    @Test
    public void shouldAddCalendarDaysAcrossADaylightSavingChangeInTheGivenZone() {
        // London leaves BST at 01:00 UTC on 25 Oct 2026, so one calendar day after 12:00 UTC on 24 Oct is 13:00 UTC
        Instant beforeClocksChange = Instant.parse("2026-10-24T12:00:00Z");
        assertThat(matchesAt("equalToDateTime:now +1 days;zone=Europe/London", "2026-10-25T13:00:00Z", beforeClocksChange), is(true));
        // in UTC a day is always 24 hours
        assertThat(matchesAt("equalToDateTime:now +1 days", "2026-10-25T12:00:00Z", beforeClocksChange), is(true));
        assertThat(matchesAt("before:now +1 days;zone=Europe/London", "2026-10-25T12:30:00Z", beforeClocksChange), is(true));
        assertThat(matchesAt("before:now +1 days", "2026-10-25T12:30:00Z", beforeClocksChange), is(false));
        // hours are exact, so the zone makes no difference
        assertThat(matchesAt("equalToDateTime:now +24 hours;zone=Europe/London", "2026-10-25T12:00:00Z", beforeClocksChange), is(true));
    }

    @Test
    public void shouldReadValuesWithoutAZoneInTheGivenZone() {
        assertThat(matches("equalToDateTime:2026-07-01T00:30:00Z", "2026-07-01T00:30:00"), is(true));
        assertThat(matches("equalToDateTime:2026-06-30T23:30:00Z;zone=Europe/London", "2026-07-01T00:30:00"), is(true));
        assertThat(matches("equalToDateTime:2026-07-01T00:30:00;zone=Europe/London", "2026-06-30T23:30:00Z"), is(true));
        assertThat(matches("equalToDateTime:2026-01-01T00:30:00;zone=Europe/London", "2026-01-01T00:30:00Z"), is(true));
    }

    @Test
    public void shouldTruncateInTheGivenZone() {
        // 23:30 UTC on 31 Oct is still 31 Oct in UTC but 1 Nov in Tokyo
        assertThat(matches("equalToDateTime:2026-10-01T00:00:00Z;truncateActual=first day of month", "2026-10-31T23:30:00Z"), is(true));
        assertThat(matches("equalToDateTime:2026-10-31T15:00:00Z;truncateActual=first day of month;zone=Asia/Tokyo", "2026-10-31T23:30:00Z"), is(true));
    }

    // ----- truncation -----

    @Test
    public void shouldTruncateTheActualValue() {
        assertThat(matches("equalToDateTime:2026-10-21T00:00:00Z;truncateActual=startOfDay", "2026-10-21T18:45:00Z"), is(true));
        assertThat(matches("equalToDateTime:2026-01-01T00:00:00Z;truncateActual=first day of year", "2026-10-21T18:45:00Z"), is(true));
        assertThat(matches("equalToDateTime:2026-10-21T18:00:00Z;truncateActual=first minute of hour", "2026-10-21T18:45:12Z"), is(true));
    }

    @Test
    public void shouldTruncateTheExpectedValue() {
        assertThat(matches("after:2026-10-21T18:45:00Z;truncateExpected=startOfDay", "2026-10-21T00:00:01Z"), is(true));
        assertThat(matchesAt("equalToDateTime:now;truncateExpected=first day of month", "2026-10-01T00:00:00Z", NOW), is(true));
        assertThat(matchesAt("before:now;truncateExpected=startOfDay", "2026-10-20T23:59:59Z", NOW), is(true));
        assertThat(matchesAt("before:now;truncateExpected=startOfDay", "2026-10-21T00:00:01Z", NOW), is(false));
    }

    @Test
    public void shouldTruncateBeforeTheOffsetUnlessApplyTruncationLast() {
        // truncate then offset: 1 Oct + 15 days = 16 Oct; offset then truncate: 5 Nov -> 1 Nov
        assertThat(matchesAt("equalToDateTime:now +15 days;truncateExpected=first day of month", "2026-10-16T00:00:00Z", NOW), is(true));
        assertThat(matchesAt("equalToDateTime:now +15 days;truncateExpected=first day of month;applyTruncationLast=true", "2026-11-01T00:00:00Z", NOW), is(true));
    }

    @Test
    public void shouldTruncateBothSides() {
        assertThat(matchesAt("equalToDateTime:now;truncateExpected=first day of month;truncateActual=first day of month", "2026-10-30T09:00:00Z", NOW), is(true));
        assertThat(matchesAt("equalToDateTime:now;truncateExpected=first day of month;truncateActual=first day of month", "2026-11-01T00:00:00Z", NOW), is(false));
    }

    // ----- actual format -----

    @Test
    public void shouldReadTheActualValueWithAPattern() {
        assertThat(matches("equalToDateTime:2026-10-21T00:00:00Z;actualFormat=dd/MM/yyyy", "21/10/2026"), is(true));
        assertThat(matches("after:2026-10-21;actualFormat=dd/MM/yyyy HH:mm", "21/10/2026 09:30"), is(true));
        assertThat(matches("before:2026-10-21T00:00:00Z;actualFormat=dd MMM yyyy", "20 Oct 2026"), is(true));
        assertThat(matches("equalToDateTime:2026-10-21T00:00:00;zone=Europe/London;actualFormat=dd/MM/yyyy", "21/10/2026"), is(true));
    }

    @Test
    public void shouldReadNamedActualFormats() {
        assertThat(matches("equalToDateTime:2026-10-21T15:00:00Z;actualFormat=epochSeconds", "1792594800"), is(true));
        assertThat(matches("equalToDateTime:1970-01-21T17:56:34.800Z;actualFormat=epochMillis", "1792594800"), is(true));
        assertThat(matches("equalToDateTime:2026-10-21T15:00:00Z;actualFormat=rfc1123", "Wed, 21 Oct 2026 15:00:00 GMT"), is(true));
        assertThat(matches("equalToDateTime:2026-10-21T15:00:00Z;actualFormat=rfc1123", "2026-10-21T15:00:00Z"), is(false));
        assertThat(matches("equalToDateTime:2026-10-21T15:00:00Z;actualFormat=iso", "1792594800"), is(false));
    }

    // ----- invalid actual values never match and never throw -----

    @Test
    public void shouldNotMatchInvalidActualValues() {
        for (String actual : new String[]{"", " ", "abc", "now", "2026-02-30", "2026-13-01T00:00:00Z", "2026-10-21T25:00:00Z",
            "Mon, 21 Oct 2026 15:00:00 GMT", "Wed, 30 Feb 2026 15:00:00 GMT", "99999999999999999999", "1792594800.5", "-", "+",
            "2026-10-21T", "T", "2026/10/21"}) {
            assertThat("before: " + actual, matches("before:2100-01-01T00:00:00Z", actual), is(false));
            assertThat("after: " + actual, matches("after:1900-01-01T00:00:00Z", actual), is(false));
            assertThat("now: " + actual, matches("after:now -100 years", actual), is(false));
        }
    }

    @Test
    public void shouldNotMatchImpossibleDatesInAPatternStrictly() {
        assertThat(matches("after:1900-01-01;actualFormat=dd/MM/yyyy", "30/02/2026"), is(false));
        assertThat(matches("after:1900-01-01;actualFormat=dd/MM/yyyy", "21-10-2026"), is(false));
        // a 12-hour clock without an AM/PM marker cannot be read
        assertThat(matches("after:1900-01-01;actualFormat=dd/MM/yyyy hh:mm", "21/10/2026 09:30"), is(false));
    }

    @Test
    public void shouldNotMatchANullActualValue() {
        assertThat(new RegexStringMatcher(new MockServerLogger(), false).matches(dateTime("after:now -1 days"), string(null)), is(false));
    }

    @Test
    public void shouldNotMatchWhenOffsetArithmeticOverflows() {
        assertThat(matchesAt("after:now +999999999 years", "2026-10-21T15:04:05Z", NOW), is(false));
        assertThat(matchesAt("before:now +999999999 years", "2026-10-21T15:04:05Z", NOW), is(false));
    }

    // ----- negation -----

    @Test
    public void shouldNegate() {
        assertThat(matches("!before:2026-10-21T15:00:00Z", "2026-10-21T15:00:00Z"), is(true));
        assertThat(matches("!before:2026-10-21T15:00:00Z", "2026-10-21T14:00:00Z"), is(false));
    }

    @Test
    public void shouldNotMatchAnUnreadableValueEvenWhenNegated() {
        for (String actual : new String[]{"abc", "", "2026-02-30", "now"}) {
            assertThat(actual, matches("!before:2026-10-21T15:00:00Z", actual), is(false));
            assertThat(actual, matches("!after:now", actual), is(false));
        }
    }

    // ----- a plain string is never a date-time matcher -----

    @Test
    public void shouldKeepPlainStringsThatLookLikeDateTimeMatchersLiteral() {
        RegexStringMatcher matcher = new RegexStringMatcher(new MockServerLogger(), false);
        for (String literal : new String[]{"before:2026-01-01", "after:now -1 days", "equalToDateTime:2026-10-21T15:00:00Z"}) {
            assertThat(literal, matcher.matches(string(literal), string(literal)), is(true));
            assertThat(literal, matcher.matches(string(literal), string("2020-01-01T00:00:00Z")), is(false));
        }
    }

    // ----- invalid matchers are rejected when built -----

    @Test
    public void shouldRejectInvalidMatchersWithAReason() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> dateTime("after:")).getMessage(), containsString("no expected date-time"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> dateTime("after:tomorrow")).getMessage(), containsString("cannot read expected \"tomorrow\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> dateTime("after:2026-02-30")).getMessage(), containsString("cannot read expected"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> dateTime("after:now +3 fortnights")).getMessage(), containsString("unknown unit \"fortnights\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> dateTime("after:nowish")).getMessage(), containsString("invalid date offset"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> dateTime("after:now;zone=Mars/Olympus")).getMessage(), containsString("unknown zone"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> dateTime("after:now;truncateActual=startOfWeek")).getMessage(), containsString("unknown truncation"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> dateTime("after:now;actualFormat=yyyy-MM-dd{")).getMessage(), containsString("invalid actualFormat pattern"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> NottableDateTimeString.dateTime(null, "now", null, null, null, null, null, false)).getMessage(), containsString("one of before, after or equalToDateTime"));
    }

    // ----- scope and control plane -----

    @Test
    public void shouldApplyWhereverAMatcherValueIsUsed() {
        // the fixed-value constructor is used for method / path; a matcher object applies there as a JSON schema does
        assertThat(new RegexStringMatcher(new MockServerLogger(), dateTime("after:2026-01-01"), false).matches((MatchDifference) null, string("2026-10-21")), is(true));
        assertThat(new RegexStringMatcher(new MockServerLogger(), dateTime("after:2026-01-01"), false).matches((MatchDifference) null, string("2025-10-21")), is(false));
    }

    @Test
    public void shouldMatchAnIdenticalMatcherOnlyOnTheControlPlane() {
        assertThat(new RegexStringMatcher(new MockServerLogger(), true).matches(dateTime("after:now -1 days"), dateTime("after:now -1 days")), is(true));
        assertThat(new RegexStringMatcher(new MockServerLogger(), true).matches(dateTime("after:now -1 days"), dateTime("after:now -2 days")), is(false));
        assertThat(new RegexStringMatcher(new MockServerLogger(), false).matches(dateTime("after:now -1 days"), dateTime("after:now -1 days")), is(false));
    }

    @Test
    public void shouldRecordWhyAValueDidNotMatch() {
        MatchDifference difference = new MatchDifference(true, request());
        difference.currentField(MatchDifference.Field.HEADERS);
        new RegexStringMatcher(new MockServerLogger(), false).matches(new MockServerLogger(), difference, dateTime("after:2026-01-01"), string("not a date"));
        assertThat(difference.getDifferences(MatchDifference.Field.HEADERS).toString(), containsString("not a date-time"));
    }
}
