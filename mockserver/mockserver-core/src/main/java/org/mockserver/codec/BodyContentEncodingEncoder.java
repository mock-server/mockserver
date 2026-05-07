package org.mockserver.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class BodyContentEncodingEncoder {

    public static byte[] encodeBody(byte[] body, String contentEncoding) {
        if (body == null || body.length == 0 || !isNotBlank(contentEncoding)) {
            return body;
        }
        String encoding = contentEncoding.trim().toLowerCase();
        if (encoding.contains("gzip")) {
            return gzipCompress(body);
        } else if (encoding.contains("deflate")) {
            return deflateCompress(body);
        }
        return body;
    }

    private static byte[] gzipCompress(byte[] data) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(data.length);
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            gzipOutputStream.write(data);
            gzipOutputStream.finish();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to gzip compress body", e);
        }
    }

    private static byte[] deflateCompress(byte[] data) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(data.length);
             DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(byteArrayOutputStream)) {
            deflaterOutputStream.write(data);
            deflaterOutputStream.finish();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to deflate compress body", e);
        }
    }

}
