package org.mockserver.model;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * Told of each binary (non-HTTP) message MockServer forwards.
 * <p>
 * A proxied connection has one upstream connection for its life. The listener is called once per message read from
 * a client connection, as soon as it is read, on a thread of its own rather than the thread serving that connection,
 * so it may block, for example on {@code binaryResponse}. The messages of one connection are reported one at a time,
 * in the order they arrived; messages of different connections are reported concurrently, so the listener must be
 * thread-safe. A call can come after the response has already been written to the client, or after the client has
 * closed its connection. A listener that throws closes the client's connection. What the upstream sends that is not
 * a message's {@code binaryResponse} is reported to {@link #onUpstreamMessage}, in the same order and one at a time
 * with {@link #onProxy}.
 */
public interface BinaryProxyListener {

    /**
     * @param binaryRequest  the bytes read from the client
     * @param binaryResponse completes with the first bytes the upstream sends after the message was written to
     *                       it, with {@code null} if the client's next message arrives first or the upstream
     *                       closes its connection without any, or exceptionally if the message could not be
     *                       forwarded
     * @param serverAddress  the upstream the message is forwarded to
     * @param clientAddress  the client that sent it
     */
    public void onProxy(BinaryMessage binaryRequest, CompletableFuture<BinaryMessage> binaryResponse, SocketAddress serverAddress, SocketAddress clientAddress);

    /**
     * Called for each read from the upstream that completes no {@code binaryResponse}: bytes it sent unprompted,
     * and each read after the first that followed a message. Does nothing unless
     * overridden; a listener that overrides it and returns more slowly than the upstream sends slows the upstream
     * down, except while a message is waiting for its response.
     *
     * @param upstreamMessage the bytes read from the upstream, which are also written to the client
     * @param serverAddress   the upstream that sent them
     * @param clientAddress   the client they are written to
     */
    default void onUpstreamMessage(BinaryMessage upstreamMessage, SocketAddress serverAddress, SocketAddress clientAddress) {
    }

}
