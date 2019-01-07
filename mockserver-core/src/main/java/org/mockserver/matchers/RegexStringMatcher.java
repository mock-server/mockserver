package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Strings;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;

import java.util.regex.PatternSyntaxException;

import static org.mockserver.logging.MockServerLogger.MOCK_SERVER_LOGGER;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class RegexStringMatcher extends BodyMatcher<NottableString> {
    private static final String[] excludedFields = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final NottableString matcher;

    public RegexStringMatcher(MockServerLogger mockServerLogger, String matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = string(matcher);
    }

    public RegexStringMatcher(MockServerLogger mockServerLogger, NottableString matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
    }

    public static boolean matches(NottableString matcher, NottableString matched, boolean ignoreCase) {
        return matcher.isNot() != (matched.isNot() != matches(matcher.getValue(), matched.getValue(), ignoreCase));
    }

    public static boolean matches(String matcher, String matched, boolean ignoreCase) {
        boolean result = false;

        if (Strings.isNullOrEmpty(matcher)) {
            result = true;
        } else if (matched != null) {
            // match as exact string
            if (matched.equals(matcher)) {
                result = true;
            }
            if (!result) {
                try {
                    if (matched.matches(matcher)) {
                        result = true;
                    }
                } catch (PatternSyntaxException pse) {
                    MOCK_SERVER_LOGGER.trace("Error while matching regex [" + matcher + "] for string [" + matched + "] " + pse.getMessage());
                }
                // match as regex - matched -> matcher
                try {
                    if (matcher.matches(matched)) {
                        result = true;
                    }
                } catch (PatternSyntaxException pse) {
                    MOCK_SERVER_LOGGER.trace("Error while matching regex [" + matched + "] for string [" + matcher + "] " + pse.getMessage());
                }
                // case insensitive comparison is mainly to improve matching in web containers like Tomcat that convert header names to lower case
                if (!result && ignoreCase) {
                    // match as exact string lower-case
                    if (matched.equalsIgnoreCase(matcher)) {
                        result = true;
                    }
                    // match as regex - matcher -> matched
                    try {
                        if (matched.toLowerCase().matches(matcher.toLowerCase())) {
                            result = true;
                        }
                    } catch (PatternSyntaxException pse) {
                        MOCK_SERVER_LOGGER.trace("Error while matching regex [" + matcher.toLowerCase() + "] for string [" + matched.toLowerCase() + "] " + pse.getMessage());
                    }
                    // match as regex - matched -> matcher
                    try {
                        if (matcher.toLowerCase().matches(matched.toLowerCase())) {
                            result = true;
                        }
                    } catch (PatternSyntaxException pse) {
                        MOCK_SERVER_LOGGER.trace("Error while matching regex [" + matched.toLowerCase() + "] for string [" + matcher.toLowerCase() + "] " + pse.getMessage());
                    }
                }
            }
        }

        return result;
    }

    public boolean matches(String matched) {
        return matches(null, string(matched));
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
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
