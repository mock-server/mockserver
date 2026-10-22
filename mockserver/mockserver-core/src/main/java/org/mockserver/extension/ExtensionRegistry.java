package org.mockserver.extension;

import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause;
import org.mockserver.server.initialize.ExpectationInitializer;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;

/**
 * The extensions one server has loaded and started, in start order.
 * <p>
 * A server builds a registry only when {@link Configuration#extensionsConfigured()} is true, as the first thing it
 * does, so with no extensions configured no class in this package is loaded and nothing is scanned. Every call
 * into extension code ({@code start}, {@code onStarted}, {@code stop}) runs on a short-lived daemon thread and is
 * awaited for at most {@code extensionStartTimeoutMillis}.
 */
public final class ExtensionRegistry {

    private final List<LoadedExtension> extensions;
    private final MockServerLogger mockServerLogger;
    private final long timeoutMillis;
    private final ExtensionContext context;
    // guarded by this
    private final List<LoadedExtension> started = new ArrayList<>();
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean notifying = new AtomicBoolean(false);
    private volatile boolean startedNotified;

    private ExtensionRegistry(List<LoadedExtension> extensions, Configuration configuration, MockServerLogger mockServerLogger) {
        this.extensions = Collections.unmodifiableList(extensions);
        this.mockServerLogger = mockServerLogger;
        this.timeoutMillis = configuration.extensionStartTimeoutMillis();
        this.context = new ServerExtensionContext(configuration, mockServerLogger);
    }

    /**
     * Loads every configured extension and calls each one's {@code start} in order. If loading fails nothing is
     * started; if a {@code start} throws or times out, the extensions already started are stopped in reverse order.
     *
     * @throws ExtensionException when start-up must be refused
     */
    public static ExtensionRegistry start(Configuration configuration, MockServerLogger mockServerLogger) {
        ExtensionRegistry registry = new ExtensionRegistry(ExtensionLoader.load(configuration), configuration, mockServerLogger);
        registry.logLoaded();
        registry.startAll();
        return registry;
    }

    public List<LoadedExtension> extensions() {
        return extensions;
    }

    /**
     * Disables a hook on every loaded extension that implements it, logging a WARN with the reason for each; the
     * extensions stay loaded and their other hooks are unaffected.
     *
     * @return the number of extensions whose hook was disabled by this call
     */
    public int disableHook(ExtensionHook hook, String reason) {
        int disabled = 0;
        for (LoadedExtension extension : extensions) {
            if (extension.disable(hook, reason)) {
                disabled++;
                if (isEnabled(Level.WARN)) {
                    mockServerLogger.logEvent(entry(Level.WARN)
                        .setMessageFormat("extension:{}is loaded but its hook:{}is disabled because:{}")
                        .setArguments(extension.name(), hook, reason));
                }
            }
        }
        return disabled;
    }

    /**
     * Loads the expectations of every started extension that implements {@link ExpectationInitializer}, replacing
     * any it loaded before. A throw refuses start-up.
     */
    public void initializeExpectations(RequestMatchers requestMatchers) {
        for (LoadedExtension extension : startedExtensions()) {
            if (extension.isHookEnabled(ExtensionHook.EXPECTATION_INITIALIZER)) {
                Expectation[] expectations;
                try {
                    expectations = ((ExpectationInitializer) extension.extension()).initializeExpectations();
                } catch (RuntimeException e) {
                    throw new ExtensionException("extension " + extension.name() + " threw while initialising expectations: " + e, e);
                }
                requestMatchers.update(expectations != null ? expectations : new Expectation[0], new Cause("extension:" + extension.name(), Cause.Type.CLASS_INITIALISER));
            }
        }
    }

    /**
     * Calls each started extension's {@code onStarted} in order; afterwards {@link #isStartedNotified()} is true.
     *
     * @throws ExtensionException when one throws or times out; the caller stops the server
     */
    public void notifyStarted(List<Integer> ports) {
        if (!notifying.compareAndSet(false, true)) {
            return;
        }
        List<Integer> unmodifiablePorts = Collections.unmodifiableList(new ArrayList<>(ports));
        for (LoadedExtension extension : startedExtensions()) {
            if (stopped.get()) {
                // the server is stopping (such as PUT /mockserver/stop during start-up), so it never becomes ready
                return;
            }
            try {
                callBounded("onStarted", extension, () -> extension.extension().onStarted(unmodifiablePorts));
            } catch (TimeoutException e) {
                throw new ExtensionException("extension " + extension.name() + " did not return from onStarted within " + timeoutMillis + "ms (extensionStartTimeoutMillis)");
            } catch (Throwable throwable) {
                throw new ExtensionException("extension " + extension.name() + " failed in onStarted: " + throwable, throwable);
            }
        }
        startedNotified = true;
    }

    /**
     * @return the longest {@link #stop()} can take: one {@code extensionStartTimeoutMillis} per started extension
     */
    public long maximumStopMillis() {
        return startedExtensions().size() * timeoutMillis;
    }

    /**
     * @return true once every extension's {@code onStarted} has returned
     */
    public boolean isStartedNotified() {
        return startedNotified;
    }

    /**
     * Calls {@code stop} on every started extension in reverse start order. Safe to call more than once.
     */
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        List<LoadedExtension> toStop = startedExtensions();
        Collections.reverse(toStop);
        // an interrupted caller (such as a refused start) must still wait for each stop
        boolean interrupted = Thread.interrupted();
        try {
            stopEach(toStop);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void stopEach(List<LoadedExtension> toStop) {
        for (LoadedExtension extension : toStop) {
            try {
                callBounded("stop", extension, () -> extension.extension().stop());
            } catch (TimeoutException e) {
                if (isEnabled(Level.WARN)) {
                    mockServerLogger.logEvent(entry(Level.WARN)
                        .setMessageFormat("extension:{}did not return from stop within extensionStartTimeoutMillis:{}its thread may still be running")
                        .setArguments(extension.name(), timeoutMillis));
                }
            } catch (Throwable throwable) {
                if (isEnabled(Level.WARN)) {
                    mockServerLogger.logEvent(entry(Level.WARN)
                        .setMessageFormat("extension:{}failed to stop")
                        .setArguments(extension.name())
                        .setThrowable(throwable));
                }
            }
        }
    }

    private void startAll() {
        for (LoadedExtension extension : extensions) {
            try {
                callBounded("start", extension, () -> extension.extension().start(context));
                synchronized (this) {
                    started.add(extension);
                }
            } catch (TimeoutException e) {
                if (isEnabled(Level.WARN)) {
                    mockServerLogger.logEvent(entry(Level.WARN)
                        .setMessageFormat("extension:{}did not return from start within extensionStartTimeoutMillis:{}so it is treated as not started and will not be stopped; its thread may still be running")
                        .setArguments(extension.name(), timeoutMillis));
                }
                stop();
                throw new ExtensionException("extension " + extension.name() + " did not return from start within " + timeoutMillis + "ms (extensionStartTimeoutMillis)");
            } catch (Throwable throwable) {
                stop();
                throw new ExtensionException("extension " + extension.name() + " failed to start: " + throwable, throwable);
            }
        }
    }

    private synchronized List<LoadedExtension> startedExtensions() {
        return new ArrayList<>(started);
    }

    private interface ExtensionCall {
        void call() throws Exception;
    }

    private void callBounded(String phase, LoadedExtension extension, ExtensionCall call) throws Throwable {
        FutureTask<Void> task = new FutureTask<>(() -> {
            call.call();
            return null;
        });
        Thread thread = new Thread(task, "MockServer-ext-" + phase + "-" + extension.name());
        thread.setDaemon(true);
        thread.setContextClassLoader(Thread.currentThread().getContextClassLoader());
        thread.start();
        try {
            task.get(timeoutMillis, MILLISECONDS);
        } catch (TimeoutException e) {
            thread.interrupt();
            throw e;
        } catch (InterruptedException e) {
            thread.interrupt();
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private void logLoaded() {
        for (LoadedExtension extension : extensions) {
            if (isEnabled(Level.INFO)) {
                mockServerLogger.logEvent(entry(Level.INFO)
                    .setMessageFormat("loaded extension:{}class:{}from:{}with hooks:{}")
                    .setArguments(extension.name(), extension.className(), extension.source(), extension.hooks()));
            }
        }
    }

    private boolean isEnabled(Level level) {
        return mockServerLogger != null && mockServerLogger.isEnabledForInstance(level);
    }

    private static LogEntry entry(Level level) {
        return new LogEntry()
            .setType(SERVER_CONFIGURATION)
            .setLogLevel(level);
    }

    private static final class ServerExtensionContext implements ExtensionContext {
        private final Configuration configuration;
        private final MockServerLogger mockServerLogger;

        private ServerExtensionContext(Configuration configuration, MockServerLogger mockServerLogger) {
            this.configuration = configuration;
            this.mockServerLogger = mockServerLogger;
        }

        @Override
        public Configuration configuration() {
            return configuration;
        }

        @Override
        public MockServerLogger logger() {
            return mockServerLogger;
        }
    }
}
