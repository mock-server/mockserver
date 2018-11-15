package org.mockserver.scheduler;

import org.mockserver.client.netty.SocketCommunicationException;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.mock.action.HttpForwardActionResult;
import org.mockserver.model.Delay;

import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * @author jamesdbloom
 */
public class Scheduler {

    private ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(poolSize(), new ThreadPoolExecutor.CallerRunsPolicy());

    private int poolSize() {
        return Math.max(2, Runtime.getRuntime().availableProcessors() * 2);
    }

    public synchronized void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    private synchronized ScheduledExecutorService getScheduler() {
        if (scheduler == null) {
            scheduler = new ScheduledThreadPoolExecutor(poolSize(), new ThreadPoolExecutor.CallerRunsPolicy());
        }
        return scheduler;
    }

    public void schedule(Runnable command, boolean synchronous, Delay... delays) {
        Delay delay = addDelays(delays);
        if (synchronous) {
            if (delay != null) {
                delay.applyDelay();
            }
            command.run();
        } else {
            if (delay != null) {
                getScheduler().schedule(command, delay.getValue(), delay.getTimeUnit());
            } else {
                command.run();
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
            command.run();
        } else {
            getScheduler().schedule(command, 0, NANOSECONDS);
        }
    }

    public void submit(HttpForwardActionResult future, Runnable command, boolean synchronous) {
        if (future != null) {
            if (synchronous) {
                try {
                    future.getHttpResponse().get(ConfigurationProperties.maxSocketTimeout(), MILLISECONDS);
                } catch (TimeoutException e) {
                    future.getHttpResponse().setException(new SocketCommunicationException("Response was not received after " + ConfigurationProperties.maxSocketTimeout() + " milliseconds, to make the proxy wait longer please use \"mockserver.maxSocketTimeout\" system property or ConfigurationProperties.maxSocketTimeout(long milliseconds)", e.getCause()));
                } catch (InterruptedException | ExecutionException ex) {
                    future.getHttpResponse().setException(ex);
                }
                command.run();
            } else {
                future.getHttpResponse().addListener(command, getScheduler());
            }
        }
    }

}
