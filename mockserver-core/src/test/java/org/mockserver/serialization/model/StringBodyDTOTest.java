package org.mockserver.serialization.model;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.MediaType;
import org.mockserver.model.StringBody;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;
import static org.mockserver.model.MediaType.PLAIN_TEXT_UTF_8;
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
        assertThat(stringBody.getRawBytes(), is("some_body".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithSubString() {
        // when
        StringBodyDTO stringBody = new StringBodyDTO(new StringBody("some_body", null, true, null));

        // then
        assertThat(stringBody.getString(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), nullValue());
        assertThat(stringBody.getRawBytes(), is("some_body".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithRawBytes() {
        // when
        byte[] rawBytes = RandomUtils.nextBytes(20);
        StringBodyDTO stringBody = new StringBodyDTO(new StringBody("some_body", rawBytes, true, null));

        // then
        assertThat(stringBody.getString(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), nullValue());
        assertThat(stringBody.getMediaType(), nullValue());
        assertThat(stringBody.getRawBytes(), is(rawBytes));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithCharset() {
        // when
        StringBodyDTO stringBody = new StringBodyDTO(new StringBody("some_body", StandardCharsets.ISO_8859_1));

        // then
        assertThat(stringBody.getString(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), is(PLAIN_TEXT_UTF_8.withCharset(StandardCharsets.ISO_8859_1).toString()));
        assertThat(stringBody.getMediaType(), is(PLAIN_TEXT_UTF_8.withCharset(StandardCharsets.ISO_8859_1)));
        assertThat(stringBody.getRawBytes(), is("some_body".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithContentType() {
        // when
        StringBodyDTO stringBody = new StringBodyDTO(new StringBody("some_body", MediaType.HTML_UTF_8));

        // then
        assertThat(stringBody.getString(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), is(MediaType.HTML_UTF_8.toString()));
        assertThat(stringBody.getMediaType(), is(MediaType.HTML_UTF_8));
        assertThat(stringBody.getRawBytes(), is("some_body".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        StringBody stringBody = new StringBodyDTO(new StringBody("some_body", null, true, MediaType.create("text", "plain").withCharset(StandardCharsets.ISO_8859_1))).buildObject();

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), is(PLAIN_TEXT_UTF_8.withCharset(StandardCharsets.ISO_8859_1).toString()));
        assertThat(stringBody.getRawBytes(), is("some_body".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    public void shouldBuildCorrectObjectWithSubString() {
        // when
        StringBody stringBody = new StringBodyDTO(new StringBody("some_body", null, true, MediaType.create("text", "plain").withCharset(StandardCharsets.ISO_8859_1))).buildObject();

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), is("text/plain; charset=iso-8859-1"));
        assertThat(stringBody.getRawBytes(), is("some_body".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void shouldBuildCorrectObjectWithRawBytes() {
        // when
        byte[] rawBytes = RandomUtils.nextBytes(20);
        StringBody stringBody = new StringBodyDTO(new StringBody("some_body", rawBytes, true, null)).buildObject();

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), nullValue());
        assertThat(stringBody.getRawBytes(), is(rawBytes));
    }

    @Test
    public void shouldBuildCorrectObjectWithCharset() {
        // when
        StringBody stringBody = new StringBodyDTO(new StringBody("some_body", StandardCharsets.ISO_8859_1)).buildObject();

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), is(PLAIN_TEXT_UTF_8.withCharset(StandardCharsets.ISO_8859_1).toString()));
        assertThat(stringBody.getRawBytes(), is("some_body".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    public void shouldBuildCorrectObjectWithContentType() {
        // when
        StringBody stringBody = new StringBodyDTO(new StringBody("some_body", MediaType.HTML_UTF_8)).buildObject();

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), is(MediaType.HTML_UTF_8.toString()));
        assertThat(stringBody.getRawBytes(), is("some_body".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    public void shouldNotSetDefaultCharset() {
        // when
        StringBody stringBody = new StringBodyDTO(new StringBody("some_body", null, true, MediaType.create("text", "plain").withCharset(DEFAULT_HTTP_CHARACTER_SET))).buildObject();

        // then
        assertThat(stringBody.getValue(), is("some_body"));
        assertThat(stringBody.isSubString(), is(true));
        assertThat(stringBody.getType(), is(Body.Type.STRING));
        assertThat(stringBody.getContentType(), is(MediaType.PLAIN_TEXT_UTF_8.withCharset(DEFAULT_HTTP_CHARACTER_SET).toString()));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticExactBuilder() {
        assertThat(exact("some_body"), is(new StringBody("some_body", null, false, null)));
        assertThat(exact("some_body"), is(new StringBody("some_body")));
        assertThat(exact("some_body", StandardCharsets.UTF_16), is(new StringBody("some_body", null, false, MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16))));
        assertThat(exact("some_body", StandardCharsets.UTF_16), is(new StringBody("some_body", StandardCharsets.UTF_16)));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticSubStringBuilder() {
        assertThat(subString("some_body"), is(new StringBody("some_body", null, true, null)));
        assertThat(subString("some_body", StandardCharsets.UTF_16), is(new StringBody("some_body", null, true, MediaType.create("text", "plain").withCharset(StandardCharsets.UTF_16))));
    }

    @Test
    public void shouldHandleNull() {
        // when
        StringBody stringBody = new StringBodyDTO(new StringBody(null)).buildObject();

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
