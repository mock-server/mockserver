package org.mockserver.mock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;

/**
 * @author jamesdbloom
 */
public class Expectation extends ObjectWithJsonToString {

    private final HttpRequest httpRequest;
    private final Times times;
    private final TimeToLive timeToLive;
    private final HttpRequestMatcher httpRequestMatcher;
    private HttpResponse httpResponse;
    private HttpForward httpForward;
    private HttpError httpError;
    private HttpCallback httpCallback;

    public Expectation(HttpRequest httpRequest, Times times, TimeToLive timeToLive) {
        this.httpRequest = httpRequest;
        this.times = times;
        this.timeToLive = timeToLive;
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

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public HttpForward getHttpForward() {
        return httpForward;
    }

    public HttpError getHttpError() {
        return httpError;
    }

    public HttpCallback getHttpCallback() {
        return httpCallback;
    }

    public Action getAction(boolean applyDelay) {
        if (httpResponse != null) {
            return getHttpResponse(applyDelay);
        } else if (httpForward != null) {
            return getHttpForward();
        } else if (httpError != null) {
            return getHttpError();
        } else {
            return getHttpCallback();
        }
    }

    public Times getTimes() {
        return times;
    }

    public TimeToLive getTimeToLive() {
        return timeToLive;
    }

    public Expectation thenRespond(HttpResponse httpResponse) {
        if (httpResponse != null) {
            if (httpForward != null) {
                throw new IllegalArgumentException("It is not possible to set a response once a forward has been set");
            }
            if (httpError != null) {
                throw new IllegalArgumentException("It is not possible to set a response once an error has been set");
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
            if (httpError != null) {
                throw new IllegalArgumentException("It is not possible to set a forward once an error has been set");
            }
            if (httpCallback != null) {
                throw new IllegalArgumentException("It is not possible to set a forward once a callback has been set");
            }
            this.httpForward = httpForward;
        }
        return this;
    }

    public Expectation thenError(HttpError httpError) {
        if (httpError != null) {
            if (httpResponse != null) {
                throw new IllegalArgumentException("It is not possible to set an error once a response has been set");
            }
            if (httpForward != null) {
                throw new IllegalArgumentException("It is not possible to set an error once a forward has been set");
            }
            if (httpCallback != null) {
                throw new IllegalArgumentException("It is not possible to set an error once a callback has been set");
            }
            this.httpError = httpError;
        }
        return this;
    }

    public Expectation thenCallback(HttpCallback httpCallback) {
        if (httpCallback != null) {
            if (httpResponse != null) {
                throw new IllegalArgumentException("It is not possible to set a callback once a response has been set");
            }
            if (httpError != null) {
                throw new IllegalArgumentException("It is not possible to set a callback once an error has been set");
            }
            if (httpForward != null) {
                throw new IllegalArgumentException("It is not possible to set a callback once a forward has been set");
            }
            this.httpCallback = httpCallback;
        }
        return this;
    }

    public boolean matches(HttpRequest httpRequest) {
        return hasRemainingMatches() && isStillAlive() && httpRequestMatcher.matches(httpRequest, true);
    }

    public boolean hasRemainingMatches() {
        return times == null || times.greaterThenZero();
    }

    @JsonIgnore
    public boolean isStillAlive() {
        return timeToLive == null || timeToLive.stillAlive();
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
