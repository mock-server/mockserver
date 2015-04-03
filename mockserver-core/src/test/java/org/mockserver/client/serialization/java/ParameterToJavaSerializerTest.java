package org.mockserver.client.serialization.java;

import org.junit.Test;
import org.mockserver.model.Parameter;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class ParameterToJavaSerializerTest {

    @Test
    public void shouldSerializeParameter() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        " new Parameter(\"requestParameterNameOne\", \"requestParameterValueOneOne\", \"requestParameterValueOneTwo\")",
                new ParameterToJavaSerializer().serializeAsJava(1, new Parameter("requestParameterNameOne", "requestParameterValueOneOne", "requestParameterValueOneTwo"))
        );
    }

    @Test
    public void shouldSerializeMultipleParameters() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "  new Parameter(\"requestParameterNameOne\", \"requestParameterValueOneOne\", \"requestParameterValueOneTwo\")," +
                        System.getProperty("line.separator") +
                        "  new Parameter(\"requestParameterNameTwo\", \"requestParameterValueTwo\")",
                new ParameterToJavaSerializer().serializeAsJava(2, new Parameter("requestParameterNameOne", "requestParameterValueOneOne", "requestParameterValueOneTwo"), new Parameter("requestParameterNameTwo", "requestParameterValueTwo"))
        );
    }

    @Test
    public void shouldSerializeListOfParameters() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "  new Parameter(\"requestParameterNameOne\", \"requestParameterValueOneOne\", \"requestParameterValueOneTwo\")," +
                        System.getProperty("line.separator") +
                        "  new Parameter(\"requestParameterNameTwo\", \"requestParameterValueTwo\")",
                new ParameterToJavaSerializer().serializeAsJava(2, Arrays.asList(
                        new Parameter("requestParameterNameOne", "requestParameterValueOneOne", "requestParameterValueOneTwo"),
                        new Parameter("requestParameterNameTwo", "requestParameterValueTwo")
                ))
        );
    }

}