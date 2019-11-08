package org.mockserver.ui;

import org.mockserver.log.MockServerEventLog;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class MockServerEventLogNotifier extends ObjectWithReflectiveEqualsHashCodeToString {

    private boolean listenerAdded = false;
    private final List<MockServerLogListener> listeners = Collections.synchronizedList(new ArrayList<>());
    private final Scheduler scheduler;

    public MockServerEventLogNotifier(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    protected void notifyListeners(final MockServerEventLog notifier) {
        if (listenerAdded && !listeners.isEmpty()) {
            scheduler.submit(() -> {
                for (MockServerLogListener listener : listeners.toArray(new MockServerLogListener[0])) {
                    listener.updated(notifier);
                }
            });
        }
    }

    public void registerListener(MockServerLogListener listener) {
        listeners.add(listener);
        listenerAdded = true;
    }

    public void unregisterListener(MockServerLogListener listener) {
        listeners.remove(listener);
    }
}
