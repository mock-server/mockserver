package org.mockserver.examples.mockserver;

import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.LogEventRequestAndResponse;
import org.mockserver.verify.VerificationTimes;

import java.util.List;

import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.Parameter.param;

public class MockServerClientExamples {

    public void createExpectationMockServerClient() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withMethod("GET")
                    .withPath("/view/cart")
                    .withCookies(
                        cookie("session", "4930456C-C718-476F-971F-CB8E047AB349")
                    )
                    .withQueryStringParameters(
                        param("cartId", "055CA455-1DF7-45BB-8535-4F83E7266092")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void createExpectationOverTLSMockServerClient() {
        new MockServerClient("localhost", 1080)
            .withSecure(true)
            .when(
                request()
                    .withMethod("GET")
                    .withPath("/view/cart")
                    .withCookies(
                        cookie("session", "4930456C-C718-476F-971F-CB8E047AB349")
                    )
                    .withQueryStringParameters(
                        param("cartId", "055CA455-1DF7-45BB-8535-4F83E7266092")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void createExpectationClientAndServer() {
        new ClientAndServer(1080)
            .when(
                request()
                    .withMethod("GET")
                    .withPath("/view/cart")
                    .withCookies(
                        cookie("session", "4930456C-C718-476F-971F-CB8E047AB349")
                    )
                    .withQueryStringParameters(
                        param("cartId", "055CA455-1DF7-45BB-8535-4F83E7266092")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void verifyRequests() {
        new MockServerClient("localhost", 1080)
            .verify(
                request()
                    .withPath("/some/path"),
                VerificationTimes.atLeast(2)
            );
    }

    public void verifyRequestSequence() {
        new MockServerClient("localhost", 1080)
            .verify(
                request()
                    .withPath("/some/path/one"),
                request()
                    .withPath("/some/path/two"),
                request()
                    .withPath("/some/path/three")
            );
    }

    public void retrieveRecordedRequests() {
        HttpRequest[] recordedRequests = new MockServerClient("localhost", 1080)
            .retrieveRecordedRequests(
                request()
                    .withPath("/some/path")
                    .withMethod("POST")
            );
    }

    public void retrieveRecordedRequestResponses() {
        LogEventRequestAndResponse[] httpRequestAndHttpResponse = new MockServerClient("localhost", 1080)
            .retrieveRecordedRequestsAndResponses(
                request()
                    .withPath("/some/path")
                    .withMethod("POST")
            );
    }

    public void retrieveRecordedLogMessages() {
        String[] logMessages = new MockServerClient("localhost", 1080)
            .retrieveLogMessagesArray(
                request()
                    .withPath("/some/path")
                    .withMethod("POST")
            );
    }

    public void clear() {
        new MockServerClient("localhost", 1080).clear(
            request()
                .withPath("/some/path")
                .withMethod("POST")
        );
    }

    public void clearLogs() {
        new MockServerClient("localhost", 1080).clear(
            request()
                .withPath("/some/path")
                .withMethod("POST"),
            ClearType.LOG
        );
    }

    public void reset() {
        new MockServerClient("localhost", 1080).reset();
    }

    public void bindToAdditionFreePort() {
        List<Integer> boundPorts = new MockServerClient("localhost", 1080).bind(
            0
        );
    }

    public void bindToAdditionalSpecifiedPort() {
        List<Integer> boundPorts = new MockServerClient("localhost", 1080).bind(
            1081, 1082
        );
    }

}
