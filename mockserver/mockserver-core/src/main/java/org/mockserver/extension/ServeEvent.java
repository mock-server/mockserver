package org.mockserver.extension;

import org.mockserver.model.HttpResponse;
import org.mockserver.model.RequestDefinition;

/**
 * A request the server answered and the response it sent, delivered to a {@link ServeEventListener}. The request
 * and response are the event log's own (possibly body-truncated) objects: treat them as read-only.
 */
public final class ServeEvent {

    /**
     * How the request was answered.
     */
    public enum Disposition {
        /**
         * An expectation matched and its response was returned.
         */
        MOCKED,
        /**
         * The request was forwarded or proxied and the upstream response returned.
         */
        FORWARDED,
        /**
         * No expectation matched and the not-matched response was returned.
         */
        NOT_MATCHED
    }

    private final Disposition disposition;
    private final RequestDefinition request;
    private final HttpResponse response;
    private final String expectationId;
    private final String correlationId;
    private final long epochTimeMillis;
    // the bytes this event keeps reachable, charged against the listener queue's byte bound
    final long weight;

    ServeEvent(Disposition disposition, RequestDefinition request, HttpResponse response, String expectationId, String correlationId, long epochTimeMillis, long weight) {
        this.disposition = disposition;
        this.request = request;
        this.response = response;
        this.expectationId = expectationId;
        this.correlationId = correlationId;
        this.epochTimeMillis = epochTimeMillis;
        this.weight = weight;
    }

    public Disposition disposition() {
        return disposition;
    }

    public RequestDefinition request() {
        return request;
    }

    public HttpResponse response() {
        return response;
    }

    /**
     * @return the id of the expectation that answered the request, or {@code null} when none did
     */
    public String expectationId() {
        return expectationId;
    }

    /**
     * @return the id that ties together the event-log entries for this request
     */
    public String correlationId() {
        return correlationId;
    }

    /**
     * @return when the response was logged, in epoch milliseconds
     */
    public long epochTimeMillis() {
        return epochTimeMillis;
    }

    @Override
    public String toString() {
        return disposition + " " + correlationId + (expectationId != null ? " expectation " + expectationId : "");
    }
}
