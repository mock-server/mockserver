package org.mockserver.client;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

/**
 * A publish/subscribe communication channel between {@link MockServerClient} and {@link ForwardChainExpectation} instances
 *
 * @author albans
 */
final class MockServerEventBus {
    private final Multimap<EventType, SubscriberHandler> subscribers = LinkedListMultimap.create();

    void publish(EventType event) {
        for (SubscriberHandler subscriber : subscribers.get(event)) {
            subscriber.handle();
        }
    }

    public void subscribe(SubscriberHandler subscriber, EventType... events) {
        for (EventType event : events) {
            subscribers.put(event, subscriber);
        }
    }

    enum EventType {
        STOP, RESET;
    }

    public static class Event {
        private final EventType eventType;
        private final String clientId;

        public Event(EventType eventType, String clientId) {
            this.eventType = eventType;
            this.clientId = clientId;
        }
    }

    interface SubscriberHandler {
        void handle();
    }
}
