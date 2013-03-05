package org.jamesdbloom.mockserver.mappers;

import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.StringUtils;
import org.jamesdbloom.mockserver.model.Cookie;
import org.jamesdbloom.mockserver.model.Header;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author jamesdbloom
 */
public class HttpServletRequestMapper {
    private static final List<String> REQUEST_WITH_BODY_PARAMETERS = Arrays.asList("POST", "PUT");
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpRequest createHttpRequest(HttpServletRequest httpServletRequest) {
        HttpRequest httpRequest = new HttpRequest();
        setPath(httpRequest, httpServletRequest);
        setBody(httpRequest, httpServletRequest);
        setHeaders(httpRequest, httpServletRequest);
        setCookies(httpRequest, httpServletRequest);
        setParameters(httpRequest, httpServletRequest);
        return httpRequest;
    }

    private void setPath(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        httpRequest.withPath(httpServletRequest.getRequestURI());
    }

    private void setBody(HttpRequest httpRequest, HttpServletRequest httpServletRequest) {
        try {
            InputStream requestInputStream = httpServletRequest.getInputStream();
            if (requestInputStream != null && httpServletRequest.getContentLength() > 0) {
                byte[] bodyBytes = new byte[httpServletRequest.getContentLength()];
                requestInputStream.read(bodyBytes);
                httpRequest.withBody(new String(bodyBytes));
            }
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
        Map<String, String[]> allParametersMap = httpServletRequest.getParameterMap();
        List<Parameter> mappedParameters = new ArrayList<Parameter>();
        if (httpServletRequest.getQueryString() != null) {
            Iterable<String> queryParameters = Splitter.on('&').split(StringUtils.removeStart(httpServletRequest.getQueryString(), "?"));
            Multimap<String, String> queryParameterMultimap = HashMultimap.create();
            for (String parameter : queryParameters) {
                String[] queryParts = parameter.split("=");
                if (queryParts.length == 2) {
                    String[] queryValues = queryParts[1].split(",");
                    for (String queryValue : queryValues) {
                        String queryName = queryParts[0];
                        queryParameterMultimap.put(queryName, queryValue);
                        if (allParametersMap.containsKey(queryName)) {
                            List<String> parameterValues = new ArrayList<String>(Arrays.asList(allParametersMap.get(queryName)));
                            parameterValues.remove(queryValue);
                            if (parameterValues.size() > 0) {
                                allParametersMap.put(queryName, parameterValues.toArray(new String[parameterValues.size()]));
                            }
                        }
                    }
                }
            }

            for (String queryName : queryParameterMultimap.keySet()) {
                mappedParameters.add(new Parameter(queryName, new ArrayList<String>(queryParameterMultimap.get(queryName))));
            }
        }
        if (REQUEST_WITH_BODY_PARAMETERS.contains(httpServletRequest.getMethod()) && allParametersMap.size() > 0) {
            for (String queryName : allParametersMap.keySet()) {
                mappedParameters.add(new Parameter(queryName, Arrays.asList(allParametersMap.get(queryName))));
            }
        }
        httpRequest.withParameters(mappedParameters);
    }
}
