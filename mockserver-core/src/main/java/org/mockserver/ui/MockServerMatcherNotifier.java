package org.mockserver.ui;

import org.mockserver.mock.MockServerMatcher;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockserver.scheduler.Scheduler.submit;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherNotifier extends ObjectWithReflectiveEqualsHashCodeToString {

    private List<MockServerMatcherListener> listeners = Collections.synchronizedList(new ArrayList<MockServerMatcherListener>());

    protected void notifyListeners(final MockServerMatcher notifier) {
        submit(
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
