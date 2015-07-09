package org.mockserver.client.serialization.java;

import org.junit.Test;
import org.mockserver.model.HttpForward;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class HttpForwardToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithForwardAsJava() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "        forward()" + System.getProperty("line.separator") +
                        "                .withHost(\"some_host\")" + System.getProperty("line.separator") +
                        "                .withPort(9090)" + System.getProperty("line.separator") +
                        "                .withScheme(HttpForward.Scheme.HTTPS)",
                new HttpForwardToJavaSerializer().serializeAsJava(1,
                        new HttpForward()
                                .withHost("some_host")
                                .withPort(9090)
                                .withScheme(HttpForward.Scheme.HTTPS)
                )
        );
    }

}
