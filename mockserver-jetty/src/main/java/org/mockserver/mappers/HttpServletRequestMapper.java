package org.mockserver.mappers;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author jamesdbloom
 */
public class HttpServletRequestMapper {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpRequest createHttpRequest(HttpServletRequest httpServletRequest) {
        HttpRequest httpRequest = new HttpRequest();
        setMethod(httpRequest, httpServletRequest);
        setPath(httpRequest, httpServletRequest);
        setBody(httpRequest, httpServletRequest);
        setHeaders(httpRequest, httpServletRequest);
        setCookies(httpRequest, httpServletRequest);
        setParameters(httpRequest, httpServletRequest);
        return httpRequest;
    }

    private void setMethod(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        httpRequest.withMethod(httpServletRequest.getMethod());
    }

    private void setPath(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        httpRequest.withPath(httpServletRequest.getRequestURI());
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

    private void setParameters(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        Map<String, String[]> parameters = httpServletRequest.getParameterMap();
        List<Parameter> mappedParameters = new ArrayList<Parameter>();
        for (String parameterName : parameters.keySet()) {
            mappedParameters.add(new Parameter(parameterName, Arrays.asList(parameters.get(parameterName))));
        }
        httpRequest.withParameters(mappedParameters);
    }
}
