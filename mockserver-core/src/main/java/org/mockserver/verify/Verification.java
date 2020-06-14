package org.mockserver.verify;

import org.mockserver.model.ObjectWithJsonToString;
import org.mockserver.model.RequestDefinition;

import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class Verification extends ObjectWithJsonToString {
    private RequestDefinition httpRequest = request();
    private VerificationTimes times = VerificationTimes.atLeast(1);

    public static Verification verification() {
        return new Verification();
    }

    public Verification withRequest(RequestDefinition requestDefinition) {
        this.httpRequest = requestDefinition;
        return this;
    }

    public RequestDefinition getHttpRequest() {
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
