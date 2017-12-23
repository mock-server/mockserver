package org.mockserver.examples.mockserver;

import org.mockserver.client.server.MockServerClient;

import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class RetrieveRecordedLogMessagesExample {

    public void retrieveAllLogMessages() {
        String logMessages = new MockServerClient("localhost", 1080)
            .retrieveLogMessages(
                request()
            );
    }

    public void retrieveLogMessagesUsingRequestMatcher() {
        String logMessages = new MockServerClient("localhost", 1080)
            .retrieveLogMessages(
                request()
                    .withPath("/some/path")
                    .withMethod("POST")
            );
    }

    public void retrieveLogMessagesArray() {
        String[] logMessages = new MockServerClient("localhost", 1080)
            .retrieveLogMessagesArray(
                request()
                    .withPath("/some/path")
            );
    }
}
