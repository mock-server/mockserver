package org.mockserver.mock.action;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class HttpForwardActionHandlerTest {

    @InjectMocks
    private HttpForwardActionHandler httpForwardActionHandler = new HttpForwardActionHandler();
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
        HttpResponse httpResponse = response("some_body");
        when(httpForward.getScheme()).thenReturn(HttpForward.Scheme.HTTP);
        when(mockHttpClient.sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()))).thenReturn(httpResponse);

        // when
        HttpResponse actualHttpResponse = httpForwardActionHandler.handle(httpForward, httpRequest);

        // then
        assertThat(actualHttpResponse, is(httpResponse));
        verify(httpRequest).withSecure(false);
        verify(mockHttpClient).sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()));
    }

    @Test
    public void shouldHandleSecureHttpRequests() {
        // given
        HttpResponse httpResponse = response("some_body");
        when(httpForward.getScheme()).thenReturn(HttpForward.Scheme.HTTPS);
        when(mockHttpClient.sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()))).thenReturn(httpResponse);

        // when
        HttpResponse actualHttpResponse = httpForwardActionHandler.handle(httpForward, httpRequest);

        // then
        assertThat(actualHttpResponse, is(httpResponse));
        verify(httpRequest).withSecure(true);
        verify(mockHttpClient).sendRequest(httpRequest, new InetSocketAddress(httpForward.getHost(), httpForward.getPort()));
    }
}