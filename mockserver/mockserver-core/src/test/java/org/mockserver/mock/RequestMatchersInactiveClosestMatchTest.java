package org.mockserver.mock;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_NOT_MATCHED;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * An expectation whose Times are used up, or whose time to live has expired, stays in the store until lazy
 * removal runs, and fails every request without recording a difference. It must not be offered as the closest
 * match: with no differences it would outrank every real near miss, and the 404 hint header would be dropped.
 * The scheduler is a mock, so lazy removal never runs here.
 */
public class RequestMatchersInactiveClosestMatchTest {

    private static final HttpRequest NEAR_MISS = request().withMethod("GET").withPath("/orders").withHeader("X-Token", "wrong");

    @Test
    public void hintSkipsAUsedUpExpectationAndNamesTheNearMiss() {
        RequestMatchers requestMatchers = requestMatchers(configuration().detailedMatchFailures(true), new MockServerLogger());
        addUsedUpOnceAndNearMiss(requestMatchers);

        RequestMatchers.ClosestMatchHint hint = requestMatchers.findClosestMatchHint(NEAR_MISS.clone());

        assertThat(hint, notNullValue());
        assertThat(hint.getExpectationId(), is("near"));
        assertThat(hint.getDifferences().isEmpty(), is(false));
        // a warm memo replay must skip it too
        assertThat(requestMatchers.findClosestMatchHint(NEAR_MISS.clone()).getExpectationId(), is("near"));
    }

    @Test
    public void hintSkipsAnExpiredExpectation() {
        RequestMatchers requestMatchers = requestMatchers(configuration().detailedMatchFailures(true), new MockServerLogger());
        Expectation expired = new Expectation(request().withMethod("GET").withPath("/other"), Times.unlimited(), TimeToLive.exactly(TimeUnit.HOURS, 1L), 10)
            .withId("expired").thenRespond(response());
        requestMatchers.add(expired, API);
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/orders").withHeader("X-Token", "right")).withId("near").thenRespond(response()), API);
        // one difference each under fail-fast, so the first in serving order wins while it is alive
        assertThat(requestMatchers.findClosestMatchHint(NEAR_MISS.clone()).getExpectationId(), is("expired"));

        storedExpectation(requestMatchers, "expired").getTimeToLive().setEndDate(1L);
        assertThat(storedExpectation(requestMatchers, "expired").isActive(), is(false));

        assertThat(requestMatchers.findClosestMatchHint(NEAR_MISS.clone()).getExpectationId(), is("near"));
    }

    @Test
    public void hintIsNullWhenOnlyInactiveExpectationsRemain() {
        RequestMatchers requestMatchers = requestMatchers(configuration().detailedMatchFailures(true), new MockServerLogger());
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/once"), Times.once(), TimeToLive.unlimited(), 0).withId("once").thenRespond(response()), API);
        assertThat(requestMatchers.firstMatchingExpectation(request().withMethod("GET").withPath("/once")), notNullValue());

        assertThat(requestMatchers.findClosestMatchHint(NEAR_MISS.clone()), nullValue());
    }

    @Test
    public void infoClosestMatchLineSkipsAUsedUpExpectation() {
        MockServerLogger logger = infoLogger();
        RequestMatchers requestMatchers = requestMatchers(configuration().detailedMatchFailures(true), logger);
        addUsedUpOnceAndNearMiss(requestMatchers);

        assertThat(requestMatchers.firstMatchingExpectation(NEAR_MISS.clone()), nullValue());

        assertThat(closestMatchLineExpectationIds(logger), contains("near"));
    }

    @Test
    public void infoClosestMatchLineSkipsAUsedUpExpectationWhoseResponseIsStillInProgress() {
        MockServerLogger logger = infoLogger();
        RequestMatchers requestMatchers = requestMatchers(configuration().detailedMatchFailures(true), logger);
        addUsedUpOnceAndNearMiss(requestMatchers);
        matcherOf(requestMatchers, "once").setResponseInProgress(true);

        assertThat(requestMatchers.firstMatchingExpectation(NEAR_MISS.clone()), nullValue());

        assertThat(closestMatchLineExpectationIds(logger), contains("near"));
    }

    @Test
    public void infoClosestMatchLineSkipsAUsedUpExpectationOutsideTheCandidateBucket() {
        MockServerLogger logger = infoLogger();
        RequestMatchers requestMatchers = requestMatchers(configuration().detailedMatchFailures(true), logger).withCandidateIndexThreshold(1);
        addUsedUpOnceAndNearMiss(requestMatchers);

        assertThat(requestMatchers.firstMatchingExpectation(NEAR_MISS.clone()), nullValue());

        assertThat(closestMatchLineExpectationIds(logger), contains("near"));
    }

    @Test
    public void infoClosestMatchLineIsOmittedWhenOnlyInactiveExpectationsRemain() {
        MockServerLogger logger = infoLogger();
        RequestMatchers requestMatchers = requestMatchers(configuration().detailedMatchFailures(true), logger);
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/once"), Times.once(), TimeToLive.unlimited(), 0).withId("once").thenRespond(response()), API);
        assertThat(requestMatchers.firstMatchingExpectation(request().withMethod("GET").withPath("/once")), notNullValue());

        assertThat(requestMatchers.firstMatchingExpectation(NEAR_MISS.clone()), nullValue());

        assertThat(closestMatchLineExpectationIds(logger), is(empty()));
    }

    /** "once" sorts ahead of "near" (higher priority) and is used up by one matching request. */
    private static void addUsedUpOnceAndNearMiss(RequestMatchers requestMatchers) {
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/once"), Times.once(), TimeToLive.unlimited(), 10).withId("once").thenRespond(response()), API);
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/orders").withHeader("X-Token", "right")).withId("near").thenRespond(response()), API);
        assertThat(requestMatchers.firstMatchingExpectation(request().withMethod("GET").withPath("/once")), notNullValue());
        // its response has been written
        matcherOf(requestMatchers, "once").setResponseInProgress(false);
        Expectation once = storedExpectation(requestMatchers, "once");
        assertThat("once() must be used up but still stored", once.isActive(), is(false));
    }

    private static RequestMatchers requestMatchers(Configuration configuration, MockServerLogger logger) {
        return new RequestMatchers(configuration, logger, mock(Scheduler.class), mock(WebSocketClientRegistry.class));
    }

    private static MockServerLogger infoLogger() {
        MockServerLogger logger = mock(MockServerLogger.class);
        when(logger.isEnabledForInstance(any(Level.class))).thenAnswer(invocation -> {
            Level level = invocation.getArgument(0);
            return level.toInt() >= Level.INFO.toInt();
        });
        return logger;
    }

    private static List<String> closestMatchLineExpectationIds(MockServerLogger logger) {
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(logger, atLeastOnce()).logEvent(captor.capture());
        return captor.getAllValues().stream()
            .filter(logEntry -> logEntry.getType() == EXPECTATION_NOT_MATCHED)
            .filter(logEntry -> logEntry.getMessageFormat() != null && logEntry.getMessageFormat().startsWith("closest expectation:"))
            .map(logEntry -> logEntry.getExpectation() == null ? "null" : logEntry.getExpectation().getId())
            .collect(Collectors.toList());
    }

    private static HttpRequestMatcher matcherOf(RequestMatchers requestMatchers, String id) {
        return requestMatchers.sortedRequestMatchers().stream()
            .filter(matcher -> matcher.getExpectation() != null && id.equals(matcher.getExpectation().getId()))
            .findFirst().orElseThrow();
    }

    private static Expectation storedExpectation(RequestMatchers requestMatchers, String id) {
        Expectation expectation = matcherOf(requestMatchers, id).getExpectation();
        assertThat(expectation, not(nullValue()));
        return expectation;
    }
}
