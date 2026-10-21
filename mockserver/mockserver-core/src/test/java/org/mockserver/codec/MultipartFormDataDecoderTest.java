package org.mockserver.codec;

import org.junit.Test;
import org.mockserver.codec.MultipartFormDataDecoder.DecodedParts;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Malformed and over-limit bodies stop Netty's decoder part-way through a part; these tests drive those paths so the
 * module's paranoid ByteBuf leak gate fails if a partially decoded part is not released.
 */
public class MultipartFormDataDecoderTest {

    private static final String BOUNDARY = "DecoderBoundary9Xy";
    private static final String CONTENT_TYPE = "multipart/form-data; boundary=" + BOUNDARY;

    private final MultipartFormDataDecoder decoder = new MultipartFormDataDecoder();

    @Test
    public void shouldDecodeFieldsAndParts() {
        byte[] body = ("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"a\"\r\n\r\none\r\n"
            + "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"f\"; filename=\"x.txt\"\r\nContent-Type: text/plain\r\n\r\ntwo\r\n"
            + "--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8);

        assertThat(decoder.decode(CONTENT_TYPE, body).getFields().getValues("a"), contains("one"));
        DecodedParts parts = decoder.decodeParts(CONTENT_TYPE, body, 10);
        assertThat(parts.tooManyParts(), is(false));
        assertThat(parts.parts().get(1).getFilename(), is("x.txt"));
        assertThat(new String(parts.parts().get(1).getBytes(), StandardCharsets.UTF_8), is("two"));
    }

    @Test
    public void shouldDropAPartThatATruncatedBodyCutsOff() {
        byte[] truncatedAttribute = ("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"a\"\r\n\r\nunterminated").getBytes(StandardCharsets.UTF_8);
        byte[] truncatedFile = ("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"f\"; filename=\"x\"\r\n\r\nunterminated").getBytes(StandardCharsets.UTF_8);

        assertThat(decoder.decode(CONTENT_TYPE, truncatedAttribute).getFields().getEntries(), is(empty()));
        assertThat(decoder.decode(CONTENT_TYPE, truncatedFile).getFields().getEntries(), is(empty()));
        assertThat(decoder.decodeParts(CONTENT_TYPE, truncatedAttribute, 10).parts(), is(empty()));
        assertThat(decoder.decodeParts(CONTENT_TYPE, truncatedFile, 10).parts(), is(empty()));
    }

    @Test
    public void shouldFindNoPartsInABodyWithoutABoundary() {
        assertThat(decoder.decodeParts(CONTENT_TYPE, "garbage".getBytes(StandardCharsets.UTF_8), 10).parts(), is(empty()));
        assertThat(decoder.decodeParts("multipart/form-data", "--x\r\n".getBytes(StandardCharsets.UTF_8), 10), is(nullValue()));
        assertThat(decoder.decode("application/json", "{}".getBytes(StandardCharsets.UTF_8)), is(nullValue()));
    }

    @Test
    public void shouldReportTooManyParts() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 130; i++) {
            body.append("--").append(BOUNDARY).append("\r\nContent-Disposition: form-data; name=\"f").append(i).append("\"\r\n\r\nv\r\n");
        }
        body.append("--").append(BOUNDARY).append("--\r\n");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);

        assertThat(decoder.decode(CONTENT_TYPE, bytes), is(nullValue()));
        DecodedParts parts = decoder.decodeParts(CONTENT_TYPE, bytes, 100);
        assertThat(parts.tooManyParts(), is(true));
        assertThat(parts.parts(), is(empty()));
    }
}
