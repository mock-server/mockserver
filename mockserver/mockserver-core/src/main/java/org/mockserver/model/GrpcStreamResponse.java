package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class GrpcStreamResponse extends Action<GrpcStreamResponse> {
    private int hashCode;
    private String statusName;
    private String statusMessage;
    private Headers headers;
    private List<GrpcStreamMessage> messages;
    private Boolean closeConnection;

    public static GrpcStreamResponse grpcStreamResponse() {
        return new GrpcStreamResponse();
    }

    public GrpcStreamResponse withStatusName(String statusName) {
        this.statusName = statusName;
        this.hashCode = 0;
        return this;
    }

    public String getStatusName() {
        return statusName;
    }

    public GrpcStreamResponse withStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
        this.hashCode = 0;
        return this;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public GrpcStreamResponse withHeaders(Headers headers) {
        this.headers = headers;
        this.hashCode = 0;
        return this;
    }

    public GrpcStreamResponse withHeader(Header header) {
        if (this.headers == null) {
            this.headers = new Headers();
        }
        this.headers.withEntry(header);
        this.hashCode = 0;
        return this;
    }

    public GrpcStreamResponse withHeader(String name, String... values) {
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

    public GrpcStreamResponse withMessages(List<GrpcStreamMessage> messages) {
        this.messages = messages;
        this.hashCode = 0;
        return this;
    }

    public GrpcStreamResponse withMessages(GrpcStreamMessage... messages) {
        this.messages = Arrays.asList(messages);
        this.hashCode = 0;
        return this;
    }

    public GrpcStreamResponse withMessage(GrpcStreamMessage message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
        this.hashCode = 0;
        return this;
    }

    public GrpcStreamResponse withMessage(String json) {
        return withMessage(GrpcStreamMessage.grpcStreamMessage(json));
    }

    public GrpcStreamResponse withMessage(String json, Delay delay) {
        return withMessage(GrpcStreamMessage.grpcStreamMessage(json).withDelay(delay));
    }

    public List<GrpcStreamMessage> getMessages() {
        return messages;
    }

    public GrpcStreamResponse withCloseConnection(Boolean closeConnection) {
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
        return Type.GRPC_STREAM_RESPONSE;
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
        GrpcStreamResponse that = (GrpcStreamResponse) o;
        return Objects.equals(statusName, that.statusName) &&
            Objects.equals(statusMessage, that.statusMessage) &&
            Objects.equals(headers, that.headers) &&
            Objects.equals(messages, that.messages) &&
            Objects.equals(closeConnection, that.closeConnection);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), statusName, statusMessage, headers, messages, closeConnection);
        }
        return hashCode;
    }
}
