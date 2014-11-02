package org.mockserver.client.server;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import java.io.UnsupportedEncodingException;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerClientTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Mock
    private ApacheHttpClient mockApacheHttpClient;
    @Mock
    private ExpectationSerializer expectationSerializer;
    @InjectMocks
    private MockServerClient mockServerClient;

    @Before
    public void setupTestFixture() throws Exception {
        mockServerClient = new MockServerClient("localhost", 8080);

        initMocks(this);
    }

    @Test
    public void shouldHandleNullHostnameExceptions() {
        // given
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("Host can not be null or empty"));


        // when
        new MockServerClient(null, 8080);
    }

    @Test
    public void shouldHandleNullContextPathExceptions() {
        // given
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("ContextPath can not be null"));


        // when
        new MockServerClient("localhost", 8080, null);
    }

    @Test
    public void shouldSetupExpectationWithResponse() {
        // given
        HttpRequest httpRequest =
                new HttpRequest()
                        .withPath("/some_path")
                        .withBody(new StringBody("some_request_body", Body.Type.STRING));
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
        assertSame(httpResponse, expectation.getHttpResponse(false));
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSetupExpectationWithForward() {
        // given
        HttpRequest httpRequest =
                new HttpRequest()
                        .withPath("/some_path")
                        .withBody(new StringBody("some_request_body", Body.Type.STRING));
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
    public void shouldSetupExpectationWithCallback() {
        // given
        HttpRequest httpRequest =
                new HttpRequest()
                        .withPath("/some_path")
                        .withBody(new StringBody("some_request_body", Body.Type.STRING));
        HttpCallback httpCallback =
                new HttpCallback()
                        .withCallbackClass("some_class");

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.callback(httpCallback);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.matches(httpRequest));
        assertSame(httpCallback, expectation.getHttpCallback());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSendExpectationRequestWithExactTimes() throws Exception {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING)),
                        Times.exactly(3)
                )
                .respond(
                        new HttpResponse()
                                .withBody("some_response_body")
                                .withHeaders(new Header("responseName", "responseValue"))
                );

        // then
        verify(expectationSerializer).serialize(
                new ExpectationDTO()
                        .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING))))
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
                                .withBody(new StringBody("some_request_body", Body.Type.STRING)),
                        Times.exactly(3)
                )
                .forward(
                        new HttpForward()
                                .withHost("some_host")
                                .withPort(9090)
                                .withScheme(HttpForward.Scheme.HTTPS)
                );

        // then
        verify(expectationSerializer).serialize(
                new ExpectationDTO()
                        .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING))))
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
    public void shouldSendExpectationWithCallback() throws Exception {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING)),
                        Times.exactly(3)
                )
                .callback(
                        new HttpCallback()
                                .withCallbackClass("some_class")
                );

        // then
        verify(expectationSerializer).serialize(
                new ExpectationDTO()
                        .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING))))
                        .setHttpCallback(
                                new HttpCallbackDTO(
                                        new HttpCallback()
                                                .withCallbackClass("some_class")
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
                                .withBody(new StringBody("some_request_body", Body.Type.STRING))
                )
                .respond(
                        new HttpResponse()
                                .withBody("some_response_body")
                                .withHeaders(new Header("responseName", "responseValue"))
                );

        // then
        verify(expectationSerializer).serialize(
                new ExpectationDTO()
                        .setHttpRequest(new HttpRequestDTO(new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING))))
                        .setHttpResponse(new HttpResponseDTO(new HttpResponse()
                                .withBody("some_response_body")
                                .withHeaders(new Header("responseName", "responseValue"))))
                        .setTimes(new TimesDTO(Times.unlimited()))
                        .buildObject()
        );
    }

    @Test
    public void shouldSendResetRequest() throws Exception {
        // when
        mockServerClient.reset();

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/reset", "");
    }

    @Test
    public void shouldSendStopRequest() throws Exception {
        // when
        mockServerClient.stop();

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/stop", "");
    }

    @Test
    public void shouldSendDumpToLogRequest() throws Exception {
        // when
        mockServerClient.dumpToLog();

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/dumpToLog", "");
    }

    @Test
    public void shouldSendClearRequest() throws Exception {
        // when
        mockServerClient
                .clear(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING))
                );

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/clear", "" +
                "{" + System.getProperty("line.separator") +
                "  \"path\" : \"/some_path\"," + System.getProperty("line.separator") +
                "  \"body\" : \"some_request_body\"" + System.getProperty("line.separator") +
                "}");
    }

    @Test
    public void shouldSendClearRequestForNullRequest() throws Exception {
        // when
        mockServerClient
                .clear(null);

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/clear", "");
    }

    @Test
    public void shouldReceiveExpectationsAsObjects() throws UnsupportedEncodingException {
        // given
        Expectation[] expectations = {};
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray("body")).thenReturn(expectations);

        // when
        assertSame(expectations, mockServerClient
                .retrieveAsExpectations(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING))
                ));

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/retrieve", "" +
                "{" + System.getProperty("line.separator") +
                "  \"path\" : \"/some_path\"," + System.getProperty("line.separator") +
                "  \"body\" : \"some_request_body\"" + System.getProperty("line.separator") +
                "}");
        verify(expectationSerializer).deserializeArray("body");
    }

    @Test
    public void shouldReceiveExpectationsAsObjectsWithNullRequest() throws UnsupportedEncodingException {
        // given
        Expectation[] expectations = {};
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray("body")).thenReturn(expectations);

        // when
        assertSame(expectations, mockServerClient.retrieveAsExpectations(null));

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/retrieve", "");
        verify(expectationSerializer).deserializeArray("body");
    }

    @Test
    public void shouldReceiveExpectationsAsJSON() throws UnsupportedEncodingException {
        // given
        String expectations = "body";
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");

        // when
        assertEquals(expectations, mockServerClient
                .retrieveAsJSON(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING))
                ));

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/retrieve", "" +
                "{" + System.getProperty("line.separator") +
                "  \"path\" : \"/some_path\"," + System.getProperty("line.separator") +
                "  \"body\" : \"some_request_body\"" + System.getProperty("line.separator") +
                "}");
    }

    @Test
    public void shouldReceiveExpectationsAsJSONWithNullRequest() throws UnsupportedEncodingException {
        // given
        String expectations = "body";
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");

        // when
        assertEquals(expectations, mockServerClient.retrieveAsJSON(null));

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/retrieve", "");
    }

    @Test
    public void shouldVerifyMultipleRequest() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{
                new ExpectationDTO().buildObject(),
                new ExpectationDTO().buildObject()
        });

        // when
        mockServerClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING))
                );

        // no assertion exception thrown
    }

    @Test
    public void shouldVerify() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{
                new ExpectationDTO().buildObject(),
                new ExpectationDTO().buildObject()
        });

        // when
        mockServerClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path_one")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING)),
                        new HttpRequest()
                                .withPath("/some_path_two")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING)),
                        new HttpRequest()
                                .withPath("/some_path_three")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING))
                );

        // no assertion exception thrown
    }

    @Test(expected = AssertionError.class)
    public void shouldVerifyZeroMatches() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{});

        // when
        mockServerClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING))
                );
    }

    @Test(expected = AssertionError.class)
    public void shouldVerifyNullExpectationsReturned() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(null);

        // when
        mockServerClient.verify(request(), org.mockserver.client.proxy.Times.exactly(2));
    }

    @Test
    public void shouldVerifyOneRequestCount() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{
                new ExpectationDTO().buildObject()
        });

        // when
        mockServerClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING)),
                        org.mockserver.client.proxy.Times.once()
                );

        // no assertion exception thrown
    }

    @Test(expected = AssertionError.class)
    public void shouldVerifyNotOneRequestCount() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{
                new ExpectationDTO().buildObject(),
                new ExpectationDTO().buildObject()
        });

        // when
        mockServerClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING)),
                        org.mockserver.client.proxy.Times.once()
                );
    }

    @Test
    public void shouldVerifyExactRequestCount() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{
                new ExpectationDTO().buildObject()
        });

        // when
        mockServerClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING)),
                        org.mockserver.client.proxy.Times.exactly(1)
                );

        // no assertion exception thrown
    }

    @Test
    public void shouldVerifyExactRequestCountWithResponseFilter() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{
                new ExpectationDTO()
                        .setHttpResponse(new HttpResponseDTO(
                                response()
                                        .withBody(new StringBody("some_response_body"))
                        ))
                        .buildObject()
        });

        // when
        mockServerClient
                .verify(
                        request()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body")),
                        response()
                                .withBody(new StringBody("some_response_body")),
                        org.mockserver.client.proxy.Times.exactly(1)
                );

        // no assertion exception thrown
    }

    @Test(expected = AssertionError.class)
    public void shouldVerifyNotExactRequestCount() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{
                new ExpectationDTO().buildObject(),
                new ExpectationDTO().buildObject()
        });

        // when
        mockServerClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING)),
                        org.mockserver.client.proxy.Times.exactly(1)
                );
    }

    @Test(expected = AssertionError.class)
    public void shouldVerifyNotExactRequestCountWithResponseFilter() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{
                new ExpectationDTO()
                        .setHttpResponse(new HttpResponseDTO(
                                response()
                                        .withBody(new StringBody("some_other_response_body"))
                        ))
                        .buildObject(),
                new ExpectationDTO()
                        .setHttpResponse(new HttpResponseDTO(
                                response()
                                        .withBody(new StringBody("some_response_body"))
                        ))
                        .buildObject()
        });

        // when
        mockServerClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING)),
                        response()
                                .withBody(new StringBody("some_response_body")),
                        org.mockserver.client.proxy.Times.exactly(2)
                );
    }

    @Test
    public void shouldVerifyAtLeastRequestCountAllowExactMatch() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{
                new ExpectationDTO().buildObject()
        });

        // when
        mockServerClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING)),
                        org.mockserver.client.proxy.Times.atLeast(1)
                );

        // no assertion exception thrown
    }

    @Test
    public void shouldVerifyAtLeastRequestCountAllowsMoreThen() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{
                new ExpectationDTO().buildObject(),
                new ExpectationDTO().buildObject(),
                new ExpectationDTO().buildObject()
        });

        // when
        mockServerClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING)),
                        org.mockserver.client.proxy.Times.atLeast(1)
                );

        // no assertion exception thrown
    }

    @Test(expected = AssertionError.class)
    public void shouldVerifyNotAtLeastRequestCount() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{
                new ExpectationDTO().buildObject()
        });

        // when
        mockServerClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.STRING)),
                        org.mockserver.client.proxy.Times.exactly(2)
                );
    }

    @Test
    public void shouldHandleNullHttpRequest() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage(containsString("verify(HttpRequest) requires a non null HttpRequest object"));

        // when
        mockServerClient.verify(null, org.mockserver.client.proxy.Times.exactly(2));
    }

    @Test(expected = AssertionError.class)
    public void shouldHandleNullTimes() {
        // when
        mockServerClient.verify(request(), null);
    }
}
