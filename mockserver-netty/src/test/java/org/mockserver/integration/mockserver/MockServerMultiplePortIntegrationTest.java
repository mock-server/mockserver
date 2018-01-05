package org.mockserver.integration.mockserver;

import com.google.common.base.Joiner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;

/**
 * @author jamesdbloom
 */
public class MockServerMultiplePortIntegrationTest extends AbstractRestartableMockServerNettyIntegrationTest {

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
        echoServer = new EchoServer( false);
    }

    @AfterClass
    public static void stopServer() {
        // stop mock server and client
        if (mockServerClient instanceof ClientAndServer) {
            mockServerClient.stop();
        }

        // stop echo server
        if (echoServer != null) {
            echoServer.stop();
        }
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
        return echoServer.getPort();
    }

    @Test
    public void shouldReturnStatus() {
        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody("{" + NEW_LINE +
                    "  \"ports\" : [ " + Joiner.on(", ").join(severHttpPort) + " ]" + NEW_LINE +
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
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody("{" + NEW_LINE +
                    "  \"ports\" : [ " + Joiner.on(", ").join(severHttpPort) + " ]" + NEW_LINE +
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
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody("{" + NEW_LINE +
                    "  \"ports\" : [ " + firstNewPort + " ]" + NEW_LINE +
                    "}"),
            makeRequest(
                request()
                    .withPath(calculatePath("bind"))
                    .withMethod("PUT")
                    .withBody("{" + NEW_LINE +
                        "  \"ports\" : [ " + firstNewPort + " ]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody("{" + NEW_LINE +
                    "  \"ports\" : [ " + Joiner.on(", ").join(severHttpPort) + ", " + firstNewPort + " ]" + NEW_LINE +
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
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody("{" + NEW_LINE +
                    "  \"ports\" : [ " + secondNewPort + " ]" + NEW_LINE +
                    "}"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("bind"))
                    .withMethod("PUT")
                    .withBody("{" + NEW_LINE +
                        "  \"ports\" : [ " + secondNewPort + " ]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody("{" + NEW_LINE +
                    "  \"ports\" : [ " + Joiner.on(", ").join(severHttpPort) + ", " + firstNewPort + ", " + secondNewPort + " ]" + NEW_LINE +
                    "}"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("status"))
                    .withMethod("PUT")
                    .withBody("{" + NEW_LINE +
                        "  \"ports\" : [ " + firstNewPort + " ]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
    }
}
