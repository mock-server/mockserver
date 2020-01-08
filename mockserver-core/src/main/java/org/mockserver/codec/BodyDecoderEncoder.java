package org.mockserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.streams.IOStreamUtils;
import org.slf4j.event.Level;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.model.JsonBody.DEFAULT_MATCH_TYPE;

@SuppressWarnings("rawtypes")
public class BodyDecoderEncoder {

    private final MockServerLogger mockServerLogger;
    private final IOStreamUtils ioStreamUtils;

    public BodyDecoderEncoder(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        this.ioStreamUtils = new IOStreamUtils(mockServerLogger);
    }

    public ByteBuf bodyToByteBuf(Body body, String contentTypeHeader) {
        byte[] bytes = bodyToBytes(body, contentTypeHeader);
        if (bytes != null) {
            return Unpooled.copiedBuffer(bytes);
        } else {
            return Unpooled.buffer(0, 0);
        }
    }

    public void bodyToServletResponse(HttpServletResponse httpServletResponse, Body body, String contentTypeHeader) {
        byte[] bytes = bodyToBytes(body, contentTypeHeader);
        if (bytes != null) {
            ioStreamUtils.writeToOutputStream(bytes, httpServletResponse);
        }
    }

    private byte[] bodyToBytes(Body body, String contentTypeHeader) {
        if (body != null) {
            if (body instanceof BinaryBody) {
                return body.getRawBytes();
            } else if (body.getValue() instanceof String) {
                Charset contentTypeCharset = MediaType.parse(contentTypeHeader).getCharsetOrDefault();
                Charset bodyCharset = body.getCharset(contentTypeCharset);
                return ((String) body.getValue()).getBytes(bodyCharset);
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

    public BodyWithContentType servletRequestToBody(HttpServletRequest servletRequest) {
        if (servletRequest != null) {
            String contentTypeHeader = servletRequest.getHeader(CONTENT_TYPE.toString());
            try {
                byte[] bodyBytes = IOUtils.toByteArray(servletRequest.getInputStream());
                return bytesToBody(bodyBytes, contentTypeHeader);
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.EXCEPTION)
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while reading HttpServletRequest input stream")
                        .setThrowable(throwable)
                );
                throw new RuntimeException("IOException while reading HttpServletRequest input stream", throwable);
            }
        }
        return null;
    }

    private BodyWithContentType bytesToBody(byte[] bodyBytes, String contentTypeHeader) {
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
