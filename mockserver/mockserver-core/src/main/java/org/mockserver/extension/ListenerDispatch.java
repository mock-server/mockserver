package org.mockserver.extension;

import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;
import java.util.List;

import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.NO_MATCH_RESPONSE;

/**
 * Hands serve events, expectation changes and configuration updates to the listener extensions that want them.
 * Every method only builds the event and offers it to each listener's {@link ListenerQueue}, which never waits, so
 * it is safe on any thread. A server holds one only when at least one started extension is a listener; the fields
 * that hold it stay {@code null} otherwise.
 */
public final class ListenerDispatch {

    private final ListenerQueue[] queues;
    private final ListenerQueue[] serveEventQueues;
    private final ListenerQueue[] lifecycleQueues;
    private final ListenerQueue[] settingsQueues;

    private ListenerDispatch(ListenerQueue[] queues) {
        this.queues = queues;
        this.serveEventQueues = implementing(queues, ExtensionHook.SERVE_EVENT_LISTENER);
        this.lifecycleQueues = implementing(queues, ExtensionHook.EXPECTATION_LIFECYCLE_LISTENER);
        this.settingsQueues = implementing(queues, ExtensionHook.SETTINGS_LISTENER);
    }

    /**
     * @return a dispatch with one queue and thread per started listener extension, or {@code null} when none is a
     * listener
     */
    static ListenerDispatch create(List<LoadedExtension> started, Configuration configuration, org.mockserver.logging.MockServerLogger mockServerLogger) {
        List<LoadedExtension> listeners = new ArrayList<>();
        int weightedListeners = 0;
        for (LoadedExtension extension : started) {
            boolean weighted = holdsMessages(extension);
            if (weighted || extension.hooks().contains(ExtensionHook.SETTINGS_LISTENER)) {
                listeners.add(extension);
                if (weighted) {
                    weightedListeners++;
                }
            }
        }
        if (listeners.isEmpty()) {
            return null;
        }
        // each serve-event or lifecycle listener gets an equal share, so one that stops reading can fill only its own
        long byteShare = weightedListeners > 0 ? Math.max(1L, configuration.extensionListenerMaxBytes() / weightedListeners) : 0L;
        ListenerQueue[] queues = new ListenerQueue[listeners.size()];
        for (int i = 0; i < queues.length; i++) {
            LoadedExtension extension = listeners.get(i);
            queues[i] = new ListenerQueue(extension, mockServerLogger, configuration.extensionListenerQueueSize(),
                holdsMessages(extension) ? byteShare : 0L);
        }
        return new ListenerDispatch(queues);
    }

    // its events keep requests, responses or expectations reachable, so its queue is bounded by bytes too
    private static boolean holdsMessages(LoadedExtension extension) {
        return extension.hooks().contains(ExtensionHook.SERVE_EVENT_LISTENER) || extension.hooks().contains(ExtensionHook.EXPECTATION_LIFECYCLE_LISTENER);
    }

    private static ListenerQueue[] implementing(ListenerQueue[] queues, ExtensionHook hook) {
        List<ListenerQueue> matching = new ArrayList<>();
        for (ListenerQueue queue : queues) {
            if (queue.extension().hooks().contains(hook)) {
                matching.add(queue);
            }
        }
        return matching.toArray(new ListenerQueue[0]);
    }

    boolean hasServeEventListeners() {
        return serveEventQueues.length > 0;
    }

    boolean hasLifecycleListeners() {
        return lifecycleQueues.length > 0;
    }

    boolean hasSettingsListeners() {
        return settingsQueues.length > 0;
    }

    /**
     * Offers a serve event for an event-log entry that records a mocked, forwarded or not-matched response. Called
     * on the event-log consumer thread, after body truncation and immediately before the entry is retained. The
     * event copies the entry's references and never holds the entry, so a later {@code LogEntry.clear()} on
     * eviction cannot change it; its weight is the entry's memoised {@code estimatedHeapSize()}, which the event
     * log's own byte accounting then reuses unchanged.
     */
    public void served(LogEntry logEntry) {
        LogEntry.LogMessageType type = logEntry.getType();
        ServeEvent.Disposition disposition;
        if (type == EXPECTATION_RESPONSE) {
            disposition = ServeEvent.Disposition.MOCKED;
        } else if (type == FORWARDED_REQUEST) {
            if (logEntry.isSkipRecordedRequestPersistence()) {
                // re-imported from a recording archive: replayed into the log, not served
                return;
            }
            disposition = ServeEvent.Disposition.FORWARDED;
        } else if (type == NO_MATCH_RESPONSE) {
            disposition = ServeEvent.Disposition.NOT_MATCHED;
        } else {
            return;
        }
        HttpResponse response = logEntry.getHttpResponse();
        if (response == null) {
            // an informational, request-only entry: the response is logged separately
            return;
        }
        ServeEvent event = new ServeEvent(disposition, logEntry.getHttpRequest(), response, logEntry.getExpectationId(),
            logEntry.getCorrelationId(), logEntry.getEpochTime(), logEntry.estimatedHeapSize());
        for (ListenerQueue queue : serveEventQueues) {
            queue.offer(event, event.weight);
        }
    }

    public void added(Expectation expectation, ChangeCause cause) {
        changed(new ExpectationChange(ExpectationChange.Type.ADDED, cause, expectation, false));
    }

    public void updated(Expectation expectation, ChangeCause cause) {
        changed(new ExpectationChange(ExpectationChange.Type.UPDATED, cause, expectation, false));
    }

    /**
     * @param fleetWide true when a clustered store reports the expectation used up across the cluster
     */
    public void removed(Expectation expectation, ChangeCause cause, boolean fleetWide) {
        changed(new ExpectationChange(ExpectationChange.Type.REMOVED, cause, expectation, fleetWide));
    }

    private void changed(ExpectationChange change) {
        for (ListenerQueue queue : lifecycleQueues) {
            queue.offer(change, change.weight);
        }
    }

    void settingsUpdated(Configuration configuration) {
        for (ListenerQueue queue : settingsQueues) {
            queue.offer(configuration, 0L);
        }
    }

    void flush(long budgetMillis) {
        long deadline = System.currentTimeMillis() + Math.max(0, budgetMillis);
        for (ListenerQueue queue : queues) {
            queue.flush(deadline - System.currentTimeMillis());
        }
    }

    void shutdown() {
        for (ListenerQueue queue : queues) {
            queue.shutdown();
        }
    }

    ListenerQueue queue(String extensionName) {
        for (ListenerQueue queue : queues) {
            if (queue.extension().name().equals(extensionName)) {
                return queue;
            }
        }
        return null;
    }
}
