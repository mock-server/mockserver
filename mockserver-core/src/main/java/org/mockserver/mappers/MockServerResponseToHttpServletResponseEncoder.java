package org.mockserver.mappers;

import com.google.common.base.Strings;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.*;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.*;
import org.mockserver.model.Cookie;
import org.mockserver.streams.IOStreamUtils;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.Charset;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;

/**
 * @author jamesdbloom
 */
public class MockServerResponseToHttpServletResponseEncoder {

    public void mapMockServerResponseToHttpServletResponse(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        setStatusCode(httpResponse, httpServletResponse);
        setHeaders(httpResponse, httpServletResponse);
        setCookies(httpResponse, httpServletResponse);
        setBody(httpResponse, httpServletResponse);
    }

    private void setStatusCode(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse.getStatusCode() != null) {
            httpServletResponse.setStatus(httpResponse.getStatusCode());
        } else {
            httpServletResponse.setStatus(200);
        }
    }

    private void setHeaders(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse.getHeaders() != null) {
            for (Header header : httpResponse.getHeaders()) {
                String headerName = header.getName().getValue();
                if (!headerName.equalsIgnoreCase(HttpHeaders.Names.CONTENT_LENGTH)
                        && !headerName.equalsIgnoreCase(HttpHeaders.Names.TRANSFER_ENCODING)
                        && !headerName.equalsIgnoreCase(HttpHeaders.Names.HOST)
                        && !headerName.equalsIgnoreCase(HttpHeaders.Names.ACCEPT_ENCODING)
                        && !headerName.equalsIgnoreCase(HttpHeaders.Names.CONNECTION)) {
                    for (NottableString value : header.getValues()) {
                        httpServletResponse.addHeader(headerName, value.getValue());
                    }
                }
            }
        }
        addContentTypeHeader(httpResponse, httpServletResponse);
    }

    private void setCookies(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse.getCookies() != null) {
            for (Cookie cookie : httpResponse.getCookies()) {
                if (!cookieHeaderAlreadyExists(httpResponse, cookie)) {
                    httpServletResponse.addHeader(SET_COOKIE, ServerCookieEncoder.LAX.encode(new DefaultCookie(cookie.getName().getValue(), cookie.getValue().getValue())));
                }
            }
        }
    }

    private boolean cookieHeaderAlreadyExists(HttpResponse response, Cookie cookieValue) {
        List<String> setCookieHeaders = response.getHeader(SET_COOKIE);
        setCookieHeaders.addAll(response.getHeader(SET_COOKIE.toLowerCase()));
        for (String setCookieHeader : setCookieHeaders) {
            String existingCookieName = ClientCookieDecoder.LAX.decode(setCookieHeader).name();
            String existingCookieValue = ClientCookieDecoder.LAX.decode(setCookieHeader).value();
            if (existingCookieName.equalsIgnoreCase(cookieValue.getName().getValue()) && existingCookieValue.equalsIgnoreCase(cookieValue.getValue().getValue())) {
                return true;
            }
        }
        return false;
    }

    private void setBody(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse.getBodyAsString() != null) {
            if (httpResponse.getBody() instanceof BinaryBody) {
                IOStreamUtils.writeToOutputStream(Base64Converter.base64StringToBytes(httpResponse.getBodyAsString()), httpServletResponse);
            } else {
                Charset bodyCharset = httpResponse.getBody().getCharset(ContentTypeMapper.determineCharsetForMessage(httpResponse));
                IOStreamUtils.writeToOutputStream(httpResponse.getBodyAsString().getBytes(bodyCharset), httpServletResponse);
            }
        }
    }

    private void addContentTypeHeader(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpServletResponse.getContentType() == null && httpResponse.getBody() != null) {
            Charset bodyCharset = httpResponse.getBody().getCharset(null);
            String bodyContentType = httpResponse.getBody().getContentType();
            if (bodyCharset != null) {
                httpServletResponse.addHeader(HttpHeaders.Names.CONTENT_TYPE, bodyContentType + "; charset=" + bodyCharset.name().toLowerCase());
            } else if (bodyContentType != null) {
                httpServletResponse.addHeader(HttpHeaders.Names.CONTENT_TYPE, bodyContentType);
            }
        }
    }
}
