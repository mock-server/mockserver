package org.mockserver.matchers;

import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpRequestMatcher extends EqualsHashCodeToString implements Matcher<HttpRequest> {

    private HttpRequest httpRequest;
    private RegexStringMatcher methodMatcher = null;
    private RegexStringMatcher urlMatcher = null;
    private RegexStringMatcher pathMatcher = null;
    private MapMatcher queryStringParameterMatcher = null;
    private BodyMatcher bodyMatcher = null;
    private MapMatcher headerMatcher = null;
    private MapMatcher cookieMatcher = null;

    public HttpRequestMatcher(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
        if (httpRequest != null) {
            withMethod(httpRequest.getMethod());
            withURL(httpRequest.getURL());
            withPath(httpRequest.getPath());
            withQueryStringParameters(httpRequest.getQueryStringParameters());
            withBody(httpRequest.getBody());
            withHeaders(httpRequest.getHeaders());
            withCookies(httpRequest.getCookies());
        }
    }

    private HttpRequestMatcher withMethod(String method) {
        this.methodMatcher = new RegexStringMatcher(method);
        return this;
    }

    private HttpRequestMatcher withURL(String url) {
        this.urlMatcher = new RegexStringMatcher(url);
        return this;
    }

    private HttpRequestMatcher withPath(String path) {
        this.pathMatcher = new RegexStringMatcher(path);
        return this;
    }

    private HttpRequestMatcher withQueryStringParameters(List<Parameter> parameters) {
        this.queryStringParameterMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(parameters));
        return this;
    }

    private HttpRequestMatcher withBody(Body body) {
        if (body != null) {
            switch (body.getType()) {
                case STRING:
                    this.bodyMatcher = new ExactStringMatcher(((StringBody) body).getValue());
                    break;
                case REGEX:
                    this.bodyMatcher = new RegexStringMatcher(((StringBody) body).getValue());
                    break;
                case PARAMETERS:
                    this.bodyMatcher = new ParameterStringMatcher(((ParameterBody) body).getValue());
                    break;
                case XPATH:
                    this.bodyMatcher = new XPathStringMatcher(((StringBody) body).getValue());
                    break;
                case JSON:
                    this.bodyMatcher = new JsonStringMatcher(((StringBody) body).getValue());
                    break;
                case BINARY:
                    this.bodyMatcher = new BinaryMatcher(((BinaryBody) body).getValue());
                    break;
            }
        }
        return this;
    }

    private HttpRequestMatcher withHeaders(Header... headers) {
        this.headerMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(headers));
        return this;
    }

    private HttpRequestMatcher withHeaders(List<Header> headers) {
        this.headerMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(headers));
        return this;
    }

    private HttpRequestMatcher withCookies(Cookie... cookies) {
        this.cookieMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(cookies));
        return this;
    }

    private HttpRequestMatcher withCookies(List<Cookie> cookies) {
        this.cookieMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(cookies));
        return this;
    }

    public boolean matches(HttpRequest httpRequest) {
        if (httpRequest == this.httpRequest) {
            return true;
        } else if (httpRequest != null) {
            boolean methodMatches = matches(methodMatcher, httpRequest.getMethod());
            boolean urlMatches = matches(urlMatcher, httpRequest.getURL());
            boolean pathMatches = matches(pathMatcher, httpRequest.getPath());
            boolean queryStringParametersMatches = matches(queryStringParameterMatcher, (httpRequest.getQueryStringParameters() != null ? new ArrayList<KeyToMultiValue>(httpRequest.getQueryStringParameters()) : null));
            boolean bodyMatches;
            if (bodyMatcher instanceof BinaryMatcher) {
                bodyMatches = matches(bodyMatcher, httpRequest.getRawBodyBytes());
            } else {
                bodyMatches = matches(bodyMatcher, (httpRequest.getBody() != null ? httpRequest.getBody().toString() : ""));
            }
            boolean headersMatch = matches(headerMatcher, (httpRequest.getHeaders() != null ? new ArrayList<KeyToMultiValue>(httpRequest.getHeaders()) : null));
            boolean cookiesMatch = matches(cookieMatcher, (httpRequest.getCookies() != null ? new ArrayList<KeyToMultiValue>(httpRequest.getCookies()) : null));
            boolean result = methodMatches && urlMatches && pathMatches && queryStringParametersMatches && bodyMatches && headersMatch && cookiesMatch;
            if (!result && logger.isDebugEnabled()) {
                logger.debug("\n\nMatcher:" + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        "[" + this + "]" + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        "did not match request:" + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        "[" + httpRequest + "]" + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        "because:" + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        "methodMatches = " + methodMatches + "" + System.getProperty("line.separator") +
                        "urlMatches = " + urlMatches + "" + System.getProperty("line.separator") +
                        "pathMatches = " + pathMatches + "" + System.getProperty("line.separator") +
                        "queryStringParametersMatch = " + queryStringParametersMatches + "" + System.getProperty("line.separator") +
                        "bodyMatches = " + bodyMatches + "" + System.getProperty("line.separator") +
                        "headersMatch = " + headersMatch + "" + System.getProperty("line.separator") +
                        "cookiesMatch = " + cookiesMatch);
            }
            return result;
        } else {
            return false;
        }
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

    @Override
    public String toString() {
        try {
            return ObjectMapperFactory
                    .createObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(httpRequest);
        } catch (Exception e) {
            return super.toString();
        }
    }
}
