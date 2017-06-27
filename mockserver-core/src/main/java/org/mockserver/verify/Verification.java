package org.mockserver.verify;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithJsonToString;

import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class Verification extends ObjectWithJsonToString {
    private HttpRequest httpRequest = request();
    private VerificationTimes times = VerificationTimes.atLeast(1);

    public static Verification verification() {
        return new Verification();
    }

    public Verification withRequest(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public Verification withTimes(VerificationTimes times) {
        this.times = times;
        return this;
    }

    public VerificationTimes getTimes() {
        return times;
    }
}
