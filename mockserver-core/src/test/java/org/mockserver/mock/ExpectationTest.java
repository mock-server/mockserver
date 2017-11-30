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
        TimeToLive timeToLive = TimeToLive.exactly(TimeUnit.HOURS, 5l);

        // when
        Expectation expectationThatResponds = new Expectation(httpRequest, times, timeToLive).thenRespond(httpResponse);

        // then
        assertEquals(httpRequest, expectationThatResponds.getHttpRequest());
        assertEquals(httpResponse, expectationThatResponds.getHttpResponse());
        assertEquals(httpResponse, expectationThatResponds.getAction());
        assertNull(expectationThatResponds.getHttpForward());
        assertNull(expectationThatResponds.getHttpError());
        assertNull(expectationThatResponds.getHttpClassCallback());
        assertNull(expectationThatResponds.getHttpObjectCallback());
        assertEquals(times, expectationThatResponds.getTimes());
        assertEquals(timeToLive, expectationThatResponds.getTimeToLive());

        // when
        Expectation expectationThatForwards = new Expectation(httpRequest, times, timeToLive).thenForward(httpForward);

        // then
        assertEquals(httpRequest, expectationThatForwards.getHttpRequest());
        assertNull(expectationThatForwards.getHttpResponse());
        assertEquals(httpForward, expectationThatForwards.getHttpForward());
        assertEquals(httpForward, expectationThatForwards.getAction());
        assertNull(expectationThatForwards.getHttpError());
        assertNull(expectationThatForwards.getHttpClassCallback());
        assertNull(expectationThatForwards.getHttpObjectCallback());
        assertEquals(times, expectationThatForwards.getTimes());
        assertEquals(timeToLive, expectationThatForwards.getTimeToLive());

        // when
        Expectation expectationThatErrors = new Expectation(httpRequest, times, timeToLive).thenError(httpError);

        // then
        assertEquals(httpRequest, expectationThatErrors.getHttpRequest());
        assertNull(expectationThatErrors.getHttpResponse());
        assertNull(expectationThatErrors.getHttpForward());
        assertEquals(httpError, expectationThatErrors.getHttpError());
        assertEquals(httpError, expectationThatErrors.getAction());
        assertNull(expectationThatErrors.getHttpClassCallback());
        assertNull(expectationThatErrors.getHttpObjectCallback());
        assertEquals(times, expectationThatErrors.getTimes());
        assertEquals(timeToLive, expectationThatErrors.getTimeToLive());

        // when
        Expectation expectationThatCallsbacksClass = new Expectation(httpRequest, times, timeToLive).thenCallback(httpClassCallback);

        // then
        assertEquals(httpRequest, expectationThatForwards.getHttpRequest());
        assertNull(expectationThatCallsbacksClass.getHttpResponse());
        assertNull(expectationThatCallsbacksClass.getHttpForward());
        assertNull(expectationThatCallsbacksClass.getHttpError());
        assertEquals(httpClassCallback, expectationThatCallsbacksClass.getHttpClassCallback());
        assertEquals(httpClassCallback, expectationThatCallsbacksClass.getAction());
        assertNull(expectationThatCallsbacksClass.getHttpObjectCallback());
        assertEquals(times, expectationThatCallsbacksClass.getTimes());
        assertEquals(timeToLive, expectationThatCallsbacksClass.getTimeToLive());

        // when
        Expectation expectationThatCallsbackObject = new Expectation(httpRequest, times, timeToLive).thenCallback(httpObjectCallback);

        // then
        assertEquals(httpRequest, expectationThatForwards.getHttpRequest());
        assertNull(expectationThatCallsbackObject.getHttpResponse());
        assertNull(expectationThatCallsbackObject.getHttpForward());
        assertNull(expectationThatCallsbackObject.getHttpError());
        assertNull(expectationThatCallsbackObject.getHttpClassCallback());
        assertEquals(httpObjectCallback, expectationThatCallsbackObject.getHttpObjectCallback());
        assertEquals(httpObjectCallback, expectationThatCallsbackObject.getAction());
        assertEquals(times, expectationThatCallsbackObject.getTimes());
        assertEquals(timeToLive, expectationThatCallsbackObject.getTimeToLive());
    }

    @Test
    public void shouldAllowForNulls() {
        // when
        Expectation expectation = new Expectation(null, null, TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward((HttpForward)null).thenCallback((HttpClassCallback)null).thenCallback((HttpObjectCallback)null);

        // then
        expectation.setNotUnlimitedResponses();
        assertTrue(expectation.matches(null));
        assertTrue(expectation.matches(new HttpRequest()));
        assertFalse(expectation.contains(null));
        assertNull(expectation.getHttpRequest());
        assertNull(expectation.getHttpResponse());
        assertNull(expectation.getHttpForward());
        assertNull(expectation.getHttpClassCallback());
        assertNull(expectation.getHttpObjectCallback());
        assertNull(expectation.getTimes());
    }

    @Test
    public void shouldMatchCorrectly() {
        // when request null should return true
        assertTrue(new Expectation(null, null, TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward((HttpForward)null).matches(null));
        assertTrue(new Expectation(null, Times.unlimited(), TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward((HttpForward)null).matches(null));

        // when basic matching request should return true
        assertTrue(new Expectation(request(), null, TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward((HttpForward)null).matches(request()));
        assertTrue(new Expectation(request(), Times.unlimited(), TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward((HttpForward)null).matches(request()));

        // when un-matching request should return false
        assertFalse(new Expectation(request().withPath("some_path"), null, TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward((HttpForward)null).matches(request().withPath("some_other_path")));
        assertFalse(new Expectation(request().withPath("some_path"), Times.unlimited(), TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward((HttpForward)null).matches(request().withPath("some_other_path")));
        assertFalse(new Expectation(request().withPath("some_path"), Times.once(), TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward((HttpForward)null).matches(request().withPath("some_other_path")));

        // when no times left should return false
        assertFalse(new Expectation(null, Times.exactly(0), TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward((HttpForward)null).matches(null));
        assertFalse(new Expectation(request(), Times.exactly(0), TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward((HttpForward)null).matches(request()));
        assertFalse(new Expectation(request().withPath("un-matching"), Times.exactly(0), TimeToLive.unlimited()).thenRespond((HttpResponse)null).thenForward((HttpForward)null).matches(request()));

        // when ttl expired should return false
        assertFalse(new Expectation(null, Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0l)).thenRespond((HttpResponse)null).thenForward((HttpForward)null).matches(null));
        assertFalse(new Expectation(request(), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0l)).thenRespond((HttpResponse)null).thenForward((HttpForward)null).matches(request()));
        assertFalse(new Expectation(request().withPath("un-matching"), Times.unlimited(), TimeToLive.exactly(TimeUnit.MICROSECONDS, 0l)).thenRespond((HttpResponse)null).thenForward((HttpForward)null).matches(request()));
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
    public void shouldPreventResponseAfterClassCallback() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpClassCallback httpClassCallback = new HttpClassCallback();
        HttpResponse httpResponse = new HttpResponse();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenCallback(httpClassCallback).thenRespond(httpResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventResponseAfterObjectCallback() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback();
        HttpResponse httpResponse = new HttpResponse();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenCallback(httpObjectCallback).thenRespond(httpResponse);
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
    public void shouldPreventForwardAfterClassCallback() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpClassCallback httpClassCallback = new HttpClassCallback();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenCallback(httpClassCallback).thenForward(httpForward);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventForwardAfterObjectCallback() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback();
        HttpForward httpForward = new HttpForward();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenCallback(httpObjectCallback).thenForward(httpForward);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventClassCallbackAfterForward() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpForward httpForward = new HttpForward();
        HttpClassCallback httpClassCallback = new HttpClassCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenForward(httpForward).thenCallback(httpClassCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventClassCallbackAfterError() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpError httpError = new HttpError();
        HttpClassCallback httpClassCallback = new HttpClassCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenError(httpError).thenCallback(httpClassCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventClassCallbackAfterResponse() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpClassCallback httpClassCallback = new HttpClassCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponse).thenCallback(httpClassCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventObjectCallbackAfterForward() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpForward httpForward = new HttpForward();
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenForward(httpForward).thenCallback(httpObjectCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventObjectCallbackAfterError() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpError httpError = new HttpError();
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenError(httpError).thenCallback(httpObjectCallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldPreventObjectCallbackAfterResponse() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpObjectCallback httpObjectCallback = new HttpObjectCallback();

        // then
        new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponse).thenCallback(httpObjectCallback);
    }
}
