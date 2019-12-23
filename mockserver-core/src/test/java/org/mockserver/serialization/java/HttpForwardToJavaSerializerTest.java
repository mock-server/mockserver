package org.mockserver.serialization.java;

import org.junit.Test;
import org.mockserver.model.HttpForward;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpForwardToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithForwardAsJava() {
        assertEquals(NEW_LINE +
                        "        forward()" + NEW_LINE +
                        "                .withHost(\"some_host\")" + NEW_LINE +
                        "                .withPort(9090)" + NEW_LINE +
                        "                .withScheme(HttpForward.Scheme.HTTPS)" + NEW_LINE +
                "                .withDelay(new Delay(TimeUnit.MILLISECONDS, 100))",
                new HttpForwardToJavaSerializer().serialize(1,
                        new HttpForward()
                                .withHost("some_host")
                                .withPort(9090)
                                .withScheme(HttpForward.Scheme.HTTPS)
                            .withDelay(TimeUnit.MILLISECONDS, 100)
                )
        );
    }

}
