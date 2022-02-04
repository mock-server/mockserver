package org.mockserver.serialization.model;

import org.mockserver.model.ExpectationId;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithJsonToString;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.verify.Verification;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.once;

/**
 * @author jamesdbloom
 */
public class VerificationDTO extends ObjectWithJsonToString implements DTO<Verification> {
    private RequestDefinitionDTO httpRequest;
    private ExpectationId expectationId;
    private VerificationTimesDTO times;

    public VerificationDTO(Verification verification) {
        if (verification != null) {
            if (verification.getHttpRequest() instanceof HttpRequest) {
                httpRequest = new HttpRequestDTO((HttpRequest) verification.getHttpRequest());
            } else if (verification.getHttpRequest() instanceof OpenAPIDefinition) {
                httpRequest = new OpenAPIDefinitionDTO((OpenAPIDefinition) verification.getHttpRequest());
            }
            expectationId = verification.getExpectationId();
            times = new VerificationTimesDTO(verification.getTimes());
        }
    }

    public VerificationDTO() {
    }

    public Verification buildObject() {
        return verification()
            .withRequest((httpRequest != null ? httpRequest.buildObject() : null))
            .withExpectationId(expectationId)
            .withTimes((times != null ? times.buildObject() : once()));
    }

    public RequestDefinitionDTO getHttpRequest() {
        return httpRequest;
    }

    public VerificationDTO setHttpRequest(HttpRequestDTO httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public ExpectationId getExpectationId() {
        return expectationId;
    }

    public VerificationDTO setExpectationId(ExpectationId expectationId) {
        this.expectationId = expectationId;
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
