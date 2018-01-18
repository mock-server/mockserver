package org.mockserver.client.serialization.java;

import org.junit.Test;
import org.mockserver.model.HttpForward;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpForwardToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithForwardAsJava() throws IOException {
        assertEquals(NEW_LINE +
                        "        forward()" + NEW_LINE +
                        "                .withHost(\"some_host\")" + NEW_LINE +
                        "                .withPort(9090)" + NEW_LINE +
                        "                .withScheme(HttpForward.Scheme.HTTPS)",
                new HttpForwardToJavaSerializer().serialize(1,
                        new HttpForward()
                                .withHost("some_host")
                                .withPort(9090)
                                .withScheme(HttpForward.Scheme.HTTPS)
                )
        );
    }

}
