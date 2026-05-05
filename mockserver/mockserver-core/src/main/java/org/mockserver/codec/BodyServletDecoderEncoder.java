package org.mockserver.codec;

import com.google.common.io.ByteStreams;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Body;
import org.mockserver.model.BodyWithContentType;
import org.mockserver.streams.IOStreamUtils;
import org.slf4j.event.Level;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

@SuppressWarnings("rawtypes")
public class BodyServletDecoderEncoder {

    private final MockServerLogger mockServerLogger;
    private final IOStreamUtils ioStreamUtils;
    private final BodyDecoderEncoder bodyDecoderEncoder = new BodyDecoderEncoder();

    public BodyServletDecoderEncoder(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        this.ioStreamUtils = new IOStreamUtils(mockServerLogger);
    }

    public void bodyToServletResponse(HttpServletResponse httpServletResponse, Body body, String contentTypeHeader) {
        byte[] bytes = bodyDecoderEncoder.bodyToBytes(body, contentTypeHeader);
        if (bytes != null) {
            ioStreamUtils.writeToOutputStream(bytes, httpServletResponse);
        }
    }

    public BodyWithContentType servletRequestToBody(HttpServletRequest servletRequest) {
        if (servletRequest != null) {
            String contentTypeHeader = servletRequest.getHeader(CONTENT_TYPE.toString());
            try {
                byte[] bodyBytes = ByteStreams.toByteArray(servletRequest.getInputStream());
                return bodyDecoderEncoder.bytesToBody(bodyBytes, contentTypeHeader);
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while reading HttpServletRequest input stream")
                        .setThrowable(throwable)
                );
                throw new RuntimeException("IOException while reading HttpServletRequest input stream", throwable);
            }
        }
        return null;
    }
}
