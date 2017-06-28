package org.mockserver.client.proxy;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.netty.SocketConnectionException;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.StringBody;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;

import java.io.UnsupportedEncodingException;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.once;

/**
 * @author jamesdbloom
 */
public class ProxyClientTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Mock
    private NettyHttpClient mockHttpClient;
    @Mock
    private ExpectationSerializer mockExpectationSerializer;
    @Mock
    private VerificationSerializer mockVerificationSerializer;
    @Mock
    private VerificationSequenceSerializer mockVerificationSequenceSerializer;
    @InjectMocks
    private ProxyClient proxyClient;

    @Before
    public void setupTestFixture() throws Exception {
        proxyClient = new ProxyClient("localhost", 1090);

        initMocks(this);
    }

    @Test
    public void shouldHandleNullHostnameExceptions() {
        // given
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Host can not be null or empty"));

        // when
        new ProxyClient(null, 1090);
    }

    @Test
    public void shouldHandleNullContextPathExceptions() {
        // given
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("ContextPath can not be null"));

        // when
        new ProxyClient("localhost", 1090, null);
    }

    @Test
    public void shouldSendDumpToLogAsJSONRequest() throws Exception {
        // when
        proxyClient.dumpToLogAsJSON();

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/dumpToLog").withBody(""));
    }

    @Test
    public void shouldQueryRunningStatus() throws Exception {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withStatusCode(HttpStatusCode.OK_200.code()));

        // when
        boolean running = proxyClient.isRunning();

        // then
        assertTrue(running);
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/status"));
    }

    @Test
    public void shouldQueryRunningStatusWhenSocketConnectionException() throws Exception {
        // given
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenThrow(SocketConnectionException.class);

        // when
        boolean running = proxyClient.isRunning();

        // then
        assertFalse(running);
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/status"));
    }

    @Test
    public void shouldSendStopRequest() throws Exception {
        // when
        proxyClient.stop();

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/stop"));
    }

    @Test
    public void shouldBeCloseabl() throws Exception {
        // when
        proxyClient.close();

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/stop"));
    }

    @Test
    public void shouldSendDumpToLogAsJavaRequest() throws Exception {
        // when
        proxyClient.dumpToLogAsJava();

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/dumpToLog?type=java").withBody(""));
    }

    @Test
    public void shouldSendResetRequest() throws Exception {
        // when
        proxyClient.reset();

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/reset"));
    }

    @Test
    public void shouldSendClearRequest() throws Exception {
        // when
        proxyClient
                .clear(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body"))
                );

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/clear").withBody("" +
                "{" + NEW_LINE +
                "  \"path\" : \"/some_path\"," + NEW_LINE +
                "  \"body\" : \"some_request_body\"" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldSendClearRequestForNullRequest() throws Exception {
        // when
        proxyClient
                .clear(null);

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/clear").withBody(""));
    }

    @Test
    public void shouldReceiveExpectationsAsObjects() throws UnsupportedEncodingException {
        // given
        Expectation[] expectations = {};
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withBody("body"));
        when(mockExpectationSerializer.deserializeArray("body")).thenReturn(expectations);

        // when
        assertSame(expectations, proxyClient
                .retrieveAsExpectations(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body"))
                ));

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/retrieve").withBody("" +
                "{" + NEW_LINE +
                "  \"path\" : \"/some_path\"," + NEW_LINE +
                "  \"body\" : \"some_request_body\"" + NEW_LINE +
                "}"));
        verify(mockExpectationSerializer).deserializeArray("body");
    }

    @Test
    public void shouldReceiveExpectationsAsObjectsWithNullRequest() throws UnsupportedEncodingException {
        // given
        Expectation[] expectations = {};
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withBody("body"));
        when(mockExpectationSerializer.deserializeArray("body")).thenReturn(expectations);

        // when
        assertSame(expectations, proxyClient.retrieveAsExpectations(null));

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/retrieve").withBody(""));
        verify(mockExpectationSerializer).deserializeArray("body");
    }

    @Test
    public void shouldReceiveExpectationsAsJSON() throws UnsupportedEncodingException {
        // given
        String expectations = "body";
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withBody("body"));

        // when
        assertEquals(expectations, proxyClient
                .retrieveAsJSON(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body"))
                ));

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/retrieve").withBody("" +
                "{" + NEW_LINE +
                "  \"path\" : \"/some_path\"," + NEW_LINE +
                "  \"body\" : \"some_request_body\"" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldReceiveExpectationsAsJSONWithNullRequest() throws UnsupportedEncodingException {
        // given
        String expectations = "body";
        when(mockHttpClient.sendRequest(any(HttpRequest.class))).thenReturn(response().withBody("body"));

        // when
        assertEquals(expectations, proxyClient.retrieveAsJSON(null));

        // then
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/retrieve").withBody(""));
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
            proxyClient.verify(httpRequest);

            // then
            fail();
        } catch (AssertionError ae) {
            verify(mockVerificationSequenceSerializer).serialize(new VerificationSequence().withRequests(httpRequest));
            verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/verifySequence").withBody("verification_json"));
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
            proxyClient.verify(httpRequest, httpRequest);

            // then
            fail();
        } catch (AssertionError ae) {
            verify(mockVerificationSequenceSerializer).serialize(new VerificationSequence().withRequests(httpRequest, httpRequest));
            verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/verifySequence").withBody("verification_json"));
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
            proxyClient.verify(httpRequest);

            // then
        } catch (AssertionError ae) {
            fail();
        }

        // then
        verify(mockVerificationSequenceSerializer).serialize(new VerificationSequence().withRequests(httpRequest));
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/verifySequence").withBody("verification_json"));
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
            proxyClient.verify(httpRequest, once());

            // then
        } catch (AssertionError ae) {
            fail();
        }

        // then
        verify(mockVerificationSerializer).serialize(verification().withRequest(httpRequest).withTimes(once()));
        verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/verify").withBody("verification_json"));
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
            proxyClient.verify(httpRequest, atLeast(1));

            // then
            fail();
        } catch (AssertionError ae) {
            verify(mockVerificationSerializer).serialize(verification().withRequest(httpRequest).withTimes(atLeast(1)));
            verify(mockHttpClient).sendRequest(request().withHeader(HOST.toString(), "localhost:" + 1090).withMethod("PUT").withPath("/verify").withBody("verification_json"));
            assertThat(ae.getMessage(), is("Request not found at least once expected:<foo> but was:<bar>"));
        }
    }

    @Test
    public void shouldHandleNullHttpRequest() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("verify(HttpRequest, VerificationTimes) requires a non null HttpRequest object"));

        // when
        proxyClient.verify(null, VerificationTimes.exactly(2));
    }

    @Test
    public void shouldHandleNullVerificationTimes() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("verify(HttpRequest, VerificationTimes) requires a non null VerificationTimes object"));

        // when
        proxyClient.verify(request(), null);
    }

    @Test
    public void shouldHandleNullHttpRequestSequence() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("verify(HttpRequest...) requires a non null non empty array of HttpRequest objects"));

        // when
        proxyClient.verify((HttpRequest)null);
    }

    @Test
    public void shouldHandleEmptyHttpRequestSequence() {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("verify(HttpRequest...) requires a non null non empty array of HttpRequest objects"));

        // when
        proxyClient.verify();
    }
}
