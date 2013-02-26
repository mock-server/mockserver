package org.jamesdbloom.mockserver.mappers;

import org.jamesdbloom.mockserver.model.Cookie;
import org.jamesdbloom.mockserver.model.Header;
import org.jamesdbloom.mockserver.model.HttpResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class HttpServletResponseMapper {
    public void mapHttpServletResponse(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        setStatusCode(httpResponse, httpServletResponse);
        setBody(httpResponse, httpServletResponse);
        setHeaders(httpResponse, httpServletResponse);
        setCookies(httpResponse, httpServletResponse);
    }

    private void setStatusCode(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse.getResponseCode() != null) {
            httpServletResponse.setStatus(httpResponse.getResponseCode());
        }
    }

    private void setBody(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse.getBody() != null) {
            try {
                httpServletResponse.getOutputStream().write(httpResponse.getBody().getBytes());
            } catch (IOException ioe) {
                throw new RuntimeException(String.format("IOException while writing %s to HttpServletResponse output stream", httpResponse.getBody()), ioe);
            }
        }
    }

    private void setHeaders(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse.getHeaders() != null) {
            for (Header header : httpResponse.getHeaders()) {
                for (String value : header.getValues()) {
                    httpServletResponse.addHeader(header.getName(), value);
                }
            }
        }
    }

    private void setCookies(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse.getCookies() != null) {
            for (Cookie cookie : httpResponse.getCookies()) {
                for (String value : cookie.getValues()) {
                    httpServletResponse.addCookie(new javax.servlet.http.Cookie(cookie.getName(), value));
                }
            }
        }
    }
}
