package org.mockserver.proxy.socks;

import com.google.common.base.Charsets;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.echo.EchoServer;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.PortFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class NettyHttpProxySOCKSIntegrationTest {

    private final static Logger logger = LoggerFactory.getLogger(NettyHttpProxySOCKSIntegrationTest.class);

    private final static Integer SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_HTTPS_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;
    private static Proxy httpProxy;
    private static ProxyClient proxyClient;

    @BeforeClass
    public static void setupFixture() throws Exception {
        logger.debug("SERVER_HTTPS_PORT = " + SERVER_HTTPS_PORT);
        logger.debug("PROXY_HTTPS_PORT = " + PROXY_HTTPS_PORT);

        // start server
        echoServer = new EchoServer(SERVER_HTTPS_PORT);

        // start proxy
        httpProxy = new ProxyBuilder()
                .withLocalPort(PROXY_HTTPS_PORT)
                .build();

        // start client
        proxyClient = new ProxyClient("localhost", PROXY_HTTPS_PORT);
    }

    @AfterClass
    public static void shutdownFixture() {
        // stop server
        echoServer.stop();

        // stop proxy
        httpProxy.stop();
    }

    @Before
    public void resetProxy() {
        proxyClient.reset();
    }

    @Test
    public void shouldForwardRequestsUsingHttpClientViaSOCKS() throws Exception {
        // given
        Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", new ConnectionSocketFactory() {

                    public Socket createSocket(final HttpContext context) throws IOException {
                        return new Socket(new java.net.Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort")))));
                    }

                    public Socket connectSocket(
                            final int connectTimeout,
                            final Socket socket,
                            final HttpHost host,
                            final InetSocketAddress remoteAddress,
                            final InetSocketAddress localAddress,
                            final HttpContext context) throws IOException {
                        Socket sock;
                        if (socket != null) {
                            sock = socket;
                        } else {
                            sock = createSocket(context);
                        }
                        if (localAddress != null) {
                            sock.bind(localAddress);
                        }
                        try {
                            sock.connect(remoteAddress, connectTimeout);
                        } catch (SocketTimeoutException ex) {
                            throw new ConnectTimeoutException(ex, host, remoteAddress.getAddress());
                        }
                        return sock;
                    }

                })
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(reg);
        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .build();

        // when
        HttpPost request = new HttpPost(
                new URIBuilder()
                        .setScheme("http")
                        .setHost("localhost")
                        .setPort(SERVER_HTTPS_PORT)
                        .setPath("/test_headers_and_body")
                        .build()
        );
        request.setEntity(new StringEntity("an_example_body"));
        HttpResponse response = httpClient.execute(request);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), response.getStatusLine().getStatusCode());
        assertEquals("an_example_body", new String(EntityUtils.toByteArray(response.getEntity()), Charsets.UTF_8));

        // and
        proxyClient.verify(
                request()
                        .withPath("/test_headers_and_body")
                        .withBody("an_example_body"),
                exactly(1)
        );
    }

    @Test
    public void shouldForwardRequestsToSecurePortUsingHttpClientViaSOCKS() throws Exception {
        // given
        Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", new ConnectionSocketFactory() {

                    public Socket createSocket(final HttpContext context) throws IOException {
                        return new Socket(new java.net.Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort")))));
                    }

                    public Socket connectSocket(
                            final int connectTimeout,
                            final Socket socket,
                            final HttpHost host,
                            final InetSocketAddress remoteAddress,
                            final InetSocketAddress localAddress,
                            final HttpContext context) throws IOException {
                        Socket sock;
                        if (socket != null) {
                            sock = socket;
                        } else {
                            sock = createSocket(context);
                        }
                        if (localAddress != null) {
                            sock.bind(localAddress);
                        }
                        try {
                            sock.connect(remoteAddress, connectTimeout);
                        } catch (SocketTimeoutException ex) {
                            throw new ConnectTimeoutException(ex, host, remoteAddress.getAddress());
                        }
                        return sock;
                    }

                })
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(reg);
        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .build();

        // when
        HttpPost request = new HttpPost(
                new URIBuilder()
                        .setScheme("https")
                        .setHost("localhost")
                        .setPort(SERVER_HTTPS_PORT)
                        .setPath("/test_headers_and_body")
                        .build()
        );
        request.setEntity(new StringEntity("an_example_body"));
        HttpResponse response = httpClient.execute(request);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), response.getStatusLine().getStatusCode());
        assertEquals("an_example_body", new String(EntityUtils.toByteArray(response.getEntity()), Charsets.UTF_8));

        // and
        proxyClient.verify(
                request()
                        .withPath("/test_headers_and_body")
                        .withBody("an_example_body"),
                exactly(1)
        );
    }
}
