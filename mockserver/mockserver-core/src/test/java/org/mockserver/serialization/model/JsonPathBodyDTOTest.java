package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.JsonPathBody;
import org.mockserver.model.JsonSchemaBody;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.JsonPathBody.jsonPath;

/**
 * @author jamesdbloom
 */
public class JsonPathBodyDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        JsonPathBodyDTO xpathBody = new JsonPathBodyDTO(new JsonPathBody("some_body"));

        // then
        assertThat(xpathBody.getJsonPath(), is("some_body"));
        assertThat(xpathBody.getType(), is(Body.Type.JSON_PATH));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        JsonPathBody jsonPathBody = new JsonPathBodyDTO(new JsonPathBody("some_body")).buildObject();

        // then
        assertThat(jsonPathBody.getValue(), is("some_body"));
        assertThat(jsonPathBody.getType(), is(Body.Type.JSON_PATH));
    }

    @Test
    public void shouldBuildCorrectObjectWithOptional() {
        // when
        JsonPathBody jsonPathBody = new JsonPathBodyDTO((JsonPathBody) new JsonPathBody("some_body").withOptional(true)).buildObject();

        // then
        assertThat(jsonPathBody.getValue(), is("some_body"));
        assertThat(jsonPathBody.getType(), is(Body.Type.JSON_PATH));
        assertThat(jsonPathBody.getOptional(), is(true));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticBuilder() {
        assertThat(jsonPath("some_body"), is(new JsonPathBody("some_body")));
    }
}
