package org.mockserver.client.proxy;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.model.ExpectationDTO;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Body;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.StringBody;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class ProxyClientTest {

    @Mock
    private ApacheHttpClient mockApacheHttpClient;
    @Mock
    private ExpectationSerializer expectationSerializer;
    @InjectMocks
    private ProxyClient proxyClient;

    @Before
    public void setupTestFixture() throws Exception {
        proxyClient = new ProxyClient("localhost", 8080);

        initMocks(this);
    }

    @Test
    public void shouldSendDumpToLogAsJSONRequest() throws Exception {
        // when
        proxyClient.dumpToLogAsJSON();

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/dumpToLog", "");
    }

    @Test
    public void shouldSendDumpToLogAsJavaRequest() throws Exception {
        // when
        proxyClient.dumpToLogAsJava();

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/dumpToLog?type=java", "");
    }

    @Test
    public void shouldSendResetRequest() throws Exception {
        // when
        proxyClient.reset();

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/reset", "");
    }

    @Test
    public void shouldSendStopRequest() throws Exception {
        // when
        proxyClient.stop();

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/stop", "");
    }

    @Test
    public void shouldSendClearRequest() throws Exception {
        // when
        proxyClient
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
        assertSame(expectations, proxyClient
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
        assertSame(expectations, proxyClient.retrieveAsExpectations(null));

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
        assertEquals(expectations, proxyClient
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
        assertEquals(expectations, proxyClient.retrieveAsJSON(null));

        // then
        verify(mockApacheHttpClient).sendPUTRequest("http://localhost:8080", "/retrieve", "");
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
        proxyClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
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
        proxyClient
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
        proxyClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
                        Times.once()
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
        proxyClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
                        Times.once()
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
        proxyClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
                        Times.exactly(1)
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
        proxyClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
                        Times.exactly(1)
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
        proxyClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
                        Times.atLeast(1)
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
        proxyClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
                        Times.atLeast(1)
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
        proxyClient
                .verify(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body", Body.Type.EXACT)),
                        Times.exactly(2)
                );
    }
}
