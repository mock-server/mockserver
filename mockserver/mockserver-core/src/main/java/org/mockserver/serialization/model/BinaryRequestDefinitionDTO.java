package org.mockserver.serialization.model;

import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.model.SocketAddress;

public class BinaryRequestDefinitionDTO extends RequestDefinitionDTO {

    private byte[] binaryData;
    private SocketAddress socketAddress;

    public BinaryRequestDefinitionDTO(BinaryRequestDefinition binaryRequestDefinition) {
        super(binaryRequestDefinition != null ? binaryRequestDefinition.getNot() : null);
        if (binaryRequestDefinition != null) {
            binaryData = binaryRequestDefinition.getBinaryData();
            socketAddress = binaryRequestDefinition.getSocketAddress();
        }
    }

    public BinaryRequestDefinitionDTO() {
        super(false);
    }

    public BinaryRequestDefinition buildObject() {
        return (BinaryRequestDefinition) new BinaryRequestDefinition()
            .withBinaryData(binaryData)
            .withSocketAddress(socketAddress)
            .withNot(getNot());
    }

    public byte[] getBinaryData() {
        return binaryData;
    }

    public BinaryRequestDefinitionDTO setBinaryData(byte[] binaryData) {
        this.binaryData = binaryData;
        return this;
    }

    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    public BinaryRequestDefinitionDTO setSocketAddress(SocketAddress socketAddress) {
        this.socketAddress = socketAddress;
        return this;
    }
}
