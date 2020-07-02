package org.mockserver.mock;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.Cookie;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockserver.ui.MockServerMatcherNotifier.Cause.API;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherOverlappingRequestsTest {

    private RequestMatchers requestMatchers;

    private HttpResponse[] httpResponse;

    private static final Scheduler scheduler = new Scheduler(new MockServerLogger());

    @Before
    public void prepareTestFixture() {
        httpResponse = new HttpResponse[]{
            new HttpResponse(),
            new HttpResponse()
        };
        MockServerLogger mockLogFormatter = mock(MockServerLogger.class);
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        requestMatchers = new RequestMatchers(mockLogFormatter, scheduler, webSocketClientRegistry);
    }


    @AfterClass
    public static void stopScheduler() {
        scheduler.shutdown();
    }

    @Test
    public void respondWhenPathMatchesAlwaysReturnFirstMatching() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))).thenRespond(httpResponse[0].withBody("somebody1"));
        requestMatchers.add(expectationZero, API);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath")).thenRespond(httpResponse[1].withBody("somebody2"));
        requestMatchers.add(expectationOne, API);

        // then
        assertEquals(expectationOne, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
        assertEquals(expectationOne, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesReturnFirstMatchingWithRemainingTimes() {
        // when
        Expectation expectationZero = new Expectation(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value")), Times.once(), TimeToLive.unlimited(), 0).thenRespond(httpResponse[0].withBody("somebody1"));
        requestMatchers.add(expectationZero, API);
        Expectation expectationOne = new Expectation(new HttpRequest().withPath("somepath")).thenRespond(httpResponse[1].withBody("somebody2"));
        requestMatchers.add(expectationOne, API);

        // then
        assertEquals(expectationZero, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
        assertEquals(expectationOne, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
    }

}
