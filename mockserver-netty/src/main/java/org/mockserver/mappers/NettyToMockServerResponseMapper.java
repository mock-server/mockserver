package org.mockserver.mappers;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.NettyHttpResponse;

import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;

/**
 * @author jamesdbloom
 */
public class NettyToMockServerResponseMapper {

    public HttpResponse mapNettyRequestToMockServerResponse(NettyHttpResponse mockServerHttpRequest) {
        HttpResponse httpRequest = new HttpResponse();
        setStatusCode(httpRequest, mockServerHttpRequest);
        setHeaders(httpRequest, mockServerHttpRequest);
        setCookies(httpRequest, mockServerHttpRequest);
        setBody(httpRequest, mockServerHttpRequest);
        return httpRequest;
    }

    private void setStatusCode(HttpResponse httpResponse, NettyHttpResponse mockServerHttpResponse) {
        httpResponse.withStatusCode(mockServerHttpResponse.getStatus().code());
    }

    private void setBody(HttpResponse httpRequest, NettyHttpResponse mockServerHttpRequest) {
        if (mockServerHttpRequest.content() != null) {
            httpRequest.withBody(mockServerHttpRequest.content().toString(Charsets.UTF_8));
        }
    }

    private void setHeaders(HttpResponse httpResponse, NettyHttpResponse mockServerHttpResponse) {
        HttpHeaders headers = mockServerHttpResponse.headers();
        for (String headerName : headers.names()) {
            httpResponse.withHeader(new Header(headerName, headers.getAll(headerName)));
        }
    }

    private void setCookies(HttpResponse httpResponse, NettyHttpResponse mockServerHttpResponse) {
        for (String cookieHeader : mockServerHttpResponse.headers().getAll(COOKIE)) {
            for (String cookie : Splitter.on(";").split(cookieHeader)) {
                httpResponse.withCookie(new Cookie(
                        StringUtils.substringBefore(cookie, "=").trim(),
                        StringUtils.substringAfter(cookie, "=").trim()
                ));
            }
        }
    }
}
