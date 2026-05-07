package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class HttpSseResponse extends Action<HttpSseResponse> {
    private int hashCode;
    private Integer statusCode;
    private Headers headers;
    private List<SseEvent> events;
    private Boolean closeConnection;

    public static HttpSseResponse sseResponse() {
        return new HttpSseResponse();
    }

    public HttpSseResponse withStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
        this.hashCode = 0;
        return this;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public HttpSseResponse withHeaders(Headers headers) {
        this.headers = headers;
        this.hashCode = 0;
        return this;
    }

    public HttpSseResponse withHeader(Header header) {
        if (this.headers == null) {
            this.headers = new Headers();
        }
        this.headers.withEntry(header);
        this.hashCode = 0;
        return this;
    }

    public HttpSseResponse withHeader(String name, String... values) {
        if (this.headers == null) {
            this.headers = new Headers();
        }
        this.headers.withEntry(name, values);
        this.hashCode = 0;
        return this;
    }

    public Headers getHeaders() {
        return headers;
    }

    public HttpSseResponse withEvents(List<SseEvent> events) {
        this.events = events;
        this.hashCode = 0;
        return this;
    }

    public HttpSseResponse withEvents(SseEvent... events) {
        this.events = Arrays.asList(events);
        this.hashCode = 0;
        return this;
    }

    public HttpSseResponse withEvent(SseEvent event) {
        if (this.events == null) {
            this.events = new ArrayList<>();
        }
        this.events.add(event);
        this.hashCode = 0;
        return this;
    }

    public List<SseEvent> getEvents() {
        return events;
    }

    public HttpSseResponse withCloseConnection(Boolean closeConnection) {
        this.closeConnection = closeConnection;
        this.hashCode = 0;
        return this;
    }

    public Boolean getCloseConnection() {
        return closeConnection;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.SSE_RESPONSE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        HttpSseResponse that = (HttpSseResponse) o;
        return Objects.equals(statusCode, that.statusCode) &&
            Objects.equals(headers, that.headers) &&
            Objects.equals(events, that.events) &&
            Objects.equals(closeConnection, that.closeConnection);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), statusCode, headers, events, closeConnection);
        }
        return hashCode;
    }
}
