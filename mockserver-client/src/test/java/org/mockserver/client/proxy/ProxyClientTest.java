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
        verify(mockHttpClient).sendRequest("", "/reset");
    }

    @Test
    public void shouldSendDumpToLogRequest() throws Exception {
        // when
        proxyClient.dumpToLog();

        // then
        verify(mockHttpClient).sendRequest("", "/dumpToLog");
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
        verify(mockHttpClient).sendRequest("{\n" +
                "  \"method\" : \"\",\n" +
                "  \"url\" : \"\",\n" +
                "  \"path\" : \"/some_path\",\n" +
                "  \"queryString\" : \"\",\n" +
                "  \"body\" : \"some_request_body\",\n" +
                "  \"cookies\" : [ ],\n" +
                "  \"headers\" : [ ]\n" +
                "}", "/clear");
    }
}
