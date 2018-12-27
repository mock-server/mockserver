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
        mockServer = new MockServer(remotePort, remoteHost, ports);
        ((SettableFuture) portFuture).set(mockServer.getLocalPort());
    }

    public static ClientAndServer startClientAndServer(Integer... port) {
        return new ClientAndServer(port);
    }

    public static ClientAndServer startClientAndServer(String remoteHost, Integer remotePort, Integer... port) {
        return new ClientAndServer(remoteHost, remotePort, port);
    }

    public boolean isRunning() {
        return mockServer.isRunning();
    }

    @Override
    public void stop() {
        mockServer.stop();
        stop(true);
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
