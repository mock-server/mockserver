package org.mockserver.ui;

import com.google.common.collect.ImmutableList;
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

    protected void notifyListeners(final MockServerMatcher notifier) {
        if (listenerAdded && !listeners.isEmpty()) {
            scheduler.submit(() -> {
                for (MockServerMatcherListener listener : ImmutableList.copyOf(listeners)) {
                    listener.updated(notifier);
                }
            });
        }
    }

    public void registerListener(MockServerMatcherListener listener) {
        listeners.add(listener);
        listenerAdded = true;
    }

    public void unregisterListener(MockServerMatcherListener listener) {
        listeners.remove(listener);
    }
}
