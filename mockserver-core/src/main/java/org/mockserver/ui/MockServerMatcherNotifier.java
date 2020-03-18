package org.mockserver.ui;

import org.mockserver.mock.MockServerMatcher;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherNotifier extends ObjectWithReflectiveEqualsHashCodeToString {

    private boolean listenerAdded = false;
    private final List<MockServerMatcherListener> listeners = Collections.synchronizedList(new ArrayList<>());
    private final Scheduler scheduler;

    public MockServerMatcherNotifier(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    protected void notifyListeners(final MockServerMatcher notifier, Cause cause) {
        if (listenerAdded && !listeners.isEmpty()) {
            for (MockServerMatcherListener listener : listeners.toArray(new MockServerMatcherListener[0])) {
                scheduler.submit(() -> listener.updated(notifier, cause));
            }
        }
    }

    public void registerListener(MockServerMatcherListener listener) {
        listeners.add(listener);
        listenerAdded = true;
    }

    public void unregisterListener(MockServerMatcherListener listener) {
        listeners.remove(listener);
    }

    public enum Cause {
        FILE_WATCHER,
        INITIALISER,
        API
    }
}
