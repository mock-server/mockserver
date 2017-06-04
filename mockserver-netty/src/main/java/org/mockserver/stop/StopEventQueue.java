package org.mockserver.stop;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class StopEventQueue {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @VisibleForTesting
    protected final List<Stoppable> stoppables = new ArrayList<Stoppable>();

    public void register(Stoppable stoppable) {
        synchronized (stoppables) {
            stoppables.add(stoppable);
        }
    }

    public void unregister(Stoppable stoppable) {
        synchronized (stoppables) {
            stoppables.remove(stoppable);
        }
    }

    public void clear() {
        synchronized (stoppables) {
            stoppables.clear();
        }
    }

    public Future<?> stopOthers(Stoppable currentStoppable) {
        unregister(currentStoppable);
        SettableFuture<String> stopped = SettableFuture.<String>create();
        try {
            synchronized (stoppables) {
                for (Stoppable stoppable : new ArrayList<Stoppable>(stoppables)) {
                    Future future = stoppable.stop();
                    if (future != null) {
                        future.get();
                    }
                    unregister(stoppable);
                }
            }
            stopped.set("stopped");
        } catch (Exception e) {
            stopped.setException(e);
        }
        return stopped;
    }

    public Future<?> stop(Stoppable currentStoppable, SettableFuture<String> stopping, EventLoopGroup bossGroup, EventLoopGroup workerGroup, List<Channel> channels) {
        try {
            for (Channel channel : new ArrayList<Channel>(channels)) {
                channel.close().sync();
            }
            bossGroup.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS).sync();
            workerGroup.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS).sync();
            TimeUnit.MILLISECONDS.sleep(500);
            stopOthers(currentStoppable).get();
            stopping.set("stopped");
        } catch (Exception ie) {
            logger.trace("Exception while stopping " + currentStoppable.getClass().getName(), ie);
            stopping.setException(ie);
        }
        return stopping;
    }

}
