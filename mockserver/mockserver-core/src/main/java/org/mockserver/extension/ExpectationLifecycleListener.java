package org.mockserver.extension;

/**
 * An {@link Extension} told each time an expectation is added, updated or removed, and why (WireMock's
 * {@code StubLifecycleListener}).
 * <p>
 * {@link #onChange} runs on the extension's own listener thread, never on the thread that changed the expectation,
 * so it may block without slowing the server. Changes are queued in the order the expectation store applied them.
 * Each queued change keeps its expectation reachable, so the queue is bounded by count
 * ({@code mockserver.extensionListenerQueueSize}) and by this listener's share of
 * {@code mockserver.extensionListenerMaxBytes}, weighed by the expectation's estimated size; changes that do not fit
 * are dropped and counted in {@code mock_server_extension_listener_dropped_total}. A throw is logged and counted
 * and does not stop later changes being delivered.
 * <p>
 * A response that uses up an expectation's last match is reported as {@link ChangeCause#EXHAUSTED} when the
 * server removes the expectation: normally as soon as that response completes; otherwise (for example a
 * {@code TimeToLive} that lapses with no traffic) when a later request or a retrieve next meets the inactive
 * expectation. If neither happens there is no event. Serving a match that leaves matches remaining is not a
 * change.
 */
public interface ExpectationLifecycleListener extends Extension {

    /**
     * @param change what changed and why
     */
    void onChange(ExpectationChange change);
}
