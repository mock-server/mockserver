package org.mockserver.model;

import org.junit.Test;
import org.mockserver.matchers.MatchType;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
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
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithMatchTypeAndCharset() {
        // when
        JsonBody jsonBody = new JsonBody("some_body", StandardCharsets.UTF_16, MatchType.STRICT);

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(MatchType.STRICT));
        assertThat(jsonBody.getContentType(), is("application/json; charset=utf-16"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithMatchTypeAndMediaType() {
        // when
        JsonBody jsonBody = new JsonBody("some_body", MediaType.JSON_UTF_8, MatchType.STRICT);

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(MatchType.STRICT));
        assertThat(jsonBody.getContentType(), is("application/json; charset=utf-8"));
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
    }

    @Test
    public void shouldReturnValuesFromStaticBuilderWithCharsetAndMatchType() {
        // when
        JsonBody jsonBody = json("some_body", StandardCharsets.UTF_16, STRICT);

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json; charset=utf-16"));
    }

    @Test
    public void shouldReturnValuesFromStaticBuilderWithMediaTypeAndMatchType() {
        // when
        JsonBody jsonBody = json("some_body", MediaType.JSON_UTF_8, STRICT);

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json; charset=utf-8"));
    }

    @Test
    public void shouldReturnValuesFromStaticBuilderWithMatchTypeAndCharset() {
        // when
        JsonBody jsonBody = json("some_body", StandardCharsets.UTF_16, STRICT);

        // then
        assertThat(jsonBody.getValue(), is("some_body"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json; charset=utf-16"));
    }

    @Test
    public void shouldReturnValuesFromStaticObjectBuilder() {
        // when
        JsonBody jsonBody = json(new TestObject());

        // then
        assertThat(jsonBody.getValue(), is("{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"}"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(ONLY_MATCHING_FIELDS));
        assertThat(jsonBody.getContentType(), is("application/json"));
    }

    @Test
    public void shouldReturnValuesFromStaticObjectBuilderWithMatchType() {
        // when
        JsonBody jsonBody = json(new TestObject(), STRICT);

        // then
        assertThat(jsonBody.getValue(), is("{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"}"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json"));
    }

    @Test
    public void shouldReturnValuesFromStaticObjectBuilderWithCharsetAndMatchType() {
        // when
        JsonBody jsonBody = json(new TestObject(), StandardCharsets.UTF_16, STRICT);

        // then
        assertThat(jsonBody.getValue(), is("{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"}"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json; charset=utf-16"));
    }

    @Test
    public void shouldReturnValuesFromStaticObjectBuilderWithMediaTypeAndMatchType() {
        // when
        JsonBody jsonBody = json(new TestObject(), MediaType.JSON_UTF_8, STRICT);

        // then
        assertThat(jsonBody.getValue(), is("{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"}"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json; charset=utf-8"));
    }

    @Test
    public void shouldReturnValuesFromStaticObjectBuilderWithMatchTypeAndCharset() {
        // when
        JsonBody jsonBody = json(new TestObject(), StandardCharsets.UTF_16, STRICT);

        // then
        assertThat(jsonBody.getValue(), is("{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"}"));
        assertThat(jsonBody.getType(), is(Body.Type.JSON));
        assertThat(jsonBody.getMatchType(), is(STRICT));
        assertThat(jsonBody.getContentType(), is("application/json; charset=utf-16"));
    }

    public class TestObject {
        private String fieldOne = "valueOne";
        private String fieldTwo = "valueTwo";

        public String getFieldOne() {
            return fieldOne;
        }

        public void setFieldOne(String fieldOne) {
            this.fieldOne = fieldOne;
        }

        public String getFieldTwo() {
            return fieldTwo;
        }

        public void setFieldTwo(String fieldTwo) {
            this.fieldTwo = fieldTwo;
        }
    }
}
