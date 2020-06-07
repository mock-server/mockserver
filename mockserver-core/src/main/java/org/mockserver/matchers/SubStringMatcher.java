package org.mockserver.matchers;

import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.NottableString;

import static org.apache.commons.lang3.StringUtils.*;
import static org.mockserver.model.NottableString.string;
import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class SubStringMatcher extends BodyMatcher<NottableString> {
    private static final String[] excludedFields = {"mockserverLogger"};
    private final MockServerLogger mockServerLogger;
    private final NottableString matcher;

    SubStringMatcher(MockServerLogger mockServerLogger, NottableString matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
    }

    public static boolean matches(String matcher, String matched, boolean ignoreCase) {
        if (isEmpty(matcher)) {
            return true;
        } else if (matched != null) {
            if (contains(matched, matcher)) {
                return true;
            }
            // case insensitive comparison is mainly to improve matching in web containers like Tomcat that convert header names to lower case
            if (ignoreCase) {
                return containsIgnoreCase(matched, matcher);
            }
        }

        return false;
    }

    public boolean matches(final MatchDifference context, String matched) {
        return matches(context, string(matched));
    }

    public boolean matches(final MatchDifference context, NottableString matched) {
        boolean result = false;

        if (matches(matcher.getValue(), matched.getValue(), false)) {
            result = true;
        }

        if (!result && context != null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setMatchDifference(context)
                    .setMessageFormat("substring match failed expected:{}found:{}")
                    .setArguments(this.matcher, matched)
            );
        }

        return matched.isNot() == (matcher.isNot() == (not != result));
    }

    public boolean isBlank() {
        return matcher == null || StringUtils.isBlank(matcher.getValue());
    }

    @Override
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
