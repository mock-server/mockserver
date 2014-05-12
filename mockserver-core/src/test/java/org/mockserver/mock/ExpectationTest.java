package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpForward;
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
        HttpForward httpForward = new HttpForward();
        Times times = Times.exactly(3);

        // when
        Expectation expectationThatResponds = new Expectation(httpRequest, times).thenRespond(httpResponse);

        // then
        assertEquals(httpRequest, expectationThatResponds.getHttpRequest());
        assertEquals(httpResponse, expectationThatResponds.getHttpResponse());
        assertNull(expectationThatResponds.getHttpForward());
        assertEquals(times, expectationThatResponds.getTimes());

        // when
        Expectation expectationThatForwards = new Expectation(httpRequest, times).thenForward(httpForward);

        // then
        assertEquals(httpRequest, expectationThatForwards.getHttpRequest());
        assertNull(expectationThatForwards.getHttpResponse());
        assertEquals(httpForward, expectationThatForwards.getHttpForward());
        assertEquals(times, expectationThatForwards.getTimes());
    }

    @Test
    public void shouldAllowForNulls() {
        // when
        Expectation expectation = new Expectation(null, null).thenRespond(null).thenForward(null);

        // then
        expectation.setNotUnlimitedResponses();
        assertTrue(expectation.matches(null));
        assertTrue(expectation.matches(new HttpRequest()));
        assertFalse(expectation.contains(null));
        assertNull(expectation.getHttpRequest());
        assertNull(expectation.getHttpResponse());
        assertNull(expectation.getHttpForward());
        assertNull(expectation.getTimes());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventForwardAfterResponse() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once()).thenRespond(httpResponse).thenForward(httpForward);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventResponseAfterForward() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once()).thenForward(httpForward).thenRespond(httpResponse);
    }
}
