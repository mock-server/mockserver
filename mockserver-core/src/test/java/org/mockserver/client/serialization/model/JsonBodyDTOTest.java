package org.mockserver.client.serialization.model;

import com.google.common.net.MediaType;
import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.JsonBody;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
        JsonBodyDTO jsonBody = new JsonBodyDTO(new JsonBody("some_body", StandardCharsets.UTF_16, STRICT));

        // then
        assertThat(jsonBody.getJson(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json; charset=utf-16"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithMatchTypeAndMediaType() {
        // when
        JsonBodyDTO jsonBody = new JsonBodyDTO(new JsonBody("some_body", MediaType.JSON_UTF_8, STRICT));

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
        JsonBody jsonBody = new JsonBodyDTO(new JsonBody("some_body", StandardCharsets.UTF_16, STRICT)).buildObject();

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json; charset=utf-16"));
    }
}
