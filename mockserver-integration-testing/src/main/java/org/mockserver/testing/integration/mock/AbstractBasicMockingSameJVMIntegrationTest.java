package org.mockserver.testing.integration.mock;

import org.junit.Test;
import org.mockserver.file.FileReader;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.mockserver.testing.integration.callback.PrecannedTestExpectationForwardCallbackRequest;
import org.mockserver.testing.integration.callback.PrecannedTestExpectationForwardCallbackRequestAndResponse;
import org.mockserver.testing.integration.callback.PrecannedTestExpectationResponseCallback;
import org.mockserver.uuid.UUIDService;
import org.mockserver.verify.VerificationTimes;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.mock.Expectation.when;
import static org.mockserver.mock.OpenAPIExpectation.openAPIExpectation;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.*;
import static org.mockserver.model.HttpStatusCode.CREATED_201;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.OpenAPIDefinition.openAPI;

/**
 * @author jamesdbloom
 */
public abstract class AbstractBasicMockingSameJVMIntegrationTest extends AbstractBasicMockingIntegrationTest {

    @Test
    public void shouldReturnResponseForCallbackClassWithDelay() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("callback"))
            )
            .respond(
                callback()
                    .withCallbackClass(PrecannedTestExpectationResponseCallback.class)
                    .withDelay(new Delay(SECONDS, 2))
            );

        // then
        long timeBeforeRequest = System.currentTimeMillis();
        HttpResponse httpResponse = makeRequest(
            request()
                .withPath(calculatePath("callback"))
                .withMethod("POST")
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            HEADERS_TO_IGNORE
        );
        long timeAfterRequest = System.currentTimeMillis();

        // and
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withHeaders(
                    header("x-callback", "test_callback_header")
                )
                .withBody("a_callback_response"),
            httpResponse
        );
        assertThat(timeAfterRequest - timeBeforeRequest, greaterThanOrEqualTo(MILLISECONDS.toMillis(1900)));
        assertThat(timeAfterRequest - timeBeforeRequest, lessThanOrEqualTo(SECONDS.toMillis(4)));
    }

    @Test
    public void shouldReturnResponseForCallbackClassForSpecifiedClassWithPrecannedResponse() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("callback"))
            )
            .respond(
                callback()
                    .withCallbackClass(PrecannedTestExpectationResponseCallback.class)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withHeaders(
                    header("x-callback", "test_callback_header")
                )
                .withBody("a_callback_response"),
            makeRequest(
                request()
                    .withPath(calculatePath("callback"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE
            )
        );
    }

    @Test
    public void shouldForwardCallbackClassWithDelay() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                callback()
                    .withCallbackClass(PrecannedTestExpectationForwardCallbackRequest.class)
                    .withDelay(new Delay(SECONDS, 2))
            );

        // then
        long timeBeforeRequest = System.currentTimeMillis();
        HttpResponse httpResponse = makeRequest(
            request()
                .withPath(calculatePath("echo"))
                .withMethod("POST")
                .withHeaders(
                    header("x-test", "test_headers_and_body"),
                    header("x-echo-server-port", insecureEchoServer.getPort())
                )
                .withBody("an_example_body_http"),
            HEADERS_TO_IGNORE
        );
        long timeAfterRequest = System.currentTimeMillis();

        // and
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            httpResponse
        );
        assertThat(timeAfterRequest - timeBeforeRequest, greaterThanOrEqualTo(MILLISECONDS.toMillis(1900)));
        assertThat(timeAfterRequest - timeBeforeRequest, lessThanOrEqualTo(SECONDS.toMillis(4)));
    }

    @Test
    public void shouldForwardCallbackClassToOverrideRequestInTestClasspathAsClass() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                callback()
                    .withCallbackClass(PrecannedTestExpectationForwardCallbackRequest.class)
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", insecureEchoServer.getPort())
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE
            )
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
            )
                .thenForward(
                    callback()
                        .withCallbackClass(PrecannedTestExpectationForwardCallbackRequest.class)
                )
        ));

        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", secureEchoServer.getPort())
                    )
                    .withBody("an_example_body_https"),
                HEADERS_TO_IGNORE
            )
        );
    }

    @Test
    public void shouldForwardCallbackClassToOverrideRequestInTestClasspathAsString() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                callback()
                    .withCallbackClass("org.mockserver.testing.integration.callback.PrecannedTestExpectationForwardCallbackRequest")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", insecureEchoServer.getPort())
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE
            )
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
            )
                .thenForward(
                    callback()
                        .withCallbackClass(PrecannedTestExpectationForwardCallbackRequest.class)
                )
        ));

        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", secureEchoServer.getPort())
                    )
                    .withBody("an_example_body_https"),
                HEADERS_TO_IGNORE
            )
        );
    }

    @Test
    public void shouldForwardCallbackClassToOverrideRequestAndResponseInTestClasspath() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                callback()
                    .withCallbackClass(PrecannedTestExpectationForwardCallbackRequestAndResponse.class)
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-response-test", "x-response-test"),
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overidden_response_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", insecureEchoServer.getPort())
                    )
                    .withBody("an_example_body_http"),
                HEADERS_TO_IGNORE
            )
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(
            new Expectation(
                request()
                    .withPath(calculatePath("echo"))
            )
                .thenForward(
                    callback()
                        .withCallbackClass(PrecannedTestExpectationForwardCallbackRequestAndResponse.class)
                )
        ));

        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-response-test", "x-response-test"),
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overidden_response_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body"),
                        header("x-echo-server-port", secureEchoServer.getPort())
                    )
                    .withBody("an_example_body_https"),
                HEADERS_TO_IGNORE
            )
        );
    }

    @Test
    public void shouldAllowSimultaneousForwardAndResponseExpectations() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo")),
                once()
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(insecureEchoServer.getPort())
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                once()
            )
            .respond(
                response()
                    .withBody("some_body")
            );

        // then
        // - forward
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body"),
                HEADERS_TO_IGNORE)
        );
        // - respond
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                HEADERS_TO_IGNORE)
        );
        // - no response or forward
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("test_headers_and_body")),
                HEADERS_TO_IGNORE)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingOpenAPIExpectationWithUrl() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .upsert(openAPIExpectation(
                "org/mockserver/mock/openapi_petstore_example.json"
            ));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("x-next", "some_string_value")
                .withHeader("content-type", "application/json")
                .withBody(json("[ {" + NEW_LINE +
                    "  \"id\" : 0," + NEW_LINE +
                    "  \"name\" : \"some_string_value\"," + NEW_LINE +
                    "  \"tag\" : \"some_string_value\"" + NEW_LINE +
                    "} ]", MediaType.APPLICATION_JSON)),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath("/pets")
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(CREATED_201.code())
                .withReasonPhrase(CREATED_201.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath("/pets")
                    .withBody(json("{" + NEW_LINE +
                        "  \"id\" : 0," + NEW_LINE +
                        "  \"name\" : \"some_string_value\"," + NEW_LINE +
                        "  \"tag\" : \"some_string_value\"" + NEW_LINE +
                        "}", MediaType.APPLICATION_JSON)),
                HEADERS_TO_IGNORE)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader("content-type", "application/json")
                .withBody(json("{" + NEW_LINE +
                    "  \"id\" : 0," + NEW_LINE +
                    "  \"name\" : \"some_string_value\"," + NEW_LINE +
                    "  \"tag\" : \"some_string_value\"" + NEW_LINE +
                    "}", MediaType.APPLICATION_JSON)),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath("/pets/12345")
                    .withHeader("x-request-id", UUIDService.getUUID()),
                HEADERS_TO_IGNORE)
        );

        // and
        assertThat(upsertedExpectations.length, is(4));
        assertThat(upsertedExpectations[0], is(
            when("org/mockserver/mock/openapi_petstore_example.json", "listPets")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("x-next", "some_string_value")
                        .withHeader("content-type", "application/json")
                        .withBody(json("[ {" + NEW_LINE +
                            "  \"id\" : 0," + NEW_LINE +
                            "  \"name\" : \"some_string_value\"," + NEW_LINE +
                            "  \"tag\" : \"some_string_value\"" + NEW_LINE +
                            "} ]"))
                )
        ));
        assertThat(upsertedExpectations[1], is(
            when("org/mockserver/mock/openapi_petstore_example.json", "createPets")
                .thenRespond(
                    response()
                        .withStatusCode(201)
                )
        ));
        assertThat(upsertedExpectations[2], is(
            when("org/mockserver/mock/openapi_petstore_example.json", "showPetById")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"id\" : 0," + NEW_LINE +
                            "  \"name\" : \"some_string_value\"," + NEW_LINE +
                            "  \"tag\" : \"some_string_value\"" + NEW_LINE +
                            "}"))
                )
        ));
        assertThat(upsertedExpectations[3], is(
            when("org/mockserver/mock/openapi_petstore_example.json", "somePath")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"id\" : 0," + NEW_LINE +
                            "  \"name\" : \"some_string_value\"," + NEW_LINE +
                            "  \"tag\" : \"some_string_value\"" + NEW_LINE +
                            "}"))
                )
        ));
    }

    @Test
    public void shouldReturnResponseByMatchingOpenAPIUrlWithOperationId() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(openAPI(
                "org/mockserver/mock/openapi_petstore_example.json",
                "listPets"
            ))
            .respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath("/pets")
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE)
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(new Expectation(openAPI(
            "org/mockserver/mock/openapi_petstore_example.json",
            "listPets"
        )).thenRespond(response().withBody("some_body"))));
    }

    @Test
    public void shouldReturnResponseByMatchingOpenAPIUrlWithoutOperationId() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(openAPI().withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json"))
            .respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath("/pets")
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE)
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(new Expectation(
            openAPI()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
        ).thenRespond(response().withBody("some_body"))));
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingOpenAPIUrl() {
        // when
        Expectation[] upsertedExpectations = mockServerClient
            .when(openAPI(
                "org/mockserver/mock/openapi_petstore_example.json",
                "listPets"
            ))
            .respond(response().withBody("some_body"));

        // then
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withMethod("PUT")
                    .withPath("/pets")
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE)
        );
        assertThat(upsertedExpectations.length, is(1));
        assertThat(upsertedExpectations[0], is(new Expectation(openAPI(
            "org/mockserver/mock/openapi_petstore_example.json",
            "listPets"
        )).thenRespond(response().withBody("some_body"))));
    }

    @Test
    public void shouldVerifyNotEnoughRequestsReceivedWithOpenAPIUrl() {
        // when
        mockServerClient
            .when(openAPI().withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json"), exactly(4))
            .respond(response().withBody("some_body"));

        // and
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("/pets"))
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("/pets"))
                    .withHeader("content-type", "application/json")
                    .withBody(json("" +
                        "{" + NEW_LINE +
                        "    \"id\": 50, " + NEW_LINE +
                        "    \"name\": \"scruffles\", " + NEW_LINE +
                        "    \"tag\": \"dog\"" + NEW_LINE +
                        "}"
                    )),
                HEADERS_TO_IGNORE
            )
        );

        // then
        mockServerClient
            .verify(
                openAPI()
                    .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json"),
                VerificationTimes.atLeast(2)
            );
    }

    @Test
    public void shouldVerifySequenceOfRequestsReceivedByOpenAPIUrl() {
        // when
        String specUrlOrPayload = "org/mockserver/mock/openapi_petstore_example.json";
        mockServerClient
            .when(openAPI().withSpecUrlOrPayload(specUrlOrPayload), exactly(4))
            .respond(response().withBody("some_body"));

        // and
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("/pets"))
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("/pets"))
                    .withHeader("content-type", "application/json")
                    .withBody(json("" +
                        "{" + NEW_LINE +
                        "    \"id\": 50, " + NEW_LINE +
                        "    \"name\": \"scruffles\", " + NEW_LINE +
                        "    \"tag\": \"dog\"" + NEW_LINE +
                        "}"
                    )),
                HEADERS_TO_IGNORE
            )
        );

        // then
        try {
            mockServerClient
                .verify(
                    openAPI()
                        .withSpecUrlOrPayload(specUrlOrPayload)
                        .withOperationId("createPets"),
                    openAPI()
                        .withSpecUrlOrPayload(specUrlOrPayload)
                        .withOperationId("listPets")
                );
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"operationId\" : \"createPets\"," + NEW_LINE +
                "  \"specUrlOrPayload\" : \"" + specUrlOrPayload + "\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"operationId\" : \"listPets\"," + NEW_LINE +
                "  \"specUrlOrPayload\" : \"" + specUrlOrPayload + "\"" + NEW_LINE +
                "} ]> but was:<[ {"));
        }
        mockServerClient
            .verify(
                openAPI()
                    .withSpecUrlOrPayload(specUrlOrPayload)
                    .withOperationId("listPets"),
                openAPI()
                    .withSpecUrlOrPayload(specUrlOrPayload)
                    .withOperationId("createPets")
            );
    }

    @Test
    public void shouldRetrieveRecordedRequestsByOpenAPIUrl() {
        // when
        mockServerClient
            .when(openAPI().withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json"), exactly(4))
            .respond(response().withBody("some_body"));

        // and
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("/pets"))
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request().withPath(calculatePath("not_found")),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("/pets"))
                    .withHeader("content-type", "application/json")
                    .withBody(json("" +
                        "{" + NEW_LINE +
                        "    \"id\": 50, " + NEW_LINE +
                        "    \"name\": \"scruffles\", " + NEW_LINE +
                        "    \"tag\": \"dog\"" + NEW_LINE +
                        "}"
                    )),
                HEADERS_TO_IGNORE
            )
        );

        // then
        // TODO(jamesdbloom) why is this path not prefixed with context route?
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(openAPI().withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")),
            request()
                .withMethod("GET")
                .withPath("/pets")
                .withQueryStringParameter("limit", "10"),
            request()
                .withMethod("POST")
                .withPath("/pets")
                .withHeader("content-type", "application/json")
                .withBody(json("" +
                    "{" + NEW_LINE +
                    "    \"id\": 50, " + NEW_LINE +
                    "    \"name\": \"scruffles\", " + NEW_LINE +
                    "    \"tag\": \"dog\"" + NEW_LINE +
                    "}"
                ))
        );
    }

    @Test
    public void shouldClearExpectationsAndLogsByOpenAPIUrl() {
        // when
        mockServerClient
            .when(openAPI().withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json"), exactly(4))
            .respond(response().withBody("some_body"));
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // and
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("/pets"))
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request().withPath(calculatePath("not_found")),
                HEADERS_TO_IGNORE
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("/pets"))
                    .withHeader("content-type", "application/json")
                    .withBody(json("" +
                        "{" + NEW_LINE +
                        "    \"id\": 50, " + NEW_LINE +
                        "    \"name\": \"scruffles\", " + NEW_LINE +
                        "    \"tag\": \"dog\"" + NEW_LINE +
                        "}"
                    )),
                HEADERS_TO_IGNORE
            )
        );

        // when
        mockServerClient
            .clear(
                openAPI()
                    .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
            );


        // and then - request log cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("not_found"))
        );

        // then - expectations cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(request()
                    .withPath(calculatePath("some_path2")))
                    .thenRespond(
                        response()
                            .withBody("some_body2")
                    )
            )
        );
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("/pets"))
                    .withQueryStringParameter("limit", "10"),
                HEADERS_TO_IGNORE
            )
        );

        // and then - remaining expectations not cleared
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                HEADERS_TO_IGNORE)
        );
    }

}
