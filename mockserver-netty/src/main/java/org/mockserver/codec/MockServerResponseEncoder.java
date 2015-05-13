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

import java.nio.charset.Charset;
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
        ByteBuf content = Unpooled.buffer(0, 0);
        if (httpResponse.getBody() != null) {
            if (httpResponse.getBody() instanceof BinaryBody) {
                content = Unpooled.copiedBuffer(httpResponse.getBody().getRawBytes());
            } else {
                // use response charset (if set)
                Charset charset = ContentTypeMapper.determineCharsetFromResponseContentType(httpResponse);

                // unless StringBody has charset defined
                if (httpResponse.getBody() instanceof StringBody) {
                    StringBody stringBody = (StringBody) httpResponse.getBody();
                    if (stringBody.getCharset() != null) {
                        charset = stringBody.getCharset();
                    }
                }

                content = Unpooled.copiedBuffer(httpResponse.getBodyAsString().getBytes(charset));
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
