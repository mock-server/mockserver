package org.mockserver.model;

import com.google.common.base.Charsets;
import com.google.common.net.MediaType;
import org.junit.Test;

import java.nio.charset.Charset;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class StringBodyTest {

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(new StringBody("some_body").exact("some_body"), exact("some_body"));
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
        assertThat(stringBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_8));
        assertThat(stringBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithCharset() {
        // when
        StringBody stringBody = new StringBody("some_body", Charsets.UTF_16);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), is(Charsets.UTF_16));
        assertThat(stringBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_16));
        assertThat(stringBody.getContentType(), is(MediaType.create("text", "plain").withCharset(Charsets.UTF_16).toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructor() {
        // when
        StringBody stringBody = exact("some_body");

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), nullValue());
        assertThat(stringBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_8));
        assertThat(stringBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithCharset() {
        // when
        StringBody stringBody = exact("some_body", Charsets.UTF_16);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), is(Charsets.UTF_16));
        assertThat(stringBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_16));
        assertThat(stringBody.getContentType(), is(MediaType.create("text", "plain").withCharset(Charsets.UTF_16).toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithNullCharset() {
        // when
        StringBody stringBody = exact("some_body", (Charset)null);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), nullValue());
        assertThat(stringBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_8));
        assertThat(stringBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithContentType() {
        // when
        StringBody stringBody = exact("some_body", MediaType.PLAIN_TEXT_UTF_8);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), is(Charsets.UTF_8));
        assertThat(stringBody.getCharset(Charsets.UTF_16), is(Charsets.UTF_8));
        assertThat(stringBody.getContentType(), is(MediaType.PLAIN_TEXT_UTF_8.toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithNullMediaType() {
        // when
        StringBody stringBody = exact("some_body", (MediaType) null);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(null), nullValue());
        assertThat(stringBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_8));
        assertThat(stringBody.getContentType(), nullValue());
    }

}
