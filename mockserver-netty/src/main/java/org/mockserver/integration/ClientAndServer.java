package org.mockserver.integration;

import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.client.MockServerClient;
import org.mockserver.mockserver.MockServer;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class ClientAndServer extends MockServerClient {

    private final MockServer mockServer;

    public ClientAndServer(Integer... ports) {
        super(SettableFuture.<Integer>create());
        mockServer = new MockServer(ports);
        ((SettableFuture) portFuture).set(mockServer.getLocalPort());
    }

    public ClientAndServer(String remoteHost, Integer remotePort, Integer... ports) {
        super(SettableFuture.<Integer>create());
        mockServer = new MockServer(remoteHost, remotePort, ports);
        ((SettableFuture) portFuture).set(mockServer.getLocalPort());
    }

    public static ClientAndServer startClientAndServer(Integer... port) {
        return new ClientAndServer(port);
    }

    public static ClientAndServer startClientAndServer(Integer port, String remoteHost, Integer remotePort) {
        return new ClientAndServer(remoteHost, remotePort, port);
    }

    public boolean isRunning() {
        return mockServer.isRunning();
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
