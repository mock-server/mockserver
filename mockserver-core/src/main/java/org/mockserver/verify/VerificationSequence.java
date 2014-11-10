package org.mockserver.verify;

import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.EqualsHashCodeToString;
import org.mockserver.model.HttpRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class VerificationSequence extends EqualsHashCodeToString {
    private List<HttpRequest> httpRequests = new ArrayList<HttpRequest>();

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

    @Override
    public String toString() {
        try {
            return ObjectMapperFactory
                    .createObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(this);
        } catch (Exception e) {
            return super.toString();
        }
    }
}
