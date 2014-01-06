package org.mockserver.model;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpRequest extends EqualsHashCodeToString {
    private String method = "";
    private String url = "";
    private String path = "";
    private String queryString = "";
    private String body = "";
    private List<Header> headers = new ArrayList<Header>();
    private List<Cookie> cookies = new ArrayList<Cookie>();

    public static HttpRequest request() {
        return new HttpRequest();
    }

    public HttpRequest() {
    }

    public HttpRequest withMethod(String method) {
        this.method = method;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public String getURL() {
        return url;
    }

    public HttpRequest withURL(String url) {
        this.url = url;
        return this;
    }

    public String getPath() {
        return path;
    }

    public HttpRequest withPath(String path) {
        this.path = path;
        return this;
    }

    public String getQueryString() {
        return queryString;
    }

    public HttpRequest withQueryString(String queryString) {
        this.queryString = queryString;
        return this;
    }

    public String getBody() {
        return body;
    }

    public HttpRequest withBody(String body) {
        this.body = body;
        return this;
    }

    public HttpRequest withHeaders(List<Header> headers) {
        this.headers = headers;
        return this;
    }

    public HttpRequest withHeaders(Header... headers) {
        this.headers = Arrays.asList(headers);
        return this;
    }

    public List<Header> getHeaders() {
        return headers;
    }

    public HttpRequest withCookies(List<Cookie> cookies) {
        this.cookies = cookies;
        return this;
    }

    public HttpRequest withCookies(Cookie... cookies) {
        this.cookies = Arrays.asList(cookies);
        return this;
    }

    public List<Cookie> getCookies() {
        return cookies;
    }

    public int getPort() {
        URL url = null;
        try {
            url = new URL(this.url);
        } catch (MalformedURLException murle) {
            logger.debug("MalformedURLException parsing uri [" + this.url + "]", murle);
        }
        if (url != null && url.getPort() != -1) {
            return url.getPort();
        } else {
            if (this.url.startsWith("https")) {
                return 443;
            } else {
                return 80;
            }
        }
    }
}
