package org.mockserver.netty.integration.mock.authenticatedcontrolplane;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.mockserver.cli.Main;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.client.MockServerClient;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RequestDefinition;
import org.mockserver.model.RetrieveType;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.RequestDefinitionSerializer;
import org.mockserver.serialization.VerificationSequenceSerializer;
import org.mockserver.serialization.VerificationSerializer;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase;

import javax.net.ssl.SSLException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Collections;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.socket.tls.PEMToFile.privateKeyFromPEMFile;
import static org.mockserver.socket.tls.PEMToFile.x509ChainFromPEMFile;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationSequence.verificationSequence;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class AuthenticatedControlPlaneUsingMTLSClientNotAuthenticatedIntegrationTest extends AbstractMockingIntegrationTestBase {

    private static final int severHttpPort = PortFactory.findFreePort();
    private static String originalControlPlaneTLSMutualAuthenticationCAChain;
    private static String originalControlPlanePrivateKeyPath;
    private static String originalControlPlaneX509CertificatePath;
    private static boolean originalControlPlaneTLSMutualAuthenticationRequired;
    private final MockServerLogger mockServerLogger = new MockServerLogger();
    private final RequestDefinitionSerializer requestDefinitionSerializer = new RequestDefinitionSerializer(mockServerLogger);
    private final ExpectationSerializer expectationSerializer = new ExpectationSerializer(mockServerLogger);
    private final VerificationSerializer verificationSerializer = new VerificationSerializer(mockServerLogger);
    private final VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer(mockServerLogger);

    @BeforeClass
    public static void startServer() {
        // save original value
        originalControlPlaneTLSMutualAuthenticationCAChain = controlPlaneTLSMutualAuthenticationCAChain();
        originalControlPlanePrivateKeyPath = controlPlanePrivateKeyPath();
        originalControlPlaneX509CertificatePath = controlPlaneX509CertificatePath();
        originalControlPlaneTLSMutualAuthenticationRequired = controlPlaneTLSMutualAuthenticationRequired();

        // set new certificate authority values
        controlPlaneTLSMutualAuthenticationCAChain("org/mockserver/netty/integration/tls/ca.pem");
        controlPlanePrivateKeyPath("org/mockserver/netty/integration/tls/leaf-key-pkcs8.pem");
        controlPlaneX509CertificatePath("org/mockserver/netty/integration/tls/leaf-cert.pem");
        controlPlaneTLSMutualAuthenticationRequired(true);

        Main.main("-serverPort", "" + severHttpPort);
        mockServerClient = new MockServerClient("localhost", severHttpPort).withSecure(true);
        mockServerClient.hasStarted();

        controlPlaneTLSMutualAuthenticationCAChain("org/mockserver/netty/integration/tls/separateca/ca.pem");
        controlPlanePrivateKeyPath("org/mockserver/netty/integration/tls/separateca/leaf-key-pkcs8.pem");
        controlPlaneX509CertificatePath("org/mockserver/netty/integration/tls/separateca/leaf-cert.pem");

        mockServerClient = new MockServerClient("localhost", severHttpPort).withSecure(true);
        MockServerLogger mockServerLogger = new MockServerLogger();
        NettySslContextFactory nettySslContextFactory = new NettySslContextFactory(configuration(), MOCK_SERVER_LOGGER);
        nettySslContextFactory.withClientSslContextBuilderFunction(
            sslContextBuilder -> {
                try {
                    RSAPrivateKey key = privateKeyFromPEMFile(controlPlanePrivateKeyPath());
                    X509Certificate[] keyCertChain = x509ChainFromPEMFile(controlPlaneX509CertificatePath()).toArray(new X509Certificate[0]);
                    X509Certificate[] trustCertCollection = nettySslContextFactory.trustCertificateChain(controlPlaneTLSMutualAuthenticationCAChain());
                    sslContextBuilder
                        .keyManager(
                            key,
                            keyCertChain
                        )
                        .trustManager(trustCertCollection);
                    return sslContextBuilder.build();
                } catch (SSLException e) {
                    throw new RuntimeException(e);
                }
            }
        );
        httpClient = new NettyHttpClient(configuration(), mockServerLogger, clientEventLoopGroup, null, false, nettySslContextFactory);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);

        // set back to original value
        controlPlaneTLSMutualAuthenticationCAChain(originalControlPlaneTLSMutualAuthenticationCAChain);
        controlPlanePrivateKeyPath(originalControlPlanePrivateKeyPath);
        controlPlaneX509CertificatePath(originalControlPlaneX509CertificatePath);
        controlPlaneTLSMutualAuthenticationRequired(originalControlPlaneTLSMutualAuthenticationRequired);
    }

    @Before
    @Override
    public void resetServer() {
        // do nothing a control authentication fails
    }

    @Override
    public int getServerPort() {
        return severHttpPort;
    }

    @Override
    protected boolean isSecureControlPlane() {
        return true;
    }

    @Test
    public void shouldAuthenticateExpectationCreationViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .when(request())
                .respond(response().withBody("some_body"))
        );
    }

    @Test
    public void shouldAuthenticateExpectationCreationViaHttp() {
        httpAPIOperationIsAuthenticated(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(addContextToPath("mockserver/expectation"))
                .withBody(expectationSerializer.serialize(
                    new Expectation(request("/some_path"), once(), TimeToLive.unlimited(), 0)
                        .thenRespond(response().withBody("some_body"))
                ))
        );
    }

    @Test
    public void shouldAuthenticateResetViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .reset()
        );
    }

    @Test
    public void shouldAuthenticateResetViaHttp() {
        httpAPIOperationIsAuthenticated(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(addContextToPath("mockserver/reset"))
        );
    }

    @Test
    public void shouldAuthenticateClearViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .clear((RequestDefinition) null)
        );
    }

    @Test
    public void shouldAuthenticateClearViaHttp() {
        httpAPIOperationIsAuthenticated(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(addContextToPath("mockserver/clear"))
        );
    }

    @Test
    public void shouldAuthenticateClearWithRequestViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .clear(request())
        );
    }

    @Test
    public void shouldAuthenticateClearWithRequestViaHttp() {
        httpAPIOperationIsAuthenticated(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(addContextToPath("mockserver/clear"))
                .withBody(requestDefinitionSerializer.serialize(request()))
        );
    }

    @Test
    public void shouldAuthenticateVerifyZeroInteractionsViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .verifyZeroInteractions()
        );
    }

    @Test
    public void shouldAuthenticateVerifyZeroInteractionsWithRequestViaHttp() {
        httpAPIOperationIsAuthenticated(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(addContextToPath("mockserver/clear"))
                .withBody(verificationSerializer.serialize(
                    verification()
                        .withRequest(request())
                        .withTimes(exactly(0))
                ))
        );
    }

    @Test
    public void shouldAuthenticateVerifyViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .verify(request())
        );
    }

    @Test
    public void shouldAuthenticateVerifyWithRequestViaHttp() {
        httpAPIOperationIsAuthenticated(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(addContextToPath("mockserver/clear"))
                .withBody(verificationSerializer.serialize(
                    verification()
                        .withRequest(request())
                ))
        );
    }

    @Test
    public void shouldAuthenticateVerifySequenceViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .verify(request(), request())
        );
    }

    @Test
    public void shouldAuthenticateVerifySequenceWithRequestViaHttp() {
        httpAPIOperationIsAuthenticated(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(addContextToPath("mockserver/clear"))
                .withBody(verificationSequenceSerializer.serialize(
                    verificationSequence()
                        .withRequests(request(), request())
                ))
        );
    }

    @Test
    public void shouldAuthenticateRetrieveRecordedRequestsViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .retrieveRecordedRequests(request())
        );
    }

    @Test
    public void shouldAuthenticateRetrieveRecordedRequestsWithRequestViaHttp() {
        httpAPIOperationIsAuthenticated(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
        );
    }

    @Test
    public void shouldAuthenticateRetrieveRecordedRequestsAndResponsesViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .retrieveRecordedRequestsAndResponses(request())
        );
    }

    @Test
    public void shouldAuthenticateRetrieveRecordedRequestsAndResponsesWithRequestViaHttp() {
        httpAPIOperationIsAuthenticated(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.REQUEST_RESPONSES.name())
        );
    }

    @Test
    public void shouldAuthenticateRetrieveRecordedExpectationsViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .retrieveRecordedExpectations(request())
        );
    }

    @Test
    public void shouldAuthenticateRetrieveRecordedExpectationsWithRequestViaHttp() {
        httpAPIOperationIsAuthenticated(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
        );
    }

    @Test
    public void shouldAuthenticateRetrieveActiveExpectationsViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .retrieveActiveExpectations(request())
        );
    }

    @Test
    public void shouldAuthenticateRetrieveActiveExpectationsWithRequestViaHttp() {
        httpAPIOperationIsAuthenticated(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
        );
    }

    @Test
    public void shouldAuthenticateRetrieveLogMessagesViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .retrieveLogMessages(request())
        );
    }

    @Test
    public void shouldAuthenticateRetrieveLogMessagesWithRequestViaHttp() {
        httpAPIOperationIsAuthenticated(
            request()
                .withMethod("PUT")
                .withSecure(isSecureControlPlane())
                .withHeader(HOST.toString(), "localhost:" + this.getServerPort())
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.LOGS.name())
        );
    }

    private void clientOperationIsAuthenticated(ThrowingRunnable throwingRunnable) {
        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, throwingRunnable);

        // then
        assertThat(authenticationException.getMessage(), equalTo("Unauthorized for control plane - control plane request failed authentication no client certificates can be validated by control plane CA"));
    }

    private void httpAPIOperationIsAuthenticated(HttpRequest httpRequest) {
        // when
        HttpResponse httpResponse = makeRequest(httpRequest, Collections.emptyList());

        // then
        assertThat(httpResponse.getStatusCode(), equalTo(401));
        assertThat(httpResponse.getBodyAsString(), equalTo("Unauthorized for control plane - control plane request failed authentication no client certificates can be validated by control plane CA"));
    }

}
