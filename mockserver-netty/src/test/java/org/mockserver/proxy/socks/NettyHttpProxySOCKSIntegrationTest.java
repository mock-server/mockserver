package org.mockserver.proxy.socks;

import com.google.common.base.Charsets;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.junit.*;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.SSLFactory;
import org.mockserver.streams.IOStreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.test.Assert.assertContains;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class NettyHttpProxySOCKSIntegrationTest {

    private final static Logger logger = LoggerFactory.getLogger(NettyHttpProxySOCKSIntegrationTest.class);

    private final static Integer SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static Integer SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_HTTP_PORT = PortFactory.findFreePort();
    private static EchoServer insecureEchoServer;
    private static EchoServer secureEchoServer;
    private static Proxy httpProxy;
    private static ProxyClient proxyClient;

    @BeforeClass
    public static void setupFixture() throws Exception {
        logger.debug("SERVER_HTTPS_PORT = " + SERVER_HTTPS_PORT);
        logger.debug("PROXY_HTTP_PORT = " + PROXY_HTTP_PORT);

        // start server
        insecureEchoServer = new EchoServer(SERVER_HTTP_PORT, false);
        secureEchoServer = new EchoServer(SERVER_HTTPS_PORT, true);

        // start proxy
        httpProxy = new ProxyBuilder()
                .withLocalPort(PROXY_HTTP_PORT)
                .build();

        // start client
        proxyClient = new ProxyClient("localhost", PROXY_HTTP_PORT);
    }

    @AfterClass
    public static void shutdownFixture() {
        // stop server
        insecureEchoServer.stop();
        secureEchoServer.stop();

        // stop proxy
        httpProxy.stop();
    }

    @Before
    public void resetProxy() {
        proxyClient.reset();
    }

    @Test
    @Ignore("Fails only on drone.io, runs correctly on travis and multiple local machines")
    public void shouldProxyRequestsUsingHttpClientViaSOCKSConfiguredForJVM() throws Exception {
        ProxySelector defaultProxySelector = ProxySelector.getDefault();
        try {
            // given - SOCKS proxy JVM settings
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<java.net.Proxy> select(URI uri) {
                    return Arrays.asList(
                            new java.net.Proxy(
                                    java.net.Proxy.Type.SOCKS,
                                    new InetSocketAddress(
                                            System.getProperty("http.proxyHost"),
                                            Integer.parseInt(System.getProperty("http.proxyPort"))
                                    )
                            )
                    );
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    System.out.println("Connection could not be established to proxy at socket [" + sa + "]");
                    ioe.printStackTrace();
                }
            });

            // and - an HTTP client
            HttpClient httpClient = HttpClientBuilder.create().build();

            // when
            HttpResponse response = httpClient.execute(new HttpHost("google.com", 443, "https"), new HttpGet("/"));

            // then
            assertThat(response.getStatusLine().getStatusCode(), is(200));
            proxyClient.verify(request().withHeader("Host", "google.com:443"));
        } finally {
            ProxySelector.setDefault(defaultProxySelector);
        }
    }

    @Test
    public void shouldProxyRequestsUsingHttpClientViaSOCKS() throws Exception {
        // given
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", new ConnectionSocketFactory() {

                    public Socket createSocket(final HttpContext context) throws IOException {
                        return new Socket(new java.net.Proxy(
                                java.net.Proxy.Type.SOCKS,
                                new InetSocketAddress(
                                        System.getProperty("http.proxyHost"),
                                        Integer.parseInt(System.getProperty("http.proxyPort"))
                                )));
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

        PoolingHttpClientConnectionManager clientConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(clientConnectionManager)
                .build();

        // when
        HttpPost request = new HttpPost(
                new URIBuilder()
                        .setScheme("http")
                        .setHost("localhost")
                        .setPort(SERVER_HTTP_PORT)
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
    public void shouldProxyRequestsUsingRawSocketViaSOCKS() throws Exception {
        Socket socket = null;
        ProxySelector proxySelector = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<java.net.Proxy> select(URI uri) {
                    return Arrays.asList(
                            new java.net.Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", PROXY_HTTP_PORT))
                    );
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    logger.error("Connection could not be established to proxy at socket [" + sa + "]", ioe);
                }
            });

            socket = new Socket("localhost", SERVER_HTTP_PORT);

            // given
            OutputStream output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                    "GET /test_headers_and_body HTTP/1.1\r\n" +
                    "Host: localhost:" + SERVER_HTTP_PORT + "\r\n" +
                    "X-Test: test_headers_and_body\r\n" +
                    "Content-Length:" + "an_example_body".getBytes(Charsets.UTF_8).length + "\r\n" +
                    "\r\n" +
                    "an_example_body" + "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            // assertThat(socket.getInputStream().available(), greaterThan(0));
            String response = IOStreamUtils.readInputStreamToString(socket);
            assertContains(response, "X-Test: test_headers_and_body");
            assertContains(response, "an_example_body");

            // and
            proxyClient.verify(
                    request()
                            .withMethod("GET")
                            .withPath("/test_headers_and_body")
                            .withBody("an_example_body"),
                    exactly(1)
            );
        } finally {
            ProxySelector.setDefault(proxySelector);
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldProxyRequestsUsingRawSecureSocketViaSOCKS() throws Exception {
        Socket socket = null;
        ProxySelector proxySelector = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<java.net.Proxy> select(URI uri) {
                    return Arrays.asList(
                            new java.net.Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", PROXY_HTTP_PORT))
                    );
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    logger.error("Connection could not be established to proxy at socket [" + sa + "]", ioe);
                }
            });

            socket = SSLFactory.getInstance().wrapSocket(new Socket("localhost", SERVER_HTTP_PORT));

            // given
            OutputStream output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                    "GET /test_headers_and_body HTTP/1.1\r\n" +
                    "Host: localhost:" + SERVER_HTTP_PORT + "\r\n" +
                    "X-Test: test_headers_and_body\r\n" +
                    "Content-Length:" + "an_example_body".getBytes(Charsets.UTF_8).length + "\r\n" +
                    "\r\n" +
                    "an_example_body" + "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            // assertThat(socket.getInputStream().available(), greaterThan(0));
            String response = IOStreamUtils.readInputStreamToString(socket);
            assertContains(response, "X-Test: test_headers_and_body");
            assertContains(response, "an_example_body");

            // and
            proxyClient.verify(
                    request()
                            .withMethod("GET")
                            .withPath("/test_headers_and_body")
                            .withBody("an_example_body"),
                    exactly(1)
            );
        } finally {
            ProxySelector.setDefault(proxySelector);
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldProxyRequestsUsingHttpClientViaSOCKSToSecureServerPort() throws Exception {
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
        request.addHeader("Secure", "true");
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
    public void shouldProxyRequestsUsingRawSocketViaSOCKSToSecureServerPort() throws Exception {
        Socket socket = null;
        ProxySelector proxySelector = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<java.net.Proxy> select(URI uri) {
                    return Arrays.asList(
                            new java.net.Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", PROXY_HTTP_PORT))
                    );
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    logger.error("Connection could not be established to proxy at socket [" + sa + "]", ioe);
                }
            });

            socket = new Socket("localhost", SERVER_HTTPS_PORT);

            // given
            OutputStream output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                    "GET /test_headers_and_body HTTP/1.1\r\n" +
                    "Host: localhost:" + SERVER_HTTPS_PORT + "\r\n" +
                    "X-Test: test_headers_and_body\r\n" +
                    "Content-Length:" + "an_example_body".getBytes(Charsets.UTF_8).length + "\r\n" +
                    "\r\n" +
                    "an_example_body" + "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            // assertThat(socket.getInputStream().available(), greaterThan(0));
            String response = IOStreamUtils.readInputStreamToString(socket);
            assertContains(response, "X-Test: test_headers_and_body");
            assertContains(response, "an_example_body");

            // and
            proxyClient.verify(
                    request()
                            .withMethod("GET")
                            .withPath("/test_headers_and_body")
                            .withBody("an_example_body"),
                    exactly(1)
            );
        } finally {
            ProxySelector.setDefault(proxySelector);
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldProxyRequestsUsingRawSecureSocketViaSOCKSToSecureServerPort() throws Exception {
        Socket socket = null;
        ProxySelector proxySelector = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<java.net.Proxy> select(URI uri) {
                    return Arrays.asList(
                            new java.net.Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", PROXY_HTTP_PORT))
                    );
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    logger.error("Connection could not be established to proxy at socket [" + sa + "]", ioe);
                }
            });

            socket = SSLFactory.getInstance().wrapSocket(new Socket("localhost", SERVER_HTTPS_PORT));

            // given
            OutputStream output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                    "GET /test_headers_and_body HTTP/1.1\r\n" +
                    "Host: localhost:" + SERVER_HTTPS_PORT + "\r\n" +
                    "X-Test: test_headers_and_body\r\n" +
                    "Content-Length:" + "an_example_body".getBytes(Charsets.UTF_8).length + "\r\n" +
                    "\r\n" +
                    "an_example_body" + "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            // assertThat(socket.getInputStream().available(), greaterThan(0));
            String response = IOStreamUtils.readInputStreamToString(socket);
            assertContains(response, "X-Test: test_headers_and_body");
            assertContains(response, "an_example_body");

            // and
            proxyClient.verify(
                    request()
                            .withMethod("GET")
                            .withPath("/test_headers_and_body")
                            .withBody("an_example_body"),
                    exactly(1)
            );
        } finally {
            ProxySelector.setDefault(proxySelector);
            if (socket != null) {
                socket.close();
            }
        }
    }
}
