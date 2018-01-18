package org.mockserver.ui;

import org.mockserver.filters.MockServerEventLog;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockserver.scheduler.Scheduler.submit;

/**
 * @author jamesdbloom
 */
public class MockServerEventLogNotifier extends ObjectWithReflectiveEqualsHashCodeToString {

    private List<MockServerLogListener> listeners = Collections.synchronizedList(new ArrayList<MockServerLogListener>());

    protected void notifyListeners(final MockServerEventLog notifier) {
        submit(
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
}
