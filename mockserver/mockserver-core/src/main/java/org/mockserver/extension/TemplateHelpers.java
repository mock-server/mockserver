package org.mockserver.extension;

import com.samskivert.mustache.Mustache;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.ThreadExecutorMap;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.slf4j.event.Level;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * The {@code ext} template namespace of one server and the executor its helper calls run on. Built only when a
 * started extension implements {@link TemplateHelperProvider} and the hook is enabled.
 * <p>
 * Every helper call is handed to a cached executor behind an admission cap of {@code extensionHelperMaxInFlight},
 * and the rendering thread waits for it for no longer than what is left of the render's budget: the first call in a
 * render starts a deadline of {@code extensionHelperTimeoutMillis}, and {@link #endRender()} clears it. A slot is
 * held until the helper's thread really returns, so a helper that ignores its interrupt cannot add threads.
 */
public final class TemplateHelpers {

    private static final Pattern MUSTACHE_KEY = Pattern.compile("[A-Za-z0-9_-]+");
    // the render budget's deadline in System.nanoTime() terms, set by the render's first helper call
    private static final ThreadLocal<long[]> RENDER_DEADLINE = new ThreadLocal<>();

    private final long timeoutMillis;
    private final int maxInFlight;
    private final Semaphore slots;
    private final ThreadPoolExecutor executor;
    private final ExtensionHelperNamespace namespace;
    private final ExtensionHelperNamespace mustacheNamespace;
    private final MockServerLogger mockServerLogger;

    private TemplateHelpers(MockServerLogger mockServerLogger, long timeoutMillis, int maxInFlight, ClassLoader contextClassLoader, Map<String, Map<Class<?>, Object>> helpers, Map<String, Map<String, MustacheHelper>> mustacheHelpers) {
        this.mockServerLogger = mockServerLogger;
        this.timeoutMillis = timeoutMillis;
        this.maxInFlight = maxInFlight;
        this.slots = new Semaphore(maxInFlight);
        AtomicInteger threadNumber = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, SECONDS, new SynchronousQueue<>(), runnable -> {
            Thread thread = new HelperThread(runnable, "MockServer-ext-helper-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            thread.setContextClassLoader(contextClassLoader);
            return thread;
        });
        Map<String, Object> byExtension = new LinkedHashMap<>();
        helpers.forEach((extensionName, interfaces) -> {
            Map<String, Object> proxies = new LinkedHashMap<>();
            interfaces.forEach((type, implementation) -> proxies.put(type.getSimpleName(), proxy(extensionName, type, implementation)));
            byExtension.put(extensionName, ExtensionHelperNamespace.of("ext." + extensionName, proxies));
        });
        this.namespace = ExtensionHelperNamespace.of("ext", byExtension);
        Map<String, Object> mustacheByExtension = new LinkedHashMap<>();
        mustacheHelpers.forEach((extensionName, sections) -> {
            Map<String, Object> lambdas = new LinkedHashMap<>();
            sections.forEach((key, helper) -> lambdas.put(key, lambda(extensionName + "." + key, helper)));
            mustacheByExtension.put(extensionName, ExtensionHelperNamespace.of("ext." + extensionName, lambdas));
        });
        this.mustacheNamespace = ExtensionHelperNamespace.of("ext", mustacheByExtension);
    }

    /**
     * Validates and wraps each provider's helpers. {@code helpers} and {@code mustacheHelpers} have already been
     * called, bounded, on start-up threads.
     *
     * @throws ExtensionException when a helper is not a public interface with an implementation of it, or a key is
     *                            not valid
     */
    static TemplateHelpers create(MockServerLogger mockServerLogger, long timeoutMillis, int maxInFlight, Map<String, Map<Class<?>, Object>> helpers, Map<String, Map<String, MustacheHelper>> mustacheHelpers) {
        helpers.forEach((extensionName, interfaces) -> {
            Map<String, Class<?>> bySimpleName = new LinkedHashMap<>();
            interfaces.forEach((type, implementation) -> {
                if (type == null || !type.isInterface() || !Modifier.isPublic(type.getModifiers())) {
                    throw new ExtensionException("extension " + extensionName + " offers the template helper " + (type == null ? "null" : type.getName()) + ", which is not a public interface; each helpers() key must be a public interface");
                }
                if (!type.isInstance(implementation)) {
                    throw new ExtensionException("extension " + extensionName + " offers " + (implementation == null ? "null" : implementation.getClass().getName()) + " as the template helper " + type.getName() + ", which it does not implement");
                }
                Class<?> previous = bySimpleName.put(type.getSimpleName(), type);
                if (previous != null) {
                    throw new ExtensionException("extension " + extensionName + " offers the template helpers " + previous.getName() + " and " + type.getName() + ", which both would be ext." + extensionName + "." + type.getSimpleName());
                }
            });
        });
        mustacheHelpers.forEach((extensionName, sections) -> sections.forEach((key, helper) -> {
            if (key == null || !MUSTACHE_KEY.matcher(key).matches()) {
                throw new ExtensionException("extension " + extensionName + " offers the Mustache helper \"" + key + "\"; a key must be letters, digits, '_' or '-'");
            }
            if (helper == null) {
                throw new ExtensionException("extension " + extensionName + " offers a null Mustache helper for \"" + key + "\"");
            }
        }));
        return new TemplateHelpers(mockServerLogger, Math.max(1L, timeoutMillis), Math.max(1, maxInFlight), Thread.currentThread().getContextClassLoader(), helpers, mustacheHelpers);
    }

    /**
     * @return {@code ext} for Velocity and JavaScript templates
     */
    public Object namespace() {
        return namespace;
    }

    /**
     * @return {@code ext} for Mustache templates
     */
    public Object mustacheNamespace() {
        return mustacheNamespace;
    }

    /**
     * Ends the current thread's render: the next helper call on this thread starts a new budget. Called by the
     * response and forward template handlers after each render.
     */
    public static void endRender() {
        RENDER_DEADLINE.remove();
    }

    /**
     * @return the helper calls running now
     */
    public int inFlight() {
        return maxInFlight - slots.availablePermits();
    }

    void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Runs {@code call} on a helper thread and waits for it, within the current render's budget.
     */
    <T> T call(String helper, Callable<T> call) {
        Thread caller = Thread.currentThread();
        if (caller instanceof HelperThread) {
            throw new ExtensionHelperException("extension template helper " + helper + " was called from inside another helper; a helper cannot call another helper");
        }
        if (onEventLoop()) {
            throw new ExtensionHelperException("extension template helper " + helper + " was called on an event loop thread (" + caller.getName() + "), where waiting for it would block other requests");
        }
        long now = System.nanoTime();
        long[] deadline = RENDER_DEADLINE.get();
        if (deadline == null) {
            deadline = new long[]{now + MILLISECONDS.toNanos(timeoutMillis)};
            RENDER_DEADLINE.set(deadline);
        }
        long remainingNanos = deadline[0] - now;
        if (remainingNanos <= 0) {
            throw new ExtensionHelperException("extension template helper " + helper + " was not called because the helpers in this template already used the render's budget of " + timeoutMillis + "ms (mockserver.extensionHelperTimeoutMillis)");
        }
        if (!slots.tryAcquire()) {
            throw new ExtensionHelperException("extension template helper " + helper + " was not called because " + maxInFlight + " helper calls are already running (mockserver.extensionHelperMaxInFlight)");
        }
        Integer port = HttpState.getPort();
        FutureTask<T> task = new FutureTask<>(() -> {
            HttpState.setPort(port);
            try {
                return call.call();
            } finally {
                HttpState.setPort((Integer) null);
            }
        });
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    slots.release();
                }
            });
        } catch (RejectedExecutionException stopping) {
            slots.release();
            throw new ExtensionHelperException("extension template helper " + helper + " was not called because the server is stopping");
        }
        try {
            return task.get(remainingNanos, NANOSECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            throw new ExtensionHelperException("extension template helper " + helper + " did not return within the render's budget of " + timeoutMillis + "ms (mockserver.extensionHelperTimeoutMillis), so it was interrupted");
        } catch (InterruptedException e) {
            task.cancel(true);
            caller.interrupt();
            throw new ExtensionHelperException("interrupted while waiting for extension template helper " + helper);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            // the template failure's message can reach the client, so it names only the exception type
            if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(Level.WARN)) {
                mockServerLogger.logEvent(new LogEntry()
                    .setLogLevel(Level.WARN)
                    .setMessageFormat("extension template helper:{}threw")
                    .setArguments(helper)
                    .setThrowable(cause));
            }
            throw new ExtensionHelperException("extension template helper " + helper + " failed with " + cause.getClass().getSimpleName() + "; the MockServer log has the details", cause);
        }
    }

    private static boolean onEventLoop() {
        EventExecutor executor = ThreadExecutorMap.currentExecutor();
        return executor instanceof EventLoop;
    }

    private Object proxy(String extensionName, Class<?> type, Object implementation) {
        String label = "ext." + extensionName + "." + type.getSimpleName();
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, arguments, label);
            }
            return call(label + "." + method.getName(), () -> invoke(method, implementation, arguments));
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object invoke(Method method, Object implementation, Object[] arguments) throws Exception {
        try {
            return method.invoke(implementation, arguments);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    private static Object objectMethod(Object proxy, Method method, Object[] arguments, String label) {
        switch (method.getName()) {
            case "equals":
                return proxy == arguments[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            default:
                return label;
        }
    }

    private Mustache.Lambda lambda(String label, MustacheHelper helper) {
        return (fragment, out) -> {
            // rendered here, on the render thread, so the helper only ever sees text and never writes to out
            String section = fragment.execute();
            String result = call("ext." + label, () -> helper.apply(section));
            if (result != null) {
                out.write(result);
            }
        };
    }

    private static final class HelperThread extends Thread {
        private HelperThread(Runnable runnable, String name) {
            super(runnable, name);
        }
    }
}
