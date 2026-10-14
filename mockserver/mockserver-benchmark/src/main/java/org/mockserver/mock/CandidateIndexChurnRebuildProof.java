package org.mockserver.mock;

import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.collections.CircularPriorityQueue;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ExpectationId;
import org.mockserver.scheduler.Scheduler;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Standalone proof (NOT a JMH benchmark) that the CHURN arm of
 * {@link org.mockserver.benchmark.CandidateIndexChurnBenchmark} genuinely forces a rebuild of the
 * sorted matcher snapshot — the mechanism it measures — rather than measuring the wrong subject.
 *
 * <p>Lives in {@code org.mockserver.mock} so it can read the package-private
 * {@link RequestMatchers#matchersModificationCountForTesting()}. It reaches the private
 * {@code httpRequestMatchers} {@link CircularPriorityQueue} by reflection to compare
 * {@link CircularPriorityQueue#toSortedList()} INSTANCE IDENTITY across calls:
 * <ul>
 *   <li>with no mutation the same cached list instance is returned (cache hit — the STATIC arm);</li>
 *   <li>the churn writer's {@code clear(id)}+{@code add} makes the next {@code toSortedList()}
 *       return a DIFFERENT instance (a rebuild — the CHURN arm), and bumps the modification
 *       counter each time.</li>
 * </ul>
 *
 * <p>Run: {@code java -cp <benchmark-cp> org.mockserver.mock.CandidateIndexChurnRebuildProof}
 * (see {@code run-g1-churn.sh}). Exits non-zero if any invariant fails.
 */
public class CandidateIndexChurnRebuildProof {

    private static final String CHURN_ID = "g1-churn-expectation";

    public static void main(String[] args) throws Exception {
        int n = 1000;
        RequestMatchers requestMatchers = new RequestMatchers(
            Configuration.configuration(),
            new MockServerLogger(),
            mock(Scheduler.class),
            mock(WebSocketClientRegistry.class)
        );
        requestMatchers.withCandidateIndexThreshold(2);
        for (int i = 0; i < n; i++) {
            requestMatchers.add(
                new Expectation(request().withMethod("GET").withPath("/exact/path-" + i))
                    .thenRespond(response().withBody("e" + i)),
                API
            );
        }
        requestMatchers.add(
            new Expectation(request().withMethod("GET").withPath("/churn/only"))
                .thenRespond(response().withBody("c"))
                .withId(CHURN_ID),
            API
        );

        Field field = RequestMatchers.class.getDeclaredField("httpRequestMatchers");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        CircularPriorityQueue<String, Object, ?> cpq =
            (CircularPriorityQueue<String, Object, ?>) field.get(requestMatchers);

        boolean ok = true;

        // (1) STATIC: no mutation between reads -> the SAME cached list instance is returned.
        List<?> a = cpq.toSortedList();
        List<?> b = cpq.toSortedList();
        boolean staticReuse = (a == b);
        ok &= staticReuse;
        System.out.println("STATIC toSortedList reuses cached instance (a == b): " + staticReuse
            + "  [a.size=" + a.size() + "]");

        // (2) CHURN: one writer mutation between reads -> a DIFFERENT instance (a rebuild),
        // and the modification counter advances.
        long genBefore = requestMatchers.matchersModificationCountForTesting();
        requestMatchers.clear(ExpectationId.expectationId(CHURN_ID), "proof");
        requestMatchers.add(
            new Expectation(request().withMethod("GET").withPath("/churn/only"))
                .thenRespond(response().withBody("c"))
                .withId(CHURN_ID),
            API
        );
        long genAfter = requestMatchers.matchersModificationCountForTesting();
        List<?> c = cpq.toSortedList();
        boolean rebuilt = (c != b);
        boolean genBumped = (genAfter > genBefore);
        ok &= rebuilt;
        ok &= genBumped;
        System.out.println("CHURN toSortedList returns a fresh instance (c != b): " + rebuilt
            + "  [c.size=" + c.size() + "]");
        System.out.println("CHURN modification counter advanced: " + genBumped
            + "  (before=" + genBefore + " after=" + genAfter + " delta=" + (genAfter - genBefore) + ")");

        // (3) After the churn read, with no further mutation, the cache is warm again.
        List<?> d = cpq.toSortedList();
        boolean rewarmed = (d == c);
        ok &= rewarmed;
        System.out.println("CHURN cache re-warms after rebuild (d == c): " + rewarmed);

        System.out.println(ok ? "PROOF: PASS" : "PROOF: FAIL");
        if (!ok) {
            System.exit(1);
        }
    }
}
