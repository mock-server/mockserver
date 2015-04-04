package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpCallback;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockserver.model.HttpRequest.request;

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
        HttpCallback httpCallback = new HttpCallback();
        Times times = Times.exactly(3);

        // when
        Expectation expectationThatResponds = new Expectation(httpRequest, times).thenRespond(httpResponse);

        // then
        assertEquals(httpRequest, expectationThatResponds.getHttpRequest());
        assertEquals(httpResponse, expectationThatResponds.getHttpResponse(false));
        assertNull(expectationThatResponds.getHttpForward());
        assertEquals(httpResponse, expectationThatResponds.getAction(false));
        assertNull(expectationThatResponds.getHttpCallback());
        assertEquals(times, expectationThatResponds.getTimes());

        // when
        Expectation expectationThatForwards = new Expectation(httpRequest, times).thenForward(httpForward);

        // then
        assertEquals(httpRequest, expectationThatForwards.getHttpRequest());
        assertNull(expectationThatForwards.getHttpResponse(false));
        assertEquals(httpForward, expectationThatForwards.getHttpForward());
        assertEquals(httpForward, expectationThatForwards.getAction(false));
        assertNull(expectationThatForwards.getHttpCallback());
        assertEquals(times, expectationThatForwards.getTimes());

        // when
        Expectation expectationThatCallsback = new Expectation(httpRequest, times).thenCallback(httpCallback);

        // then
        assertEquals(httpRequest, expectationThatForwards.getHttpRequest());
        assertNull(expectationThatCallsback.getHttpResponse(false));
        assertNull(expectationThatCallsback.getHttpForward());
        assertEquals(httpCallback, expectationThatCallsback.getHttpCallback());
        assertEquals(httpCallback, expectationThatCallsback.getAction(false));
        assertEquals(times, expectationThatForwards.getTimes());
    }

    @Test
    public void shouldAllowForNulls() {
        // when
        Expectation expectation = new Expectation(null, null).thenRespond(null).thenForward(null).thenCallback(null);

        // then
        expectation.setNotUnlimitedResponses();
        assertTrue(expectation.matches(null));
        assertTrue(expectation.matches(new HttpRequest()));
        assertFalse(expectation.contains(null));
        assertNull(expectation.getHttpRequest());
        assertNull(expectation.getHttpResponse(false));
        assertNull(expectation.getHttpForward());
        assertNull(expectation.getHttpCallback());
        assertNull(expectation.getTimes());
    }

    @Test
    public void shouldMatchCorrectly() {
        // when request null should return true
        assertTrue(new Expectation(null, null).thenRespond(null).thenForward(null).matches(null));
        assertTrue(new Expectation(null, Times.unlimited()).thenRespond(null).thenForward(null).matches(null));

        // when basic matching request should return true
        assertTrue(new Expectation(request(), null).thenRespond(null).thenForward(null).matches(request()));
        assertTrue(new Expectation(request(), Times.unlimited()).thenRespond(null).thenForward(null).matches(request()));

        // when un-matching request should return false
        assertFalse(new Expectation(request().withPath("un-matching"), null).thenRespond(null).thenForward(null).matches(request()));
        assertFalse(new Expectation(request().withPath("un-matching"), Times.unlimited()).thenRespond(null).thenForward(null).matches(request()));
        assertFalse(new Expectation(request().withPath("un-matching"), Times.once()).thenRespond(null).thenForward(null).matches(request()));

        // when no times left should return false
        assertFalse(new Expectation(null, Times.exactly(0)).thenRespond(null).thenForward(null).matches(null));
        assertFalse(new Expectation(request(), Times.exactly(0)).thenRespond(null).thenForward(null).matches(request()));
        assertFalse(new Expectation(request().withPath("un-matching"), Times.exactly(0)).thenRespond(null).thenForward(null).matches(request()));
    }


    @Test
    public void shouldReduceRemainingMatches() {
        // given
        Expectation expectation = new Expectation(null, Times.once());

        // when
        expectation.decrementRemainingMatches();

        // then
        assertThat(expectation.getTimes().getRemainingTimes(), is(0));
    }

    @Test
    public void shouldNotThrowExceptionWithReducingNullRemainingMatches() {
        // given
        Expectation expectation = new Expectation(null, null);

        // when
        expectation.decrementRemainingMatches();

        // then
        assertThat(expectation.getTimes(), nullValue());
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
    public void shouldPreventForwardAfterCallback() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpCallback httpCallback = new HttpCallback();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once()).thenCallback(httpCallback).thenForward(httpForward);
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

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventResponseAfterCallback() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpCallback httpCallback = new HttpCallback();
        HttpResponse httpResponse = new HttpResponse();

        // then
        new Expectation(httpRequest, Times.once()).thenCallback(httpCallback).thenRespond(httpResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventCallbackAfterForward() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpCallback httpCallback = new HttpCallback();

        // then
        new Expectation(httpRequest, Times.once()).thenCallback(httpCallback).thenRespond(httpResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventCallbackAfterResponse() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpCallback httpCallback = new HttpCallback();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once()).thenCallback(httpCallback).thenForward(httpForward);
    }
}
