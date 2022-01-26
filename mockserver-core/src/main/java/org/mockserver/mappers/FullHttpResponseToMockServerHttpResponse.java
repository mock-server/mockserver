package org.mockserver.mappers;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.mockserver.codec.BodyDecoderEncoder;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.slf4j.event.Level;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

/**
 * @author jamesdbloom
 */
public class FullHttpResponseToMockServerHttpResponse {

    private final MockServerLogger mockServerLogger;
    private final BodyDecoderEncoder bodyDecoderEncoder;

    public FullHttpResponseToMockServerHttpResponse(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        this.bodyDecoderEncoder = new BodyDecoderEncoder();
    }

    public HttpResponse mapFullHttpResponseToMockServerResponse(FullHttpResponse fullHttpResponse) {
        HttpResponse httpResponse = new HttpResponse();
        try {
            if (fullHttpResponse != null) {
                if (fullHttpResponse.decoderResult().isFailure()) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception decoding response " + fullHttpResponse.decoderResult().cause().getMessage())
                            .setThrowable(fullHttpResponse.decoderResult().cause())
                    );
                }
                setStatusCode(httpResponse, fullHttpResponse);
                setHeaders(httpResponse, fullHttpResponse);
                setCookies(httpResponse);
                setBody(httpResponse, fullHttpResponse);
            }
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception decoding response{}")
                    .setArguments(fullHttpResponse)
                    .setThrowable(throwable)
            );
        }
        return httpResponse;
    }

    private void setStatusCode(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
        HttpResponseStatus status = fullHttpResponse.status();
        httpResponse.withStatusCode(status.code());
        httpResponse.withReasonPhrase(status.reasonPhrase());
    }

    private void setHeaders(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
        Headers headers = new Headers();
        for (String headerName : fullHttpResponse.headers().names()) {
            headers.withEntry(headerName, fullHttpResponse.headers().getAll(headerName));
        }
        if (!headers.isEmpty()) {
            httpResponse.withHeaders(headers);
        }
    }

    private void setCookies(HttpResponse httpResponse) {
        Cookies cookies = new Cookies();
        for (Header header : httpResponse.getHeaderList()) {
            if (header.getName().getValue().equalsIgnoreCase("Set-Cookie")) {
                for (NottableString cookieHeader : header.getValues()) {
                    io.netty.handler.codec.http.cookie.Cookie httpCookie = ClientCookieDecoder.LAX.decode(cookieHeader.getValue());
                    String name = httpCookie.name().trim();
                    String value = httpCookie.value() != null ? httpCookie.value().trim() : "";
                    cookies.withEntry(new Cookie(name, value));
                }
            }
            if (header.getName().getValue().equalsIgnoreCase("Cookie")) {
                for (NottableString cookieHeader : header.getValues()) {
                    for (io.netty.handler.codec.http.cookie.Cookie httpCookie : ServerCookieDecoder.LAX.decode(cookieHeader.getValue())) {
                        String name = httpCookie.name().trim();
                        String value = httpCookie.value() != null ? httpCookie.value().trim() : "";
                        cookies.withEntry(new Cookie(name, value));
                    }
                }
            }
        }
        if (!cookies.isEmpty()) {
            httpResponse.withCookies(cookies);
        }
    }

    private void setBody(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
        httpResponse.withBody(bodyDecoderEncoder.byteBufToBody(fullHttpResponse.content(), fullHttpResponse.headers().get(CONTENT_TYPE)));
    }
}
