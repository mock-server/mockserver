package org.mockserver.serialization.java;

import org.junit.Test;
import org.mockserver.model.Parameter;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

public class ParameterToJavaSerializerTest {

    @Test
    public void shouldSerializeParameter() throws IOException {
        assertEquals(NEW_LINE +
                        "        new Parameter(\"requestParameterNameOne\", \"requestParameterValueOneOne\", \"requestParameterValueOneTwo\")",
                new ParameterToJavaSerializer().serialize(1, new Parameter("requestParameterNameOne", "requestParameterValueOneOne", "requestParameterValueOneTwo"))
        );
    }

    @Test
    public void shouldSerializeMultipleParameters() throws IOException {
        assertEquals(NEW_LINE +
                        "        new Parameter(\"requestParameterNameOne\", \"requestParameterValueOneOne\", \"requestParameterValueOneTwo\"),"        +
                        NEW_LINE +
                        "        new Parameter(\"requestParameterNameTwo\", \"requestParameterValueTwo\")",
                new ParameterToJavaSerializer().serializeAsJava(1, new Parameter("requestParameterNameOne", "requestParameterValueOneOne", "requestParameterValueOneTwo"), new Parameter("requestParameterNameTwo", "requestParameterValueTwo"))
        );
    }

    @Test
    public void shouldSerializeListOfParameters() throws IOException {
        assertEquals(NEW_LINE +
                        "        new Parameter(\"requestParameterNameOne\", \"requestParameterValueOneOne\", \"requestParameterValueOneTwo\"),"        +
                        NEW_LINE +
                        "        new Parameter(\"requestParameterNameTwo\", \"requestParameterValueTwo\")",
                new ParameterToJavaSerializer().serializeAsJava(1, Arrays.asList(
                        new Parameter("requestParameterNameOne", "requestParameterValueOneOne", "requestParameterValueOneTwo"),
                        new Parameter("requestParameterNameTwo", "requestParameterValueTwo")
                ))
        );
    }

}