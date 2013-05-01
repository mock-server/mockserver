package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author jamesdbloom
 */
public class MockServerCleanUpTest {

    private MockServer mockServer;

    @Before
    public void prepareTestFixture() {
        mockServer = new MockServer();
    }

    @Test
    public void shouldRemoveExpectationWhenNoMoreTimes() {
        // when
        HttpResponse httpResponse = new HttpResponse().withBody("somebody");
        mockServer.when(new HttpRequest().withPath("somepath"), Times.exactly(2)).respond(httpResponse);

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath")));
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath")));
        assertArrayEquals(new Expectation[]{}, mockServer.expectations.toArray());
        assertEquals(null, mockServer.handle(new HttpRequest().withPath("somepath")));
    }

}
