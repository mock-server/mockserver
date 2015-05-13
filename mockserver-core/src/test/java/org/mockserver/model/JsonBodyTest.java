package org.mockserver.model;

import org.junit.Test;
import org.mockserver.matchers.MatchType;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
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
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithMatchType() {
        // when
        JsonBody jsonBody = new JsonBody("some_body", MatchType.STRICT);

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(MatchType.STRICT));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructor() {
        // when
        JsonBody jsonBody = json("some_body");

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(MatchType.ONLY_MATCHING_FIELDS));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithMatchType() {
        // when
        JsonBody jsonBody = json("some_body", MatchType.STRICT);

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(MatchType.STRICT));
    }

}
