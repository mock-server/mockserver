package org.mockserver.client;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.client.MockServerEventBus.EventType.RESET;
import static org.mockserver.client.MockServerEventBus.EventType.STOP;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockserver.client.MockServerEventBus.SubscriberHandler;

/**
 * @author albans
 */
public class MockServerEventBusTest {
	@Mock
	private SubscriberHandler subscriber;

	@Mock
	private SubscriberHandler secondSubscriber;

	private MockServerEventBus bus;

	@Before
	public void setupTestFixture() {
		bus = new MockServerEventBus();

		openMocks(this);
	}

	@Test
	public void shouldPublishStopEventWhenNoRegisterSubscriber() {
		// given no subscriber registered yet

		// when
		bus.publish(STOP);

		// then nothing, no exception
	}

	@Test
	public void shouldPublishStopEventToOneRegisteredSubscriber() {
		// given
		bus.subscribe(subscriber, STOP);

		// when
		bus.publish(STOP);

		// then
		verify(subscriber).handle();
	}

	@Test
	public void shouldPublishResetEventToTwoSubscribers() {
		// given
		bus.subscribe(subscriber, RESET, STOP);
		bus.subscribe(subscriber, RESET, STOP);

		// when
		bus.publish(RESET);

		// then
		verify(subscriber, times(2)).handle();
	}

	@Test
	public void shouldPublishEventToCorrectConsumer() {
		// given
		bus.subscribe(subscriber, RESET);
		bus.subscribe(secondSubscriber, STOP);

		// when
		bus.publish(STOP);

		// then
		verify(subscriber, never()).handle();
		verify(secondSubscriber).handle();
	}
}
