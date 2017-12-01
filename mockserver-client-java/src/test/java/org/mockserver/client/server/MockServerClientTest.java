package org.mockserver.client.server;

import com.google.common.base.Charsets;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.netty.SocketConnectionException;
import org.mockserver.client.netty.websocket.WebSocketClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.model.*;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Charsets.UTF_8;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
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
    public void setupTestFixture() throws Exception {
        mockServerClient = new MockServerClient("localhost", 1080);

        initMocks(this);
    }

    @Test
    public void shouldHandleNullHostnameExceptions() {
        // given
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Host can not be null or empty"));

        // when
        new MockServerClient(null, 1080);
    }

    @Test
    public void shouldHandleNullContextPathExceptions() {
        // given
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("ContextPath can not be null"));

        // when
        new MockServerClient("localhost", 1080, null);
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
        assertTrue(expectation.matches(httpRequest));
        assertSame(httpResponse, expectation.getHttpResponse());
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
        assertTrue(expectation.matches(httpRequest));
        assertSame(httpForward, expectation.getHttpForward());
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
        assertTrue(expectation.matches(httpRequest));
        assertSame(httpError, expectation.getHttpError());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithClassCallback() {
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
        forwardChainExpectation.callback(httpClassCallback);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.matches(httpRequest));
        assertSame(httpClassCallback, expectation.getHttpClassCallback());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithObjectCallback() {
        // given
        HttpRequest httpRequest =
                new HttpRequest()
                        .withPath("/some_path")
                        .withBody(new StringBody("some_request_body"));
        ExpectationCallback expectationCallback = new ExpectationCallback() {
            @Override
            public HttpResponse handle(HttpRequest httpRequest) {
                return response();
            }
        };

        // and
        WebSocketClient webSocketClient = mock(WebSocketClient.class);
        when(webSocketClient.registerExpectationCallback(expectationCallback)).thenReturn(webSocketClient);
        when(webSocketClient.clientId()).thenReturn("some_client_id");

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);

        // and given
        forwardChainExpectation.setWebSocketClient(webSocketClient);

        // and when
        forwardChainExpectation.callback(expectationCallback);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.matches(httpRequest));
        assertThat(expectation.getHttpClassCallback(), nullValue());
        assertThat(expectation.getHttpObjectCallback(), is(new HttpObjectCallback().withClientId("some_client_id")));
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSendExpectationRequestWithExactTimes() throws Exception {
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
                        .setTimes(new TimesDTO(Times.exactly(3)))
                        .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithForward() throws Exception {
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
                        .setTimes(new TimesDTO(Times.exactly(3)))
                        .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithError() throws Exception {
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
                        .setTimes(new TimesDTO(Times.exactly(3)))
                        .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithClassCallback() throws Exception {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body")),
                        Times.exactly(3)
                )
                .callback(
                        new HttpClassCallback()
                                .withCallbackClass("some_class")
                );

        // then
        verify(mockExpectationSerializer).serialize(
                new ExpectationDTO()
                        .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body"))))
                        .setHttpClassCallback(
                                new HttpClassCallbackDTO(
                                        new HttpClassCallback()
                                                .withCallbackClass("some_class")
                                )
                        )
                        .setTimes(new TimesDTO(Times.exactly(3)))
                        .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationWithCallback() throws Exception {
        // given
        ExpectationCallback expectationCallback = new ExpectationCallback() {
            @Override
            public HttpResponse handle(HttpRequest httpRequest) {
                return response();
            }
        };

        // and
        WebSocketClient webSocketClient = mock(WebSocketClient.class);
        when(webSocketClient.registerExpectationCallback(expectationCallback)).thenReturn(webSocketClient);
        when(webSocketClient.clientId()).thenReturn("some_client_id");

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient
                .when(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body")),
                        Times.exactly(3)
                );

        // and given
        forwardChainExpectation.setWebSocketClient(webSocketClient);

        // and when
        forwardChainExpectation.callback(expectationCallback);

        // then
        verify(mockExpectationSerializer).serialize(
                new ExpectationDTO()
                        .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body"))))
                        .setHttpObjectCallback(
                                new HttpObjectCallbackDTO(
                                        new HttpObjectCallback()
                                                .withClientId("some_client_id")
                                )
                        )
                        .setTimes(new TimesDTO(Times.exactly(3)))
                        .buildObject()
        );
    }

    @Test
    public void shouldSendExpectationRequestWithDefaultTimes() throws Exception {
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
                        .setTimes(new TimesDTO(Times.unlimited()))
                        .buildObject()
        );
    }

    @Test
    public void shouldSendDumpToLogRequest() throws Exception {
        // when
        mockServerClient.dumpToLog();

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/dumpToLog").withBody("", Charsets.UTF_8));
    }

    @Test
    public void shouldSendStopRequest() throws Exception {
        // when
        mockServerClient.stop();

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/stop"));
    }

    @Test
    public void shouldBeCloseable() throws Exception {
        // when
        mockServerClient.close();

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/stop"));
    }

    @Test
    public void shouldQueryRunningStatus() throws Exception {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withStatusCode(HttpStatusCode.OK_200.code()));

        // when
        boolean running = mockServerClient.isRunning();

        // then
        assertTrue(running);
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldQueryRunningStatusWhenSocketConnectionException() throws Exception {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenThrow(SocketConnectionException.class);

        // when
        boolean running = mockServerClient.isRunning();

        // then
        assertFalse(running);
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/status"));
    }

    @Test
    public void shouldSendResetRequest() throws Exception {
        // when
        mockServerClient.reset();

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/reset"));
    }

    @Test
    public void shouldSendClearRequest() throws Exception {
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
                        .withPath("/clear")
                        .withBody(someRequestMatcher.toString(), Charsets.UTF_8)
        );
    }

    @Test
    public void shouldSendClearRequestWithType() throws Exception {
        // given
        HttpRequest someRequestMatcher = new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        when(mockHttpRequestSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // when
        mockServerClient.clear(someRequestMatcher, MockServerClient.TYPE.LOG);

        // then
        verify(mockHttpClient).sendRequest(
                request()
                        .withHeader(HOST.toString(), "localhost:" + 1080)
                        .withMethod("PUT")
                        .withPath("/clear")
                        .withQueryStringParameter("type", "log")
                        .withBody(someRequestMatcher.toString(), Charsets.UTF_8)
        );
    }

    @Test
    public void shouldSendClearRequestForNullRequest() throws Exception {
        // when
        mockServerClient
                .clear(null);

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/clear").withBody("", Charsets.UTF_8));
    }

    @Test
    public void shouldRetrieveRequests() throws UnsupportedEncodingException {
        // given - a request
        HttpRequest someRequestMatcher = new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        when(mockHttpRequestSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // and - a client
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withBody("body"));

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
                        .withPath("/retrieve")
                        .withBody(someRequestMatcher.toString(), Charsets.UTF_8));
        verify(mockHttpRequestSerializer).deserializeArray("body");
    }

    @Test
    public void shouldRetrieveRequestsWithNullRequest() throws UnsupportedEncodingException {
        // given
        HttpRequest[] httpRequests = {};
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withBody("body"));
        when(mockHttpRequestSerializer.deserializeArray("body")).thenReturn(httpRequests);

        // when
        assertSame(httpRequests, mockServerClient.retrieveRecordedRequests(null));

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/retrieve").withBody("", Charsets.UTF_8));
        verify(mockHttpRequestSerializer).deserializeArray("body");
    }

    @Test
    public void shouldRetrieveSetupExpectations() throws UnsupportedEncodingException {
        // given - a request
        HttpRequest someRequestMatcher = new HttpRequest()
                .withPath("/some_path")
                .withBody(new StringBody("some_request_body"));
        when(mockHttpRequestSerializer.serialize(someRequestMatcher)).thenReturn(someRequestMatcher.toString());

        // and - a client
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withBody("body"));

        // and - an expectation
        Expectation[] expectations = {};
        when(mockExpectationSerializer.deserializeArray("body")).thenReturn(expectations);

        // when
        assertSame(expectations, mockServerClient.retrieveExistingExpectations(someRequestMatcher));

        // then
        verify(mockHttpClient).sendRequest(
                request()
                        .withHeader(HOST.toString(), "localhost:1080")
                        .withMethod("PUT")
                        .withPath("/retrieve")
                        .withQueryStringParameter("type", "expectation")
                        .withBody(someRequestMatcher.toString(), Charsets.UTF_8)
        );
        verify(mockExpectationSerializer).deserializeArray("body");
    }

    @Test
    public void shouldRetrieveSetupExpectationsWithNullRequest() throws UnsupportedEncodingException {
        // given
        Expectation[] expectations = {};
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withBody("body"));
        when(mockExpectationSerializer.deserializeArray("body")).thenReturn(expectations);

        // when
        assertSame(expectations, mockServerClient.retrieveExistingExpectations(null));

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/retrieve").withQueryStringParameter("type", "expectation").withBody("", Charsets.UTF_8));
        verify(mockExpectationSerializer).deserializeArray("body");
    }

    @Test
    public void shouldVerifyDoesNotMatchSingleRequestNoVerificationTimes() throws UnsupportedEncodingException {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withBody("Request not found at least once expected:<foo> but was:<bar>"));
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
            verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/verifySequence").withBody("verification_json", Charsets.UTF_8));
            assertThat(ae.getMessage(), is("Request not found at least once expected:<foo> but was:<bar>"));
        }
    }

    @Test
    public void shouldVerifyDoesNotMatchMultipleRequestsNoVerificationTimes() throws UnsupportedEncodingException {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withBody("Request not found at least once expected:<foo> but was:<bar>"));
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
            verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/verifySequence").withBody("verification_json", Charsets.UTF_8));
            assertThat(ae.getMessage(), is("Request not found at least once expected:<foo> but was:<bar>"));
        }
    }

    @Test
    public void shouldVerifyDoesMatchSingleRequestNoVerificationTimes() throws UnsupportedEncodingException {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withBody(""));
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
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/verifySequence").withBody("verification_json", Charsets.UTF_8));
    }

    @Test
    public void shouldVerifyDoesMatchSingleRequestOnce() throws UnsupportedEncodingException {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withBody(""));
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
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/verify").withBody("verification_json", Charsets.UTF_8));
    }

    @Test
    public void shouldVerifyDoesNotMatchSingleRequest() throws UnsupportedEncodingException {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withBody("Request not found at least once expected:<foo> but was:<bar>"));
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
            verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1080).withMethod("PUT").withPath("/verify").withBody("verification_json", Charsets.UTF_8));
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
}
