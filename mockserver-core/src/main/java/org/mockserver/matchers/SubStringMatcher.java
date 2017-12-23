package org.mockserver.matchers;

import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.NottableString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class SubStringMatcher extends BodyMatcher<NottableString> {
    private static Logger logger = LoggerFactory.getLogger(SubStringMatcher.class);
    private final NottableString matcher;

    public SubStringMatcher(String matcher) {
        this.matcher = string(matcher);
    }

    public SubStringMatcher(NottableString matcher) {
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
