package org.mockserver.codec;

import com.google.common.base.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.*;
import org.mockserver.model.HttpResponse;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;

/**
 * @author jamesdbloom
 */
public class MockServerResponseEncoder extends MessageToMessageEncoder<HttpResponse> {
    @Override
    protected void encode(ChannelHandlerContext ctx, HttpResponse response, List<Object> out) {
        DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf((response.getStatusCode() != null ? response.getStatusCode() : 200)),
                getBody(response)
        );
        setHeaders(response, defaultFullHttpResponse);
        setCookies(response, defaultFullHttpResponse);
        out.add(defaultFullHttpResponse);
    }

    private ByteBuf getBody(HttpResponse response) {
        ByteBuf content = Unpooled.buffer(0, 0);

        Body body = response.getBody();
        if (body != null) {
            Object bodyContents = body.getValue();
            Charset bodyCharset = body.getCharset(ContentTypeMapper.determineCharsetForMessage(response));
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

    private void setHeaders(HttpResponse response, DefaultFullHttpResponse httpServletResponse) {
        if (response.getHeaders() != null) {
            for (Header header : response.getHeaders()) {
                for (NottableString value : header.getValues()) {
                    httpServletResponse.headers().add(header.getName().getValue(), value.getValue());
                }
            }
        }

        if (Strings.isNullOrEmpty(response.getFirstHeader(CONTENT_TYPE))) {
            if (response.getBody() != null && !Strings.isNullOrEmpty(response.getBody().toString())) {
                Charset bodyCharset = response.getBody().getCharset(null);
                String bodyContentType = response.getBody().getContentType();
                if (bodyCharset != null) {
                    httpServletResponse.headers().set(CONTENT_TYPE, bodyContentType + "; charset=" + bodyCharset.name().toLowerCase());
                } else if (bodyContentType != null) {
                    httpServletResponse.headers().set(CONTENT_TYPE, bodyContentType);
                }
            }
        }
    }

    private void setCookies(HttpResponse response, DefaultFullHttpResponse httpServletResponse) {
        if (response.getCookies() != null) {
            List<Cookie> cookieValues = new ArrayList<Cookie>();
            for (org.mockserver.model.Cookie cookie : response.getCookies()) {
                cookieValues.add(new DefaultCookie(cookie.getName().getValue(), cookie.getValue().getValue()));
            }
            if (!cookieValues.isEmpty()) {
                httpServletResponse.headers().add(SET_COOKIE, ServerCookieEncoder.LAX.encode(cookieValues));
            }
        }
    }
}
