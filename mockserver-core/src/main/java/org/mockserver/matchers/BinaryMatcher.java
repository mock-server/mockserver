package org.mockserver.matchers;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;

import java.util.Arrays;

/**
 * @author jamesdbloom
 */
public class BinaryMatcher extends BodyMatcher<byte[]> {
    private final MockServerLogger mockServerLogger;
    private final byte[] matcher;

    public BinaryMatcher(MockServerLogger mockServerLogger, byte[] matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
    }

    public boolean matches(HttpRequest context, byte[] matched) {
        boolean result = false;

        if (matcher == null || matcher.length == 0 || Arrays.equals(matcher, matched)) {
            result = true;
        }

        if (!result) {
            mockServerLogger.trace(context, "Failed to perform binary match [{}] with [{}] because {}", matched);
        }

        return reverseResultIfNot(result);
    }

    @Override
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return new String[]{"logger"};
    }
}
