package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;

import java.util.concurrent.TimeUnit;

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
        HttpError httpError = new HttpError();
        HttpCallback httpCallback = new HttpCallback();
        Times times = Times.exactly(3);
        TimeToLive timeToLive = TimeToLive.exactly(TimeUnit.HOURS, 5l);

        // when
        Expectation expectationThatResponds = new Expectation(httpRequest, times, timeToLive).thenRespond(httpResponse);

        // then
        assertEquals(httpRequest, expectationThatResponds.getHttpRequest());
        assertEquals(httpResponse, expectationThatResponds.getHttpResponse(false));
        assertEquals(httpResponse, expectationThatResponds.getAction(false));
        assertNull(expectationThatResponds.getHttpForward());
        assertNull(expectationThatResponds.getHttpError());
        assertNull(expectationThatResponds.getHttpCallback());
        assertEquals(times, expectationThatResponds.getTimes());
        assertEquals(timeToLive, expectationThatResponds.getTimeToLive());

        // when
        Expectation expectationThatForwards = new Expectation(httpRequest, times, timeToLive).thenForward(httpForward);

        // then
        assertEquals(httpRequest, expectationThatForwards.getHttpRequest());
        assertNull(expectationThatForwards.getHttpResponse(false));
        assertEquals(httpForward, expectationThatForwards.getHttpForward());
        assertEquals(httpForward, expectationThatForwards.getAction(false));
        assertNull(expectationThatForwards.getHttpError());
        assertNull(expectationThatForwards.getHttpCallback());
        assertEquals(times, expectationThatForwards.getTimes());
        assertEquals(timeToLive, expectationThatForwards.getTimeToLive());

        // when
        Expectation expectationThatErrors = new Expectation(httpRequest, times, timeToLive).thenError(httpError);

        // then
        assertEquals(httpRequest, expectationThatErrors.getHttpRequest());
        assertNull(expectationThatErrors.getHttpResponse(false));
        assertNull(expectationThatErrors.getHttpForward());
        assertEquals(httpError, expectationThatErrors.getHttpError());
        assertEquals(httpError, expectationThatErrors.getAction(false));
        assertNull(expectationThatErrors.getHttpCallback());
        assertEquals(times, expectationThatErrors.getTimes());
        assertEquals(timeToLive, expectationThatErrors.getTimeToLive());

        // when
        Expectation expectationThatCallsback = new Expectation(httpRequest, times, timeToLive).thenCallback(httpCallback);

        // then
        assertEquals(httpRequest, expectationThatForwards.getHttpRequest());
        assertNull(expectationThatCallsback.getHttpResponse(false));
        assertNull(expectationThatCallsback.getHttpForward());
        assertNull(expectationThatCallsback.getHttpError());
        assertEquals(httpCallback, expectationThatCallsback.getHttpCallback());
        assertEquals(httpCallback, expectationThatCallsback.getAction(false));
        assertEquals(times, expectationThatCallsback.getTimes());
        assertEquals(timeToLive, expectationThatCallsback.getTimeToLive());
    }

    @Test
    public void shouldAllowForNulls() {
        // when
        Expectation expectation = new Expectation(null, null, TimeToLive.unlimited()).thenRespond(null).thenForward(null).thenCallback(null);

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
        assertTrue(new Expectation(null, null, TimeToLive.unlimited()).thenRespond(null).thenForward(null).matches(null));
        assertTrue(new Expectation(null, Times.unlimited(), TimeToLive.unlimited()).thenRespond(null).thenForward(null).matches(null));

        // when basic matching request should return true
        assertTrue(new Expectation(request(), null, TimeToLive.unlimited()).thenRespond(null).thenForward(null).matches(request()));
        assertTrue(new Expectation(request(), Times.unlimited(), TimeToLive.unlimited()).thenRespond(null).thenForward(null).matches(request()));

        // when un-matching request should return false
        assertFalse(new Expectation(request().withPath("some_path"), null, TimeToLive.unlimited()).thenRespond(null).thenForward(null).matches(request().withPath("some_other_path")));
        assertFalse(new Expectation(request().withPath("some_path"), Times.unlimited(), TimeToLive.unlimited()).thenRespond(null).thenForward(null).matches(request().withPath("some_other_path")));
        assertFalse(new Expectation(request().withPath("some_path"), Times.once(), TimeToLive.unlimited()).thenRespond(null).thenForward(null).matches(request().withPath("some_other_path")));

        // when no times left should return false
        assertFalse(new Expectation(null, Times.exactly(0), TimeToLive.unlimited()).thenRespond(null).thenForward(null).matches(null));
        assertFalse(new Expectation(request(), Times.exactly(0), TimeToLive.unlimited()).thenRespond(null).thenForward(null).matches(request()));
        assertFalse(new Expectation(request().withPath("un-matching"), Times.exactly(0), TimeToLive.unlimited()).thenRespond(null).thenForward(null).matches(request()));

        // when ttl expired should return false
        assertFalse(new Expectation(null, Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0l)).thenRespond(null).thenForward(null).matches(null));
        assertFalse(new Expectation(request(), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0l)).thenRespond(null).thenForward(null).matches(request()));
        assertFalse(new Expectation(request().withPath("un-matching"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0l)).thenRespond(null).thenForward(null).matches(request()));
    }

    @Test
    public void shouldReduceRemainingMatches() {
        // given
        Expectation expectation = new Expectation(null, Times.once(), TimeToLive.unlimited());

        // when
        expectation.decrementRemainingMatches();

        // then
        assertThat(expectation.getTimes().getRemainingTimes(), is(0));
    }

    @Test
    public void shouldCalculateRemainingMatches() {
        assertThat(new Expectation(null, Times.once(), TimeToLive.unlimited()).hasRemainingMatches(), is(true));
        assertThat(new Expectation(null, Times.unlimited(), TimeToLive.unlimited()).hasRemainingMatches(), is(true));
        assertThat(new Expectation(null, Times.exactly(1), TimeToLive.unlimited()).hasRemainingMatches(), is(true));
        assertThat(new Expectation(null, null, TimeToLive.unlimited()).hasRemainingMatches(), is(true));

        assertThat(new Expectation(null, Times.exactly(0), TimeToLive.unlimited()).hasRemainingMatches(), is(false));
    }

    @Test
    public void shouldCalculateRemainingLife() {
        assertThat(new Expectation(null, Times.unlimited(), TimeToLive.unlimited()).isStillAlive(), is(true));
        assertThat(new Expectation(null, Times.unlimited(), TimeToLive.exactly(TimeUnit.MINUTES, 5L)).isStillAlive(), is(true));
        assertThat(new Expectation(null, Times.unlimited(), null).hasRemainingMatches(), is(true));

        assertThat(new Expectation(null, Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L)).isStillAlive(), is(false));
    }

    @Test
    public void shouldNotThrowExceptionWithReducingNullRemainingMatches() {
        // given
        Expectation expectation = new Expectation(null, null, TimeToLive.unlimited());

        // when
        expectation.decrementRemainingMatches();

        // then
        assertThat(expectation.getTimes(), nullValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventResponseAfterForward() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenForward(httpForward).thenRespond(httpResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventResponseAfterError() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpError httpError = new HttpError();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenError(httpError).thenRespond(httpResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventResponseAfterCallback() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpCallback httpCallback = new HttpCallback();
        HttpResponse httpResponse = new HttpResponse();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenCallback(httpCallback).thenRespond(httpResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventForwardAfterResponse() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponse).thenForward(httpForward);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventForwardAfterError() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpError httpError = new HttpError();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenError(httpError).thenForward(httpForward);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventForwardAfterCallback() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpCallback httpCallback = new HttpCallback();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenCallback(httpCallback).thenForward(httpForward);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventCallbackAfterForward() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpForward httpForward = new HttpForward();
        HttpCallback httpCallback = new HttpCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenForward(httpForward).thenCallback(httpCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventCallbackAfterError() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpError httpError = new HttpError();
        HttpCallback httpCallback = new HttpCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenError(httpError).thenCallback(httpCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventCallbackAfterResponse() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpCallback httpCallback = new HttpCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponse).thenCallback(httpCallback);
    }
}
