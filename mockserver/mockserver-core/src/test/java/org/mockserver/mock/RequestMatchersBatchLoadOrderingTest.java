package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Ordering / priority preservation for a large batch registered in one
 * {@link RequestMatchers#update(Expectation[], org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause)}
 * call — the path a JSON {@code initializationJsonPath} takes at startup.
 *
 * <p>The registration path was made linear (an O(1) size counter in
 * {@link org.mockserver.collections.CircularPriorityQueue} replacing an O(n) size() call per
 * add). The speed-up must not change the user-visible evaluation order: first-match-wins,
 * higher priority first, and equal priority broken by created (insertion) time. These tests
 * would fail if the batch registration re-ordered expectations.
 */
public class RequestMatchersBatchLoadOrderingTest {

    private RequestMatchers newMatchers() {
        return new RequestMatchers(
            configuration().maxExpectations(20000),
            new MockServerLogger(),
            mock(Scheduler.class),
            mock(WebSocketClientRegistry.class)
        );
    }

    private static Expectation overlapping(String id, int priority) {
        // every expectation matches the SAME request, so which one firstMatchingExpectation
        // returns is decided entirely by the priority/created ordering, not by the matcher.
        return new Expectation(request().withMethod("GET").withPath("/overlap"),
            Times.unlimited(), TimeToLive.unlimited(), priority)
            .withId(id)
            .thenRespond(response().withBody(id));
    }

    @Test
    public void highestPriorityWinsAmongLargeOverlappingBatchRegisteredInOneUpdate() {
        // given - many overlapping expectations, one clear highest-priority winner, added scrambled
        RequestMatchers matchers = newMatchers();
        List<Expectation> batch = new ArrayList<>();
        for (int i = 0; i < 4000; i++) {
            batch.add(overlapping("prio-" + i, i % 50));
        }
        Expectation winner = overlapping("the-winner", 1000);
        // place the winner in the middle of the batch so position cannot be what selects it
        batch.add(2000, winner);

        // when - one batch registration
        matchers.update(batch.toArray(new Expectation[0]), API);

        // then
        assertThat(matchers.size(), is(4001));
        Expectation matched = matchers.firstMatchingExpectation(request().withMethod("GET").withPath("/overlap"));
        assertThat(matched.getId(), is("the-winner"));
    }

    @Test
    public void equalPriorityResolvesToFirstRegisteredAmongLargeBatch() {
        // given - all the same priority; first-match-wins means the earliest-created (first in the
        // input array, since update propagates created order) must win
        RequestMatchers matchers = newMatchers();
        List<Expectation> batch = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            batch.add(overlapping("same-prio-" + i, 7));
        }

        // when
        matchers.update(batch.toArray(new Expectation[0]), API);

        // then - the first expectation in the input array is the one served
        Expectation matched = matchers.firstMatchingExpectation(request().withMethod("GET").withPath("/overlap"));
        assertThat(matched.getId(), is("same-prio-0"));
    }

    @Test
    public void batchRegistrationOrderMatchesIncrementalAddOrder() {
        // given - the SAME expectations registered two ways: one batch update vs one-at-a-time add.
        // The served expectation for the overlapping request must be identical, proving the batch
        // path does not re-order relative to the incremental path.
        List<Expectation> batch = new ArrayList<>();
        for (int i = 0; i < 1500; i++) {
            batch.add(overlapping("e-" + i, i % 13));
        }

        RequestMatchers batchMatchers = newMatchers();
        batchMatchers.update(batch.toArray(new Expectation[0]), API);

        RequestMatchers incrementalMatchers = newMatchers();
        for (Expectation expectation : batch) {
            incrementalMatchers.add(expectation.clone(), API);
        }

        // then
        Expectation viaBatch = batchMatchers.firstMatchingExpectation(request().withMethod("GET").withPath("/overlap"));
        Expectation viaIncremental = incrementalMatchers.firstMatchingExpectation(request().withMethod("GET").withPath("/overlap"));
        assertThat(viaBatch.getId(), is(viaIncremental.getId()));
    }

    @Test
    public void repeatedIdKeepsTheLastEntryServedFromTheFirstEntrysPlaceAsIncrementalAddDoes() {
        // given - 'dup' twice, with an equal-priority expectation for the same request between them
        List<Expectation> batch = List.of(
            overlapping("dup", 0).thenRespond(response().withBody("first")),
            overlapping("between", 0),
            overlapping("dup", 0).thenRespond(response().withBody("last"))
        );

        // when - registered as one batch, and as one upsert per entry
        RequestMatchers batchMatchers = newMatchers();
        batchMatchers.update(batch.stream().map(Expectation::clone).toArray(Expectation[]::new), API);
        RequestMatchers incrementalMatchers = newMatchers();
        for (Expectation expectation : batch) {
            incrementalMatchers.add(expectation.clone(), API);
        }

        // then - both keep the last 'dup' and serve it ahead of 'between'
        assertThat(servedBodies(batchMatchers), is(List.of("last", "between")));
        assertThat(servedBodies(incrementalMatchers), is(List.of("last", "between")));
    }

    @Test
    public void repeatedIdInABatchReplacingAStoredExpectationKeepsTheStoredExpectationsPlace() {
        // given - 'dup' already stored ahead of 'between'
        RequestMatchers matchers = newMatchers();
        matchers.update(new Expectation[]{
            overlapping("dup", 0).thenRespond(response().withBody("stored")),
            overlapping("between", 0)
        }, API);

        // when - a later batch repeats 'dup'
        matchers.update(new Expectation[]{
            overlapping("dup", 0).thenRespond(response().withBody("first")),
            overlapping("between", 0),
            overlapping("dup", 0).thenRespond(response().withBody("last"))
        }, API);

        // then
        assertThat(servedBodies(matchers), is(List.of("last", "between")));
    }

    private static List<String> servedBodies(RequestMatchers matchers) {
        List<String> bodies = new ArrayList<>();
        for (Expectation expectation : matchers.retrieveActiveExpectations(null)) {
            bodies.add(((org.mockserver.model.HttpResponse) expectation.getAction()).getBodyAsString());
        }
        return bodies;
    }
}
