package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.Cookie;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherOverlappingRequestsTest {

    private MockServerMatcher mockServerMatcher;

    private HttpResponse[] httpResponse;

    @Before
    public void prepareTestFixture() {
        httpResponse = new HttpResponse[]{
                new HttpResponse(),
                new HttpResponse()
        };

        mockServerMatcher = new MockServerMatcher();
    }

    @Test
    public void respondWhenPathMatchesAlwaysReturnFirstMatching() {
        // when
        mockServerMatcher.when(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))).thenRespond(httpResponse[0].withBody("somebody1"));
        mockServerMatcher.when(new HttpRequest().withPath("somepath")).thenRespond(httpResponse[1].withBody("somebody2"));

        // then
        assertEquals(httpResponse[1], mockServerMatcher.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[1], mockServerMatcher.handle(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesReturnFirstMatchingWithRemainingTimes() {
        // when
        mockServerMatcher.when(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value")), Times.once(), TimeToLive.unlimited()).thenRespond(httpResponse[0].withBody("somebody1"));
        mockServerMatcher.when(new HttpRequest().withPath("somepath")).thenRespond(httpResponse[1].withBody("somebody2"));

        // then
        assertEquals(httpResponse[0], mockServerMatcher.handle(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
        assertEquals(httpResponse[1], mockServerMatcher.handle(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
    }

}
