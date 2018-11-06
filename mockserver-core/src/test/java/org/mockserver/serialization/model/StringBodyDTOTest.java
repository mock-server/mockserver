package org.mockserver.serialization.model;

import com.google.common.net.MediaType;
import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.StringBody;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.StringBody.subString;

/**
 * @author jamesdbloom
 */
public class StringBodyDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        StringBodyDTO stringBody = new StringBodyDTO(new StringBody("some_body"));

        // then
        assertThat(stringBody.getString(), is("some_body"));
        assertThat(stringBody.isSubString(), is(false));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithSubString() {
        // when
        StringBodyDTO stringBody = new StringBodyDTO(new StringBody("some_body", true));

        // then
        assertThat(stringBody.getString(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithCharset() {
        // when
        StringBodyDTO stringBody = new StringBodyDTO(new StringBody("some_body", StandardCharsets.UTF_8));

        // then
        assertThat(stringBody.getString(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), is(MediaType.PLAIN_TEXT_UTF_8.toString()));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithContentType() {
        // when
        StringBodyDTO stringBody = new StringBodyDTO(new StringBody("some_body", MediaType.HTML_UTF_8));

        // then
        assertThat(stringBody.getString(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), is(MediaType.HTML_UTF_8.toString()));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        StringBody stringBody = new StringBodyDTO(new StringBody("some_body", true, StandardCharsets.UTF_8)).buildObject();

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), is(MediaType.PLAIN_TEXT_UTF_8.toString()));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticExactBuilder() {
        assertThat(exact("some_body"), is(new StringBody("some_body", false)));
        assertThat(exact("some_body"), is(new StringBody("some_body")));
        assertThat(exact("some_body", StandardCharsets.UTF_16), is(new StringBody("some_body", false, StandardCharsets.UTF_16)));
        assertThat(exact("some_body", StandardCharsets.UTF_16), is(new StringBody("some_body", StandardCharsets.UTF_16)));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticSubStringBuilder() {
        assertThat(subString("some_body"), is(new StringBody("some_body", true)));
        assertThat(subString("some_body", StandardCharsets.UTF_16), is(new StringBody("some_body", true, StandardCharsets.UTF_16)));
    }
}
