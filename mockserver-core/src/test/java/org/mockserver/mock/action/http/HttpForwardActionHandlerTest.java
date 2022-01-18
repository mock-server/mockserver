package org.mockserver.mock.action.http;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class HttpForwardActionHandlerTest {

    private HttpForwardActionHandler httpForwardActionHandler;
    private NettyHttpClient mockHttpClient;

    @Before
    public void setupMocks() {
        mockHttpClient = mock(NettyHttpClient.class);
        MockServerLogger logFormatter = mock(MockServerLogger.class);
        httpForwardActionHandler = new HttpForwardActionHandler(logFormatter, mockHttpClient);
        openMocks(this);
    }

    @Test
    public void shouldHandleHttpRequests() {
        // given
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
        HttpRequest httpRequest = request();
        HttpForward httpForward = forward()
            .withHost("some_host")
            .withPort(1090)
            .withScheme(HttpForward.Scheme.HTTP);
        when(mockHttpClient.sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()))).thenReturn(responseFuture);

        // when
        CompletableFuture<HttpResponse> actualHttpResponse = httpForwardActionHandler
            .handle(httpForward, httpRequest)
            .getHttpResponse();

        // then
        assertThat(actualHttpResponse, is(sameInstance(responseFuture)));
        verify(mockHttpClient).sendRequest(httpRequest.withSecure(false), new InetSocketAddress(httpForward.getHost(), httpForward.getPort()));
    }

    @Test
    public void shouldHandleSecureHttpRequests() {
        // given
        CompletableFuture<HttpResponse> httpResponse = new CompletableFuture<>();
        HttpRequest httpRequest = request();
        HttpForward httpForward = forward()
            .withHost("some_host")
            .withPort(1090)
            .withScheme(HttpForward.Scheme.HTTPS);
        when(mockHttpClient.sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()))).thenReturn(httpResponse);

        // when
        CompletableFuture<HttpResponse> actualHttpResponse = httpForwardActionHandler
            .handle(httpForward, httpRequest)
            .getHttpResponse();

        // then
        assertThat(actualHttpResponse, is(sameInstance(httpResponse)));
        verify(mockHttpClient).sendRequest(httpRequest.withSecure(true), new InetSocketAddress(httpForward.getHost(), httpForward.getPort()));
    }
}
