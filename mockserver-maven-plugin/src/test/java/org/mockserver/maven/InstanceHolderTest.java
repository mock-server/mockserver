package org.mockserver.maven;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.MockServer;
import org.mockserver.mockserver.MockServerBuilder;
import org.mockserver.proxy.http.HttpProxy;
import org.mockserver.proxy.http.HttpProxyBuilder;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class InstanceHolderTest {

    @Mock
    private HttpProxyBuilder mockProxyBuilder;
    @Mock
    private MockServerBuilder mockMockServerBuilder;

    @Mock
    private HttpProxy mockProxy;
    @Mock
    private MockServer mockMockServer;

    @Mock
    private MockServerClient mockServerClient;
    @Mock
    private ProxyClient proxyClient;

    @InjectMocks
    private InstanceHolder instanceHolder;

    @Before
    public void setupMock() {
        instanceHolder = new InstanceHolder();

        initMocks(this);

        InstanceHolder.mockServerBuilder = mockMockServerBuilder;
        InstanceHolder.proxyBuilder = mockProxyBuilder;

        when(mockMockServerBuilder.withHTTPPort(anyInt())).thenReturn(mockMockServerBuilder);
        when(mockMockServerBuilder.withHTTPSPort(anyInt())).thenReturn(mockMockServerBuilder);
        when(mockProxyBuilder.withHTTPPort(anyInt())).thenReturn(mockProxyBuilder);
        when(mockProxyBuilder.withHTTPSPort(anyInt())).thenReturn(mockProxyBuilder);

        when(mockProxy.isRunning()).thenReturn(false);
        when(mockMockServer.isRunning()).thenReturn(false);
    }

    @After
    public void shutdownProxyAndMockServer() {
        instanceHolder.stop();
    }

    @Test
    public void shouldStartServerAndProxyOnBothPorts() {
        // when
        instanceHolder.start(1, 2, 3, 4);

        // then
        verify(mockMockServerBuilder).withHTTPPort(1);
        verify(mockMockServerBuilder).withHTTPSPort(2);
        verify(mockProxyBuilder).withHTTPPort(3);
        verify(mockProxyBuilder).withHTTPSPort(4);
    }

    @Test
    public void shouldStartOnlyServerOnBothPorts() {
        // when
        instanceHolder.start(1, 2, -1, -1);

        // then
        verify(mockMockServerBuilder).withHTTPPort(1);
        verify(mockMockServerBuilder).withHTTPSPort(2);
        verifyNoMoreInteractions(mockProxyBuilder);
    }

    @Test
    public void shouldStartOnlyProxyOnBothPorts() {
        // when
        instanceHolder.start(-1, -1, 3, 4);

        // then
        verifyNoMoreInteractions(mockMockServerBuilder);
        verify(mockProxyBuilder).withHTTPPort(3);
        verify(mockProxyBuilder).withHTTPSPort(4);
    }

    @Test
    public void shouldNotStartServerOrProxy() {
        // when
        instanceHolder.start(-1, -1, -1, -1);

        // then
        verifyNoMoreInteractions(mockMockServerBuilder);
        verifyNoMoreInteractions(mockProxyBuilder);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfServerRunning() {
        // given
        when(mockMockServer.isRunning()).thenReturn(true);

        // when
        instanceHolder.start(1, 2, 3, 4);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfProxyRunning() {
        // given
        when(mockProxy.isRunning()).thenReturn(true);

        // when
        instanceHolder.start(1, 2, 3, 4);
    }

    @Test
    public void shouldStopMockServer() {
        // given
        when(mockMockServer.isRunning()).thenReturn(true);
        when(mockProxy.isRunning()).thenReturn(true);

        // when
        instanceHolder.stop();

        // then
        verify(mockMockServer).stop();
        verify(mockProxy).stop();
    }

    @Test
    public void shouldStopMockServerAndProxyRemotely() {
        // given
        InstanceHolder embeddedJettyHolder = spy(instanceHolder);
        when(embeddedJettyHolder.newMockServerClient(1)).thenReturn(mockServerClient);
        when(embeddedJettyHolder.newProxyClient(2)).thenReturn(proxyClient);

        // when
        embeddedJettyHolder.stop(1, 2);

        // then
        verify(mockServerClient).stop();
        verify(proxyClient).stop();
    }

    @Test
    public void shouldStopMockServerOnlyRemotely() {
        // given
        InstanceHolder embeddedJettyHolder = spy(instanceHolder);
        when(embeddedJettyHolder.newMockServerClient(1)).thenReturn(mockServerClient);
        when(embeddedJettyHolder.newProxyClient(2)).thenReturn(proxyClient);

        // when
        embeddedJettyHolder.stop(1, -1);

        // then
        verify(mockServerClient).stop();
        verify(proxyClient, times(0)).stop();
    }

    @Test
    public void shouldStopProxyOnlyRemotely() {
        // given
        InstanceHolder embeddedJettyHolder = spy(instanceHolder);
        when(embeddedJettyHolder.newMockServerClient(1)).thenReturn(mockServerClient);
        when(embeddedJettyHolder.newProxyClient(2)).thenReturn(proxyClient);

        // when
        embeddedJettyHolder.stop(-1, 2);

        // then
        verify(mockServerClient, times(0)).stop();
        verify(proxyClient).stop();
    }
}
