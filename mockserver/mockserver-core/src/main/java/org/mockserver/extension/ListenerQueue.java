package org.mockserver.extension;

import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.metrics.Metrics;
import org.slf4j.event.Level;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;

/**
 * One listener extension's event queue and the single daemon thread that delivers it. {@link #offer} is lock-free
 * and never waits: an event that would exceed the count bound, or the byte bound, is dropped and counted, so any
 * thread (an event loop included) can offer.
 */
final class ListenerQueue {

    /**
     * Why an event was not delivered; the label of {@code mock_server_extension_listener_dropped_total}.
     */
    enum DropReason {
        COUNT("count"),
        BYTES("bytes"),
        SHUTDOWN("shutdown");

        private final String label;

        DropReason(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private static final long IDLE_PARK_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    private final LoadedExtension extension;
    private final MockServerLogger mockServerLogger;
    private final int maxCount;
    private final long maxBytes;
    private final Queue<Object> queue = new ConcurrentLinkedQueue<>();
    // events offered and not yet delivered (or discarded), including the one being delivered
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicLong pendingBytes = new AtomicLong();
    private final AtomicLong droppedForCount = new AtomicLong();
    private final AtomicLong droppedForBytes = new AtomicLong();
    private final AtomicLong droppedForShutdown = new AtomicLong();
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicBoolean dropWarned = new AtomicBoolean();
    private final Thread thread;
    private volatile boolean accepting = true;
    private volatile boolean running = true;
    private volatile boolean parked;

    /**
     * @param maxBytes the byte bound for weighted events, or {@code <= 0} for none
     */
    ListenerQueue(LoadedExtension extension, MockServerLogger mockServerLogger, int maxCount, long maxBytes) {
        this.extension = extension;
        this.mockServerLogger = mockServerLogger;
        this.maxCount = Math.max(1, maxCount);
        this.maxBytes = maxBytes;
        this.thread = new Thread(this::deliverUntilStopped, "MockServer-ext-listener-" + extension.name());
        this.thread.setDaemon(true);
        this.thread.setContextClassLoader(Thread.currentThread().getContextClassLoader());
        this.thread.start();
    }

    LoadedExtension extension() {
        return extension;
    }

    long maxBytes() {
        return maxBytes;
    }

    /**
     * Queues an event without waiting. A weighted event is refused when it would take the queued bytes over the
     * bound, unless nothing is queued, so one event larger than the whole bound is still delivered.
     *
     * @param weight the bytes the event keeps reachable, or 0
     * @return true when the event was queued
     */
    boolean offer(Object event, long weight) {
        if (!accepting) {
            drop(DropReason.SHUTDOWN);
            return false;
        }
        if (pending.incrementAndGet() > maxCount) {
            pending.decrementAndGet();
            drop(DropReason.COUNT);
            return false;
        }
        if (weight > 0 && maxBytes > 0) {
            long queuedBytes;
            do {
                queuedBytes = pendingBytes.get();
                if (queuedBytes > 0 && queuedBytes + weight > maxBytes) {
                    pending.decrementAndGet();
                    drop(DropReason.BYTES);
                    return false;
                }
            } while (!pendingBytes.compareAndSet(queuedBytes, queuedBytes + weight));
        } else if (weight > 0) {
            pendingBytes.addAndGet(weight);
        }
        queue.offer(event);
        if (!accepting && queue.remove(event)) {
            // shutdown drained the queue between the check above and this offer
            release(event);
            drop(DropReason.SHUTDOWN);
            return false;
        }
        if (parked) {
            LockSupport.unpark(thread);
        }
        return true;
    }

    /**
     * Stops accepting events and waits, up to {@code budgetMillis}, for those queued to be delivered; anything left
     * is discarded and counted.
     */
    void flush(long budgetMillis) {
        accepting = false;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, budgetMillis));
        boolean interrupted = false;
        while (pending.get() > 0 && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            if (Thread.interrupted()) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Ends the delivery thread, discarding and counting whatever is still queued.
     */
    void shutdown() {
        accepting = false;
        running = false;
        LockSupport.unpark(thread);
        thread.interrupt();
        Object event;
        while ((event = queue.poll()) != null) {
            release(event);
            drop(DropReason.SHUTDOWN);
        }
    }

    long dropped(DropReason reason) {
        switch (reason) {
            case COUNT:
                return droppedForCount.get();
            case BYTES:
                return droppedForBytes.get();
            default:
                return droppedForShutdown.get();
        }
    }

    long delivered() {
        return delivered.get();
    }

    long errors() {
        return errors.get();
    }

    long pendingBytes() {
        return pendingBytes.get();
    }

    boolean isAlive() {
        return thread.isAlive();
    }

    private void deliverUntilStopped() {
        while (running) {
            Object event = queue.poll();
            if (event == null) {
                parked = true;
                // re-check after publishing parked, so an offer racing the first poll is never missed
                event = queue.poll();
                if (event == null) {
                    LockSupport.parkNanos(this, IDLE_PARK_NANOS);
                    parked = false;
                    continue;
                }
                parked = false;
            }
            try {
                deliver(event);
                delivered.incrementAndGet();
            } catch (Throwable throwable) {
                recordError(event, throwable);
            } finally {
                release(event);
            }
        }
    }

    private void deliver(Object event) {
        Extension instance = extension.extension();
        if (event instanceof ServeEvent) {
            if (extension.isHookEnabled(ExtensionHook.SERVE_EVENT_LISTENER)) {
                ((ServeEventListener) instance).onServed((ServeEvent) event);
            }
        } else if (event instanceof ExpectationChange) {
            if (extension.isHookEnabled(ExtensionHook.EXPECTATION_LIFECYCLE_LISTENER)) {
                ((ExpectationLifecycleListener) instance).onChange((ExpectationChange) event);
            }
        } else if (event instanceof Configuration) {
            if (extension.isHookEnabled(ExtensionHook.SETTINGS_LISTENER)) {
                ((SettingsListener) instance).onConfigurationUpdated((Configuration) event);
            }
        }
    }

    private void release(Object event) {
        long weight = weightOf(event);
        if (weight > 0) {
            pendingBytes.addAndGet(-weight);
        }
        pending.decrementAndGet();
    }

    private static long weightOf(Object event) {
        if (event instanceof ServeEvent) {
            return ((ServeEvent) event).weight;
        } else if (event instanceof ExpectationChange) {
            return ((ExpectationChange) event).weight;
        }
        return 0L;
    }

    private void drop(DropReason reason) {
        if (reason == DropReason.COUNT) {
            droppedForCount.incrementAndGet();
        } else if (reason == DropReason.BYTES) {
            droppedForBytes.incrementAndGet();
        } else {
            droppedForShutdown.incrementAndGet();
        }
        Metrics.incrementExtensionListenerDropped(extension.name(), reason.label());
        if (reason != DropReason.SHUTDOWN && dropWarned.compareAndSet(false, true) && isEnabled(Level.WARN)) {
            mockServerLogger.logEvent(entry(Level.WARN)
                .setMessageFormat("extension:{}listener queue is full, so events for it are being dropped (reason:{}); it is not keeping up, raise extensionListenerQueueSize or extensionListenerMaxBytes, or make the listener faster. Drops are counted in mock_server_extension_listener_dropped_total and never affect the event log or verification")
                .setArguments(extension.name(), reason.label()));
        }
    }

    private void recordError(Object event, Throwable throwable) {
        String hook = hookOf(event);
        Metrics.incrementExtensionErrors(extension.name(), hook);
        if (errors.incrementAndGet() == 1 && isEnabled(Level.WARN)) {
            mockServerLogger.logEvent(entry(Level.WARN)
                .setMessageFormat("extension:{}threw from hook:{}the event is skipped and later events are still delivered; further throws are only counted in mock_server_extension_errors_total")
                .setArguments(extension.name(), hook)
                .setThrowable(throwable));
        }
    }

    private static String hookOf(Object event) {
        if (event instanceof ServeEvent) {
            return ExtensionHook.SERVE_EVENT_LISTENER.label();
        } else if (event instanceof ExpectationChange) {
            return ExtensionHook.EXPECTATION_LIFECYCLE_LISTENER.label();
        }
        return ExtensionHook.SETTINGS_LISTENER.label();
    }

    private boolean isEnabled(Level level) {
        return mockServerLogger != null && mockServerLogger.isEnabledForInstance(level);
    }

    private static LogEntry entry(Level level) {
        return new LogEntry()
            .setType(SERVER_CONFIGURATION)
            .setLogLevel(level);
    }
}
