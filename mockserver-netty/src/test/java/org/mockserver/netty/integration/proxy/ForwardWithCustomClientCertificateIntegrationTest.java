package org.mockserver.netty.integration.proxy;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.netty.MockServer;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.mockserver.socket.tls.jdk.X509Generator;
import org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase;

import javax.net.ssl.SSLException;

import static org.junit.Assert.assertEquals;
import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ForwardWithCustomClientCertificateIntegrationTest extends AbstractMockingIntegrationTestBase {

    private static MockServer mockServer;
    private static EchoServer trustNoneTLSEchoServer;
    private static EchoServer trustCustomTLSEchoServer;
    private static ForwardProxyTLSX509CertificatesTrustManager originalForwardProxyTLSX509CertificatesTrustManager;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalForwardProxyTLSX509CertificatesTrustManager = ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType();

        forwardProxyPrivateKey("org/mockserver/netty/integration/tls/leaf-key-pkcs8.pem");
        forwardProxyCertificateChain("org/mockserver/netty/integration/tls/leaf-cert-chain.pem");

        mockServer = new MockServer();

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort(), servletContext);
    }

    @BeforeClass
    public static void startTrustEchoServers() throws SSLException {
        if (trustCustomTLSEchoServer == null) {
            trustCustomTLSEchoServer = new EchoServer(SslContextBuilder
                .forServer(
                    X509Generator.privateKeyFromPEMFile("org/mockserver/netty/integration/tls/leaf-key-pkcs8.pem"),
                    X509Generator.x509FromPEMFile("org/mockserver/netty/integration/tls/leaf-cert.pem"),
                    X509Generator.x509FromPEMFile("org/mockserver/netty/integration/tls/ca.pem")
                )
                .trustManager(
                    X509Generator.x509FromPEMFile("org/mockserver/netty/integration/tls/leaf-cert.pem"),
                    X509Generator.x509FromPEMFile("org/mockserver/netty/integration/tls/ca.pem")
                )
                .clientAuth(ClientAuth.REQUIRE)
                .build());
        }
        if (trustNoneTLSEchoServer == null) {
            trustNoneTLSEchoServer = new EchoServer(SslContextBuilder
                .forServer(
                    X509Generator.privateKeyFromPEMFile("org/mockserver/netty/integration/tls/trustnoneechoserver/leaf-key-pkcs8.pem"),
                    X509Generator.x509FromPEMFile("org/mockserver/netty/integration/tls/trustnoneechoserver/leaf-cert.pem"),
                    X509Generator.x509FromPEMFile("org/mockserver/netty/integration/tls/trustnoneechoserver/ca.pem")
                )
                .trustManager(
                    X509Generator.x509FromPEMFile("org/mockserver/netty/integration/tls/trustnoneechoserver/leaf-cert.pem"),
                    X509Generator.x509FromPEMFile("org/mockserver/netty/integration/tls/trustnoneechoserver/ca.pem")
                )
                .clientAuth(ClientAuth.REQUIRE)
                .build());
        }
    }

    @AfterClass
    public static void stopTrustEchoServers() {
        if (trustCustomTLSEchoServer != null) {
            trustCustomTLSEchoServer.stop();
        }
        if (trustNoneTLSEchoServer != null) {
            trustNoneTLSEchoServer.stop();
        }
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServer);
        stopQuietly(mockServerClient);

        // set back to original value
        forwardProxyTLSX509CertificatesTrustManagerType(originalForwardProxyTLSX509CertificatesTrustManager.name());
    }

    @Override
    public int getServerPort() {
        return mockServer.getLocalPort();
    }

    @Test
    public void shouldForwardWithCustomClientAuthenticationCertificate() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("trustNone"))
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(trustNoneTLSEchoServer.getPort())
                    .withScheme(HttpForward.Scheme.HTTPS)
            );

        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("trustCustom"))
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(trustCustomTLSEchoServer.getPort())
                    .withScheme(HttpForward.Scheme.HTTPS)
            );

        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(secureEchoServer.getPort())
                    .withScheme(HttpForward.Scheme.HTTPS)
            );

        // then - invalid certificate returns 404
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.NOT_FOUND_404.code())
                .withReasonPhrase(HttpStatusCode.NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("trustNone"))
                    .withMethod("POST")
                    .withHeaders(
                        header("Host", "127.0.0.1:" + trustNoneTLSEchoServer.getPort()),
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore)
        );

        // then - trusted certificate returns response
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("trustCustom"))
                    .withMethod("POST")
                    .withHeaders(
                        header("Host", "127.0.0.1:" + secureEchoServer.getPort()),
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore)
        );

        // then - valid certificate returns response
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("Host", "127.0.0.1:" + secureEchoServer.getPort()),
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore)
        );
    }

}
