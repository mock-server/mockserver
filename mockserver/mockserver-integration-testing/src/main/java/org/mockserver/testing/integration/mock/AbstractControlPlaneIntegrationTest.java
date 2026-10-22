package org.mockserver.testing.integration.mock;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.mock.Expectation;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.RequestDefinition;
import org.mockserver.verify.VerificationTimes;

import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertThrows;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.unlimited;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.*;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * Transport-agnostic control-plane integration tests.
 * <p>
 * These tests exercise MockServer control-plane API semantics (verify,
 * clear, reset, retrieve, error validation) with ONLY path-based request
 * matching.  They are intentionally run once (against a single transport)
 * rather than per-transport, because they do NOT exercise request body /
 * header / cookie WIRE decoding.
 * <p>
 * Extracted from {@link AbstractBasicMockingIntegrationTest} and
 * {@link AbstractExtendedMockingIntegrationTest} as part of integration-test
 * inheritance decomposition step 1.
 *
 * @see AbstractBasicMockingIntegrationTest
 * @see AbstractExtendedMockingIntegrationTest
 */
public abstract class AbstractControlPlaneIntegrationTest extends AbstractMockingIntegrationTestBase {

    protected HttpResponse localNotFoundResponse() {
        return response()
            .withStatusCode(NOT_FOUND_404.code())
            .withReasonPhrase(NOT_FOUND_404.reasonPhrase());
    }

    // ========================================================================
    // Verify count semantics (from AbstractBasicMockingIntegrationTest)
    // ========================================================================

    @Test
    public void shouldVerifyNoRequestsReceived() {
        // when
        mockServerClient.reset();

        // then
        mockServerClient.verifyZeroInteractions();
    }

    @Test
    public void shouldVerifyNotEnoughRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );
        try {
            mockServerClient
                .verify(
                    request()
                        .withPath(calculatePath("some_path")), VerificationTimes.atLeast(2)
                );
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found at least 2 times, expected:<{" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path") + "\"" + NEW_LINE +
                "}> but was:<{"));
        }
    }

    @Test
    public void shouldVerifyNotEnoughRequestsReceivedWithMaximumNumberOfRequestToReturnInFailure() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(3)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );

        try {
            mockServerClient
                .verify(
                    request()
                        .withPath(calculatePath("some_path")),
                    VerificationTimes.atLeast(4),
                    2
                );
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), equalTo("Request not found at least 4 times, expected:<{" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path") + "\"" + NEW_LINE +
                "}> but was found 3 times among 3 total requests"));
        }
    }

    // ========================================================================
    // Verify by expectation ID (from AbstractBasicMockingIntegrationTest)
    // ========================================================================

    @Test
    public void shouldVerifyReceivedRequestsByExpectationId() {
        // when
        Expectation firstExpectation = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")), exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            )[0];
        Expectation secondExpectation = mockServerClient
            .when(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_other_path")), exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            )[0];

        // when
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );

        // then
        mockServerClient.verify(firstExpectation.getId(), VerificationTimes.atLeast(1));
        mockServerClient.verify(firstExpectation.getId(), VerificationTimes.exactly(2));
        mockServerClient.verify(secondExpectation.getId(), VerificationTimes.never());
        AssertionError firstAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(secondExpectation.getId(), VerificationTimes.atLeast(1)));
        assertThat(firstAssertionError.getMessage(), startsWith("Request not found at least once"));
        AssertionError secondAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(UUID.randomUUID().toString(), VerificationTimes.atLeast(1)));
        assertThat(secondAssertionError.getMessage(), startsWith("No expectation found with id "));
    }

    @Test
    public void shouldVerifyReceivedRequestsByExpectationIdWithIdenticalRequestMatchers() {
        // when
        Expectation firstExpectation = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")), exactly(1)
            )
            .respond(
                response()
                    .withBody("some_body")
            )[0];
        Expectation secondExpectation = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path")), exactly(1)
            )
            .respond(
                response()
                    .withBody("some_other_body")
            )[0];
        Expectation thirdExpectation = mockServerClient
            .when(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_other_path")), exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            )[0];

        // when
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_other_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );

        // then
        mockServerClient.verify(firstExpectation.getId(), VerificationTimes.atLeast(1));
        mockServerClient.verify(firstExpectation.getId(), VerificationTimes.exactly(1));
        mockServerClient.verify(secondExpectation.getId(), VerificationTimes.atLeast(1));
        mockServerClient.verify(secondExpectation.getId(), VerificationTimes.exactly(1));
        mockServerClient.verify(firstExpectation.getHttpRequest(), VerificationTimes.atLeast(2));
        AssertionError firstAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(firstExpectation.getId(), VerificationTimes.atLeast(2)));
        assertThat(firstAssertionError.getMessage(), startsWith("Request not found at least 2 times"));
        AssertionError secondAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(secondExpectation.getId(), VerificationTimes.atLeast(2)));
        assertThat(secondAssertionError.getMessage(), startsWith("Request not found at least 2 times"));
        mockServerClient.verify(thirdExpectation.getId(), VerificationTimes.never());
    }

    // ========================================================================
    // Verify sequence (from AbstractBasicMockingIntegrationTest)
    // ========================================================================

    @Test
    public void shouldVerifySequenceOfRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(6)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_two")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                getHeadersToRemove()
            )
        );
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_three")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_two")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_two")), request(calculatePath("some_path_three")));
    }

    @Test
    public void shouldVerifySequenceOfRequestsReceivedByExceptionId() {
        // when
        Expectation firstExpectation = mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(6)).respond(response().withBody("some_body"))[0];
        Expectation secondExpectation = mockServerClient.when(request().withPath(calculatePath("some_other_path.*")), exactly(6)).respond(response().withBody("some_body"))[0];

        // then
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_two")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                getHeadersToRemove()
            )
        );
        mockServerClient.verify(firstExpectation.getId());
        mockServerClient.verify(firstExpectation.getId(), firstExpectation.getId());
        mockServerClient.verify(firstExpectation.getId(), firstExpectation.getId(), firstExpectation.getId());
        AssertionError firstAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(firstExpectation.getId(), firstExpectation.getId(), firstExpectation.getId(), firstExpectation.getId(), firstExpectation.getId(), firstExpectation.getId()));
        assertThat(firstAssertionError.getMessage(), startsWith("Request sequence not found"));
        AssertionError secondAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(secondExpectation.getId()));
        assertThat(secondAssertionError.getMessage(), startsWith("Request sequence not found"));
        AssertionError thirdAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        assertThat(thirdAssertionError.getMessage(), startsWith("No expectation found with id "));
    }

    @Test
    public void shouldVerifySequenceOfRequestsReceivedByExceptionIdWithIdenticalRequestMatchers() {
        // when
        Expectation firstExpectation = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path.*")),
                exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            )[0];
        Expectation secondExpectation = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path.*")),
                exactly(2)
            )
            .respond(
                response()
                    .withBody("some_body")
            )[0];
        Expectation thirdExpectation = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_other_path.*")),
                unlimited()
            )
            .respond(
                response()
                    .withBody("some_body")
            )[0];

        // then
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_two")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_four")),
                getHeadersToRemove()
            )
        );
        mockServerClient.verify(firstExpectation.getId());
        mockServerClient.verify(firstExpectation.getId(), firstExpectation.getId());
        mockServerClient.verify(firstExpectation.getId(), firstExpectation.getId(), secondExpectation.getId());
        mockServerClient.verify(firstExpectation.getId(), firstExpectation.getId(), secondExpectation.getId(), secondExpectation.getId());
        mockServerClient.verify(firstExpectation.getId(), secondExpectation.getId(), secondExpectation.getId());
        mockServerClient.verify(secondExpectation.getId(), secondExpectation.getId());
        mockServerClient.verify(firstExpectation.getId(), secondExpectation.getId());
        // should pass only as request matchers
        mockServerClient.verify(secondExpectation.getHttpRequest(), firstExpectation.getHttpRequest());
        mockServerClient.verify(secondExpectation.getHttpRequest(), secondExpectation.getHttpRequest(), firstExpectation.getHttpRequest());
        mockServerClient.verify(firstExpectation.getHttpRequest(), firstExpectation.getHttpRequest(), firstExpectation.getHttpRequest());
        mockServerClient.verify(firstExpectation.getHttpRequest(), firstExpectation.getHttpRequest(), firstExpectation.getHttpRequest(), secondExpectation.getHttpRequest());
        AssertionError firstAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(secondExpectation.getId(), firstExpectation.getId()));
        assertThat(firstAssertionError.getMessage(), startsWith("Request sequence not found"));
        AssertionError secondAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(secondExpectation.getId(), secondExpectation.getId(), firstExpectation.getId()));
        assertThat(secondAssertionError.getMessage(), startsWith("Request sequence not found"));
        AssertionError thirdAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(firstExpectation.getId(), firstExpectation.getId(), firstExpectation.getId()));
        assertThat(thirdAssertionError.getMessage(), startsWith("Request sequence not found"));
        AssertionError fourthAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(firstExpectation.getId(), firstExpectation.getId(), firstExpectation.getId(), secondExpectation.getId()));
        assertThat(fourthAssertionError.getMessage(), startsWith("Request sequence not found"));
        AssertionError fifthAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(thirdExpectation.getId()));
        assertThat(fifthAssertionError.getMessage(), startsWith("Request sequence not found"));
        AssertionError sixAssertionError = assertThrows(AssertionError.class, () -> mockServerClient.verify(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        assertThat(sixAssertionError.getMessage(), startsWith("No expectation found with id "));
    }

    @Test
    public void shouldVerifySequenceNotFoundWithMaximumNumberOfRequestToReturnInFailure() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(3)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );

        try {
            mockServerClient
                .verify(
                    2,
                    request()
                        .withPath(calculatePath("some_other_path")),
                    request()
                        .withPath(calculatePath("some_path"))
                );
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_other_path") + "\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path") + "\"" + NEW_LINE +
                "} ]> but was not found, found 3 other requests"));
        }
    }

    // ========================================================================
    // Retrieve recorded requests (from AbstractBasicMockingIntegrationTest)
    // ========================================================================

    @Test
    public void shouldRetrieveRecordedRequests() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).respond(response().withBody("some_body"));
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request().withPath(calculatePath("not_found")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                getHeadersToRemove()
            )
        );

        // then
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(request().withPath(calculatePath("some_path.*"))),
            request(calculatePath("some_path_one")),
            request(calculatePath("some_path_three"))
        );

        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(request()),
            request(calculatePath("some_path_one")),
            request(calculatePath("not_found")),
            request(calculatePath("some_path_three"))
        );

        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path_one")),
            request(calculatePath("not_found")),
            request(calculatePath("some_path_three"))
        );
    }

    // ========================================================================
    // Clear and reset (from AbstractBasicMockingIntegrationTest)
    // ========================================================================

    @Test
    public void shouldClearExpectationsAndLogs() {
        // given - some expectations
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // and - some matching requests
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                getHeadersToRemove()
            )
        );

        // when
        mockServerClient
            .clear(
                request()
                    .withPath(calculatePath("some_path1"))
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

        // and then - request log cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path2"))
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
                getHeadersToRemove()
            )
        );
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                getHeadersToRemove()
            )
        );
    }

    @Test
    public void shouldReset() {
        // given
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // when
        mockServerClient.reset();

        // then
        // - in http
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            localNotFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                getHeadersToRemove()
            )
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path1")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path2")),
                getHeadersToRemove()
            )
        );
    }

    // ========================================================================
    // Error validation (from AbstractBasicMockingIntegrationTest)
    // ========================================================================

    @Test
    public void shouldErrorForInvalidExpectation() throws Exception {
        // when
        HttpResponse httpResponse = makeRequest(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withHeader(authorisationHeader())
                .withPath(addContextToPath("expectation"))
                .withBody("{" + NEW_LINE +
                    "  \"httpRequest\" : {" + NEW_LINE +
                    "    \"path\" : \"/path_one\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"incorrectField\" : {" + NEW_LINE +
                    "    \"body\" : \"some_body_one\"" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"times\" : {" + NEW_LINE +
                    "    \"remainingTimes\" : 1" + NEW_LINE +
                    "  }," + NEW_LINE +
                    "  \"timeToLive\" : {" + NEW_LINE +
                    "    \"unlimited\" : true" + NEW_LINE +
                    "  }" + NEW_LINE +
                    "}"),
            getHeadersToRemove()
        );

        // then
        assertThat(httpResponse.getStatusCode(), is(400));
        assertThat(httpResponse.getBodyAsString(), is("incorrect expectation json format for:" + NEW_LINE +
            "" + NEW_LINE +
            "  {" + NEW_LINE +
            "    \"httpRequest\" : {" + NEW_LINE +
            "      \"path\" : \"/path_one\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"incorrectField\" : {" + NEW_LINE +
            "      \"body\" : \"some_body_one\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"times\" : {" + NEW_LINE +
            "      \"remainingTimes\" : 1" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"timeToLive\" : {" + NEW_LINE +
            "      \"unlimited\" : true" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "" + NEW_LINE +
            " schema validation errors:" + NEW_LINE +
            "" + NEW_LINE +
            "  23 errors:" + NEW_LINE +
            "   - $.binaryResponse: is missing but it is required" + NEW_LINE +
            "   - $.dnsResponse: is missing but it is required" + NEW_LINE +
            "   - $.grpcBidiResponse: is missing, but is required, if specifying action of type GrpcBidiResponse" + NEW_LINE +
            "   - $.grpcStreamResponse: is missing, but is required, if specifying action of type GrpcStreamResponse" + NEW_LINE +
            "   - $.httpError: is missing, but is required, if specifying action of type Error" + NEW_LINE +
            "   - $.httpForward: is missing, but is required, if specifying action of type Forward" + NEW_LINE +
            "   - $.httpForwardClassCallback: is missing, but is required, if specifying action of type ForwardClassCallback" + NEW_LINE +
            "   - $.httpForwardObjectCallback: is missing, but is required, if specifying action of type ForwardObjectCallback" + NEW_LINE +
            "   - $.httpForwardTemplate: is missing, but is required, if specifying action of type ForwardTemplate" + NEW_LINE +
            "   - $.httpForwardValidateAction: is missing, but is required, if specifying action of type ForwardValidateAction" + NEW_LINE +
            "   - $.httpForwardWithFallback: is missing, but is required, if specifying action of type ForwardWithFallback" + NEW_LINE +
            "   - $.httpLlmResponse: is missing, but is required, if specifying action of type LlmResponse" + NEW_LINE +
            "   - $.httpOverrideForwardedRequest: is missing, but is required, if specifying action of type OverrideForwardedRequest" + NEW_LINE +
            "   - $.httpResponse: is missing, but is required, if specifying action of type Response" + NEW_LINE +
            "   - $.httpResponseClassCallback: is missing, but is required, if specifying action of type ResponseClassCallback" + NEW_LINE +
            "   - $.httpResponseObjectCallback: is missing, but is required, if specifying action of type ResponseObjectCallback" + NEW_LINE +
            "   - $.httpResponseTemplate: is missing, but is required, if specifying action of type ResponseTemplate" + NEW_LINE +
            "   - $.httpResponses: is missing, but is required, if specifying action of type Responses" + NEW_LINE +
            "   - $.httpSseResponse: is missing, but is required, if specifying action of type SseResponse" + NEW_LINE +
            "   - $.httpWebSocketResponse: is missing, but is required, if specifying action of type WebSocketResponse" + NEW_LINE +
            "   - $.incorrectField: is not defined in the schema and the schema does not allow additional properties" + NEW_LINE +
            "   - $.steps: is missing but it is required" + NEW_LINE +
            "   - oneOf of the following must be specified [httpError, httpForward, httpForwardClassCallback, httpForwardObjectCallback, httpForwardTemplate, httpForwardValidateAction, httpForwardWithFallback, httpOverrideForwardedRequest, httpResponse, httpResponseClassCallback, httpResponseObjectCallback, httpResponseTemplate]" + NEW_LINE +
            "  " + NEW_LINE +
            "  " + OPEN_API_SPECIFICATION_URL.replaceAll(NEW_LINE, NEW_LINE + "  ")));
    }

    @Test
    public void shouldErrorForInvalidRequest() throws Exception {
        // when
        HttpResponse httpResponse = makeRequest(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withHeader(authorisationHeader())
                .withPath(addContextToPath("clear"))
                .withBody("{" + NEW_LINE +
                    "    \"path\" : 500," + NEW_LINE +
                    "    \"method\" : true," + NEW_LINE +
                    "    \"keepAlive\" : \"false\"" + NEW_LINE +
                    "  }"),
            getHeadersToRemove()
        );

        // then
        assertThat(httpResponse.getStatusCode(), is(400));
        assertThat(httpResponse.getBodyAsString(), is("incorrect request matcher json format for:" + NEW_LINE +
            "" + NEW_LINE +
            "  {" + NEW_LINE +
            "      \"path\" : 500," + NEW_LINE +
            "      \"method\" : true," + NEW_LINE +
            "      \"keepAlive\" : \"false\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "" + NEW_LINE +
            " schema validation errors:" + NEW_LINE +
            "" + NEW_LINE +
            "  10 errors:" + NEW_LINE +
            "   - $.anyOf: is missing but it is required" + NEW_LINE +
            "   - $.binaryData: is missing but it is required" + NEW_LINE +
            "   - $.dnsName: is missing but it is required" + NEW_LINE +
            "   - $.if: is missing but it is required" + NEW_LINE +
            "   - $.keepAlive: string found, boolean expected" + NEW_LINE +
            "   - $.method: boolean found, string expected" + NEW_LINE +
            "   - $.method: should be valid to one and only one schema, but 0 are valid" + NEW_LINE +
            "   - $.path: integer found, string expected" + NEW_LINE +
            "   - $.path: should be valid to one and only one schema, but 0 are valid" + NEW_LINE +
            "   - $.specUrlOrPayload: is missing, but is required, if specifying OpenAPI request matcher" + NEW_LINE +
            "  " + NEW_LINE +
            "  " + OPEN_API_SPECIFICATION_URL.replaceAll(NEW_LINE, NEW_LINE + "  ")));
    }

    // ========================================================================
    // TTL expiry (from AbstractExtendedMockingIntegrationTest)
    // ========================================================================

    @Test
    public void shouldRemoveExpiredTimeToLiveFromActiveExpectations() throws InterruptedException {
        // when
        mockServerClient
            .when(
                request().withPath(calculatePath("some_path")),
                unlimited(),
                TimeToLive.exactly(SECONDS, 2L)
            )
            .respond(
                response().withBody("some_body")
            );

        // then - expectation should be active before expiry
        assertThat(
            mockServerClient.retrieveActiveExpectations(null).length,
            equalTo(1)
        );

        // when - wait for TTL to expire
        MILLISECONDS.sleep(2500);

        // then - expired expectation should be removed from active list without requiring a request
        assertThat(
            mockServerClient.retrieveActiveExpectations(null).length,
            equalTo(0)
        );
    }

    @Test
    public void shouldRemoveExhaustedTimesFromActiveExpectations() {
        // when
        mockServerClient
            .when(
                request().withPath(calculatePath("some_path")),
                exactly(1)
            )
            .respond(
                response().withBody("some_body")
            );

        // then - expectation should be active before it is matched
        assertThat(
            mockServerClient.retrieveActiveExpectations(null).length,
            equalTo(1)
        );

        // when - the single matching request exhausts the Times
        makeRequest(
            request()
                .withPath(calculatePath("some_path")),
            getHeadersToRemove()
        );

        // then - exhausted expectation should be removed from active list without requiring a second request
        assertThat(
            mockServerClient.retrieveActiveExpectations(null).length,
            equalTo(0)
        );
    }

    @Test
    public void shouldRetrieveByExpectationId() {
        // given - two expectations that both match the same request, so retrieving by the
        // request matcher would return both while retrieving by id must return only one
        mockServerClient
            .upsert(
                new Expectation(request().withPath(calculatePath("retrieve_by_id")))
                    .withId("retrieve_by_id_one")
                    .thenRespond(response().withBody("one")),
                new Expectation(request().withPath(calculatePath("retrieve_by_id")))
                    .withId("retrieve_by_id_two")
                    .thenRespond(response().withBody("two")),
                new Expectation(request().withPath(calculatePath("retrieve_by_id_other")))
                    .withId("retrieve_by_id_other")
                    .thenRespond(response().withBody("other"))
            );

        makeRequest(request().withPath(calculatePath("retrieve_by_id")), getHeadersToRemove());
        makeRequest(request().withPath(calculatePath("retrieve_by_id_other")), getHeadersToRemove());

        // then - only the expectation with that id
        Expectation[] byId = mockServerClient.retrieveActiveExpectationsById("retrieve_by_id_one");
        assertThat(byId.length, equalTo(1));
        assertThat(byId[0].getId(), equalTo("retrieve_by_id_one"));

        // and - the request matcher matches both expectations
        assertThat(
            mockServerClient.retrieveActiveExpectations(request().withPath(calculatePath("retrieve_by_id"))).length,
            equalTo(2)
        );

        // and - recorded requests are filtered by the request that expectation matches
        HttpRequest[] recordedRequests = mockServerClient.retrieveRecordedRequestsById("retrieve_by_id_one");
        assertThat(recordedRequests.length, equalTo(1));
        assertThat(recordedRequests[0].getPath().getValue(), equalTo(calculatePath("retrieve_by_id")));

        assertThat(
            mockServerClient.retrieveRecordedRequestsAndResponsesById("retrieve_by_id_one").length,
            equalTo(1)
        );

        // and - log messages are filtered the same way
        assertThat(
            mockServerClient.retrieveLogMessagesById("retrieve_by_id_one"),
            containsString(calculatePath("retrieve_by_id"))
        );
    }

    @Test
    public void shouldRejectRetrieveByUnknownExpectationId() {
        // when
        try {
            mockServerClient.retrieveActiveExpectationsById("does_not_exist");
            fail("expected an exception for an unknown expectation id");
        } catch (Throwable throwable) {
            // then
            assertThat(throwable.getMessage(), containsString("No expectation found with id does_not_exist"));
        }
    }

    // ========================================================================
    // Additional verify semantics (from AbstractExtendedMockingIntegrationTest)
    // ========================================================================

    @Test
    public void shouldVerifyReceivedRequestsWithNoBody() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response());

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );
        mockServerClient.verify(request()
            .withPath(calculatePath("some_path")));
        mockServerClient.verify(request()
            .withPath(calculatePath("some_path")), VerificationTimes.exactly(1));
    }

    @Test
    public void shouldVerifyReceivedRequestsWithNoMatchingExpectation() {
        // when
        makeRequest(
            request()
                .withPath(calculatePath("some_path")),
            getHeadersToRemove()
        );

        mockServerClient.verify(request()
            .withPath(calculatePath("some_path")));
        mockServerClient.verify(request()
            .withPath(calculatePath("some_path")), VerificationTimes.exactly(1));
        mockServerClient.verify(request()
            .withPath(calculatePath("some_path")), VerificationTimes.once());
    }

    @Test
    public void shouldVerifyTooManyRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );
        try {
            mockServerClient.verify(request()
                .withPath(calculatePath("some_path")), VerificationTimes.exactly(0));
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request found 1 time but should have been found exactly 0 times, expected:<{" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path") + "\"" + NEW_LINE +
                "}> but was:<{"));
        }
    }

    @Test
    public void shouldVerifyNoMatchingRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );
        try {
            mockServerClient.verify(request()
                .withPath(calculatePath("some_other_path")), VerificationTimes.exactly(2));
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found exactly 2 times, expected:<{" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_other_path") + "\"" + NEW_LINE +
                "}> but was:<{"));
        }
    }

    @Test
    public void shouldNotVerifyNoRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );
        try {
            mockServerClient.verifyZeroInteractions();
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request found 1 time but should have been found exactly 0 times, expected:<{ }> but was:<{"));
        }
    }

    @Test
    public void shouldVerifySequenceOfRequestsNotReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(6)).respond(response().withBody("some_body"));

        // then
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_one")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_two")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response("some_body"),
            makeRequest(
                request().withPath(calculatePath("some_path_three")),
                getHeadersToRemove()
            )
        );
        try {
            mockServerClient.verify(request(calculatePath("some_path_two")), request(calculatePath("some_path_one")));
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path_two") + "\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path_one") + "\"" + NEW_LINE +
                "} ]> but was:<[ {"));
        }
        try {
            mockServerClient.verify(request(calculatePath("some_path_three")), request(calculatePath("some_path_two")));
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path_three") + "\"" + NEW_LINE +
                "}, {" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path_two") + "\"" + NEW_LINE +
                "} ]> but was:<[ {"));
        }
        try {
            mockServerClient.verify(request(calculatePath("some_path_four")));
            fail("expected exception to be thrown");
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + NEW_LINE +
                "  \"path\" : \"" + calculatePath("some_path_four") + "\"" + NEW_LINE +
                "} ]> but was:<[ {"));
        }
    }

    // ========================================================================
    // Clear semantics (from AbstractExtendedMockingIntegrationTest)
    // ========================================================================

    @Test
    public void shouldClearExpectationsOnly() {
        // given - some expectations
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // and - some matching requests
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                getHeadersToRemove()
            )
        );

        // when
        mockServerClient
            .clear(
                request()
                    .withPath(calculatePath("some_path1")),
                ClearType.EXPECTATIONS
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

        // and then - request log not cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path1")),
            request(calculatePath("some_path2"))
        );
    }

    @Test
    public void shouldClearExpectationsOnlyByExpectationId() {
        // given - some expectations
        Expectation firstExpectation = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            )[0];
        Expectation secondExpectation = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            )[0];

        // and - some matching requests
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                getHeadersToRemove()
            )
        );

        // when - wrong id
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> mockServerClient.clear(
            UUID.randomUUID().toString(), ClearType.EXPECTATIONS));
        assertThat(illegalArgumentException.getMessage(), startsWith("No expectation found with id "));

        // then - expectations not cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                firstExpectation,
                secondExpectation
            )
        );

        // and then - request log not cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path1")),
            request(calculatePath("some_path2"))
        );

        // when
        mockServerClient
            .clear(
                firstExpectation.getId(),
                ClearType.EXPECTATIONS
            );

        // then - expectations cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                secondExpectation
            )
        );

        // and then - request log not cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path1")),
            request(calculatePath("some_path2"))
        );

        // when
        mockServerClient
            .clear(
                secondExpectation.getId(),
                ClearType.EXPECTATIONS
            );

        // then - expectations cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            emptyArray()
        );

        // and then - request log not cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path1")),
            request(calculatePath("some_path2"))
        );
    }

    @Test
    public void shouldClearExpectationsWithIdenticalRequestMatchersByExpectationId() {
        // given - some expectations
        Expectation firstExpectation = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            )[0];
        Expectation secondExpectation = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            )[0];

        // and - some matching requests
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path")),
                getHeadersToRemove()
            )
        );

        // when - wrong id
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> mockServerClient.clear(UUID.randomUUID().toString(), ClearType.EXPECTATIONS));
        assertThat(illegalArgumentException.getMessage(), startsWith("No expectation found with id "));

        // then - expectations not cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                firstExpectation,
                secondExpectation
            )
        );

        // and then - request log not cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path")),
            request(calculatePath("some_path"))
        );

        // when
        mockServerClient
            .clear(
                firstExpectation.getId(),
                ClearType.EXPECTATIONS
            );

        // then - expectations cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                secondExpectation
            )
        );

        // and then - request log not cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path")),
            request(calculatePath("some_path"))
        );

        // when
        mockServerClient
            .clear(
                secondExpectation.getId(),
                ClearType.EXPECTATIONS
            );

        // then - expectations cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            emptyArray()
        );

        // and then - request log not cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path")),
            request(calculatePath("some_path"))
        );
    }

    @Test
    public void shouldClearLogsOnly() {
        // given - some expectations
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );

        // and - some matching requests
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                getHeadersToRemove()
            )
        );

        // when
        mockServerClient
            .clear(
                request()
                    .withPath(calculatePath("some_path1")),
                ClearType.LOG
            );

        // then - expectations not cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(request()
                    .withPath(calculatePath("some_path1")))
                    .thenRespond(
                        response()
                            .withBody("some_body1")
                    ),
                new Expectation(request()
                    .withPath(calculatePath("some_path2")))
                    .thenRespond(
                        response()
                            .withBody("some_body2")
                    )
            )
        );

        // and then - request log partially cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path2"))
        );
    }

    @Test
    public void shouldClearLogsOnlyByExpectationId() {
        // given - some expectations
        Expectation firstExpectation = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            )[0];
        Expectation secondExpectation = mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            )[0];

        // and - some matching requests
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path1")),
                getHeadersToRemove()
            )
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                getHeadersToRemove()
            )
        );

        // when
        mockServerClient
            .clear(
                firstExpectation.getId(),
                ClearType.LOG
            );

        // then - expectations not cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(request()
                    .withPath(calculatePath("some_path1")))
                    .thenRespond(
                        response()
                            .withBody("some_body1")
                    ),
                new Expectation(request()
                    .withPath(calculatePath("some_path2")))
                    .thenRespond(
                        response()
                            .withBody("some_body2")
                    )
            )
        );

        // and then - request log partially cleared
        verifyRequestsMatches(
            mockServerClient.retrieveRecordedRequests(null),
            request(calculatePath("some_path2"))
        );

        // when
        mockServerClient
            .clear(
                secondExpectation.getId(),
                ClearType.LOG
            );

        // then - expectations not cleared
        assertThat(
            mockServerClient.retrieveActiveExpectations(null),
            arrayContaining(
                new Expectation(request()
                    .withPath(calculatePath("some_path1")))
                    .thenRespond(
                        response()
                            .withBody("some_body1")
                    ),
                new Expectation(request()
                    .withPath(calculatePath("some_path2")))
                    .thenRespond(
                        response()
                            .withBody("some_body2")
                    )
            )
        );

        // and then - request log cleared
        verifyRequestsMatches(mockServerClient.retrieveRecordedRequests(null));
    }

    @Test
    public void shouldClearAllExpectationsWithNull() {
        // given
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                getHeadersToRemove()
            )
        );

        // when
        mockServerClient.clear((RequestDefinition) null);

        // then
        assertThat(mockServerClient.retrieveActiveExpectations(null), emptyArray());
        assertThat(mockServerClient.retrieveRecordedRequests(null), emptyArray());
    }

    @Test
    public void shouldClearAllExpectationsWithEmptyRequest() {
        // given
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path1"))
            )
            .respond(
                response()
                    .withBody("some_body1")
            );
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("some_path2"))
            )
            .respond(
                response()
                    .withBody("some_body2")
            );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body2"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path2")),
                getHeadersToRemove()
            )
        );

        // when
        mockServerClient.clear(request());

        // then
        assertThat(mockServerClient.retrieveActiveExpectations(null), emptyArray());
        assertThat(mockServerClient.retrieveRecordedRequests(null), emptyArray());
    }

}
