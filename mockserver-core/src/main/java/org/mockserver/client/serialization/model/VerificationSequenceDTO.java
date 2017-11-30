package org.mockserver.client.serialization.model;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class VerificationSequenceDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<VerificationSequence> {
    private List<HttpRequestDTO> httpRequests = new ArrayList<HttpRequestDTO>();

    public VerificationSequenceDTO(VerificationSequence verification) {
        if (verification != null) {
            for (HttpRequest httpRequest : verification.getHttpRequests()) {
                httpRequests.add(new HttpRequestDTO(httpRequest));
            }
        }
    }

    public VerificationSequenceDTO() {
    }

    public VerificationSequence buildObject() {
        List<HttpRequest> httpRequests = new ArrayList<HttpRequest>();
        for (HttpRequestDTO httpRequest : this.httpRequests) {
            httpRequests.add(httpRequest.buildObject());
        }
        return new VerificationSequence()
                .withRequests(httpRequests);
    }

    public List<HttpRequestDTO> getHttpRequests() {
        return httpRequests;
    }

    public VerificationSequenceDTO setHttpRequests(List<HttpRequestDTO> httpRequests) {
        this.httpRequests = httpRequests;
        return this;
    }
}
