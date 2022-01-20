package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherSequentialResponsesTest {

    private RequestMatchers requestMatchers;

    @Before
    public void prepareTestFixture() {
        MockServerLogger mockLogFormatter = mock(MockServerLogger.class);
        Scheduler scheduler = mock(Scheduler.class);
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        requestMatchers = new RequestMatchers(mockLogFormatter, scheduler, webSocketClientRegistry);
    }

    @Test
    public void respondWhenPathMatchesExpectationWithLimitedMatchesWithMultipleResponses() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited(), 0).thenRespond(response().withBody("somebody1"));
        requestMatchers.add(expectationZero, API);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(1), TimeToLive.unlimited(), 0).thenRespond(response().withBody("somebody2"));
        requestMatchers.add(expectationOne, API);
        Expectation expectationTwo = new Expectation(new HttpRequest().withPath("somepath")).thenRespond(response().withBody("somebody3"));
        requestMatchers.add(expectationTwo, API);

        // then
        assertEquals(expectationZero, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationZero, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationOne, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationTwo, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesExpectationWithPriorityAndLimitedMatchesWithMultipleResponses() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited(), 0).thenRespond(response().withBody("somebody1"));
        requestMatchers.add(expectationZero, API);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(1), TimeToLive.unlimited(), 10).thenRespond(response().withBody("somebody2"));
        requestMatchers.add(expectationOne, API);
        Expectation expectationTwo = new Expectation(new HttpRequest().withPath("somepath")).thenRespond(response().withBody("somebody3"));
        requestMatchers.add(expectationTwo, API);

        // then
        assertEquals(expectationOne, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationZero, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationZero, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationTwo, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesExpectationWithPriorityWithMultipleResponses() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited(), 0).thenRespond(response().withBody("somebody1"));
        requestMatchers.add(expectationZero, API);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited(), 10).thenRespond(response().withBody("somebody2"));
        requestMatchers.add(expectationOne, API);
        Expectation expectationTwo = new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited(), 5).thenRespond(response().withBody("somebody3"));
        requestMatchers.add(expectationTwo, API);

        // then - match in priority order 10 (one) -> 5 (two) -> 0 (zero)
        assertEquals(expectationOne, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));

        // when
        Expectation expectationZeroWithHigherPriority = new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited(), 15)
            .withId(expectationZero.getId())
            .thenRespond(response().withBody("somebody1"));
        requestMatchers.update(new Expectation[]{expectationZeroWithHigherPriority}, API);

        // then - match in priority order 15 (zero) -> 10 (one) -> 5 (two)
        assertEquals(expectationZeroWithHigherPriority, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));

        // when
        Expectation expectationTwoWithHigherPriority = new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited(), 20)
            .withId(expectationTwo.getId())
            .thenRespond(response().withBody("somebody3"));
        requestMatchers.update(new Expectation[]{expectationTwoWithHigherPriority}, API);

        // then - match in priority order 20 (two) -> 15 (zero) -> 10 (one)
        assertEquals(expectationTwoWithHigherPriority, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesMultipleDifferentResponses() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath1")).thenRespond(response().withBody("somebody1"));
        requestMatchers.add(expectationZero, API);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath2")).thenRespond(response().withBody("somebody2"));
        requestMatchers.add(expectationOne, API);

        // then
        assertEquals(expectationZero, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath1")));
        assertEquals(expectationZero, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath1")));
        assertEquals(expectationOne, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath2")));
        assertEquals(expectationOne, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath2")));
        assertEquals(expectationZero, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath1")));
        assertEquals(expectationOne, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath2")));
    }

    @Test
    public void doesNotRespondAfterMatchesFinishedExpectedTimes() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited(), 0).thenRespond(response().withBody("somebody"));
        requestMatchers.add(expectationZero, API);

        // then
        assertEquals(expectationZero, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationZero, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertNull(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void doesNotRespondAfterTimeToLiveFinishedExpectedTimes() throws InterruptedException {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.exactly(SECONDS, 2L), 0).thenRespond(response().withBody("somebody"));
        requestMatchers.add(expectationZero, API);

        // then
        assertEquals(expectationZero, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        MILLISECONDS.sleep(2250L);
        assertThat(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")), is(nullValue()));
        assertNull(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

}
