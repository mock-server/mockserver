package org.jamesdbloom.mockserver.mock;

import org.jamesdbloom.mockserver.mappers.ExpectationMapper;
import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.model.Cookie;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerOverlappingRequestsTest {

    @Mock
    private ExpectationMapper expectationMapper;

    @InjectMocks
    private MockServer mockServer;

    private HttpResponse[] httpResponse;

    @Before
    public void prepareTestFixture() {
        httpResponse = new HttpResponse[]{
                new HttpResponse(),
                new HttpResponse()
        };

        mockServer = new MockServer();

        initMocks(this);
    }

    @Test
    public void respondWhenPathMatchesAlwaysReturnFirstMatching() {
        // given
        when(expectationMapper.transformsToMatcher(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value")))).thenReturn(new HttpRequestMatcher().withPath("somepath").withCookies(new Cookie("name", "value")));
        when(expectationMapper.transformsToMatcher(new HttpRequest().withPath("somepath"))).thenReturn(new HttpRequestMatcher().withPath("somepath"));

        // when
        mockServer.when(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))).respond(httpResponse[0].withBody("somebody1"));
        mockServer.when(new HttpRequest().withPath("somepath")).respond(httpResponse[1].withBody("somebody2"));

        // then
        assertEquals(httpResponse[1], mockServer.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse[1], mockServer.handle(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenPathMatchesReturnFirstMatchingWithRemainingTimes() {
        // given
        when(expectationMapper.transformsToMatcher(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value")))).thenReturn(new HttpRequestMatcher().withPath("somepath").withCookies(new Cookie("name", "value")));
        when(expectationMapper.transformsToMatcher(new HttpRequest().withPath("somepath"))).thenReturn(new HttpRequestMatcher().withPath("somepath"));

        // when
        mockServer.when(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value")), Times.once()).respond(httpResponse[0].withBody("somebody1"));
        mockServer.when(new HttpRequest().withPath("somepath")).respond(httpResponse[1].withBody("somebody2"));

        // then
        assertEquals(httpResponse[0], mockServer.handle(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
        assertEquals(httpResponse[1], mockServer.handle(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
    }

}
