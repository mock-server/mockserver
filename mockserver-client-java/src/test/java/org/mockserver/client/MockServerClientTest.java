package org.mockserver.client;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.Version;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.netty.SocketConnectionException;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.HttpRequestSerializer;
import org.mockserver.serialization.VerificationSequenceSerializer;
import org.mockserver.serialization.VerificationSerializer;
import org.mockserver.serialization.model.*;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.once;

/**
 * @author jamesdbloom
 */
public class MockServerClientTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Mock
    private NettyHttpClient mockHttpClient;
    @Mock
    private ExpectationSerializer mockExpectationSerializer;
    @Mock
    private HttpRequestSerializer mockHttpRequestSerializer;
    @Mock
    private VerificationSerializer mockVerificationSerializer;
    @Mock
    private VerificationSequenceSerializer mockVerificationSequenceSerializer;
    @InjectMocks
    private MockServerClient mockServerClient;

    @Before
    public void setupTestFixture() {
        mockServerClient = new MockServerClient("localhost", 1080);

        initMocks(this);
    }

    @Test
    public void shouldHandleNullHostnameExceptions() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Host can not be null or empty"));

        // when
        new MockServerClient(null, 1080);
    }

    @Test
    public void shouldHandleNullContextPathExceptions() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("ContextPath can not be null"));

        // when
        new MockServerClient("localhost", 1080, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldHandleInvalidExpectationException() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("error_body"));

        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class)))
            .thenReturn(response()
                .withStatusCode(BAD_REQUEST.code())
                .withBody("error_body")
            );

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(request());
        forwardChainExpectation.respond(response());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldHandleUnmatchingServerVersion() {
        try {
            System.setProperty("MOCKSERVER_VERSION", "some_version");

            // then
            exception.expect(ClientException.class);
            exception.expectMessage(containsString("Client version \"" + Version.getVersion() + "\" does not match server version \"another_non_matching_version\""));

            // given
            when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(response()
                    .withHeader("version", "another_non_matching_version")
                    .withStatusCode(CREATED.code())
                );

            // when
            ForwardChainExpectation forwardChainExpectation = mockServerClient.when(request());
            forwardChainExpectation.respond(response());
        } finally {
            System.clearProperty("MOCKSERVER_VERSION");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldHandleMatchingServerVersion() {
        try {
            // given
            System.setProperty("MOCKSERVER_VERSION", "same_version");
            when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(response()
                    .withHeader("version", Version.getVersion())
                    .withStatusCode(CREATED.code())
                );

            // when
            ForwardChainExpectation forwardChainExpectation = mockServerClient.when(request());
            forwardChainExpectation.respond(response());
        } catch (Throwable t) {
            // then - no exception should be thrown
            fail();
        } finally {
            System.clearProperty("MOCKSERVER_VERSION");
        }


    }

    @Test
    public void shouldSetupExpectationWithResponse() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpResponse httpResponse =
            new HttpResponse()
                .withBody("some_response_body")
                .withHeaders(new Header("responseName", "responseValue"));

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.respond(httpResponse);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpResponse, expectation.getHttpResponse());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithResponseTemplate() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpTemplate template =
            new HttpTemplate(HttpTemplate.TemplateType.VELOCITY)
                .withTemplate("some_template");

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.respond(template);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(template, expectation.getHttpResponseTemplate());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithResponseClassCallback() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpClassCallback httpClassCallback =
            new HttpClassCallback()
                .withCallbackClass("some_class");

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.respond(httpClassCallback);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpClassCallback, expectation.getHttpResponseClassCallback());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithForward() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpForward httpForward =
            new HttpForward()
                .withHost("some_host")
                .withPort(9090)
                .withScheme(HttpForward.Scheme.HTTPS);

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.forward(httpForward);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpForward, expectation.getHttpForward());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithForwardTemplate() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpTemplate template =
            new HttpTemplate(HttpTemplate.TemplateType.VELOCITY)
                .withTemplate("some_template");

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.forward(template);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(template, expectation.getHttpForwardTemplate());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithForwardClassCallback() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpClassCallback httpClassCallback =
            new HttpClassCallback()
                .withCallbackClass("some_class");

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.forward(httpClassCallback);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpClassCallback, expectation.getHttpForwardClassCallback());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithOverrideForwardedRequest() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.forward(forwardOverriddenRequest(request().withBody("some_overridden_body")));

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertThat(expectation.getHttpForwardTemplate(), nullValue());
        assertThat(expectation.getHttpOverrideForwardedRequest(), is(new HttpOverrideForwardedRequest()
            .withHttpRequest(request().withBody("some_overridden_body"))
        ));
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithError() {
        // given
        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        HttpError httpError =
            new HttpError()
                .withDropConnection(true)
                .withResponseBytes("silly_bytes".getBytes(UTF_8));

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.error(httpError);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpError, expectation.getHttpError());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSendExpectationWithRequest() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .respond(
                new HttpResponse()
                    .withBody("some_response_body")
                    .withHeaders(new Header("responseName", "responseValue"))
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))))
                .setHttpResponse(new HttpResponseDTO(new HttpResponse()
                    .withBody("some_response_body")
                    .withHeaders(new Header("responseName", "responseValue"))))
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithRequestTemplate() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .respond(
                new HttpResponse()
                    .withBody("some_response_body")
                    .withHeaders(new Header("responseName", "responseValue"))
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))))
                .setHttpResponse(new HttpResponseDTO(new HttpResponse()
                    .withBody("some_response_body")
                    .withHeaders(new Header("responseName", "responseValue"))))
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithRequestClassCallback() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .respond(
                new HttpClassCallback()
                    .withCallbackClass("some_class")
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))))
                .setHttpResponseClassCallback(
                    new HttpClassCallbackDTO(
                        new HttpClassCallback()
                            .withCallbackClass("some_class")
                    )
                )
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithForward() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .forward(
                new HttpForward()
                    .withHost("some_host")
                    .withPort(9090)
                    .withScheme(HttpForward.Scheme.HTTPS)
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))))
                .setHttpForward(
                    new HttpForwardDTO(
                        new HttpForward()
                            .withHost("some_host")
                            .withPort(9090)
                            .withScheme(HttpForward.Scheme.HTTPS)
                    )
                )
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithForwardTemplate() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .forward(
                new HttpForward()
                    .withHost("some_host")
                    .withPort(9090)
                    .withScheme(HttpForward.Scheme.HTTPS)
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))))
                .setHttpForward(
                    new HttpForwardDTO(
                        new HttpForward()
                            .withHost("some_host")
                            .withPort(9090)
                            .withScheme(HttpForward.Scheme.HTTPS)
                    )
                )
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithForwardClassCallback() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .forward(
                new HttpClassCallback()
                    .withCallbackClass("some_class")
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))))
                .setHttpForwardClassCallback(
                    new HttpClassCallbackDTO(
                        new HttpClassCallback()
                            .withCallbackClass("some_class")
                    )
                )
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithOverrideForwardedRequest() {
        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            );
        forwardChainExpectation.forward(forwardOverriddenRequest(request().withBody("some_replaced_body")));

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))))
                .setHttpOverrideForwardedRequest(
                    new HttpOverrideForwardedRequestDTO(
                        new HttpOverrideForwardedRequest()
                            .withHttpRequest(
                                request()
                                    .withBody("some_replaced_body")
                            )
                    )
                )
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithError() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body")),
                Times.exactly(3)
            )
            .error(
                new HttpError()
                    .withDelay(TimeUnit.MILLISECONDS, 100)
                    .withResponseBytes("random_bytes".getBytes(UTF_8))
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))))
                .setHttpError(
                    new HttpErrorDTO(
                        new HttpError()
                            .withDelay(TimeUnit.MILLISECONDS, 100)
                            .withResponseBytes("random_bytes".getBytes(UTF_8))
                    )
                )
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.exactly(3)))
                .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationRequestWithDefaultTimes() {
        // when
        mockServerClient
            .when(
                new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))
            )
            .respond(
                new HttpResponse()
                    .withBody("some_response_body")
                    .withHeaders(new Header("responseName", "responseValue"))
            );

        // then
        verify(mockExpectationSerializer).serialize(
            new ExpectationDTO()
                .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                    .withPath("/some_path")
                    .withBody(new StringBody("some_request_body"))))
                .setHttpResponse(new HttpResponseDTO(new HttpResponse()
                    .withBody("some_response_body")
                    .withHeaders(new Header("responseName", "responseValue"))))
                .setTimes(new org.mockserver.serialization.model.TimesDTO(Times.unlimited()))
                .buildObject()
        );
    }

    @Test
    public void shouldSendStopRequest() {
        // when
        mockServerClient.stop();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/stop"),
            20000,
            TimeUnit.MILLISECONDS
        );
    }

    @Test
    public void shouldBeCloseable() {
        // when
        mockServerClient.close();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/stop"),
            20000,
            TimeUnit.MILLISECONDS
        );
    }

    @Test
    public void shouldQueryRunningStatus() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class))).thenReturn(response().withStatusCode(HttpStatusCode.OK_200.code()));

        // when
        boolean running = mockServerClient.isRunning();

        // then
        assertTrue(running);
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/status"),
            20000,
            TimeUnit.MILLISECONDS
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldQueryRunningStatusWhenSocketConnectionException() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class))).thenThrow(SocketConnectionException.class);

        // when
        boolean running = mockServerClient.isRunning();

        // then
        assertFalse(running);
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/status"),
            20000,
            TimeUnit.MILLISECONDS
        );
    }

    @Test
    public void shouldSendResetRequest() {
        // when
        mockServerClient.reset();

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/reset"),
            20000,
            TimeUnit.MILLISECONDS
        );
    }

    @Test
    public void shouldSendClearRequest() {
        // given
        HttpRequest someRequestMatcher = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));
        when(mockHttpRequestSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // when
        mockServerClient.clear(someRequestMatcher);

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/clear")
                .withBody(someRequestMatcher.toString(), StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS
        );
    }

    @Test
    public void shouldSendClearRequestWithType() {
        // given
        HttpRequest someRequestMatcher = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));
        when(mockHttpRequestSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // when
        mockServerClient.clear(someRequestMatcher, ClearType.LOG);

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/clear")
                .withQueryStringParameter("type", "log")
                .withBody(someRequestMatcher.toString(), StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS
        );
    }

    @Test
    public void shouldSendClearRequestForNullRequest() {
        // when
        mockServerClient
            .clear(null);

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/clear")
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS
        );
    }

    @Test
    public void shouldRetrieveRequests() {
        // given - a request
        HttpRequest someRequestMatcher = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));
        when(mockHttpRequestSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // and - a client
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class))).thenReturn(response().withBody("body"));

        // and - a response
        HttpRequest[] httpRequests = {};
        when(mockHttpRequestSerializer.deserializeArray("body")).thenReturn(httpRequests);

        // when
        assertSame(httpRequests, mockServerClient.retrieveRecordedRequests(someRequestMatcher));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody(someRequestMatcher.toString(), StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS);
        verify(mockHttpRequestSerializer).deserializeArray("body");
    }

    @Test
    public void shouldRetrieveRequestsWithNullRequest() {
        // given
        HttpRequest[] httpRequests = {};
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class))).thenReturn(response().withBody("body"));
        when(mockHttpRequestSerializer.deserializeArray("body")).thenReturn(httpRequests);

        // when
        assertSame(httpRequests, mockServerClient.retrieveRecordedRequests(null));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS
        );
        verify(mockHttpRequestSerializer).deserializeArray("body");
    }

    @Test
    public void shouldRetrieveActiveExpectations() {
        // given - a request
        HttpRequest someRequestMatcher = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));
        when(mockHttpRequestSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // and - a client
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class))).thenReturn(response().withBody("body"));

        // and - an expectation
        Expectation[] expectations = {};
        when(mockExpectationSerializer.deserializeArray("body")).thenReturn(expectations);

        // when
        assertSame(expectations, mockServerClient.retrieveActiveExpectations(someRequestMatcher));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody(someRequestMatcher.toString(), StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS
        );
        verify(mockExpectationSerializer).deserializeArray("body");
    }

    @Test
    public void shouldRetrieveActiveExpectationsWithNullRequest() {
        // given
        Expectation[] expectations = {};
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class))).thenReturn(response().withBody("body"));
        when(mockExpectationSerializer.deserializeArray("body")).thenReturn(expectations);

        // when
        assertSame(expectations, mockServerClient.retrieveActiveExpectations(null));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS
        );
        verify(mockExpectationSerializer).deserializeArray("body");
    }

    @Test
    public void shouldRetrieveRecordedExpectations() {
        // given - a request
        HttpRequest someRequestMatcher = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));
        when(mockHttpRequestSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // and - a client
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class))).thenReturn(response().withBody("body"));

        // and - an expectation
        Expectation[] expectations = {};
        when(mockExpectationSerializer.deserializeArray("body")).thenReturn(expectations);

        // when
        assertSame(expectations, mockServerClient.retrieveRecordedExpectations(someRequestMatcher));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody(someRequestMatcher.toString(), StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS
        );
        verify(mockExpectationSerializer).deserializeArray("body");
    }

    @Test
    public void shouldRetrieveExpectationsWithNullRequest() {
        // given
        Expectation[] expectations = {};
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class))).thenReturn(response().withBody("body"));
        when(mockExpectationSerializer.deserializeArray("body")).thenReturn(expectations);

        // when
        assertSame(expectations, mockServerClient.retrieveRecordedExpectations(null));

        // then
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", Format.JSON.name())
                .withBody("", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS
        );
        verify(mockExpectationSerializer).deserializeArray("body");
    }

    @Test
    public void shouldVerifyDoesNotMatchSingleRequestNoVerificationTimes() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class))).thenReturn(response().withBody("Request not found at least once expected:<foo> but was:<bar>"));
        when(mockVerificationSequenceSerializer.serialize(any(VerificationSequence.class))).thenReturn("verification_json");
        HttpRequest httpRequest = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));

        try {
            mockServerClient.verify(httpRequest);

            // then
            fail();
        } catch (AssertionError ae) {
            verify(mockVerificationSequenceSerializer).serialize(new VerificationSequence().withRequests(httpRequest));
            verify(mockHttpClient).sendRequest(
                request()
                    .withHeader(HOST.toString(), "localhost:" + 1080)
                    .withMethod("PUT")
                    .withPath("/mockserver/verifySequence")
                    .withBody("verification_json", StandardCharsets.UTF_8),
                20000,
                TimeUnit.MILLISECONDS
            );
            assertThat(ae.getMessage(), is("Request not found at least once expected:<foo> but was:<bar>"));
        }
    }

    @Test
    public void shouldVerifyDoesNotMatchMultipleRequestsNoVerificationTimes() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class))).thenReturn(response().withBody("Request not found at least once expected:<foo> but was:<bar>"));
        when(mockVerificationSequenceSerializer.serialize(any(VerificationSequence.class))).thenReturn("verification_json");
        HttpRequest httpRequest = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));

        try {
            mockServerClient.verify(httpRequest, httpRequest);

            // then
            fail();
        } catch (AssertionError ae) {
            verify(mockVerificationSequenceSerializer).serialize(new VerificationSequence().withRequests(httpRequest, httpRequest));
            verify(mockHttpClient).sendRequest(
                request()
                    .withHeader(HOST.toString(), "localhost:" + 1080)
                    .withMethod("PUT")
                    .withPath("/mockserver/verifySequence")
                    .withBody("verification_json", StandardCharsets.UTF_8),
                20000,
                TimeUnit.MILLISECONDS
            );
            assertThat(ae.getMessage(), is("Request not found at least once expected:<foo> but was:<bar>"));
        }
    }

    @Test
    public void shouldVerifyDoesMatchSingleRequestNoVerificationTimes() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class))).thenReturn(response().withBody(""));
        when(mockVerificationSequenceSerializer.serialize(any(VerificationSequence.class))).thenReturn("verification_json");
        HttpRequest httpRequest = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));

        try {
            mockServerClient.verify(httpRequest);

            // then
        } catch (AssertionError ae) {
            fail();
        }

        // then
        verify(mockVerificationSequenceSerializer).serialize(new VerificationSequence().withRequests(httpRequest));
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/verifySequence")
                .withBody("verification_json", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS
        );
    }

    @Test
    public void shouldVerifyDoesMatchSingleRequestOnce() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class))).thenReturn(response().withBody(""));
        when(mockVerificationSerializer.serialize(any(Verification.class))).thenReturn("verification_json");
        HttpRequest httpRequest = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));

        try {
            mockServerClient.verify(httpRequest, once());

            // then
        } catch (AssertionError ae) {
            fail();
        }

        // then
        verify(mockVerificationSerializer).serialize(verification().withRequest(httpRequest).withTimes(once()));
        verify(mockHttpClient).sendRequest(
            request()
                .withHeader(HOST.toString(), "localhost:" + 1080)
                .withMethod("PUT")
                .withPath("/mockserver/verify")
                .withBody("verification_json", StandardCharsets.UTF_8),
            20000,
            TimeUnit.MILLISECONDS
        );
    }

    @Test
    public void shouldVerifyDoesNotMatchSingleRequest() {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class), anyLong(), any(TimeUnit.class))).thenReturn(response().withBody("Request not found at least once expected:<foo> but was:<bar>"));
        when(mockVerificationSerializer.serialize(any(Verification.class))).thenReturn("verification_json");
        HttpRequest httpRequest = new HttpRequest()
            .withPath("/some_path")
            .withBody(new StringBody("some_request_body"));

        try {
            mockServerClient.verify(httpRequest, atLeast(1));

            // then
            fail();
        } catch (AssertionError ae) {
            verify(mockVerificationSerializer).serialize(verification().withRequest(httpRequest).withTimes(atLeast(1)));
            verify(mockHttpClient).sendRequest(
                request()
                    .withHeader(HOST.toString(), "localhost:" + 1080)
                    .withMethod("PUT")
                    .withPath("/mockserver/verify")
                    .withBody("verification_json", StandardCharsets.UTF_8),
                20000,
                TimeUnit.MILLISECONDS
            );
            assertThat(ae.getMessage(), is("Request not found at least once expected:<foo> but was:<bar>"));
        }
    }

    @Test
    public void shouldHandleNullHttpRequest() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("verify(HttpRequest, VerificationTimes) requires a non null HttpRequest object"));

        // when
        mockServerClient.verify(null, VerificationTimes.exactly(2));
    }

    @Test
    public void shouldHandleNullVerificationTimes() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("verify(HttpRequest, VerificationTimes) requires a non null VerificationTimes object"));

        // when
        mockServerClient.verify(request(), null);
    }

    @Test
    public void shouldHandleNullHttpRequestSequence() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("verify(HttpRequest...) requires a non null non empty array of HttpRequest objects"));

        // when
        mockServerClient.verify((HttpRequest) null);
    }

    @Test
    public void shouldHandleEmptyHttpRequestSequence() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("verify(HttpRequest...) requires a non null non empty array of HttpRequest objects"));

        // when
        mockServerClient.verify();
    }

    @Test
    public void shouldHandleExplicitUnsecuredConnectionsToMockServer() {
        // given
        mockServerClient.withSecure(false);

        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));

        HttpResponse httpResponse =
            new HttpResponse()
                .withBody("some_response_body")
                .withHeaders(new Header("responseName", "responseValue"));

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.respond(httpResponse);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpResponse, expectation.getHttpResponse());
        assertEquals(Times.unlimited(), expectation.getTimes());

        ArgumentCaptor<HttpRequest> configRequestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).sendRequest(configRequestCaptor.capture(), anyInt(), any(TimeUnit.class));
        assertFalse(configRequestCaptor.getValue().isSecure());
    }

    @Test
    public void shouldHandleExplicitSecuredConnectionsToMockServer() {
        // given
        mockServerClient.withSecure(true);

        HttpRequest httpRequest =
            new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));

        HttpResponse httpResponse =
            new HttpResponse()
                .withBody("some_response_body")
                .withHeaders(new Header("responseName", "responseValue"));

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.respond(httpResponse);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.isActive());
        assertSame(httpResponse, expectation.getHttpResponse());
        assertEquals(Times.unlimited(), expectation.getTimes());

        ArgumentCaptor<HttpRequest> configRequestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).sendRequest(configRequestCaptor.capture(), anyInt(), any(TimeUnit.class));
        assertTrue(configRequestCaptor.getValue().isSecure());
    }

}
