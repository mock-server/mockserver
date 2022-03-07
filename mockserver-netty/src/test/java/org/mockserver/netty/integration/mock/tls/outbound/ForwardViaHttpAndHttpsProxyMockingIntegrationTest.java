package org.mockserver.netty.integration.mock.tls.outbound;

import com.google.common.collect.ImmutableList;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.netty.MockServer;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;

import static junit.framework.TestCase.assertEquals;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.configuration.ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ForwardViaHttpAndHttpsProxyMockingIntegrationTest extends AbstractForwardViaHttpsProxyMockingIntegrationTest {

    private static MockServer mockServer;
    private static MockServer proxy;
    private static MockServerClient proxyClient;
    private static ForwardProxyTLSX509CertificatesTrustManager originalForwardProxyTLSX509CertificatesTrustManager;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalForwardProxyTLSX509CertificatesTrustManager = ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType();

        forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.ANY.name());

        proxy = new MockServer();
        mockServer = new MockServer(
            configuration(),
            ImmutableList.of(
                proxyConfiguration(ProxyConfiguration.Type.HTTPS, "127.0.0.1:" + proxy.getLocalPort()),
                proxyConfiguration(ProxyConfiguration.Type.HTTP, "127.0.0.1:" + proxy.getLocalPort())
            )
        );

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort(), servletContext);
        proxyClient = new MockServerClient("localhost", proxy.getLocalPort(), "");
    }

    @Before
    public void clearProxy() {
        proxyClient.reset();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(proxy);
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
    public void shouldForwardRequestInHTTP() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                forward()
                    .withHost("127.0.0.1")
                    .withPort(insecureEchoServer.getPort())
            );

        // then
        HttpRequest httpRequest = request()
            .withPath(calculatePath("echo"))
            .withMethod("POST")
            .withHeaders(
                header("Host", "127.0.0.1:" + insecureEchoServer.getPort()),
                header("x-test", "test_headers_and_body")
            )
            .withBody("an_example_body_http");
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            makeRequest(
                httpRequest,
                HEADERS_TO_IGNORE)
        );
        proxyClient.verify(httpRequest.withSecure(false));
    }

    @Test
    public void shouldForwardRequestInHTTPS() {
        // when
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

        // then
        HttpRequest httpRequest = request()
            .withSecure(true)
            .withPath(calculatePath("echo"))
            .withMethod("POST")
            .withHeaders(
                header("Host", "127.0.0.1:" + secureEchoServer.getPort()),
                header("x-test", "test_headers_and_body")
            )
            .withBody("an_example_body_http");
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            makeRequest(
                httpRequest,
                HEADERS_TO_IGNORE)
        );
        proxyClient.verify(httpRequest.withSecure(true));
    }

}
