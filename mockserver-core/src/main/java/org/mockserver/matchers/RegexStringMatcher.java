package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;

import java.util.regex.PatternSyntaxException;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.model.NottableString.string;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
public class RegexStringMatcher extends BodyMatcher<NottableString> {

    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final NottableString matcher;
    private final boolean controlPlaneMatcher;

    public RegexStringMatcher(MockServerLogger mockServerLogger, boolean controlPlaneMatcher) {
        this.mockServerLogger = mockServerLogger;
        this.controlPlaneMatcher = controlPlaneMatcher;
        this.matcher = null;
    }

    RegexStringMatcher(MockServerLogger mockServerLogger, NottableString matcher, boolean controlPlaneMatcher) {
        this.mockServerLogger = mockServerLogger;
        this.controlPlaneMatcher = controlPlaneMatcher;
        this.matcher = matcher;
    }

    public boolean matches(String matched) {
        return matches(null, string(matched));
    }

    public boolean matches(final HttpRequest context, NottableString matched) {
        boolean result = false;

        if (matcher == null || matches(matcher.getValue(), matched.getValue(), false)) {
            result = true;
        }

        if (!result) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.DEBUG)
                    .setLogLevel(DEBUG)
                    .setHttpRequest(context)
                    .setMessageFormat("failed to perform regex match{}with{}")
                    .setArguments(matched, this.matcher)
            );
        }

        return matched.isNot() == (matcher.isNot() == (not != result));
    }

    public boolean matches(NottableString matcher, NottableString matched, boolean ignoreCase) {
        if (matcher.isNot() && matched.isNot()) {
            // mutual notted control plane match
            return matchesInternal(matcher, matched, ignoreCase);
        } else {
            // data plane & control plan match
            return (matcher.isNot() || matched.isNot()) ^ matchesInternal(matcher, matched, ignoreCase);
        }
    }

    private boolean matchesInternal(NottableString matcher, NottableString matched, boolean ignoreCase) {
        if (matcher.isBlank()) {
            return true;
        } else if (matched.getValue() != null) {
            // match as exact string
            if (matched.getValue().equals(matcher.getValue())) {
                return true;
            }

            // match as regex - matcher -> matched (data plane or control plane)
            try {
                if (matcher.matches(matched.getValue())) {
                    return true;
                }
            } catch (PatternSyntaxException pse) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.TRACE)
                        .setLogLevel(TRACE)
                        .setMessageFormat("error while matching regex [" + matcher + "] for string [" + matched + "] " + pse.getMessage())
                );
            }
            // match as regex - matched -> matcher (control plane only)
            try {
                if (controlPlaneMatcher && matched.matches(matcher.getValue())) {
                    return true;
                }
            } catch (PatternSyntaxException pse) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.TRACE)
                        .setLogLevel(TRACE)
                        .setMessageFormat("error while matching regex [" + matched + "] for string [" + matcher + "] " + pse.getMessage())
                );
            }
            // case insensitive comparison is mainly to improve matching in web containers like Tomcat that convert header names to lower case
            if (ignoreCase) {
                // match as exact string lower-case
                if (matcher.getValue().equalsIgnoreCase(matched.getValue())) {
                    return true;
                }
                // match as regex - matcher -> matched (data plane or control plane)
                try {
                    if (matcher.matchesIgnoreCase(matched.getValue())) {
                        return true;
                    }
                } catch (PatternSyntaxException pse) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(LogEntry.LogMessageType.TRACE)
                            .setLogLevel(TRACE)
                            .setMessageFormat("error while matching regex [" + matcher + "] for string [" + matched + "] and ignoring case " + pse.getMessage())
                    );
                }
                // match as regex - matched -> matcher (control plane only)
                try {
                    if (controlPlaneMatcher && matched.matchesIgnoreCase(matcher.getValue())) {
                        return true;
                    }
                } catch (PatternSyntaxException pse) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(LogEntry.LogMessageType.TRACE)
                            .setLogLevel(TRACE)
                            .setMessageFormat("error while matching regex [" + matched + "] for string [" + matcher + "] and ignoring case " + pse.getMessage())
                    );
                }
            }
        }

        return false;
    }

    public boolean matches(String matcher, String matched, boolean ignoreCase) {
        if (isBlank(matcher)) {
            return true;
        } else if (matched != null) {
            // match as exact string
            if (matched.equals(matcher)) {
                return true;
            }

            // match as regex - matcher -> matched (data plane or control plane)
            try {
                if (matched.matches(matcher)) {
                    return true;
                }
            } catch (PatternSyntaxException pse) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.TRACE)
                        .setLogLevel(TRACE)
                        .setMessageFormat("error while matching regex [" + matcher + "] for string [" + matched + "] " + pse.getMessage())
                );
            }
            // match as regex - matched -> matcher (control plane only)
            try {
                if (controlPlaneMatcher && matcher.matches(matched)) {
                    return true;
                }
            } catch (PatternSyntaxException pse) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.TRACE)
                        .setLogLevel(TRACE)
                        .setMessageFormat("error while matching regex [" + matched + "] for string [" + matcher + "] " + pse.getMessage())
                );
            }
            // case insensitive comparison is mainly to improve matching in web containers like Tomcat that convert header names to lower case
            if (ignoreCase) {
                // match as exact string lower-case
                if (matched.equalsIgnoreCase(matcher)) {
                    return true;
                }
                // match as regex - matcher -> matched (data plane or control plane)
                try {
                    if (matched.toLowerCase().matches(matcher.toLowerCase())) {
                        return true;
                    }
                } catch (PatternSyntaxException pse) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(LogEntry.LogMessageType.TRACE)
                            .setLogLevel(TRACE)
                            .setMessageFormat("error while matching regex [" + matcher.toLowerCase() + "] for string [" + matched.toLowerCase() + "] " + pse.getMessage())
                    );
                }
                // match as regex - matched -> matcher (control plane only)
                try {
                    if (controlPlaneMatcher && matcher.toLowerCase().matches(matched.toLowerCase())) {
                        return true;
                    }
                } catch (PatternSyntaxException pse) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(LogEntry.LogMessageType.TRACE)
                            .setLogLevel(TRACE)
                            .setMessageFormat("error while matching regex [" + matched.toLowerCase() + "] for string [" + matcher.toLowerCase() + "] " + pse.getMessage())
                    );
                }
            }
        }

        return false;
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
