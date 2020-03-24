package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.buffer.ByteBuf;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.BinaryArrayFormatter;
import org.mockserver.logging.MockServerLogger;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.mockserver.character.Character.NEW_LINE;
import static org.slf4j.event.Level.DEBUG;

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

        if (!result) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setMatchDifference(context)
                    .setMessageFormat("binary match failed expected:{}found:{}")
                    .setArguments(BinaryArrayFormatter.byteArrayToString(this.matcher), BinaryArrayFormatter.byteArrayToString(matched))
            );
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
