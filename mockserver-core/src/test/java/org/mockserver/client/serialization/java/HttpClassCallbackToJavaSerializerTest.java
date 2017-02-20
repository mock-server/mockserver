package org.mockserver.client.serialization.java;

import org.junit.Test;
import org.mockserver.model.HttpClassCallback;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class HttpClassCallbackToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithCallbackAsJava() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "        callback()" + System.getProperty("line.separator") +
                        "                .withCallbackClass(\"some_class\")",
                new HttpCallbackToJavaSerializer().serializeAsJava(1,
                        new HttpClassCallback()
                                .withCallbackClass("some_class")
                )
        );
    }

}
