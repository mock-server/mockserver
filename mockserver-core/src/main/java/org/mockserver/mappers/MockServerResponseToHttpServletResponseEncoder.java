package org.mockserver.mappers;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.*;
import org.mockserver.streams.IOStreamUtils;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.Charset;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.*;

/**
 * @author jamesdbloom
 */
public class MockServerResponseToHttpServletResponseEncoder {

    private final Base64Converter base64Converter = new Base64Converter();

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
        if (httpResponse.getHeaderList() != null) {
            for (Header header : httpResponse.getHeaderList()) {
                String headerName = header.getName().getValue();
                if (!headerName.equalsIgnoreCase(CONTENT_LENGTH.toString())
                    && !headerName.equalsIgnoreCase(TRANSFER_ENCODING.toString())
                    && !headerName.equalsIgnoreCase(HOST.toString())
                    && !headerName.equalsIgnoreCase(ACCEPT_ENCODING.toString())
                    && !headerName.equalsIgnoreCase(CONNECTION.toString())) {
                    for (NottableString value : header.getValues()) {
                        httpServletResponse.addHeader(headerName, value.getValue());
                    }
                }
            }
        }
        addContentTypeHeader(httpResponse, httpServletResponse);
    }

    private void setCookies(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse.getCookieList() != null) {
            for (Cookie cookie : httpResponse.getCookieList()) {
                if (!cookieHeaderAlreadyExists(httpResponse, cookie)) {
                    httpServletResponse.addHeader(SET_COOKIE.toString(), ServerCookieEncoder.LAX.encode(new DefaultCookie(cookie.getName().getValue(), cookie.getValue().getValue())));
                }
            }
        }
    }

    private boolean cookieHeaderAlreadyExists(HttpResponse response, Cookie cookieValue) {
        List<String> setCookieHeaders = response.getHeader(SET_COOKIE.toString());
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
                IOStreamUtils.writeToOutputStream(base64Converter.base64StringToBytes(httpResponse.getBodyAsString()), httpServletResponse);
            } else {
                Charset bodyCharset = httpResponse.getBody().getCharset(ContentTypeMapper.getCharsetFromContentTypeHeader(httpResponse.getFirstHeader(CONTENT_TYPE.toString())));
                IOStreamUtils.writeToOutputStream(httpResponse.getBodyAsString().getBytes(bodyCharset), httpServletResponse);
            }
        }
    }

    private void addContentTypeHeader(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpServletResponse.getContentType() == null
            && httpResponse.getBody() != null
            && httpResponse.getBody().getContentType() != null) {
            httpServletResponse.addHeader(CONTENT_TYPE.toString(), httpResponse.getBody().getContentType());
        }
    }
}
