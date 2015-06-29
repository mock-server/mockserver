package org.mockserver.matchers;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class BooleanMatcher extends ObjectWithReflectiveEqualsHashCodeToString implements Matcher<Boolean> {

    private static Logger logger = LoggerFactory.getLogger(BooleanMatcher.class);

    private final Boolean matcher;

    public BooleanMatcher(Boolean matcher) {
        this.matcher = matcher;
    }

    @Override
    public boolean matches(Boolean matched) {
        boolean result = false;

        if (matcher == null) {
            result = true;
        } else if (matched != null) {
            result = matched == matcher;
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
