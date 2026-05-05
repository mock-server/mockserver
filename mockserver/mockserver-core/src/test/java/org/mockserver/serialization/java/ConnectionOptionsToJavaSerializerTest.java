package org.mockserver.serialization.java;

import org.junit.Test;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.Delay;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static junit.framework.TestCase.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class ConnectionOptionsToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithForwardAsJava() {
        assertEquals(NEW_LINE +
                "        connectionOptions()" + NEW_LINE +
                "                .withSuppressContentLengthHeader(false)" + NEW_LINE +
                "                .withContentLengthHeaderOverride(10)" + NEW_LINE +
                "                .withSuppressConnectionHeader(true)" + NEW_LINE +
                "                .withChunkSize(100)" + NEW_LINE +
                "                .withKeepAliveOverride(false)" + NEW_LINE +
                "                .withCloseSocket(true)" + NEW_LINE +
                "                .withCloseSocketDelay(new Delay(TimeUnit.MILLISECONDS, 50))",
            new ConnectionOptionsToJavaSerializer().serialize(1,
                new ConnectionOptions()
                    .withSuppressContentLengthHeader(false)
                    .withContentLengthHeaderOverride(10)
                    .withSuppressConnectionHeader(true)
                    .withChunkSize(100)
                    .withKeepAliveOverride(false)
                    .withCloseSocket(true)
                    .withCloseSocketDelay(new Delay(MILLISECONDS, 50))
            )
        );
    }

}
