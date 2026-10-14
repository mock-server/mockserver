package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Arrays;
import java.util.Objects;

public class BinaryResponse extends Action<BinaryResponse> {
    private int hashCode;
    private byte[] binaryData;
    private Upstream upstream;

    public static BinaryResponse binaryResponse() {
        return new BinaryResponse();
    }

    public static BinaryResponse binaryResponse(byte[] binaryData) {
        return new BinaryResponse().withBinaryData(binaryData);
    }

    public byte[] getBinaryData() {
        return binaryData;
    }

    /**
     * The bytes written in reply to a matching binary message. With no data, or an empty array, the message has
     * no reply: nothing is written and the connection stays open. The two mean the same, because an empty array
     * is not serialised: set here and sent to MockServer it arrives as no data, and an expectation retrieved
     * from MockServer never has one.
     */
    public BinaryResponse withBinaryData(byte[] binaryData) {
        this.binaryData = binaryData;
        this.hashCode = 0;
        return this;
    }

    public Upstream getUpstream() {
        return upstream;
    }

    /**
     * What happens upstream when the matched message arrives on a binary connection MockServer relays to an upstream
     * (matchBinaryExpectationsWhenProxying). Null means {@link Upstream#ANSWER_ONLY}. The other two need
     * binaryMessageFraming POSTGRESQL, which says where the upstream's reply ends; without it, and on a connection
     * that is not relayed, the expectation answers as {@link Upstream#ANSWER_ONLY}.
     */
    public BinaryResponse withUpstream(Upstream upstream) {
        this.upstream = upstream;
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
        return Arrays.equals(binaryData, that.binaryData) && upstream == that.upstream;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), Arrays.hashCode(binaryData), upstream);
        }
        return hashCode;
    }

    /**
     * What happens upstream to a message a binary expectation matches on a relayed connection.
     */
    public enum Upstream {
        /**
         * MockServer writes the binary data and the message is not forwarded.
         */
        ANSWER_ONLY,
        /**
         * MockServer writes the binary data and also forwards the message, dropping the upstream's reply to it.
         */
        ANSWER_AND_FORWARD,
        /**
         * The message is forwarded and the upstream's reply to it is dropped; the binary data is written in its place
         * once that reply has ended.
         */
        FORWARD_AND_REPLACE
    }
}
