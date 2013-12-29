package org.mockserver.proxy.connect;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.mockserver.test.Assert.assertContains;

/**
 * @author jamesdbloom
 */
public class ConnectHandlerTest {

    private final int HTTP_PORT = 8090;
    private final int HTTPS_PORT = 8091;
    protected Server server;

    @After
    public void stopServers() throws Exception {
        server.stop();
    }

    @Before
    public void startServer() throws Exception {
        server = new Server();
        addServerConnector(server, HTTP_PORT);
        addServerConnector(server, HTTPS_PORT);
        ServletHandler servletHandler = new ServletHandler();
        servletHandler.addServletWithMapping(TestServlet.class, "/test");
        server.setHandler(new ConnectHandler(servletHandler, HTTPS_PORT));
        server.start();
    }

    protected void addServerConnector(Server server, int port) {
        ServerConnector serverConnector = new ServerConnector(server);
        serverConnector.setPort(port);
        server.addConnector(serverConnector);
    }

    @Test
    public void shouldConnectToSecurePort() throws Exception {
        try (Socket socket = new Socket("localhost", HTTP_PORT)) {
            // given
            OutputStream output = socket.getOutputStream();
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // when
            output.write(("" +
                    "CONNECT localhost:666 HTTP/1.1\r\n" +
                    "Host: localhost:666\r\n" +
                    "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertContains(readerToString(input), "HTTP/1.1 101 Switching Protocols");
        }
    }

    @Test
    public void shouldHandleConnectFailure() throws Exception {
        Server server = new Server();
        addServerConnector(server, 1090);
        server.setHandler(new ConnectHandler(null, 1091));
        try {
            server.start();

            try (Socket socket = new Socket("localhost", 1090)) {
                // given
                OutputStream output = socket.getOutputStream();
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // when
                output.write(("" +
                        "CONNECT localhost:666 HTTP/1.1\r\n" +
                        "Host: localhost:666\r\n" +
                        "\r\n"
                ).getBytes(StandardCharsets.UTF_8));
                output.flush();

                // then
                assertContains(readerToString(input), "HTTP/1.1 504 Gateway Timeout");
            }
        } finally {
            server.stop();
        }
    }

    @Test
    public void shouldForwardRequestsToSecurePort() throws Exception {
        try (Socket socket = new Socket("localhost", HTTP_PORT)) {
            // given
            OutputStream output = socket.getOutputStream();
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // when

            // - send CONNECT request
            output.write(("" +
                    "CONNECT localhost:666 HTTP/1.1\r\n" +
                    "Host: localhost:666\r\n" +
                    "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // - flush CONNECT response
            assertContains(readerToString(input), "HTTP/1.1 101 Switching Protocols");

            // - send GET request
            output.write(("" +
                    "GET /test HTTP/1.1\r\n" +
                    "Host: localhost:666\r\n" +
                    "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertContains(readerToString(input), "X-Test: request-received");
        }
    }

    @Test
    public void shouldForwardRequestsToUnknownPath() throws Exception {
        try (Socket socket = new Socket("localhost", HTTP_PORT)) {
            // given
            OutputStream output = socket.getOutputStream();
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // when

            // - send CONNECT request
            output.write(("" +
                    "CONNECT localhost:666 HTTP/1.1\r\n" +
                    "Host: localhost:666\r\n" +
                    "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // - flush CONNECT response
            assertContains(readerToString(input), "HTTP/1.1 101 Switching Protocols");

            // - send GET request
            output.write(("" +
                    "GET /unknown HTTP/1.1\r\n" +
                    "Host: localhost:666\r\n" +
                    "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertContains(readerToString(input), "HTTP/1.1 404 Not Found");
        }
    }

    private String readerToString(BufferedReader input) throws IOException {
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = input.readLine()) != null) {
            if (line.length() == 0) {
                break;
            }
            result.append(line).append('\n');
        }
        System.out.println("result = " + result);
        return result.toString();
    }

    public static class TestServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
            httpServletResponse.setStatus(304);
            httpServletResponse.setHeader("X-Test", "request-received");
            httpServletResponse.flushBuffer();
        }
    }
}
