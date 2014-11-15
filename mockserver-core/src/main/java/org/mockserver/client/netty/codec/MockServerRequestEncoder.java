package org.mockserver.client.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.*;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.mappers.URIMapper;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT_ENCODING;
import static io.netty.handler.codec.http.HttpHeaders.Values.GZIP;
import static io.netty.handler.codec.http.HttpHeaders.Values.DEFLATE;
import static io.netty.handler.codec.http.HttpHeaders.Values.CLOSE;
import static io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

/**
 * @author jamesdbloom
 */
public class MockServerRequestEncoder extends MessageToMessageEncoder<HttpRequest> {

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpRequest httpRequest, List<Object> out) throws Exception {
        // url
        URI uri = URIMapper.getURI(httpRequest);

        // method
        HttpMethod httpMethod = HttpMethod.valueOf(httpRequest.getMethod("GET"));

        // the request
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, httpMethod, StringUtils.substringAfter(uri.getRawSchemeSpecificPart(), uri.getRawAuthority()), getBody(httpRequest));

        // headers
        setHeader(httpRequest, uri, request);

        // cookies
        setCookies(httpRequest, request);

        out.add(request);
    }

    private ByteBuf getBody(HttpRequest httpRequest) {
        ByteBuf content = Unpooled.buffer(0);
        if (httpRequest.getBody() != null) {
            content = Unpooled.copiedBuffer(httpRequest.getBodyAsRawBytes());
        }
        return content;
    }

    private void setCookies(HttpRequest httpRequest, DefaultFullHttpRequest request) {
        List<io.netty.handler.codec.http.Cookie> cookies = new ArrayList<Cookie>();
        for (org.mockserver.model.Cookie cookie : httpRequest.getCookies()) {
            if (!cookie.getValues().isEmpty()) {
                for (String value : cookie.getValues()) {
                    cookies.add(new DefaultCookie(cookie.getName(), value));
                }
            } else {
                cookies.add(new DefaultCookie(cookie.getName(), ""));
            }
        }
        if (cookies.size() > 0) {
            request.headers().set(
                    HttpHeaders.Names.COOKIE,
                    ClientCookieEncoder.encode(cookies)
            );
        }
    }

    private void setHeader(HttpRequest httpRequest, URI uri, DefaultFullHttpRequest request) {
        for (Header header : httpRequest.getHeaders()) {
            String headerName = header.getName();
            // do not set hop-by-hop headers
            if (!headerName.equalsIgnoreCase(CONTENT_LENGTH)
                    && !headerName.equalsIgnoreCase(TRANSFER_ENCODING)
                    && !headerName.equalsIgnoreCase(HOST)
                    && !headerName.equalsIgnoreCase(ACCEPT_ENCODING)) {
                if (!header.getValues().isEmpty()) {
                    for (String headerValue : header.getValues()) {
                        request.headers().add(headerName, headerValue);
                    }
                } else {
                    request.headers().add(headerName, "");
                }
            }
        }

        request.headers().set(HOST, StringUtils.substringAfter(uri.getRawAuthority(), (uri.getRawUserInfo() != null ? uri.getRawUserInfo() : "")));
        request.headers().set(ACCEPT_ENCODING, GZIP + "," + DEFLATE);
        request.headers().set(CONTENT_LENGTH, request.content().readableBytes());
        if (isKeepAlive(request)) {
            request.headers().set(CONNECTION, KEEP_ALIVE);
        } else {
            request.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
        }
    }
}
