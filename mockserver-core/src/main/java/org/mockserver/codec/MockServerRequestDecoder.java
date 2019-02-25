package org.mockserver.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.url.URLParser;

import java.util.List;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.COOKIE;
import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;

/**
 * @author jamesdbloom
 */
public class MockServerRequestDecoder extends MessageToMessageDecoder<FullHttpRequest> {

    private final MockServerLogger mockServerLogger;
    private final boolean isSecure;

    public MockServerRequestDecoder(MockServerLogger mockServerLogger, boolean isSecure) {
        this.mockServerLogger = mockServerLogger;
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

            setHeaders(httpRequest, fullHttpRequest);
            setCookies(httpRequest, fullHttpRequest);
            setBody(httpRequest, fullHttpRequest);

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
        Parameters parameters = new Parameters();
        try {
            parameters.withEntries(queryStringDecoder.parameters());
        } catch (IllegalArgumentException iae) {
            mockServerLogger.error(httpRequest, "Exception while parsing query string", iae);
        }
        httpRequest.withQueryStringParameters(parameters);
    }

    private void setHeaders(HttpRequest httpRequest, FullHttpRequest fullHttpResponse) {
        Headers headers = new Headers();
        HttpHeaders httpHeaders = fullHttpResponse.headers();
        for (String headerName : httpHeaders.names()) {
            headers.withEntry(new Header(headerName, httpHeaders.getAll(headerName)));
        }
        httpRequest.withHeaders(headers);
    }

    private void setCookies(HttpRequest httpRequest, FullHttpRequest fullHttpResponse) {
        Cookies cookies = new Cookies();
        for (String cookieHeader : fullHttpResponse.headers().getAll(COOKIE)) {
            Set<io.netty.handler.codec.http.cookie.Cookie> decodedCookies =
                ServerCookieDecoder.LAX.decode(cookieHeader);
            for (io.netty.handler.codec.http.cookie.Cookie decodedCookie : decodedCookies) {
                cookies.withEntry(new Cookie(
                    decodedCookie.name(),
                    decodedCookie.value()
                ));
            }
        }
        httpRequest.withCookies(cookies);
    }

    private void setBody(HttpRequest httpRequest, FullHttpRequest fullHttpRequest) {
        httpRequest.withBody(BodyDecoderEncoder.byteBufToBody(fullHttpRequest.content(), fullHttpRequest.headers().get(CONTENT_TYPE)));
    }
}
