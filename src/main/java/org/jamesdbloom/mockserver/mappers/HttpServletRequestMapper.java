package org.jamesdbloom.mockserver.mappers;

import org.jamesdbloom.mockserver.model.Cookie;
import org.jamesdbloom.mockserver.model.Header;
import org.jamesdbloom.mockserver.model.HttpRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpServletRequestMapper {

    public HttpRequest createHttpRequest(HttpServletRequest httpServletRequest) {
        HttpRequest httpRequest = new HttpRequest();
        setPath(httpRequest, httpServletRequest);
        setBody(httpRequest, httpServletRequest);
        setHeaders(httpRequest, httpServletRequest);
        setCookies(httpRequest, httpServletRequest);
        setQueryParameters(httpRequest, httpServletRequest);
        setBodyParameters(httpRequest, httpServletRequest);
        return httpRequest;
    }

    private void setPath(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        httpRequest.withPath(httpServletRequest.getRequestURI());
    }

    private void setBody(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        try {
            InputStream requestInputStream = httpServletRequest.getInputStream();
            if (requestInputStream != null) {
                byte[] bodyBytes = new byte[httpServletRequest.getContentLength()];
                requestInputStream.read(bodyBytes);
                httpRequest.withBody(new String(bodyBytes));
            }
        } catch (IOException ioe) {
            throw new RuntimeException("IOException while reading HttpServletRequest input stream", ioe);
        }
    }

    private void setHeaders(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        List<Header> mappedheaders = new ArrayList<Header>();
        Enumeration headerNames = httpServletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = (String) headerNames.nextElement();
            List<String> mappedHeaderValues = new ArrayList<String>();
            Enumeration headerValues = httpServletRequest.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                mappedHeaderValues.add((String) headerValues.nextElement());
            }
            mappedheaders.add(new Header(headerName, mappedHeaderValues.toArray(new String[mappedHeaderValues.size()])));
        }
        httpRequest.withHeaders(mappedheaders);
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

    private void setQueryParameters(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {

    }

    private void setBodyParameters(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {

    }
}
