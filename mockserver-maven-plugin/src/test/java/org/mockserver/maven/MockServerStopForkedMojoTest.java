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
    public void shouldStopMockServerSuccessfully() throws MojoExecutionException {
        // given
        mockServerStopForkedMojo.stopPort = 1;
        mockServerStopForkedMojo.stopKey = "stopKey";
        mockServerStopForkedMojo.stopWait = 2;
        mockServerStopForkedMojo.logLevel = "LEVEL";
        when(mockEmbeddedJettyHolder.stop(1, "stopKey", 2, "LEVEL")).thenReturn(true);

        // when
        mockServerStopForkedMojo.execute();

        // then
        verify(mockEmbeddedJettyHolder).stop(1, "stopKey", 2, "LEVEL");
    }

    @Test
    public void shouldStopMockServerUnsuccessfully() throws MojoExecutionException {
        // given
        mockServerStopForkedMojo.stopPort = 1;
        mockServerStopForkedMojo.stopKey = "stopKey";
        mockServerStopForkedMojo.stopWait = 2;
        mockServerStopForkedMojo.logLevel = "LEVEL";
        when(mockEmbeddedJettyHolder.stop(1, "stopKey", 2, "LEVEL")).thenReturn(false);

        // when
        mockServerStopForkedMojo.execute();

        // then
        verify(mockEmbeddedJettyHolder).stop(1, "stopKey", 2, "LEVEL");
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
