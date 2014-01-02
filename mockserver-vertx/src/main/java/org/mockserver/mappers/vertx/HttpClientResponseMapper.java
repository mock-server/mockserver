package org.mockserver.mappers.vertx;

import org.apache.commons.lang3.CharEncoding;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.http.HttpClientResponse;

import java.net.HttpCookie;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpClientResponseMapper {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpResponse mapHttpClientResponseToHttpResponse(HttpClientResponse httpClientResponse, byte[] bodyBytes) {
        HttpResponse httpResponse = new HttpResponse();
        setStatusCode(httpResponse, httpClientResponse);
        setHeaders(httpResponse, httpClientResponse);
        setCookies(httpResponse, httpClientResponse);
        setBody(httpResponse, bodyBytes);
        return httpResponse;
    }

    private void setStatusCode(HttpResponse httpResponse, HttpClientResponse httpClientResponse) {
        httpResponse.withStatusCode(httpClientResponse.statusCode());
    }

    private void setBody(HttpResponse httpResponse, byte[] bodyBytes) {
        httpResponse.withBody(new String(bodyBytes, Charset.forName(CharEncoding.UTF_8)));
    }

    private void setHeaders(HttpResponse httpResponse, HttpClientResponse httpServletResponse) {
        List<Header> mappedHeaders = new ArrayList<Header>();
        MultiMap headers = httpServletResponse.headers();
        for (String headerName : headers.names()) {
            mappedHeaders.add(new Header(headerName, new ArrayList<>(headers.getAll(headerName))));
        }
        httpResponse.withHeaders(mappedHeaders);
    }

    private void setCookies(HttpResponse httpResponse, HttpClientResponse httpServletResponse) {
        List<Cookie> mappedCookies = new ArrayList<>();
        MultiMap headers = httpServletResponse.headers();
        for (String headerName : headers.names()) {
            if (headerName.equals("Cookie") || headerName.equals("Set-Cookie")) {
                for (String cookieHeader : headers.getAll(headerName)) {
                    for (HttpCookie httpCookie : HttpCookie.parse(cookieHeader)) {
                        mappedCookies.add(new Cookie(httpCookie.getName(), httpCookie.getValue()));
                    }
                }
            }
        }
        httpResponse.withCookies(mappedCookies);
    }
}
