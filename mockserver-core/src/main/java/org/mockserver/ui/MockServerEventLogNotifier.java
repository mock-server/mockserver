package org.mockserver.ui;

import org.mockserver.filters.MockServerEventLog;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class MockServerEventLogNotifier extends ObjectWithReflectiveEqualsHashCodeToString {

    private final List<MockServerLogListener> listeners = Collections.synchronizedList(new ArrayList<MockServerLogListener>());
    private final Scheduler scheduler;

    public MockServerEventLogNotifier(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    protected void notifyListeners(final MockServerEventLog notifier) {
        scheduler.submit(
            new Runnable() {
                public void run() {
                    for (MockServerLogListener listener : new ArrayList<>(listeners)) {
                        listener.updated(notifier);
                    }
                }
            });

    }

    public void registerListener(MockServerLogListener listener) {
        listeners.add(listener);
    }

    public void unregisterListener(MockServerLogListener listener) {
        listeners.remove(listener);
    }
}
