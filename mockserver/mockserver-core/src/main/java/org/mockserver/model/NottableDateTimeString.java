package org.mockserver.model;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.DateTimeComparisonMatcher;
import org.mockserver.matchers.DateTimeComparisonMatcher.Operator;
import org.mockserver.matchers.MatchDifference;

/**
 * A date-time comparison matcher value, like WireMock's {@code before}, {@code after} and
 * {@code equalToDateTime}. In JSON it is the object form of a value, for example
 * {@code {"after": "now -1 hours", "actualFormat": "rfc1123"}}; a plain string is never read as one.
 *
 * <pre>{@code
 *   request()
 *       .withHeader(string("X-Expires"), after("now +1 days").withActualFormat("rfc1123"))
 *       .withQueryStringParameter(string("from"), before("2026-01-01T00:00:00Z"))
 * }</pre>
 *
 * <p>The expected value is {@code now}, {@code now} with an offset ({@code now -2 hours},
 * {@code now +1 months}) or a date-time (ISO-8601, RFC-1123 or epoch seconds/millis). It is parsed once,
 * here; each step throws {@link IllegalArgumentException} at once when a value is not valid. An actual
 * value that cannot be read as a date-time never matches, even when the matcher is negated.
 */
public class NottableDateTimeString extends NottableMatcherString {

    private final Operator operator;
    private final String expected;
    private final String actualFormat;
    private final String truncateExpected;
    private final String truncateActual;
    private final Boolean applyTruncationLast;
    private final String zone;
    private final DateTimeComparisonMatcher compiled;

    private NottableDateTimeString(Operator operator, String expected, String actualFormat, String truncateExpected, String truncateActual, Boolean applyTruncationLast, String zone, Boolean not, ParameterStyle parameterStyle, String schemaType) {
        super(describe(operator, expected, actualFormat, truncateExpected, truncateActual, applyTruncationLast, zone), not, parameterStyle, schemaType);
        this.operator = operator;
        this.expected = expected;
        this.actualFormat = actualFormat;
        this.truncateExpected = truncateExpected;
        this.truncateActual = truncateActual;
        this.applyTruncationLast = applyTruncationLast;
        this.zone = zone;
        this.compiled = DateTimeComparisonMatcher.compile(operator, expected, actualFormat, truncateExpected, truncateActual, applyTruncationLast, zone);
    }

    /**
     * @throws IllegalArgumentException when the expected value or an option is not valid
     */
    public static NottableDateTimeString dateTime(Operator operator, String expected, String actualFormat, String truncateExpected, String truncateActual, Boolean applyTruncationLast, String zone, Boolean not) {
        return new NottableDateTimeString(operator, expected, actualFormat, truncateExpected, truncateActual, applyTruncationLast, zone, not, null, null);
    }

    /**
     * Matches a value earlier than {@code expected}.
     */
    public static NottableDateTimeString before(String expected) {
        return dateTime(Operator.BEFORE, expected, null, null, null, null, null, false);
    }

    /**
     * Matches a value later than {@code expected}.
     */
    public static NottableDateTimeString after(String expected) {
        return dateTime(Operator.AFTER, expected, null, null, null, null, null, false);
    }

    /**
     * Matches a value at the same instant as {@code expected}.
     */
    public static NottableDateTimeString equalToDateTime(String expected) {
        return dateTime(Operator.EQUAL_TO_DATE_TIME, expected, null, null, null, null, null, false);
    }

    /**
     * How the actual value is written: a {@link java.time.format.DateTimeFormatter} pattern such as
     * {@code dd/MM/yyyy}, or {@code iso}, {@code rfc1123}, {@code epochSeconds} or {@code epochMillis}.
     * By default any of the named forms is read.
     */
    public NottableDateTimeString withActualFormat(String actualFormat) {
        return new NottableDateTimeString(operator, expected, actualFormat, truncateExpected, truncateActual, applyTruncationLast, zone, isNot(), getParameterStyle(), getSchemaType());
    }

    /**
     * Truncates the expected value, such as {@code first day of month} or {@code startOfDay}.
     */
    public NottableDateTimeString withTruncateExpected(String truncateExpected) {
        return new NottableDateTimeString(operator, expected, actualFormat, truncateExpected, truncateActual, applyTruncationLast, zone, isNot(), getParameterStyle(), getSchemaType());
    }

    /**
     * Truncates the actual value, such as {@code first day of month} or {@code startOfDay}.
     */
    public NottableDateTimeString withTruncateActual(String truncateActual) {
        return new NottableDateTimeString(operator, expected, actualFormat, truncateExpected, truncateActual, applyTruncationLast, zone, isNot(), getParameterStyle(), getSchemaType());
    }

    /**
     * When {@code true}, {@code now} is truncated after its offset is added instead of before.
     */
    public NottableDateTimeString withApplyTruncationLast(Boolean applyTruncationLast) {
        return new NottableDateTimeString(operator, expected, actualFormat, truncateExpected, truncateActual, applyTruncationLast, zone, isNot(), getParameterStyle(), getSchemaType());
    }

    /**
     * The time zone for values without one, for truncation and for day, month and year offsets, such as
     * {@code Europe/London}; UTC by default.
     */
    public NottableDateTimeString withZone(String zone) {
        return new NottableDateTimeString(operator, expected, actualFormat, truncateExpected, truncateActual, applyTruncationLast, zone, isNot(), getParameterStyle(), getSchemaType());
    }

    /**
     * Negates the comparison; an unreadable actual value still does not match.
     */
    public NottableDateTimeString withNot(Boolean not) {
        return new NottableDateTimeString(operator, expected, actualFormat, truncateExpected, truncateActual, applyTruncationLast, zone, not, getParameterStyle(), getSchemaType());
    }

    @Override
    NottableString copyWith(ParameterStyle parameterStyle, String schemaType) {
        return new NottableDateTimeString(operator, expected, actualFormat, truncateExpected, truncateActual, applyTruncationLast, zone, isNot(), parameterStyle, schemaType);
    }

    @Override
    public boolean matches(MockServerLogger mockServerLogger, MatchDifference context, String value) {
        Boolean comparison = compiled.compare(value);
        boolean result = comparison != null && isNot() != comparison;
        if (!result && context != null) {
            context.addDifference(mockServerLogger, comparison == null ? "date-time match failed expected:{}found value that is not a date-time:{}" : "date-time match failed expected:{}found:{}", this, value);
        }
        return result;
    }

    public Operator getOperator() {
        return operator;
    }

    public String getExpected() {
        return expected;
    }

    public String getActualFormat() {
        return actualFormat;
    }

    public String getTruncateExpected() {
        return truncateExpected;
    }

    public String getTruncateActual() {
        return truncateActual;
    }

    public Boolean getApplyTruncationLast() {
        return applyTruncationLast;
    }

    public String getZone() {
        return zone;
    }

    // the value used for equality, hashing and log messages; matching never reads it
    private static String describe(Operator operator, String expected, String actualFormat, String truncateExpected, String truncateActual, Boolean applyTruncationLast, String zone) {
        StringBuilder description = new StringBuilder(operator == null ? "" : operator.fieldName()).append(' ').append(expected);
        option(description, "actualFormat", actualFormat);
        option(description, "truncateExpected", truncateExpected);
        option(description, "truncateActual", truncateActual);
        option(description, "applyTruncationLast", applyTruncationLast == null ? null : applyTruncationLast.toString());
        option(description, "zone", zone);
        return description.toString();
    }

    private static void option(StringBuilder description, String name, String value) {
        if (value != null) {
            description.append(", ").append(name).append(' ').append(value);
        }
    }
}
