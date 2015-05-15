package org.mockserver.model;

import com.google.common.base.Charsets;
import com.google.common.net.MediaType;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class StringBodyTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        StringBody stringBody = new StringBody("some_body");

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(), nullValue());
        assertThat(stringBody.getCharset(null), nullValue());
        assertThat(stringBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_8));
        assertThat(stringBody.getContentType(), is(MediaType.create("text", "plain").toString()));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithCharset() {
        // when
        StringBody stringBody = new StringBody("some_body", Charsets.UTF_16);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(), is(Charsets.UTF_16));
        assertThat(stringBody.getCharset(null), is(Charsets.UTF_16));
        assertThat(stringBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_16));
        assertThat(stringBody.getContentType(), is(MediaType.create("text", "plain").toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructor() {
        // when
        StringBody stringBody = exact("some_body");

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(), nullValue());
        assertThat(stringBody.getCharset(null), nullValue());
        assertThat(stringBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_8));
        assertThat(stringBody.getContentType(), is(MediaType.create("text", "plain").toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithCharset() {
        // when
        StringBody stringBody = exact("some_body", Charsets.UTF_16);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(), is(Charsets.UTF_16));
        assertThat(stringBody.getCharset(null), is(Charsets.UTF_16));
        assertThat(stringBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_16));
        assertThat(stringBody.getContentType(), is(MediaType.create("text", "plain").toString()));
    }

}
