package org.mockserver.state.infinispan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.mockserver.configuration.Configuration;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.state.ExpectationEntry;
import org.mockserver.state.Versioned;

import java.time.Duration;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Clustered-state negative control: <b>kill a cluster member
 * mid-run and record the survivor's behaviour</b>.
 * <p>
 * This is the in-JVM counterpart to the perf harness's clustered A/B (in
 * {@code perf-test-run.sh}). That harness measures the per-request cost of
 * membership and forms/breaks a cluster to prove its genuineness gate can red
 * ({@code PERF_CLU_BREAK=cluster} starts node B in a different cluster so the view
 * never reaches 2). Neither that harness nor the existing suite ever kills a
 * <em>formed</em> member <em>mid-run</em> and asserts what the survivor does — the
 * behaviour the mandate's central-deployment profile depends on. This test does.
 * <p>
 * <b>Why the observation is ownership-independent (the {@code 9642abe8e} trap).</b>
 * Every MockServer clustered cache is {@code REPL_SYNC}
 * ({@link InfinispanStateBackend}), so <em>every node holds a full replica of
 * every key</em>. A survivor therefore reads seeded state out of its own local
 * copy no matter which node was the key's JGroups primary owner — there is no coin
 * flip. This is exactly the property that node-local {@code Cache.evict()} does NOT
 * have (see {@code ClusteredTwoNodeTest.nodeLocalCounterEvictionIsNotReplicatedToPeer}):
 * eviction removes one node's copy, replication puts a copy on all of them. The
 * bounded-{@code Times} counter that {@code 9642abe8e} moved to a dedicated
 * replicated cache is asserted here to survive a peer death at its true decremented
 * value — NOT resurrected to its full allotment — for the same reason.
 * <p>
 * <b>What "red" means for this control.</b> The load-bearing assertion is that the
 * survivor still serves the seeded state after its peer is stopped. Its recorded
 * negative control (degrade-and-confirm-red) is that if the survivor's replica is
 * removed before the peer dies — the fleet then holds the value nowhere — the same
 * assertion goes red. That degrade is documented on
 * {@link #survivorKeepsReplicatedStateAfterPeerIsKilledMidRun()} and is exercised by
 * evicting the survivor's replicas immediately before the kill.
 * <p>
 * No Docker, no external service: the cluster forms in-JVM over the built-in
 * JGroups loopback stack, exactly as {@link ClusteredTwoNodeTest} does.
 */
@Timeout(90)
class ClusteredMemberDeathTest {

    private static final int MAX_EXPECTATIONS = 100;
    private static final Duration CLUSTER_FORMATION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration VIEW_CHANGE_TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper JSON = new ObjectMapper();

    private String clusterName;
    private InfinispanStateBackend nodeA;
    private InfinispanStateBackend nodeB;

    @BeforeEach
    void setUp() {
        clusterName = "member-death-cluster-" + System.nanoTime();
        nodeA = new InfinispanStateBackend(clusteredConfig());
        nodeB = new InfinispanStateBackend(clusteredConfig());
        awaitViewSize(nodeA, 2, CLUSTER_FORMATION_TIMEOUT);
        awaitViewSize(nodeB, 2, CLUSTER_FORMATION_TIMEOUT);
    }

    @AfterEach
    void tearDown() {
        // nodeA may already have been closed mid-test (the whole point); guard it.
        if (nodeB != null) {
            nodeB.close();
        }
        if (nodeA != null) {
            nodeA.close();
        }
    }

    private Configuration clusteredConfig() {
        return Configuration.configuration()
            .maxExpectations(MAX_EXPECTATIONS)
            .stateBackend("infinispan")
            .clusterEnabled(true)
            .clusterName(clusterName);
    }

    /**
     * The control. Seed expectation / scenario / CRUD / bounded-Times state on node
     * A, confirm REPL_SYNC has put a copy on node B, then STOP node A while node B is
     * the target — and assert node B (the survivor) both observes the death (view
     * drops to 1) and still serves every piece of seeded state, then still accepts a
     * fresh write.
     * <p>
     * <b>Degrade-and-confirm-red (recorded negative control).</b> Set
     * {@code -Dmemberdeath.degrade=evict-survivor}: the survivor's replicas are
     * evicted just before the kill, so after node A is gone the value lives nowhere
     * and every "survivor still has it" assertion fails. That is the deliberate
     * change that turns this test red.
     */
    @Test
    void survivorKeepsReplicatedStateAfterPeerIsKilledMidRun() {
        boolean degradeEvictSurvivor =
            "evict-survivor".equals(System.getProperty("memberdeath.degrade"));

        // --- seed a representative spread of state ON NODE A ------------------
        Expectation expectation = Expectation
            .when(HttpRequest.request("/survivor-path"))
            .thenRespond(HttpResponse.response("alive"));
        nodeA.expectations().put(expectation.getId(), new ExpectationEntry(expectation));

        nodeA.scenarioStates().put("survivor-scenario", "Started");

        ObjectNode crud = JSON.createObjectNode();
        crud.put("id", "e1").put("shape", "square");
        nodeA.crudEntities("openApi").put("e1", crud);

        // Bounded-Times counter: seed the full allotment, then consume one on node A
        // so the LIVE value is N-1 (the 9642abe8e cache). The survival assertion is
        // that node B holds N-1 after A dies — NOT resurrected to N.
        final int allotment = 5;
        String counterId = expectation.getId();
        long counterVersion = nodeA.sharedTimesCounters().put(counterId, allotment);
        assertTrue(nodeA.sharedTimesCounters().compareAndSet(counterId, counterVersion, allotment - 1),
            "node A should consume one from the bounded-Times counter");

        // --- confirm REPL_SYNC actually crossed the network to node B --------
        assertTrue(nodeB.expectations().get(expectation.getId()).isPresent(),
            "pre-kill: node B should hold the replicated expectation");
        assertThat("pre-kill: node B holds the decremented counter, not the full allotment",
            nodeB.sharedTimesCounters().get(counterId).map(Versioned::getValue).orElse(-1),
            is(allotment - 1));
        // CRUD namespace caches are created lazily PER NAMESPACE. A survivor can only
        // hold a REPL_SYNC replica of a namespace it has already materialised while a
        // source was live — a node that first touches "crud-openApi" AFTER the peer is
        // gone gets an empty cache (no live member to state-transfer from). The
        // realistic central-deployment model is that both nodes already serve the
        // namespace, so materialise it on node B here (this also proves the CRUD write
        // crossed the network), matching ClusteredTwoNodeTest's CRUD visibility test.
        assertTrue(nodeB.crudEntities("openApi").get("e1").isPresent(),
            "pre-kill: node B should hold the replicated CRUD entity");

        // Anti-vacuity negative-control key: never seeded anywhere, must be absent on
        // the survivor throughout, so a wholesale "everything present" bug can't pass.
        assertFalse(nodeB.expectations().get("never-seeded-id").isPresent(),
            "a never-seeded id must be absent on node B");

        // --- the degrade: remove the survivor's replicas before the peer dies -
        if (degradeEvictSurvivor) {
            nodeB.getCacheManager().getCache(InfinispanStateBackend.EXPECTATIONS_CACHE).evict(expectation.getId());
            nodeB.getCacheManager().getCache(InfinispanStateBackend.SCENARIO_STATES_CACHE).evict("survivor-scenario");
            nodeB.getCacheManager().getCache(InfinispanStateBackend.SHARED_TIMES_CACHE).evict(counterId);
            nodeB.getCacheManager().getCache(InfinispanStateBackend.CRUD_CACHE_PREFIX + "openApi").evict("e1");
        }

        // --- KILL node A mid-run --------------------------------------------
        nodeA.close();
        nodeA = null; // so tearDown does not double-close

        // The death must be OBSERVED: node B's JGroups view drops to 1.
        awaitViewSize(nodeB, 1, VIEW_CHANGE_TIMEOUT);
        assertThat("survivor sees itself as clustered only after the view settles",
            nodeB.getCacheManager().getTransport().getMembers().size(), is(1));

        // --- survivor still serves every piece of seeded state --------------
        Optional<Versioned<ExpectationEntry>> survivedExpectation =
            nodeB.expectations().get(expectation.getId());
        assertTrue(survivedExpectation.isPresent(),
            "survivor must still serve the expectation seeded on the dead peer");
        assertThat(survivedExpectation.get().getValue().getExpectation().getHttpRequest(),
            is(expectation.getHttpRequest()));

        assertThat("survivor must still serve the scenario state",
            nodeB.scenarioStates().get("survivor-scenario").map(Versioned::getValue).orElse(null),
            is("Started"));

        assertTrue(nodeB.crudEntities("openApi").get("e1").isPresent(),
            "survivor must still serve the CRUD entity");

        assertThat("survivor holds the bounded-Times counter at its true decremented "
                + "value after the peer died — not resurrected to the full allotment",
            nodeB.sharedTimesCounters().get(counterId).map(Versioned::getValue).orElse(-1),
            is(allotment - 1));

        // Anti-vacuity holds on the survivor too.
        assertFalse(nodeB.expectations().get("never-seeded-id").isPresent(),
            "a never-seeded id must remain absent on the survivor");

        // --- survivor is a functioning single-node cluster, not frozen ------
        long v = nodeB.sharedTimesCounters().put("post-death-counter", 3);
        assertTrue(nodeB.sharedTimesCounters().compareAndSet("post-death-counter", v, 2),
            "survivor must still accept new writes / CAS after the peer died");
        nodeB.scenarioStates().put("post-death-scenario", "AfterKill");
        assertThat(nodeB.scenarioStates().get("post-death-scenario").map(Versioned::getValue).orElse(null),
            is("AfterKill"));
    }

    // --- helpers ---------------------------------------------------------------

    private static void awaitViewSize(InfinispanStateBackend backend, int expected, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int current = -1;
        while (System.currentTimeMillis() < deadline) {
            current = backend.getCacheManager().getTransport().getMembers().size();
            if (current == expected) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for view size " + expected, e);
            }
        }
        fail("cluster view did not reach " + expected + " within " + timeout + "; last size=" + current);
    }
}
