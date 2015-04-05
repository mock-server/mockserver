package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.JsonBody;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.matchers.JsonBodyMatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.matchers.JsonBodyMatchType.STRICT;
import static org.mockserver.model.JsonBody.json;


/**
 * @author jamesdbloom
 */
public class JsonBodyDTOTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // when
        JsonBodyDTO jsonBody = new JsonBodyDTO(new JsonBody("some_body"), false);

        // then
        assertThat(jsonBody.getJson(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(ONLY_MATCHING_FIELDS));
    }

    @Test
    public void shouldReturnValueSetInConstructorWithMatchType() {
        // when
        JsonBodyDTO jsonBody = new JsonBodyDTO(new JsonBody("some_body", STRICT), false);

        // then
        assertThat(jsonBody.getJson(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        JsonBody jsonBody = new JsonBodyDTO(new JsonBody("some_body"), false).buildObject();

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(ONLY_MATCHING_FIELDS));
    }

    @Test
    public void shouldBuildCorrectObjectWithMatchType() {
        // when
        JsonBody jsonBody = new JsonBodyDTO(new JsonBody("some_body", STRICT), false).buildObject();

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticBuilder() {
        assertThat(json("some_body"), is(new JsonBody("some_body")));
        assertThat(json("some_body", STRICT), is(new JsonBody("some_body", STRICT)));
    }

    @Test
    public void coverage() {
        new JsonBodyDTO();
    }
}
