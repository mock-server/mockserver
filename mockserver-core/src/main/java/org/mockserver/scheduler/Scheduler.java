package org.mockserver.scheduler;

import org.mockserver.client.SocketCommunicationException;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.HttpForwardActionResult;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpResponse;
import org.slf4j.event.Level;

import java.util.concurrent.*;
import java.util.function.Consumer;

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
        this.mockServerLogger = mockServerLogger;
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
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(WARN)
                    .setLogLevel(Level.INFO)
                    .setMessageFormat(throwable.getMessage())
                    .setThrowable(throwable)
            );
        }
    }

    private <T> void run(T input, Consumer<T> command) {
        try {
            command.accept(input);
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(WARN)
                    .setLogLevel(Level.INFO)
                    .setMessageFormat(throwable.getMessage())
                    .setThrowable(throwable)
            );
        }
    }

    public void schedule(Runnable command, boolean synchronous, Delay... delays) {
        Delay delay = addDelays(delays);
        if (synchronous) {
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
        if (synchronous) {
            run(command);
        } else {
            scheduler.submit(() -> run(command));
        }
    }

    public void submit(HttpForwardActionResult future, Runnable command, boolean synchronous) {
        if (future != null) {
            if (synchronous) {
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

    public void submit(HttpForwardActionResult future, Consumer<HttpResponse> consumer, boolean synchronous) {
        if (future != null) {
            if (synchronous) {
                try {
                    run(future.getHttpResponse().get(ConfigurationProperties.maxSocketTimeout(), MILLISECONDS), consumer);
                } catch (TimeoutException e) {
                    future.getHttpResponse().completeExceptionally(new SocketCommunicationException("Response was not received after " + ConfigurationProperties.maxSocketTimeout() + " milliseconds, to make the proxy wait longer please use \"mockserver.maxSocketTimeout\" system property or ConfigurationProperties.maxSocketTimeout(long milliseconds)", e.getCause()));
                } catch (InterruptedException | ExecutionException ex) {
                    future.getHttpResponse().completeExceptionally(ex);
                }
            } else {
                future.getHttpResponse().whenCompleteAsync((httpResponse, throwable) -> consumer.accept(httpResponse), scheduler);
            }
        }
    }

}
