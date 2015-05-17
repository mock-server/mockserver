package org.mockserver.client.netty.codec;

import com.google.common.base.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.*;
import org.mockserver.model.HttpRequest;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

/**
 * @author jamesdbloom
 */
public class MockServerRequestEncoder extends MessageToMessageEncoder<OutboundHttpRequest> {

    @Override
    protected void encode(ChannelHandlerContext ctx, OutboundHttpRequest httpRequest, List<Object> out) {
        // method
        HttpMethod httpMethod = HttpMethod.valueOf(httpRequest.getMethod("GET"));

        // the request
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, httpMethod, getURI(httpRequest), getBody(httpRequest));

        // headers
        setHeader(httpRequest, request);

        // cookies
        setCookies(httpRequest, request);

        out.add(request);
    }

    public String getURI(OutboundHttpRequest httpRequest) {
        String contextPath = httpRequest.getContextPath();
        if (!Strings.isNullOrEmpty(contextPath)) {
            if (contextPath.endsWith("/")) {
                contextPath = contextPath.substring(0, contextPath.lastIndexOf("/"));
            }
            if (!contextPath.startsWith("/")) {
                contextPath = "/" + contextPath;
            }
        }
        QueryStringEncoder queryStringEncoder = new QueryStringEncoder(contextPath + httpRequest.getPath().getValue());
        for (Parameter parameter : httpRequest.getQueryStringParameters()) {
            for (String value : parameter.getValues()) {
                queryStringEncoder.addParam(parameter.getName(), value);
            }
        }
        return queryStringEncoder.toString();
    }

    private ByteBuf getBody(HttpRequest httpRequest) {
        ByteBuf content = Unpooled.buffer(0, 0);

        Body body = httpRequest.getBody();
        if (body != null) {
            Object bodyContents = body.getValue();
            Charset bodyCharset = body.getCharset(ContentTypeMapper.determineCharsetForMessage(httpRequest));
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

    private void setCookies(HttpRequest httpRequest, FullHttpRequest request) {
        List<Cookie> cookies = new ArrayList<Cookie>();
        for (org.mockserver.model.Cookie cookie : httpRequest.getCookies()) {
            cookies.add(new DefaultCookie(cookie.getName(), cookie.getValue()));
        }
        if (cookies.size() > 0) {
            request.headers().set(
                    HttpHeaders.Names.COOKIE,
                    ClientCookieEncoder.LAX.encode(cookies)
            );
        }
    }

    private void setHeader(OutboundHttpRequest httpRequest, FullHttpRequest request) {
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

        String port = "";
        if ((!httpRequest.isSecure() && httpRequest.getDestination().getPort() != 80) ||
                (httpRequest.isSecure() && httpRequest.getDestination().getPort() != 443)) {
            port = ":" + httpRequest.getDestination().getPort();
        }
        request.headers().add(HOST, httpRequest.getDestination().getHostName() + port);
        request.headers().set(ACCEPT_ENCODING, GZIP + "," + DEFLATE);
        request.headers().set(CONTENT_LENGTH, request.content().readableBytes());
        if (isKeepAlive(request)) {
            request.headers().set(CONNECTION, KEEP_ALIVE);
        } else {
            request.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
        }

        if (!request.headers().contains(CONTENT_TYPE)) {
            if (httpRequest.getBody() != null) {
                Charset bodyCharset = httpRequest.getBody().getCharset(null);
                String bodyContentType = httpRequest.getBody().getContentType();
                if (bodyCharset != null) {
                    request.headers().set(CONTENT_TYPE, bodyContentType + "; charset=" + bodyCharset.name().toLowerCase());
                } else if (bodyContentType != null) {
                    request.headers().set(CONTENT_TYPE, bodyContentType);
                }
            }
        }
    }
}
