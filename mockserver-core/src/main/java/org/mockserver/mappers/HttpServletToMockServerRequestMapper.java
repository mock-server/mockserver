package org.mockserver.mappers;

import com.google.common.base.Charsets;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.*;
import org.mockserver.streams.IOStreamUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpServletToMockServerRequestMapper {
    public HttpRequest mapHttpServletRequestToMockServerRequest(HttpServletRequest httpServletRequest) {
        HttpRequest httpRequest = new HttpRequest();
        setMethod(httpRequest, httpServletRequest);
        setUrl(httpRequest, httpServletRequest);
        setPath(httpRequest, httpServletRequest);
        setQueryString(httpRequest, httpServletRequest);
        setBody(httpRequest, httpServletRequest);
        setHeaders(httpRequest, httpServletRequest);
        setCookies(httpRequest, httpServletRequest);
        return httpRequest;
    }

    private void setMethod(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        httpRequest.withMethod(httpServletRequest.getMethod());
    }

    private void setUrl(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        StringBuilder url = new StringBuilder(httpServletRequest.getRequestURL());
        String queryString = httpServletRequest.getQueryString();
        if (queryString != null) {
            url.append('?');
            url.append(queryString);
        }
        httpRequest.withURL(url.toString());
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
            if (ContentTypeMapper.isBinary(httpServletRequest.getHeader(HttpHeaders.Names.CONTENT_TYPE))) {
                httpRequest.withBody(new BinaryBody(bodyBytes));
            } else {
                httpRequest.withBody(new StringBody(new String(bodyBytes, Charsets.UTF_8), bodyBytes));
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
}
