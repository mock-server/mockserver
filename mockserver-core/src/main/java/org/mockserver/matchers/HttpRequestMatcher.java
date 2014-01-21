package org.mockserver.matchers;

import org.apache.commons.lang3.StringUtils;
import org.mockserver.collections.CircularMultiMap;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpRequestMatcher extends EqualsHashCodeToString implements Matcher<HttpRequest> {

    private StringMatcher methodMatcher = null;
    private StringMatcher urlMatcher = null;
    private StringMatcher pathMatcher = null;
    private StringMatcher queryStringMatcher = null;
    private MapMatcher parameterMatcher = null;
    private StringMatcher bodyMatcher = null;
    private MapMatcher headerMatcher = null;
    private MapMatcher cookieMatcher = null;

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

    public HttpRequestMatcher withParameters(Parameter... parameters) {
        this.parameterMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(parameters));
        return this;
    }

    public HttpRequestMatcher withParameters(List<Parameter> parameters) {
        this.parameterMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(parameters));
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

    public boolean matches(HttpRequest httpRequest) {
        if (httpRequest != null) {
            boolean methodMatches = matches(methodMatcher, httpRequest.getMethod());
            boolean urlMatches = matches(urlMatcher, httpRequest.getURL());
            boolean pathMatches = matches(pathMatcher, httpRequest.getPath());
            boolean queryStringMatches = matches(queryStringMatcher, httpRequest.getQueryString());
            boolean parametersMatch = matches(parameterMatcher, (httpRequest.getParameters() != null && httpRequest.getParameters().size() > 0 ? new ArrayList<KeyToMultiValue>(httpRequest.getParameters()) : new ArrayList<KeyToMultiValue>(queryStringToParameters(httpRequest.getQueryString()))));
            boolean bodyMatches = matches(bodyMatcher, httpRequest.getBody());
            boolean headersMatch = matches(headerMatcher, (httpRequest.getHeaders() != null ? new ArrayList<KeyToMultiValue>(httpRequest.getHeaders()) : null));
            boolean cookiesMatch = matches(cookieMatcher, (httpRequest.getCookies() != null ? new ArrayList<KeyToMultiValue>(httpRequest.getCookies()) : null));
            return methodMatches && urlMatches && pathMatches && queryStringMatches && parametersMatch && bodyMatches && headersMatch && cookiesMatch;
        } else {
            return false;
        }
    }

    private List<Parameter> queryStringToParameters(String queryString) {
        List<Parameter> parameters = new ArrayList<Parameter>();
        if (StringUtils.isNotEmpty(queryString)) {
            CircularMultiMap<String, String> parameterMap = new CircularMultiMap<String, String>(20, 20);
            for (String param : queryString.split("&")) {
                String[] pair = param.split("=");
                String key = pair[0];
                String value = "";
                if (pair.length > 1) {
                    value = pair[1];
                }
                parameterMap.put(key, value);
            }
            for (String key : parameterMap.keySet()) {
                parameters.add(new Parameter(key, parameterMap.getAll(key)));
            }
        }
        return parameters;
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
