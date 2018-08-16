package org.mockserver.client;


import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder.ListMultimapBuilder;

/**
 * A publish/subscribe communication channel between {@link MockServerClient} and {@link ForwardChainExpectation} instances
 * 
 * Use {@link #getInstance()} to get the instance.
 * 
 * @author albans
 */
final class MockServerEventBus {
	private static MockServerEventBus INSTANCE;

	private final Multimap<MockServerEvent, MockServerEventSubscriber> subscribers = ListMultimapBuilder
			.enumKeys(MockServerEvent.class)
			.arrayListValues()
			.build();

	/**
	 * {@link MockServerEvent} subscribers pass an instance of an
	 * implementation of this class to {@link MockServerEventBus#subscribe(MockServerEventSubscriber, MockServerEvent...)}
	 * to subscribe to events
	 */
	static interface MockServerEventSubscriber {
		void handle();
	}

	/**
	 * Enumeration of events handled by this bus.
	 */
	static enum MockServerEvent {
		STOP, RESET;
	}

	private MockServerEventBus() {
	}

	/**
	 * Return the instance of {@link MockServerEventBus}.
	 * @return instance
	 */
	public static MockServerEventBus getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new MockServerEventBus();
		}
		return INSTANCE;
	}

	/**
	 * Publish the event given as parameter.
	 * @param event
	 */
	public void publish(MockServerEvent event) {
		for (MockServerEventSubscriber subscriber : subscribers.get(event)) {
			subscriber.handle();
		}
	}

	/**
	 * Subscribe the the events given as parameters using the  given subscriber.
	 * @param subscriber
	 * @param events 
	 */
	public void subscribe(MockServerEventSubscriber subscriber, MockServerEvent... events) {
		for (MockServerEvent event : events) {
			subscribers.put(event, subscriber);
		}
	}
}
