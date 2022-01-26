package org.mockserver.netty.integration.proxy.http;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.netty.MockServer;
import org.mockserver.testing.integration.proxy.AbstractProxyIntegrationTest;
import org.mockserver.uuid.UUIDService;

import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.handler.codec.http.HttpHeaderNames.PROXY_AUTHENTICATE;
import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.testing.closurecallback.ViaWebSocket.viaWebSocket;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class NettyHttpProxyIntegrationTest extends AbstractProxyIntegrationTest {

    private static final String AUTH_TUNNELING_DISABLED_SCHEMES_SYSTEM_PROPERTY = "jdk.http.auth.tunneling.disabledSchemes";
    private static final String AUTH_PROXYING_DISABLED_SCHEMES_SYSTEM_PROPERTY = "jdk.http.auth.proxying.disabledSchemes";
    private static int mockServerPort;
    private static EchoServer echoServer;
    private static MockServerClient mockServerClient;
    private static String originalTunnelingAuthDisabledSchemes;
    private static String originalProxyAuthDisabledSchemes;

    @BeforeClass
    public static void setupFixture() {
        servletContext = "";

        // see: https://www.oracle.com/java/technologies/javase/8u111-relnotes.html
        originalTunnelingAuthDisabledSchemes = System.getProperty(AUTH_TUNNELING_DISABLED_SCHEMES_SYSTEM_PROPERTY);
        originalProxyAuthDisabledSchemes = System.getProperty(AUTH_PROXYING_DISABLED_SCHEMES_SYSTEM_PROPERTY);
        System.setProperty(AUTH_TUNNELING_DISABLED_SCHEMES_SYSTEM_PROPERTY, "");
        System.setProperty(AUTH_PROXYING_DISABLED_SCHEMES_SYSTEM_PROPERTY, "");
        mockServerPort = new MockServer().getLocalPort();
        mockServerClient = new MockServerClient("localhost", mockServerPort);

        echoServer = new EchoServer(false);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(echoServer);
        stopQuietly(mockServerClient);

        if (isBlank(originalTunnelingAuthDisabledSchemes)) {
            System.clearProperty(AUTH_TUNNELING_DISABLED_SCHEMES_SYSTEM_PROPERTY);
        } else {
            System.setProperty(AUTH_TUNNELING_DISABLED_SCHEMES_SYSTEM_PROPERTY, originalTunnelingAuthDisabledSchemes);
        }
        if (isBlank(originalProxyAuthDisabledSchemes)) {
            System.clearProperty(AUTH_PROXYING_DISABLED_SCHEMES_SYSTEM_PROPERTY);
        } else {
            System.setProperty(AUTH_PROXYING_DISABLED_SCHEMES_SYSTEM_PROPERTY, originalProxyAuthDisabledSchemes);
        }
    }

    @Before
    public void resetProxy() {
        mockServerClient.reset();
    }

    @Override
    public int getProxyPort() {
        return mockServerPort;
    }

    @Override
    public int getSecureProxyPort() {
        return mockServerPort;
    }

    @Override
    public MockServerClient getMockServerClient() {
        return mockServerClient;
    }

    @Override
    public int getServerPort() {
        return echoServer.getPort();
    }

    @Test
    public void shouldForwardRequestsAndFixContentTypeViaWebSocket() throws Exception {
        viaWebSocket(() -> {
            // given
            getMockServerClient()
                .when(
                    request()
                        .withHeader("Content-Type", "application/encrypted;charset=UTF-8")
                )
                .forward(
                    httpRequest ->
                        httpRequest
                            .replaceHeader(header("Content-Type", "application/encrypted"))
                            .withBody(binary(httpRequest.getBodyAsRawBytes()))
                );

            // and
            HttpClient httpClient = createHttpClient();
            byte[] hexBytes = RandomUtils.nextBytes(150);
            String hexString = Hex.encodeHexString(hexBytes).toUpperCase();

            // when
            HttpPost request = new HttpPost(
                new URIBuilder()
                    .setScheme("http")
                    .setHost("127.0.0.1")
                    .setPort(getServerPort())
                    .setPath(addContextToPath("test_headers_and_body"))
                    .build()
            );
            request.setEntity(new ByteArrayEntity(hexBytes));
            request.setHeader("Content-Type", "application/encrypted;charset=utf-8");
            HttpResponse response = httpClient.execute(request);

            // then
            assertEquals(HttpStatusCode.OK_200.code(), response.getStatusLine().getStatusCode());
            assertEquals(hexString.toUpperCase(), Hex.encodeHexString(EntityUtils.toByteArray(response.getEntity())).toUpperCase());

            // and
            getMockServerClient().verify(
                request()
                    .withMethod("POST")
                    .withPath("/test_headers_and_body")
                    .withBody(hexBytes),
                exactly(1)
            );
        });
    }

    @Test
    public void shouldForwardRequestsAndFixContentTypeViaLocalJVM() throws Exception {
        // given
        getMockServerClient()
            .when(
                request()
                    .withHeader("Content-Type", "application/encrypted;charset=UTF-8")
            )
            .forward(
                httpRequest ->
                    httpRequest
                        .replaceHeader(header("Content-Type", "application/encrypted"))
                        .withBody(binary(httpRequest.getBodyAsRawBytes()))
            );

        // and
        HttpClient httpClient = createHttpClient();
        byte[] hexBytes = RandomUtils.nextBytes(150);
        String hexString = Hex.encodeHexString(hexBytes).toUpperCase();

        // when
        HttpPost request = new HttpPost(
            new URIBuilder()
                .setScheme("http")
                .setHost("127.0.0.1")
                .setPort(getServerPort())
                .setPath(addContextToPath("test_headers_and_body"))
                .build()
        );
        request.setEntity(new ByteArrayEntity(hexBytes));
        request.setHeader("Content-Type", "application/encrypted;charset=utf-8");
        HttpResponse response = httpClient.execute(request);

        // then
        assertEquals(HttpStatusCode.OK_200.code(), response.getStatusLine().getStatusCode());
        assertEquals(hexString.toUpperCase(), Hex.encodeHexString(EntityUtils.toByteArray(response.getEntity())).toUpperCase());

        // and
        getMockServerClient().verify(
            request()
                .withMethod("POST")
                .withPath("/test_headers_and_body")
                .withBody(hexBytes),
            exactly(1)
        );
    }

    @Test
    public void shouldNotCallAuthenticatorWhenNoProxyCredentialsConfigured() throws Exception {
        String existingUsername = ConfigurationProperties.proxyAuthenticationUsername();
        String existingPassword = ConfigurationProperties.proxyAuthenticationPassword();
        try {
            // given
            ConfigurationProperties.proxyAuthenticationUsername("");
            ConfigurationProperties.proxyAuthenticationPassword("");
            AtomicInteger proaxyAuthenticatorCounter = new AtomicInteger(0);

            // when
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    proaxyAuthenticatorCounter.incrementAndGet();
                    if (getRequestorType() == RequestorType.PROXY) {
                        return new PasswordAuthentication(UUIDService.getUUID(), UUIDService.getUUID().toCharArray());
                    }
                    return null;
                }
            });
            URL url = new URL("http://localhost:" + echoServer.getPort() + "/somePath");
            HttpURLConnection con = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", mockServerPort)));

            // then
            assertThat(con.getResponseCode(), equalTo(200));
            assertThat(proaxyAuthenticatorCounter.get(), equalTo(0));
        } finally {
            ConfigurationProperties.proxyAuthenticationUsername(existingUsername);
            ConfigurationProperties.proxyAuthenticationPassword(existingPassword);
        }
    }

    @Test
    public void shouldAllowAuthenticatedProxiedRequestWithValidCredentialsForValidTarget() throws Exception {
        String existingUsername = ConfigurationProperties.proxyAuthenticationUsername();
        String existingPassword = ConfigurationProperties.proxyAuthenticationPassword();
        try {
            // given
            String username = UUIDService.getUUID();
            String password = UUIDService.getUUID();
            ConfigurationProperties.proxyAuthenticationUsername(username);
            ConfigurationProperties.proxyAuthenticationPassword(password);
            AtomicInteger proaxyAuthenticatorCounter = new AtomicInteger(0);

            // when
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() == RequestorType.PROXY) {
                        proaxyAuthenticatorCounter.incrementAndGet();
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                    return null;
                }
            });
            URL url = new URL("http://localhost:" + echoServer.getPort() + "/somePath");
            HttpURLConnection con = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", mockServerPort)));

            // then
            assertThat(con.getResponseCode(), equalTo(200));
            assertThat(proaxyAuthenticatorCounter.get(), equalTo(1));
        } finally {
            ConfigurationProperties.proxyAuthenticationUsername(existingUsername);
            ConfigurationProperties.proxyAuthenticationPassword(existingPassword);
        }
    }

    @Test
    public void shouldAllowAuthenticatedProxiedRequestWithValidCredentialsForRecursiveMockServer() throws Exception {
        String existingUsername = ConfigurationProperties.proxyAuthenticationUsername();
        String existingPassword = ConfigurationProperties.proxyAuthenticationPassword();
        try {
            // given
            String username = UUIDService.getUUID();
            String password = UUIDService.getUUID();
            ConfigurationProperties.proxyAuthenticationUsername(username);
            ConfigurationProperties.proxyAuthenticationPassword(password);

            // when
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() == RequestorType.PROXY) {
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                    return null;
                }
            });
            URL url = new URL("http://localhost:" + mockServerPort + "/somePath");
            HttpURLConnection con = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", mockServerPort)));

            // then
            assertThat(con.getResponseCode(), equalTo(404));
        } finally {
            ConfigurationProperties.proxyAuthenticationUsername(existingUsername);
            ConfigurationProperties.proxyAuthenticationPassword(existingPassword);
        }
    }

    @Test
    public void shouldPreventUnauthenticatedProxiedRequestDueToInvalidPassword() throws Exception {
        String existingUsername = ConfigurationProperties.proxyAuthenticationUsername();
        String existingPassword = ConfigurationProperties.proxyAuthenticationPassword();
        try {
            // given
            String username = UUIDService.getUUID();
            String password = UUIDService.getUUID();
            ConfigurationProperties.proxyAuthenticationUsername(username);
            ConfigurationProperties.proxyAuthenticationPassword(password);

            // when
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() == RequestorType.PROXY) {
                        String wrongPassword = UUIDService.getUUID();
                        return new PasswordAuthentication(username, wrongPassword.toCharArray());
                    }
                    return null;
                }
            });
            URL url = new URL("http://localhost:" + echoServer.getPort() + "/somePath");
            HttpURLConnection con = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", mockServerPort)));

            // then
            assertThat(con.getHeaderField(PROXY_AUTHENTICATE.toString()), equalTo("Basic realm=\"MockServer HTTP Proxy\", charset=\"UTF-8\""));
            assertThat(con.getResponseCode(), equalTo(407));
        } finally {
            ConfigurationProperties.proxyAuthenticationUsername(existingUsername);
            ConfigurationProperties.proxyAuthenticationPassword(existingPassword);
        }
    }

    @Test
    public void shouldPreventUnauthenticatedProxiedRequestDueToNoAuthenticator() throws Exception {
        String existingUsername = ConfigurationProperties.proxyAuthenticationUsername();
        String existingPassword = ConfigurationProperties.proxyAuthenticationPassword();
        try {
            // given
            String username = UUIDService.getUUID();
            String password = UUIDService.getUUID();
            ConfigurationProperties.proxyAuthenticationUsername(username);
            ConfigurationProperties.proxyAuthenticationPassword(password);

            // when
            URL url = new URL("http://localhost:" + echoServer.getPort() + "/somePath");
            HttpURLConnection con = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", mockServerPort)));

            // then
            assertThat(con.getHeaderField(PROXY_AUTHENTICATE.toString()), equalTo("Basic realm=\"MockServer HTTP Proxy\", charset=\"UTF-8\""));
            assertThat(con.getResponseCode(), equalTo(407));
        } finally {
            ConfigurationProperties.proxyAuthenticationUsername(existingUsername);
            ConfigurationProperties.proxyAuthenticationPassword(existingPassword);
        }
    }

}
