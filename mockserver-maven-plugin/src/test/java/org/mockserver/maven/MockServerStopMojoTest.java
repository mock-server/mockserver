package org.mockserver.maven;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.openMocks;

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
        openMocks(this);
        MockServerAbstractMojo.instanceHolder = mockEmbeddedJettyHolder;
    }

    @Test
    public void shouldStopMockServer() {
        // when
        mockServerStopMojo.execute();

        // then
        verify(mockEmbeddedJettyHolder).stop();
    }

    @Test
    public void shouldSkipStoppingMockServer() {
        // given
        mockServerStopMojo.skip = true;

        // when
        mockServerStopMojo.execute();

        // then
        verifyNoMoreInteractions(mockEmbeddedJettyHolder);
    }
}
