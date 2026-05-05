package org.mockserver.integration;

import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.lifecycle.ExpectationsListener;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.MockServer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.mockserver.configuration.ClientConfiguration.clientConfiguration;
import static org.mockserver.configuration.Configuration.configuration;
import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class ClientAndServer extends MockServerClient {

    private final Configuration configuration;

    public static ClientAndServer startClientAndServer(List<Integer> ports) {
        return startClientAndServer(ports.toArray(new Integer[0]));
    }

    public static ClientAndServer startClientAndServer(Configuration configuration, List<Integer> ports) {
        return startClientAndServer(configuration, ports.toArray(new Integer[0]));
    }

    public static ClientAndServer startClientAndServer(Integer... port) {
        return startClientAndServer(configuration(), port);
    }

    public static ClientAndServer startClientAndServer(Configuration configuration, Integer... port) {
        return new ClientAndServer(configuration, port);
    }

    public static ClientAndServer startClientAndServer(String remoteHost, Integer remotePort, Integer... port) {
        return startClientAndServer(configuration(), remoteHost, remotePort, port);
    }

    public static ClientAndServer startClientAndServer(Configuration configuration, String remoteHost, Integer remotePort, Integer... port) {
        return new ClientAndServer(configuration, remoteHost, remotePort, port);
    }

    private final MockServer mockServer;

    public ClientAndServer(Integer... ports) {
        this(configuration(), ports);
    }

    public ClientAndServer(Configuration configuration, Integer... ports) {
        super(clientConfiguration(configuration), new CompletableFuture<>());
        this.configuration = configuration;
        this.mockServer = new MockServer(configuration, ports);
        completePortFutureAndOpenUI();
    }

    public ClientAndServer(String remoteHost, Integer remotePort, Integer... ports) {
        this(configuration(), remoteHost, remotePort, ports);
    }

    public ClientAndServer(Configuration configuration, String remoteHost, Integer remotePort, Integer... ports) {
        super(clientConfiguration(configuration), new CompletableFuture<>());
        this.configuration = configuration;
        this.mockServer = new MockServer(configuration, remotePort, remoteHost, ports);
        completePortFutureAndOpenUI();
    }

    private void completePortFutureAndOpenUI() {
        if (MockServerLogger.isEnabled(DEBUG) && configuration.launchUIForLogLevelDebug()) {
            portFuture.whenComplete((integer, throwable) -> openUI());
        }
        portFuture.complete(mockServer.getLocalPort());
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
    public CompletableFuture stopAsync() {
        return mockServer.stopAsync().thenComposeAsync(s -> stop(true));
    }

    @Override
    public void stop() {
        mockServer.stop();
        super.stop();
    }

    public Integer getPort() {
        return mockServer.getLocalPort();
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

    public ClientAndServer registerListener(ExpectationsListener expectationsListener) {
        mockServer.registerListener(expectationsListener);
        return this;
    }
}
