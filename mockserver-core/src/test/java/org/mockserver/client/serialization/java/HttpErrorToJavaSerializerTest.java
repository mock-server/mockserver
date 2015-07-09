package org.mockserver.client.serialization.java;

import org.junit.Test;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.HttpError;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class HttpErrorToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithForwardAsJava() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "        error()" + System.getProperty("line.separator") +
                        "                .withDelay(new Delay(TimeUnit.MILLISECONDS, 100))" + System.getProperty("line.separator") +
                        "                .withDropConnection(true)" + System.getProperty("line.separator") +
                        "                .withResponseBytes(Base64Converter.base64StringToBytes(\"" + Base64Converter.bytesToBase64String("example_bytes".getBytes()) + "\"))",
                new HttpErrorToJavaSerializer().serializeAsJava(1,
                        new HttpError()
                                .withDelay(TimeUnit.MILLISECONDS, 100)
                                .withDropConnection(true)
                                .withResponseBytes("example_bytes".getBytes())
                )
        );
    }

}
