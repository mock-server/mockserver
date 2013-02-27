package org.jamesdbloom.mockserver.mock;

import org.jamesdbloom.mockserver.mappers.ExpectationMapper;
import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerSequentialResponsesTest {

    private MockServer mockServer;

    private HttpResponse[] httpResponse;

    @Before
    public void prepareTestFixture() {
        httpResponse = new HttpResponse[]{
                new HttpResponse(),
                new HttpResponse(),
                new HttpResponse()
        };
        mockServer = new MockServer();
    }

    @Test
    public void respondWhenPathMatchesMultipleSequentialExpectation() {
        // when
        mockServer.when(new HttpRequest().withPath("somepath")).respond(httpResponse[0].withBody("somebody1"));
        mockServer.when(new HttpRequest().withPath("somepath")).respond(httpResponse[1].withBody("somebody2"));
        mockServer.when(new HttpRequest().withPath("somepath")).respond(httpResponse[2].withBody("somebody3"));

        // then
        assertEquals(httpResponse[0], mockServer.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[1], mockServer.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[2], mockServer.handle(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesExpectationWithMultipleResponses() {
        // when
        mockServer.when(new HttpRequest().withPath("somepath"), Times.exactly(2)).respond(httpResponse[0].withBody("somebody1"));
        mockServer.when(new HttpRequest().withPath("somepath"), Times.exactly(1)).respond(httpResponse[1].withBody("somebody2"));
        mockServer.when(new HttpRequest().withPath("somepath")).respond(httpResponse[2].withBody("somebody3"));

        // then
        assertEquals(httpResponse[0], mockServer.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[0], mockServer.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[1], mockServer.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[2], mockServer.handle(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesMultipleDifferentResponses() {
        // when
        mockServer.when(new HttpRequest().withPath("somepath1")).respond(httpResponse[0].withBody("somebody1"));
        mockServer.when(new HttpRequest().withPath("somepath2")).respond(httpResponse[1].withBody("somebody2"));

        // then
        assertEquals(httpResponse[0], mockServer.handle(new HttpRequest().withPath("somepath1")));
        assertEquals(httpResponse[0], mockServer.handle(new HttpRequest().withPath("somepath1")));
        assertEquals(httpResponse[1], mockServer.handle(new HttpRequest().withPath("somepath2")));
        assertEquals(httpResponse[1], mockServer.handle(new HttpRequest().withPath("somepath2")));
        assertEquals(httpResponse[0], mockServer.handle(new HttpRequest().withPath("somepath1")));
        assertEquals(httpResponse[1], mockServer.handle(new HttpRequest().withPath("somepath2")));
    }

    @Test
    public void doesNotRespondAfterMatchesFinishedExpectedTimes() {
        // when
        mockServer.when(new HttpRequest().withPath("somepath"), Times.exactly(2)).respond(httpResponse[0].withBody("somebody"));

        // then
        assertEquals(httpResponse[0], mockServer.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[0], mockServer.handle(new HttpRequest().withPath("somepath")));
        assertNull(mockServer.handle(new HttpRequest().withPath("somepath")));
    }


}
