package org.mockserver.serialization.java;

import org.junit.Test;
import org.mockserver.model.HttpError;
import org.mockserver.serialization.Base64Converter;

import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpErrorToJavaSerializerTest {

    private final Base64Converter base64Converter = new Base64Converter();

    @Test
    public void shouldSerializeFullObjectWithForwardAsJava() {
        assertEquals(NEW_LINE +
                "        error()" + NEW_LINE +
                "                .withDelay(new Delay(TimeUnit.MILLISECONDS, 100))" + NEW_LINE +
                "                .withDropConnection(true)" + NEW_LINE +
                "                .withResponseBytes(new Base64Converter().base64StringToBytes(\"" + base64Converter.bytesToBase64String("example_bytes".getBytes(UTF_8)) + "\"))",
            new HttpErrorToJavaSerializer().serialize(1,
                new HttpError()
                    .withDelay(TimeUnit.MILLISECONDS, 100)
                    .withDropConnection(true)
                    .withResponseBytes("example_bytes".getBytes(UTF_8))
            )
        );
    }

}
