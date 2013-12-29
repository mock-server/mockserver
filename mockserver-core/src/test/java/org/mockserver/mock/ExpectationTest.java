package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.junit.Assert.*;

/**
 * @author jamesdbloom
 */
public class ExpectationTest {

    @Test
    public void shouldConstructAndGetFields() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        Times times = Times.exactly(3);

        // when
        Expectation expectation = new Expectation(httpRequest, times).respond(httpResponse);

        // then
        assertEquals(httpRequest, expectation.getHttpRequest());
        assertEquals(httpResponse, expectation.getHttpResponse());
        assertEquals(times, expectation.getTimes());
    }

    @Test
    public void shouldAllowForNulls() {
        // when
        Expectation expectation = new Expectation(null, null).respond(null);

        // then
        expectation.setNotUnlimitedResponses();
        assertTrue(expectation.matches(null));
        assertFalse(expectation.matches(new HttpRequest()));
        assertFalse(expectation.contains(null));
        assertNull(expectation.getHttpRequest());
        assertNull(expectation.getHttpResponse());
        assertNull(expectation.getTimes());
    }
}
