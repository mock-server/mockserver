package org.mockserver.model;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

public interface BinaryProxyListener {

    public void onProxy(BinaryMessage binaryRequest, CompletableFuture<BinaryMessage> binaryResponse, SocketAddress serverAddress, SocketAddress clientAddress);

}
