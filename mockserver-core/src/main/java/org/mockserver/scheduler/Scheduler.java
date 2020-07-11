package org.mockserver.scheduler;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.client.SocketCommunicationException;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.HttpForwardActionResult;
import org.mockserver.model.BinaryMessage;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpResponse;
import org.slf4j.event.Level;

import java.util.concurrent.*;
import java.util.function.BiConsumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockserver.log.model.LogEntry.LogMessageType.WARN;

/**
 * @author jamesdbloom
 */
public class Scheduler {

    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(
        ConfigurationProperties.actionHandlerThreadCount(),
        new SchedulerThreadFactory("Scheduler"),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final boolean synchronous;

    public static class SchedulerThreadFactory implements ThreadFactory {

        private final String name;
        private static int threadInitNumber;

        public SchedulerThreadFactory(String name) {
            this.name = name;
        }

        @Override
        @SuppressWarnings("NullableProblems")
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "MockServer-" + name + threadInitNumber++);
            thread.setDaemon(true);
            return thread;
        }
    }

    private final MockServerLogger mockServerLogger;

    public Scheduler(MockServerLogger mockServerLogger) {
        this(mockServerLogger, false);
    }

    @VisibleForTesting
    public Scheduler(MockServerLogger mockServerLogger, boolean synchronous) {
        this.mockServerLogger = mockServerLogger;
        this.synchronous = synchronous;
    }

    public synchronized void shutdown() {
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(500, MILLISECONDS);
            } catch (InterruptedException ignore) {
                // ignore interrupted exception
            }
        }
    }

    private void run(Runnable command) {
        try {
            command.run();
        } catch (Throwable throwable) {
            if (MockServerLogger.isEnabled(Level.INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(WARN)
                        .setLogLevel(Level.INFO)
                        .setMessageFormat(throwable.getMessage())
                        .setThrowable(throwable)
                );
            }
        }
    }

    public void schedule(Runnable command, boolean synchronous, Delay... delays) {
        Delay delay = addDelays(delays);
        if (this.synchronous || synchronous) {
            if (delay != null) {
                delay.applyDelay();
            }
            run(command);
        } else {
            if (delay != null) {
                scheduler.schedule(() -> run(command), delay.getValue(), delay.getTimeUnit());
            } else {
                run(command);
            }
        }
    }

    private Delay addDelays(Delay... delays) {
        if (delays == null || delays.length == 0) {
            return null;
        } else if (delays.length == 1) {
            return delays[0];
        } else if (delays.length == 2 && delays[0] == delays[1]) {
            return delays[0];
        } else {
            long timeInMilliseconds = 0;
            for (Delay delay : delays) {
                if (delay != null) {
                    timeInMilliseconds += delay.getTimeUnit().toMillis(delay.getValue());
                }
            }
            return new Delay(MILLISECONDS, timeInMilliseconds);
        }
    }

    public void submit(Runnable command) {
        submit(command, false);
    }

    public void submit(Runnable command, boolean synchronous) {
        if (this.synchronous || synchronous) {
            run(command);
        } else {
            scheduler.submit(() -> run(command));
        }
    }

    public void submit(HttpForwardActionResult future, Runnable command, boolean synchronous) {
        if (future != null) {
            if (this.synchronous || synchronous) {
                try {
                    future.getHttpResponse().get(ConfigurationProperties.maxSocketTimeout(), MILLISECONDS);
                } catch (TimeoutException e) {
                    future.getHttpResponse().completeExceptionally(new SocketCommunicationException("Response was not received after " + ConfigurationProperties.maxSocketTimeout() + " milliseconds, to make the proxy wait longer please use \"mockserver.maxSocketTimeout\" system property or ConfigurationProperties.maxSocketTimeout(long milliseconds)", e.getCause()));
                } catch (InterruptedException | ExecutionException ex) {
                    future.getHttpResponse().completeExceptionally(ex);
                }
                run(command);
            } else {
                future.getHttpResponse().whenCompleteAsync((httpResponse, throwable) -> command.run(), scheduler);
            }
        }
    }

    public void submit(CompletableFuture<BinaryMessage> future, Runnable command, boolean synchronous) {
        if (future != null) {
            if (this.synchronous || synchronous) {
                try {
                    future.get(ConfigurationProperties.maxSocketTimeout(), MILLISECONDS);
                } catch (TimeoutException e) {
                    future.completeExceptionally(new SocketCommunicationException("Response was not received after " + ConfigurationProperties.maxSocketTimeout() + " milliseconds, to make the proxy wait longer please use \"mockserver.maxSocketTimeout\" system property or ConfigurationProperties.maxSocketTimeout(long milliseconds)", e.getCause()));
                } catch (InterruptedException | ExecutionException ex) {
                    future.completeExceptionally(ex);
                }
                run(command);
            } else {
                future.whenCompleteAsync((httpResponse, throwable) -> command.run(), scheduler);
            }
        }
    }

    public void submit(HttpForwardActionResult future, BiConsumer<HttpResponse, Throwable> consumer, boolean synchronous) {
        if (future != null) {
            if (this.synchronous || synchronous) {
                HttpResponse httpResponse = null;
                Throwable exception = null;
                try {
                    httpResponse = future.getHttpResponse().get(ConfigurationProperties.maxSocketTimeout(), MILLISECONDS);
                } catch (TimeoutException e) {
                    exception = new SocketCommunicationException("Response was not received after " + ConfigurationProperties.maxSocketTimeout() + " milliseconds, to make the proxy wait longer please use \"mockserver.maxSocketTimeout\" system property or ConfigurationProperties.maxSocketTimeout(long milliseconds)", e.getCause());
                } catch (InterruptedException | ExecutionException ex) {
                    exception = ex;
                }
                try {
                    consumer.accept(httpResponse, exception);
                } catch (Throwable throwable) {
                    if (MockServerLogger.isEnabled(Level.INFO)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(WARN)
                                .setLogLevel(Level.INFO)
                                .setMessageFormat(throwable.getMessage())
                                .setThrowable(throwable)
                        );
                    }
                }
            } else {
                future.getHttpResponse().whenCompleteAsync(consumer, scheduler);
            }
        }
    }

}
