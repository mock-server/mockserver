package org.mockserver.codec;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.*;
import org.mockserver.url.URLParser;

import java.util.*;

import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;

/**
 * @author jamesdbloom
 */
public class MockServerRequestDecoder extends MessageToMessageDecoder<FullHttpRequest> {

    private final boolean isSecure;

    public MockServerRequestDecoder(boolean isSecure) {
        this.isSecure = isSecure;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpRequest fullHttpResponse, List<Object> out) throws Exception {
        HttpRequest httpRequest = new HttpRequest();
        if (fullHttpResponse != null) {
            setMethod(httpRequest, fullHttpResponse);
            setUrl(httpRequest, isSecure, fullHttpResponse);

            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(fullHttpResponse.getUri());
            setPath(httpRequest, queryStringDecoder);
            setQueryString(httpRequest, queryStringDecoder);

            setBody(httpRequest, fullHttpResponse);
            setHeaders(httpRequest, fullHttpResponse);
            setCookies(httpRequest, fullHttpResponse);
        }
        out.add(httpRequest);
    }

    private void setMethod(HttpRequest httpRequest, FullHttpRequest nettyHttpRequest) {
        httpRequest.withMethod(nettyHttpRequest.getMethod().name());
    }

    private void setUrl(HttpRequest httpRequest, boolean secure, FullHttpRequest nettyHttpRequest) {
        String hostAndPort = nettyHttpRequest.headers().get(HttpHeaders.Names.HOST);
        String uri = nettyHttpRequest.getUri();
        if (URLParser.isFullUrl(uri)) {
            httpRequest.withURL(uri);
        } else {
            httpRequest.withURL("http" + (secure ? "s" : "") + "://" + (hostAndPort != null ? hostAndPort : "localhost") + uri);
        }
    }

    private void setPath(HttpRequest httpRequest, QueryStringDecoder queryStringDecoder) {
        httpRequest.withPath(URLParser.returnPath(queryStringDecoder.path()));
    }

    private void setQueryString(HttpRequest httpRequest, QueryStringDecoder queryStringDecoder) {
        httpRequest.withQueryStringParameters(queryStringDecoder.parameters());
    }

    private void setBody(HttpRequest httpRequest, FullHttpRequest nettyHttpRequest) {
        if (nettyHttpRequest.content() != null && nettyHttpRequest.content().readableBytes() > 0) {
            byte[] bodyBytes = new byte[nettyHttpRequest.content().readableBytes()];
            nettyHttpRequest.content().readBytes(bodyBytes);
            if (bodyBytes.length > 0) {
                if (ContentTypeMapper.isBinary(nettyHttpRequest.headers().get(HttpHeaders.Names.CONTENT_TYPE))) {
                    httpRequest.withBody(new BinaryBody(bodyBytes));
                } else {
                    httpRequest.withBody(new StringBody(new String(bodyBytes, Charsets.UTF_8), bodyBytes));
                }
            }
        }
    }

    private void setHeaders(HttpRequest httpRequest, FullHttpRequest nettyHttpRequest) {
        HttpHeaders headers = nettyHttpRequest.headers();
        for (String headerName : headers.names()) {
            httpRequest.withHeader(new Header(headerName, headers.getAll(headerName)));
        }
    }

    private void setCookies(HttpRequest httpRequest, FullHttpRequest nettyHttpRequest) {
        for (String cookieHeader : nettyHttpRequest.headers().getAll(COOKIE)) {
            for (String cookie : Splitter.on(";").split(cookieHeader)) {
                if (!cookie.trim().isEmpty()) {
                    httpRequest.withCookie(new Cookie(
                            StringUtils.substringBefore(cookie, "=").trim(),
                            StringUtils.substringAfter(cookie, "=").trim()
                    ));
                }
            }
        }
    }
}
