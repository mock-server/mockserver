package org.mockserver.serialization.model;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.model.RequestDefinition;
import org.mockserver.verify.VerificationSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class VerificationSequenceDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<VerificationSequence> {
    private List<RequestDefinitionDTO> httpRequests = new ArrayList<>();

    public VerificationSequenceDTO(VerificationSequence verification) {
        if (verification != null) {
            for (RequestDefinition httpRequest : verification.getHttpRequests()) {
                if (httpRequest instanceof HttpRequest) {
                    httpRequests.add(new HttpRequestDTO((HttpRequest) httpRequest));
                } else if (httpRequest instanceof OpenAPIDefinition) {
                    httpRequests.add(new OpenAPIDefinitionDTO((OpenAPIDefinition) httpRequest));
                }
            }
        }
    }

    public VerificationSequenceDTO() {
    }

    public VerificationSequence buildObject() {
        List<RequestDefinition> httpRequests = new ArrayList<>();
        for (RequestDefinitionDTO httpRequest : this.httpRequests) {
            httpRequests.add(httpRequest.buildObject());
        }
        return new VerificationSequence()
            .withRequests(httpRequests);
    }

    public List<RequestDefinitionDTO> getHttpRequests() {
        return httpRequests;
    }

    public VerificationSequenceDTO setHttpRequests(List<RequestDefinitionDTO> httpRequests) {
        this.httpRequests = httpRequests;
        return this;
    }
}
