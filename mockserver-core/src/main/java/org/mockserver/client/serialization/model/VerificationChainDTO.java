package org.mockserver.client.serialization.model;

import org.mockserver.model.EqualsHashCodeToString;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationChain;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class VerificationChainDTO extends EqualsHashCodeToString {
    private List<HttpRequestDTO> httpRequests = new ArrayList<HttpRequestDTO>();

    public VerificationChainDTO(VerificationChain verification) {
        if (verification != null) {
            for (HttpRequest httpRequest : verification.getHttpRequests()) {
                httpRequests.add(new HttpRequestDTO(httpRequest));
            }
        }
    }

    public VerificationChainDTO() {
    }

    public VerificationChain buildObject() {
        List<HttpRequest> httpRequests = new ArrayList<HttpRequest>();
        for (HttpRequestDTO httpRequest : this.httpRequests) {
            httpRequests.add(httpRequest.buildObject());
        }
        return new VerificationChain()
                .withRequests(httpRequests);
    }

    public List<HttpRequestDTO> getHttpRequests() {
        return httpRequests;
    }

    public VerificationChainDTO setHttpRequests(List<HttpRequestDTO> httpRequests) {
        this.httpRequests = httpRequests;
        return this;
    }
}
