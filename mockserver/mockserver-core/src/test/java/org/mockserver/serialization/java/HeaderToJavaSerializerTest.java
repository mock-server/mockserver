package org.mockserver.serialization.java;

import org.junit.Test;
import org.mockserver.model.Header;

import java.util.Arrays;

import static junit.framework.TestCase.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableOptionalString.optional;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

public class HeaderToJavaSerializerTest {

    @Test
    public void shouldSerializeHeader() {
        assertEquals(NEW_LINE +
                "        new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")",
            new HeaderToJavaSerializer().serialize(1, new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"))
        );
    }

    @Test
    public void shouldSerializeMultipleHeaders() {
        assertEquals(NEW_LINE +
                "        new Header(\"requestHeaderNameOne\", \"requestHeaderValueOneOne\", \"requestHeaderValueOneTwo\")," +
                NEW_LINE +
                "        new Header(\"requestHeaderNameTwo\", \"requestHeaderValueTwo\")",
            new HeaderToJavaSerializer().serializeAsJava(1, new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"), new Header("requestHeaderNameTwo", "requestHeaderValueTwo"))
        );
    }

    @Test
    public void shouldSerializeListOfHeaders() {
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

    @Test
    public void shouldSerializeListOfNottedAndOptionalHeaders() {
        assertEquals(NEW_LINE +
                "        new Header(not(\"requestHeaderNameOne\"), not(\"requestHeaderValueOneOne\"), string(\"requestHeaderValueOneTwo\"))," +
                NEW_LINE +
                "        new Header(optional(\"requestHeaderNameTwo\"), not(\"requestHeaderValueTwo\"))",
            new HeaderToJavaSerializer().serializeAsJava(1, Arrays.asList(
                new Header(not("requestHeaderNameOne"), not("requestHeaderValueOneOne"), string("requestHeaderValueOneTwo")),
                new Header(optional("requestHeaderNameTwo"), not("requestHeaderValueTwo"))
            ))
        );
    }

}