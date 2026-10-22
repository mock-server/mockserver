package org.mockserver.netty.proxy;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;
import org.mockserver.streams.IOStreamUtils;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * With transparent proxying on, a connection that begins with a PROXY protocol header is matched by the client
 * address that header names, on the normal path and on the early (respondBeforeBody) path alike.
 */
public class ClientIpProxyProtocolIntegrationTest {

    private static MockServer mockServer;
    private static MockServerClient client;

    @BeforeClass
    public static void startServer() {
        mockServer = new MockServer(configuration().transparentProxyEnabled(true), 0);
        client = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServer);
    }

    @Before
    public void reset() {
        client.reset();
    }

    @Test
    public void shouldMatchTheClientAddressNamedByTheProxyProtocolHeader() throws Exception {
        client.when(request().withPath("/who").withClientIp("203.0.113.0/24")).respond(response().withBody("proxied client"));
        client.when(request().withPath("/who").withClientIp("127.0.0.0/8")).respond(response().withBody("loopback client"));

        assertThat(send("PROXY TCP4 203.0.113.9 127.0.0.1 5555 " + mockServer.getLocalPort() + "\r\n", "/who"), containsString("proxied client"));
        assertThat(send("", "/who"), containsString("loopback client"));
    }

    @Test
    public void shouldMatchTheProxyProtocolClientAddressWhenRespondingBeforeTheBody() throws Exception {
        client.when(request().withPath("/early").withClientIp("198.51.100.0/24").withRespondBeforeBody(true)).respond(response().withBody("early proxied client"));
        client.when(request().withPath("/early").withClientIp("127.0.0.0/8").withRespondBeforeBody(true)).respond(response().withBody("early loopback client"));

        assertThat(send("PROXY TCP4 198.51.100.20 127.0.0.1 5555 " + mockServer.getLocalPort() + "\r\n", "/early"), containsString("early proxied client"));
    }

    private static String send(String proxyHeader, String path) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            socket.setSoTimeout(10_000);
            OutputStream output = socket.getOutputStream();
            String httpRequest = "GET " + path + " HTTP/1.1\r\n" +
                "Host: 127.0.0.1:" + mockServer.getLocalPort() + "\r\n" +
                "\r\n";
            output.write((proxyHeader + httpRequest).getBytes(StandardCharsets.UTF_8));
            output.flush();
            return IOStreamUtils.readHttpInputStreamToString(socket);
        }
    }
}
