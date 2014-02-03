package org.mockserver.maven;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.NettyMockServer;
import org.mockserver.proxy.http.HttpProxy;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class InstanceHolderTest {

    private HttpProxy mockProxy;
    private NettyMockServer mockMockServer;

    @Mock
    private MockServerClient mockServerClient;
    @Mock
    private ProxyClient proxyClient;

    @Before
    public void setupMock() {
        mockMockServer = mock(NettyMockServer.class);
        InstanceHolder.mockServer = mockMockServer;

        mockProxy = mock(HttpProxy.class);
        InstanceHolder.proxy = mockProxy;

        initMocks(this);
    }

    @Test
    public void shouldStartServerAndProxyOnBothPorts() {
        // when
        new InstanceHolder().start(1, 2, 3, 4, "LEVEL");

        // then
        verify(mockMockServer).overrideLogLevel("LEVEL");
        verify(mockMockServer).start(1, 2);
        verify(mockProxy).startHttpProxy(3, 4);
    }

    @Test
    public void shouldStartOnlyServerOnBothPorts() {
        // when
        new InstanceHolder().start(1, 2, -1, -1, "LEVEL");

        // then
        verify(mockMockServer).overrideLogLevel("LEVEL");
        verify(mockMockServer).start(1, 2);
        verify((mockProxy), times(0)).startHttpProxy(anyInt(), anyInt());
    }

    @Test
    public void shouldStartOnlyProxyOnBothPorts() {
        // when
        new InstanceHolder().start(-1, -1, 3, 4, "LEVEL");

        // then
        verify(mockMockServer).overrideLogLevel("LEVEL");
        verify((mockMockServer), times(0)).start(anyInt(), anyInt());
        verify(mockProxy).startHttpProxy(3, 4);
    }

    @Test
    public void shouldNotStartServerOrProxy() {
        // when
        new InstanceHolder().start(-1, -1, -1, -1, "LEVEL");

        // then
        verify(mockMockServer).overrideLogLevel("LEVEL");
        verify((mockMockServer), times(0)).start(anyInt(), anyInt());
        verify((mockProxy), times(0)).startHttpProxy(anyInt(), anyInt());
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfServerRunning() {
        // given
        when(mockMockServer.isRunning()).thenReturn(true);

        // when
        new InstanceHolder().start(1, 2, 3, 4, "LEVEL");
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfProxyRunning() {
        // given
        when(mockProxy.isRunning()).thenReturn(true);

        // when
        new InstanceHolder().start(1, 2, 3, 4, "LEVEL");
    }

    @Test
    public void shouldStopMockServer() {
        // given
        when(mockMockServer.isRunning()).thenReturn(true);
        when(mockProxy.isRunning()).thenReturn(true);

        // when
        new InstanceHolder().stop();

        // then
        verify(mockMockServer).stop();
        verify(mockProxy).stop();
    }

    @Test
    public void shouldStopMockServerAndProxyRemotely() {
        // given
        InstanceHolder embeddedJettyHolder = spy(new InstanceHolder());
        when(embeddedJettyHolder.newMockServerClient(1)).thenReturn(mockServerClient);
        when(embeddedJettyHolder.newProxyClient(2)).thenReturn(proxyClient);

        // when
        embeddedJettyHolder.stop(1, 2, "LEVEL");

        // then
        verify(mockMockServer).overrideLogLevel("LEVEL");
        verify(mockServerClient).stop();
        verify(proxyClient).stop();
    }

    @Test
    public void shouldStopMockServerOnlyRemotely() {
        // given
        InstanceHolder embeddedJettyHolder = spy(new InstanceHolder());
        when(embeddedJettyHolder.newMockServerClient(1)).thenReturn(mockServerClient);
        when(embeddedJettyHolder.newProxyClient(2)).thenReturn(proxyClient);

        // when
        embeddedJettyHolder.stop(1, -1, "LEVEL");

        // then
        verify(mockMockServer).overrideLogLevel("LEVEL");
        verify(mockServerClient).stop();
        verify(proxyClient, times(0)).stop();
    }

    @Test
    public void shouldStopProxyOnlyRemotely() {
        // given
        InstanceHolder embeddedJettyHolder = spy(new InstanceHolder());
        when(embeddedJettyHolder.newMockServerClient(1)).thenReturn(mockServerClient);
        when(embeddedJettyHolder.newProxyClient(2)).thenReturn(proxyClient);

        // when
        embeddedJettyHolder.stop(-1, 2, "LEVEL");

        // then
        verify(mockMockServer).overrideLogLevel("LEVEL");
        verify(mockServerClient, times(0)).stop();
        verify(proxyClient).stop();
    }
}
