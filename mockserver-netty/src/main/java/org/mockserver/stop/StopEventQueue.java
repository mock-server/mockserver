package org.mockserver.stop;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author jamesdbloom
 */
public class StopEventQueue {

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

}
