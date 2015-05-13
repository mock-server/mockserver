package org.mockserver.model;

import com.google.common.base.Charsets;
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
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithCharset() {
        // when
        StringBody stringBody = new StringBody("some_body", Charsets.UTF_16);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(), is(Charsets.UTF_16));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructor() {
        // when
        StringBody stringBody = exact("some_body", Charsets.UTF_16);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(), is(Charsets.UTF_16));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithCharset() {
        // when
        StringBody stringBody = exact("some_body", Charsets.UTF_16);

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getCharset(), is(Charsets.UTF_16));
    }

}
