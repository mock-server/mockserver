package org.mockserver.maven;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.jetty.proxy.ProxyRunner;
import org.mockserver.jetty.server.MockServerRunner;

import static org.mockito.Mockito.*;

/**
 * @author jamesdbloom
 */
public class EmbeddedJettyHolderTest {

    private MockServerRunner mockMockServerRunner;
    private ProxyRunner mockProxyRunner;

    @Before
    public void setupMock() {
        mockMockServerRunner = mock(MockServerRunner.class);
        EmbeddedJettyHolder.MOCK_SERVER_RUNNER = mockMockServerRunner;

        mockProxyRunner = mock(ProxyRunner.class);
        EmbeddedJettyHolder.PROXY_RUNNER = mockProxyRunner;
    }

    @Test
    public void shouldStartServerAndProxyOnBothPorts() {
        // when
        new EmbeddedJettyHolder().start(1, 2, 3, 4, "LEVEL");

        // then
        verify(mockMockServerRunner).overrideLogLevel("LEVEL");
        verify(mockMockServerRunner).start(1, 2);
        verify(mockProxyRunner).start(3, 4);
    }

    @Test
    public void shouldStartOnlyServerOnBothPorts() {
        // when
        new EmbeddedJettyHolder().start(1, 2, -1, -1, "LEVEL");

        // then
        verify(mockMockServerRunner).overrideLogLevel("LEVEL");
        verify(mockMockServerRunner).start(1, 2);
        verify((mockProxyRunner), times(0)).start(anyInt(), anyInt());
    }

    @Test
    public void shouldStartOnlyProxyOnBothPorts() {
        // when
        new EmbeddedJettyHolder().start(-1, -1, 3, 4, "LEVEL");

        // then
        verify(mockMockServerRunner).overrideLogLevel("LEVEL");
        verify((mockMockServerRunner), times(0)).start(anyInt(), anyInt());
        verify(mockProxyRunner).start(3, 4);
    }

    @Test
    public void shouldNotStartServerOrProxy() {
        // when
        new EmbeddedJettyHolder().start(-1, -1, -1, -1, "LEVEL");

        // then
        verify(mockMockServerRunner).overrideLogLevel("LEVEL");
        verify((mockMockServerRunner), times(0)).start(anyInt(), anyInt());
        verify((mockProxyRunner), times(0)).start(anyInt(), anyInt());
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfServerRunning() {
        // given
        when(mockMockServerRunner.isRunning()).thenReturn(true);

        // when
        new EmbeddedJettyHolder().start(1, 2, 3, 4, "LEVEL");
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfProxyRunning() {
        // given
        when(mockProxyRunner.isRunning()).thenReturn(true);

        // when
        new EmbeddedJettyHolder().start(1, 2, 3, 4, "LEVEL");
    }

    @Test
    public void shouldStopMockServer() {
        // given
        when(mockMockServerRunner.isRunning()).thenReturn(true);
        when(mockProxyRunner.isRunning()).thenReturn(true);

        // when
        new EmbeddedJettyHolder().stop();

        // then
        verify(mockMockServerRunner).stop();
        verify(mockProxyRunner).stop();
    }

    @Test
    public void shouldStopMockServerAndProxyRemotely() {
        // when
        new EmbeddedJettyHolder().stop(1, 2, 3, "LEVEL");

        // then
        verify(mockMockServerRunner).overrideLogLevel("LEVEL");
        verify(mockMockServerRunner).stop("127.0.0.1", 1, 3);
        verify(mockProxyRunner).stop("127.0.0.1", 2, 3);
    }

    @Test
    public void shouldStopMockServerOnlyRemotely() {
        // when
        new EmbeddedJettyHolder().stop(1, -1, 3, "LEVEL");

        // then
        verify(mockMockServerRunner).overrideLogLevel("LEVEL");
        verify(mockMockServerRunner).stop("127.0.0.1", 1, 3);
        verify((mockProxyRunner), times(0)).start(anyInt(), anyInt());
    }

    @Test
    public void shouldStopProxyOnlyRemotely() {
        // when
        new EmbeddedJettyHolder().stop(-1, 2, 3, "LEVEL");

        // then
        verify(mockMockServerRunner).overrideLogLevel("LEVEL");
        verify((mockMockServerRunner), times(0)).start(anyInt(), anyInt());
        verify(mockProxyRunner).stop("127.0.0.1", 2, 3);
    }
}
