package org.mockserver.proxy.direct;

import org.apache.commons.io.Charsets;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.helloworld.HttpHelloWorldServer;
import org.mockserver.netty.proxy.direct.DirectProxy;
import org.mockserver.netty.proxy.http.HttpProxy;
import org.mockserver.socket.PortFactory;
import org.mockserver.streams.IOStreamUtils;

import java.io.OutputStream;
import java.net.Socket;

import static org.mockserver.test.Assert.assertContains;

/**
 * @author jamesdbloom
 */
public class NettyHttpProxyIntegrationTest {

    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTP_PORT = PortFactory.findFreePort();
    private static HttpHelloWorldServer httpHelloWorldServer;
    private static DirectProxy directProxy;
    private static ProxyClient proxyClient;

    @BeforeClass
    public static void setupFixture() throws Exception {
        // start server
        httpHelloWorldServer = new HttpHelloWorldServer(SERVER_HTTP_PORT, SERVER_HTTPS_PORT);

        // start proxy
        directProxy = new DirectProxy(PROXY_HTTP_PORT, "127.0.0.1", SERVER_HTTP_PORT);

        // start client
        proxyClient = new ProxyClient("localhost", PROXY_HTTP_PORT);
    }

    @AfterClass
    public static void shutdownFixture() {
        // stop server
        httpHelloWorldServer.stop();

        // stop proxy
        directProxy.stop();
    }

    @Before
    public void resetProxy() {
        proxyClient.reset();
    }

    @Test
    public void shouldForwardRequestsUsingSocketDirectly() throws Exception {
        Socket socket = null;
        try {
            socket = new Socket("localhost", PROXY_HTTP_PORT);

            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send GET request for headers only
            output.write(("" +
                    "GET /test_headers_only HTTP/1.1\r\n" +
                    "Host: localhost:" + SERVER_HTTP_PORT + "\r\n" +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "X-Test: test_headers_only");

            socket = new Socket("localhost", PROXY_HTTP_PORT);

            // given
            output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                    "GET /test_headers_and_body HTTP/1.1\r\n" +
                    "Host: localhost:" + SERVER_HTTP_PORT + "\r\n" +
                    "\r\n"
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
}
