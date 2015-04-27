package org.mockserver.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.*;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.StringBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.internet.ContentType;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;

/**
 * @author jamesdbloom
 */
public class MockServerResponseEncoder extends MessageToMessageEncoder<HttpResponse> {
    @Override
    protected void encode(ChannelHandlerContext ctx, HttpResponse httpResponse, List<Object> out) {
        DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf((httpResponse.getStatusCode() != null ? httpResponse.getStatusCode() : 200)),
                getBody(httpResponse)
        );
        setHeaders(httpResponse, defaultFullHttpResponse);
        setCookies(httpResponse, defaultFullHttpResponse);
        out.add(defaultFullHttpResponse);
    }

    private ByteBuf getBody(HttpResponse httpResponse) {
        if (httpResponse.getBody() != null) {
            if (httpResponse.getBody() instanceof BinaryBody) {
                // if the body is binary, copy the bytes from the body object verbatim.
                return Unpooled.copiedBuffer(httpResponse.getBody().getRawBytes());
            } else {
                // if the body is a StringBody, use the character set on the StringBody if present. otherwise,
                // derive the character set from the response headers and encode the string using that character set.
                Charset charset;
                if (httpResponse.getBody() instanceof StringBody && ((StringBody) httpResponse.getBody()).getCharset() != null) {
                    charset = ((StringBody) httpResponse.getBody()).getCharset();
                } else {
                    charset = ContentTypeMapper.identifyCharsetFromResponse(httpResponse);
                }

                return Unpooled.copiedBuffer(httpResponse.getBodyAsString().getBytes(charset));
            }
        } else {
            // no body content set, so return a zero-length buffer
            return Unpooled.buffer(0, 0);
        }
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
            List<Cookie> cookieValues = new ArrayList<Cookie>();
            for (org.mockserver.model.Cookie cookie : httpResponse.getCookies()) {
                cookieValues.add(new DefaultCookie(cookie.getName(), cookie.getValue()));
            }
            if (!cookieValues.isEmpty()) {
                httpServletResponse.headers().add(SET_COOKIE, ServerCookieEncoder.encode(cookieValues));
            }
        }
    }
}
