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
public class MockServerStopMojoTest {

    @Mock
    private InstanceHolder mockEmbeddedJettyHolder;
    @InjectMocks
    private MockServerStopMojo mockServerStopMojo;

    @Before
    public void setupMocks() {
        initMocks(this);
        MockServerAbstractMojo.embeddedJettyHolder = mockEmbeddedJettyHolder;
    }

    @Test
    public void shouldStopMockServer() throws MojoExecutionException {
        // when
        mockServerStopMojo.execute();

        // then
        verify(mockEmbeddedJettyHolder).stop();
    }

    @Test
    public void shouldSkipStoppingMockServer() throws MojoExecutionException {
        // given
        mockServerStopMojo.skip = true;

        // when
        mockServerStopMojo.execute();

        // then
        verifyNoMoreInteractions(mockEmbeddedJettyHolder);
    }
}
