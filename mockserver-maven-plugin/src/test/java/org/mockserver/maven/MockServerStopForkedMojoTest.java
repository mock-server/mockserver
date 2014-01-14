package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerStopForkedMojoTest {

    @Mock
    private EmbeddedJettyHolder mockEmbeddedJettyHolder;
    @InjectMocks
    private MockServerStopForkedMojo mockServerStopForkedMojo;

    @Before
    public void setupMocks() {
        initMocks(this);
    }

    @Test
    public void shouldStopMockServerAndProxySuccessfully() throws MojoExecutionException {
        // given
        mockServerStopForkedMojo.serverStopPort = 1;
        mockServerStopForkedMojo.proxyStopPort = 2;
        mockServerStopForkedMojo.stopWait = 3;
        mockServerStopForkedMojo.logLevel = "LEVEL";
        when(mockEmbeddedJettyHolder.stop(1, 2, 3, "LEVEL")).thenReturn(true);

        // when
        mockServerStopForkedMojo.execute();

        // then
        verify(mockEmbeddedJettyHolder).stop(1, 2, 3, "LEVEL");
    }

    @Test
    public void shouldStopMockServerAndProxyUnsuccessfully() throws MojoExecutionException {
        // given
        mockServerStopForkedMojo.serverStopPort = 1;
        mockServerStopForkedMojo.proxyStopPort = 2;
        mockServerStopForkedMojo.stopWait = 3;
        mockServerStopForkedMojo.logLevel = "LEVEL";
        when(mockEmbeddedJettyHolder.stop(1, 2, 3, "LEVEL")).thenReturn(false);

        // when
        mockServerStopForkedMojo.execute();

        // then
        verify(mockEmbeddedJettyHolder).stop(1, 2, 3, "LEVEL");
    }

    @Test
    public void shouldSkipStoppingMockServer() throws MojoExecutionException {
        // given
        mockServerStopForkedMojo.skip = true;

        // when
        mockServerStopForkedMojo.execute();

        // then
        verifyNoMoreInteractions(mockEmbeddedJettyHolder);
    }
}
