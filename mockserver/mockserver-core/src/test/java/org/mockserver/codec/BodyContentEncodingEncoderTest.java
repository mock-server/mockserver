package org.mockserver.codec;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;

public class BodyContentEncodingEncoderTest {

    @Test
    public void shouldGzipCompressBody() throws IOException {
        byte[] input = "test-data".getBytes(UTF_8);

        byte[] compressed = BodyContentEncodingEncoder.encodeBody(input, "gzip");

        assertThat(compressed, is(not(input)));
        assertThat(decompress(compressed, "gzip"), is(input));
    }

    @Test
    public void shouldDeflateCompressBody() throws IOException {
        byte[] input = "test-data".getBytes(UTF_8);

        byte[] compressed = BodyContentEncodingEncoder.encodeBody(input, "deflate");

        assertThat(compressed, is(not(input)));
        assertThat(decompress(compressed, "deflate"), is(input));
    }

    @Test
    public void shouldReturnBodyUnchangedForUnknownEncoding() {
        byte[] input = "test-data".getBytes(UTF_8);

        byte[] result = BodyContentEncodingEncoder.encodeBody(input, "br");

        assertThat(result, is(input));
    }

    @Test
    public void shouldReturnNullBodyUnchanged() {
        assertThat(BodyContentEncodingEncoder.encodeBody(null, "gzip"), is((byte[]) null));
    }

    @Test
    public void shouldReturnEmptyBodyUnchanged() {
        byte[] result = BodyContentEncodingEncoder.encodeBody(new byte[0], "gzip");

        assertThat(result, is(new byte[0]));
    }

    @Test
    public void shouldReturnBodyUnchangedWhenNoContentEncoding() {
        byte[] input = "test-data".getBytes(UTF_8);

        assertThat(BodyContentEncodingEncoder.encodeBody(input, null), is(input));
        assertThat(BodyContentEncodingEncoder.encodeBody(input, ""), is(input));
        assertThat(BodyContentEncodingEncoder.encodeBody(input, "  "), is(input));
    }

    @Test
    public void shouldHandleCaseInsensitiveGzip() throws IOException {
        byte[] input = "test-data".getBytes(UTF_8);

        assertThat(decompress(BodyContentEncodingEncoder.encodeBody(input, "GZIP"), "gzip"), is(input));
        assertThat(decompress(BodyContentEncodingEncoder.encodeBody(input, "Gzip"), "gzip"), is(input));
    }

    private byte[] decompress(byte[] data, String encoding) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if ("gzip".equalsIgnoreCase(encoding)) {
            try (GZIPInputStream gis = new GZIPInputStream(bais)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = gis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
            }
        } else if ("deflate".equalsIgnoreCase(encoding)) {
            try (InflaterInputStream iis = new InflaterInputStream(bais)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = iis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
            }
        }
        return baos.toByteArray();
    }

}
