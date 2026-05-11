package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.mockserver.model.GrpcStreamResponse;
import org.mockserver.model.Headers;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.ArrayList;
import java.util.List;

public class GrpcStreamResponseDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<GrpcStreamResponse> {
    private DelayDTO delay;
    private String statusName;
    private String statusMessage;
    private Headers headers;
    private List<GrpcStreamMessageDTO> messages;
    private Boolean closeConnection;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean primary;

    public GrpcStreamResponseDTO(GrpcStreamResponse grpcStreamResponse) {
        if (grpcStreamResponse != null) {
            if (grpcStreamResponse.getDelay() != null) {
                delay = new DelayDTO(grpcStreamResponse.getDelay());
            }
            statusName = grpcStreamResponse.getStatusName();
            statusMessage = grpcStreamResponse.getStatusMessage();
            headers = grpcStreamResponse.getHeaders();
            closeConnection = grpcStreamResponse.getCloseConnection();
            if (grpcStreamResponse.getMessages() != null) {
                messages = new ArrayList<>();
                grpcStreamResponse.getMessages().forEach(msg -> messages.add(new GrpcStreamMessageDTO(msg)));
            }
            primary = grpcStreamResponse.isPrimary();
        }
    }

    public GrpcStreamResponseDTO() {
    }

    public GrpcStreamResponse buildObject() {
        GrpcStreamResponse grpcStreamResponse = new GrpcStreamResponse()
            .withDelay(delay != null ? delay.buildObject() : null)
            .withStatusName(statusName)
            .withStatusMessage(statusMessage)
            .withHeaders(headers)
            .withCloseConnection(closeConnection)
            .withPrimary(primary);
        if (messages != null) {
            messages.forEach(msgDTO -> grpcStreamResponse.withMessage(msgDTO.buildObject()));
        }
        return grpcStreamResponse;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public GrpcStreamResponseDTO setDelay(DelayDTO delay) {
        this.delay = delay;
        return this;
    }

    public String getStatusName() {
        return statusName;
    }

    public GrpcStreamResponseDTO setStatusName(String statusName) {
        this.statusName = statusName;
        return this;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public GrpcStreamResponseDTO setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
        return this;
    }

    public Headers getHeaders() {
        return headers;
    }

    public GrpcStreamResponseDTO setHeaders(Headers headers) {
        this.headers = headers;
        return this;
    }

    public List<GrpcStreamMessageDTO> getMessages() {
        return messages;
    }

    public GrpcStreamResponseDTO setMessages(List<GrpcStreamMessageDTO> messages) {
        this.messages = messages;
        return this;
    }

    public Boolean getCloseConnection() {
        return closeConnection;
    }

    public GrpcStreamResponseDTO setCloseConnection(Boolean closeConnection) {
        this.closeConnection = closeConnection;
        return this;
    }

    public boolean isPrimary() {
        return primary;
    }

    public GrpcStreamResponseDTO setPrimary(boolean primary) {
        this.primary = primary;
        return this;
    }
}
