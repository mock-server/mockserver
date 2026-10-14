package org.mockserver.serialization.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.mockserver.model.*;
import org.mockserver.serialization.deserializers.body.VerificationHttpResponseDTODeserializer;
import org.mockserver.verify.VerificationSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class VerificationSequenceDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<VerificationSequence> {
    private List<RequestDefinitionDTO> httpRequests = new ArrayList<>();
    private List<HttpResponseDTO> httpResponses = new ArrayList<>();
    private List<ExpectationId> expectationIds = new ArrayList<>();
    private Integer maximumNumberOfRequestToReturnInVerificationFailure;
    private Long timeout;

    public VerificationSequenceDTO(VerificationSequence verification) {
        if (verification != null) {
            for (RequestDefinition httpRequest : verification.getHttpRequests()) {
                if (httpRequest != null) {
                    httpRequests.add(RequestDefinitionDTO.of(httpRequest));
                }
            }
            for (HttpResponse httpResponse : verification.getHttpResponses()) {
                httpResponses.add(new HttpResponseDTO(httpResponse));
            }
            expectationIds.addAll(verification.getExpectationIds());
            maximumNumberOfRequestToReturnInVerificationFailure = verification.getMaximumNumberOfRequestToReturnInVerificationFailure();
            timeout = verification.getTimeout();
        }
    }

    public VerificationSequenceDTO() {
    }

    public VerificationSequence buildObject() {
        List<RequestDefinition> httpRequests = new ArrayList<>();
        for (RequestDefinitionDTO httpRequest : this.httpRequests) {
            httpRequests.add(httpRequest.buildObject());
        }
        List<HttpResponse> httpResponses = new ArrayList<>();
        for (HttpResponseDTO httpResponse : this.httpResponses) {
            httpResponses.add(httpResponse.buildObject());
        }
        return new VerificationSequence()
            .withRequests(httpRequests)
            .withResponses(httpResponses)
            .withExpectationIds(expectationIds)
            .withMaximumNumberOfRequestToReturnInVerificationFailure(maximumNumberOfRequestToReturnInVerificationFailure)
            .withTimeout(timeout);
    }

    public List<RequestDefinitionDTO> getHttpRequests() {
        return httpRequests;
    }

    public VerificationSequenceDTO setHttpRequests(List<RequestDefinitionDTO> httpRequests) {
        this.httpRequests = httpRequests;
        return this;
    }

    public List<HttpResponseDTO> getHttpResponses() {
        return httpResponses;
    }

    @JsonDeserialize(contentUsing = VerificationHttpResponseDTODeserializer.class)
    public VerificationSequenceDTO setHttpResponses(List<HttpResponseDTO> httpResponses) {
        this.httpResponses = httpResponses;
        return this;
    }

    public List<ExpectationId> getExpectationIds() {
        return expectationIds;
    }

    public VerificationSequenceDTO setExpectationIds(List<ExpectationId> expectationIds) {
        this.expectationIds = expectationIds;
        return this;
    }

    public Integer getMaximumNumberOfRequestToReturnInVerificationFailure() {
        return maximumNumberOfRequestToReturnInVerificationFailure;
    }

    public VerificationSequenceDTO setMaximumNumberOfRequestToReturnInVerificationFailure(Integer maximumNumberOfRequestToReturnInVerificationFailure) {
        this.maximumNumberOfRequestToReturnInVerificationFailure = maximumNumberOfRequestToReturnInVerificationFailure;
        return this;
    }

    public Long getTimeout() {
        return timeout;
    }

    public VerificationSequenceDTO setTimeout(Long timeout) {
        this.timeout = timeout;
        return this;
    }
}
