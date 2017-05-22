package org.mockserver.client.netty.codec.mappers;

import com.google.common.base.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.*;
import org.mockserver.model.HttpRequest;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;

/**
 * @author jamesdbloom
 */
public class MockServerHttpRequestToFullHttpRequest {

    public FullHttpRequest mapMockServerResponseToHttpServletResponse(HttpRequest httpRequest) {
        // method
        HttpMethod httpMethod = HttpMethod.valueOf(httpRequest.getMethod("GET"));

        // the request
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, httpMethod, getURI(httpRequest), getBody(httpRequest));

        // headers
        setHeader(httpRequest, request);

        // cookies
        setCookies(httpRequest, request);

        return request;
    }

    public String getURI(HttpRequest httpRequest) {
        QueryStringEncoder queryStringEncoder = new QueryStringEncoder(httpRequest.getPath().getValue());
        for (Parameter parameter : httpRequest.getQueryStringParameters()) {
            for (NottableString value : parameter.getValues()) {
                queryStringEncoder.addParam(parameter.getName().getValue(), value.getValue());
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
        List<io.netty.handler.codec.http.cookie.Cookie> cookies = new ArrayList<io.netty.handler.codec.http.cookie.Cookie>();
        for (org.mockserver.model.Cookie cookie : httpRequest.getCookies()) {
            cookies.add(new io.netty.handler.codec.http.cookie.DefaultCookie(cookie.getName().getValue(), cookie.getValue().getValue()));
        }
        if (cookies.size() > 0) {
            request.headers().set(
                    COOKIE.toString(),
                    io.netty.handler.codec.http.cookie.ClientCookieEncoder.LAX.encode(cookies)
            );
        }
    }

    private void setHeader(HttpRequest httpRequest, FullHttpRequest request) {
        for (Header header : httpRequest.getHeaders()) {
            String headerName = header.getName().getValue();
            // do not set hop-by-hop headers
            if (!headerName.equalsIgnoreCase(CONTENT_LENGTH.toString())
                    && !headerName.equalsIgnoreCase(TRANSFER_ENCODING.toString())
                    && !headerName.equalsIgnoreCase(HOST.toString())
                    && !headerName.equalsIgnoreCase(ACCEPT_ENCODING.toString())) {
                if (!header.getValues().isEmpty()) {
                    for (NottableString headerValue : header.getValues()) {
                        request.headers().add(headerName, headerValue.getValue());
                    }
                } else {
                    request.headers().add(headerName, "");
                }
            }
        }

        if (!Strings.isNullOrEmpty(httpRequest.getFirstHeader(HOST.toString()))) {
            request.headers().add(HOST, httpRequest.getFirstHeader(HOST.toString()));
        }
        request.headers().set(ACCEPT_ENCODING, GZIP + "," + DEFLATE);
        request.headers().set(CONTENT_LENGTH, request.content().readableBytes());
        if (isKeepAlive(request)) {
            request.headers().set(CONNECTION, KEEP_ALIVE);
        } else {
            request.headers().set(CONNECTION, CLOSE);
        }

        if (!request.headers().contains(CONTENT_TYPE)) {
            if (httpRequest.getBody() != null
                    && httpRequest.getBody().getContentType() != null) {
                request.headers().set(CONTENT_TYPE, httpRequest.getBody().getContentType());
            }
        }
    }
}
