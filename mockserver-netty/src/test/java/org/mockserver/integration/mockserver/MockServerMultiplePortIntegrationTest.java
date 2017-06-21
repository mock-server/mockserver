package org.mockserver.integration.mockserver;

import com.google.common.base.Joiner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.socket.PortFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.junit.Assert.assertEquals;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerMultiplePortIntegrationTest extends AbstractRestartableMockServerNettyIntegrationTest {

    private final static int TEST_SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static Integer[] severHttpPort;
    private static EchoServer echoServer;
    private final Random random = new Random();

    @BeforeClass
    public static void startServer() throws InterruptedException, ExecutionException {
        // start mock server and client
        mockServerClient = startClientAndServer(0, PortFactory.findFreePort(), 0, PortFactory.findFreePort());
        List<Integer> boundPorts = ((ClientAndServer) mockServerClient).getPorts();
        severHttpPort = boundPorts.toArray(new Integer[boundPorts.size()]);

        // start echo servers
        echoServer = new EchoServer(TEST_SERVER_HTTP_PORT, false);
    }

    @AfterClass
    public static void stopServer() {
        // stop mock server and client
        if (mockServerClient instanceof ClientAndServer) {
            mockServerClient.stop();
        }

        // stop echo server
        echoServer.stop();
    }

    @Override
    public void startServerAgain() {
        startClientAndServer(severHttpPort);
    }

    @Override
    public int getMockServerPort() {
        return severHttpPort[random.nextInt(severHttpPort.length)];
    }

    @Override
    public int getMockServerSecurePort() {
        return severHttpPort[random.nextInt(severHttpPort.length)];
    }

    @Override
    public int getTestServerPort() {
        return TEST_SERVER_HTTP_PORT;
    }

    @Test
    public void shouldReturnStatus() {
        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                        .withBody("{" + System.getProperty("line.separator") +
                                "  \"ports\" : [ " + Joiner.on(", ").join(severHttpPort) + " ]" + System.getProperty("line.separator") +
                                "}"),
                makeRequest(
                        request()
                                .withPath(calculatePath("status"))
                                .withMethod("PUT"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                        .withBody("{" + System.getProperty("line.separator") +
                                "  \"ports\" : [ " + Joiner.on(", ").join(severHttpPort) + " ]" + System.getProperty("line.separator") +
                                "}"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("status"))
                                .withMethod("PUT"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldBindToNewSocket() {
        // given
        int firstNewPort = PortFactory.findFreePort();
        int secondNewPort = PortFactory.findFreePort();

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                        .withBody("{" + System.getProperty("line.separator") +
                                "  \"ports\" : [ " + firstNewPort + " ]" + System.getProperty("line.separator") +
                                "}"),
                makeRequest(
                        request()
                                .withPath(calculatePath("bind"))
                                .withMethod("PUT")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "  \"ports\" : [ " + firstNewPort + " ]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                        .withBody("{" + System.getProperty("line.separator") +
                                "  \"ports\" : [ " + Joiner.on(", ").join(severHttpPort) + ", " + firstNewPort + " ]" + System.getProperty("line.separator") +
                                "}"),
                makeRequest(
                        request()
                                .withPath(calculatePath("status"))
                                .withMethod("PUT"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                        .withBody("{" + System.getProperty("line.separator") +
                                "  \"ports\" : [ " + secondNewPort + " ]" + System.getProperty("line.separator") +
                                "}"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("bind"))
                                .withMethod("PUT")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "  \"ports\" : [ " + secondNewPort + " ]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                        .withBody("{" + System.getProperty("line.separator") +
                                "  \"ports\" : [ " + Joiner.on(", ").join(severHttpPort) + ", " + firstNewPort + ", " + secondNewPort + " ]" + System.getProperty("line.separator") +
                                "}"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("status"))
                                .withMethod("PUT")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "  \"ports\" : [ " + firstNewPort + " ]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
    }
}
