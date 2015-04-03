package org.mockserver.client.serialization.java;

import org.junit.Test;
import org.mockserver.model.Header;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class HeaderToJavaSerializerTest {

    @Test
    public void shouldSerializeHeader() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        " new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")",
                new HeaderToJavaSerializer().serializeAsJava(1, new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"))
        );
    }

    @Test
    public void shouldSerializeMultipleHeaders() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "  new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," +
                        System.getProperty("line.separator") +
                        "  new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")",
                new HeaderToJavaSerializer().serializeAsJava(2, new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"), new Header("requestHeaderNameTwo", "requestHeaderValueTwo"))
        );
    }

    @Test
    public void shouldSerializeListOfHeaders() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "  new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," +
                        System.getProperty("line.separator") +
                        "  new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")",
                new HeaderToJavaSerializer().serializeAsJava(2, Arrays.asList(
                        new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                        new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                ))
        );
    }

}