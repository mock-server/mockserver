package org.mockserver.examples.mockserver;

import org.mockserver.client.MockServerClient;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Format;

import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class RetrieveRecordedExpectationsExample {

    public void retrieveAllRecordedExpectations() {
        Expectation[] recordedExpectations = new MockServerClient("localhost", 1080)
            .retrieveRecordedExpectations(
                request()
            );
    }

    public void retrieveRecordedExpectationsUsingRequestMatcher() {
        Expectation[] recordedExpectations = new MockServerClient("localhost", 1080)
            .retrieveRecordedExpectations(
                request()
                    .withPath("/some/path")
                    .withMethod("POST")
            );
    }

    public void retrieveRecordedExpectationsAsJava() {
        String recordedExpectations = new MockServerClient("localhost", 1080)
            .retrieveRecordedExpectations(
                request()
                    .withPath("/some/path"),
                Format.JAVA
            );
    }

    public void retrieveRecordedExpectationsInJson() {
        String recordedExpectations = new MockServerClient("localhost", 1080)
            .retrieveRecordedExpectations(
                request()
                    .withPath("/some/path"),
                Format.JSON
            );
    }
}
