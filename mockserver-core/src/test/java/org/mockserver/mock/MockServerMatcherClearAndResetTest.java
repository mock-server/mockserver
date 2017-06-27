package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherClearAndResetTest {

    private MockServerMatcher mockServerMatcher;

    @Before
    public void prepareTestFixture() {
        mockServerMatcher = new MockServerMatcher();
    }

    @Test
    public void shouldRemoveExpectationWhenNoMoreTimes() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");

        // when
        mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.exactly(2), TimeToLive.unlimited()).thenRespond(httpResponse);

        // then
        assertEquals(httpResponse, mockServerMatcher.retrieveAction(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse, mockServerMatcher.retrieveAction(new HttpRequest().withPath("somepath")));
        assertArrayEquals(new Expectation[]{}, mockServerMatcher.expectations.toArray());
        assertEquals(null, mockServerMatcher.retrieveAction(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void shouldClearAllExpectations() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);
        mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);

        // when
        mockServerMatcher.clear(new HttpRequest().withPath("somepath"));

        // then
        assertArrayEquals(new Expectation[]{}, mockServerMatcher.expectations.toArray());
    }

    @Test
    public void shouldResetAllExpectationsWhenHttpRequestNull() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);
        mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);

        // when
        mockServerMatcher.clear(null);

        // then
        assertArrayEquals(new Expectation[]{}, mockServerMatcher.expectations.toArray());
    }

    @Test
    public void shouldResetAllExpectations() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);
        mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);

        // when
        mockServerMatcher.reset();

        // then
        assertArrayEquals(new Expectation[]{}, mockServerMatcher.expectations.toArray());
    }

    @Test
    public void shouldClearMatchingExpectationsByPathOnly() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServerMatcher.when(new HttpRequest().withPath("abc"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);
        Expectation expectation = mockServerMatcher.when(new HttpRequest().withPath("def"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);

        // when
        mockServerMatcher.clear(new HttpRequest().withPath("abc"));

        // then
        assertArrayEquals(new Expectation[]{expectation}, mockServerMatcher.expectations.toArray());
    }

    @Test
    public void shouldClearMatchingExpectationsByMethodOnly() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServerMatcher.when(new HttpRequest().withMethod("GET").withPath("abc"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);
        mockServerMatcher.when(new HttpRequest().withMethod("GET").withPath("def"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);
        Expectation expectation = mockServerMatcher.when(new HttpRequest().withMethod("POST").withPath("def"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);

        // when
        mockServerMatcher.clear(new HttpRequest().withMethod("GET"));

        // then
        assertArrayEquals(new Expectation[]{expectation}, mockServerMatcher.expectations.toArray());
    }

    @Test
    public void shouldClearMatchingExpectationsByHeaderOnly() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServerMatcher.when(new HttpRequest().withMethod("GET").withPath("abc").withHeader(new Header("headerOneName", "headerOneValue")), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);
        mockServerMatcher.when(new HttpRequest().withMethod("PUT").withPath("def").withHeaders(new Header("headerOneName", "headerOneValue")), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);
        Expectation expectation = mockServerMatcher.when(new HttpRequest().withMethod("POST").withPath("def"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse);

        // when
        mockServerMatcher.clear(new HttpRequest().withHeader(new Header("headerOneName", "headerOneValue")));

        // then
        assertArrayEquals(new Expectation[]{expectation}, mockServerMatcher.expectations.toArray());
    }

    @Test
    public void shouldClearNoExpectations() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        Expectation[] expectations = new Expectation[]{
                mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse),
                mockServerMatcher.when(new HttpRequest().withPath("somepath"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(httpResponse)
        };

        // when
        mockServerMatcher.clear(new HttpRequest().withPath("foobar"));

        // then
        assertArrayEquals(expectations, mockServerMatcher.expectations.toArray());
    }

}
