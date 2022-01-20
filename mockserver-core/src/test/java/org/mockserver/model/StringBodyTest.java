package org.mockserver.model;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotSame;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.StringBody.subString;

/**
 * @author jamesdbloom
 */
public class StringBodyTest {

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(exact("some_body"), exact("some_body"));
        assertNotSame(exact("some_body"), exact("some_body"));
    }

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        StringBody stringBody = new StringBody("some_body");

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), nullValue());
        assertThat(stringBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(stringBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithSubString() {
        // when
        StringBody stringBody = new StringBody("some_body", null, true, null);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithCharset() {
        // when
        StringBody stringBody = new StringBody("some_body", StandardCharsets.UTF_16);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), is(StandardCharsets.UTF_16));
        assertThat(stringBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_16));
        assertThat(stringBody.getContentType(), is(MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16).toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticExactConstructor() {
        // when
        StringBody stringBody = exact("some_body");

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(false));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), nullValue());
        assertThat(stringBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(stringBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValueSetInStaticExactConstructorWithCharset() {
        // when
        StringBody stringBody = exact("some_body", StandardCharsets.UTF_16);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(false));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), is(StandardCharsets.UTF_16));
        assertThat(stringBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_16));
        assertThat(stringBody.getContentType(), is(MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16).toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticExactConstructorWithNullCharset() {
        // when
        StringBody stringBody = exact("some_body", (Charset) null);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(false));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), nullValue());
        assertThat(stringBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(stringBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValueSetInStaticExactConstructorWithContentType() {
        // when
        StringBody stringBody = exact("some_body", MediaType.PLAIN_TEXT_UTF_8);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(false));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), is(StandardCharsets.UTF_8));
        assertThat(stringBody.getCharset(StandardCharsets.UTF_16), is(StandardCharsets.UTF_8));
        assertThat(stringBody.getContentType(), is(MediaType.PLAIN_TEXT_UTF_8.toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticExactConstructorWithNullMediaType() {
        // when
        StringBody stringBody = exact("some_body", (MediaType) null);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(false));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), nullValue());
        assertThat(stringBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(stringBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValueSetInStaticSubStringConstructor() {
        // when
        StringBody stringBody = subString("some_body");

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), nullValue());
        assertThat(stringBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(stringBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValueSetInStaticSubStringConstructorWithCharset() {
        // when
        StringBody stringBody = subString("some_body", StandardCharsets.UTF_16);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), is(StandardCharsets.UTF_16));
        assertThat(stringBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_16));
        assertThat(stringBody.getContentType(), is(MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16).toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticSubStringConstructorWithNullCharset() {
        // when
        StringBody stringBody = subString("some_body", (Charset) null);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), nullValue());
        assertThat(stringBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(stringBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValueSetInStaticSubStringConstructorWithContentType() {
        // when
        StringBody stringBody = subString("some_body", MediaType.PLAIN_TEXT_UTF_8);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), is(StandardCharsets.UTF_8));
        assertThat(stringBody.getCharset(StandardCharsets.UTF_16), is(StandardCharsets.UTF_8));
        assertThat(stringBody.getContentType(), is(MediaType.PLAIN_TEXT_UTF_8.toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticSubStringConstructorWithNullMediaType() {
        // when
        StringBody stringBody = subString("some_body", (MediaType) null);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), nullValue());
        assertThat(stringBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(stringBody.getContentType(), nullValue());
    }

}
