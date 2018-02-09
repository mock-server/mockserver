package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherSequentialResponsesTest {

    private MockServerMatcher mockServerMatcher;

    private HttpResponse[] httpResponse;

    private MockServerLogger mockLogFormatter;
    private Scheduler scheduler;

    @Before
    public void prepareTestFixture() {
        httpResponse = new HttpResponse[]{
                new HttpResponse(),
                new HttpResponse(),
                new HttpResponse()
        };
        mockLogFormatter = mock(MockServerLogger.class);
        scheduler = mock(Scheduler.class);
        mockServerMatcher = new MockServerMatcher(mockLogFormatter, scheduler);
    }

    @Test
    public void respondWhenPathMatchesExpectationWithMultipleResponses() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited()).thenRespond(httpResponse[0].withBody("somebody1"));
        mockServerMatcher.add(expectationZero);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(1), TimeToLive.unlimited()).thenRespond(httpResponse[1].withBody("somebody2"));
        mockServerMatcher.add(expectationOne);
        Expectation expectationTwo = new Expectation(new HttpRequest().withPath("somepath")).thenRespond(httpResponse[2].withBody("somebody3"));
        mockServerMatcher.add(expectationTwo);

        // then
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationOne, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationTwo, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesMultipleDifferentResponses() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath1")).thenRespond(httpResponse[0].withBody("somebody1"));
        mockServerMatcher.add(expectationZero);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath2")).thenRespond(httpResponse[1].withBody("somebody2"));
        mockServerMatcher.add(expectationOne);

        // then
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath1")));
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath1")));
        assertEquals(expectationOne, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath2")));
        assertEquals(expectationOne, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath2")));
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath1")));
        assertEquals(expectationOne, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath2")));
    }

    @Test
    public void doesNotRespondAfterMatchesFinishedExpectedTimes() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited()).thenRespond(httpResponse[0].withBody("somebody"));
        mockServerMatcher.add(expectationZero);

        // then
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }


}
