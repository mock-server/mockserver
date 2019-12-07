package org.mockserver.integration.proxy;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.proxy.ProxyConfiguration;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.tls.KeyStoreFactory;
import org.mockserver.streams.IOStreamUtils;

import javax.net.ssl.SSLSocket;
import java.io.OutputStream;
import java.net.Socket;
import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.proxy.ProxyConfiguration.proxyConfiguration;
import static org.mockserver.socket.tls.SSLSocketFactory.sslSocketFactory;
import static org.mockserver.test.Assert.assertContains;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientSecureProxyIntegrationTest {

    public abstract int getProxyPort();

    public abstract int getServerSecurePort();

    public abstract MockServerClient getProxyClient();

    private static EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(0, new Scheduler.SchedulerThreadFactory(AbstractClientSecureProxyIntegrationTest.class.getSimpleName() + "-eventLoop"));

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Test
    public void shouldConnectToSecurePort() throws Exception {
        try (Socket socket = new Socket("localhost", getProxyPort())) {
            // given
            OutputStream output = socket.getOutputStream();

            // when
            output.write(("" +
                "CONNECT localhost:443 HTTP/1.1\r\n" +
                "Host: localhost:" + getServerSecurePort() + "\r\n" +
                "\r\n"
            ).getBytes(UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 200 OK");
        }
    }

    @Test
    public void shouldNotConnectToSecurePortRequiringAuthenticationWithDefaultRelm() throws Exception {
        String existingUsername = ConfigurationProperties.proxyAuthenticationUsername();
        String existingPassword = ConfigurationProperties.proxyAuthenticationPassword();
        try {
            String username = UUID.randomUUID().toString();
            String password = UUID.randomUUID().toString();
            ConfigurationProperties.proxyAuthenticationUsername(username);
            ConfigurationProperties.proxyAuthenticationPassword(password);

            try (Socket socket = new Socket("localhost", getProxyPort())) {
                // given
                OutputStream output = socket.getOutputStream();

                // when
                output.write(("" +
                    "CONNECT localhost:443 HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerSecurePort() + "\r\n" +
                    "\r\n"
                ).getBytes(UTF_8));
                output.flush();

                // then
                assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 407 Proxy Authentication Required\n" +
                    "proxy-authenticate: Basic realm=\"MockServer HTTP Proxy\", charset=\"UTF-8\"");
            }

        } finally {
            ConfigurationProperties.proxyAuthenticationUsername(existingUsername);
            ConfigurationProperties.proxyAuthenticationPassword(existingPassword);
        }
    }

    @Test
    public void shouldNotConnectToSecurePortRequiringAuthenticationWithCustomRelm() throws Exception {
        String existingUsername = ConfigurationProperties.proxyAuthenticationUsername();
        String existingPassword = ConfigurationProperties.proxyAuthenticationPassword();
        String existingRealm = ConfigurationProperties.proxyAuthenticationRealm();
        try {
            String username = UUID.randomUUID().toString();
            String password = UUID.randomUUID().toString();
            String realm = "Some other random \"realm\"";
            ConfigurationProperties.proxyAuthenticationUsername(username);
            ConfigurationProperties.proxyAuthenticationPassword(password);
            ConfigurationProperties.proxyAuthenticationRealm(realm);

            try (Socket socket = new Socket("localhost", getProxyPort())) {
                // given
                OutputStream output = socket.getOutputStream();

                // when
                output.write(("" +
                    "CONNECT localhost:443 HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerSecurePort() + "\r\n" +
                    "\r\n"
                ).getBytes(UTF_8));
                output.flush();

                // then
                assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 407 Proxy Authentication Required\n" +
                    "proxy-authenticate: Basic realm=\"Some other random \\\"realm\\\"\", charset=\"UTF-8\"");
            }

        } finally {
            ConfigurationProperties.proxyAuthenticationUsername(existingUsername);
            ConfigurationProperties.proxyAuthenticationPassword(existingPassword);
            ConfigurationProperties.proxyAuthenticationRealm(existingRealm);
        }
    }

    @Test
    public void shouldConnectToSecurePortWithAuthenticationHeader() throws Exception {
        String existingUsername = ConfigurationProperties.proxyAuthenticationUsername();
        String existingPassword = ConfigurationProperties.proxyAuthenticationPassword();
        String existingRealm = ConfigurationProperties.proxyAuthenticationRealm();
        try {
            String username = UUID.randomUUID().toString();
            String password = UUID.randomUUID().toString();
            String realm = "Some other random \"realm\"";
            ConfigurationProperties.proxyAuthenticationUsername(username);
            ConfigurationProperties.proxyAuthenticationPassword(password);
            ConfigurationProperties.proxyAuthenticationRealm(realm);

            try (Socket socket = new Socket("localhost", getProxyPort())) {
                // given
                OutputStream output = socket.getOutputStream();

                // when
                output.write(("" +
                    "CONNECT localhost:443 HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerSecurePort() + "\r\n" +
                    "Proxy-Authorization: Basic " + Base64.encodeBase64String((username + ":" + password).getBytes(UTF_8)) + "\r\n" +
                    "\r\n"
                ).getBytes(UTF_8));
                output.flush();

                // then
                assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 200 OK");
            }

        } finally {
            ConfigurationProperties.proxyAuthenticationUsername(existingUsername);
            ConfigurationProperties.proxyAuthenticationPassword(existingPassword);
            ConfigurationProperties.proxyAuthenticationRealm(existingRealm);
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
                "CONNECT localhost:443 HTTP/1.1\r\n" +
                "Host: localhost:" + getServerSecurePort() + "\r\n" +
                "\r\n"
            ).getBytes(UTF_8));
            output.flush();

            // - flush CONNECT response
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 200 OK");

            // Upgrade the socket to SSL
            try (SSLSocket sslSocket = sslSocketFactory().wrapSocket(socket)) {

                output = sslSocket.getOutputStream();

                // - send GET request for headers only
                output.write(("" +
                    "GET /test_headers_only HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerSecurePort() + "\r\n" +
                    "X-Test: test_headers_only\r\n" +
                    "Connection: keep-alive\r\n" +
                    "\r\n"
                ).getBytes(UTF_8));
                output.flush();

                // then
                assertContains(IOStreamUtils.readInputStreamToString(sslSocket), "X-Test: test_headers_only");

                // - send GET request for headers and body
                output.write(("" +
                    "GET /test_headers_and_body HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerSecurePort() + "\r\n" +
                    "Content-Length: " + "an_example_body".getBytes(UTF_8).length + "\r\n" +
                    "X-Test: test_headers_and_body\r\n" +
                    "\r\n" +
                    "an_example_body"
                ).getBytes(UTF_8));
                output.flush();

                // then
                String response = IOStreamUtils.readInputStreamToString(sslSocket);
                assertContains(response, "X-Test: test_headers_and_body");
                assertContains(response, "an_example_body");

                // and
                getProxyClient().verify(
                    request()
                        .withMethod("GET")
                        .withPath("/test_headers_and_body")
                        .withBody("an_example_body"),
                    exactly(1)
                );
            }
        }
    }

    @Test
    public void shouldForwardRequestsToSecurePortUsingHttpClientViaHTTP_CONNECT() throws Exception {
        // given
        HttpClient httpClient = HttpClients
            .custom()
            .setSSLSocketFactory(new SSLConnectionSocketFactory(new KeyStoreFactory(new MockServerLogger()).sslContext(), NoopHostnameVerifier.INSTANCE))
            .setRoutePlanner(
                new DefaultProxyRoutePlanner(
                    new HttpHost(
                        System.getProperty("http.proxyHost"),
                        Integer.parseInt(System.getProperty("http.proxyPort"))
                    )
                )
            ).build();

        // when
        HttpPost request = new HttpPost(
            new URIBuilder()
                .setScheme("https")
                .setHost("localhost")
                .setPort(getServerSecurePort())
                .setPath("/test_headers_and_body")
                .build()
        );
        request.setEntity(new StringEntity("an_example_body"));
        HttpResponse response = httpClient.execute(request);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), response.getStatusLine().getStatusCode());
        assertEquals("an_example_body", new String(EntityUtils.toByteArray(response.getEntity()), UTF_8));

        // and
        getProxyClient().verify(
            request()
                .withPath("/test_headers_and_body")
                .withBody("an_example_body"),
            exactly(1)
        );
    }

    @Test
    public void shouldForwardRequestsToSecurePortAndUnknownPath() throws Exception {
        try (Socket socket = new Socket("localhost", getProxyPort())) {
            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send CONNECT request
            output.write(("" +
                "CONNECT localhost:443 HTTP/1.1\r\n" +
                "Host: localhost:" + getServerSecurePort() + "\r\n" +
                "\r\n"
            ).getBytes(UTF_8));
            output.flush();

            // - flush CONNECT response
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 200 OK");

            // Upgrade the socket to SSL
            try (SSLSocket sslSocket = sslSocketFactory().wrapSocket(socket)) {

                // - send GET request
                output = sslSocket.getOutputStream();
                output.write(("" +
                    "GET /not_found HTTP/1.1\r\n" +
                    "Host: localhost:" + getServerSecurePort() + "\r\n" +
                    "\r\n"
                ).getBytes(UTF_8));
                output.flush();

                // then
                assertContains(IOStreamUtils.readInputStreamToString(sslSocket), "HTTP/1.1 404 Not Found");

                // and
                getProxyClient().verify(
                    request()
                        .withMethod("GET")
                        .withPath("/not_found"),
                    exactly(1)
                );
            }
        }
    }

    @Test
    public void shouldPreventUnauthenticatedConnectRequestWhenClientConfiguredWithProxyConfiguration() throws Exception {
        String existingUsername = ConfigurationProperties.proxyAuthenticationUsername();
        String existingPassword = ConfigurationProperties.proxyAuthenticationPassword();
        try {
            String username = UUID.randomUUID().toString();
            String password = UUID.randomUUID().toString();
            ConfigurationProperties.proxyAuthenticationUsername(username);
            ConfigurationProperties.proxyAuthenticationPassword(password);

            try {
                new NettyHttpClient(
                    new MockServerLogger(),
                    clientEventLoopGroup,
                    proxyConfiguration(
                        ProxyConfiguration.Type.HTTPS,
                        "localhost:" + getProxyPort()
                    ))
                    .sendRequest(
                        request()
                            .withPath("/target")
                            .withHeader(HOST.toString(), "localhost:" + getServerSecurePort())
                    )
                    .get(10, SECONDS);
                fail();
            } catch (Throwable throwable) {
                assertThat(throwable.getCause().getMessage(), containsString("407 Proxy Authentication Required"));

            }

        } finally {
            ConfigurationProperties.proxyAuthenticationUsername(existingUsername);
            ConfigurationProperties.proxyAuthenticationPassword(existingPassword);
        }
    }

    @Test
    public void shouldAuthenticateConnectRequestWhenClientConfiguredWithProxyConfiguration() throws Exception {
        String existingUsername = ConfigurationProperties.proxyAuthenticationUsername();
        String existingPassword = ConfigurationProperties.proxyAuthenticationPassword();
        try {
            String username = UUID.randomUUID().toString();
            String password = UUID.randomUUID().toString();
            ConfigurationProperties.proxyAuthenticationUsername(username);
            ConfigurationProperties.proxyAuthenticationPassword(password);

            org.mockserver.model.HttpResponse httpResponse = new NettyHttpClient(
                new MockServerLogger(),
                clientEventLoopGroup,
                proxyConfiguration(
                    ProxyConfiguration.Type.HTTPS,
                    "localhost:" + getProxyPort(),
                    username,
                    password
                ))
                .sendRequest(
                    request()
                        .withPath("/target")
                        .withSecure(true)
                        .withHeader(HOST.toString(), "localhost:" + getServerSecurePort())
                )
                .get(10, SECONDS);

            assertThat(httpResponse.getStatusCode(), is(200));

        } finally {
            ConfigurationProperties.proxyAuthenticationUsername(existingUsername);
            ConfigurationProperties.proxyAuthenticationPassword(existingPassword);
        }
    }

    @Test
    public void shouldConnectInHTTPSAndForceRequestToHTTPS() throws Exception {
        org.mockserver.model.HttpResponse httpResponse = new NettyHttpClient(
            new MockServerLogger(),
            clientEventLoopGroup,
            proxyConfiguration(
                ProxyConfiguration.Type.HTTPS,
                "localhost:" + getProxyPort()
            ))
            .sendRequest(
                request()
                    .withPath("/target")
                    .withHeader(HOST.toString(), "localhost:" + getServerSecurePort())
            )
            .get(10, SECONDS);

        assertThat(httpResponse.getStatusCode(), is(200));
    }
}
