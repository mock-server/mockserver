package org.mockserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.mockserver.model.*;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.model.JsonBody.DEFAULT_MATCH_TYPE;

@SuppressWarnings("rawtypes")
public class BodyDecoderEncoder {

    public ByteBuf bodyToByteBuf(Body body, String contentTypeHeader) {
        byte[] bytes = bodyToBytes(body, contentTypeHeader);
        if (bytes != null) {
            return Unpooled.copiedBuffer(bytes);
        } else {
            return Unpooled.buffer(0, 0);
        }
    }

    public ByteBuf[] bodyToByteBuf(Body body, String contentTypeHeader, int chunkSize) {
        byte[][] chunks = split(bodyToBytes(body, contentTypeHeader), chunkSize);
        ByteBuf[] byteBufs = new ByteBuf[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            if (chunks[i] != null) {
                byteBufs[i] = Unpooled.copiedBuffer(chunks[i]);
            } else {
                byteBufs[i] = Unpooled.buffer(0, 0);
            }
        }
        return byteBufs;
    }

    public static byte[][] split(byte[] array, int chunkSize) {
        if (chunkSize < array.length) {
            int numOfChunks = (array.length + chunkSize - 1) / chunkSize;
            byte[][] output = new byte[numOfChunks][];

            for (int i = 0; i < numOfChunks; ++i) {
                int start = i * chunkSize;
                int length = Math.min(array.length - start, chunkSize);

                byte[] temp = new byte[length];
                System.arraycopy(array, start, temp, 0, length);
                output[i] = temp;
            }
            return output;
        } else {
            return new byte[][]{array};
        }
    }

    byte[] bodyToBytes(Body body, String contentTypeHeader) {
        if (body != null) {
            if (body instanceof BinaryBody) {
                return body.getRawBytes();
            } else if (body.getValue() instanceof String) {
                Charset contentTypeCharset = MediaType.parse(contentTypeHeader).getCharsetOrDefault();
                Charset bodyCharset = body.getCharset(contentTypeCharset);
                return ((String) body.getValue()).getBytes(bodyCharset != null ? bodyCharset : MediaType.DEFAULT_TEXT_HTTP_CHARACTER_SET);
            } else {
                return body.getRawBytes();
            }
        }
        return null;
    }

    public BodyWithContentType byteBufToBody(ByteBuf content, String contentTypeHeader) {
        if (content != null && content.readableBytes() > 0) {
            byte[] bodyBytes = new byte[content.readableBytes()];
            content.readBytes(bodyBytes);
            return bytesToBody(bodyBytes, contentTypeHeader);
        }
        return null;
    }

    BodyWithContentType bytesToBody(byte[] bodyBytes, String contentTypeHeader) {
        if (bodyBytes.length > 0) {
            MediaType mediaType = MediaType.parse(contentTypeHeader);
            if (mediaType.isJson()) {
                return new JsonBody(
                    new String(bodyBytes, mediaType.getCharsetOrDefault()),
                    bodyBytes,
                    mediaType,
                    DEFAULT_MATCH_TYPE
                );
            } else if (mediaType.isXml()) {
                return new XmlBody(
                    new String(bodyBytes, mediaType.getCharsetOrDefault()),
                    mediaType
                );
            } else if (mediaType.isString()) {
                return new StringBody(
                    new String(bodyBytes, mediaType.getCharsetOrDefault()),
                    bodyBytes,
                    false,
                    isNotBlank(contentTypeHeader) ? mediaType : null
                );
            } else {
                return new BinaryBody(bodyBytes, mediaType);
            }
        }
        return null;
    }
}
