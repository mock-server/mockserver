package org.mockserver.mock.action;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;

import java.net.InetSocketAddress;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class HttpForwardActionHandlerTest {

    @InjectMocks
    HttpForwardActionHandler httpForwardActionHandler = new HttpForwardActionHandler();
    @Mock
    private HttpRequest httpRequest;
    @Mock
    private HttpForward httpForward;
    @Mock
    private NettyHttpClient mockHttpClient;

    @Before
    public void setupMocks() {
        initMocks(this);
        when(httpForward.getHost()).thenReturn("some_host");
        when(httpForward.getPort()).thenReturn(1080);
    }

    @Test
    public void shouldHandleHttpRequests() {
        // given
        when(httpForward.getScheme()).thenReturn(HttpForward.Scheme.HTTP);

        // when
        httpForwardActionHandler.handle(httpForward, httpRequest);

        // then
        verify(httpRequest).withSecure(false);
        verify(mockHttpClient).sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()));
    }

    @Test
    public void shouldHandleSecureHttpRequests() {
        // given
        when(httpForward.getScheme()).thenReturn(HttpForward.Scheme.HTTPS);

        // when
        httpForwardActionHandler.handle(httpForward, httpRequest);

        // then
        verify(httpRequest).withSecure(true);
        verify(mockHttpClient).sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()));
    }
}