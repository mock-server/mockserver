package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.*;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

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

    @Test
    public void shouldAllowResponseAfterForward() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpForward httpForward = new HttpForward();

        // when
        Expectation expectation = new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenForward(httpForward).thenRespond(httpResponse);

        // then
        assertEquals(httpResponse, expectation.getHttpResponse());
        assertEquals(httpForward, expectation.getHttpForward());
    }

    @Test
    public void shouldAllowResponseAfterError() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpError httpError = new HttpError();

        // when
        Expectation expectation = new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenError(httpError).thenRespond(httpResponse);

        // then
        assertEquals(httpResponse, expectation.getHttpResponse());
        assertEquals(httpError, expectation.getHttpError());
    }

    @Test
    public void shouldAllowForwardAfterResponse() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        HttpResponse httpResponse = new HttpResponse();
        HttpForward httpForward = new HttpForward();

        // when
        Expectation expectation = new Expectation(httpRequest, Times.once(), TimeToLive.unlimited(), 0).thenRespond(httpResponse).thenForward(httpForward);

        // then
        assertEquals(httpResponse, expectation.getHttpResponse());
        assertEquals(httpForward, expectation.getHttpForward());
    }

    @Test
    public void shouldReturnPrimaryActionWhenFlagged() {
        // given
        HttpResponse httpResponse = response().withPrimary(false);
        HttpForward httpForward = new HttpForward().withHost("localhost").withPrimary(true);

        // when
        Expectation expectation = new Expectation(request())
            .thenRespond(httpResponse)
            .thenForward(httpForward);

        // then
        assertEquals(httpForward, expectation.getPrimaryAction());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenMultipleActionsAndNoPrimary() {
        // given
        HttpResponse httpResponse = response();
        HttpForward httpForward = new HttpForward().withHost("localhost");

        // when
        Expectation expectation = new Expectation(request())
            .thenRespond(httpResponse)
            .thenForward(httpForward);

        // then - throws
        expectation.getPrimaryAction();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowWhenMultipleActionsAndMultiplePrimaries() {
        // given
        HttpResponse httpResponse = response().withPrimary(true);
        HttpForward httpForward = new HttpForward().withHost("localhost").withPrimary(true);

        // when
        Expectation expectation = new Expectation(request())
            .thenRespond(httpResponse)
            .thenForward(httpForward);

        // then - throws
        expectation.getPrimaryAction();
    }

    @Test
    public void shouldReturnSecondaryActions() {
        // given
        HttpResponse httpResponse = response().withPrimary(true);
        HttpForward httpForward = new HttpForward().withHost("localhost");

        // when
        Expectation expectation = new Expectation(request())
            .thenRespond(httpResponse)
            .thenForward(httpForward);

        // then
        assertThat(expectation.getSecondaryActions().size(), is(1));
        assertEquals(httpForward, expectation.getSecondaryActions().get(0));
    }

    @Test
    public void shouldReturnEmptySecondaryActionsForSingleAction() {
        // given
        HttpResponse httpResponse = response();

        // when
        Expectation expectation = new Expectation(request())
            .thenRespond(httpResponse);

        // then
        assertThat(expectation.getSecondaryActions().size(), is(0));
    }

    @Test
    public void shouldReturnEmptySecondaryActionsForNoAction() {
        // when
        Expectation expectation = new Expectation(request());

        // then
        assertThat(expectation.getSecondaryActions().size(), is(0));
    }

    @Test
    public void shouldGetActionDelegatesToPrimaryAction() {
        // given
        HttpResponse httpResponse = response().withPrimary(false);
        HttpForward httpForward = new HttpForward().withHost("localhost").withPrimary(true);

        // when
        Expectation expectation = new Expectation(request())
            .thenRespond(httpResponse)
            .thenForward(httpForward);

        // then
        assertEquals(httpForward, expectation.getAction());
        assertEquals(expectation.getPrimaryAction(), expectation.getAction());
    }

    @Test
    public void shouldReturnNullPrimaryActionWhenNoActions() {
        // when
        Expectation expectation = new Expectation(request());

        // then
        assertNull(expectation.getPrimaryAction());
        assertNull(expectation.getAction());
    }

    @Test
    public void shouldSetAndGetPercentage() {
        Expectation expectation = new Expectation(request()).withPercentage(50);
        assertThat(expectation.getPercentage(), is(50));
    }

    @Test
    public void shouldReturnNullPercentageByDefault() {
        Expectation expectation = new Expectation(request());
        assertThat(expectation.getPercentage(), nullValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectPercentageBelow0() {
        new Expectation(request()).withPercentage(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectPercentageAbove100() {
        new Expectation(request()).withPercentage(101);
    }

    @Test
    public void shouldAcceptPercentageBoundaryValues() {
        assertThat(new Expectation(request()).withPercentage(0).getPercentage(), is(0));
        assertThat(new Expectation(request()).withPercentage(100).getPercentage(), is(100));
    }

    @Test
    public void shouldMatchByPercentageWhenNull() {
        assertTrue(new Expectation(request()).matchesByPercentage());
    }

    @Test
    public void shouldMatchByPercentageWhen100() {
        assertTrue(new Expectation(request()).withPercentage(100).matchesByPercentage());
    }

    @Test
    public void shouldNotMatchByPercentageWhen0() {
        assertFalse(new Expectation(request()).withPercentage(0).matchesByPercentage());
    }

    @Test
    public void shouldMatchByPercentageStatistically() {
        Expectation expectation = new Expectation(request()).withPercentage(50);
        int matchCount = 0;
        int iterations = 10000;
        for (int i = 0; i < iterations; i++) {
            if (expectation.matchesByPercentage()) {
                matchCount++;
            }
        }
        assertTrue("Expected ~50% matches but got " + matchCount, matchCount > 3000 && matchCount < 7000);
    }

    @Test
    public void shouldIncludePercentageInClone() {
        Expectation original = new Expectation(request()).withPercentage(75).thenRespond(response());
        Expectation clone = original.clone();
        assertThat(clone.getPercentage(), is(75));
    }

    @Test
    public void shouldIncludePercentageInEquals() {
        Expectation a = new Expectation(request(), Times.unlimited(), TimeToLive.unlimited(), 0).withPercentage(50);
        Expectation b = new Expectation(request(), Times.unlimited(), TimeToLive.unlimited(), 0).withPercentage(50);
        Expectation c = new Expectation(request(), Times.unlimited(), TimeToLive.unlimited(), 0).withPercentage(75);
        assertEquals(a, b);
        assertFalse(a.equals(c));
    }

    @Test
    public void shouldIncludePercentageInHashCode() {
        Expectation a = new Expectation(request(), Times.unlimited(), TimeToLive.unlimited(), 0).withPercentage(50);
        Expectation b = new Expectation(request(), Times.unlimited(), TimeToLive.unlimited(), 0).withPercentage(50);
        Expectation c = new Expectation(request(), Times.unlimited(), TimeToLive.unlimited(), 0).withPercentage(75);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotSame(a.hashCode(), c.hashCode());
    }

    @Test
    public void shouldSelectSequentialResponsesByDefault() {
        HttpResponse r1 = response("one");
        HttpResponse r2 = response("two");
        HttpResponse r3 = response("three");
        Expectation expectation = new Expectation(request())
            .thenRespond(Arrays.asList(r1, r2, r3));

        expectation.consumeMatch();
        assertThat(((HttpResponse) expectation.getAction()).getBodyAsString(), is("one"));

        expectation.consumeMatch();
        assertThat(((HttpResponse) expectation.getAction()).getBodyAsString(), is("two"));

        expectation.consumeMatch();
        assertThat(((HttpResponse) expectation.getAction()).getBodyAsString(), is("three"));

        expectation.consumeMatch();
        assertThat(((HttpResponse) expectation.getAction()).getBodyAsString(), is("one"));
    }

    @Test
    public void shouldSelectSequentialResponsesExplicitly() {
        HttpResponse r1 = response("a");
        HttpResponse r2 = response("b");
        Expectation expectation = new Expectation(request())
            .thenRespond(Arrays.asList(r1, r2))
            .withResponseMode(ResponseMode.SEQUENTIAL);

        expectation.consumeMatch();
        assertThat(((HttpResponse) expectation.getAction()).getBodyAsString(), is("a"));

        expectation.consumeMatch();
        assertThat(((HttpResponse) expectation.getAction()).getBodyAsString(), is("b"));

        expectation.consumeMatch();
        assertThat(((HttpResponse) expectation.getAction()).getBodyAsString(), is("a"));
    }

    @Test
    public void shouldSelectRandomResponses() {
        HttpResponse r1 = response("x");
        HttpResponse r2 = response("y");
        HttpResponse r3 = response("z");
        Expectation expectation = new Expectation(request())
            .thenRespond(Arrays.asList(r1, r2, r3))
            .withResponseMode(ResponseMode.RANDOM);

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            expectation.consumeMatch();
            seen.add(((HttpResponse) expectation.getAction()).getBodyAsString());
        }
        assertTrue("Expected multiple different responses in RANDOM mode, but got: " + seen, seen.size() > 1);
    }

    @Test
    public void shouldReturnSingleResponseFromHttpResponses() {
        HttpResponse r1 = response("only");
        Expectation expectation = new Expectation(request())
            .thenRespond(Arrays.asList(r1));

        expectation.consumeMatch();
        assertThat(((HttpResponse) expectation.getAction()).getBodyAsString(), is("only"));

        expectation.consumeMatch();
        assertThat(((HttpResponse) expectation.getAction()).getBodyAsString(), is("only"));
    }

    @Test
    public void shouldPrioritizeHttpResponsesOverHttpResponse() {
        HttpResponse single = response("single");
        HttpResponse multi1 = response("multi1");
        HttpResponse multi2 = response("multi2");
        Expectation expectation = new Expectation(request())
            .thenRespond(single)
            .thenRespond(Arrays.asList(multi1, multi2));

        expectation.consumeMatch();
        assertThat(((HttpResponse) expectation.getAction()).getBodyAsString(), is("multi1"));
    }

    @Test
    public void shouldFallBackToHttpResponseWhenHttpResponsesEmpty() {
        HttpResponse single = response("fallback");
        Expectation expectation = new Expectation(request()).thenRespond(single);

        assertThat(((HttpResponse) expectation.getAction()).getBodyAsString(), is("fallback"));
    }

    @Test
    public void shouldIncludeHttpResponsesInClone() {
        HttpResponse r1 = response("one");
        HttpResponse r2 = response("two");
        Expectation original = new Expectation(request())
            .thenRespond(Arrays.asList(r1, r2))
            .withResponseMode(ResponseMode.RANDOM);
        Expectation clone = original.clone();

        assertThat(clone.getHttpResponses().size(), is(2));
        assertThat(clone.getResponseMode(), is(ResponseMode.RANDOM));
    }

    @Test
    public void shouldIncludeHttpResponsesInEquals() {
        HttpResponse r1 = response("one");
        HttpResponse r2 = response("two");
        Expectation a = new Expectation(request(), Times.unlimited(), TimeToLive.unlimited(), 0)
            .thenRespond(Arrays.asList(r1, r2))
            .withResponseMode(ResponseMode.SEQUENTIAL);
        Expectation b = new Expectation(request(), Times.unlimited(), TimeToLive.unlimited(), 0)
            .thenRespond(Arrays.asList(r1, r2))
            .withResponseMode(ResponseMode.SEQUENTIAL);
        Expectation c = new Expectation(request(), Times.unlimited(), TimeToLive.unlimited(), 0)
            .thenRespond(Arrays.asList(r1, r2))
            .withResponseMode(ResponseMode.RANDOM);
        assertEquals(a, b);
        assertFalse(a.equals(c));
    }

    @Test
    public void shouldTrackMatchCount() {
        Expectation expectation = new Expectation(request()).thenRespond(response());

        assertThat(expectation.getMatchCount(), is(0));

        expectation.consumeMatch();
        assertThat(expectation.getMatchCount(), is(1));

        expectation.consumeMatch();
        assertThat(expectation.getMatchCount(), is(2));

        expectation.consumeMatch();
        assertThat(expectation.getMatchCount(), is(3));
    }

    @Test
    public void shouldNotIncrementMatchCountWhenTimesExhausted() {
        Expectation expectation = new Expectation(request(), Times.exactly(2), TimeToLive.unlimited(), 0)
            .thenRespond(response());

        assertTrue(expectation.consumeMatch());
        assertThat(expectation.getMatchCount(), is(1));

        assertTrue(expectation.consumeMatch());
        assertThat(expectation.getMatchCount(), is(2));

        assertFalse(expectation.consumeMatch());
        assertThat(expectation.getMatchCount(), is(2));
    }

    @Test
    public void shouldIncludeScenarioFieldsInClone() {
        Expectation original = new Expectation(request())
            .thenRespond(response())
            .withScenarioName("TestScenario")
            .withScenarioState("Started")
            .withNewScenarioState("Step2");
        Expectation clone = original.clone();

        assertThat(clone.getScenarioName(), is("TestScenario"));
        assertThat(clone.getScenarioState(), is("Started"));
        assertThat(clone.getNewScenarioState(), is("Step2"));
    }

    @Test
    public void shouldIncludeScenarioFieldsInEquals() {
        Expectation a = new Expectation(request(), Times.unlimited(), TimeToLive.unlimited(), 0)
            .withScenarioName("S1")
            .withScenarioState("Started");
        Expectation b = new Expectation(request(), Times.unlimited(), TimeToLive.unlimited(), 0)
            .withScenarioName("S1")
            .withScenarioState("Started");
        Expectation c = new Expectation(request(), Times.unlimited(), TimeToLive.unlimited(), 0)
            .withScenarioName("S1")
            .withScenarioState("Different");
        assertEquals(a, b);
        assertFalse(a.equals(c));
    }
}
