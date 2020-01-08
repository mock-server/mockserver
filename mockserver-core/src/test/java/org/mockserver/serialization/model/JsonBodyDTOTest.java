package org.mockserver.serialization.model;

import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.matchers.MatchType.STRICT;

/**
 * @author jamesdbloom
 */
public class JsonBodyDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        JsonBodyDTO jsonBody = new JsonBodyDTO(new JsonBody("some_body"));

        // then
        assertThat(jsonBody.getJson(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(ONLY_MATCHING_FIELDS));
        assertThat(jsonBody.getContentType(), is("application/json"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithMatchType() {
        // when
        JsonBodyDTO jsonBody = new JsonBodyDTO(new JsonBody("some_body", STRICT));

        // then
        assertThat(jsonBody.getJson(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithMatchTypeAndCharset() {
        // when
        JsonBodyDTO jsonBody = new JsonBodyDTO(new JsonBody("some_body", null, (StandardCharsets.UTF_16 != null ? MediaType.create("application", "json").withCharset(StandardCharsets.UTF_16) : null), STRICT));

        // then
        assertThat(jsonBody.getJson(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json; charset=utf-16"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithMatchTypeAndMediaType() {
        // when
        JsonBodyDTO jsonBody = new JsonBodyDTO(new JsonBody("some_body", null, MediaType.JSON_UTF_8, STRICT));

        // then
        assertThat(jsonBody.getJson(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json; charset=utf-8"));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        JsonBody jsonBody = new JsonBodyDTO(new JsonBody("some_body")).buildObject();

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(ONLY_MATCHING_FIELDS));
        assertThat(jsonBody.getContentType(), is("application/json"));
    }

    @Test
    public void shouldBuildCorrectObjectWithMatchType() {
        // when
        JsonBody jsonBody = new JsonBodyDTO(new JsonBody("some_body", STRICT)).buildObject();

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json"));
    }

    @Test
    public void shouldBuildCorrectObjectWithMatchTypeAndCharset() {
        // when
        JsonBody jsonBody = new JsonBodyDTO(new JsonBody("some_body", null, (StandardCharsets.UTF_16 != null ? MediaType.create("application", "json").withCharset(StandardCharsets.UTF_16) : null), STRICT)).buildObject();

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json; charset=utf-16"));
    }

    @Test
    public void shouldHandleNull() {
        // given
        String body = null;

        // when
        JsonBody jsonBody = new JsonBodyDTO(new JsonBody(body)).buildObject();

        // then
        assertThat(jsonBody.getValue(), CoreMatchers.nullValue());
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
    }

    @Test
    public void shouldHandleEmptyByteArray() {
        // given
        String body = "";

        // when
        JsonBody jsonBody = new JsonBodyDTO(new JsonBody(body)).buildObject();

        // then
        assertThat(jsonBody.getValue(), is(""));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
    }
}
