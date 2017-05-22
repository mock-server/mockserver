package org.mockserver.verify;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class VerificationSequence extends ObjectWithReflectiveEqualsHashCodeToString {
    private List<HttpRequest> httpRequests = new ArrayList<HttpRequest>();

    public static VerificationSequence verificationSequence() {
        return new VerificationSequence();
    }

    public VerificationSequence withRequests(HttpRequest... httpRequests) {
        Collections.addAll(this.httpRequests, httpRequests);
        return this;
    }

    public VerificationSequence withRequests(List<HttpRequest> httpRequests) {
        this.httpRequests = httpRequests;
        return this;
    }

    public List<HttpRequest> getHttpRequests() {
        return httpRequests;
    }
}
