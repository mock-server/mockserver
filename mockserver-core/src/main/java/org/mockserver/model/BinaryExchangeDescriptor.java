package org.mockserver.model;

import java.net.SocketAddress;

public class BinaryExchangeDescriptor {

    private final BinaryMessage binaryRequest;
    private final BinaryMessage binaryResponse;
    private final SocketAddress serverAddress;
    private final SocketAddress clientAddress;

    public BinaryExchangeDescriptor(BinaryMessage binaryRequest,
        BinaryMessage binaryResponse, SocketAddress serverAddress, SocketAddress clientAddress) {
        this.binaryRequest = binaryRequest;
        this.binaryResponse = binaryResponse;
        this.serverAddress = serverAddress;
        this.clientAddress = clientAddress;
    }

    public org.mockserver.model.BinaryMessage getBinaryRequest() {
        return binaryRequest;
    }

    public org.mockserver.model.BinaryMessage getBinaryResponse() {
        return binaryResponse;
    }

    public SocketAddress getServerAddress() {
        return serverAddress;
    }

    public SocketAddress getClientAddress() {
        return clientAddress;
    }
}
