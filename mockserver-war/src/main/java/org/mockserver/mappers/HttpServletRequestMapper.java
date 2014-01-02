package org.mockserver.mappers;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpServletRequestMapper {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpRequest mapHttpServletRequestToHttpRequest(HttpServletRequest httpServletRequest) {
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
        httpRequest.withPath(httpServletRequest.getRequestURI());
    }

    private void setQueryString(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        httpRequest.withQueryString(httpServletRequest.getQueryString());
    }

    private void setBody(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        try {
            byte[] bodyBytes = IOUtils.toByteArray(new InputStreamReader(httpServletRequest.getInputStream()), Charset.forName(CharEncoding.UTF_8));
            httpRequest.withBody(new String(bodyBytes, Charset.forName(CharEncoding.UTF_8)));
        } catch (IOException ioe) {
            logger.error("IOException while reading HttpServletRequest input stream", ioe);
            throw new RuntimeException("IOException while reading HttpServletRequest input stream", ioe);
        }
    }

    private void setHeaders(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        List<Header> mappedHeaders = new ArrayList<>();
        Enumeration headerNames = httpServletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = (String) headerNames.nextElement();
            List<String> mappedHeaderValues = new ArrayList<>();
            Enumeration headerValues = httpServletRequest.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                mappedHeaderValues.add((String) headerValues.nextElement());
            }
            mappedHeaders.add(new Header(headerName, mappedHeaderValues.toArray(new String[mappedHeaderValues.size()])));
        }
        httpRequest.withHeaders(mappedHeaders);
    }

    private void setCookies(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        List<Cookie> mappedCookies = new ArrayList<>();
        if (httpServletRequest.getCookies() != null) {
            for (javax.servlet.http.Cookie cookie : httpServletRequest.getCookies()) {
                mappedCookies.add(new Cookie(cookie.getName(), cookie.getValue()));
            }
        }
        httpRequest.withCookies(mappedCookies);
    }
}
