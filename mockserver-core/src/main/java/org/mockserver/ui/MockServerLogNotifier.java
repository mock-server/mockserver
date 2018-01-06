package org.mockserver.ui;

import org.mockserver.filters.MockServerLog;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author jamesdbloom
 */
public class MockServerLogNotifier extends ObjectWithReflectiveEqualsHashCodeToString {

    private List<MockServerLogListener> listeners = Collections.synchronizedList(new ArrayList<MockServerLogListener>());
    private ExecutorService listenerNotifyExecutor = Executors.newFixedThreadPool(3);

    protected void notifyListeners(final MockServerLog notifier) {
        listenerNotifyExecutor.submit(
            new Runnable() {
                public void run() {
                    for (MockServerLogListener listener : new ArrayList<>(listeners)) {
                        listener.updated(notifier);
                    }
                }
            }
        );

    }

    public void registerListener(MockServerLogListener listener) {
        listeners.add(listener);
    }
}
