package org.mockserver.client;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.client.MockServerEventBus.MockServerEvent.RESET;
import static org.mockserver.client.MockServerEventBus.MockServerEvent.STOP;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockserver.client.MockServerEventBus.MockServerEventSubscriber;

/**
 * @author albans
 */
public class MockServerEventBusTest {
	@Mock
	private MockServerEventSubscriber subscriber;
	
	@Mock
	private MockServerEventSubscriber secondSubscriber;
	
	private MockServerEventBus bus;
	
	@Before
	public void setupTestFixture() {
		bus = MockServerEventBus.getInstance();
		
		initMocks(this);
	}

	@Test
	public void shouldPublishStopEventWhenNoRegisterSubscriber() {
		// given no subscriber registered yet
		
		// when
		bus.publish(STOP);
		
		// then nothing, no exception
	}
	
	@Test
	public void shoudPublishStopEventToOneRegisteredSubscriber() {
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
