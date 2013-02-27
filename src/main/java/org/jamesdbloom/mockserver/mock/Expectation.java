package org.jamesdbloom.mockserver.mock;

import com.google.common.annotations.VisibleForTesting;
import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.jamesdbloom.mockserver.model.ModelObject;

/**
 * @author jamesdbloom
 */
public class Expectation extends ModelObject {

    private final HttpRequestMatcher httpRequestMatcher;
    private final Times times;
    private HttpResponse httpResponse;
    private HttpResponse httpRequest;

    public Expectation(HttpRequestMatcher httpRequestMatcher, Times times) {
        this.httpRequestMatcher = httpRequestMatcher;
        this.times = times;
    }

    public Expectation respond(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse.applyDelay();
    }

    public boolean matches(HttpRequest httpRequest) {
        boolean matches = times.greaterThenZero() && httpRequestMatcher.matches(httpRequest);
        if (matches) {
            times.decrement();
        }
        return matches;
    }

    public void setNotUnlimitedResponses() {
        times.setNotUnlimitedResponses();
    }

    public boolean contains(HttpRequestMatcher httpRequestMatcher) {
        return httpRequestMatcher != null && this.httpRequestMatcher.equals(httpRequestMatcher);
    }

    @VisibleForTesting
    public HttpResponse getHttpRequest() {
        return httpRequest;
    }
}
