package org.mockserver.mappers;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.*;
import org.mockserver.url.URLParser;

import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;

/**
 * @author jamesdbloom
 */
public class NettyToMockServerRequestMapper {

    public HttpRequest mapNettyRequestToMockServerRequest(NettyHttpRequest mockServerHttpRequest) {
        HttpRequest httpRequest = new HttpRequest();
        if (mockServerHttpRequest != null) {
            setMethod(httpRequest, mockServerHttpRequest);
            setUrl(httpRequest, mockServerHttpRequest);
            setPath(httpRequest, mockServerHttpRequest);
            setQueryString(httpRequest, mockServerHttpRequest);
            setBody(httpRequest, mockServerHttpRequest);
            setHeaders(httpRequest, mockServerHttpRequest);
            setCookies(httpRequest, mockServerHttpRequest);
        }
        return httpRequest;
    }

    private void setMethod(HttpRequest httpRequest, NettyHttpRequest mockServerHttpRequest) {
        httpRequest.withMethod(mockServerHttpRequest.getMethod().name());
    }

    private void setUrl(HttpRequest httpRequest, NettyHttpRequest mockServerHttpRequest) {
        String hostAndPort = mockServerHttpRequest.headers().get(HttpHeaders.Names.HOST);
        String uri = mockServerHttpRequest.getUri();
        if (URLParser.isFullUrl(uri)) {
            httpRequest.withURL(uri);
        } else {
            httpRequest.withURL("http" + (mockServerHttpRequest.isSecure() ? "s" : "") + "://" + (hostAndPort != null ? hostAndPort : "localhost") + uri);
        }
    }

    private void setPath(HttpRequest httpRequest, NettyHttpRequest mockServerHttpRequest) {
        httpRequest.withPath(mockServerHttpRequest.path());
    }

    private void setQueryString(HttpRequest httpRequest, NettyHttpRequest mockServerHttpRequest) {
        httpRequest.withQueryStringParameters(mockServerHttpRequest.parameters());
    }

    private void setBody(HttpRequest httpRequest, NettyHttpRequest mockServerHttpRequest) {
        if (mockServerHttpRequest.content() != null) {
            byte[] bodyBytes = new byte[mockServerHttpRequest.content().readableBytes()];
            mockServerHttpRequest.content().readBytes(bodyBytes);
            httpRequest.setRawBodyBytes(bodyBytes);
            httpRequest.withBody(new StringBody(new String(bodyBytes, Charsets.UTF_8), Body.Type.STRING));
        }
    }

    private void setHeaders(HttpRequest httpRequest, NettyHttpRequest mockServerHttpRequest) {
        HttpHeaders headers = mockServerHttpRequest.headers();
        for (String headerName : headers.names()) {
            httpRequest.withHeader(new Header(headerName, headers.getAll(headerName)));
        }
    }

    private void setCookies(HttpRequest httpRequest, NettyHttpRequest mockServerHttpRequest) {
        for (String cookieHeader : mockServerHttpRequest.headers().getAll(COOKIE)) {
            for (String cookie : Splitter.on(";").split(cookieHeader)) {
                if (!cookie.trim().isEmpty()) {
                    httpRequest.withCookie(new Cookie(
                            StringUtils.substringBefore(cookie, "=").trim(),
                            StringUtils.substringAfter(cookie, "=").trim()
                    ));
                }
            }
        }
    }
}
