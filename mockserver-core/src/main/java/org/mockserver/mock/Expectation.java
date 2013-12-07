package org.mockserver.mock;

import org.mockserver.mappers.ExpectationMapper;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ModelObject;

/**
 * @author jamesdbloom
 */
public class Expectation extends ModelObject {

    public static final ExpectationMapper EXPECTATION_MAPPER = new ExpectationMapper();
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
        return (httpResponse != null ? httpResponse.applyDelay() : null);
    }

    public Times getTimes() {
        return times;
    }

    public Expectation respond(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }

    public boolean matches(HttpRequest httpRequest) {
        logger.trace("\nMatching expectation: \n{} \nwith incoming request: \n{}\n", this.httpRequest, httpRequest);
        boolean matches = times.greaterThenZero() && EXPECTATION_MAPPER.transformsToMatcher(this.httpRequest).matches(httpRequest);
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
