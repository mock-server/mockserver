package org.mockserver.mappers;

import com.google.common.base.Splitter;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.http.HttpServerRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class VertXToMockServerRequestMapper {

    public HttpRequest mapVertXRequestToMockServerRequest(HttpServerRequest httpServerRequest, byte[] bodyBytes) {
        HttpRequest httpRequest = new HttpRequest();
        setMethod(httpRequest, httpServerRequest);
        setURL(httpRequest, httpServerRequest);
        setPath(httpRequest, httpServerRequest);
        setQueryString(httpRequest, httpServerRequest);
        setHeaders(httpRequest, httpServerRequest);
        setCookies(httpRequest, httpServerRequest);
        setBody(httpRequest, bodyBytes);
        return httpRequest;
    }

    private void setMethod(HttpRequest httpRequest, HttpServerRequest httpServletRequest) {
        httpRequest.withMethod(httpServletRequest.method());
    }

    private void setURL(HttpRequest httpRequest, HttpServerRequest httpServerRequest) {
        String hostAndPort = httpServerRequest.headers().get(HttpHeaders.Names.HOST);
        httpRequest.withURL("http://" + hostAndPort + httpServerRequest.uri());
    }

    private void setPath(HttpRequest httpRequest, HttpServerRequest httpServerRequest) {
        httpRequest.withPath(httpServerRequest.path());
    }

    private void setQueryString(HttpRequest httpRequest, HttpServerRequest httpServerRequest) {
        httpRequest.withQueryStringParameters(new QueryStringDecoder("?" + httpServerRequest.query()).parameters());
    }

    private void setHeaders(HttpRequest httpRequest, HttpServerRequest httpServerRequest) {
        List<Header> mappedHeaders = new ArrayList<Header>();
        MultiMap headers = httpServerRequest.headers();
        for (String headerName : headers.names()) {
            mappedHeaders.add(new Header(headerName, new ArrayList<String>(headers.getAll(headerName))));
        }
        httpRequest.withHeaders(mappedHeaders);
    }

    private void setCookies(HttpRequest httpRequest, HttpServerRequest httpServerRequest) {
        List<Cookie> mappedCookies = new ArrayList<Cookie>();
        MultiMap headers = httpServerRequest.headers();
        for (String headerName : headers.names()) {
            if (headerName.equals("Cookie") || headerName.equals("Set-Cookie")) {
                for (String cookieHeader : headers.getAll(headerName)) {
                    for (String cookie : Splitter.on(";").split(cookieHeader)) {
                        mappedCookies.add(new Cookie(
                                StringUtils.substringBefore(cookie, "=").trim(),
                                StringUtils.substringAfter(cookie, "=").trim()
                        ));
                    }
                }
            }
        }
        httpRequest.withCookies(mappedCookies);
    }

    private void setBody(HttpRequest httpRequest, byte[] bodyBytes) {
        httpRequest.withBody(new String(bodyBytes, Charsets.UTF_8));
    }
}
