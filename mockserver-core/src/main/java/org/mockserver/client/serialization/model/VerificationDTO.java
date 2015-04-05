package org.mockserver.client.serialization.model;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.verify.Verification;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.once;

/**
 * @author jamesdbloom
 */
public class VerificationDTO extends ObjectWithReflectiveEqualsHashCodeToString {
    private HttpRequestDTO httpRequest;
    private VerificationTimesDTO times;

    public VerificationDTO(Verification verification) {
        if (verification != null) {
            httpRequest = new HttpRequestDTO(verification.getHttpRequest(), false);
            times = new VerificationTimesDTO(verification.getTimes());
        }
    }

    public VerificationDTO() {
    }

    public Verification buildObject() {
        return new Verification()
                .withRequest((httpRequest != null ? httpRequest.buildObject() : request()))
                .withTimes((times != null ? times.buildObject() : once()));
    }

    public HttpRequestDTO getHttpRequest() {
        return httpRequest;
    }

    public VerificationDTO setHttpRequest(HttpRequestDTO httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public VerificationTimesDTO getTimes() {
        return times;
    }

    public VerificationDTO setTimes(VerificationTimesDTO times) {
        this.times = times;
        return this;
    }
}
