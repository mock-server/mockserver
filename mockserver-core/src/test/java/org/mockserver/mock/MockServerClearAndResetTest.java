package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class MockServerClearAndResetTest {

    private MockServer mockServer;

    @Before
    public void prepareTestFixture() {
        mockServer = new MockServer();
    }

    @Test
    public void shouldRemoveExpectationWhenNoMoreTimes() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");

        // when
        mockServer.when(new HttpRequest().withPath("somepath"), Times.exactly(2)).thenRespond(httpResponse);

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath")));
        assertArrayEquals(new Expectation[]{}, mockServer.expectations.toArray());
        assertEquals(null, mockServer.handle(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void shouldClearAllExpectations() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServer.when(new HttpRequest().withPath("somepath"), Times.unlimited()).thenRespond(httpResponse);
        mockServer.when(new HttpRequest().withPath("somepath"), Times.unlimited()).thenRespond(httpResponse);

        // when
        mockServer.clear(new HttpRequest().withPath("somepath"));

        // then
        assertArrayEquals(new Expectation[]{}, mockServer.expectations.toArray());
    }

    @Test
    public void shouldResetAllExpectationsWhenHttpRequestNull() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServer.when(new HttpRequest().withPath("somepath"), Times.unlimited()).thenRespond(httpResponse);
        mockServer.when(new HttpRequest().withPath("somepath"), Times.unlimited()).thenRespond(httpResponse);

        // when
        mockServer.clear(null);

        // then
        assertArrayEquals(new Expectation[]{}, mockServer.expectations.toArray());
    }

    @Test
    public void shouldResetAllExpectations() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServer.when(new HttpRequest().withPath("somepath"), Times.unlimited()).thenRespond(httpResponse);
        mockServer.when(new HttpRequest().withPath("somepath"), Times.unlimited()).thenRespond(httpResponse);

        // when
        mockServer.reset();

        // then
        assertArrayEquals(new Expectation[]{}, mockServer.expectations.toArray());
    }

    @Test
    public void shouldClearOnlyMatchingExpectations() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServer.when(new HttpRequest().withPath("abc"), Times.unlimited()).thenRespond(httpResponse);
        Expectation expectation = mockServer.when(new HttpRequest().withPath("def"), Times.unlimited()).thenRespond(httpResponse);

        // when
        mockServer.clear(new HttpRequest().withPath("abc"));

        // then
        assertArrayEquals(new Expectation[]{expectation}, mockServer.expectations.toArray());
    }

    @Test
    public void shouldClearNoExpectations() {
        // given
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        Expectation[] expectations = new Expectation[]{
                mockServer.when(new HttpRequest().withPath("somepath"), Times.unlimited()).thenRespond(httpResponse),
                mockServer.when(new HttpRequest().withPath("somepath"), Times.unlimited()).thenRespond(httpResponse)
        };

        // when
        mockServer.clear(new HttpRequest().withPath("foobar"));

        // then
        assertArrayEquals(expectations, mockServer.expectations.toArray());
    }

}
