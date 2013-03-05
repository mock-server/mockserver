package org.jamesdbloom.mockserver.matchers;

import org.jamesdbloom.mockserver.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpRequestMatcher extends ModelObject implements Matcher<HttpRequest> {

    private StringMatcher methodMatcher = null;
    private StringMatcher pathMatcher = null;
    private StringMatcher bodyMatcher = null;
    private MapMatcher<String, String> headerMatcher = null;
    private MapMatcher<String, String> parameterMatcher = null;
    private MapMatcher<String, String> cookieMatcher = null;

    public HttpRequestMatcher withMethod(String method) {
        this.methodMatcher = new StringMatcher(method);
        return this;
    }

    public HttpRequestMatcher withPath(String path) {
        this.pathMatcher = new StringMatcher(path);
        return this;
    }

    public HttpRequestMatcher withBody(String body) {
        this.bodyMatcher = new StringMatcher(body);
        return this;
    }

    public HttpRequestMatcher withHeaders(Header... headers) {
        this.headerMatcher = new MapMatcher<String, String>(KeyToMultiValue.toMultiMap(headers));
        return this;
    }

    public HttpRequestMatcher withHeaders(List<Header> headers) {
        this.headerMatcher = new MapMatcher<String, String>(KeyToMultiValue.toMultiMap(headers));
        return this;
    }

    public HttpRequestMatcher withParameters(Parameter... parameters) {
        this.parameterMatcher = new MapMatcher<String, String>(KeyToMultiValue.toMultiMap(parameters));
        return this;
    }

    public HttpRequestMatcher withParameters(List<Parameter> parameters) {
        this.parameterMatcher = new MapMatcher<String, String>(KeyToMultiValue.toMultiMap(parameters));
        return this;
    }

    public HttpRequestMatcher withCookies(Cookie... cookies) {
        this.cookieMatcher = new MapMatcher<String, String>(KeyToMultiValue.toMultiMap(cookies));
        return this;
    }

    public HttpRequestMatcher withCookies(List<Cookie> cookies) {
        this.cookieMatcher = new MapMatcher<String, String>(KeyToMultiValue.toMultiMap(cookies));
        return this;
    }

    public boolean matches(HttpRequest httpRequest) {
        return matches(methodMatcher, httpRequest.getMethod())
                && matches(pathMatcher, httpRequest.getPath())
                && matches(bodyMatcher, httpRequest.getBody())
                && matches(headerMatcher, (httpRequest.getHeaders() != null ? new ArrayList<KeyToMultiValue<String, String>>(httpRequest.getHeaders()) : null))
                && matches(parameterMatcher, (httpRequest.getParameters() != null ? new ArrayList<KeyToMultiValue<String, String>>(httpRequest.getParameters()) : null))
                && matches(cookieMatcher, (httpRequest.getCookies() != null ? new ArrayList<KeyToMultiValue<String, String>>(httpRequest.getCookies()) : null));
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
