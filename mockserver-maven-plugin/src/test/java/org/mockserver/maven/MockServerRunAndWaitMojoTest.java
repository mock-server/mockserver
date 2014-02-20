package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockserver.mockserver.MockServer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerRunAndWaitMojoTest {

    @Mock
    private SettableFuture<Object> objectSettableFuture;
    @Mock
    private InstanceHolder mockEmbeddedJettyHolder;
    @Mock
    private MockServer mockServerRunner;
    @Spy
    @InjectMocks
    private MockServerRunAndWaitMojo mockServerRunAndWaitMojo = new MockServerRunAndWaitMojo();

    @Before
    public void setupMocks() {
        initMocks(this);

        when(mockServerRunAndWaitMojo.newSettableFuture()).thenReturn(objectSettableFuture);
    }

    @Test
    public void shouldRunMockServerAndWaitIndefinitely() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunAndWaitMojo.serverPort = 1;
        mockServerRunAndWaitMojo.serverSecurePort = 2;
        mockServerRunAndWaitMojo.proxyPort = 3;
        mockServerRunAndWaitMojo.proxySecurePort = 4;
        mockServerRunAndWaitMojo.timeout = 0;
        mockServerRunAndWaitMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";

        // when
        mockServerRunAndWaitMojo.execute();

        // then
        verify(mockEmbeddedJettyHolder).start(eq(1), eq(2), eq(3), eq(4), any(ExampleInitializationClass.class));
        verify(objectSettableFuture).get();
    }

    @Test
    public void shouldRunMockServerAndWaitIndefinitelyAndHandleInterruptedException() throws MojoExecutionException, ExecutionException, InterruptedException {
        // given
        mockServerRunAndWaitMojo.serverPort = 1;
        mockServerRunAndWaitMojo.serverSecurePort = 2;
        mockServerRunAndWaitMojo.proxyPort = 3;
        mockServerRunAndWaitMojo.proxySecurePort = 4;
        mockServerRunAndWaitMojo.timeout = 0;
        when(objectSettableFuture.get()).thenThrow(new InterruptedException("TEST EXCEPTION"));

        // when
        mockServerRunAndWaitMojo.execute();
    }

    @Test
    public void shouldRunMockServerAndWaitForFixedPeriod() throws MojoExecutionException, ExecutionException, InterruptedException, TimeoutException {
        // given
        mockServerRunAndWaitMojo.serverPort = 1;
        mockServerRunAndWaitMojo.serverSecurePort = 2;
        mockServerRunAndWaitMojo.proxyPort = 3;
        mockServerRunAndWaitMojo.proxySecurePort = 4;
        mockServerRunAndWaitMojo.timeout = 2;
        mockServerRunAndWaitMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";

        // when
        mockServerRunAndWaitMojo.execute();

        // then
        verify(mockEmbeddedJettyHolder).start(eq(1), eq(2), eq(3), eq(4), any(ExampleInitializationClass.class));
        verify(objectSettableFuture).get(2, TimeUnit.SECONDS);
    }

    @Test
    public void shouldRunMockServerAndWaitForFixedPeriodAndHandleInterruptedException() throws MojoExecutionException, ExecutionException, InterruptedException, TimeoutException {
        // given
        mockServerRunAndWaitMojo.serverPort = 1;
        mockServerRunAndWaitMojo.serverSecurePort = 2;
        mockServerRunAndWaitMojo.proxyPort = 3;
        mockServerRunAndWaitMojo.proxySecurePort = 4;
        mockServerRunAndWaitMojo.timeout = 2;
        when(objectSettableFuture.get(2, TimeUnit.SECONDS)).thenThrow(new InterruptedException("TEST EXCEPTION"));

        // when
        mockServerRunAndWaitMojo.execute();
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
