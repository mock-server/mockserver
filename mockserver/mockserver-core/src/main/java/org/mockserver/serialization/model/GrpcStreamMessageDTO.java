package org.mockserver.serialization.model;

import org.mockserver.model.GrpcStreamMessage;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

public class GrpcStreamMessageDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<GrpcStreamMessage> {
    private String json;
    private DelayDTO delay;

    public GrpcStreamMessageDTO(GrpcStreamMessage grpcStreamMessage) {
        if (grpcStreamMessage != null) {
            json = grpcStreamMessage.getJson();
            if (grpcStreamMessage.getDelay() != null) {
                delay = new DelayDTO(grpcStreamMessage.getDelay());
            }
        }
    }

    public GrpcStreamMessageDTO() {
    }

    public GrpcStreamMessage buildObject() {
        GrpcStreamMessage grpcStreamMessage = new GrpcStreamMessage();
        grpcStreamMessage.withJson(json);
        if (delay != null) {
            grpcStreamMessage.withDelay(delay.buildObject());
        }
        return grpcStreamMessage;
    }

    public String getJson() {
        return json;
    }

    public GrpcStreamMessageDTO setJson(String json) {
        this.json = json;
        return this;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public GrpcStreamMessageDTO setDelay(DelayDTO delay) {
        this.delay = delay;
        return this;
    }
}
