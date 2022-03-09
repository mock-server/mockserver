package org.mockserver.mappers;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.mockserver.codec.BodyDecoderEncoder;
import org.mockserver.codec.ExpandedParameterDecoder;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Cookies;
import org.mockserver.model.Headers;
import org.mockserver.model.HttpRequest;
import org.mockserver.url.URLParser;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.cert.Certificate;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.COOKIE;
import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;

/**
 * @author jamesdbloom
 */
public class FullHttpRequestToMockServerHttpRequest {

    private final MockServerLogger mockServerLogger;
    private final BodyDecoderEncoder bodyDecoderEncoder;
    private final ExpandedParameterDecoder formParameterParser;
    private final boolean isSecure;
    private final Certificate[] clientCertificates;
    private final Integer port;
    private final JDKCertificateToMockServerX509Certificate jdkCertificateToMockServerX509Certificate;

    public FullHttpRequestToMockServerHttpRequest(Configuration configuration, MockServerLogger mockServerLogger, boolean isSecure, Certificate[] clientCertificates, Integer port) {
        this.mockServerLogger = mockServerLogger;
        this.bodyDecoderEncoder = new BodyDecoderEncoder();
        this.formParameterParser = new ExpandedParameterDecoder(configuration, mockServerLogger);
        this.isSecure = isSecure;
        this.clientCertificates = clientCertificates;
        this.port = port;
        this.jdkCertificateToMockServerX509Certificate = new JDKCertificateToMockServerX509Certificate(mockServerLogger);
    }

    public HttpRequest mapFullHttpRequestToMockServerRequest(FullHttpRequest fullHttpRequest, SocketAddress remoteAddress) {
        HttpRequest httpRequest = new HttpRequest();
        try {
            if (fullHttpRequest != null) {
                if (fullHttpRequest.decoderResult().isFailure()) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception decoding request " + fullHttpRequest.decoderResult().cause().getMessage())
                            .setThrowable(fullHttpRequest.decoderResult().cause())
                    );
                }
                setMethod(httpRequest, fullHttpRequest);

                setPath(httpRequest, fullHttpRequest);
                setQueryString(httpRequest, fullHttpRequest);
                setHeaders(httpRequest, fullHttpRequest);
                setCookies(httpRequest, fullHttpRequest);
                setBody(httpRequest, fullHttpRequest);
                setSocketAddress(httpRequest, fullHttpRequest, isSecure, port, remoteAddress);
                jdkCertificateToMockServerX509Certificate.setClientCertificates(httpRequest, clientCertificates);

                httpRequest.withKeepAlive(isKeepAlive(fullHttpRequest));
                httpRequest.withSecure(isSecure);
            }
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception decoding request{}")
                    .setArguments(fullHttpRequest)
                    .setThrowable(throwable)
            );
        }
        return httpRequest;
    }

    private void setSocketAddress(HttpRequest httpRequest, FullHttpRequest fullHttpRequest, boolean isSecure, Integer port, SocketAddress remoteAddress) {
        httpRequest.withSocketAddress(isSecure, fullHttpRequest.headers().get("host"), port);
        if (remoteAddress instanceof InetSocketAddress) {
            httpRequest.withRemoteAddress(((InetSocketAddress) remoteAddress).getHostString());
        }
    }

    private void setMethod(HttpRequest httpRequest, FullHttpRequest fullHttpResponse) {
        httpRequest.withMethod(fullHttpResponse.method().name());
    }

    private void setPath(HttpRequest httpRequest, FullHttpRequest fullHttpRequest) {
        httpRequest.withPath(URLParser.returnPath(fullHttpRequest.uri()));
    }

    private void setQueryString(HttpRequest httpRequest, FullHttpRequest fullHttpRequest) {
        if (fullHttpRequest.uri().contains("?")) {
            httpRequest.withQueryStringParameters(formParameterParser.retrieveQueryParameters(fullHttpRequest.uri(), true));
        }
    }

    private void setHeaders(HttpRequest httpRequest, FullHttpRequest fullHttpResponse) {
        Headers headers = new Headers();
        HttpHeaders httpHeaders = fullHttpResponse.headers();
        for (String headerName : httpHeaders.names()) {
            headers.withEntry(headerName, httpHeaders.getAll(headerName));
        }
        httpRequest.withHeaders(headers);
    }

    private void setCookies(HttpRequest httpRequest, FullHttpRequest fullHttpResponse) {
        Cookies cookies = new Cookies();
        for (String cookieHeader : fullHttpResponse.headers().getAll(COOKIE)) {
            Set<Cookie> decodedCookies =
                ServerCookieDecoder.LAX.decode(cookieHeader);
            for (io.netty.handler.codec.http.cookie.Cookie decodedCookie : decodedCookies) {
                cookies.withEntry(
                    decodedCookie.name(),
                    decodedCookie.value()
                );
            }
        }
        httpRequest.withCookies(cookies);
    }

    private void setBody(HttpRequest httpRequest, FullHttpRequest fullHttpRequest) {
        httpRequest.withBody(bodyDecoderEncoder.byteBufToBody(fullHttpRequest.content(), fullHttpRequest.headers().get(CONTENT_TYPE)));
    }
}
