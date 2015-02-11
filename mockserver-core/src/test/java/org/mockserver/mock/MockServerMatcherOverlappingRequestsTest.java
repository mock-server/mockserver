package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.matchers.Times;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
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
        mockServerMatcher.when(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value")), Times.once()).thenRespond(httpResponse[0].withBody("somebody1"));
        mockServerMatcher.when(new HttpRequest().withPath("somepath")).thenRespond(httpResponse[1].withBody("somebody2"));

        // then
        assertEquals(httpResponse[0], mockServerMatcher.handle(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
        assertEquals(httpResponse[1], mockServerMatcher.handle(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void respondWithMoreDetailedMatch() {
        // when
        mockServerMatcher.when(new HttpRequest()
                .withPath("somepath"))
                .thenRespond(new HttpResponse().withBody("somebody1"));
        mockServerMatcher.when(new HttpRequest()
                .withPath("somepath")
                .withHeader(new Header("Content-Type", "xml")))
                .thenRespond(new HttpResponse().withBody("somebody2"));
        mockServerMatcher.when(new HttpRequest()
                .withPath("somepath")
                .withHeader(new Header("Content-Type", "xml"))
                .withCookies(new Cookie("name", "value")))
                .thenRespond(new HttpResponse().withBody("somebody3"));
        // then
        HttpResponse response = (HttpResponse) mockServerMatcher.handle(new HttpRequest()
                .withPath("somepath")
                .withCookies(new Cookie("name", "value"))
                .withHeader(new Header("Content-Type", "xml")));
        assertEquals("somebody3", response.getBodyAsString());
    }

}
