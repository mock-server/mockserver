package org.mockserver.extension;

import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.server.initialize.ExpectationInitializer;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final long listenerFlushMillis;
    // null until every extension has started, and null afterwards when none is a listener
    private volatile ListenerDispatch listenerDispatch;
    // guarded by this
    private final List<LoadedExtension> started = new ArrayList<>();
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean notifying = new AtomicBoolean(false);
    private volatile boolean startedNotified;
    // null unless a started extension offers template helpers and the hook is enabled
    private volatile TemplateHelpers templateHelpers;
    // the stub this registry put on the configuration, removed again when it stops
    private volatile Configuration stubbedConfiguration;
    private volatile Object templateHelperStub;

    private ExtensionRegistry(List<LoadedExtension> extensions, Configuration configuration, MockServerLogger mockServerLogger) {
        this.extensions = Collections.unmodifiableList(extensions);
        this.mockServerLogger = mockServerLogger;
        this.timeoutMillis = configuration.extensionStartTimeoutMillis();
        this.context = new ServerExtensionContext(configuration, mockServerLogger);
        this.listenerFlushMillis = configuration.stopDrainMillis();
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
        registry.listenerDispatch = ListenerDispatch.create(registry.startedExtensions(), configuration, mockServerLogger);
        registry.prepareTemplateHelpers(configuration);
        return registry;
    }

    static final String HELPERS_NOT_AVAILABLE_HERE = "extension template helpers ($ext) can only be called from an httpResponseTemplate or httpForwardTemplate,"
        + " not from a webhook, secondary action, delay, file body, stream, load scenario or template dry run";

    public List<LoadedExtension> extensions() {
        return extensions;
    }

    /**
     * @return the {@code ext} template namespace and its executor, or {@code null} when no started extension offers
     * template helpers or the hook is disabled
     */
    public TemplateHelpers templateHelpers() {
        return templateHelpers;
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
     * Builds the routes of every started extension that implements {@link AdminApiExtension}, or returns
     * {@code null} when none does, so a server with no such extension routes nothing and starts no thread.
     *
     * @param gate the control-plane authentication, authorization and audit decision each call must pass
     */
    public ExtensionAdminRoutes createAdminRoutes(Configuration configuration, Scheduler scheduler, ExtensionAdminRoutes.ControlPlaneGate gate) {
        List<LoadedExtension> adminExtensions = new ArrayList<>();
        for (LoadedExtension extension : startedExtensions()) {
            if (extension.isHookEnabled(ExtensionHook.ADMIN_API)) {
                adminExtensions.add(extension);
            }
        }
        if (adminExtensions.isEmpty()) {
            return null;
        }
        ExtensionAdminRoutes routes = new ExtensionAdminRoutes(adminExtensions, configuration, mockServerLogger, scheduler, gate);
        if (isEnabled(Level.INFO)) {
            mockServerLogger.logEvent(entry(Level.INFO)
                .setMessageFormat("serving extension admin routes under:{}for:{}with at most:{}calls in flight")
                .setArguments(ExtensionAdminRoutes.PATH, routes.names(), configuration.extensionAdminMaxInFlight()));
        }
        return routes;
    }

    /**
     * @return the dispatch for {@link ServeEventListener}s, or {@code null} when no started extension is one
     */
    public ListenerDispatch serveEventDispatch() {
        ListenerDispatch dispatch = listenerDispatch;
        return dispatch != null && dispatch.hasServeEventListeners() ? dispatch : null;
    }

    /**
     * @return the dispatch for {@link ExpectationLifecycleListener}s, or {@code null} when no started extension is
     * one
     */
    public ListenerDispatch lifecycleDispatch() {
        ListenerDispatch dispatch = listenerDispatch;
        return dispatch != null && dispatch.hasLifecycleListeners() ? dispatch : null;
    }

    /**
     * Queues the updated configuration for every {@link SettingsListener}, without waiting.
     */
    public void notifySettingsUpdated(Configuration configuration) {
        ListenerDispatch dispatch = listenerDispatch;
        if (dispatch != null && dispatch.hasSettingsListeners()) {
            dispatch.settingsUpdated(configuration);
        }
    }

    ListenerDispatch listenerDispatch() {
        return listenerDispatch;
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
     * @return the longest {@link #stop()} can take: the listener flush ({@code stopDrainMillis}) when any extension is
     * a listener, plus one {@code extensionStartTimeoutMillis} per started extension
     */
    public long maximumStopMillis() {
        return (listenerDispatch != null ? listenerFlushMillis : 0L) + startedExtensions().size() * timeoutMillis;
    }

    /**
     * @return true once every extension's {@code onStarted} has returned
     */
    public boolean isStartedNotified() {
        return startedNotified;
    }

    /**
     * Delivers what the listener queues still hold (for at most {@code stopDrainMillis}; anything left is dropped and
     * counted), calls {@code stop} on every started extension in reverse start order, then ends the listener
     * threads. Safe to call more than once.
     */
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        TemplateHelpers helpers = templateHelpers;
        if (helpers != null) {
            helpers.shutdown();
        }
        clearStub();
        List<LoadedExtension> toStop = startedExtensions();
        Collections.reverse(toStop);
        // an interrupted caller (such as a refused start) must still wait for each stop
        boolean interrupted = Thread.interrupted();
        ListenerDispatch dispatch = listenerDispatch;
        try {
            if (dispatch != null) {
                dispatch.flush(listenerFlushMillis);
            }
            stopEach(toStop);
        } finally {
            if (dispatch != null) {
                dispatch.shutdown();
            }
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

    /**
     * Collects each started provider's helpers and builds the {@code ext} namespace. Every other template engine of
     * this configuration binds {@code ext} to a stub that fails with the reason helpers cannot be called there; with
     * HTTP/3 enabled the hook is disabled and every engine, response and forward templates included, gets the stub.
     */
    private void prepareTemplateHelpers(Configuration configuration) {
        List<LoadedExtension> providers = new ArrayList<>();
        for (LoadedExtension extension : startedExtensions()) {
            if (extension.isHookEnabled(ExtensionHook.TEMPLATE_HELPER)) {
                providers.add(extension);
            }
        }
        if (providers.isEmpty()) {
            // a configuration reused from a server that had template helpers must not keep its stub
            configuration.extensionTemplateHelperStub(null);
            return;
        }
        Integer http3Port = configuration.http3Port();
        if (http3Port != null && http3Port > 0) {
            String reason = "HTTP/3 is enabled (http3Port " + http3Port + ") and HTTP/3 renders templates on its event loop, where waiting for a helper would block other requests";
            disableHook(ExtensionHook.TEMPLATE_HELPER, reason);
            setStub(configuration, ExtensionHelperNamespace.unavailable("extension template helpers ($ext) are disabled because " + reason));
            return;
        }
        Map<String, Map<Class<?>, Object>> helpers = new LinkedHashMap<>();
        Map<String, Map<String, MustacheHelper>> mustacheHelpers = new LinkedHashMap<>();
        for (LoadedExtension extension : providers) {
            TemplateHelperProvider provider = (TemplateHelperProvider) extension.extension();
            Object[] offered = new Object[2];
            try {
                callBounded("helpers", extension, () -> {
                    offered[0] = provider.helpers();
                    offered[1] = provider.mustacheHelpers();
                });
            } catch (TimeoutException e) {
                stop();
                throw new ExtensionException("extension " + extension.name() + " did not return its template helpers within " + timeoutMillis + "ms (extensionStartTimeoutMillis)");
            } catch (Throwable throwable) {
                stop();
                throw new ExtensionException("extension " + extension.name() + " failed to return its template helpers: " + throwable, throwable);
            }
            @SuppressWarnings("unchecked")
            Map<Class<?>, Object> offeredHelpers = offered[0] != null ? new LinkedHashMap<>((Map<Class<?>, Object>) offered[0]) : Map.of();
            @SuppressWarnings("unchecked")
            Map<String, MustacheHelper> offeredMustacheHelpers = offered[1] != null ? new LinkedHashMap<>((Map<String, MustacheHelper>) offered[1]) : Map.of();
            helpers.put(extension.name(), offeredHelpers);
            mustacheHelpers.put(extension.name(), offeredMustacheHelpers);
        }
        try {
            templateHelpers = TemplateHelpers.create(mockServerLogger, configuration.extensionHelperTimeoutMillis(), configuration.extensionHelperMaxInFlight(), helpers, mustacheHelpers);
        } catch (ExtensionException e) {
            stop();
            throw e;
        } catch (RuntimeException e) {
            stop();
            throw new ExtensionException("the extension template helpers are not valid: " + e, e);
        }
        setStub(configuration, ExtensionHelperNamespace.unavailable(HELPERS_NOT_AVAILABLE_HERE));
    }

    private void setStub(Configuration configuration, Object stub) {
        stubbedConfiguration = configuration;
        templateHelperStub = stub;
        configuration.extensionTemplateHelperStub(stub);
    }

    private void clearStub() {
        Configuration configuration = stubbedConfiguration;
        // only if no later server on the same configuration has replaced it
        if (configuration != null && configuration.extensionTemplateHelperStub() == templateHelperStub) {
            configuration.extensionTemplateHelperStub(null);
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
