package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.mockserver.testing.integration.callback.PrecannedTestExpectationForwardCallbackRequest;
import org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase;
import org.mockserver.netty.MockServer;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.HttpTemplate;
import org.mockserver.proxyconfiguration.ProxyConfiguration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManager;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ForwardViaHttpsProxyWithAnyTrustManagerMockingIntegrationTest extends AbstractForwardViaHttpsProxyMockingIntegrationTest {

    private static MockServer mockServer;
    private static MockServer proxy;
    private static ForwardProxyTLSX509CertificatesTrustManager originalForwardProxyTLSX509CertificatesTrustManager;

    @BeforeClass
    public static void startServer() {
        // save original value
        originalForwardProxyTLSX509CertificatesTrustManager = forwardProxyTLSX509CertificatesTrustManager();

        forwardProxyTLSX509CertificatesTrustManager(ForwardProxyTLSX509CertificatesTrustManager.ANY.name());

        proxy = new MockServer();
        mockServer = new MockServer(proxyConfiguration(ProxyConfiguration.Type.HTTPS, "127.0.0.1:" + proxy.getLocalPort()));

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort(), servletContext);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(proxy);
        stopQuietly(mockServer);
        stopQuietly(mockServerClient);

        // set back to original value
        forwardProxyTLSX509CertificatesTrustManager(originalForwardProxyTLSX509CertificatesTrustManager.name());
    }

    @Override
    public int getServerPort() {
        return mockServer.getLocalPort();
    }

}
