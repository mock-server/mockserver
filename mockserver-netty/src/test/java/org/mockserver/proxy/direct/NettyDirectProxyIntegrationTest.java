package org.mockserver.proxy.direct;

import com.google.common.base.Charsets;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.integration.testserver.TestServer;
import org.mockserver.proxy.http.HttpProxy;
import org.mockserver.proxy.http.HttpProxyBuilder;
import org.mockserver.socket.PortFactory;
import org.mockserver.streams.IOStreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.Socket;

import static org.mockserver.test.Assert.assertContains;

/**
 * @author jamesdbloom
 */
public class NettyDirectProxyIntegrationTest {

    private final static Logger logger = LoggerFactory.getLogger(NettyDirectProxyIntegrationTest.class);

    private final static Integer SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static Integer SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_DIRECT_PORT = PortFactory.findFreePort();
    private static TestServer testServer = new TestServer();
    private static HttpProxy httpProxy;

    @BeforeClass
    public static void setupFixture() throws Exception {
        logger.debug("SERVER_HTTP_PORT = " + SERVER_HTTP_PORT);
        logger.debug("SERVER_HTTPS_PORT = " + SERVER_HTTPS_PORT);
        logger.debug("PROXY_DIRECT_PORT = " + PROXY_DIRECT_PORT);

        // start server
        testServer.startServer(SERVER_HTTP_PORT, SERVER_HTTPS_PORT);

        // start proxy
        httpProxy = new HttpProxyBuilder()
                .withDirect(PROXY_DIRECT_PORT, "127.0.0.1", SERVER_HTTP_PORT)
                .build();
    }

    @AfterClass
    public static void shutdownFixture() {
        // stop server
        testServer.stop();

        // stop proxy
        httpProxy.stop();
    }

    @Test
    public void shouldForwardRequestsUsingSocketDirectlyHeadersOnly() throws Exception {
        Socket socket = null;
        try {
            socket = new Socket("localhost", PROXY_DIRECT_PORT);

            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send GET request for headers only
            output.write(("" +
                    "GET /test_headers_only HTTP/1.1\r" + System.getProperty("line.separator") +
                    "Host: localhost:" + SERVER_HTTP_PORT + "\r" + System.getProperty("line.separator") +
                    "\r" + System.getProperty("line.separator")
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "X-Test: test_headers_only");
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldForwardRequestsUsingSocketDirectlyHeadersAndBody() throws Exception {
        Socket socket = null;
        try {

            socket = new Socket("localhost", PROXY_DIRECT_PORT);

            // given
            OutputStream output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                    "GET /test_headers_and_body HTTP/1.1\r" + System.getProperty("line.separator") +
                    "Host: localhost:" + SERVER_HTTP_PORT + "\r" + System.getProperty("line.separator") +
                    "\r" + System.getProperty("line.separator")
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            String response = IOStreamUtils.readInputStreamToString(socket);
            assertContains(response, "X-Test: test_headers_and_body");
            assertContains(response, "an_example_body");
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldForwardRequestsUsingSocketDirectlyNotFound() throws Exception {
        Socket socket = null;
        try {

            socket = new Socket("localhost", PROXY_DIRECT_PORT);

            // given
            OutputStream output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                    "GET /unknown HTTP/1.1\r" + System.getProperty("line.separator") +
                    "Host: localhost:" + SERVER_HTTP_PORT + "\r" + System.getProperty("line.separator") +
                    "\r" + System.getProperty("line.separator")
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 404 Not Found");
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }
}
