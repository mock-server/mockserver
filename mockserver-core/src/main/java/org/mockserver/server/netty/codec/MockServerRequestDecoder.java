package org.mockserver.server.netty.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.*;
import org.mockserver.url.URLParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.COOKIE;
import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;
import static org.mockserver.mappers.ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET;
import static org.mockserver.mappers.ContentTypeMapper.determineCharsetForMessage;

/**
 * @author jamesdbloom
 */
public class MockServerRequestDecoder extends MessageToMessageDecoder<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(MockServerRequestDecoder.class);
    private final boolean isSecure;

    public MockServerRequestDecoder(boolean isSecure) {
        this.isSecure = isSecure;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, List<Object> out) {
        out.add(decode(fullHttpRequest));
    }

    public HttpRequest decode(FullHttpRequest fullHttpRequest) {
        HttpRequest httpRequest = new HttpRequest();
        if (fullHttpRequest != null) {
            setMethod(httpRequest, fullHttpRequest);

            setPath(httpRequest, fullHttpRequest);
            setQueryString(httpRequest, new QueryStringDecoder(fullHttpRequest.uri()));

            setBody(httpRequest, fullHttpRequest);
            setHeaders(httpRequest, fullHttpRequest);
            setCookies(httpRequest, fullHttpRequest);

            httpRequest.withKeepAlive(isKeepAlive(fullHttpRequest));
            httpRequest.withSecure(isSecure);
        }
        return httpRequest;
    }

    private void setMethod(HttpRequest httpRequest, FullHttpRequest fullHttpResponse) {
        httpRequest.withMethod(fullHttpResponse.method().name());
    }

    private void setPath(HttpRequest httpRequest, FullHttpRequest fullHttpRequest) {
        httpRequest.withPath(URLParser.returnPath(fullHttpRequest.uri()));
    }

    private void setQueryString(HttpRequest httpRequest, QueryStringDecoder queryStringDecoder) {
        try {
            httpRequest.withQueryStringParameters(queryStringDecoder.parameters());
        } catch (IllegalArgumentException iae) {
            logger.debug("Exception while parsing query string", iae);
        }
    }

    private void setBody(HttpRequest httpRequest, FullHttpRequest fullHttpRequest) {
        if (fullHttpRequest.content() != null && fullHttpRequest.content().readableBytes() > 0) {
            byte[] bodyBytes = new byte[fullHttpRequest.content().readableBytes()];
            fullHttpRequest.content().readBytes(bodyBytes);
            if (bodyBytes.length > 0) {
                if (ContentTypeMapper.isBinary(fullHttpRequest.headers().get(CONTENT_TYPE))) {
                    httpRequest.withBody(new BinaryBody(bodyBytes));
                } else {
                    Charset requestCharset = determineCharsetForMessage(fullHttpRequest);
                    httpRequest.withBody(new StringBody(new String(bodyBytes, requestCharset), DEFAULT_HTTP_CHARACTER_SET.equals(requestCharset) ? null : requestCharset));
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
            Set<io.netty.handler.codec.http.cookie.Cookie> decodedCookies =
                    ServerCookieDecoder.LAX.decode(cookieHeader);
            for (io.netty.handler.codec.http.cookie.Cookie decodedCookie : decodedCookies) {
                httpRequest.withCookie(new Cookie(
                        decodedCookie.name(),
                        decodedCookie.value()
                ));
            }
        }
    }
}
