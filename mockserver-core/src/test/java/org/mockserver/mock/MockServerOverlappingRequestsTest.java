package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.matchers.Times;
import org.mockserver.model.Cookie;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class MockServerOverlappingRequestsTest {

    private MockServer mockServer;

    private HttpResponse[] httpResponse;

    @Before
    public void prepareTestFixture() {
        httpResponse = new HttpResponse[]{
                new HttpResponse(),
                new HttpResponse()
        };

        mockServer = new MockServer();
    }

    @Test
    public void respondWhenPathMatchesAlwaysReturnFirstMatching() {
        // when
        mockServer.when(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))).respond(httpResponse[0].withBody("somebody1"));
        mockServer.when(new HttpRequest().withPath("somepath")).respond(httpResponse[1].withBody("somebody2"));

        // then
        assertEquals(httpResponse[1], mockServer.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[1], mockServer.handle(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesReturnFirstMatchingWithRemainingTimes() {
        // when
        mockServer.when(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value")), Times.once()).respond(httpResponse[0].withBody("somebody1"));
        mockServer.when(new HttpRequest().withPath("somepath")).respond(httpResponse[1].withBody("somebody2"));

        // then
        assertEquals(httpResponse[0], mockServer.handle(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
        assertEquals(httpResponse[1], mockServer.handle(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
    }

}
