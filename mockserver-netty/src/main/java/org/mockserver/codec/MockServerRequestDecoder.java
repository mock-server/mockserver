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

import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

/**
 * @author jamesdbloom
 */
public class MockServerRequestDecoder extends MessageToMessageDecoder<FullHttpRequest> {

    private final boolean isSecure;

    public MockServerRequestDecoder(boolean isSecure) {
        this.isSecure = isSecure;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpRequest fullHttpResponse, List<Object> out) {
        HttpRequest httpRequest = new HttpRequest();
        if (fullHttpResponse != null) {
            setMethod(httpRequest, fullHttpResponse);

            QueryStringDecoder queryStringDecoder = new QueryStringDecoder(fullHttpResponse.getUri());
            setPath(httpRequest, queryStringDecoder);
            setQueryString(httpRequest, queryStringDecoder);

            setBody(httpRequest, fullHttpResponse);
            setHeaders(httpRequest, fullHttpResponse);
            setCookies(httpRequest, fullHttpResponse);

            httpRequest.setKeepAlive(isKeepAlive(fullHttpResponse));
            httpRequest.setSecure(isSecure);
        }
        out.add(httpRequest);
    }

    private void setMethod(HttpRequest httpRequest, FullHttpRequest fullHttpResponse) {
        httpRequest.withMethod(fullHttpResponse.getMethod().name());
    }

    private void setPath(HttpRequest httpRequest, QueryStringDecoder queryStringDecoder) {
        httpRequest.withPath(URLParser.returnPath(queryStringDecoder.path()));
    }

    private void setQueryString(HttpRequest httpRequest, QueryStringDecoder queryStringDecoder) {
        httpRequest.withQueryStringParameters(queryStringDecoder.parameters());
    }

    private void setBody(HttpRequest httpRequest, FullHttpRequest fullHttpResponse) {
        if (fullHttpResponse.content() != null && fullHttpResponse.content().readableBytes() > 0) {
            byte[] bodyBytes = new byte[fullHttpResponse.content().readableBytes()];
            fullHttpResponse.content().readBytes(bodyBytes);
            if (bodyBytes.length > 0) {
                if (ContentTypeMapper.isBinary(fullHttpResponse.headers().get(HttpHeaders.Names.CONTENT_TYPE))) {
                    httpRequest.withBody(new BinaryBody(bodyBytes));
                } else {
                    httpRequest.withBody(new StringBody(new String(bodyBytes, Charsets.UTF_8), bodyBytes));
                }
            }
        }
    }

    private void setHeaders(HttpRequest httpRequest, FullHttpRequest fullHttpResponse) {
        HttpHeaders headers = fullHttpResponse.headers();
        for (String headerName : headers.names()) {
            httpRequest.withHeader(new Header(headerName, headers.getAll(headerName)));
        }
    }

    private void setCookies(HttpRequest httpRequest, FullHttpRequest fullHttpResponse) {
        for (String cookieHeader : fullHttpResponse.headers().getAll(COOKIE)) {
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
