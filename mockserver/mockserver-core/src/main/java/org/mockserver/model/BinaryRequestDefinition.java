package org.mockserver.model;

import java.util.Arrays;
import java.util.Objects;

public class BinaryRequestDefinition extends RequestDefinition {
    private int hashCode;
    private byte[] binaryData;
    private SocketAddress socketAddress;

    public static BinaryRequestDefinition binaryRequest() {
        return new BinaryRequestDefinition();
    }

    public static BinaryRequestDefinition binaryRequest(byte[] binaryData) {
        return new BinaryRequestDefinition().withBinaryData(binaryData);
    }

    public byte[] getBinaryData() {
        return binaryData;
    }

    public BinaryRequestDefinition withBinaryData(byte[] binaryData) {
        this.binaryData = binaryData;
        this.hashCode = 0;
        return this;
    }

    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    public BinaryRequestDefinition withSocketAddress(SocketAddress socketAddress) {
        this.socketAddress = socketAddress;
        this.hashCode = 0;
        return this;
    }

    @Override
    public BinaryRequestDefinition shallowClone() {
        return not(binaryRequest(), not)
            .withBinaryData(binaryData)
            .withSocketAddress(socketAddress);
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
        BinaryRequestDefinition that = (BinaryRequestDefinition) o;
        return Arrays.equals(binaryData, that.binaryData) &&
            Objects.equals(socketAddress, that.socketAddress);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), Arrays.hashCode(binaryData), socketAddress);
        }
        return hashCode;
    }
}
