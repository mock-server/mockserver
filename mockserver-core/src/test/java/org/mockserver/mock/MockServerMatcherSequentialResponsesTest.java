package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherSequentialResponsesTest {

    private MockServerMatcher mockServerMatcher;

    private HttpResponse[] httpResponse;

    @Before
    public void prepareTestFixture() {
        httpResponse = new HttpResponse[]{
                new HttpResponse(),
                new HttpResponse(),
                new HttpResponse()
        };
        mockServerMatcher = new MockServerMatcher();
    }

    @Test
    public void respondWhenPathMatchesMultipleSequentialExpectation() {
        // when
        mockServerMatcher.when(new HttpRequest().withPath("somepath")).thenRespond(httpResponse[0].withBody("somebody1"));
        mockServerMatcher.when(new HttpRequest().withPath("somepath")).thenRespond(httpResponse[1].withBody("somebody2"));
        mockServerMatcher.when(new HttpRequest().withPath("somepath")).thenRespond(httpResponse[2].withBody("somebody3"));

        // then
        assertEquals(httpResponse[0], mockServerMatcher.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[1], mockServerMatcher.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[2], mockServerMatcher.handle(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesExpectationWithMultipleResponses() {
        // when
        mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited()).thenRespond(httpResponse[0].withBody("somebody1"));
        mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.exactly(1), TimeToLive.unlimited()).thenRespond(httpResponse[1].withBody("somebody2"));
        mockServerMatcher.when(new HttpRequest().withPath("somepath")).thenRespond(httpResponse[2].withBody("somebody3"));

        // then
        assertEquals(httpResponse[0], mockServerMatcher.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[0], mockServerMatcher.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[1], mockServerMatcher.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[2], mockServerMatcher.handle(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesMultipleDifferentResponses() {
        // when
        mockServerMatcher.when(new HttpRequest().withPath("somepath1")).thenRespond(httpResponse[0].withBody("somebody1"));
        mockServerMatcher.when(new HttpRequest().withPath("somepath2")).thenRespond(httpResponse[1].withBody("somebody2"));

        // then
        assertEquals(httpResponse[0], mockServerMatcher.handle(new HttpRequest().withPath("somepath1")));
        assertEquals(httpResponse[0], mockServerMatcher.handle(new HttpRequest().withPath("somepath1")));
        assertEquals(httpResponse[1], mockServerMatcher.handle(new HttpRequest().withPath("somepath2")));
        assertEquals(httpResponse[1], mockServerMatcher.handle(new HttpRequest().withPath("somepath2")));
        assertEquals(httpResponse[0], mockServerMatcher.handle(new HttpRequest().withPath("somepath1")));
        assertEquals(httpResponse[1], mockServerMatcher.handle(new HttpRequest().withPath("somepath2")));
    }

    @Test
    public void doesNotRespondAfterMatchesFinishedExpectedTimes() {
        // when
        mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited()).thenRespond(httpResponse[0].withBody("somebody"));

        // then
        assertEquals(httpResponse[0], mockServerMatcher.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[0], mockServerMatcher.handle(new HttpRequest().withPath("somepath")));
        assertNull(mockServerMatcher.handle(new HttpRequest().withPath("somepath")));
    }


}
