package org.mockserver.maven;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author jamesdbloom
 */
public class MockServerRunAndWaitMojoTest {

    @Mock
    private CompletableFuture<Object> objectSettableFuture;
    @Mock
    private InstanceHolder mockInstanceHolder;
    @InjectMocks
    private MockServerRunAndWaitMojo mockServerRunAndWaitMojo = new MockServerRunAndWaitMojo();

    @Before
    public void setupMocks() {
        openMocks(this);

        MockServerAbstractMojo.instanceHolder = mockInstanceHolder;
    }

    @Test
    public void shouldRunMockServerWithNullTimeout() throws ExecutionException, InterruptedException {
        // given
        mockServerRunAndWaitMojo.serverPort = "1,2";
        mockServerRunAndWaitMojo.logLevel = "WARN";
        mockServerRunAndWaitMojo.pipeLogToConsole = true;
        mockServerRunAndWaitMojo.timeout = null;
        mockServerRunAndWaitMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";

        // when
        mockServerRunAndWaitMojo.execute();

        // then
        verify(mockInstanceHolder).start(eq(new Integer[]{1, 2}), eq(-1), eq(""), eq("WARN"), any(ExampleInitializationClass.class), eq(""));
        verify(objectSettableFuture).get();
    }

    @Test
    public void shouldRunMockServerAndWaitIndefinitelyAndHandleInterruptedException() throws ExecutionException, InterruptedException {
        // given
        mockServerRunAndWaitMojo.serverPort = "1";
        mockServerRunAndWaitMojo.timeout = 0;
        doThrow(new InterruptedException("TEST EXCEPTION")).when(objectSettableFuture).get();

        // when
        mockServerRunAndWaitMojo.execute();
    }

    @Test
    public void shouldRunMockServerAndWaitForFixedPeriod() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        mockServerRunAndWaitMojo.serverPort = "1,2";
        mockServerRunAndWaitMojo.timeout = 2;
        mockServerRunAndWaitMojo.initializationClass = "org.mockserver.maven.ExampleInitializationClass";

        // when
        mockServerRunAndWaitMojo.execute();

        // then
        verify(mockInstanceHolder).start(eq(new Integer[]{1, 2}), eq(-1), eq(""), eq("INFO"), any(ExampleInitializationClass.class), eq(""));
        verify(objectSettableFuture).get(2, TimeUnit.SECONDS);
    }

    @Test
    public void shouldSkipStoppingMockServer() {
        // given
        mockServerRunAndWaitMojo.skip = true;

        // when
        mockServerRunAndWaitMojo.execute();

        // then
        verifyNoMoreInteractions(mockInstanceHolder);
    }
}
