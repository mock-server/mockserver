package org.jamesdbloom.mockserver.mock;

import org.jamesdbloom.mockserver.client.serialization.model.ExpectationDTO;
import org.jamesdbloom.mockserver.mappers.ExpectationMapper;
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

    public Expectation(ExpectationDTO expectation) {
        if (expectation.getHttpRequest() != null) {
            httpRequest = new HttpRequest(expectation.getHttpRequest());
        } else {
            throw new IllegalArgumentException("HttpRequest must be specified to create an Expectation");
        }
        if (expectation.getHttpResponse() != null) {
            httpResponse = new HttpResponse(expectation.getHttpResponse());
        } else {
            throw new IllegalArgumentException("HttpResponse must be specified to create an Expectation");
        }
        if (expectation.getTimes() != null) {
            times = new Times(expectation.getTimes());
        } else {
            times = Times.unlimited();
        }
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
