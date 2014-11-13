package org.mockserver.mappers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.BinaryBody;
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

    public FullHttpResponse mapMockServerResponseToNettyResponse(HttpResponse httpResponse) {
        if (httpResponse != null) {
            DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf((httpResponse.getStatusCode() != null ? httpResponse.getStatusCode() : 200)),
                    getBody(httpResponse)
            );
            setHeaders(httpResponse, defaultFullHttpResponse);
            setCookies(httpResponse, defaultFullHttpResponse);
            return defaultFullHttpResponse;
        } else {
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        }
    }

    private ByteBuf getBody(HttpResponse httpResponse) {
        ByteBuf content = Unpooled.buffer(0);
        if (httpResponse.getBodyAsString() != null) {
            if (httpResponse.getBody() instanceof BinaryBody) {
                content = Unpooled.copiedBuffer(Base64Converter.base64StringToBytes(httpResponse.getBodyAsString()));
            } else {
                content = Unpooled.copiedBuffer(httpResponse.getBodyAsString().getBytes());
            }
        }
        return content;
    }

    private void setHeaders(HttpResponse httpResponse, DefaultFullHttpResponse httpServletResponse) {
        if (httpResponse.getHeaders() != null) {
            for (Header header : httpResponse.getHeaders()) {
                for (String value : header.getValues()) {
                    httpServletResponse.headers().add(header.getName(), value);
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
