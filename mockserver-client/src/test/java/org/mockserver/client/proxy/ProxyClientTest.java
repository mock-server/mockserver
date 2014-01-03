package org.mockserver.client.proxy;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.http.HttpRequestClient;
import org.mockserver.model.HttpRequest;

import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class ProxyClientTest {

    @Mock
    private HttpRequestClient mockHttpClient;
    @InjectMocks
    private ProxyClient proxyClient;

    @Before
    public void setupTestFixture() throws Exception {
        proxyClient = new ProxyClient("localhost", 8080);

        initMocks(this);
    }

    @Test
    public void shouldSendResetRequest() throws Exception {
        // when
        proxyClient.reset();

        // then
        verify(mockHttpClient).sendPUTRequest("http://localhost:8080", "", "/reset");
    }

    @Test
    public void shouldSendDumpToLogRequest() throws Exception {
        // when
        proxyClient.dumpToLog();

        // then
        verify(mockHttpClient).sendPUTRequest("http://localhost:8080", "", "/dumpToLog");
    }

    @Test
    public void shouldSendClearRequest() throws Exception {
        // when
        proxyClient
                .clear(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody("some_request_body")
                );

        // then
        verify(mockHttpClient).sendPUTRequest("http://localhost:8080", "{\n" +
                "  \"path\" : \"/some_path\",\n" +
                "  \"body\" : \"some_request_body\"\n" +
                "}", "/clear");
    }
}
