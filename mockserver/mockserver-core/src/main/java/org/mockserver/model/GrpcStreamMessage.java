package org.mockserver.model;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class GrpcStreamMessage extends ObjectWithJsonToString {
    private int hashCode;
    private String json;
    private Delay delay;

    public static GrpcStreamMessage grpcStreamMessage() {
        return new GrpcStreamMessage();
    }

    public static GrpcStreamMessage grpcStreamMessage(String json) {
        return new GrpcStreamMessage().withJson(json);
    }

    public GrpcStreamMessage withJson(String json) {
        this.json = json;
        this.hashCode = 0;
        return this;
    }

    public String getJson() {
        return json;
    }

    public GrpcStreamMessage withDelay(Delay delay) {
        this.delay = delay;
        this.hashCode = 0;
        return this;
    }

    public GrpcStreamMessage withDelay(TimeUnit timeUnit, long value) {
        this.delay = new Delay(timeUnit, value);
        this.hashCode = 0;
        return this;
    }

    public Delay getDelay() {
        return delay;
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
        GrpcStreamMessage that = (GrpcStreamMessage) o;
        return Objects.equals(json, that.json) &&
            Objects.equals(delay, that.delay);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(json, delay);
        }
        return hashCode;
    }
}
