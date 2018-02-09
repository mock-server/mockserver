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

    private List<MockServerMatcherListener> listeners = Collections.synchronizedList(new ArrayList<MockServerMatcherListener>());
    private final Scheduler scheduler;

    public MockServerMatcherNotifier(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    protected void notifyListeners(final MockServerMatcher notifier) {
        scheduler.submit(
            new Runnable() {
                public void run() {
                    for (MockServerMatcherListener listener : new ArrayList<>(listeners)) {
                        listener.updated(notifier);
                    }
                }
            });

    }

    public void registerListener(MockServerMatcherListener listener) {
        listeners.add(listener);
    }
}
