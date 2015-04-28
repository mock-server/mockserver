package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockserver.model.JsonSchemaBody.jsonSchemaFromResource;

public class JsonSchemaBodyTest {

    @Test
    public void shouldLoadSchemaFromClasspath() {
        // when
        JsonSchemaBody jsonSchemaBody = jsonSchemaFromResource("org/mockserver/model/testJsonSchema.json");

        // then
        assertThat(jsonSchemaBody.getValue(), is("{\n" +
                "  \"type\": \"object\",\n" +
                "  \"properties\": {\n" +
                "    \"someField\": {\n" +
                "      \"type\": \"string\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"required\": [\"someField\"]\n" +
                "}"));
    }

}