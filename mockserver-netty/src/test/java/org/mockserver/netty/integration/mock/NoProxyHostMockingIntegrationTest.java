package org.mockserver.netty.integration.mock;

import static junit.framework.TestCase.assertEquals;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;
import static org.mockserver.stop.Stop.stopQuietly;
import java.net.InetSocketAddress;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.netty.MockServer;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.proxyconfiguration.ProxyConfiguration.Type;
import org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase;

public class NoProxyHostMockingIntegrationTest extends AbstractMockingIntegrationTestBase {

    private static MockServer mockServer;
    private static MockServer proxy;

    @BeforeClass
    public static void startServer() {
        proxy = new MockServer();
        mockServer = new MockServer(Configuration.configuration()
            .noProxyHosts("localhost,127.0.0.1")
            .forwardHttpProxy(InetSocketAddress.createUnresolved("240.0.0.1", 1234)));

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(proxy);
        stopQuietly(mockServer);
        stopQuietly(mockServerClient);
    }

    public int getServerPort() {
        return mockServer.getLocalPort();
    }

    @Test
    public void localhost() {
        checkProxyNotUsed("localhost");
        checkProxyNotUsed("127.0.0.1");
        checkProxyNotUsed("view-localhost");
        checkProxyNotUsed("local*");
        checkProxyNotUsed("127.*");
    }

    public void checkProxyNotUsed(String noProxyList) {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                forward()
                    .withHost(noProxyList)
                    .withPort(insecureEchoServer.getPort())
            );

        // then
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
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("Host", "127.0.0.1:" + insecureEchoServer.getPort()),
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                getHeadersToRemove())
        );
    }
}
