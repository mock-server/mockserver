package org.mockserver.maven;

import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class SettableFutureTest {

    @Test
    public void shouldCreateSettableFuture() {
        assertTrue(SettableFuture.create() instanceof SettableFuture);
    }

    @Test
    public void shouldReturnResultFromAnotherThread() throws ExecutionException, InterruptedException {
        // given
        final SettableFuture<String> settableFuture = SettableFuture.create();

        // when
        new Thread(new Runnable() {
            @Override
            public void run() {
                settableFuture.set("DONE");
            }
        }).start();

        // then
        assertEquals("DONE", settableFuture.get());
        assertTrue(settableFuture.isDone());
    }

    @Test(expected = ExecutionException.class)
    public void shouldReturnExceptionFromAnotherThread() throws ExecutionException, InterruptedException {
        // given
        final SettableFuture<String> settableFuture = SettableFuture.create();

        // when
        new Thread(new Runnable() {
            @Override
            public void run() {
                settableFuture.setException(new RuntimeException("TEST EXCEPTION"));
            }
        }).start();

        // then
        settableFuture.get();
    }


    @Test(expected = TimeoutException.class)
    public void shouldTimeoutIfResultNotSetByAnotherThread() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        final SettableFuture<String> settableFuture = SettableFuture.create();

        // then
        assertFalse(settableFuture.isDone());
        settableFuture.get(1, TimeUnit.SECONDS);
    }
}
