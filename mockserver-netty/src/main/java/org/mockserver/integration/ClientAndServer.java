package org.mockserver.integration;

import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.MockServer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.mockserver.configuration.ConfigurationProperties.launchUIForLogLevelDebug;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class ClientAndServer extends MockServerClient {

    private final MockServer mockServer;

    public static void main(String[] args) {
        ConfigurationProperties.logLevel("DEBUG");
        ClientAndServer clientAndServer = startClientAndServer(1080);
        clientAndServer.when(request()).respond(response());
        clientAndServer.stop();
    }

    public ClientAndServer(Integer... ports) {
        super(new CompletableFuture<>());
        mockServer = new MockServer(ports);
        completePortFutureAndOpenUI();
    }

    public ClientAndServer(String remoteHost, Integer remotePort, Integer... ports) {
        super(new CompletableFuture<>());
        mockServer = new MockServer(remotePort, remoteHost, ports);
        completePortFutureAndOpenUI();
    }

    private void completePortFutureAndOpenUI() {
        if (MockServerLogger.isEnabled(DEBUG) && launchUIForLogLevelDebug()) {
            portFuture.whenComplete((integer, throwable) -> openUI());
        }
        portFuture.complete(mockServer.getLocalPort());
    }

    public static ClientAndServer startClientAndServer(List<Integer> ports) {
        return startClientAndServer(ports.toArray(new Integer[0]));
    }

    public static ClientAndServer startClientAndServer(Integer... port) {
        return new ClientAndServer(port);
    }

    public static ClientAndServer startClientAndServer(String remoteHost, Integer remotePort, Integer... port) {
        return new ClientAndServer(remoteHost, remotePort, port);
    }

    /**
     * Launch UI and wait the default period to allow the UI to launch and start collecting logs,
     * this ensures that the log are visible in the UI even if MockServer is shutdown by a test
     * shutdown function, such as After, AfterClass, AfterAll, etc
     */
    @Override
    public ClientAndServer openUI() {
        super.openUI();
        return this;
    }

    /**
     * Launch UI and wait a specified period to allow the UI to launch and start collecting logs,
     * this ensures that the log are visible in the UI even if MockServer is shutdown by a test
     * shutdown function, such as After, AfterClass, AfterAll, etc
     *
     * @param timeUnit TimeUnit the time unit, for example TimeUnit.SECONDS
     * @param pause    the number of time units to delay before the function returns to ensure the UI is receiving logs
     */
    @Override
    public ClientAndServer openUI(TimeUnit timeUnit, long pause) {
        super.openUI(timeUnit, pause);
        return this;
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
