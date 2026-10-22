package org.mockserver.extension;

import org.mockserver.mock.Expectation;

/**
 * One change to the expectations a server holds, delivered to an {@link ExpectationLifecycleListener} on the
 * listener's own thread.
 * <p>
 * {@link #expectation()} is the server's live expectation, not a copy: treat it as read-only. Fields such as its
 * remaining {@code Times} keep changing while the expectation is served.
 */
public final class ExpectationChange {

    /**
     * What happened to the expectation.
     */
    public enum Type {
        ADDED,
        UPDATED,
        REMOVED
    }

    private final Type type;
    private final ChangeCause cause;
    private final Expectation expectation;
    private final boolean fleetWide;
    // the bytes the referenced expectation keeps reachable, charged against the listener queue's byte bound
    final long weight;

    ExpectationChange(Type type, ChangeCause cause, Expectation expectation, boolean fleetWide) {
        this.type = type;
        this.cause = cause;
        this.expectation = expectation;
        this.fleetWide = fleetWide;
        this.weight = expectation != null ? expectation.estimatedHeapSize() : 0L;
    }

    public Type type() {
        return type;
    }

    public ChangeCause cause() {
        return cause;
    }

    /**
     * @return the live expectation; read-only by contract
     */
    public Expectation expectation() {
        return expectation;
    }

    /**
     * @return true when the expectation was {@link ChangeCause#EXHAUSTED} across a whole cluster rather than on
     * this node
     */
    public boolean isFleetWide() {
        return fleetWide;
    }

    @Override
    public String toString() {
        return type + " " + cause + (fleetWide ? " (fleet-wide)" : "") + " " + (expectation != null ? expectation.getId() : null);
    }
}
