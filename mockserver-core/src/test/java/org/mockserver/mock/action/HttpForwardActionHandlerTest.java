package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Header;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class HttpForwardActionHandlerTest {

    @InjectMocks
    private HttpForwardActionHandler httpForwardActionHandler;
    @Mock
    private HttpRequest httpRequest;
    @Mock
    private HttpForward httpForward;
    @Mock
    private NettyHttpClient mockHttpClient;
    private MockServerLogger logFormatter;

    @Before
    public void setupMocks() {
        logFormatter = mock(MockServerLogger.class);
        httpForwardActionHandler = new HttpForwardActionHandler(logFormatter);
        initMocks(this);
        when(httpForward.getHost()).thenReturn("some_host");
        when(httpForward.getPort()).thenReturn(1080);
        when(httpRequest.clone()).thenReturn(httpRequest);
        when(httpRequest.withHeaders(anyListOf(Header.class))).thenReturn(httpRequest);
    }

    @Test
    public void shouldHandleHttpRequests() {
        // given
        SettableFuture<HttpResponse> httpResponse = SettableFuture.create();
        when(httpForward.getScheme()).thenReturn(HttpForward.Scheme.HTTP);
        when(mockHttpClient.sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()))).thenReturn(httpResponse);

        // when
        SettableFuture<HttpResponse> actualHttpResponse = httpForwardActionHandler.handle(httpForward, httpRequest);

        // then
        assertThat(actualHttpResponse, is(sameInstance(httpResponse)));
        verify(httpRequest).withSecure(false);
        verify(mockHttpClient).sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()));
    }

    @Test
    public void shouldHandleSecureHttpRequests() {
        // given
        SettableFuture<HttpResponse> httpResponse = SettableFuture.create();
        when(httpForward.getScheme()).thenReturn(HttpForward.Scheme.HTTPS);
        when(mockHttpClient.sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()))).thenReturn(httpResponse);

        // when
        SettableFuture<HttpResponse> actualHttpResponse = httpForwardActionHandler.handle(httpForward, httpRequest);

        // then
        assertThat(actualHttpResponse, is(sameInstance(httpResponse)));
        verify(httpRequest).withSecure(true);
        verify(mockHttpClient).sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()));
    }
}
