package org.mockserver.client.serialization.java;

import org.junit.Test;
import org.mockserver.model.HttpCallback;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class HttpCallbackToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithCallbackAsJava() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "        callback()" + System.getProperty("line.separator") +
                        "                .withCallbackClass(\"some_class\")",
                new HttpCallbackToJavaSerializer().serializeAsJava(1,
                        new HttpCallback()
                                .withCallbackClass("some_class")
                )
        );
    }

}
