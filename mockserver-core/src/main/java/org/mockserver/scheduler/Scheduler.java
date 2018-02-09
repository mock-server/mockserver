package org.mockserver.scheduler;

import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.client.netty.SocketCommunicationException;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpResponse;

import java.util.concurrent.*;

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

    public void schedule(Runnable command, Delay delay) {
        schedule(command, delay, false);
    }

    public void schedule(Runnable command, Delay delay, boolean synchronous) {
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

    public void submit(Runnable command) {
        submit(command, false);
    }

    public void submit(Runnable command, boolean synchronous) {
        if (synchronous) {
            command.run();
        } else {
            getScheduler().schedule(command, 0, TimeUnit.NANOSECONDS);
        }
    }

    public void submit(SettableFuture<HttpResponse> future, Runnable command, boolean synchronous) {
        if (future != null) {
            if (synchronous) {
                try {
                    future.get(ConfigurationProperties.maxSocketTimeout(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.setException(new SocketCommunicationException("Response was not received after " + ConfigurationProperties.maxSocketTimeout() + " milliseconds, to make the proxy wait longer please use \"mockserver.maxSocketTimeout\" system property or ConfigurationProperties.maxSocketTimeout(long milliseconds)", e.getCause()));
                } catch (InterruptedException | ExecutionException ex) {
                    future.setException(ex);
                }
                command.run();
            } else {
                future.addListener(command, getScheduler());
            }
        }
    }
}
