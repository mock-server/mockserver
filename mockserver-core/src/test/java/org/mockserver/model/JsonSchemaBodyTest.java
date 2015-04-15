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
        assertThat(jsonSchemaBody.getValue(), is("{" + System.getProperty("line.separator") +
                "  \"type\": \"object\"," + System.getProperty("line.separator") +
                "  \"properties\": {" + System.getProperty("line.separator") +
                "    \"someField\": {" + System.getProperty("line.separator") +
                "      \"type\": \"string\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"required\": [\"someField\"]" + System.getProperty("line.separator") +
                "}"));
    }

}