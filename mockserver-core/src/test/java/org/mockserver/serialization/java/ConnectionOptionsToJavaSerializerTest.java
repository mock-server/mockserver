package org.mockserver.serialization.java;

import org.junit.Test;
import org.mockserver.model.ConnectionOptions;

import static org.junit.Assert.assertEquals;
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
                "                .withKeepAliveOverride(false)" + NEW_LINE +
                "                .withCloseSocket(true)",
            new ConnectionOptionsToJavaSerializer().serialize(1,
                new ConnectionOptions()
                    .withSuppressContentLengthHeader(false)
                    .withContentLengthHeaderOverride(10)
                    .withSuppressConnectionHeader(true)
                    .withKeepAliveOverride(false)
                    .withCloseSocket(true)
            )
        );
    }

}
