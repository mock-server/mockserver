package org.mockserver.model;

import org.junit.Test;
import org.mockserver.serialization.Base64Converter;

import java.nio.charset.StandardCharsets;

import javax.xml.bind.DatatypeConverter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockserver.model.BinaryBody.binary;

/**
 * @author jamesdbloom
 */
public class BinaryBodyTest {

    private final Base64Converter base64Converter = new Base64Converter();

    @Test
    @SuppressWarnings("AccessStaticViaInstance")
    public void shouldAlwaysCreateNewObject() {
        byte[] body = DatatypeConverter.parseBase64Binary("some_body");

        assertEquals(binary(body), binary(body));
        assertNotSame(binary(body), binary(body));
    }

    @Test
    public void shouldReturnFormattedRequestInToString() {
        assertEquals(base64Converter.bytesToBase64String("some_body".getBytes(UTF_8)), binary("some_body".getBytes(UTF_8)).toString());
    }

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        byte[] body = DatatypeConverter.parseBase64Binary("some_body");

        // when
        BinaryBody binaryBody = new BinaryBody(body);

        // then
        assertThat(binaryBody.getValue(), is(body));
        assertThat(binaryBody.getType(), is(Body.Type.BINARY));
        assertThat(binaryBody.getCharset(null), nullValue());
        assertThat(binaryBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(binaryBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValueSetInStaticConstructor() {
        // given
        byte[] body = DatatypeConverter.parseBase64Binary("some_body");

        // when
        BinaryBody binaryBody = binary(body);

        // then
        assertThat(binaryBody.getValue(), is(body));
        assertThat(binaryBody.getType(), is(Body.Type.BINARY));
        assertThat(binaryBody.getCharset(null), nullValue());
        assertThat(binaryBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(binaryBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithCharsetAndMediaType() {
        // given
        byte[] body = DatatypeConverter.parseBase64Binary("some_body");

        // when
        BinaryBody binaryBody = binary(body, MediaType.PLAIN_TEXT_UTF_8);

        // then
        assertThat(binaryBody.getValue(), is(body));
        assertThat(binaryBody.getType(), is(Body.Type.BINARY));
        assertThat(binaryBody.getCharset(null), is(StandardCharsets.UTF_8));
        assertThat(binaryBody.getCharset(StandardCharsets.UTF_16), is(StandardCharsets.UTF_8));
        assertThat(binaryBody.getContentType(), is(MediaType.PLAIN_TEXT_UTF_8.toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithNullMediaType() {
        // given
        byte[] body = DatatypeConverter.parseBase64Binary("some_body");

        // when
        BinaryBody binaryBody = binary(body, null);

        // then
        assertThat(binaryBody.getValue(), is(body));
        assertThat(binaryBody.getType(), is(Body.Type.BINARY));
        assertThat(binaryBody.getCharset(null), nullValue());
        assertThat(binaryBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(binaryBody.getContentType(), nullValue());
    }

}
