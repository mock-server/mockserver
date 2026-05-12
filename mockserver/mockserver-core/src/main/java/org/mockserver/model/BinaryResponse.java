package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Arrays;
import java.util.Objects;

public class BinaryResponse extends Action<BinaryResponse> {
    private int hashCode;
    private byte[] binaryData;

    public static BinaryResponse binaryResponse() {
        return new BinaryResponse();
    }

    public static BinaryResponse binaryResponse(byte[] binaryData) {
        return new BinaryResponse().withBinaryData(binaryData);
    }

    public byte[] getBinaryData() {
        return binaryData;
    }

    public BinaryResponse withBinaryData(byte[] binaryData) {
        this.binaryData = binaryData;
        this.hashCode = 0;
        return this;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.BINARY_RESPONSE;
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
        BinaryResponse that = (BinaryResponse) o;
        return Arrays.equals(binaryData, that.binaryData);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), Arrays.hashCode(binaryData));
        }
        return hashCode;
    }
}
