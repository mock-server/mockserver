package org.jamesdbloom.mockserver.client;

import org.jamesdbloom.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public class ForwardChainExpectation {

    private final MockServerClient mockServerClient;
    private final ExpectationDTO expectationDTO;

    public ForwardChainExpectation(MockServerClient mockServerClient, ExpectationDTO expectationDTO) {
        this.mockServerClient = mockServerClient;
        this.expectationDTO = expectationDTO;
    }

    public void respond(HttpResponse httpResponse) {
        expectationDTO.respond(httpResponse);
        mockServerClient.sendExpectation(expectationDTO);
    }
}
