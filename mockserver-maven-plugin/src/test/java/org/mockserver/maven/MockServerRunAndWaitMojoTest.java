package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerRunAndWaitMojoTest {

    @Mock
    private EmbeddedJettyHolder mockEmbeddedJettyHolder;
    @Mock
    private Future mockFuture;
    @InjectMocks
    private MockServerRunAndWaitMojo mockServerRunAndWaitMojo;

    @Before
    public void setupMocks() {
        initMocks(this);
    }

    @Test
    public void shouldRunMockServerAndWaitIndefinitely() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunAndWaitMojo.port = 1;
        mockServerRunAndWaitMojo.securePort = 2;
        mockServerRunAndWaitMojo.logLevel = "LEVEL";
        mockServerRunAndWaitMojo.timeout = 0;
        when(mockEmbeddedJettyHolder.start(1, 2, "LEVEL")).thenReturn(mockFuture);

        // when
        mockServerRunAndWaitMojo.execute();

        // then
        verify(mockEmbeddedJettyHolder).start(1, 2, "LEVEL");
        verify(mockFuture).get();
    }

    @Test
    public void shouldRunMockServerAndWaitForFixedPeriod() throws MojoExecutionException, ExecutionException, InterruptedException, TimeoutException {
        // given
        mockServerRunAndWaitMojo.port = 1;
        mockServerRunAndWaitMojo.securePort = 2;
        mockServerRunAndWaitMojo.logLevel = "LEVEL";
        mockServerRunAndWaitMojo.timeout = 2;
        when(mockEmbeddedJettyHolder.start(1, 2, "LEVEL")).thenReturn(mockFuture);

        // when
        mockServerRunAndWaitMojo.execute();

        // then
        verify(mockEmbeddedJettyHolder).start(1, 2, "LEVEL");
        verify(mockFuture).get(2, TimeUnit.SECONDS);
    }

    @Test
    public void shouldSkipStoppingMockServer() throws MojoExecutionException {
        // given
        mockServerRunAndWaitMojo.skip = true;

        // when
        mockServerRunAndWaitMojo.execute();

        // then
        verifyNoMoreInteractions(mockEmbeddedJettyHolder);
    }
}
