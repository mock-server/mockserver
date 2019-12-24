package org.mockserver.integration;

import org.mockserver.client.MockServerClient;
import org.mockserver.mockserver.MockServer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * @author jamesdbloom
 */
public class ClientAndServer extends MockServerClient {

    private final MockServer mockServer;

    public ClientAndServer(Integer... ports) {
        super(new CompletableFuture<>());
        mockServer = new MockServer(ports);
        portFuture.complete(mockServer.getLocalPort());
    }

    public ClientAndServer(String remoteHost, Integer remotePort, Integer... ports) {
        super(new CompletableFuture<>());
        mockServer = new MockServer(remotePort, remoteHost, ports);
        portFuture.complete(mockServer.getLocalPort());
    }

    public ClientAndServer startClientAndServer(List<Integer> ports) {
        return startClientAndServer(ports.toArray(new Integer[0]));
    }

    public static ClientAndServer startClientAndServer(Integer... port) {
        return new ClientAndServer(port);
    }

    public static ClientAndServer startClientAndServer(String remoteHost, Integer remotePort, Integer... port) {
        return new ClientAndServer(remoteHost, remotePort, port);
    }

    @SuppressWarnings("deprecation")
    public boolean isRunning() {
        return mockServer.isRunning();
    }

    public boolean hasStarted() {
        return mockServer.isRunning();
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Future stopAsync() {
        Future<String> stopAsync = mockServer.stopAsync();
        if (stopAsync instanceof CompletableFuture) {
            ((CompletableFuture<String>) stopAsync).thenAccept(ignore -> super.stop());
        } else {
            // no need to wait for client to clean up event loop
            super.stopAsync();
        }
        return stopAsync;
    }

    @Override
    public void stop() {
        mockServer.stop();
        super.stop();
    }

    /**
     * @deprecated use getLocalPort instead of getPort
     */
    @Deprecated
    public Integer getPort() {
        return getLocalPort();
    }

    public Integer getLocalPort() {
        return mockServer.getLocalPort();
    }

    public List<Integer> getLocalPorts() {
        return mockServer.getLocalPorts();
    }

    public InetSocketAddress getRemoteAddress() {
        return mockServer.getRemoteAddress();
    }
}
