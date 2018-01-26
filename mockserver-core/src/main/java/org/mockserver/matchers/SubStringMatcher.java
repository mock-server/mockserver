package org.mockserver.matchers;

import org.apache.commons.lang3.StringUtils;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class SubStringMatcher extends BodyMatcher<NottableString> {
    private static final String[] excludedFields = {"mockserverLogger"};
    private final MockServerLogger mockServerLogger;
    private final NottableString matcher;

    public SubStringMatcher(MockServerLogger mockServerLogger, String matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = string(matcher);
    }

    public SubStringMatcher(MockServerLogger mockServerLogger, NottableString matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
    }

    public static boolean matches(String matcher, String matched, boolean ignoreCase) {
        boolean result = false;

        if (StringUtils.isEmpty(matcher)) {
            result = true;
        } else if (matched != null) {
            if (StringUtils.contains(matched, matcher)) {
                result = true;
            }
            // case insensitive comparison is mainly to improve matching in web containers like Tomcat that convert header names to lower case
            if (ignoreCase) {
                if (StringUtils.containsIgnoreCase(matched, matcher)) {
                    result = true;
                }
            }
        }

        return result;
    }

    public boolean matches(final HttpRequest context, String matched) {
        return matches(context, string(matched));
    }

    public boolean matches(final HttpRequest context, NottableString matched) {
        boolean result = false;

        if (matches(matcher.getValue(), matched.getValue(), false)) {
            result = true;
        }

        if (!result) {
            mockServerLogger.trace(context, "Failed to match [{}] with [{}]", matched, this.matcher);
        }

        return matched.isNot() != (matcher.isNot() != (not != result));
    }

    @Override
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
