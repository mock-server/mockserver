package org.mockserver.matchers;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class ExactStringMatcher extends BodyMatcher implements Matcher<String> {
    private static Logger logger = LoggerFactory.getLogger(ExactStringMatcher.class);
    private final String matcher;

    public ExactStringMatcher(String matcher) {
        this.matcher = matcher;
    }

    public static boolean matches(String matcher, String matched, boolean ignoreCase) {
        boolean result = false;

        if (Strings.isNullOrEmpty(matcher)) {
            result = true;
        } else if (matched != null) {
            if (matched.equals(matcher)) {
                result = true;
            }
            // case insensitive comparison is mainly to improve matching in web containers like Tomcat that convert header names to lower case
            if (ignoreCase) {
                if (matched.equalsIgnoreCase(matcher)) {
                    result = true;
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
        return new String[]{"logger", "xpathExpression"};
    }
}
