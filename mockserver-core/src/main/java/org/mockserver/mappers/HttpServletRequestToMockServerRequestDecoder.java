package org.mockserver.mappers;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.*;
import org.mockserver.streams.IOStreamUtils;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.mockserver.mappers.ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET;

/**
 * @author jamesdbloom
 */
public class HttpServletRequestToMockServerRequestDecoder {
    public HttpRequest mapHttpServletRequestToMockServerRequest(HttpServletRequest httpServletRequest) {
        HttpRequest request = new HttpRequest();
        setMethod(request, httpServletRequest);

        setPath(request, httpServletRequest);
        setQueryString(request, httpServletRequest);

        setBody(request, httpServletRequest);
        setHeaders(request, httpServletRequest);
        setCookies(request, httpServletRequest);

        request.withKeepAlive(isKeepAlive(httpServletRequest));
        request.withSecure(httpServletRequest.isSecure());
        return request;
    }

    private void setMethod(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        httpRequest.withMethod(httpServletRequest.getMethod());
    }

    private void setPath(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        httpRequest.withPath(httpServletRequest.getPathInfo() != null && httpServletRequest.getContextPath() != null ? httpServletRequest.getPathInfo() : httpServletRequest.getRequestURI());
    }

    private void setQueryString(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        if (StringUtils.isNotEmpty(httpServletRequest.getQueryString())) {
            httpRequest.withQueryStringParameters(new QueryStringDecoder("?" + httpServletRequest.getQueryString()).parameters());
        }
    }

    private void setBody(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        byte[] bodyBytes = IOStreamUtils.readInputStreamToByteArray(httpServletRequest);
        if (bodyBytes.length > 0) {
            if (ContentTypeMapper.isBinary(httpServletRequest.getHeader(CONTENT_TYPE.toString()))) {
                httpRequest.withBody(new BinaryBody(bodyBytes));
            } else {
                Charset requestCharset = ContentTypeMapper.getCharsetFromContentTypeHeader(httpServletRequest.getHeader(CONTENT_TYPE.toString()));
                httpRequest.withBody(new StringBody(new String(bodyBytes, requestCharset), DEFAULT_HTTP_CHARACTER_SET.equals(requestCharset) ? null : requestCharset));
            }
        }
    }

    private void setHeaders(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        List<Header> mappedHeaders = new ArrayList<Header>();
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            List<String> mappedHeaderValues = new ArrayList<String>();
            Enumeration<String> headerValues = httpServletRequest.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                mappedHeaderValues.add(headerValues.nextElement());
            }
            mappedHeaders.add(new Header(headerName, mappedHeaderValues.toArray(new String[mappedHeaderValues.size()])));
        }
        httpRequest.withHeaders(mappedHeaders);
    }

    private void setCookies(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        List<Cookie> mappedCookies = new ArrayList<Cookie>();
        if (httpServletRequest.getCookies() != null) {
            for (javax.servlet.http.Cookie cookie : httpServletRequest.getCookies()) {
                mappedCookies.add(new Cookie(cookie.getName(), cookie.getValue()));
            }
        }
        httpRequest.withCookies(mappedCookies);
    }

    public boolean isKeepAlive(HttpServletRequest httpServletRequest) {
        CharSequence connection = httpServletRequest.getHeader(HttpHeaderNames.CONNECTION.toString());
        if (connection != null && HttpHeaderValues.CLOSE.contentEqualsIgnoreCase(connection)) {
            return false;
        }

        if (httpServletRequest.getProtocol().equals("HTTP/1.1")) {
            return !HttpHeaderValues.CLOSE.contentEqualsIgnoreCase(connection);
        } else {
            return HttpHeaderValues.KEEP_ALIVE.contentEqualsIgnoreCase(connection);
        }
    }
}
