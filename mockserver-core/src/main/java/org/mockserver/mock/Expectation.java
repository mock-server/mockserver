package org.mockserver.mock;

import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;

/**
 * @author jamesdbloom
 */
public class Expectation extends ObjectWithJsonToString {

    private final HttpRequest httpRequest;
    private final Times times;
    private final HttpRequestMatcher httpRequestMatcher;
    private HttpResponse httpResponse;
    private HttpForward httpForward;
    private HttpCallback httpCallback;

    public Expectation(HttpRequest httpRequest, Times times) {
        this.httpRequest = httpRequest;
        this.times = times;
        this.httpRequestMatcher = new MatcherBuilder().transformsToMatcher(this.httpRequest);
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public HttpResponse getHttpResponse(boolean applyDelay) {
        if (httpResponse != null) {
            if (applyDelay) {
                return httpResponse.applyDelay();
            } else {
                return httpResponse;
            }
        } else {
            return null;
        }
    }

    public HttpForward getHttpForward() {
        return httpForward;
    }

    public HttpCallback getHttpCallback() {
        return httpCallback;
    }

    public Action getAction(boolean applyDelay) {
        if (httpResponse != null) {
            return getHttpResponse(applyDelay);
        } else if (httpForward != null) {
            return getHttpForward();
        } else {
            return getHttpCallback();
        }
    }

    public Times getTimes() {
        return times;
    }

    public Expectation thenRespond(HttpResponse httpResponse) {
        if (httpResponse != null) {
            if (httpForward != null) {
                throw new IllegalArgumentException("It is not possible to set a response once a forward has been set");
            }
            if (httpCallback != null) {
                throw new IllegalArgumentException("It is not possible to set a response once a callback has been set");
            }
            this.httpResponse = httpResponse;
        }
        return this;
    }

    public Expectation thenForward(HttpForward httpForward) {
        if (httpForward != null) {
            if (httpResponse != null) {
                throw new IllegalArgumentException("It is not possible to set a forward once a response has been set");
            }
            if (httpCallback != null) {
                throw new IllegalArgumentException("It is not possible to set a forward once a callback has been set");
            }
            this.httpForward = httpForward;
        }
        return this;
    }


    public Expectation thenCallback(HttpCallback httpCallback) {
        if (httpCallback != null) {
            if (httpResponse != null) {
                throw new IllegalArgumentException("It is not possible to set a callback once a response has been set");
            }
            if (httpForward != null) {
                throw new IllegalArgumentException("It is not possible to set a callback once a forward has been set");
            }
            this.httpCallback = httpCallback;
        }
        return this;
    }

    public boolean matches(HttpRequest httpRequest) {
        logger.trace("\nMatching expectation: \n{} \nwith incoming http: \n{}" + System.getProperty("line.separator"), this.httpRequest, httpRequest);
        return hasRemainingMatches() && httpRequestMatcher.matches(httpRequest);
    }

    private boolean hasRemainingMatches() {
        return times == null || times.greaterThenZero();
    }

    public void decrementRemainingMatches() {
        if (times != null) {
            times.decrement();
        }
    }

    public void setNotUnlimitedResponses() {
        if (times != null) {
            times.setNotUnlimitedResponses();
        }
    }

    public boolean contains(HttpRequest httpRequest) {
        return httpRequest != null && this.httpRequest.equals(httpRequest);
    }
}
