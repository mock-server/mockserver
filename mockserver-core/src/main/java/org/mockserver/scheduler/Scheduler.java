package org.mockserver.scheduler;

import org.mockserver.model.Delay;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
}
