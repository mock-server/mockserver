package org.mockserver.lifecycle;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.netty.MockServer;
import org.mockserver.socket.PortFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class StopIntegrationTest {

    private static final int MOCK_SERVER_PORT = PortFactory.findFreePort();

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void returnsExceptionWhenAlreadyStopped() {
        // given
        exception.expect(IllegalStateException.class);
        exception.expectMessage(containsString("Request sent after client has been stopped - the event loop has been shutdown so it is not possible to send a request"));

        // when - server started
        new MockServer(MOCK_SERVER_PORT);

        // and - start client
        MockServerClient mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);
        mockServerClient.hasStarted();
        mockServerClient.stop();

        // then
        mockServerClient.retrieveLogMessages(request());
    }

    @Test
    public void canStartAndStopMultipleTimesViaClient() throws ExecutionException, InterruptedException, TimeoutException {
        // start server
        new MockServer(MOCK_SERVER_PORT);

        // start client
        MockServerClient mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);

        for (int i = 0; i < 2; i++) {
            // when
            mockServerClient.stop();
            mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);

            // then
            assertTrue(mockServerClient.hasStopped());
            new MockServer(MOCK_SERVER_PORT);
            assertTrue(mockServerClient.hasStarted());
        }

        assertTrue(mockServerClient.hasStarted());
        mockServerClient.stopAsync().get(10, SECONDS);
        mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);
        assertTrue(mockServerClient.hasStopped());
    }

    @Test
    @Deprecated
    public void reportsIsRunningCorrectlyAfterClientStopped() {
        // start server
        MockServerClient mockServerClient = ClientAndServer.startClientAndServer();

        // when
        mockServerClient.stop();

        // then
        assertFalse(mockServerClient.isRunning());
        assertFalse(mockServerClient.isRunning(10, 1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void reportsHasStoppedCorrectlyAfterClientStopped() {
        // start server
        MockServerClient mockServerClient = ClientAndServer.startClientAndServer();

        // when
        mockServerClient.stop();

        // then
        assertTrue(mockServerClient.hasStopped());
        assertTrue(mockServerClient.hasStopped(10, 1000, TimeUnit.MILLISECONDS));
    }


    @Test
    public void reportsHasStartedCorrectlyAfterClientStarted() {
        // when
        MockServerClient mockServerClient = ClientAndServer.startClientAndServer();

        // then
        assertTrue(mockServerClient.hasStarted());
        assertTrue(mockServerClient.hasStarted(20, 1000, TimeUnit.MILLISECONDS));

        // clean-up
        mockServerClient.stop();
    }

    @Test
    public void canStartAndStopMultipleTimes() {
        // start server
        MockServer mockServer = new MockServer(MOCK_SERVER_PORT);

        for (int i = 0; i < 2; i++) {
            // when
            mockServer.stop();

            // then
            assertFalse(mockServer.isRunning());
            mockServer = new MockServer(MOCK_SERVER_PORT);
            assertTrue(mockServer.isRunning());
        }

        assertTrue(mockServer.isRunning());
        mockServer.stop();
        assertFalse(mockServer.isRunning());
    }

    @Test
    public void closesSocketBeforeStopMethodReturns() {
        // start server
        MockServer mockServer = new MockServer(MOCK_SERVER_PORT);

        // when
        mockServer.stop();

        // then
        try {
            new Socket("localhost", MOCK_SERVER_PORT);
            fail("socket should be closed");
        } catch (IOException ioe) {
            assertThat(ioe.getMessage(), anyOf(
                containsString("Connection refused"),
                containsString("Socket closed")
            ));
        }
    }

    @Test
    public void freesPortBeforeStopMethodReturns() {
        // start server
        MockServer mockServer = new MockServer(MOCK_SERVER_PORT);

        // when
        mockServer.stop();

        // then
        try (ServerSocket serverSocket = new ServerSocket(MOCK_SERVER_PORT)) {
            assertThat(serverSocket.isBound(), is(true));
        } catch (IOException ioe) {
            fail("port should be freed");
        }
    }
}
