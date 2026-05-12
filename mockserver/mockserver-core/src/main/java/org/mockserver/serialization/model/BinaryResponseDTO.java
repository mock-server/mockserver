package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.mockserver.model.BinaryResponse;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

public class BinaryResponseDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<BinaryResponse> {
    private DelayDTO delay;
    private byte[] binaryData;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean primary;

    public BinaryResponseDTO(BinaryResponse binaryResponse) {
        if (binaryResponse != null) {
            if (binaryResponse.getDelay() != null) {
                delay = new DelayDTO(binaryResponse.getDelay());
            }
            binaryData = binaryResponse.getBinaryData();
            primary = binaryResponse.isPrimary();
        }
    }

    public BinaryResponseDTO() {
    }

    public BinaryResponse buildObject() {
        return new BinaryResponse()
            .withDelay(delay != null ? delay.buildObject() : null)
            .withBinaryData(binaryData)
            .withPrimary(primary);
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public BinaryResponseDTO setDelay(DelayDTO delay) {
        this.delay = delay;
        return this;
    }

    public byte[] getBinaryData() {
        return binaryData;
    }

    public BinaryResponseDTO setBinaryData(byte[] binaryData) {
        this.binaryData = binaryData;
        return this;
    }

    public boolean isPrimary() {
        return primary;
    }

    public BinaryResponseDTO setPrimary(boolean primary) {
        this.primary = primary;
        return this;
    }
}
