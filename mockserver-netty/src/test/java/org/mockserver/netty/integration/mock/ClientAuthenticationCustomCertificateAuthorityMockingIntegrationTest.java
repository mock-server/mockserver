package org.mockserver.netty.integration.mock;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockserver.cli.Main;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.tls.KeyStoreFactory;
import org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.echo.tls.NonMatchingX509KeyManager.invalidClientSSLContext;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ClientAuthenticationCustomCertificateAuthorityMockingIntegrationTest extends AbstractMockingIntegrationTestBase {

    private static final int severHttpPort = PortFactory.findFreePort();
    private static String originalCertificateAuthorityCertificate;
    private static String originalCertificateAuthorityPrivateKey;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalCertificateAuthorityCertificate = certificateAuthorityCertificate();
        originalCertificateAuthorityPrivateKey = certificateAuthorityPrivateKey();

        // set new certificate authority values
        certificateAuthorityCertificate("org/mockserver/netty/integration/tls/ca.pem");
        certificateAuthorityPrivateKey("org/mockserver/netty/integration/tls/ca-key-pkcs8.pem");
        tlsMutualAuthenticationRequired(true);

        forwardProxyTLSX509CertificatesTrustManager("JVM");
        Main.main("-serverPort", "" + severHttpPort);

        mockServerClient = new MockServerClient("localhost", severHttpPort).withSecure(true);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);

        // set back to original value
        certificateAuthorityCertificate(originalCertificateAuthorityCertificate);
        certificateAuthorityPrivateKey(originalCertificateAuthorityPrivateKey);
        tlsMutualAuthenticationRequired(false);
    }

    @Override
    public int getServerPort() {
        return severHttpPort;
    }

    @Test
    public void shouldReturnUpdateInHttp() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withBody("some_body_response")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(426)
                .withReasonPhrase("Upgrade Required")
                .withHeader("Upgrade", "TLS/1.2, HTTP/1.1"),
            makeRequest(
                request()
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseInHttpsNettyClient() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withBody("some_body_response")
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("some_path"))
                    .withMethod("POST"),
                headersToIgnore)
        );
    }

    @Test
    @Ignore("TODO (jamesdbloom) determine why this test fails in build server but not else where?")
    public void shouldReturnResponseInHttpsApacheClient() throws Exception {
        StatusLine statusLine = null;
        String responseBody = null;
        Level originalLevel = ConfigurationProperties.logLevel();
        try {
            ConfigurationProperties.logLevel("TRACE");
            // given
            mockServerClient
                .when(
                    request()
                        .withMethod("POST")
                        .withPath(calculatePath("some_path"))
                )
                .respond(
                    response()
                        .withStatusCode(200)
                        .withBody("some_body_response")
                );

            // when
            HttpClient httpClient = HttpClients.custom().setSSLContext(new KeyStoreFactory(new MockServerLogger()).sslContext()).build();
            HttpResponse response = httpClient.execute(new HttpPost(new URIBuilder()
                .setScheme("https")
                .setHost("localhost")
                .setPort(getServerPort())
                .setPath(calculatePath("some_path"))
                .build()));
            statusLine = response.getStatusLine();
            responseBody = new String(EntityUtils.toByteArray(response.getEntity()), StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            System.err.println("throwable = " + throwable);
            throwable.printStackTrace();
        } finally {
            ConfigurationProperties.logLevel(originalLevel.name());
        }

        // then
        assertThat(statusLine, is(OK_200.code()));
        assertThat(responseBody, is("some_body_response"));
    }

    @Test
    public void shouldFailToAuthenticateInHttpsApacheClient() {
        // given
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withBody("some_body_response")
            );

        // when
        try {
            HttpClient httpClient = HttpClients.custom().setSSLContext(invalidClientSSLContext()).build();
            httpClient.execute(new HttpPost(new URIBuilder()
                .setScheme("https")
                .setHost("localhost")
                .setPort(getServerPort())
                .setPath(calculatePath("some_path"))
                .build()));

            // then
            fail("SSLHandshakeException expected");
        } catch (Throwable throwable) {
            assertThat(throwable.getMessage(),
                anyOf(
                    is("Received fatal alert: certificate_unknown"),
                    is("readHandshakeRecord"),
                    is("Broken pipe")
                )
            );
        }
    }

    @Test
    public void shouldFailToAuthenticateInHttpApacheClient() throws Exception {
        // given
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withBody("some_body_response")
            );

        // when
        HttpClient httpClient = HttpClients.custom().setSSLContext(new KeyStoreFactory(new MockServerLogger()).sslContext()).build();
        HttpResponse response = httpClient.execute(new HttpPost(new URIBuilder()
            .setScheme("http")
            .setHost("localhost")
            .setPort(getServerPort())
            .setPath(calculatePath("some_path"))
            .build()));
        String responseBody = new String(EntityUtils.toByteArray(response.getEntity()), StandardCharsets.UTF_8);

        // then
        assertThat(response.getStatusLine().getStatusCode(), is(426));
        assertThat(response.containsHeader("Upgrade"), is(true));
        assertThat(responseBody, is(""));
    }

}
