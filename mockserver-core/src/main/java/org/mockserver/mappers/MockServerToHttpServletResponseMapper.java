package org.mockserver.mappers;

import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.ServerCookieEncoder;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.streams.IOStreamUtils;

import javax.servlet.http.HttpServletResponse;

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
                String headerName = header.getName();
                if (!headerName.equalsIgnoreCase(HttpHeaders.Names.CONTENT_LENGTH)
                        && !headerName.equalsIgnoreCase(HttpHeaders.Names.TRANSFER_ENCODING)
                        && !headerName.equalsIgnoreCase(HttpHeaders.Names.HOST)
                        && !headerName.equalsIgnoreCase(HttpHeaders.Names.ACCEPT_ENCODING)
                        && !headerName.equalsIgnoreCase(HttpHeaders.Names.CONNECTION)) {
                    for (String value : header.getValues()) {
                        httpServletResponse.addHeader(headerName, value);
                    }
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
        if (httpResponse.getBodyAsString() != null) {
            if (httpResponse.getBody() instanceof BinaryBody) {
                IOStreamUtils.writeToOutputStream(Base64Converter.base64StringToBytes(httpResponse.getBodyAsString()), httpServletResponse);
            } else {
                IOStreamUtils.writeToOutputStream(httpResponse.getBodyAsString().getBytes(), httpServletResponse);
            }
        }
    }
}
