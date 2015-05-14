package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.JsonSchemaBody.jsonSchemaFromResource;

public class JsonSchemaBodyTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        JsonSchemaBody jsonSchemaBody = new JsonSchemaBody("some_body");

        // then
        assertThat(jsonSchemaBody.getValue(), is("some_body"));
        assertThat(jsonSchemaBody.getType(), is(Body.Type.JSON_SCHEMA));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructor() {
        // when
        JsonSchemaBody jsonSchemaBody = jsonSchema("some_body");

        // then
        assertThat(jsonSchemaBody.getValue(), is("some_body"));
        assertThat(jsonSchemaBody.getType(), is(Body.Type.JSON_SCHEMA));
    }

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