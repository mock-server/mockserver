package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerStartMojoTest {

    @Mock
    private InstanceHolder mockEmbeddedJettyHolder;
    @InjectMocks
    private MockServerStartMojo mockServerStartMojo;

    @Before
    public void setupMocks() {
        initMocks(this);
    }

    @Test
    public void shouldStartMockServer() throws MojoExecutionException {
        // given
        mockServerStartMojo.serverPort = 1;
        mockServerStartMojo.serverSecurePort = 2;
        mockServerStartMojo.proxyPort = 3;
        mockServerStartMojo.proxySecurePort = 4;
        mockServerStartMojo.logLevel = "LEVEL";

        // when
        mockServerStartMojo.execute();

        // then
        verify(mockEmbeddedJettyHolder).start(1, 2, 3, 4, "LEVEL");
    }

    @Test
    public void shouldSkipStartingMockServer() throws MojoExecutionException {
        // given
        mockServerStartMojo.skip = true;

        // when
        mockServerStartMojo.execute();

        // then
        verifyNoMoreInteractions(mockEmbeddedJettyHolder);
    }
}
