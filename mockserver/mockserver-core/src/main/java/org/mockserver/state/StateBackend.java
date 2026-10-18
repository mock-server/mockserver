package org.mockserver.state;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.Closeable;

/**
 * Pluggable backend for all shared MockServer state. A single
 * {@code StateBackend} instance is created per {@code HttpState} via
 * {@link StateBackendFactory}.
 * <p>
 * The default {@link InMemoryStateBackend} wraps the in-memory data
 * structures directly. A clustered implementation (for example the
 * Infinispan backend) backs these stores with a distributed cache.
 */
public interface StateBackend extends Closeable {

    /**
     * Returns the expectation key-value store. The in-memory implementation
     * internally wraps a {@code CircularPriorityQueue} for identical
     * ordering and eviction behaviour.
     */
    KeyValueStore<ExpectationEntry> expectations();

    /**
     * Returns the scenario-state key-value store. Keys are composite
     * scenario-name + isolation strings; values are state strings.
     */
    KeyValueStore<String> scenarioStates();

    /**
     * Returns the shared remaining-times counter store used by a clustered
     * backend to enforce per-expectation {@code Times} limits fleet-wide.
     * Keyed by expectation id; each value is the remaining match count.
     * <p>
     * This store is kept <b>separate</b> from {@link #expectations()} on
     * purpose: a {@code Times} consume on the hot request path decrements only
     * this counter, so a clustered (replicated) decrement ships a single
     * {@code Integer} across the wire rather than re-serialising the whole
     * {@link ExpectationEntry} (which marshals the entire expectation as JSON
     * on every write). See {@code RequestMatchers.consumeTimesViaBackendCas}.
     * <p>
     * Only ever consulted on a clustered backend ({@link #isClustered()} is
     * {@code true}). The default returns {@code null}, which makes
     * {@code RequestMatchers} fall back to the legacy path that CASes the
     * remaining count on the {@link ExpectationEntry} itself — so any backend
     * that does not override this keeps its previous behaviour exactly.
     *
     * @return the shared-times counter store, or {@code null} to use the
     *         legacy on-{@code ExpectationEntry} counter
     */
    default KeyValueStore<Integer> sharedTimesCounters() {
        return null;
    }

    /**
     * Returns a CRUD entity key-value store for the given namespace.
     * Each namespace corresponds to a distinct CRUD resource path.
     */
    KeyValueStore<ObjectNode> crudEntities(String namespace);

    /**
     * Returns the blob store for persisted expectations, recorded
     * cassettes, fixtures, and snapshots.
     */
    BlobStore blobs();

    /**
     * Adds an invalidation listener that receives change notifications
     * from ALL stores managed by this backend.
     */
    void addInvalidationListener(InvalidationListener listener);

    /**
     * Returns a unique identifier for this node/instance. Used to
     * distinguish local vs. remote writes in a clustered backend.
     */
    String nodeId();

    /**
     * Returns whether this backend is clustered (multi-node state
     * replication). When {@code true}, callers must use CAS on the
     * shared {@link KeyValueStore} for correctness-critical mutations
     * such as Times consumption. The default is {@code false} (single
     * node, all state node-local).
     */
    default boolean isClustered() {
        return false;
    }

    /**
     * Returns a snapshot of cluster membership and health for the
     * {@code GET /mockserver/cluster} operability endpoint.
     * <p>
     * The default implementation returns a degenerate single-node snapshot
     * ({@code clustered=false}, one local member equal to {@link #nodeId()},
     * which is also the coordinator). A clustered backend overrides this to
     * report the real fleet membership and the elected coordinator.
     *
     * @return a non-null {@link ClusterInfo} (always contains at least this node)
     */
    default ClusterInfo clusterInfo() {
        return ClusterInfo.singleNode(nodeId());
    }

    /**
     * Closes the backend and releases any resources. No-op for the
     * in-memory backend.
     */
    @Override
    void close();
}
