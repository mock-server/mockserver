package org.mockserver.model;

import com.google.common.base.Charsets;
import org.junit.Test;
import org.mockserver.matchers.MatchType;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.matchers.MatchType.STRICT;
import static org.mockserver.model.JsonBody.json;

/**
 * @author jamesdbloom
 */
public class JsonBodyTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        JsonBody jsonBody = new JsonBody("some_body");

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(MatchType.ONLY_MATCHING_FIELDS));
        assertThat(jsonBody.getContentType(), is("application/json"));
        assertThat(jsonBody.getCharset(), nullValue());
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithMatchType() {
        // when
        JsonBody jsonBody = new JsonBody("some_body", MatchType.STRICT);

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(MatchType.STRICT));
        assertThat(jsonBody.getContentType(), is("application/json"));
        assertThat(jsonBody.getCharset(), nullValue());
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithMatchTypeAndCharset() {
        // when
        JsonBody jsonBody = new JsonBody("some_body", Charsets.UTF_16, MatchType.STRICT);

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(MatchType.STRICT));
        assertThat(jsonBody.getContentType(), is("application/json"));
        assertThat(jsonBody.getCharset(), is(Charsets.UTF_16));
    }


    @Test
    public void shouldReturnValuesFromStaticBuilder() {
        // when
        JsonBody jsonBody = json("some_body");

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(ONLY_MATCHING_FIELDS));
        assertThat(jsonBody.getContentType(), is("application/json"));
        assertThat(jsonBody.getCharset(), nullValue());
    }

    @Test
    public void shouldReturnValuesFromStaticBuilderWithMatchType() {
        // when
        JsonBody jsonBody = json("some_body", STRICT);

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json"));
        assertThat(jsonBody.getCharset(), nullValue());
    }

    @Test
    public void shouldReturnValuesFromStaticBuilderWithCharset() {
        // when
        JsonBody jsonBody = json("some_body", Charsets.UTF_16);

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(ONLY_MATCHING_FIELDS));
        assertThat(jsonBody.getContentType(), is("application/json"));
        assertThat(jsonBody.getCharset(), is(Charsets.UTF_16));
    }

    @Test
    public void shouldReturnValuesFromStaticBuilderWithMatchTypeAndCharset() {
        // when
        JsonBody jsonBody = json("some_body", Charsets.UTF_16, STRICT);

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json"));
        assertThat(jsonBody.getCharset(), is(Charsets.UTF_16));
    }

}
