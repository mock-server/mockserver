package org.mockserver.client.netty.codec.mappers;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.mockserver.mappers.ContentTypeMapper;
import org.mockserver.model.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

/**
 * @author jamesdbloom
 */
public class FullHttpResponseToMockServerResponse {

    public HttpResponse mapMockServerResponseToFullHttpResponse(FullHttpResponse fullHttpResponse) {
        HttpResponse httpResponse = new HttpResponse();
        if (fullHttpResponse != null) {
            setStatusCode(httpResponse, fullHttpResponse);
            setHeaders(httpResponse, fullHttpResponse);
            setCookies(httpResponse);
            setBody(httpResponse, fullHttpResponse);
        }
        return httpResponse;
    }

    private void setStatusCode(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
        httpResponse.withStatusCode(fullHttpResponse.status().code());
    }

    private void setHeaders(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
        Map<String, Header> mappedHeaders = new HashMap<String, Header>();
        for (String headerName : fullHttpResponse.headers().names()) {
            mappedHeaders.put(headerName, new Header(headerName, fullHttpResponse.headers().getAll(headerName)));
        }
        List<Header> headers = new ArrayList<Header>(mappedHeaders.values());
        httpResponse.withHeaders(headers);
    }

    private void setCookies(HttpResponse httpResponse) {
        Map<String, Cookie> mappedCookies = new HashMap<String, Cookie>();
        for (Header header : httpResponse.getHeaders()) {
            if (header.getName().getValue().equalsIgnoreCase("Set-Cookie")) {
                for (NottableString cookieHeader : header.getValues()) {
                    io.netty.handler.codec.http.cookie.Cookie httpCookie = ClientCookieDecoder.LAX.decode(cookieHeader.getValue());
                    String name = httpCookie.name().trim();
                    String value = httpCookie.value().trim();
                    mappedCookies.put(name, new Cookie(name, value));
                }
            }
            if (header.getName().getValue().equalsIgnoreCase("Cookie")) {
                for (NottableString cookieHeader : header.getValues()) {
                    for (io.netty.handler.codec.http.cookie.Cookie httpCookie : ServerCookieDecoder.LAX.decode(cookieHeader.getValue())) {
                        String name = httpCookie.name().trim();
                        String value = httpCookie.value().trim();
                        mappedCookies.put(name, new Cookie(name, value));
                    }
                }
            }
        }
        httpResponse.withCookies(new ArrayList<Cookie>(mappedCookies.values()));
    }

    private void setBody(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
        if (fullHttpResponse.content().readableBytes() > 0) {
            byte[] bodyBytes = new byte[fullHttpResponse.content().readableBytes()];
            fullHttpResponse.content().readBytes(bodyBytes);
            if (bodyBytes.length > 0) {
                if (ContentTypeMapper.isBinary(fullHttpResponse.headers().get(CONTENT_TYPE))) {
                    httpResponse.withBody(new BinaryBody(bodyBytes));
                } else {
                    Charset requestCharset = ContentTypeMapper.getCharsetFromContentTypeHeader(fullHttpResponse.headers().get(CONTENT_TYPE));
                    httpResponse.withBody(new String(bodyBytes, requestCharset));
                }
            }
        }
    }
}
