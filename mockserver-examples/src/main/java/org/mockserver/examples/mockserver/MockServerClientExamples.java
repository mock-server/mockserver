package org.mockserver.examples.mockserver;

import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.ClearType;
import org.mockserver.model.LogEventRequestAndResponse;
import org.mockserver.model.RequestDefinition;
import org.mockserver.verify.VerificationTimes;

import java.util.List;

import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.OpenAPIDefinition.openAPI;
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

    public void verifyRequestsReceiveAtLeastTwice() {
        new MockServerClient("localhost", 1080)
            .verify(
                request()
                    .withPath("/some/path"),
                VerificationTimes.atLeast(2)
            );
    }

    public void verifyRequestsReceiveAtMostTwice() {
        new MockServerClient("localhost", 1080)
            .verify(
                request()
                    .withPath("/some/path"),
                VerificationTimes.atMost(2)
            );
    }

    public void verifyRequestsReceiveExactlyTwice() {
        new MockServerClient("localhost", 1080)
            .verify(
                request()
                    .withPath("/some/path"),
                VerificationTimes.exactly(2)
            );
    }

    public void verifyRequestsReceiveAtLeastTwiceByOpenAPI() {
        new MockServerClient("localhost", 1080)
            .verify(
                openAPI(
                    "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/openapi/openapi_petstore_example.json"
                ),
                VerificationTimes.atLeast(2)
            );
    }

    public void verifyRequestsReceiveExactlyOnceByOpenAPIWithOperation() {
        new MockServerClient("localhost", 1080)
            .verify(
                openAPI(
                    "org/mockserver/openapi/openapi_petstore_example.json",
                    "showPetById"
                ),
                VerificationTimes.once()

            );
    }

    public void verifyRequestsReceiveExactlyOnceByExpectationIds() {
        new MockServerClient("localhost", 1080)
            .verify(
                "31e4ca35-66c6-4645-afeb-6e66c4ca0559",
                VerificationTimes.once()
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

    public void verifyRequestSequenceUsingOpenAPI() {
        new MockServerClient("localhost", 1080)
            .verify(
                request()
                    .withPath("/status"),
                openAPI(
                    "org/mockserver/openapi/openapi_petstore_example.json",
                    "listPets"
                ),
                openAPI(
                    "org/mockserver/openapi/openapi_petstore_example.json",
                    "showPetById"
                )
            );
    }

    public void verifyRequestSequenceUsingExpectationIds() {
        new MockServerClient("localhost", 1080)
            .verify(
                "31e4ca35-66c6-4645-afeb-6e66c4ca0559",
                "66c6ca35-ca35-66f5-8feb-5e6ac7ca0559",
                "ca3531e4-23c8-ff45-88f5-4ca0c7ca0559"
            );
    }

    public void retrieveRecordedRequests() {
        RequestDefinition[] recordedRequests = new MockServerClient("localhost", 1080)
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

    public void clearWithRequestPropertiesMatcher() {
        new MockServerClient("localhost", 1080).clear(
            request()
                .withPath("/some/path")
        );
    }

    public void clearWithOpenAPIRequestMatcher() {
        new MockServerClient("localhost", 1080).clear(
            openAPI(
                "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/openapi/openapi_petstore_example.json",
                "showPetById"
            )
        );
    }

    public void clearWithExpectation() {
        new MockServerClient("localhost", 1080)
            .clear("31e4ca35-66c6-4645-afeb-6e66c4ca0559");
    }

    public void clearRequestAndLogsWithRequestPropertiesMatcher() {
        new MockServerClient("localhost", 1080).clear(
            request()
                .withPath("/some/path"),
            ClearType.LOG
        );
    }

    public void clearRequestAndLogsWithOpenAPIRequestMatcher() {
        new MockServerClient("localhost", 1080).clear(
            openAPI(
                "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/openapi/openapi_petstore_example.json",
                "showPetById"
            ),
            ClearType.LOG
        );
    }

    public void clearExpectationsWithRequestPropertiesMatcher() {
        new MockServerClient("localhost", 1080).clear(
            request()
                .withPath("/some/path"),
            ClearType.EXPECTATIONS
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
