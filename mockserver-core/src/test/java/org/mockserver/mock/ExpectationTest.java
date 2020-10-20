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
        HttpClassCallback httpClassCallback = new HttpClassCallback();
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback();
        Times times = Times.exactly(3);
        TimeToLive timeToLive = TimeToLive.exactly(TimeUnit.HOURS, 5L);
        int priority = 10;

        // when
        Expectation expectationThatResponds = new Expectation(httpRequest, times, timeToLive, priority).thenRespond(httpResponse);

        // then
        assertEquals(httpRequest, expectationThatResponds.getHttpRequest());
        assertEquals(httpResponse, expectationThatResponds.getHttpResponse());
        assertEquals(httpResponse, expectationThatResponds.getAction());
        assertNull(expectationThatResponds.getHttpForward());
        assertNull(expectationThatResponds.getHttpError());
        assertNull(expectationThatResponds.getHttpResponseClassCallback());
        assertNull(expectationThatResponds.getHttpResponseObjectCallback());
        assertEquals(times, expectationThatResponds.getTimes());
        assertEquals(timeToLive, expectationThatResponds.getTimeToLive());
        assertEquals(priority, expectationThatResponds.getPriority());

        // when
        Expectation expectationThatForwards = new Expectation(httpRequest, times, timeToLive, priority).thenForward(httpForward);

        // then
        assertEquals(httpRequest, expectationThatForwards.getHttpRequest());
        assertNull(expectationThatForwards.getHttpResponse());
        assertEquals(httpForward, expectationThatForwards.getHttpForward());
        assertEquals(httpForward, expectationThatForwards.getAction());
        assertNull(expectationThatForwards.getHttpError());
        assertNull(expectationThatForwards.getHttpResponseClassCallback());
        assertNull(expectationThatForwards.getHttpResponseObjectCallback());
        assertEquals(times, expectationThatForwards.getTimes());
        assertEquals(timeToLive, expectationThatForwards.getTimeToLive());
        assertEquals(priority, expectationThatForwards.getPriority());

        // when
        Expectation expectationThatErrors = new Expectation(httpRequest, times, timeToLive, priority).thenError(httpError);

        // then
        assertEquals(httpRequest, expectationThatErrors.getHttpRequest());
        assertNull(expectationThatErrors.getHttpResponse());
        assertNull(expectationThatErrors.getHttpForward());
        assertEquals(httpError, expectationThatErrors.getHttpError());
        assertEquals(httpError, expectationThatErrors.getAction());
        assertNull(expectationThatErrors.getHttpResponseClassCallback());
        assertNull(expectationThatErrors.getHttpResponseObjectCallback());
        assertEquals(times, expectationThatErrors.getTimes());
        assertEquals(timeToLive, expectationThatErrors.getTimeToLive());
        assertEquals(priority, expectationThatErrors.getPriority());

        // when
        Expectation expectationThatCallsbacksClass = new Expectation(httpRequest, times, timeToLive, priority).thenRespond(httpClassCallback);

        // then
        assertEquals(httpRequest, expectationThatForwards.getHttpRequest());
        assertNull(expectationThatCallsbacksClass.getHttpResponse());
        assertNull(expectationThatCallsbacksClass.getHttpForward());
        assertNull(expectationThatCallsbacksClass.getHttpError());
        assertEquals(httpClassCallback, expectationThatCallsbacksClass.getHttpResponseClassCallback());
        assertEquals(httpClassCallback, expectationThatCallsbacksClass.getAction());
        assertNull(expectationThatCallsbacksClass.getHttpResponseObjectCallback());
        assertEquals(times, expectationThatCallsbacksClass.getTimes());
        assertEquals(timeToLive, expectationThatCallsbacksClass.getTimeToLive());
        assertEquals(priority, expectationThatCallsbacksClass.getPriority());

        // when
        Expectation expectationThatCallsbackObject = new Expectation(httpRequest, times, timeToLive, priority).thenRespond(httpObjectCallback);

        // then
        assertEquals(httpRequest, expectationThatForwards.getHttpRequest());
        assertNull(expectationThatCallsbackObject.getHttpResponse());
        assertNull(expectationThatCallsbackObject.getHttpForward());
        assertNull(expectationThatCallsbackObject.getHttpError());
        assertNull(expectationThatCallsbackObject.getHttpResponseClassCallback());
        assertEquals(httpObjectCallback, expectationThatCallsbackObject.getHttpResponseObjectCallback());
        assertEquals(httpObjectCallback, expectationThatCallsbackObject.getAction());
        assertEquals(times, expectationThatCallsbackObject.getTimes());
        assertEquals(timeToLive, expectationThatCallsbackObject.getTimeToLive());
        assertEquals(priority, expectationThatCallsbackObject.getPriority());
    }

    @Test
    public void shouldAllowForNulls() {
        // when
        Expectation expectation = new Expectation(null, null, null, 0).thenRespond((HttpResponse) null).thenForward((HttpForward) null).thenRespond((HttpClassCallback) null).thenRespond((HttpObjectCallback) null);

        // then
        assertTrue(expectation.isActive());
        assertFalse(expectation.contains(null));
        assertNull(expectation.getHttpRequest());
        assertNull(expectation.getHttpResponse());
        assertNull(expectation.getHttpForward());
        assertNull(expectation.getHttpResponseClassCallback());
        assertNull(expectation.getHttpResponseObjectCallback());
        assertNull(expectation.getTimes());
        assertNull(expectation.getTimeToLive());
        assertEquals(expectation.getPriority(), 0);
    }

    @Test
    public void shouldReturnAliveStatus() {
        // when no times left should return false
        assertFalse(new Expectation(null, Times.exactly(0), TimeToLive.unlimited(), 0).thenRespond((HttpResponse) null).thenForward((HttpForward) null).isActive());
        assertFalse(new Expectation(request(), Times.exactly(0), TimeToLive.unlimited(), 0).thenRespond((HttpResponse) null).thenForward((HttpForward) null).isActive());
        assertFalse(new Expectation(request().withPath("un-matching"), Times.exactly(0), TimeToLive.unlimited(), 0).thenRespond((HttpResponse) null).thenForward((HttpForward) null).isActive());

        // when ttl expired should return false
        assertFalse(new Expectation(null, Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L), 0).thenRespond((HttpResponse) null).thenForward((HttpForward) null).isActive());
        assertFalse(new Expectation(request(), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L), 0).thenRespond((HttpResponse) null).thenForward((HttpForward) null).isActive());
        assertFalse(new Expectation(request().withPath("un-matching"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L), 0).thenRespond((HttpResponse) null).thenForward((HttpForward) null).isActive());
    }

    @Test
    public void shouldReduceRemainingMatches() {
        // given
        Expectation expectation = new Expectation(null, Times.once(), TimeToLive.unlimited(), 0);

        // when
        expectation.decrementRemainingMatches();

        // then
        assertThat(expectation.getTimes().getRemainingTimes(), is(0));
    }

    @Test
    public void shouldCalculateRemainingMatches() {
        assertThat(new Expectation(null, Times.once(), TimeToLive.unlimited(), 0).isActive(), is(true));
        assertThat(new Expectation(null, Times.unlimited(), TimeToLive.unlimited(), 0).isActive(), is(true));
        assertThat(new Expectation(null, Times.exactly(1), TimeToLive.unlimited(), 0).isActive(), is(true));
        assertThat(new Expectation(null, null, TimeToLive.unlimited(), 0).isActive(), is(true));

        assertThat(new Expectation(null, Times.exactly(0), TimeToLive.unlimited(), 0).isActive(), is(false));
    }

    @Test
    public void shouldCalculateRemainingLife() {
        assertThat(new Expectation(null, Times.unlimited(), TimeToLive.unlimited(), 0).isActive(), is(true));
        assertThat(new Expectation(null, Times.unlimited(), TimeToLive.exactly(TimeUnit.MINUTES, 5L), 0).isActive(), is(true));
        assertThat(new Expectation(null, Times.unlimited(), null, 0).isActive(), is(true));

        assertThat(new Expectation(null, Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0L), 0).isActive(), is(false));
    }

    @Test
    public void shouldNotThrowExceptionWithReducingNullRemainingMatches() {
        // given
        Expectation expectation = new Expectation(null, null, TimeToLive.unlimited(), 0);

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
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenForward(httpForward).thenRespond(httpResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventResponseAfterError() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpError httpError = new HttpError();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenError(httpError).thenRespond(httpResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventResponseAfterClassCallback() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpClassCallback httpClassCallback = new HttpClassCallback();
        HttpResponse httpResponse = new HttpResponse();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenRespond(httpClassCallback).thenRespond(httpResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventResponseAfterObjectCallback() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback();
        HttpResponse httpResponse = new HttpResponse();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenRespond(httpObjectCallback).thenRespond(httpResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventForwardAfterResponse() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenRespond(httpResponse).thenForward(httpForward);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventForwardAfterError() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpError httpError = new HttpError();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenError(httpError).thenForward(httpForward);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventForwardAfterClassCallback() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpClassCallback httpClassCallback = new HttpClassCallback();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenRespond(httpClassCallback).thenForward(httpForward);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventForwardAfterObjectCallback() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenRespond(httpObjectCallback).thenForward(httpForward);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventClassCallbackAfterForward() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpForward httpForward = new HttpForward();
        HttpClassCallback httpClassCallback = new HttpClassCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenForward(httpForward).thenRespond(httpClassCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventClassCallbackAfterError() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpError httpError = new HttpError();
        HttpClassCallback httpClassCallback = new HttpClassCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenError(httpError).thenRespond(httpClassCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventClassCallbackAfterResponse() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpClassCallback httpClassCallback = new HttpClassCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenRespond(httpResponse).thenRespond(httpClassCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventObjectCallbackAfterForward() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpForward httpForward = new HttpForward();
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenForward(httpForward).thenRespond(httpObjectCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventObjectCallbackAfterError() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpError httpError = new HttpError();
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenError(httpError).thenRespond(httpObjectCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventObjectCallbackAfterResponse() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenRespond(httpResponse).thenRespond(httpObjectCallback);
    }
}
