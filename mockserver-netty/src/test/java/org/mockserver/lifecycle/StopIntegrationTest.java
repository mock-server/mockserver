package org.mockserver.lifecycle;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.MockServerClient;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.PortFactory;
import org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase.HEADERS_TO_IGNORE;
import static org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase.filterHeaders;

/**
 * @author jamesdbloom
 */
public class StopIntegrationTest {

    private static final int MOCK_SERVER_PORT = PortFactory.findFreePort();

    private static EventLoopGroup clientEventLoopGroup;
    static NettyHttpClient httpClient;

    @BeforeClass
    public static void createClientAndEventLoopGroup() {
        clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(AbstractMockingIntegrationTestBase.class.getSimpleName() + "-eventLoop"));
        httpClient = new NettyHttpClient(new MockServerLogger(), clientEventLoopGroup, null, false);
    }

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void returnsExceptionWhenAlreadyStopped() {
        // given
        exception.expect(IllegalStateException.class);
        exception.expectMessage(containsString("MockServerClient has already been stopped, please create new MockServerClient instance"));

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
    public void canStartAndStopMultipleTimesViaClient() {
        // start server
        new MockServer(MOCK_SERVER_PORT);

        // start client
        MockServerClient mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);

        try {

            for (int i = 0; i < 5; i++) {
                // then
                testBasicExpectationAndResponse(mockServerClient);

                // when
                mockServerClient.stop();
                mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);

                // then
                assertTrue(mockServerClient.hasStopped());
                new MockServer(MOCK_SERVER_PORT);
                assertTrue(mockServerClient.hasStarted());
            }

        } finally {
            mockServerClient.stop();
        }
    }

    @Test
    public void canStartAndStopMultipleTimesViaClientWithoutCallingHasStopped() {
        // start server
        new MockServer(MOCK_SERVER_PORT);

        // start client
        MockServerClient mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);

        try {

            for (int i = 0; i < 5; i++) {
                // then
                testBasicExpectationAndResponse(mockServerClient);

                // when
                mockServerClient.stop();
                mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);

                // then
                new MockServer(MOCK_SERVER_PORT);
            }

        } finally {
            mockServerClient.stop();
        }
    }

    @Test
    public void canStartAndStopAsyncMultipleTimesViaClient() throws ExecutionException, InterruptedException, TimeoutException {
        // start server
        new MockServer(MOCK_SERVER_PORT);

        // start client
        MockServerClient mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);

        try {

            for (int i = 0; i < 5; i++) {
                // then
                testBasicExpectationAndResponse(mockServerClient);

                // when
                mockServerClient.stopAsync().get(10, SECONDS);
                mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);

                // then
                assertTrue(mockServerClient.hasStopped());
                new MockServer(MOCK_SERVER_PORT);
                assertTrue(mockServerClient.hasStarted());
            }

        } finally {
            mockServerClient.stopAsync().get(10, SECONDS);
        }
    }

    @Test
    public void canStartAndStopAsyncMultipleTimesViaClientWithoutCallingHasStopped() throws ExecutionException, InterruptedException, TimeoutException {
        // start server
        new MockServer(MOCK_SERVER_PORT);

        // start client
        MockServerClient mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);

        try {

            for (int i = 0; i < 5; i++) {
                // then
                testBasicExpectationAndResponse(mockServerClient);

                // when
                mockServerClient.stopAsync().get(10, SECONDS);
                mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);

                // then
                new MockServer(MOCK_SERVER_PORT);
            }

        } finally {
            mockServerClient.stopAsync().get(10, SECONDS);
        }
    }

    @Test
    public void canStartAndStopMultipleTimesViaClientAndServer() {
        // start server
        MockServerClient mockServerClient = new ClientAndServer(MOCK_SERVER_PORT);

        try {

            for (int i = 0; i < 5; i++) {
                // then
                testBasicExpectationAndResponse(mockServerClient);

                // when
                mockServerClient.stop();

                // then
                assertTrue(mockServerClient.hasStopped());
                mockServerClient = new ClientAndServer(MOCK_SERVER_PORT);
                assertTrue(mockServerClient.hasStarted());
            }

        } finally {
            mockServerClient.stop();
        }
    }

    @Test
    public void canStartAndStopMultipleTimesViaClientAndServerWithoutCallingHasStopped() {
        // start server
        MockServerClient mockServerClient = new ClientAndServer(MOCK_SERVER_PORT);

        try {

            for (int i = 0; i < 5; i++) {
                // then
                testBasicExpectationAndResponse(mockServerClient);

                // when
                mockServerClient.stop();

                // then
                mockServerClient = new ClientAndServer(MOCK_SERVER_PORT);
            }

        } finally {
            mockServerClient.stop();
        }
    }

    @Test
    public void canStartAndStopMultipleTimesViaMockServer() {
        // start server
        MockServer mockServer = new MockServer(MOCK_SERVER_PORT);
        MockServerClient mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);

        try {

            for (int i = 0; i < 5; i++) {
                // then
                testBasicExpectationAndResponse(mockServerClient);

                // when
                mockServer.stop();

                // then
                assertFalse(mockServer.isRunning());
                mockServer = new MockServer(MOCK_SERVER_PORT);
                assertTrue(mockServer.isRunning());
            }

        } finally {
            mockServer.stop();
            mockServerClient.stop();
        }
    }

    @Test
    public void canStartAndStopMultipleTimesViaMockServerWithoutCallingIsRunning() {
        // start server
        MockServer mockServer = new MockServer(MOCK_SERVER_PORT);
        MockServerClient mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);

        try {

            for (int i = 0; i < 5; i++) {
                // then
                testBasicExpectationAndResponse(mockServerClient);

                // when
                mockServer.stop();

                // then
                mockServer = new MockServer(MOCK_SERVER_PORT);
            }

        } finally {
            mockServer.stop();
            mockServerClient.stop();
        }
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

    private void testBasicExpectationAndResponse(MockServerClient mockServerClient) {
        // then
        mockServerClient
            .when(
                request()
                    .withPath("/some/path")
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withBody("some_body_response")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withPath("/some/path")
                    .withMethod("POST"),
                HEADERS_TO_IGNORE
            )
        );
    }

    protected HttpResponse makeRequest(HttpRequest httpRequest, Collection<String> headersToIgnore) {
        try {
            if (!httpRequest.containsHeader(HOST.toString())) {
                httpRequest.withHeader(HOST.toString(), "localhost:" + MOCK_SERVER_PORT);
            }
            boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0;
            HttpResponse httpResponse = httpClient.sendRequest(httpRequest, new InetSocketAddress("localhost", MOCK_SERVER_PORT)).get(30, (isDebug ? TimeUnit.MINUTES : TimeUnit.SECONDS));
            httpResponse.withHeaders(filterHeaders(headersToIgnore, httpResponse.getHeaderList()));
            httpResponse.withReasonPhrase(
                isBlank(httpResponse.getReasonPhrase()) ?
                    HttpResponseStatus.valueOf(httpResponse.getStatusCode()).reasonPhrase() :
                    httpResponse.getReasonPhrase()
            );
            return httpResponse;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
