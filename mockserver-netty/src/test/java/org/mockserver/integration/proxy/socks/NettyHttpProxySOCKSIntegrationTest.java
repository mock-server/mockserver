package org.mockserver.integration.proxy.socks;

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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mockserver.MockServer;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.socket.KeyStoreFactory;
import org.mockserver.streams.IOStreamUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.socket.SSLSocketFactory.sslSocketFactory;
import static org.mockserver.test.Assert.assertContains;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class NettyHttpProxySOCKSIntegrationTest {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(NettyHttpProxySOCKSIntegrationTest.class);

    private static Integer mockServerPort;
    private static EchoServer insecureEchoServer;
    private static EchoServer secureEchoServer;
    private static MockServer httpProxy;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void setupFixture() {
        insecureEchoServer = new EchoServer(false);
        secureEchoServer = new EchoServer(true);

        mockServerPort = new MockServer().getLocalPort();

        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", String.valueOf(mockServerPort));

        mockServerClient = new MockServerClient("localhost", mockServerPort);
    }

    @AfterClass
    public static void shutdownFixture() {
        if (insecureEchoServer != null) {
            insecureEchoServer.stop();
        }
        if (secureEchoServer != null) {
            secureEchoServer.stop();
        }

        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");

        if (mockServerClient != null) {
            mockServerClient.stop();
        }
    }

    @Before
    public void resetProxy() {
        if (mockServerClient != null) {
            mockServerClient.reset();
        }
    }

    @Test
    public void shouldProxyRequestsUsingHttpClientViaSOCKSConfiguredForJVM() throws Exception {
        assumeThat("SOCKS5 is broken in JRE <9", System.getProperty("java.version"), not(anyOf(
            startsWith("1.7."), equalTo("1.7"),
            startsWith("7."), equalTo("7"),
            startsWith("1.8."), equalTo("1.8"),
            startsWith("8."), equalTo("8"))));

        ProxySelector defaultProxySelector = ProxySelector.getDefault();
        try {
            // given - SOCKS proxy JVM settings
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<java.net.Proxy> select(URI uri) {
                    return Collections.singletonList(
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
            HttpClient httpClient = HttpClientBuilder.create().setSslcontext(KeyStoreFactory.keyStoreFactory().sslContext()).build();

            // when
            HttpResponse response = httpClient.execute(new HttpHost("127.0.0.1", insecureEchoServer.getPort(), "http"), new HttpGet("/"));

            // then
            assertThat(response.getStatusLine().getStatusCode(), is(200));
            mockServerClient.verify(request().withHeader("Host", "127.0.0.1" + ":" + insecureEchoServer.getPort()));
        } finally {
            ProxySelector.setDefault(defaultProxySelector);
        }
    }

    @Test
    public void shouldProxyRequestsUsingHttpClientViaSOCKSConfiguredForJVMToSecureServerPort() throws Exception {
        assumeThat("SOCKS5 is broken in JRE <9", System.getProperty("java.version"), not(anyOf(
            startsWith("1.7."), equalTo("1.7"),
            startsWith("7."), equalTo("7"),
            startsWith("1.8."), equalTo("1.8"),
            startsWith("8."), equalTo("8"))));

        ProxySelector defaultProxySelector = ProxySelector.getDefault();
        try {
            // given - SOCKS proxy JVM settings
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<java.net.Proxy> select(URI uri) {
                    return Collections.singletonList(
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
            HttpClient httpClient = HttpClientBuilder.create().setSslcontext(KeyStoreFactory.keyStoreFactory().sslContext()).build();

            // when
            HttpResponse response = httpClient.execute(new HttpHost("127.0.0.1", secureEchoServer.getPort(), "https"), new HttpGet("/"));

            // then
            assertThat(response.getStatusLine().getStatusCode(), is(200));
            mockServerClient.verify(request().withHeader("Host", "127.0.0.1" + ":" + secureEchoServer.getPort()));
        } finally {
            ProxySelector.setDefault(defaultProxySelector);
        }
    }

    @Test
    public void shouldProxyRequestsUsingHttpClientViaSOCKS() throws Exception {
        assumeThat("SOCKS5 is broken in JRE <9", System.getProperty("java.version"), not(anyOf(
            startsWith("1.7."), equalTo("1.7"),
            startsWith("7."), equalTo("7"),
            startsWith("1.8."), equalTo("1.8"),
            startsWith("8."), equalTo("8"))));

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
                .setPort(insecureEchoServer.getPort())
                .setPath("/test_headers_and_body")
                .build()
        );
        request.setEntity(new StringEntity("an_example_body"));
        HttpResponse response = httpClient.execute(request);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), response.getStatusLine().getStatusCode());
        assertEquals("an_example_body", new String(EntityUtils.toByteArray(response.getEntity()), StandardCharsets.UTF_8));

        // and
        mockServerClient.verify(
            request()
                .withPath("/test_headers_and_body")
                .withBody("an_example_body"),
            exactly(1)
        );
    }

    @Test
    public void shouldProxyRequestsUsingHttpClientViaSOCKSToSecureServerPort() throws Exception {
        assumeThat("SOCKS5 is broken in JRE <9", System.getProperty("java.version"), not(anyOf(
            startsWith("1.7."), equalTo("1.7"),
            startsWith("7."), equalTo("7"),
            startsWith("1.8."), equalTo("1.8"),
            startsWith("8."), equalTo("8"))));

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
                    return sslSocketFactory().wrapSocket(sock);
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
                .setPort(secureEchoServer.getPort())
                .setPath("/test_headers_and_body")
                .build()
        );
        request.setEntity(new StringEntity("an_example_body"));
        request.addHeader("Secure", "true");
        HttpResponse response = httpClient.execute(request);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), response.getStatusLine().getStatusCode());
        assertEquals("an_example_body", new String(EntityUtils.toByteArray(response.getEntity()), StandardCharsets.UTF_8));

        // and
        mockServerClient.verify(
            request()
                .withPath("/test_headers_and_body")
                .withBody("an_example_body"),
            exactly(1)
        );
    }

    @Test
    public void shouldProxyRequestsUsingRawSocketViaSOCKS() throws Exception {
        proxyRequestsUsingRawSocketViaSOCKS(false);
    }

    @Test
    public void shouldProxyRequestsUsingRawSecureSocketViaSOCKSToSecureServerPort() throws Exception {
        assumeThat("SOCKS5 is broken in JRE <9", System.getProperty("java.version"), not(anyOf(
            startsWith("1.7."), equalTo("1.7"),
            startsWith("7."), equalTo("7"),
            startsWith("1.8."), equalTo("1.8"),
            startsWith("8."), equalTo("8"))));

        proxyRequestsUsingRawSocketViaSOCKS(true);
    }

    private void proxyRequestsUsingRawSocketViaSOCKS(boolean useTLS) throws Exception {
        Socket socket = null;
        ProxySelector proxySelector = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<java.net.Proxy> select(URI uri) {
                    return Collections.singletonList(
                        new java.net.Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", mockServerPort))
                    );
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    MOCK_SERVER_LOGGER.error("Connection could not be established to proxy at socket [" + sa + "]", ioe);
                }
            });

            if (useTLS) {
                Socket localhost = new Socket("localhost", secureEchoServer.getPort());
                socket = sslSocketFactory().wrapSocket(localhost);
            } else {
                socket = new Socket("localhost", insecureEchoServer.getPort());
            }

            // given
            OutputStream output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                "GET /test_headers_and_body HTTP/1.1\r\n" +
                "Host: localhost:" + (useTLS ? secureEchoServer.getPort() : insecureEchoServer.getPort()) + "\r\n" +
                "X-Test: test_headers_and_body\r\n" +
                "Content-Length:" + "an_example_body".getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                "\r\n" +
                "an_example_body" + "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            String response = IOStreamUtils.readInputStreamToString(socket);
            assertContains(response, "X-Test: test_headers_and_body");
            assertContains(response, "an_example_body");

            // and
            mockServerClient.verify(
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
