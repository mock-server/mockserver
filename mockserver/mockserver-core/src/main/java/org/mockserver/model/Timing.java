package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Timing {
    private Long requestStartedMillis;
    private Long connectionEstablishedMillis;
    private Long responseReceivedMillis;
    private Long connectionTimeInMillis;
    private Long timeToFirstByteInMillis;
    private Long totalTimeInMillis;

    public static Timing timing() {
        return new Timing();
    }

    public Long getRequestStartedMillis() {
        return requestStartedMillis;
    }

    public Timing withRequestStartedMillis(Long requestStartedMillis) {
        this.requestStartedMillis = requestStartedMillis;
        return this;
    }

    public Long getConnectionEstablishedMillis() {
        return connectionEstablishedMillis;
    }

    public Timing withConnectionEstablishedMillis(Long connectionEstablishedMillis) {
        this.connectionEstablishedMillis = connectionEstablishedMillis;
        return this;
    }

    public Long getResponseReceivedMillis() {
        return responseReceivedMillis;
    }

    public Timing withResponseReceivedMillis(Long responseReceivedMillis) {
        this.responseReceivedMillis = responseReceivedMillis;
        return this;
    }

    public Long getConnectionTimeInMillis() {
        return connectionTimeInMillis;
    }

    public Timing withConnectionTimeInMillis(Long connectionTimeInMillis) {
        this.connectionTimeInMillis = connectionTimeInMillis;
        return this;
    }

    public Long getTimeToFirstByteInMillis() {
        return timeToFirstByteInMillis;
    }

    public Timing withTimeToFirstByteInMillis(Long timeToFirstByteInMillis) {
        this.timeToFirstByteInMillis = timeToFirstByteInMillis;
        return this;
    }

    public Long getTotalTimeInMillis() {
        return totalTimeInMillis;
    }

    public Timing withTotalTimeInMillis(Long totalTimeInMillis) {
        this.totalTimeInMillis = totalTimeInMillis;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Timing timing = (Timing) o;
        return Objects.equals(requestStartedMillis, timing.requestStartedMillis) &&
            Objects.equals(connectionEstablishedMillis, timing.connectionEstablishedMillis) &&
            Objects.equals(responseReceivedMillis, timing.responseReceivedMillis) &&
            Objects.equals(connectionTimeInMillis, timing.connectionTimeInMillis) &&
            Objects.equals(timeToFirstByteInMillis, timing.timeToFirstByteInMillis) &&
            Objects.equals(totalTimeInMillis, timing.totalTimeInMillis);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestStartedMillis, connectionEstablishedMillis, responseReceivedMillis, connectionTimeInMillis, timeToFirstByteInMillis, totalTimeInMillis);
    }
}
