package org.mockserver.mappers;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;

/**
 * @author jamesdbloom
 */
public class MockServerToNettyResponseMapper {

    public DefaultFullHttpResponse mapMockServerResponseToNettyResponse(HttpResponse httpResponse) {
        if (httpResponse != null) {
            DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf((httpResponse.getStatusCode() != null ? httpResponse.getStatusCode() : 200)),
                    (httpResponse.getBody() != null ? Unpooled.copiedBuffer(httpResponse.getBody()) : Unpooled.buffer(0))
            );
            setHeaders(httpResponse, defaultFullHttpResponse);
            setCookies(httpResponse, defaultFullHttpResponse);
            return defaultFullHttpResponse;
        } else {
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        }
    }

    private void setHeaders(HttpResponse httpResponse, DefaultFullHttpResponse httpServletResponse) {
        if (httpResponse.getHeaders() != null) {
            for (Header header : httpResponse.getHeaders()) {
                for (String value : header.getValues()) {
                    httpServletResponse.headers().set(header.getName(), value);
                }
            }
        }
    }

    private void setCookies(HttpResponse httpResponse, DefaultFullHttpResponse httpServletResponse) {
        if (httpResponse.getCookies() != null) {
            List<String> cookieValues = new ArrayList<String>();
            for (Cookie cookie : httpResponse.getCookies()) {
                for (String value : cookie.getValues()) {
                    cookieValues.add(ServerCookieEncoder.encode(new DefaultCookie(cookie.getName(), value)));
                }
            }
            if (!cookieValues.isEmpty()) {
                httpServletResponse.headers().add(SET_COOKIE, cookieValues);
            }
        }
    }
}
