package org.jamesdbloom.mockserver.mock;

import com.google.common.annotations.VisibleForTesting;
import org.jamesdbloom.mockserver.mappers.ExpectationMapper;
import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.jamesdbloom.mockserver.model.ModelObject;

/**
 * @author jamesdbloom
 */
public class Expectation extends ModelObject {

    private final HttpRequest httpRequest;
    private final Times times;
    private HttpResponse httpResponse;

    public Expectation(HttpRequest httpRequest, Times times) {
        this.httpRequest = httpRequest;
        this.times = times;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse.applyDelay();
    }

    public Times getTimes() {
        return times;
    }

    public Expectation respond(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }

    public boolean matches(HttpRequest httpRequest) {
        boolean matches = times.greaterThenZero() && new ExpectationMapper().transformsToMatcher(this.httpRequest).matches(httpRequest);
        if (matches) {
            times.decrement();
        }
        return matches;
    }

    public void setNotUnlimitedResponses() {
        times.setNotUnlimitedResponses();
    }

    public boolean contains(HttpRequest httpRequest) {
        return httpRequest != null && this.httpRequest.equals(httpRequest);
    }
}
