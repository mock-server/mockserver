package org.mockserver.examples.mockserver;

import org.mockserver.client.server.MockServerClient;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Format;

import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class RetrieveActiveExpectationsExample {

    public void retrieveAllActiveExpectations() {
        Expectation[] activeExpectations = new MockServerClient("localhost", 1080)
            .retrieveActiveExpectations(
                request()
            );
    }

    public void retrieveActiveExpectationsUsingRequestMatcher() {
        Expectation[] activeExpectations = new MockServerClient("localhost", 1080)
            .retrieveActiveExpectations(
                request()
                    .withPath("/some/path")
                    .withMethod("POST")
            );
    }

    public void retrieveActiveExpectationsAsJava() {
        String activeExpectations = new MockServerClient("localhost", 1080)
            .retrieveActiveExpectations(
                request()
                    .withPath("/some/path"),
                Format.JAVA
            );
    }

    public void retrieveActiveExpectationsInJson() {
        String activeExpectations = new MockServerClient("localhost", 1080)
            .retrieveActiveExpectations(
                request()
                    .withPath("/some/path"),
                Format.JSON
            );
    }
}
