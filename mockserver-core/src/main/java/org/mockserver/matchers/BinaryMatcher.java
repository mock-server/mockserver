package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.logging.BinaryArrayFormatter;
import org.mockserver.logging.MockServerLogger;

import java.util.Arrays;

/**
 * @author jamesdbloom
 */
public class BinaryMatcher extends BodyMatcher<byte[]> {
    private static final String[] excludedFields = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final byte[] matcher;

    BinaryMatcher(MockServerLogger mockServerLogger, byte[] matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
    }

    public boolean matches(final MatchDifference context, byte[] matched) {
        boolean result = false;

        if (matcher == null || matcher.length == 0 || Arrays.equals(matcher, matched)) {
            result = true;
        }

        if (!result && context != null) {
            context.addDifference(mockServerLogger, "binary match failed expected:{}found:{}", BinaryArrayFormatter.byteArrayToString(this.matcher), BinaryArrayFormatter.byteArrayToString(matched));
        }

        return not != result;
    }

    public boolean isBlank() {
        return matcher == null || matcher.length == 0;
    }

    @Override
    @JsonIgnore
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
