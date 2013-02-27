package org.jamesdbloom.mockserver.client;

import com.google.common.annotations.VisibleForTesting;
import org.jamesdbloom.mockserver.client.serialization.model.ExpectationDTO;
import org.jamesdbloom.mockserver.mock.Expectation;
import org.jamesdbloom.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public class ForwardChainExpectation {

    private final MockServerClient mockServerClient;
    private final Expectation expectation;

    public ForwardChainExpectation(MockServerClient mockServerClient, Expectation expectation) {
        this.mockServerClient = mockServerClient;
        this.expectation = expectation;
    }

    public void respond(HttpResponse httpResponse) {
        expectation.respond(httpResponse);
        mockServerClient.sendExpectation(expectation);
    }

    @VisibleForTesting
    Expectation getExpectation() {
        return expectation;
    }
}
