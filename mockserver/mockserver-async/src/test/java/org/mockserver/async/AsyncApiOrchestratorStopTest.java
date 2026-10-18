package org.mockserver.async;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Test;
import org.mockserver.async.asyncapi.AsyncApiChannel;
import org.mockserver.async.asyncapi.AsyncApiSpec;
import org.mockserver.async.publish.MessagePublisher;
import org.mockserver.async.publish.PublishOptions;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * {@code stop()} must not return while a publish cycle is still in flight.
 *
 * <p>Callers close the publishers immediately after {@code stop()} returns — the control plane's
 * teardown does exactly that — so a scheduler thread still inside {@code publishAll} would be publishing
 * against a channel being closed underneath it. {@code shutdownNow()} alone does not prevent this:
 * it interrupts the worker and returns straight away, without waiting for it to unwind.
 *
 * <p>The blocking publisher below is what makes this discriminating. A publisher that returns
 * immediately would pass whether or not {@code stop()} waits, because the race window would be too
 * small to observe — a fixture chosen around the bug. Here the publish takes a known, deliberately long time, so "did stop() wait?" is
 * directly observable rather than inferred from timing luck.
 *
 * <p><strong>The assertion counts starts against completions rather than asking "did some publish
 * finish?"</strong> The weaker form passes vacuously under load: the schedule runs publishes
 * back-to-back, so if enough wall-clock elapses between the latch being released and {@code stop()}
 * being called — a GC pause, or Surefire contention on a machine this repo has run at load 60+ —
 * publish #1 completes and sets a one-way flag, publish #2 is the one actually in flight,
 * {@code shutdownNow()} cuts #2 short, and a "some publish completed" assertion still passes. That
 * is a false green in the very test guarding the race, on exactly the machine profile that
 * provokes it. Comparing counts closes it: a publish cut short never increments the completion
 * counter, so {@code completed == started} is false whenever ANY cycle was interrupted, regardless
 * of how many cycles ran.
 */
public class AsyncApiOrchestratorStopTest {

    /** Long enough to be unambiguous, short enough to keep the suite fast. */
    private static final long PUBLISH_DURATION_MILLIS = 300;

    private AsyncApiMockOrchestrator orchestrator;

    @After
    public void tearDown() {
        if (orchestrator != null) {
            orchestrator.stop();
        }
    }

    @Test
    public void stopShouldWaitForAnInFlightPublishToFinish() throws Exception {
        CountDownLatch firstPublishStarted = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        AtomicBoolean anyPublishCutShort = new AtomicBoolean(false);

        MessagePublisher slowPublisher = new MessagePublisher() {
            @Override
            public void publish(String channel, String payload) {
                started.incrementAndGet();
                firstPublishStarted.countDown();
                try {
                    Thread.sleep(PUBLISH_DURATION_MILLIS);
                } catch (InterruptedException e) {
                    // shutdownNow() interrupts the worker mid-publish. Deliberately do NOT count
                    // this as a completion — that asymmetry is what makes the comparison below
                    // detect an interrupted cycle no matter how many cycles ran before it.
                    anyPublishCutShort.set(true);
                    Thread.currentThread().interrupt();
                    return;
                }
                completed.incrementAndGet();
            }

            @Override
            public void publish(String channel, String payload, PublishOptions options) {
                publish(channel, payload);
            }

            @Override
            public void close() {
                // nothing to release
            }
        };

        orchestrator = new AsyncApiMockOrchestrator(specWithOneChannel(), slowPublisher);
        orchestrator.startPublishing(50);

        assertThat("a publish cycle should have started",
            firstPublishStarted.await(10, TimeUnit.SECONDS), is(true));

        orchestrator.stop();

        // Read after stop() returns. With the fix, awaitTermination guarantees the worker has
        // finished unwinding by this point, so the counters are stable.
        int startedCount = started.get();
        int completedCount = completed.get();

        assertThat(
            "stop() must not return while a publish is still in flight — the caller closes the "
                + "publishers straight afterwards. started=" + startedCount
                + " completed=" + completedCount + " cutShort=" + anyPublishCutShort.get(),
            completedCount, is(startedCount));
    }

    @Test
    public void stopShouldBeIdempotentAndReturnPromptlyWhenIdle() {
        MessagePublisher noop = new MessagePublisher() {
            @Override
            public void publish(String channel, String payload) {
                // nothing published in this test
            }

            @Override
            public void publish(String channel, String payload, PublishOptions options) {
                publish(channel, payload);
            }

            @Override
            public void close() {
                // nothing to release
            }
        };

        orchestrator = new AsyncApiMockOrchestrator(specWithOneChannelUnchecked(), noop);

        // never started: stop() must be a no-op rather than blocking for the await budget
        long before = System.nanoTime();
        orchestrator.stop();
        orchestrator.stop();
        long elapsedMillis = (System.nanoTime() - before) / 1_000_000;

        assertThat("stop() on an unstarted orchestrator should return immediately",
            elapsedMillis < 1_000, is(true));
    }

    private static AsyncApiSpec specWithOneChannel() throws Exception {
        JsonNode example = new ObjectMapper().readTree("{\"v\":1}");
        AsyncApiChannel channel = new AsyncApiChannel("events", List.of(example), null);
        return new AsyncApiSpec("2.6.0", "Test", List.of(channel));
    }

    private static AsyncApiSpec specWithOneChannelUnchecked() {
        try {
            return specWithOneChannel();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
