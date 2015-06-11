package org.mockserver.matchers;

import com.google.common.base.Strings;
import org.mockserver.model.NottableString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.PatternSyntaxException;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class RegexStringMatcher extends BodyMatcher<NottableString> {
    private static final Logger logger = LoggerFactory.getLogger(RegexStringMatcher.class);
    private final NottableString matcher;

    public RegexStringMatcher(String matcher) {
        this.matcher = string(matcher);
    }

    public RegexStringMatcher(NottableString matcher) {
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
                // match as regex - matcher -> matched
                try {
                    if (matched.matches(matcher)) {
                        result = true;
                    }
                } catch (PatternSyntaxException pse) {
                    logger.trace("Error while matching regex [" + matcher + "] for string [" + matched + "] " + pse.getMessage());
                }
                // match as regex - matched -> matcher
                try {
                    if (matcher.matches(matched)) {
                        result = true;
                    }
                } catch (PatternSyntaxException pse) {
                    logger.trace("Error while matching regex [" + matched + "] for string [" + matcher + "] " + pse.getMessage());
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
                        logger.trace("Error while matching regex [" + matcher.toLowerCase() + "] for string [" + matched.toLowerCase() + "] " + pse.getMessage());
                    }
                    // match as regex - matched -> matcher
                    try {
                        if (matcher.toLowerCase().matches(matched.toLowerCase())) {
                            result = true;
                        }
                    } catch (PatternSyntaxException pse) {
                        logger.trace("Error while matching regex [" + matched.toLowerCase() + "] for string [" + matcher.toLowerCase() + "] " + pse.getMessage());
                    }
                }
            }
        }

        return result;
    }

    public boolean matches(String matched) {
        return matches(string(matched));
    }

    public boolean matches(NottableString matched) {
        boolean result = false;

        if (matches(matcher.getValue(), matched.getValue(), false)) {
            result = true;
        }

        if (!result) {
            logger.trace("Failed to match [{}] with [{}]", matched, this.matcher);
        }

        return (matcher.isNot() || matched.isNot()) != reverseResultIfNot(result);
    }
}
