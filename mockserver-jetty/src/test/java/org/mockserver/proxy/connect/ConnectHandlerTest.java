package org.mockserver.proxy.connect;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.integration.proxy.BufferReaderUtils;
import org.mockserver.integration.proxy.SSLContextFactory;
import org.mockserver.integration.proxy.SSLFactory;

import javax.net.ssl.SSLSocket;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.mockserver.test.Assert.assertContains;

/**
 * @author jamesdbloom
 */
public class ConnectHandlerTest {

    private final int HTTP_PORT = 8090;
    private final int HTTPS_PORT = 8091;
    private Server server;
    private SslContextFactory sslContextFactory = SSLContextFactory.createSSLContextFactory();

    @After
    public void stopServers() throws Exception {
        server.stop();
    }

    @Before
    public void startServer() throws Exception {
        server = new Server();
        addServerConnector(server, HTTP_PORT, false);
        addServerConnector(server, HTTPS_PORT, true);
        server.setHandler(new ConnectHandler(new AbstractHandler() {
            public void handle(String target, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
                String uri = httpServletRequest.getRequestURI();
                if ("/test_headers_only".equals(uri)) {
                    request.setHandled(true);
                    httpServletResponse.setStatus(304);
                    httpServletResponse.setHeader("X-Test", "test_headers_only");
                } else if ("/test_headers_and_body".equals(uri)) {
                    request.setHandled(true);
                    httpServletResponse.setStatus(200);
                    httpServletResponse.setHeader("X-Test", "test_headers_and_body");
                    httpServletResponse.getOutputStream().print("an_example_body");
                }
            }
        }, HTTPS_PORT));
        server.start();
    }

    private void addServerConnector(Server server, int port, boolean isSecure) throws Exception {
        ServerConnector serverConnector = new ServerConnector(server);
        if (isSecure) {
            serverConnector = new ServerConnector(server, sslContextFactory);
        }
        serverConnector.setPort(port);
        server.addConnector(serverConnector);
    }

    @Test
    public void shouldConnectToSecurePort() throws Exception {
        try (Socket socket = new Socket("localhost", HTTP_PORT)) {
            // given
            OutputStream output = socket.getOutputStream();

            // when
            output.write(("" +
                    "CONNECT localhost:666 HTTP/1.1\r\n" +
                    "Host: localhost:666\r\n" +
                    "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertContains(BufferReaderUtils.readerToString(socket.getInputStream()), "HTTP/1.1 200 OK");
        }
    }

    @Test
    public void shouldHandleConnectFailure() throws Exception {
        Server server = new Server();
        addServerConnector(server, 1090, false);
        server.setHandler(new ConnectHandler(null, 1091));
        try {
            server.start();

            try (Socket socket = new Socket("localhost", 1090)) {
                // given
                OutputStream output = socket.getOutputStream();

                // when
                output.write(("" +
                        "CONNECT localhost:666 HTTP/1.1\r\n" +
                        "Host: localhost:666\r\n" +
                        "\r\n"
                ).getBytes(StandardCharsets.UTF_8));
                output.flush();

                // then
                assertContains(BufferReaderUtils.readerToString(socket.getInputStream()), "HTTP/1.1 504 Gateway Timeout");
            }
        } finally {
            server.stop();
        }
    }

    @Test
    public void shouldForwardRequestsToSecurePortUsingSocketDirectly() throws Exception {
        try (Socket socket = new Socket("localhost", HTTP_PORT)) {
            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send CONNECT request
            output.write(("" +
                    "CONNECT localhost:666 HTTP/1.1\r\n" +
                    "Host: localhost:666\r\n" +
                    "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // - flush CONNECT response
            assertContains(BufferReaderUtils.readerToString(socket.getInputStream()), "HTTP/1.1 200 OK");

            // Upgrade the socket to SSL
            try (SSLSocket sslSocket = SSLFactory.wrapSocket(socket, sslContextFactory.getSslContext())) {
                output = sslSocket.getOutputStream();

                // - send GET request for headers only
                output.write(("" +
                        "GET /test_headers_only HTTP/1.1\r\n" +
                        "Host: localhost:666\r\n" +
                        "\r\n"
                ).getBytes(StandardCharsets.UTF_8));
                output.flush();

                // then
                assertContains(BufferReaderUtils.readerToString(sslSocket.getInputStream()), "X-Test: test_headers_only");

                // - send GET request for headers and body
                output.write(("" +
                        "GET /test_headers_and_body HTTP/1.1\r\n" +
                        "Host: localhost:666\r\n" +
                        "\r\n"
                ).getBytes(StandardCharsets.UTF_8));
                output.flush();

                // then
                String response = BufferReaderUtils.readerToString(sslSocket.getInputStream());
                assertContains(response, "X-Test: test_headers_and_body");
                assertContains(response, "an_example_body");
            }
        }
    }

    @Test
    public void shouldForwardRequestsToSecurePortUsingHttpClient() throws Exception {
        // given
        HttpClient httpClient = new HttpClient(sslContextFactory);
        httpClient.getProxyConfiguration().getProxies().add(new HttpProxy("localhost", HTTP_PORT));
        try {
            httpClient.start();

            // when
            ContentResponse response = httpClient.newRequest("localhost", HTTP_PORT)
                    .scheme(HttpScheme.HTTPS.asString())
                    .method(HttpMethod.GET)
                    .path("/test_headers_and_body")
                    .send();

            // then
            assertEquals(HttpStatus.OK_200, response.getStatus());
            assertEquals("an_example_body", response.getContentAsString());
        } finally {
            httpClient.stop();
        }
    }

    @Test
    public void shouldForwardRequestsToUnknownPath() throws Exception {
        try (Socket socket = new Socket("localhost", HTTP_PORT)) {
            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send CONNECT request
            output.write(("" +
                    "CONNECT localhost:666 HTTP/1.1\r\n" +
                    "Host: localhost:666\r\n" +
                    "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // - flush CONNECT response
            assertContains(BufferReaderUtils.readerToString(socket.getInputStream()), "HTTP/1.1 200 OK");

            // Upgrade the socket to SSL
            try (SSLSocket sslSocket = SSLFactory.wrapSocket(socket, sslContextFactory.getSslContext())) {
                // - send GET request
                output = sslSocket.getOutputStream();
                output.write(("" +
                        "GET /unknown HTTP/1.1\r\n" +
                        "Host: localhost:666\r\n" +
                        "\r\n"
                ).getBytes(StandardCharsets.UTF_8));
                output.flush();

                // then
                assertContains(BufferReaderUtils.readerToString(sslSocket.getInputStream()), "HTTP/1.1 404 Not Found");
            }
        }
    }
}
