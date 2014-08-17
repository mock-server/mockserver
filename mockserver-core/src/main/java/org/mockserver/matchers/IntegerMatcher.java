package org.mockserver.matchers;

/**
 * @author jamesdbloom
 */
public class IntegerMatcher extends BodyMatcher<Integer> implements Matcher<Integer> {
    private final Integer matcher;

    public IntegerMatcher(Integer matcher) {
        this.matcher = matcher;
    }

    public boolean matches(Integer matched) {
        boolean result = false;

        if (matcher == null) {
            result = true;
        } else if (matched != null) {
            result = matcher.equals(matched);
        }

        if (!result) {
            logger.trace("Failed to match [{}] with [{}]", matched, this.matcher);
        }
        return result;
    }
}
