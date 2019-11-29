package org.mockserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.*;

import java.nio.charset.Charset;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class BodyDecoderEncoder {

    private final ContentTypeMapper contentTypeMapper;

    public BodyDecoderEncoder(MockServerLogger mockServerLogger) {
        this.contentTypeMapper = new ContentTypeMapper(mockServerLogger);
    }

    public ByteBuf bodyToByteBuf(Body body, String contentTypeHeader) {
        ByteBuf content = Unpooled.buffer(0, 0);
        if (body != null) {
            Object bodyContents = body.getValue();
            Charset bodyCharset = body.getCharset(contentTypeMapper.getCharsetFromContentTypeHeader(contentTypeHeader));
            if (bodyContents instanceof byte[]) {
                content = Unpooled.copiedBuffer((byte[]) bodyContents);
            } else if (bodyContents instanceof String) {
                content = Unpooled.copiedBuffer(((String) bodyContents).getBytes(bodyCharset));
            } else if (body.toString() != null) {
                content = Unpooled.copiedBuffer(body.toString().getBytes(bodyCharset));
            }
        }
        return content;
    }

    public BodyWithContentType byteBufToBody(ByteBuf content, String contentTypeHeader) {
        if (content != null && content.readableBytes() > 0) {
            byte[] bodyBytes = new byte[content.readableBytes()];
            content.readBytes(bodyBytes);
            if (bodyBytes.length > 0) {
                if (ContentTypeMapper.isBinary(contentTypeHeader)) {
                    return new BinaryBody(bodyBytes);
                } else {
                    MediaType parse = null;
                    try {
                        if (isNotBlank(contentTypeHeader)) {
                            parse = MediaType.parse(contentTypeHeader);
                        }
                    } catch (Throwable throwable) {
                        // ignore content-type parse failure
                    }
                    return new StringBody(
                        new String(bodyBytes, contentTypeMapper.getCharsetFromContentTypeHeader(contentTypeHeader)),
                        bodyBytes,
                        false,
                        parse
                    );
                }
            }
        }
        return null;
    }
}
