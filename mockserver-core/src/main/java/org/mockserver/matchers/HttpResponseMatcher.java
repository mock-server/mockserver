package org.mockserver.matchers;

import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpResponseMatcher extends EqualsHashCodeToString implements Matcher<HttpResponse> {

    private HttpResponse httpResponse;
    private IntegerMatcher statusCodeMatcher = null;
    private BodyMatcher bodyMatcher = null;
    private MapMatcher headerMatcher = null;
    private MapMatcher cookieMatcher = null;

    public HttpResponseMatcher(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        if (httpResponse != null) {
            withStatusCode(httpResponse.getStatusCode());
            withBody(httpResponse.getBody());
            withHeaders(httpResponse.getHeaders());
            withCookies(httpResponse.getCookies());
        }
    }

    private HttpResponseMatcher withStatusCode(Integer statusCode) {
        this.statusCodeMatcher = new IntegerMatcher(statusCode);
        return this;
    }

    private HttpResponseMatcher withBody(Body body) {
        if (body != null) {
            switch (body.getType()) {
                case STRING:
                    this.bodyMatcher = new ExactStringMatcher(((StringBody) body).getValue());
                    break;
                case REGEX:
                    this.bodyMatcher = new RegexStringMatcher(((StringBody) body).getValue());
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

    private HttpResponseMatcher withHeaders(Header... headers) {
        this.headerMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(headers));
        return this;
    }

    private HttpResponseMatcher withHeaders(List<Header> headers) {
        this.headerMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(headers));
        return this;
    }

    private HttpResponseMatcher withCookies(Cookie... cookies) {
        this.cookieMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(cookies));
        return this;
    }

    private HttpResponseMatcher withCookies(List<Cookie> cookies) {
        this.cookieMatcher = new MapMatcher(KeyToMultiValue.toMultiMap(cookies));
        return this;
    }

    public boolean matches(HttpResponse httpResponse) {
        if (httpResponse != null) {
            boolean statusCodeMatches = matches(statusCodeMatcher, httpResponse.getStatusCode());
            boolean bodyMatches;
            if (bodyMatcher instanceof BinaryMatcher) {
                bodyMatches = matches(bodyMatcher, httpResponse.getRawBodyBytes());
            } else {
                bodyMatches = matches(bodyMatcher, (httpResponse.getBody() != null ? httpResponse.getBody().toString() : ""));
            }
            boolean headersMatch = matches(headerMatcher, (httpResponse.getHeaders() != null ? new ArrayList<KeyToMultiValue>(httpResponse.getHeaders()) : null));
            boolean cookiesMatch = matches(cookieMatcher, (httpResponse.getCookies() != null ? new ArrayList<KeyToMultiValue>(httpResponse.getCookies()) : null));
            boolean result = statusCodeMatches && bodyMatches && headersMatch && cookiesMatch;
            if (!result && logger.isDebugEnabled()) {
                logger.debug("\n\nMatcher:" + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        "[" + this + "]" + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        "did not match request:" + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        "[" + httpResponse + "]" + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        "because:" + System.getProperty("line.separator") + System.getProperty("line.separator") +
                        "statusCodeMatches = " + statusCodeMatches + "" + System.getProperty("line.separator") +
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
                    .writeValueAsString(httpResponse);
        } catch (Exception e) {
            return super.toString();
        }
    }
}
