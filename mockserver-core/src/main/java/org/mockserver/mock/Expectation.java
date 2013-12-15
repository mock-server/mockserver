package org.mockserver.mock;

import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ModelObject;

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
        if (httpResponse != null && httpResponse.applyDelay() != null) {
            return httpResponse.applyDelay();
        } else {
            return httpResponse;
        }
    }

    public Times getTimes() {
        return times;
    }

    public Expectation respond(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }

    public boolean matches(HttpRequest httpRequest) {
        logger.trace("\nMatching expectation: \n{} \nwith incoming http: \n{}\n", this.httpRequest, httpRequest);
        boolean matches = (times == null || times.greaterThenZero()) && MatcherBuilder.transformsToMatcher(this.httpRequest).matches(httpRequest);
        if (matches && times != null) {
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
