package org.mockserver.model;

import java.time.LocalDateTime;

/**
 * @author jamesdbloom
 */
public class BinaryMessage implements Message {

    private byte[] bytes;
    private LocalDateTime timestamp;

    public static BinaryMessage bytes(byte[] bytes) {
        return new BinaryMessage()
            .withBytes(bytes)
            .withTimestamp(LocalDateTime.now());
    }

    public BinaryMessage withBytes(byte[] bytes) {
        this.bytes = bytes;
        return this;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public BinaryMessage withTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
