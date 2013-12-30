package org.mockserver.matchers;

import org.mockserver.model.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class HttpRequestMatcher extends ModelObject implements Matcher<HttpRequest> {

    private StringMatcher methodMatcher = null;
    private StringMatcher urlMatcher = null;
    private StringMatcher pathMatcher = null;
    private StringMatcher queryStringMatcher = null;
    private StringMatcher bodyMatcher = null;
    private MapMatcher headerMatcher = null;
    private MapMatcher cookieMatcher = null;
    private XpathBodyMatcher xpathBodyMatcher = null;

    public HttpRequestMatcher withMethod(String method) {
        this.methodMatcher = new StringMatcher(method);
        return this;
    }

    public HttpRequestMatcher withURL(String url) {
        this.urlMatcher = new StringMatcher(url);
        return this;
    }

    public HttpRequestMatcher withPath(String path) {
        this.pathMatcher = new StringMatcher(path);
        return this;
    }

    public HttpRequestMatcher withQueryString(String queryString) {
        this.queryStringMatcher = new StringMatcher(queryString);
        return this;
    }

    public HttpRequestMatcher withBody(String body) {
        this.bodyMatcher = new StringMatcher(body);
        return this;
    }

    public HttpRequestMatcher withHeaders(Header... headers) {
        this.headerMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(headers));
        return this;
    }

    public HttpRequestMatcher withHeaders(List<Header> headers) {
        this.headerMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(headers));
        return this;
    }

    public HttpRequestMatcher withCookies(Cookie... cookies) {
        this.cookieMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(cookies));
        return this;
    }

    public HttpRequestMatcher withCookies(List<Cookie> cookies) {
        this.cookieMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(cookies));
        return this;
    }

    public HttpRequestMatcher withXpathBody(Map<String, String> xpathToValueMap) {
        try {
            this.xpathBodyMatcher = new XpathBodyMatcher(xpathToValueMap, XPathFactory.newInstance().newXPath(), DocumentBuilderFactory.newInstance().newDocumentBuilder());
        } catch (ParserConfigurationException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return this;
    }

    public boolean matches(HttpRequest httpRequest) {
        boolean methodMatches = matches(methodMatcher, httpRequest.getMethod());
        boolean urlMatches = matches(urlMatcher, httpRequest.getURL());
        boolean pathMatches = matches(pathMatcher, httpRequest.getPath());
        boolean queryStringMatches = matches(queryStringMatcher, httpRequest.getQueryString());
        boolean bodyMatches = matches(bodyMatcher, httpRequest.getBody());
        boolean headersMatch = matches(headerMatcher, (httpRequest.getHeaders() != null ? new ArrayList<KeyToMultiValue>(httpRequest.getHeaders()) : null));
        boolean cookiesMatch = matches(cookieMatcher, (httpRequest.getCookies() != null ? new ArrayList<KeyToMultiValue>(httpRequest.getCookies()) : null));
        boolean xpathMatch = matches(xpathBodyMatcher, httpRequest.getBody());
        return methodMatches && urlMatches && pathMatches && queryStringMatches && bodyMatches && headersMatch && cookiesMatch && xpathMatch;
    }

    private <T> boolean matches(Matcher<T> matcher, T t) {
        boolean result = false;

        if (matcher == null) {
            result = true;
        } else if (matcher.matches(t)) {
            result = true;
        }

        return result;
    }
}
