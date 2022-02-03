package org.mockserver.mock.listeners;

import org.mockserver.mock.RequestMatchers;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    protected void notifyListeners(final RequestMatchers notifier, Cause cause) {
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

    public static class Cause {
        public Cause(String source, Type type) {
            this.source = source;
            this.type = type;
        }

        public static final Cause API = new Cause("", Type.API);

        public enum Type {
            FILE_WATCHER,
            FILE_INITIALISER,
            CLASS_INITIALISER,
            API
        }

        private final String source;
        private final Type type;

        public String getSource() {
            return source;
        }

        public Type getType() {
            return type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Cause cause = (Cause) o;
            return Objects.equals(source, cause.source) && type == cause.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, type);
        }
    }
}
