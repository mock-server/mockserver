package org.mockserver.mappers;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import org.mockserver.codec.BodyDecoderEncoder;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author jamesdbloom
 */
public class MockServerHttpRequestToFullHttpRequest {

    private final MockServerLogger mockServerLogger;
    private final Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations;
    private final BodyDecoderEncoder bodyDecoderEncoder;

    public MockServerHttpRequestToFullHttpRequest(MockServerLogger mockServerLogger, Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations) {
        this.mockServerLogger = mockServerLogger;
        this.proxyConfigurations = proxyConfigurations;
        this.bodyDecoderEncoder = new BodyDecoderEncoder();
    }

    public FullHttpRequest mapMockServerRequestToNettyRequest(HttpRequest httpRequest) {
        // method
        HttpMethod httpMethod = HttpMethod.valueOf(httpRequest.getMethod("GET"));
        try {
            // the request
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, httpMethod, getURI(httpRequest, proxyConfigurations), getBody(httpRequest));

            // headers
            setHeader(httpRequest, request);

            // cookies
            setCookies(httpRequest, request);

            return request;
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception encoding request{}")
                    .setArguments(httpRequest)
                    .setThrowable(throwable)
            );
            return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, httpMethod, getURI(httpRequest, proxyConfigurations));
        }
    }

    @SuppressWarnings("HttpUrlsUsage")
    public String getURI(HttpRequest httpRequest, Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations) {
        String uri = "";
        if (httpRequest.getPath() != null) {
            if (httpRequest.getQueryStringParameters() != null && isNotBlank(httpRequest.getQueryStringParameters().getRawParameterString())) {
                uri = httpRequest.getPath().getValue() + "?" + httpRequest.getQueryStringParameters().getRawParameterString();
            } else {
                QueryStringEncoder queryStringEncoder = new QueryStringEncoder(httpRequest.getPath().getValue());
                for (Parameter parameter : httpRequest.getQueryStringParameterList()) {
                    for (NottableString value : parameter.getValues()) {
                        queryStringEncoder.addParam(parameter.getName().getValue(), value.getValue());
                    }
                }
                uri = queryStringEncoder.toString();
            }
        }
        if (proxyConfigurations != null && proxyConfigurations.get(ProxyConfiguration.Type.HTTP) != null && !Boolean.TRUE.equals(httpRequest.isSecure())) {
            if (isNotBlank(httpRequest.getFirstHeader(HOST.toString()))) {
                uri = "http://" + httpRequest.getFirstHeader(HOST.toString()) + uri;
            } else if (httpRequest.getRemoteAddress() != null) {
                uri = "http://" + httpRequest.getRemoteAddress() + uri;
            }
        }
        return uri;
    }

    private ByteBuf getBody(HttpRequest httpRequest) {
        return bodyDecoderEncoder.bodyToByteBuf(httpRequest.getBody(), httpRequest.getFirstHeader(CONTENT_TYPE.toString()));
    }

    private void setCookies(HttpRequest httpRequest, FullHttpRequest request) {
        List<io.netty.handler.codec.http.cookie.Cookie> cookies = new ArrayList<>();
        for (org.mockserver.model.Cookie cookie : httpRequest.getCookieList()) {
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
        for (Header header : httpRequest.getHeaderList()) {
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

        if (isNotBlank(httpRequest.getFirstHeader(HOST.toString()))) {
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
