package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatchDifference;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.ExpectationId;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.state.ExpectationEntry;
import org.mockserver.state.InMemoryStateBackend;
import org.mockserver.state.InvalidationListener;
import org.mockserver.state.KeyValueStore;
import org.mockserver.state.Versioned;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.ConditionalRequestDefinition.requestIf;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.Not.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.OpenAPIDefinition.openAPI;

/**
 * Differential evidence for the capped, memoised 404 closest-match scan: every hint must equal what the
 * original full scan (copied verbatim below as {@link #reference}) returns for the same store and request,
 * across warm memo hits, every kind of store change, expiry and a fail-fast change.
 */
public class RequestMatchersClosestMatchMemoTest {

    private static final String[] METHODS = {"GET", "POST", "PUT", "DELETE", ""};
    private static final String[] PATHS = {
        "/users", "/users/1", "/users/", "/Users", "/orders/42", "/orders/x", "/admin", "/secret",
        "/once", "/twice", "/ttl", "/v1/pets/7", "/v1/pets", "/nothing", "/headers/scan", ""
    };

    private final Configuration configuration = configuration().detailedMatchFailures(true);
    private final RequestMatchers requestMatchers = new RequestMatchers(configuration, new MockServerLogger(), mock(Scheduler.class), mock(WebSocketClientRegistry.class));

    @Test
    public void hintEqualsTheFullScanAcrossStoreChangesExpiryAndFailFast() {
        List<Expectation> expectations = corpusExpectations();
        for (Expectation expectation : expectations) {
            requestMatchers.add(expectation, API);
        }
        List<HttpRequest> probes = corpusProbes(new Random(2241), 600);

        assertSameAsFullScan("initial", probes);
        long warmHits = requestMatchers.closestMatchMemoHitsForTesting();
        assertThat("the corpus repeats (method, path) pairs, so the memo must serve hits", warmHits, greaterThan(200L));

        // a higher-priority expectation that is closer than anything else for GET /orders/42
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/orders/42").withHeader("X-Added", "yes"), Times.unlimited(), TimeToLive.unlimited(), 50)
            .withId("added-priority").thenRespond(response()), API);
        assertSameAsFullScan("after add", probes);

        // remove the expectation that was closest for GET /users/1
        requestMatchers.clear(new ExpectationId().withId("users-get"), null);
        assertSameAsFullScan("after remove", probes);

        // update in place: same id, new method and path
        requestMatchers.add(new Expectation(request().withMethod("PATCH").withPath("/users/1")).withId("users-post").thenRespond(response()), API);
        assertSameAsFullScan("after in-place update", probes);

        // fail-fast off records every differing field, so the memo built with it on must not be reused
        configuration.matchersFailFast(false);
        assertSameAsFullScan("fail-fast off", probes);
        configuration.matchersFailFast(true);
        assertSameAsFullScan("fail-fast back on", probes);

        // An inactive expectation fails with no differences, so it outranks every expectation after it and
        // masks them: each phase below removes it again before the next one. Consumption and
        // expiry move neither the store generation nor the modification count (the mock scheduler never
        // runs the lazy removal), so only the memo's own rule keeps these exact.
        requestMatchers.firstMatchingExpectation(request().withMethod("GET").withPath("/once"));
        assertSameAsFullScan("after once() consumed", probes, 1);
        requestMatchers.clear(new ExpectationId().withId("once"), null);
        assertSameAsFullScan("after once() removed", probes);

        requestMatchers.firstMatchingExpectation(request().withMethod("GET").withPath("/twice"));
        assertSameAsFullScan("after exactly(2) consumed once", probes);
        requestMatchers.firstMatchingExpectation(request().withMethod("GET").withPath("/twice"));
        assertSameAsFullScan("after exactly(2) consumed", probes, 1);
        requestMatchers.clear(new ExpectationId().withId("twice"), null);
        assertSameAsFullScan("after exactly(2) removed", probes);

        Expectation ttl = requestMatchers.sortedRequestMatchers().stream()
            .map(HttpRequestMatcher::getExpectation)
            .filter(e -> e != null && "ttl".equals(e.getId()))
            .findFirst().orElseThrow();
        ttl.getTimeToLive().setEndDate(1L);
        assertThat("time-to-live must have expired", ttl.isActive(), is(false));
        assertSameAsFullScan("after ttl expiry", probes, 1);
        requestMatchers.clear(new ExpectationId().withId("ttl"), null);
        assertSameAsFullScan("after ttl removed", probes);

        requestMatchers.reset();
        assertSameAsFullScan("after reset", probes, 1);
    }

    @Test
    public void warmLookupReEvaluatesOnlyTheExpectationsThatShareTheMethodAndPath() {
        for (int i = 0; i < 200; i++) {
            requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/literal/" + i)).thenRespond(response()), API);
        }
        for (int i = 0; i < 3; i++) {
            requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/shared").withHeader("X-Tenant", "t" + i)).thenRespond(response()), API);
        }
        HttpRequest miss = request().withMethod("GET").withPath("/shared").withHeader("X-Tenant", "none");

        RequestMatchers.ClosestMatchHint cold = requestMatchers.findClosestMatchHint(miss.clone());
        RequestMatchers.ClosestMatchHint warm = requestMatchers.findClosestMatchHint(miss.clone());

        // only the first of the 200 path misses can ever be the closest, and the three header expectations are re-checked
        assertThat(requestMatchers.closestMatchMemoShapeForTesting("GET", "/shared"), is(new int[]{1, 3}));
        assertThat(requestMatchers.closestMatchMemoHitsForTesting(), is(1L));
        assertThat(describe(warm), is(describe(cold)));
        assertThat(describe(warm), is(reference(requestMatchers, miss.clone(), Integer.MAX_VALUE)));
    }

    @Test
    public void reusedDifferencesNeverCarryAnotherRequestsHeaderValues() throws Exception {
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/tenant").withHeader("X-Id", "expected")).withId("header-only").thenRespond(response()), API);
        for (int i = 0; i < 50; i++) {
            requestMatchers.add(new Expectation(request().withMethod("POST").withPath("/elsewhere/" + i)).thenRespond(response()), API);
        }
        assertEachCallerSeesOnlyItsOwnHeader(requestMatchers, "header-only");

        // a conditional's guard fails on PATH, which marks the shared context, then its else branch records
        // this request's header: the matcher must never be treated as decided by method and path alone
        RequestMatchers conditional = new RequestMatchers(configuration, new MockServerLogger(), mock(Scheduler.class), mock(WebSocketClientRegistry.class));
        conditional.add(new Expectation(requestIf(
            request().withMethod("GET").withPath("/guard"),
            request(),
            request().withHeader("X-Id", "expected")
        )).withId("conditional").thenRespond(response()), API);
        assertEachCallerSeesOnlyItsOwnHeader(conditional, "conditional");
    }

    private static void assertEachCallerSeesOnlyItsOwnHeader(RequestMatchers requestMatchers, String expectedId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<List<String>>> futures = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                final int threadId = thread;
                futures.add(executor.submit(() -> {
                    List<String> failures = new ArrayList<>();
                    for (int i = 0; i < 200; i++) {
                        String own = "caller-" + threadId + "-" + i;
                        HttpRequest miss = request().withMethod("GET").withPath("/tenant").withHeader("X-Id", own);
                        String rendered = describe(requestMatchers.findClosestMatchHint(miss));
                        if (!rendered.startsWith(expectedId + " ") || !rendered.contains(own) || rendered.replace(own, "").contains("caller-")) {
                            failures.add(rendered);
                        }
                    }
                    return failures;
                }));
            }
            for (Future<List<String>> future : futures) {
                assertThat(future.get(60, TimeUnit.SECONDS), is(empty()));
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(requestMatchers.closestMatchMemoHitsForTesting(), greaterThan(1000L));
    }

    @Test
    public void scanStopsAtTheCapAndSaysSo() {
        // without fail-fast every differing field counts, so the one expectation sharing the method is closest
        configuration.matchersFailFast(false);
        requestMatchers.withClosestMatchMaxEvaluations(5);
        for (int i = 0; i < 10; i++) {
            String method = i == 7 ? "GET" : "POST";
            requestMatchers.add(new Expectation(request().withMethod(method).withPath("/capped/" + i)).withId("capped-" + i).thenRespond(response()), API);
        }
        HttpRequest miss = request().withMethod("GET").withPath("/capped/none");

        RequestMatchers.ClosestMatchHint cold = requestMatchers.findClosestMatchHint(miss.clone());
        RequestMatchers.ClosestMatchHint warm = requestMatchers.findClosestMatchHint(miss.clone());

        assertThat(cold.isTruncated(), is(true));
        assertThat(cold.getExpectationsChecked(), is(5));
        assertThat(cold.getExpectationId(), is("capped-0"));
        assertThat(describe(cold), is(reference(requestMatchers, miss.clone(), 5)));
        assertThat(describe(warm), is(describe(cold)));
        assertThat(reference(requestMatchers, miss.clone(), Integer.MAX_VALUE), startsWith("capped-7 "));
    }

    @Test
    public void scanBelowTheCapIsNotTruncated() {
        requestMatchers.withClosestMatchMaxEvaluations(5);
        for (int i = 0; i < 5; i++) {
            requestMatchers.add(new Expectation(request().withMethod("POST").withPath("/full/" + i)).thenRespond(response()), API);
        }
        RequestMatchers.ClosestMatchHint hint = requestMatchers.findClosestMatchHint(request().withMethod("GET").withPath("/full/none"));
        assertThat(hint.isTruncated(), is(false));
    }

    @Test
    public void memoIsBoundedInEntriesAndKeyLength() {
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/bounded")).thenRespond(response()), API);
        int maxSeen = 0;
        for (int i = 0; i < RequestMatchers.CLOSEST_MATCH_MEMO_MAX_ENTRIES * 3; i++) {
            requestMatchers.findClosestMatchHint(request().withMethod("GET").withPath("/attacker/" + i));
            maxSeen = Math.max(maxSeen, requestMatchers.closestMatchMemoSizeForTesting());
        }
        assertThat(maxSeen, is(RequestMatchers.CLOSEST_MATCH_MEMO_MAX_ENTRIES));

        String longPath = "/" + "x".repeat(RequestMatchers.CLOSEST_MATCH_MEMO_MAX_KEY_LENGTH);
        HttpRequest longRequest = request().withMethod("GET").withPath(longPath);
        RequestMatchers.ClosestMatchHint hint = requestMatchers.findClosestMatchHint(longRequest.clone());
        assertThat(requestMatchers.closestMatchMemoShapeForTesting("GET", longPath), is(nullValue()));
        assertThat(describe(hint), is(reference(requestMatchers, longRequest.clone(), Integer.MAX_VALUE)));
    }

    @Test
    public void entryThatWouldReCheckTooManyMatchersStoresOnlyRescan() {
        for (int i = 0; i <= RequestMatchers.CLOSEST_MATCH_MEMO_MAX_RECHECKED; i++) {
            requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/many").withHeader("X-N", "" + i)).thenRespond(response()), API);
        }
        requestMatchers.add(new Expectation(request().withMethod("PUT").withPath("/many")).withId("put-many").thenRespond(response()), API);
        HttpRequest miss = request().withMethod("GET").withPath("/many").withHeader("X-N", "none");

        requestMatchers.findClosestMatchHint(miss.clone());
        RequestMatchers.ClosestMatchHint warm = requestMatchers.findClosestMatchHint(miss.clone());

        assertThat(requestMatchers.closestMatchMemoShapeForTesting("GET", "/many"), is(new int[]{-1, -1}));
        assertThat(describe(warm), is(reference(requestMatchers, miss.clone(), Integer.MAX_VALUE)));
    }

    @Test
    public void inPlaceUpdateIsSeenWhileItsBackendWriteIsStillInFlight() throws Exception {
        BlockingPutStateBackend backend = new BlockingPutStateBackend();
        requestMatchers.setStateBackend(backend);
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/users")).withId("moving").thenRespond(response()), API);
        HttpRequest miss = request().withMethod("GET").withPath("/x");
        requestMatchers.findClosestMatchHint(miss.clone());
        assertThat(requestMatchers.closestMatchMemoShapeForTesting("GET", "/x"), is(new int[]{1, 0}));

        // the update lands in the store before its backend write returns, which is when the modification count moves
        backend.blockNextPut();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> update = executor.submit(() -> requestMatchers.add(
                new Expectation(request().withMethod("GET").withPath("/x").withHeader("X-Required", "yes")).withId("moving").thenRespond(response()), API));
            assertThat("the update must reach its backend write", backend.putEntered.await(30, TimeUnit.SECONDS), is(true));

            String expected = reference(requestMatchers, miss.clone(), Integer.MAX_VALUE);
            assertThat(expected, startsWith("moving {HEADERS="));
            assertThat(describe(requestMatchers.findClosestMatchHint(miss.clone())), is(expected));

            backend.releasePut.countDown();
            update.get(30, TimeUnit.SECONDS);
        } finally {
            backend.releasePut.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void traceLoggingBypassesTheMemo() {
        // at TRACE every recorded difference is also logged, so a skipped evaluation would drop log lines
        Configuration traceConfiguration = configuration().detailedMatchFailures(true).logLevel("TRACE");
        RequestMatchers tracing = new RequestMatchers(traceConfiguration, new MockServerLogger(traceConfiguration, RequestMatchersClosestMatchMemoTest.class), mock(Scheduler.class), mock(WebSocketClientRegistry.class));
        tracing.add(new Expectation(request().withMethod("GET").withPath("/plain")).thenRespond(response()), API);
        HttpRequest miss = request().withMethod("GET").withPath("/other");
        tracing.findClosestMatchHint(miss.clone());
        RequestMatchers.ClosestMatchHint hint = tracing.findClosestMatchHint(miss.clone());
        assertThat(tracing.closestMatchMemoSizeForTesting(), is(0));
        assertThat(describe(hint), is(reference(tracing, miss.clone(), Integer.MAX_VALUE)));
    }

    @Test
    public void storeChangeReleasesTheMemoWithoutWaitingForTheNextMiss() {
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/held")).thenRespond(response()), API);
        requestMatchers.findClosestMatchHint(request().withMethod("GET").withPath("/other"));
        assertThat(requestMatchers.closestMatchMemoSizeForTesting(), is(1));

        requestMatchers.reset();

        assertThat(requestMatchers.closestMatchMemoSizeForTesting(), is(0));
    }

    @Test
    public void notRequestBypassesTheMemo() {
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/plain")).thenRespond(response()), API);
        HttpRequest notRequest = not(request().withMethod("GET").withPath("/other"));
        RequestMatchers.ClosestMatchHint hint = requestMatchers.findClosestMatchHint(notRequest.clone());
        assertThat(requestMatchers.closestMatchMemoSizeForTesting(), is(0));
        assertThat(describe(hint), is(reference(requestMatchers, notRequest.clone(), Integer.MAX_VALUE)));
    }

    private void assertSameAsFullScan(String phase, List<HttpRequest> probes) {
        // a phase whose hints barely vary could not tell a stale memo from a fresh scan
        assertSameAsFullScan(phase, probes, 8);
    }

    private void assertSameAsFullScan(String phase, List<HttpRequest> probes, int distinctHints) {
        Set<String> seen = new HashSet<>();
        for (HttpRequest probe : probes) {
            String expected = reference(requestMatchers, probe.clone(), RequestMatchers.DEFAULT_CLOSEST_MATCH_MAX_EVALUATIONS);
            String actual = describe(requestMatchers.findClosestMatchHint(probe.clone()));
            assertEquals(phase + " " + probe.getMethod().getValue() + " " + probe.getPath().getValue() + " " + probe.getHeaders(), expected, actual);
            seen.add(expected);
        }
        assertThat(phase + " distinct hints", seen.size(), greaterThanOrEqualTo(distinctHints));
    }

    /**
     * The closest-match scan as it was before the memo, verbatim, with the evaluation cap applied.
     */
    static String reference(RequestMatchers requestMatchers, HttpRequest httpRequest, int cap) {
        int closestMatchFailures = Integer.MAX_VALUE;
        Map<MatchDifference.Field, List<String>> closestDifferences = null;
        String closestExpectationId = null;
        List<HttpRequestMatcher> sortedMatchers = requestMatchers.sortedRequestMatchers();
        int limit = Math.min(sortedMatchers.size(), cap);
        for (HttpRequestMatcher httpRequestMatcher : sortedMatchers.subList(0, limit)) {
            MatchDifference matchDifference = new MatchDifference(true, httpRequest).suppressMatchResultLogging();
            if (!httpRequestMatcher.matches(matchDifference, httpRequest)) {
                Map<MatchDifference.Field, List<String>> differences = matchDifference.getAllDifferences();
                int failures = differences.size();
                if (failures < closestMatchFailures && httpRequestMatcher.getExpectation() != null) {
                    closestMatchFailures = failures;
                    closestDifferences = differences;
                    closestExpectationId = httpRequestMatcher.getExpectation().getId();
                }
            }
        }
        if (closestDifferences == null) {
            return "null";
        }
        return closestExpectationId + " " + closestDifferences + (limit < sortedMatchers.size() ? " truncated at " + limit : "");
    }

    static String describe(RequestMatchers.ClosestMatchHint hint) {
        if (hint == null) {
            return "null";
        }
        return hint.getExpectationId() + " " + hint.getDifferences() + (hint.isTruncated() ? " truncated at " + hint.getExpectationsChecked() : "");
    }

    private static List<Expectation> corpusExpectations() {
        List<Expectation> expectations = new ArrayList<>();
        expectations.add(new Expectation(request().withMethod("GET").withPath("/users")).withId("users-get").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("POST").withPath("/users").withBody(json("{\"name\":\"a\"}"))).withId("users-post").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("GET").withPath("/users/{id}").withPathParameter("id", "[0-9]+")).withId("users-by-id").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("GET").withPath("/orders/.*").withHeader("X-Tenant", "a")).withId("orders-regex").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("GET").withPath("/orders/42").withQueryStringParameter("q", "1")).withId("orders-query").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("GET").withPath(string("/admin", true))).withId("not-admin").thenRespond(response()));
        expectations.add(new Expectation(not(request().withMethod("GET").withPath("/secret"))).withId("not-request").thenRespond(response()));
        expectations.add(new Expectation(request().withHeader("X-Only", "header")).withId("header-only").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("GET").withPath("/once"), Times.once(), TimeToLive.unlimited(), 0).withId("once").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("GET").withPath("/twice"), Times.exactly(2), TimeToLive.unlimited(), 0).withId("twice").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("GET").withPath("/ttl"), Times.unlimited(), TimeToLive.exactly(TimeUnit.HOURS, 1L), 0).withId("ttl").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("PUT").withPath("/users").withSecure(true)).withId("secure-put").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("DELETE").withPath("/users/1").withKeepAlive(false)).withId("keepalive-delete").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("GET").withPath("/headers/scan").withHeader(header("X-Tenant", "t1"))).withId("headers-1").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("GET").withPath("/headers/scan").withHeader(header("X-Tenant", "t2"))).withId("headers-2").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("GET").withPath("/headers/scan").withCookie("c", "v")).withId("cookie").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("GET").withPath("/users"), Times.unlimited(), TimeToLive.unlimited(), 10).withId("users-priority").thenRespond(response()));
        expectations.add(new Expectation(request().withMethod("!GET").withPath("/users/")).withId("not-get").thenRespond(response()));
        expectations.add(new Expectation(requestIf(request().withMethod("GET").withPath("/users"), request(), request().withHeader("X-Tenant", "a"))).withId("conditional").thenRespond(response()));
        expectations.add(new Expectation(openAPI("org/mockserver/openapi/openapi_petstore_example.json", "showPetById")).withId("openapi-pet").thenRespond(response()));
        return expectations;
    }

    private static List<HttpRequest> corpusProbes(Random random, int count) {
        List<HttpRequest> probes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            HttpRequest probe = request()
                .withMethod(METHODS[random.nextInt(METHODS.length)])
                .withPath(PATHS[random.nextInt(PATHS.length)]);
            switch (random.nextInt(5)) {
                case 1:
                    probe.withHeader("X-Tenant", random.nextBoolean() ? "a" : "t" + random.nextInt(3));
                    break;
                case 2:
                    probe.withQueryStringParameter("q", "" + random.nextInt(2));
                    break;
                case 3:
                    probe.withBody(json("{\"name\":\"" + (random.nextBoolean() ? "a" : "b") + "\"}"));
                    break;
                case 4:
                    probe.withHeader("X-Only", random.nextBoolean() ? "header" : "other").withCookie("c", "v");
                    break;
                default:
                    break;
            }
            if (random.nextInt(4) == 0) {
                probe.withSecure(random.nextBoolean()).withKeepAlive(random.nextBoolean());
            }
            probes.add(probe);
        }
        return probes;
    }

    private static final class BlockingPutStateBackend extends InMemoryStateBackend {
        private final CountDownLatch putEntered = new CountDownLatch(1);
        private final CountDownLatch releasePut = new CountDownLatch(1);
        private final AtomicBoolean blockNext = new AtomicBoolean();
        private final KeyValueStore<ExpectationEntry> blocking;

        private BlockingPutStateBackend() {
            super(1000);
            KeyValueStore<ExpectationEntry> delegate = super.expectations();
            blocking = new KeyValueStore<>() {
                public Optional<Versioned<ExpectationEntry>> get(String key) {
                    return delegate.get(key);
                }

                public long put(String key, ExpectationEntry value) {
                    if (blockNext.compareAndSet(true, false)) {
                        putEntered.countDown();
                        try {
                            releasePut.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return delegate.put(key, value);
                }

                public Optional<Versioned<ExpectationEntry>> putIfAbsent(String key, ExpectationEntry value) {
                    return delegate.putIfAbsent(key, value);
                }

                public boolean compareAndSet(String key, long expectedVersion, ExpectationEntry value) {
                    return delegate.compareAndSet(key, expectedVersion, value);
                }

                public boolean compareAndRemove(String key, long expectedVersion) {
                    return delegate.compareAndRemove(key, expectedVersion);
                }

                public boolean remove(String key) {
                    return delegate.remove(key);
                }

                public Stream<Entry<ExpectationEntry>> entries() {
                    return delegate.entries();
                }

                public int size() {
                    return delegate.size();
                }

                public void clear() {
                    delegate.clear();
                }

                public void addInvalidationListener(InvalidationListener listener) {
                    delegate.addInvalidationListener(listener);
                }
            };
        }

        private void blockNextPut() {
            blockNext.set(true);
        }

        @Override
        public KeyValueStore<ExpectationEntry> expectations() {
            return blocking;
        }
    }
}
