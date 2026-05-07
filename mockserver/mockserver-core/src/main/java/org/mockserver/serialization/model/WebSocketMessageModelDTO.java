package org.mockserver.serialization.model;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.model.WebSocketMessage;

public class WebSocketMessageModelDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<WebSocketMessage> {
    private String text;
    private byte[] binary;
    private DelayDTO delay;

    public WebSocketMessageModelDTO(WebSocketMessage webSocketMessage) {
        if (webSocketMessage != null) {
            text = webSocketMessage.getText();
            binary = webSocketMessage.getBinary();
            if (webSocketMessage.getDelay() != null) {
                delay = new DelayDTO(webSocketMessage.getDelay());
            }
        }
    }

    public WebSocketMessageModelDTO() {
    }

    public WebSocketMessage buildObject() {
        WebSocketMessage message = new WebSocketMessage();
        message.withText(text);
        message.withBinary(binary);
        if (delay != null) {
            message.withDelay(delay.buildObject());
        }
        return message;
    }

    public String getText() {
        return text;
    }

    public WebSocketMessageModelDTO setText(String text) {
        this.text = text;
        return this;
    }

    public byte[] getBinary() {
        return binary;
    }

    public WebSocketMessageModelDTO setBinary(byte[] binary) {
        this.binary = binary;
        return this;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public WebSocketMessageModelDTO setDelay(DelayDTO delay) {
        this.delay = delay;
        return this;
    }
}
