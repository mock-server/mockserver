package org.mockserver.maven;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.server.MockServerRunner;

import static org.mockito.Mockito.*;

/**
 * @author jamesdbloom
 */
public class EmbeddedJettyHolderTest {

    private MockServerRunner mockMockServerRunner;

    @Before
    public void setupMock() {
        mockMockServerRunner = mock(MockServerRunner.class);
        EmbeddedJettyHolder.MOCK_SERVER_RUNNER = mockMockServerRunner;
    }

    @Test
    public void shouldStartMockServer() {
        // when
        new EmbeddedJettyHolder().start(1, 2, "LEVEL");

        // then
        verify(mockMockServerRunner).overrideLogLevel("LEVEL");
        verify(mockMockServerRunner).start(1, 2);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfRunning() {
        // given
        when(mockMockServerRunner.isRunning()).thenReturn(true);

        // when
        new EmbeddedJettyHolder().start(1, 2, "LEVEL");
    }

    @Test
    public void shouldStopMockServer() {
        // given
        when(mockMockServerRunner.isRunning()).thenReturn(true);

        // when
        new EmbeddedJettyHolder().stop();

        // then
        verify(mockMockServerRunner).stop();
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfNotRunning() {
        // given
        when(mockMockServerRunner.isRunning()).thenReturn(false);

        // when
        new EmbeddedJettyHolder().stop();
    }

    @Test
    public void shouldStopMockServerRemotely() {
        // when
        new EmbeddedJettyHolder().stop(1, 2, "LEVEL");

        // then
        verify(mockMockServerRunner).overrideLogLevel("LEVEL");
        verify(mockMockServerRunner).stop("127.0.0.1", 1, 2);
    }
}
