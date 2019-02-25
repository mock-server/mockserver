package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
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
        initMocks(this);
    }

    @Test
    public void shouldHandleHttpRequests() {
        // given
        SettableFuture<HttpResponse> responseFuture = SettableFuture.create();
        HttpRequest httpRequest = request();
        HttpForward httpForward = forward()
            .withHost("some_host")
            .withPort(1080)
            .withScheme(HttpForward.Scheme.HTTP);
        when(mockHttpClient.sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()))).thenReturn(responseFuture);

        // when
        SettableFuture<HttpResponse> actualHttpResponse = httpForwardActionHandler.handle(httpForward, httpRequest).getHttpResponse();

        // then
        assertThat(actualHttpResponse, is(sameInstance(responseFuture)));
        verify(mockHttpClient).sendRequest(httpRequest.withSecure(false), new InetSocketAddress(httpForward.getHost(), httpForward.getPort()));
    }

    @Test
    public void shouldHandleSecureHttpRequests() {
        // given
        SettableFuture<HttpResponse> httpResponse = SettableFuture.create();
        HttpRequest httpRequest = request();
        HttpForward httpForward = forward()
            .withHost("some_host")
            .withPort(1080)
            .withScheme(HttpForward.Scheme.HTTPS);
        when(mockHttpClient.sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()))).thenReturn(httpResponse);

        // when
        SettableFuture<HttpResponse> actualHttpResponse = httpForwardActionHandler.handle(httpForward, httpRequest).getHttpResponse();

        // then
        assertThat(actualHttpResponse, is(sameInstance(httpResponse)));
        verify(mockHttpClient).sendRequest(httpRequest.withSecure(true), new InetSocketAddress(httpForward.getHost(), httpForward.getPort()));
    }
}
