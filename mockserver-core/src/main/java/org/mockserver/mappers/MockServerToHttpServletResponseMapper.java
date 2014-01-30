package org.mockserver.mappers;

import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.ServerCookieEncoder;
import org.mockserver.model.*;
import org.mockserver.streams.IOStreamUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;

/**
 * @author jamesdbloom
 */
public class MockServerToHttpServletResponseMapper {

    public void mapMockServerResponseToHttpServletResponse(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
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
                    httpServletResponse.addHeader(SET_COOKIE, ServerCookieEncoder.encode(new DefaultCookie(cookie.getName(), value)));
                }
            }
        }
    }

    private void setBody(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse.getBody() != null) {
            IOStreamUtils.writeToOutputStream(httpResponse.getBody(), httpServletResponse);
        }
    }
}
