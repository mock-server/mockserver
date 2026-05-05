package org.mockserver.maven;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author jamesdbloom
 */
public class MockServerStopForkedMojoTest {

    @Mock
    private InstanceHolder mockInstanceHolder;
    @InjectMocks
    private MockServerStopForkedMojo mockServerStopForkedMojo;

    @Before
    public void setupMocks() {
        openMocks(this);
        MockServerAbstractMojo.instanceHolder = mockInstanceHolder;
    }

    @Test
    public void shouldStopMockServerAndProxySuccessfully() {
        // given
        mockServerStopForkedMojo.serverPort = "1,2";

        // when
        mockServerStopForkedMojo.execute();

        // then
        verify(mockInstanceHolder).stop(new Integer[]{1,2}, false);
    }

    @Test
    public void shouldSkipStoppingMockServer() {
        // given
        mockServerStopForkedMojo.skip = true;

        // when
        mockServerStopForkedMojo.execute();

        // then
        verifyNoMoreInteractions(mockInstanceHolder);
    }
}
