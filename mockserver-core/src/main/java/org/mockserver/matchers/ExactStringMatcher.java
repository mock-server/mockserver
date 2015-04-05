package org.mockserver.matchers;

import com.google.common.base.Strings;
import org.mockserver.model.NottableString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class ExactStringMatcher extends BodyMatcher<NottableString> {
    private static Logger logger = LoggerFactory.getLogger(ExactStringMatcher.class);
    private final NottableString matcher;

    public ExactStringMatcher(String matcher) {
        this.matcher = string(matcher);
    }

    public ExactStringMatcher(NottableString matcher) {
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

        return matcher.isNot() != reverseResultIfNot(result);
    }

    @Override
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return new String[]{"logger"};
    }
}
