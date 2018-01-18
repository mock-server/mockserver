package org.mockserver.scheduler;

import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.client.netty.SocketCommunicationException;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpResponse;

import java.util.concurrent.*;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

/**
 * @author jamesdbloom
 */
public class Scheduler {

    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), new ThreadPoolExecutor.CallerRunsPolicy());

    public static void schedule(Runnable command, Delay delay) {
        schedule(command, delay, false);
    }

    public static void schedule(Runnable command, Delay delay, boolean synchronous) {
        if (synchronous) {
            if (delay != null) {
                delay.applyDelay();
            }
            command.run();
        } else {
            if (delay != null) {
                scheduler.schedule(command, delay.getValue(), delay.getTimeUnit());
            } else {
                command.run();
            }
        }
    }

    public static void submit(Runnable command) {
        submit(command, false);
    }

    public static void submit(Runnable command, boolean synchronous) {
        if (synchronous) {
            command.run();
        } else {
            scheduler.schedule(command, 0, TimeUnit.NANOSECONDS);
        }
    }

    public static void submit(SettableFuture<HttpResponse> future, Runnable command, boolean synchronous) {
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
            future.addListener(command, scheduler);
        }
    }
}
