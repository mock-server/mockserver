package org.mockserver.mappers;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import org.mockserver.codec.BodyDecoderEncoder;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.NottableString;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * @author jamesdbloom
 */
public class MockServerHttpResponseToFullHttpResponse {

    private final MockServerLogger mockServerLogger;
    private final BodyDecoderEncoder bodyDecoderEncoder;

    public MockServerHttpResponseToFullHttpResponse(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        this.bodyDecoderEncoder = new BodyDecoderEncoder();
    }

    public List<DefaultHttpObject> mapMockServerResponseToNettyResponse(HttpResponse httpResponse) {
        try {
            ConnectionOptions connectionOptions = httpResponse.getConnectionOptions();
            if (connectionOptions != null && connectionOptions.getChunkSize() != null && connectionOptions.getChunkSize() > 0) {
                List<DefaultHttpObject> httpMessages = new ArrayList<>();
                ByteBuf body = getBody(httpResponse);
                DefaultHttpResponse defaultHttpResponse = new DefaultHttpResponse(
                    HttpVersion.HTTP_1_1,
                    getStatus(httpResponse)
                );
                setHeaders(httpResponse, defaultHttpResponse, body);
                HttpUtil.setTransferEncodingChunked(defaultHttpResponse, true);
                setCookies(httpResponse, defaultHttpResponse);
                httpMessages.add(defaultHttpResponse);

                ByteBuf[] chunks = bodyDecoderEncoder.bodyToByteBuf(httpResponse.getBody(), httpResponse.getFirstHeader(CONTENT_TYPE.toString()), connectionOptions.getChunkSize());
                for (int i = 0; i < chunks.length - 1; i++) {
                    DefaultHttpContent defaultHttpContent = new DefaultHttpContent(chunks[i]);
                    httpMessages.add(defaultHttpContent);
                }
                httpMessages.add(new DefaultLastHttpContent(chunks[chunks.length - 1]));
                return httpMessages;
            } else {
                ByteBuf body = getBody(httpResponse);
                DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    getStatus(httpResponse),
                    body
                );
                setHeaders(httpResponse, defaultFullHttpResponse, body);
                setCookies(httpResponse, defaultFullHttpResponse);
                return Collections.singletonList(defaultFullHttpResponse);
            }
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception encoding response{}")
                    .setArguments(httpResponse)
                    .setThrowable(throwable)
            );
            return Collections.singletonList(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, getStatus(httpResponse)));
        }
    }

    private HttpResponseStatus getStatus(HttpResponse httpResponse) {
        int statusCode = httpResponse.getStatusCode() != null ? httpResponse.getStatusCode() : 200;
        if (!isEmpty(httpResponse.getReasonPhrase())) {
            return new HttpResponseStatus(statusCode, httpResponse.getReasonPhrase());
        } else {
            return HttpResponseStatus.valueOf(statusCode);
        }
    }

    private ByteBuf getBody(HttpResponse httpResponse) {
        return bodyDecoderEncoder.bodyToByteBuf(httpResponse.getBody(), httpResponse.getFirstHeader(CONTENT_TYPE.toString()));
    }

    private void setHeaders(HttpResponse httpResponse, DefaultHttpResponse response, ByteBuf body) {
        if (httpResponse.getHeaderMultimap() != null) {
            httpResponse
                .getHeaderMultimap()
                .entries()
                .forEach(entry ->
                    response
                        .headers()
                        .add(entry.getKey().getValue(), entry.getValue().getValue())
                );
        }

        // Content-Type
        if (isBlank(httpResponse.getFirstHeader(CONTENT_TYPE.toString()))) {
            if (httpResponse.getBody() != null
                && httpResponse.getBody().getContentType() != null) {
                response.headers().set(CONTENT_TYPE, httpResponse.getBody().getContentType());
            }
        }

        // Content-Length
        ConnectionOptions connectionOptions = httpResponse.getConnectionOptions();
        if (isBlank(httpResponse.getFirstHeader(CONTENT_LENGTH.toString()))) {
            boolean overrideContentLength = connectionOptions != null && connectionOptions.getContentLengthHeaderOverride() != null;
            boolean addContentLength = connectionOptions == null || !Boolean.TRUE.equals(connectionOptions.getSuppressContentLengthHeader());
            boolean chunkedEncoding = (connectionOptions != null && connectionOptions.getChunkSize() != null) || response.headers().contains(HttpHeaderNames.TRANSFER_ENCODING);
            if (overrideContentLength) {
                response.headers().set(CONTENT_LENGTH, connectionOptions.getContentLengthHeaderOverride());
            } else if (addContentLength && !chunkedEncoding) {
                response.headers().set(CONTENT_LENGTH, body.readableBytes());
            }
            if (chunkedEncoding) {
                response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }
        }
    }

    private void setCookies(HttpResponse httpResponse, DefaultHttpResponse response) {
        if (httpResponse.getCookieMap() != null) {
            for (Map.Entry<NottableString, NottableString> cookie : httpResponse.getCookieMap().entrySet()) {
                if (httpResponse.cookieHeadeDoesNotAlreadyExists(cookie.getKey().getValue(), cookie.getValue().getValue())) {
                    response.headers().add(SET_COOKIE, io.netty.handler.codec.http.cookie.ServerCookieEncoder.LAX.encode(new DefaultCookie(cookie.getKey().getValue(), cookie.getValue().getValue())));
                }
            }
        }
    }
}
