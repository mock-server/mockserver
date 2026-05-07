package org.mockserver.model;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class WebSocketMessage extends ObjectWithJsonToString {
    private int hashCode;
    private String text;
    private byte[] binary;
    private Delay delay;

    public static WebSocketMessage webSocketMessage() {
        return new WebSocketMessage();
    }

    public static WebSocketMessage webSocketMessage(String text) {
        return new WebSocketMessage().withText(text);
    }

    public WebSocketMessage withText(String text) {
        this.text = text;
        this.hashCode = 0;
        return this;
    }

    public String getText() {
        return text;
    }

    public WebSocketMessage withBinary(byte[] binary) {
        this.binary = binary;
        this.hashCode = 0;
        return this;
    }

    public byte[] getBinary() {
        return binary;
    }

    public WebSocketMessage withDelay(Delay delay) {
        this.delay = delay;
        this.hashCode = 0;
        return this;
    }

    public WebSocketMessage withDelay(TimeUnit timeUnit, long value) {
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
        WebSocketMessage that = (WebSocketMessage) o;
        return Objects.equals(text, that.text) &&
            Arrays.equals(binary, that.binary) &&
            Objects.equals(delay, that.delay);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(text, Arrays.hashCode(binary), delay);
        }
        return hashCode;
    }
}
