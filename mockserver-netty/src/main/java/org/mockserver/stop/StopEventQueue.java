package org.mockserver.stop;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class StopEventQueue implements Stoppable {

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

    @Override
    public void stop() {
        synchronized (stoppables) {
            for (Stoppable stoppable : new ArrayList<Stoppable>(stoppables)) {
                unregister(stoppable);
                stoppable.stop();
            }
        }
    }

}
