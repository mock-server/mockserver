package org.mockserver.client.serialization.java;

import org.junit.Test;
import org.mockserver.model.HttpClassCallback;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpClassCallbackToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithCallbackAsJava() throws IOException {
        assertEquals(NEW_LINE +
                        "        callback()" + NEW_LINE +
                        "                .withCallbackClass(\"some_class\")",
                new HttpCallbackToJavaSerializer().serializeAsJava(1,
                        new HttpClassCallback()
                                .withCallbackClass("some_class")
                )
        );
    }

}
