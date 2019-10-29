package org.mockserver.codec;

import com.google.common.net.MediaType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.Body;
import org.mockserver.model.BodyWithContentType;
import org.mockserver.model.StringBody;

import java.nio.charset.Charset;

import static com.google.common.net.MediaType.parse;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class BodyDecoderEncoder {

    public static ByteBuf bodyToByteBuf(Body body, String contentTypeHeader) {
        ByteBuf content = Unpooled.buffer(0, 0);
        if (body != null) {
            Object bodyContents = body.getValue();
            Charset bodyCharset = body.getCharset(ContentTypeMapper.getCharsetFromContentTypeHeader(contentTypeHeader));
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

    public static BodyWithContentType byteBufToBody(ByteBuf content, String contentTypeHeader) {
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
                            parse = parse(contentTypeHeader);
                        }
                    } catch (Throwable throwable) {
                        // ignore content-type parse failure
                    }
                    return new StringBody(
                        new String(bodyBytes, ContentTypeMapper.getCharsetFromContentTypeHeader(contentTypeHeader)),
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
