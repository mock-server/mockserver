package org.mockserver.client.netty.codec;

import com.google.common.base.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.*;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.OutboundHttpRequest;
import org.mockserver.model.Parameter;
import org.mockserver.url.URLEncoder;

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

    public static String getURI(OutboundHttpRequest httpRequest) {
        StringBuilder queryString = new StringBuilder();
        List<Parameter> queryStringParameters = httpRequest.getQueryStringParameters();
        for (int i = 0; i < queryStringParameters.size(); i++) {
            Parameter parameter = queryStringParameters.get(i);
            if (parameter.getValues().isEmpty()) {
                queryString.append(URLEncoder.encodeURL(parameter.getName()));
                queryString.append('=');
            } else {
                List<String> values = parameter.getValues();
                for (int j = 0; j < values.size(); j++) {
                    String value = values.get(j);
                    queryString.append(URLEncoder.encodeURL(parameter.getName()));
                    queryString.append('=');
                    queryString.append(URLEncoder.encodeURL(value));
                    if (j < (values.size() - 1)) {
                        queryString.append('&');
                    }
                }
            }
            if (i < (queryStringParameters.size() - 1)) {
                queryString.append('&');
            }
        }
        String contextPath = httpRequest.getContextPath();
        if (!Strings.isNullOrEmpty(contextPath)) {
            if (contextPath.endsWith("/")) {
                contextPath = contextPath.substring(0, contextPath.lastIndexOf("/"));
            }
            if (!contextPath.startsWith("/")) {
                contextPath = "/" + contextPath;
            }
        }
        return contextPath + httpRequest.getPath() + (queryString.length() > 0 ? '?' + queryString.toString() : "");
    }

    private ByteBuf getBody(HttpRequest httpRequest) {
        ByteBuf content = Unpooled.buffer(0);
        if (httpRequest.getBody() != null) {
            content = Unpooled.copiedBuffer(httpRequest.getBodyAsRawBytes());
        }
        return content;
    }

    private void setCookies(HttpRequest httpRequest, FullHttpRequest request) {
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
        if ((!httpRequest.isSecure() && httpRequest.getPort() != 80) ||
                (httpRequest.isSecure() && httpRequest.getPort() != 443)) {
            port = ":" + httpRequest.getPort();
        }
        request.headers().add(HOST, httpRequest.getHost() + port);
        request.headers().set(ACCEPT_ENCODING, GZIP + "," + DEFLATE);
        request.headers().set(CONTENT_LENGTH, request.content().readableBytes());
        if (isKeepAlive(request)) {
            request.headers().set(CONNECTION, KEEP_ALIVE);
        } else {
            request.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
        }
    }
}
