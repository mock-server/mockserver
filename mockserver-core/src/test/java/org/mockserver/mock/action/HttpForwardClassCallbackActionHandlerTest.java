package org.mockserver.mock.action;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class HttpForwardClassCallbackActionHandlerTest {

    private NettyHttpClient mockHttpClient;
    private HttpForwardClassCallbackActionHandler httpForwardClassCallbackActionHandler;

    @Before
    public void setupFixture() {
        mockHttpClient = mock(NettyHttpClient.class);
        httpForwardClassCallbackActionHandler = new HttpForwardClassCallbackActionHandler(new MockServerLogger(), mockHttpClient);

        initMocks(this);
    }

    @Test
    public void shouldHandleInvalidClass() throws Exception {
        // given
        CompletableFuture<HttpResponse> httpResponse = new CompletableFuture<>();
        httpResponse.complete(response("some_response_body"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), isNull(InetSocketAddress.class))).thenReturn(httpResponse);

        HttpClassCallback httpClassCallback = callback("org.mockserver.mock.action.FooBar");

        // when
        CompletableFuture<HttpResponse> actualHttpResponse = httpForwardClassCallbackActionHandler
            .handle(httpClassCallback, request().withBody("some_body"))
            .getHttpResponse();

        // then
        assertThat(actualHttpResponse.get(), is(httpResponse.get()));
        verify(mockHttpClient).sendRequest(request().withBody("some_body"), null);
    }

    @Test
    public void shouldHandleValidLocalClass() throws Exception {
        // given
        CompletableFuture<HttpResponse> httpResponse = new CompletableFuture<>();
        httpResponse.complete(response("some_response_body"));
        when(mockHttpClient.sendRequest(any(HttpRequest.class), isNull(InetSocketAddress.class))).thenReturn(httpResponse);

        HttpClassCallback httpClassCallback = callback(HttpForwardClassCallbackActionHandlerTest.TestCallback.class);

        // when
        CompletableFuture<HttpResponse> actualHttpResponse = httpForwardClassCallbackActionHandler
            .handle(httpClassCallback, request().withBody("some_body"))
            .getHttpResponse();

        // then
        assertThat(actualHttpResponse.get(), is(httpResponse.get()));
        verify(mockHttpClient).sendRequest(request("some_path"), null);
    }

    public static class TestCallback implements ExpectationForwardCallback {

        @Override
        public HttpRequest handle(HttpRequest httpRequest) {
            return request("some_path");
        }
    }
}
