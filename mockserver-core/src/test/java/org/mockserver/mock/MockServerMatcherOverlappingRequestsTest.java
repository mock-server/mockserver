package org.mockserver.mock;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.Cookie;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherOverlappingRequestsTest {

    private MockServerMatcher mockServerMatcher;

    private HttpResponse[] httpResponse;

    private static Scheduler scheduler = new Scheduler(new MockServerLogger());

    @Before
    public void prepareTestFixture() {
        httpResponse = new HttpResponse[]{
                new HttpResponse(),
                new HttpResponse()
        };
        MockServerLogger mockLogFormatter = mock(MockServerLogger.class);
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        mockServerMatcher = new MockServerMatcher(mockLogFormatter, scheduler, webSocketClientRegistry);
    }


    @AfterClass
    public static void stopScheduler() {
        scheduler.shutdown();
    }

    @Test
    public void respondWhenPathMatchesAlwaysReturnFirstMatching() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))).thenRespond(httpResponse[0].withBody("somebody1"));
        mockServerMatcher.add(expectationZero);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath")).thenRespond(httpResponse[1].withBody("somebody2"));
        mockServerMatcher.add(expectationOne);

        // then
        assertEquals(expectationOne, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationOne, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesReturnFirstMatchingWithRemainingTimes() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value")), Times.once(), TimeToLive.unlimited()).thenRespond(httpResponse[0].withBody("somebody1"));
        mockServerMatcher.add(expectationZero);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath")).thenRespond(httpResponse[1].withBody("somebody2"));
        mockServerMatcher.add(expectationOne);

        // then
        assertEquals(expectationZero, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
        assertEquals(expectationOne, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
    }

}
