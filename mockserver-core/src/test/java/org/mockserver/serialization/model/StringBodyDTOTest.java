package org.mockserver.serialization.model;

import io.netty.util.CharsetUtil;
import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.MediaType;
import org.mockserver.model.StringBody;

import java.nio.charset.StandardCharsets;

import static org.mockserver.model.MediaType.PLAIN_TEXT_UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;
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
        StringBodyDTO stringBody = new StringBodyDTO(new StringBody("some_body", null, true, (MediaType) null));

        // then
        assertThat(stringBody.getString(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithCharset() {
        // when
        StringBodyDTO stringBody = new StringBodyDTO(new StringBody("some_body", CharsetUtil.ISO_8859_1));

        // then
        assertThat(stringBody.getString(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), is(PLAIN_TEXT_UTF_8.withCharset(CharsetUtil.ISO_8859_1).toString()));
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
        StringBody stringBody = new StringBodyDTO(new StringBody("some_body", null, true, (CharsetUtil.ISO_8859_1 != null ? MediaType.create("text", "plain").withCharset(CharsetUtil.ISO_8859_1) : null))).buildObject();

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), is(PLAIN_TEXT_UTF_8.withCharset(CharsetUtil.ISO_8859_1).toString()));
    }

    @Test
    public void shouldNotSetDefaultCharset() {
        // when
        StringBody stringBody = new StringBodyDTO(new StringBody("some_body", null, true, (DEFAULT_HTTP_CHARACTER_SET != null ? MediaType.create("text", "plain").withCharset(DEFAULT_HTTP_CHARACTER_SET) : null))).buildObject();

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), is(MediaType.PLAIN_TEXT_UTF_8.withCharset(DEFAULT_HTTP_CHARACTER_SET).toString()));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticExactBuilder() {
        assertThat(exact("some_body"), is(new StringBody("some_body", null, false, (MediaType) null)));
        assertThat(exact("some_body"), is(new StringBody("some_body")));
        assertThat(exact("some_body", StandardCharsets.UTF_16), is(new StringBody("some_body", null, false, (StandardCharsets.UTF_16 != null ? MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16) : null))));
        assertThat(exact("some_body", StandardCharsets.UTF_16), is(new StringBody("some_body", StandardCharsets.UTF_16)));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticSubStringBuilder() {
        assertThat(subString("some_body"), is(new StringBody("some_body", null, true, (MediaType) null)));
        assertThat(subString("some_body", StandardCharsets.UTF_16), is(new StringBody("some_body", null, true, (StandardCharsets.UTF_16 != null ? MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16) : null))));
    }

    @Test
    public void shouldHandleNull() {
        // given
        String body = null;

        // when
        StringBody stringBody = new StringBodyDTO(new StringBody(body)).buildObject();

        // then
        assertThat(stringBody.getValue(), nullValue());
        assertThat(stringBody.getType(), is(Body.Type.STRING));
    }

    @Test
    public void shouldHandleEmptyByteArray() {
        // given
        String body = "";

        // when
        StringBody stringBody = new StringBodyDTO(new StringBody(body)).buildObject();

        // then
        assertThat(stringBody.getValue(), is(""));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
    }
}
