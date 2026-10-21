package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.log.model.SensitiveLogValue;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.NottableMatcherString;
import org.mockserver.model.NottableString;

import java.util.regex.PatternSyntaxException;

import static org.mockserver.model.NottableString.string;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.WARN;

/**
 * @author jamesdbloom
 */
public class RegexStringMatcher extends BodyMatcher<NottableString> {

    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final NottableString matcher;
    private final boolean controlPlaneMatcher;
    // Numeric comparison operators (e.g. "> 60") are only honoured for key/value map
    // matching (headers, cookies, query-string and form/multipart parameters), which use
    // the no-fixed-value constructor. The fixed-value constructor is used for method, path,
    // regex body and reason-phrase matching, where a value like "== 5" must keep its
    // original exact/regex string semantics — so numeric comparison stays disabled there.
    private final boolean numericComparison;
    // When false (the default for every pre-existing caller, including all key/value map
    // matching for headers/cookies/query/params) matching is case-insensitive — byte-for-byte
    // the original behaviour. When true (only the method/path/string-body matchers, and only
    // when the opt-in matchExactCase flag is enabled) the equalsIgnoreCase fallback is skipped
    // and the regex is compiled without CASE_INSENSITIVE|UNICODE_CASE, so matching is exact-case.
    private final boolean caseSensitive;

    public RegexStringMatcher(MockServerLogger mockServerLogger, boolean controlPlaneMatcher) {
        this(mockServerLogger, controlPlaneMatcher, false);
    }

    public RegexStringMatcher(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, boolean caseSensitive) {
        this.mockServerLogger = mockServerLogger;
        this.controlPlaneMatcher = controlPlaneMatcher;
        this.matcher = null;
        this.numericComparison = true;
        this.caseSensitive = caseSensitive;
    }

    RegexStringMatcher(MockServerLogger mockServerLogger, NottableString matcher, boolean controlPlaneMatcher) {
        this(mockServerLogger, matcher, controlPlaneMatcher, false);
    }

    RegexStringMatcher(MockServerLogger mockServerLogger, NottableString matcher, boolean controlPlaneMatcher, boolean caseSensitive) {
        this.mockServerLogger = mockServerLogger;
        this.controlPlaneMatcher = controlPlaneMatcher;
        this.matcher = matcher;
        this.numericComparison = false;
        this.caseSensitive = caseSensitive;
    }

    public boolean matches(String matched) {
        return matches((MatchDifference) null, string(matched));
    }

    public boolean matches(final MatchDifference context, NottableString matched) {
        boolean result = matcher == null || matches(mockServerLogger, context, matcher, matched);
        return not != result;
    }

    public boolean matches(NottableString matcher, NottableString matched) {
        return matches(mockServerLogger, null, matcher, matched);
    }

    public boolean matches(MockServerLogger mockServerLogger, MatchDifference context, NottableString matcher, NottableString matched) {
        // NottableMatcherString (JSON schema or date-time) is only ever built from the explicit object form
        // of a value, so a plain value takes exactly the same branches as it did before date-time matchers
        if (matcher instanceof NottableMatcherString && matched instanceof NottableMatcherString) {
            return controlPlaneMatcher && matchesByNottedStrings(mockServerLogger, context, matcher, matched);
        } else if (matcher instanceof NottableMatcherString) {
            return matchesBySchemas(mockServerLogger, context, (NottableMatcherString) matcher, matched);
        } else if (matched instanceof NottableMatcherString) {
            return controlPlaneMatcher && matchesBySchemas(mockServerLogger, context, (NottableMatcherString) matched, matcher);
        } else {
            return matchesByNottedStrings(mockServerLogger, context, matcher, matched);
        }
    }

    private boolean matchesByNottedStrings(MockServerLogger mockServerLogger, MatchDifference context, NottableString matcher, NottableString matched) {
        if (matcher.isNot() && matched.isNot()) {
            // mutual notted control plane match
            return matchesByStrings(mockServerLogger, context, matcher, matched);
        } else {
            // data plane & control plan match
            return (matcher.isNot() || matched.isNot()) ^ matchesByStrings(mockServerLogger, context, matcher, matched);
        }
    }

    private boolean matchesBySchemas(MockServerLogger mockServerLogger, MatchDifference context, NottableMatcherString schema, NottableString string) {
        return string.isNot() != schema.matches(mockServerLogger, context, string.getValue());
    }

    private boolean matchesByStrings(MockServerLogger mockServerLogger, MatchDifference context, NottableString matcher, NottableString matched) {
        if (matcher == null) {
            return true;
        }
        final String matcherValue = matcher.getValue();
        if (StringUtils.isBlank(matcherValue)) {
            return true;
        } else if (numericComparison && NumericComparisonMatcher.isNumericComparison(matcherValue)) {
            // match by numeric comparison operator (e.g. "> 60", "<= 30", "== 5"); for
            // not-equal use NottableString negation ("!== 5"). Only enabled for key/value
            // map matching (headers/cookies/query/params), not body/path/method/reason-phrase.
            // a non-numeric or absent actual value simply does not match and never throws
            boolean numericMatch = matched != null && NumericComparisonMatcher.matches(matcherValue, matched.getValue());
            if (!numericMatch && context != null) {
                context.addDifference(mockServerLogger, "numeric comparison match failed expected:{}found:{}", matcher, matched);
            }
            return numericMatch;
        } else if (numericComparison && AcceptHeaderMatcher.isAcceptDirective(matcherValue)) {
            // match by HTTP content-negotiation (e.g. "accept:application/json"): the actual
            // Accept header value (matched) must make the desired media type acceptable per
            // RFC 7231 (q-values, type/* and */* wildcards, preference order). Only enabled for
            // key/value map matching (headers/cookies/query/params), not body/path/method/reason-phrase.
            // a blank or non-acceptable actual value simply does not match and never throws.
            boolean acceptMatch = matched != null && AcceptHeaderMatcher.matches(matcherValue, matched.getValue());
            if (!acceptMatch && context != null) {
                context.addDifference(mockServerLogger, "accept header content-negotiation match failed expected:{}found:{}", matcher, matched);
            }
            return acceptMatch;
        } else {
            if (matched != null) {
                final String matchedValue = matched.getValue();
                if (matchedValue != null) {
                    // match as exact string
                    // The equalsIgnoreCase fallback is the default case-insensitive behaviour; it is
                    // dropped only when caseSensitive is set (opt-in matchExactCase, method/path/body only).
                    if (matchedValue.equals(matcherValue) || (!caseSensitive && matchedValue.equalsIgnoreCase(matcherValue))) {
                        return true;
                    }

                    // match as regex - matcher -> matched (data plane or control plane).
                    // Skip the timeout-protected regex path when the matcher value is a pure-ASCII
                    // literal (no regex metacharacters): NottableString.matches() does an anchored full
                    // match, so for a pure-ASCII literal the regex outcome is provably identical to the
                    // string comparison that already ran above (and failed) — hence a guaranteed
                    // non-match. This holds in both modes: case-insensitive matching compiles with
                    // CASE_INSENSITIVE|UNICODE_CASE and is equivalent to the equalsIgnoreCase that ran;
                    // case-sensitive matching (matchExactCase) compiles without them and is equivalent
                    // to the equals that ran. Non-ASCII values keep the regex path because UNICODE_CASE
                    // folding can diverge from String.equalsIgnoreCase.
                    if (!isPureAsciiLiteral(matcherValue)) {
                        try {
                            if (runRegexWithTimeout(mockServerLogger, matcher, matchedValue, caseSensitive, false)) {
                                return true;
                            }
                        } catch (PatternSyntaxException pse) {
                            if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(DEBUG)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
                                        .setMessageFormat("error while matching regex{}for string{}{}")
                                        .setArguments(matcher, SensitiveLogValue.of(matched), pse.getMessage())
                                        .setThrowable(pse)
                                );
                            }
                        }
                    }
                    // match as regex - matched -> matcher (control plane only).
                    // Same pure-ASCII-literal short-circuit as above: when the matched value is a
                    // pure-ASCII literal the anchored regex result is identical to the
                    // equalsIgnoreCase already performed, so there is nothing left to gain by
                    // entering the timeout-protected pool.
                    if (!isPureAsciiLiteral(matchedValue)) {
                        try {
                            // evaluate the reversed regex once and reuse the result for both the
                            // control-plane match decision and the DEBUG "would match" trace, so a
                            // single logical check submits a single task to the timeout-protected pool
                            // and cannot observe two divergent results under pool contention.
                            if (controlPlaneMatcher) {
                                if (runRegexWithTimeout(mockServerLogger, matched, matcherValue, caseSensitive, true)) {
                                    return true;
                                }
                            } else if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(DEBUG) && runRegexWithTimeout(mockServerLogger, matched, matcherValue, caseSensitive, true)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
                                        .setMessageFormat("matcher{}would match{}if matcher was used for control plane")
                                        .setArguments(matcher, SensitiveLogValue.of(matched))
                                );
                            }
                        } catch (PatternSyntaxException pse) {
                            if (controlPlaneMatcher) {
                                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(DEBUG)) {
                                    mockServerLogger.logEvent(
                                        new LogEntry()
                                            .setLogLevel(DEBUG)
                                            .setMessageFormat("error while matching regex{}for string{}{}")
                                            .setArguments(SensitiveLogValue.of(matched), matcher, pse.getMessage())
                                            .setThrowable(pse)
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
        if (context != null) {
            context.addDifference(mockServerLogger, "string or regex match failed expected:{}found:{}", matcher, matched);
        }

        return false;
    }

    public boolean isBlank() {
        return matcher == null || StringUtils.isBlank(matcher.getValue());
    }

    private static boolean runRegexWithTimeout(MockServerLogger mockServerLogger, NottableString pattern, String input, boolean caseSensitive, boolean patternIsMatchedValue) {
        // Prefer the live Configuration carried by the logger (the matcher graph's existing
        // configuration carrier) so a timeout set over PUT /mockserver/configuration takes effect;
        // fall back to the static store when no instance is available.
        org.mockserver.configuration.Configuration configuration =
            mockServerLogger != null ? mockServerLogger.getConfiguration() : null;
        long timeoutMillis = configuration != null
            ? configuration.regexMatchingTimeoutMillis()
            : ConfigurationProperties.regexMatchingTimeoutMillis();
        // NottableString.matches() performs an anchored full match, so a pattern the classifier
        // proves linear can run inline without the pool hand-off. caseSensitive == false means the
        // pattern is compiled CASE_INSENSITIVE, which the classifier must account for when checking
        // quantifier-alphabet disjointness. Any pattern it cannot prove safe keeps the pool.
        boolean inlineSafe = RegexComplexityClassifier.isAnchoredInlineSafe(pattern.getValue(), !caseSensitive);
        try {
            return MatchingTimeoutExecutor.callWithTimeout(
                () -> pattern.matches(input, caseSensitive),
                timeoutMillis,
                Boolean.FALSE,
                fired -> {
                    if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(WARN)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(WARN)
                                .setMessageFormat("regex evaluation timed out after {}ms for pattern:{}— treating as non-match (raise mockserver.regexMatchingTimeoutMillis or simplify the pattern to suppress this)")
                                .setArguments(fired, patternIsMatchedValue ? SensitiveLogValue.of(pattern) : pattern)
                        );
                    }
                },
                inlineSafe);
        } catch (PatternSyntaxException pse) {
            throw pse;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * A value is a "pure ASCII literal" when it contains no regex metacharacter AND every
     * character is ASCII (code point &lt;= 0x7F). For such a value the anchored, case-insensitive
     * regex match performed by {@link NottableString#matches(String)} is provably identical to the
     * {@code equalsIgnoreCase} comparison already performed by the caller, so the timeout-protected
     * regex path can be skipped entirely — a literal non-match never needs the executor pool.
     * <p>
     * Non-ASCII values are deliberately excluded even when they contain no metacharacter, because
     * {@code Pattern}'s {@code UNICODE_CASE} folding can differ from {@link String#equalsIgnoreCase},
     * so the regex path must still run for them to preserve behaviour.
     */
    private static boolean isPureAsciiLiteral(String s) {
        return !looksLikeRegex(s) && isPureAscii(s);
    }

    /**
     * Returns true when the string contains any regex metacharacter (any of:
     * {@code \ . [ ] { } ( ) * + ? ^ $ |}). A string with none of these characters cannot behave
     * as anything other than a literal when compiled as a pattern.
     */
    private static boolean looksLikeRegex(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case '\\':
                case '.':
                case '[':
                case ']':
                case '{':
                case '}':
                case '(':
                case ')':
                case '*':
                case '+':
                case '?':
                case '^':
                case '$':
                case '|':
                    return true;
                default:
                    // continue scanning
            }
        }
        return false;
    }

    private static boolean isPureAscii(String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
