package org.mockserver.matchers;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.PatternSyntaxException;

/**
 * @author jamesdbloom
 */
public class RegexStringMatcher extends BodyMatcher<String> implements Matcher<String> {
    private static Logger logger = LoggerFactory.getLogger(RegexStringMatcher.class);
    private final String matcher;

    public RegexStringMatcher(String matcher) {
        this.matcher = matcher;
    }

    public static boolean matches(String matcher, String matched, boolean ignoreCase) {
        boolean result = false;

        if (Strings.isNullOrEmpty(matcher)) {
            result = true;
        } else if (matched != null) {
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
            if (ignoreCase) {
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

        return result;
    }

    public boolean matches(String matched) {
        boolean result = false;

        if (matches(matcher, matched, false)) {
            result = true;
        }

        if (!result) {
            logger.trace("Failed to match [{}] with [{}]", matched, this.matcher);
        }
        return result;
    }

    @Override
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return new String[]{"logger"};
    }
}
