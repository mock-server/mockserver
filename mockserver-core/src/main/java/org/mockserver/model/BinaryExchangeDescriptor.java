package org.mockserver.model;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;

public class BinaryExchangeDescriptor {

    private final BinaryMessage binaryRequest;
    private final BinaryMessage binaryResponse;
    private final LocalDateTime requestStart;
    private final LocalDateTime requestEnd;
    private final InetSocketAddress serverAddress;
    private final InetSocketAddress clientAddress;

    public BinaryExchangeDescriptor(BinaryMessage binaryRequest,
        BinaryMessage binaryResponse, LocalDateTime requestStart, LocalDateTime requestEnd,
        InetSocketAddress serverAddress, InetSocketAddress clientAddress) {
        this.binaryRequest = binaryRequest;
        this.binaryResponse = binaryResponse;
        this.requestStart = requestStart;
        this.requestEnd = requestEnd;
        this.serverAddress = serverAddress;
        this.clientAddress = clientAddress;
    }

    public org.mockserver.model.BinaryMessage getBinaryRequest() {
        return binaryRequest;
    }

    public org.mockserver.model.BinaryMessage getBinaryResponse() {
        return binaryResponse;
    }

    public LocalDateTime getRequestStart() {
        return requestStart;
    }

    public LocalDateTime getRequestEnd() {
        return requestEnd;
    }

    public InetSocketAddress getServerAddress() {
        return serverAddress;
    }

    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }
}
