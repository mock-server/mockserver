package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
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
        mockServerStartMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";

        // when
        mockServerStartMojo.execute();

        // then
        verify(mockEmbeddedJettyHolder).start(eq(1), eq(2), eq(3), eq(4), any(ExampleInitializationClass.class));
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
