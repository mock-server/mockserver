package org.mockserver.serialization.java;

import org.junit.Test;
import org.mockserver.model.Header;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

public class HeaderToJavaSerializerTest {

    @Test
    public void shouldSerializeHeader() throws IOException {
        assertEquals(NEW_LINE +
                        "        new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")",
                new HeaderToJavaSerializer().serialize(1, new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"))
        );
    }

    @Test
    public void shouldSerializeMultipleHeaders() throws IOException {
        assertEquals(NEW_LINE +
                        "        new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," +
                        NEW_LINE +
                        "        new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")",
                new HeaderToJavaSerializer().serializeAsJava(1, new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"), new Header("requestHeaderNameTwo", "requestHeaderValueTwo"))
        );
    }

    @Test
    public void shouldSerializeListOfHeaders() throws IOException {
        assertEquals(NEW_LINE +
                        "        new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," +
                        NEW_LINE +
                        "        new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")",
                new HeaderToJavaSerializer().serializeAsJava(1, Arrays.asList(
                        new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                        new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                ))
        );
    }

}