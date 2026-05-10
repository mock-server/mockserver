package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.mockserver.model.HttpWebSocketResponse;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.ArrayList;
import java.util.List;

public class HttpWebSocketResponseDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpWebSocketResponse> {
    private DelayDTO delay;
    private String subprotocol;
    private List<WebSocketMessageModelDTO> messages;
    private Boolean closeConnection;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean primary;

    public HttpWebSocketResponseDTO(HttpWebSocketResponse httpWebSocketResponse) {
        if (httpWebSocketResponse != null) {
            if (httpWebSocketResponse.getDelay() != null) {
                delay = new DelayDTO(httpWebSocketResponse.getDelay());
            }
            subprotocol = httpWebSocketResponse.getSubprotocol();
            closeConnection = httpWebSocketResponse.getCloseConnection();
            if (httpWebSocketResponse.getMessages() != null) {
                messages = new ArrayList<>();
                httpWebSocketResponse.getMessages().forEach(message -> messages.add(new WebSocketMessageModelDTO(message)));
            }
            primary = httpWebSocketResponse.isPrimary();
        }
    }

    public HttpWebSocketResponseDTO() {
    }

    public HttpWebSocketResponse buildObject() {
        HttpWebSocketResponse httpWebSocketResponse = new HttpWebSocketResponse()
            .withDelay(delay != null ? delay.buildObject() : null)
            .withSubprotocol(subprotocol)
            .withCloseConnection(closeConnection)
            .withPrimary(primary);
        if (messages != null) {
            messages.forEach(messageDTO -> httpWebSocketResponse.withMessage(messageDTO.buildObject()));
        }
        return httpWebSocketResponse;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public HttpWebSocketResponseDTO setDelay(DelayDTO delay) {
        this.delay = delay;
        return this;
    }

    public String getSubprotocol() {
        return subprotocol;
    }

    public HttpWebSocketResponseDTO setSubprotocol(String subprotocol) {
        this.subprotocol = subprotocol;
        return this;
    }

    public List<WebSocketMessageModelDTO> getMessages() {
        return messages;
    }

    public HttpWebSocketResponseDTO setMessages(List<WebSocketMessageModelDTO> messages) {
        this.messages = messages;
        return this;
    }

    public Boolean getCloseConnection() {
        return closeConnection;
    }

    public HttpWebSocketResponseDTO setCloseConnection(Boolean closeConnection) {
        this.closeConnection = closeConnection;
        return this;
    }

    public boolean isPrimary() {
        return primary;
    }

    public HttpWebSocketResponseDTO setPrimary(boolean primary) {
        this.primary = primary;
        return this;
    }
}
