package org.mockserver.mappers;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

/**
 * @author jamesdbloom
 */
public class HttpServletResponseMapper {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public void mapHttpServletResponse(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        setStatusCode(httpResponse, httpServletResponse);
        setHeaders(httpResponse, httpServletResponse);
        setCookies(httpResponse, httpServletResponse);
        setBody(httpResponse, httpServletResponse);
    }

    private void setStatusCode(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse.getStatusCode() != null) {
            httpServletResponse.setStatus(httpResponse.getStatusCode());
        }
    }

    private void setBody(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse.getBody() != null) {
            try {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(httpServletResponse.getOutputStream());
                IOUtils.write(httpResponse.getBody().getBytes(Charset.forName(CharEncoding.UTF_8)), outputStreamWriter, Charset.forName(CharEncoding.UTF_8));
                outputStreamWriter.flush();
            } catch (IOException ioe) {
                logger.error(String.format("IOException while writing %s to HttpServletResponse output stream", httpResponse.getBody()), ioe);
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
