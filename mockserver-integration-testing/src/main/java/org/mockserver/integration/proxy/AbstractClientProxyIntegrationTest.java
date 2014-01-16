package org.mockserver.integration.proxy;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.Test;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.client.proxy.Times;
import org.mockserver.socket.SSLFactory;
import org.mockserver.streams.IOStreamUtils;

import javax.net.ssl.SSLSocket;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.test.Assert.assertContains;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientProxyIntegrationTest {

    protected static SslContextFactory sslContextFactory = createSSLContextFactory();

    public static SslContextFactory createSSLContextFactory() {
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStore(SSLFactory.buildKeyStore());
        sslContextFactory.setKeyStorePassword(SSLFactory.KEY_STORE_PASSWORD);
        sslContextFactory.setKeyManagerPassword(SSLFactory.KEY_STORE_PASSWORD);
        sslContextFactory.checkKeyStore();
        sslContextFactory.setTrustStore(SSLFactory.buildKeyStore());
        return sslContextFactory;
    }

    public abstract int getProxyPort();

    public abstract int getServerPort();

    public abstract int getServerSecurePort();

    @Test
    public void shouldForwardRequestsUsingSocketDirectly() throws Exception {
        try (Socket socket = new Socket("localhost", getProxyPort())) {
            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send GET request for headers only
            output.write(("" +
                    "GET /test_headers_only HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerPort() + "\r\n" +
                    "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "X-Test: test_headers_only");

            // - send GET request for headers and body
            output.write(("" +
                    "GET /test_headers_and_body HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerPort() + "\r\n" +
                    "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            String response = IOStreamUtils.readInputStreamToString(socket);
            assertContains(response, "X-Test: test_headers_and_body");
            assertContains(response, "an_example_body");
        }
    }

    @Test
    public void shouldForwardRequestsUsingHttpClient() throws Exception {
        // given
        HttpClient httpClient = new HttpClient();
        httpClient.getProxyConfiguration().getProxies().add(new HttpProxy("localhost", getProxyPort()));
        try {
            httpClient.start();

            // when
            ContentResponse response = httpClient.newRequest("localhost", getServerPort())
                    .scheme(HttpScheme.HTTP.asString())
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
        try (Socket socket = new Socket("localhost", getProxyPort())) {
            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send GET request
            output.write(("" +
                    "GET /unknown HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerPort() + "\r\n" +
                    "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 404 Not Found");
        }
    }

    @Test
    public void shouldConnectToSecurePort() throws Exception {
        try (Socket socket = new Socket("localhost", getProxyPort())) {
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
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 200 OK");
        }
    }

    @Test
    public void shouldForwardRequestsToSecurePortUsingSocketDirectly() throws Exception {
        try (Socket socket = new Socket("localhost", getProxyPort())) {
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
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 200 OK");

            // Upgrade the socket to SSL
            try (SSLSocket sslSocket = SSLFactory.wrapSocket(socket, sslContextFactory.getSslContext())) {
                output = sslSocket.getOutputStream();

                // - send GET request for headers only
                output.write(("" +
                        "GET /test_headers_only HTTP/1.1\r\n" +
                        "Host: localhost:" + getServerSecurePort() + "\r\n" +
                        "\r\n"
                ).getBytes(StandardCharsets.UTF_8));
                output.flush();

                // then
                assertContains(IOStreamUtils.readInputStreamToString(sslSocket), "X-Test: test_headers_only");

                // - send GET request for headers and body
                output.write(("" +
                        "GET /test_headers_and_body HTTP/1.1\r\n" +
                        "Host: localhost:" + getServerSecurePort() + "\r\n" +
                        "\r\n"
                ).getBytes(StandardCharsets.UTF_8));
                output.flush();

                // then
                String response = IOStreamUtils.readInputStreamToString(sslSocket);
                assertContains(response, "X-Test: test_headers_and_body");
                assertContains(response, "an_example_body");
            }
        }
    }

    @Test
    public void shouldForwardRequestsToSecurePortUsingHttpClient() throws Exception {
        // given
        HttpClient httpClient = new HttpClient(sslContextFactory);
        httpClient.getProxyConfiguration().getProxies().add(new HttpProxy("localhost", getProxyPort()));
        try {
            httpClient.start();

            // when
            ContentResponse response = httpClient.newRequest("localhost", getServerSecurePort())
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
    public void shouldForwardRequestsToSecurePortAndUnknownPath() throws Exception {
        try (Socket socket = new Socket("localhost", getProxyPort())) {
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
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 200 OK");

            // Upgrade the socket to SSL
            try (SSLSocket sslSocket = SSLFactory.wrapSocket(socket, sslContextFactory.getSslContext())) {
                // - send GET request
                output = sslSocket.getOutputStream();
                output.write(("" +
                        "GET /unknown HTTP/1.1\r\n" +
                        "Host: localhost:" + getServerSecurePort() + "\r\n" +
                        "\r\n"
                ).getBytes(StandardCharsets.UTF_8));
                output.flush();

                // then
                assertContains(IOStreamUtils.readInputStreamToString(sslSocket), "HTTP/1.1 404 Not Found");
            }
        }
    }

    @Test
    public void shouldVerifyRequests() throws Exception {
        // given
        HttpClient httpClient = new HttpClient();
        httpClient.getProxyConfiguration().getProxies().add(new HttpProxy("localhost", getProxyPort()));
        ProxyClient proxyClient = new ProxyClient("127.0.0.1", getProxyPort()).reset();
        try {
            httpClient.start();

            // when
            httpClient.newRequest("localhost", getServerPort())
                    .scheme(HttpScheme.HTTP.asString())
                    .method(HttpMethod.GET)
                    .path("/test_headers_and_body")
                    .send();
            httpClient.newRequest("localhost", getServerPort())
                    .scheme(HttpScheme.HTTP.asString())
                    .method(HttpMethod.GET)
                    .path("/test_headers_only")
                    .send();

            // then
            proxyClient
                    .verify(
                            request()
                                    .withMethod("GET")
                                    .withPath("/test_headers_and_body"),
                            Times.exactly(1)
                    );
            proxyClient
                    .verify(
                            request()
                                    .withPath("/test_headers_.*"),
                            Times.atLeast(1)
                    );
            proxyClient
                    .verify(
                            request()
                                    .withPath("/test_headers_.*"),
                            Times.exactly(2)
                    );
        } finally {
            httpClient.stop();
        }
    }

    @Test
    public void shouldClearRequests() throws Exception {
        // given
        HttpClient httpClient = new HttpClient();
        httpClient.getProxyConfiguration().getProxies().add(new HttpProxy("localhost", getProxyPort()));
        ProxyClient proxyClient = new ProxyClient("127.0.0.1", getProxyPort()).reset();
        try {
            httpClient.start();

            // when
            httpClient.newRequest("localhost", getServerPort())
                    .scheme(HttpScheme.HTTP.asString())
                    .method(HttpMethod.GET)
                    .path("/test_headers_and_body")
                    .send();
            httpClient.newRequest("localhost", getServerPort())
                    .scheme(HttpScheme.HTTP.asString())
                    .method(HttpMethod.GET)
                    .path("/test_headers_only")
                    .send();
            proxyClient.clear(
                    request()
                            .withMethod("GET")
                            .withPath("/test_headers_and_body")
            );

            // then
            proxyClient
                    .verify(
                            request()
                                    .withMethod("GET")
                                    .withPath("/test_headers_and_body"),
                            Times.exactly(0)
                    );
            proxyClient
                    .verify(
                            request()
                                    .withPath("/test_headers_.*"),
                            Times.atLeast(1)
                    );
            proxyClient
                    .verify(
                            request()
                                    .withPath("/test_headers_.*"),
                            Times.exactly(1)
                    );
        } finally {
            httpClient.stop();
        }
    }

    @Test
    public void shouldResetRequests() throws Exception {
        // given
        HttpClient httpClient = new HttpClient();
        httpClient.getProxyConfiguration().getProxies().add(new HttpProxy("localhost", getProxyPort()));
        ProxyClient proxyClient = new ProxyClient("127.0.0.1", getProxyPort()).reset();
        try {
            httpClient.start();

            // when
            httpClient.newRequest("localhost", getServerPort())
                    .scheme(HttpScheme.HTTP.asString())
                    .method(HttpMethod.GET)
                    .path("/test_headers_and_body")
                    .send();
            httpClient.newRequest("localhost", getServerPort())
                    .scheme(HttpScheme.HTTP.asString())
                    .method(HttpMethod.GET)
                    .path("/test_headers_only")
                    .send();
            proxyClient.reset();

            // then
            proxyClient
                    .verify(
                            request()
                                    .withMethod("GET")
                                    .withPath("/test_headers_and_body"),
                            Times.exactly(0)
                    );
            proxyClient
                    .verify(
                            request()
                                    .withPath("/test_headers_.*"),
                            Times.atLeast(0)
                    );
            proxyClient
                    .verify(
                            request()
                                    .withPath("/test_headers_.*"),
                            Times.exactly(0)
                    );
        } finally {
            httpClient.stop();
        }
    }
}
