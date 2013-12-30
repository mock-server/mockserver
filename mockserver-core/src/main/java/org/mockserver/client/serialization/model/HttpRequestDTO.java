package org.mockserver.client.serialization.model;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class HttpRequestDTO extends ModelObject {
    private String method;
    private String url;
    private String path;
    private String queryString;
    private String body;
    private List<CookieDTO> cookies = new ArrayList<CookieDTO>();
    private List<HeaderDTO> headers = new ArrayList<HeaderDTO>();
    private Map<String, String> bodyXpath = new HashMap<>();

    public HttpRequestDTO(HttpRequest httpRequest) {
        method = httpRequest.getMethod();
        url = httpRequest.getURL();
        path = httpRequest.getPath();
        queryString = httpRequest.getQueryString();
        body = httpRequest.getBody();
        headers = Lists.transform(httpRequest.getHeaders(), new Function<Header, HeaderDTO>() {
            public HeaderDTO apply(Header header) {
                return new HeaderDTO(header);
            }
        });
        cookies = Lists.transform(httpRequest.getCookies(), new Function<Cookie, CookieDTO>() {
            public CookieDTO apply(Cookie cookie) {
                return new CookieDTO(cookie);
            }
        });
        bodyXpath.putAll(httpRequest.getBodyXpath());

    }

    public HttpRequestDTO() {
    }

    public HttpRequest buildObject() {
        return new HttpRequest()
                .withMethod(method)
                .withURL(url)
                .withPath(path)
                .withQueryString(queryString)
                .withBody(body)
                .withHeaders(Lists.transform(headers, new Function<HeaderDTO, Header>() {
                    public Header apply(HeaderDTO header) {
                        return header.buildObject();
                    }
                }))
                .withCookies(Lists.transform(cookies, new Function<CookieDTO, Cookie>() {
                    public Cookie apply(CookieDTO cookie) {
                        return cookie.buildObject();
                    }
                })).withBodyXpath(bodyXpath);
    }

    public String getMethod() {
        return method;
    }

    public HttpRequestDTO setMethod(String method) {
        this.method = method;
        return this;
    }

    public String getURL() {
        return url;
    }

    public HttpRequestDTO setURL(String url) {
        this.url = url;
        return this;
    }

    public String getPath() {
        return path;
    }

    public HttpRequestDTO setPath(String path) {
        this.path = path;
        return this;
    }

    public String getQueryString() {
        return queryString;
    }

    public HttpRequestDTO setQueryString(String queryString) {
        this.queryString = queryString;
        return this;
    }

    public String getBody() {
        return body;
    }

    public HttpRequestDTO setBody(String body) {
        this.body = body;
        return this;
    }

    public List<HeaderDTO> getHeaders() {
        return headers;
    }

    public HttpRequestDTO setHeaders(List<HeaderDTO> headers) {
        this.headers = headers;
        return this;
    }

    public List<CookieDTO> getCookies() {
        return cookies;
    }

    public HttpRequestDTO setCookies(List<CookieDTO> cookies) {
        this.cookies = cookies;
        return this;
    }

    public Map<String, String> getBodyXpath() {return bodyXpath;}

    public HttpRequestDTO setBodyXpath(Map<String, String> bodyXpath) {
        this.bodyXpath = bodyXpath;
        return this;
    }
}
