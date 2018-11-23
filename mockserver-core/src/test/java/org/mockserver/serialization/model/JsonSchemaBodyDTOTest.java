package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.JsonSchemaBody;
import org.mockserver.model.JsonSchemaBody;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;


/**
 * @author jamesdbloom
 */
public class JsonSchemaBodyDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        JsonSchemaBodyDTO jsonSchemaBodyDTO = new JsonSchemaBodyDTO(new JsonSchemaBody("some_body"));

        // then
        assertThat(jsonSchemaBodyDTO.getJson(), is("some_body"));
        assertThat(jsonSchemaBodyDTO.getType(), is(Body.Type.JSON_SCHEMA));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        JsonSchemaBody jsonSchemaBody = new JsonSchemaBodyDTO(new JsonSchemaBody("some_body")).buildObject();

        // then
        assertThat(jsonSchemaBody.getValue(), is("some_body"));
        assertThat(jsonSchemaBody.getType(), is(Body.Type.JSON_SCHEMA));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticBuilder() {
        assertThat(jsonSchema("some_body"), is(new JsonSchemaBody("some_body")));
    }

    @Test
    public void shouldHandleNull() {
        // given
        String body = null;

        // when
        JsonSchemaBody jsonSchemaBody = new JsonSchemaBodyDTO(new JsonSchemaBody(body)).buildObject();

        // then
        assertThat(jsonSchemaBody.getValue(), nullValue());
        assertThat(jsonSchemaBody.getType(), is(Body.Type.JSON_SCHEMA));
    }

    @Test
    public void shouldHandleEmptyByteArray() {
        // given
        String body = "";

        // when
        JsonSchemaBody jsonSchemaBody = new JsonSchemaBodyDTO(new JsonSchemaBody(body)).buildObject();

        // then
        assertThat(jsonSchemaBody.getValue(), is(""));
        assertThat(jsonSchemaBody.getType(), is(Body.Type.JSON_SCHEMA));
    }
}
