package org.mockserver.extension;

/**
 * Why an expectation was added, updated or removed, as reported to an {@link ExpectationLifecycleListener}.
 */
public enum ChangeCause {

    /**
     * Added, updated, cleared or reset through the control plane or a client.
     */
    API,

    /**
     * Loaded from {@code initializationJsonPath} or {@code initializationOpenAPIPath} when the server started, or
     * reloaded from a persisted expectation blob.
     */
    FILE_INITIALISER,

    /**
     * Loaded by an {@code initializationClass} or an extension that is an
     * {@link org.mockserver.server.initialize.ExpectationInitializer} when the server started.
     */
    CLASS_INITIALISER,

    /**
     * Reloaded because a watched initialisation file changed ({@code watchInitializationJson}).
     */
    FILE_WATCHER,

    /**
     * Removed because it had no matches left. With a clustered state backend and limited {@code Times}, this is
     * reported when a node next tries to match an expectation the whole cluster has used up, with
     * {@link ExpectationChange#isFleetWide()} true.
     */
    EXHAUSTED,

    /**
     * Removed because its {@code TimeToLive} had passed. When an expectation has also run out of matches it is
     * reported as {@link #EXHAUSTED}.
     */
    EXPIRED,

    /**
     * Removed to keep the store within {@code maxExpectations} or {@code maxExpectationsSizeInBytes}.
     */
    EVICTED,

    /**
     * Added, updated or removed on this node because another node of a cluster changed it (or the clustered
     * store evicted it).
     */
    CLUSTER
}
