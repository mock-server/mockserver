package org.mockserver.client.serialization.model;

import com.google.common.base.Charsets;
import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.JsonBody;

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
        assertThat(jsonBody.getCharset(), nullValue());
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithMatchType() {
        // when
        JsonBodyDTO jsonBody = new JsonBodyDTO(new JsonBody("some_body", STRICT));

        // then
        assertThat(jsonBody.getJson(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getCharset(), nullValue());
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithMatchTypeAndCharset() {
        // when
        JsonBodyDTO jsonBody = new JsonBodyDTO(new JsonBody("some_body", Charsets.UTF_16, STRICT));

        // then
        assertThat(jsonBody.getJson(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getCharset(), is(Charsets.UTF_16));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        JsonBody jsonBody = new JsonBodyDTO(new JsonBody("some_body")).buildObject();

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(ONLY_MATCHING_FIELDS));
        assertThat(jsonBody.getCharset(), nullValue());
    }

    @Test
    public void shouldBuildCorrectObjectWithMatchType() {
        // when
        JsonBody jsonBody = new JsonBodyDTO(new JsonBody("some_body", STRICT)).buildObject();

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getCharset(), nullValue());
    }

    @Test
    public void shouldBuildCorrectObjectWithMatchTypeAndCharset() {
        // when
        JsonBody jsonBody = new JsonBodyDTO(new JsonBody("some_body", Charsets.UTF_16, STRICT)).buildObject();

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getCharset(), is(Charsets.UTF_16));
    }

    @Test
    public void coverage() {
        new JsonBodyDTO();
    }
}
