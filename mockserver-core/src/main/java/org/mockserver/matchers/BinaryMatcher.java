package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;

import java.util.Arrays;

/**
 * @author jamesdbloom
 */
public class BinaryMatcher extends BodyMatcher<byte[]> {
    private static final String[] excludedFields = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final byte[] matcher;

    public BinaryMatcher(MockServerLogger mockServerLogger, byte[] matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
    }

    public boolean matches(final HttpRequest context, byte[] matched) {
        boolean result = false;

        if (matcher == null || matcher.length == 0 || Arrays.equals(matcher, matched)) {
            result = true;
        }

        if (!result) {
            mockServerLogger.trace(context, "Failed to perform binary match [{}] with [{}] because {}", (Object) matched);
        }

        return not != result;
    }

    @Override
    @JsonIgnore
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
