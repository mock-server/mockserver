package org.mockserver.client.http;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class HttpRequestClientTest {

    @Spy
    private HttpClient mockHttpClient;
    @Mock
    private Request mockRequest = mock(Request.class);

    @Before
    public void setupTestFixture() throws Exception {
        mockHttpClient = new HttpClient();

        initMocks(this);

        // - do nothing when start is called
        doNothing().when(mockHttpClient).start();
        // - an http client that can create a request
        when(mockHttpClient.newRequest(anyString())).thenReturn(mockRequest);
        // - a request that has a fluent API
        when(mockRequest.method(any(HttpMethod.class))).thenReturn(mockRequest);
        when(mockRequest.header(anyString(), anyString())).thenReturn(mockRequest);
        when(mockRequest.content(any(StringContentProvider.class))).thenReturn(mockRequest);
    }

    @Test
    public void shouldSendExpectationRequest() throws Exception {
        // when
        new HttpRequestClient("baseUri", mockHttpClient).sendRequest("body", "/path");
        // then
        verify(mockHttpClient).newRequest("baseUri/path");
        verify(mockRequest).method(HttpMethod.PUT);
        verify(mockRequest).header("Content-Type", "application/json; charset=utf-8");
        verify(mockRequest).content(new ComparableStringContentProvider("body", "UTF-8"));
        verify(mockRequest).send();
    }
}
