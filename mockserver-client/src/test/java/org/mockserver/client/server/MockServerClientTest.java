package org.mockserver.client.server;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerClientTest {

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
    public void shouldSetupExpectationWithResponse() {
        // given
        HttpRequest httpRequest =
                new HttpRequest()
                        .withPath("/some_path")
                        .withBody(new StringBody("some_request_body", Body.Type.EXACT));
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
                        .withBody(new StringBody("some_request_body", Body.Type.EXACT));
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
    public void shouldSendExpectationRequestWithExactTimes() throws Exception {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT))))
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT))))
                        .setHttpForward(new HttpForwardDTO(new HttpForward()
                                .withHost("some_host")
                                .withPort(9090)
                                .withScheme(HttpForward.Scheme.HTTPS)))
                        .setTimes(new TimesDTO(Times.exactly(3)))
                        .buildObject());
    }

    @Test
    public void shouldSendExpectationRequestWithDefaultTimes() throws Exception {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT))
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT))))
                        .setHttpResponse(new HttpResponseDTO(new HttpResponse()
                                .withBody("some_response_body")
                                .withHeaders(new Header("responseName", "responseValue"))))
                        .setTimes(new TimesDTO(Times.unlimited()))
                        .buildObject());
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT))
                );

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/clear", "" +
                "{\n" +
                "  \"path\" : \"/some_path\",\n" +
                "  \"body\" : {\n" +
                "    \"type\" : \"EXACT\",\n" +
                "    \"value\" : \"some_request_body\"\n" +
                "  }\n" +
                "}");
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT))
                ));

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/retrieve", "" +
                "{\n" +
                "  \"path\" : \"/some_path\",\n" +
                "  \"body\" : {\n" +
                "    \"type\" : \"EXACT\",\n" +
                "    \"value\" : \"some_request_body\"\n" +
                "  }\n" +
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT))
                ));

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/retrieve", "" +
                "{\n" +
                "  \"path\" : \"/some_path\",\n" +
                "  \"body\" : {\n" +
                "    \"type\" : \"EXACT\",\n" +
                "    \"value\" : \"some_request_body\"\n" +
                "  }\n" +
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT))
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
                        new HttpRequest()
                                .withPath("/some_path_two")
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
                        new HttpRequest()
                                .withPath("/some_path_three")
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT))
                );

        // no assertion exception thrown
    }

    @Test(expected = AssertionError.class)
    public void shouldVerifyNoMatch() throws UnsupportedEncodingException {
        // given
        when(mockApacheHttpClient.sendPUTRequest(anyString(), anyString(), anyString())).thenReturn("body");
        when(expectationSerializer.deserializeArray(anyString())).thenReturn(new Expectation[]{});

        // when
        mockServerClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT))
                );
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
                        org.mockserver.client.proxy.Times.exactly(1)
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
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
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
                        org.mockserver.client.proxy.Times.exactly(2)
                );
    }
}
