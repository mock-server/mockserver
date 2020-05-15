package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class BinaryMessage implements Message {

    private byte[] bytes;

    public static BinaryMessage bytes(byte[] bytes) {
        return new BinaryMessage()
            .withBytes(bytes);
    }

    public BinaryMessage withBytes(byte[] bytes) {
        this.bytes = bytes;
        return this;
    }

    public byte[] getBytes() {
        return bytes;
    }

}
