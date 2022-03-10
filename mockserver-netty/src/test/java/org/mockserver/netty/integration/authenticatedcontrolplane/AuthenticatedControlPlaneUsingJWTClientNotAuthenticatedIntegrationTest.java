package org.mockserver.netty.integration.authenticatedcontrolplane;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.jwt.JWKGenerator;
import org.mockserver.authentication.jwt.JWTGenerator;
import org.mockserver.cli.Main;
import org.mockserver.client.MockServerClient;
import org.mockserver.keys.AsymmetricKeyGenerator;
import org.mockserver.keys.AsymmetricKeyPair;
import org.mockserver.keys.AsymmetricKeyPairAlgorithm;
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
import org.mockserver.test.TempFileWriter;
import org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase;

import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationSequence.verificationSequence;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class AuthenticatedControlPlaneUsingJWTClientNotAuthenticatedIntegrationTest extends AbstractMockingIntegrationTestBase {

    private static final int severHttpPort = PortFactory.findFreePort();
    private static String originalControlPlaneJWTAuthenticationJWKSource;
    private static String originalControlPlaneJWTAuthenticationExpectedAudience;
    private static Map<String, String> originalControlPlaneJWTAuthenticationMatchingClaims;
    private static Set<String> originalControlPlaneJWTAuthenticationRequiredClaims;
    private static boolean originalControlPlaneJWTAuthenticationRequired;
    private static AsymmetricKeyPair asymmetricKeyPair;
    private final MockServerLogger mockServerLogger = new MockServerLogger();
    private final RequestDefinitionSerializer requestDefinitionSerializer = new RequestDefinitionSerializer(mockServerLogger);
    private final ExpectationSerializer expectationSerializer = new ExpectationSerializer(mockServerLogger);
    private final VerificationSerializer verificationSerializer = new VerificationSerializer(mockServerLogger);
    private final VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer(mockServerLogger);

    @BeforeClass
    public static void startServer() {
        // save original value
        originalControlPlaneJWTAuthenticationJWKSource = controlPlaneJWTAuthenticationJWKSource();
        originalControlPlaneJWTAuthenticationExpectedAudience = controlPlaneJWTAuthenticationExpectedAudience();
        originalControlPlaneJWTAuthenticationMatchingClaims = controlPlaneJWTAuthenticationMatchingClaims();
        originalControlPlaneJWTAuthenticationRequiredClaims = controlPlaneJWTAuthenticationRequiredClaims();
        originalControlPlaneJWTAuthenticationRequired = controlPlaneJWTAuthenticationRequired();

        // set jwt authorisation values
        asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        controlPlaneJWTAuthenticationJWKSource(jwkFile);
        controlPlaneJWTAuthenticationExpectedAudience("https://mock-server.com");
        controlPlaneJWTAuthenticationMatchingClaims(ImmutableMap.of("name", "John Doe", "admin", "true"));
        controlPlaneJWTAuthenticationRequiredClaims(ImmutableSet.of("name", "admin", "scope"));
        controlPlaneJWTAuthenticationRequired(true);

        Main.main("-serverPort", "" + severHttpPort);

        mockServerClient = new MockServerClient("localhost", severHttpPort);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);

        // set back to original value
        controlPlaneJWTAuthenticationJWKSource(originalControlPlaneJWTAuthenticationJWKSource);
        controlPlaneJWTAuthenticationExpectedAudience(originalControlPlaneJWTAuthenticationExpectedAudience);
        controlPlaneJWTAuthenticationMatchingClaims(originalControlPlaneJWTAuthenticationMatchingClaims);
        controlPlaneJWTAuthenticationRequiredClaims(originalControlPlaneJWTAuthenticationRequiredClaims);
        controlPlaneJWTAuthenticationRequired(originalControlPlaneJWTAuthenticationRequired);
    }

    @Before
    @Override
    public void resetServer() {
        // do nothing as control plane authentication fails
    }

    @Override
    public int getServerPort() {
        return severHttpPort;
    }

    @Override
    protected boolean isSecureControlPlane() {
        return true;
    }

    private Supplier<String> wrongKeyJWTSupplier() {
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().plus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "scope", "internal public",
                    "name", "John Doe",
                    "admin", "true",
                    "aud", "https://mock-server.com"
                )
            );
        return () -> jwt;
    }

    @Test
    public void shouldAuthenticateValidJWTForExpectationCreationViaJavaClient() {
        // given
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().plus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "scope", "internal public",
                    "name", "John Doe",
                    "admin", "true",
                    "aud", "https://mock-server.com"
                )
            );

        // when
        mockServerClient
                .withControlPlaneJWT(() -> jwt)
                .when(request())
                .respond(response().withBody("some_body"));

        // then no exception thrown
    }

    @Test
    public void shouldAuthenticateExpiredJWTForExpectationCreationViaJavaClient() {
        // given
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().minus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "scope", "internal public",
                    "name", "John Doe",
                    "admin", "true",
                    "aud", "https://mock-server.com"
                )
            );

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () ->
            mockServerClient
                .withControlPlaneJWT(() -> jwt)
                .when(request())
                .respond(response().withBody("some_body")));

        // then
        assertThat(authenticationException.getMessage(), equalTo("Unauthorized for control plane - Expired JWT"));
    }

    @Test
    public void shouldAuthenticateInvalidAudienceForExpectationCreationViaJavaClient() {
        // given
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().plus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "scope", "internal public",
                    "name", "John Doe",
                    "admin", "true",
                    "aud", "https://not-mock-server.com"
                )
            );

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () ->
            mockServerClient
                .withControlPlaneJWT(() -> jwt)
                .when(request())
                .respond(response().withBody("some_body")));

        // then
        assertThat(authenticationException.getMessage(), equalTo("Unauthorized for control plane - JWT audience rejected: [https://not-mock-server.com]"));
    }

    @Test
    public void shouldAuthenticateNonMatchingFirstClaimForExpectationCreationViaJavaClient() {
        // given
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().plus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "scope", "internal public",
                    "name", "John Smith",
                    "admin", "true",
                    "aud", "https://mock-server.com"
                )
            );

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () ->
            mockServerClient
                .withControlPlaneJWT(() -> jwt)
                .when(request())
                .respond(response().withBody("some_body")));

        // then
        assertThat(authenticationException.getMessage(), equalTo("Unauthorized for control plane - JWT name claim has value John Smith, must be John Doe"));
    }

    @Test
    public void shouldAuthenticateNonMatchingSecondClaimForExpectationCreationViaJavaClient() {
        // given
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().plus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "scope", "internal public",
                    "name", "John Doe",
                    "admin", "false",
                    "aud", "https://mock-server.com"
                )
            );

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () ->
            mockServerClient
                .withControlPlaneJWT(() -> jwt)
                .when(request())
                .respond(response().withBody("some_body")));

        // then
        assertThat(authenticationException.getMessage(), equalTo("Unauthorized for control plane - JWT admin claim has value false, must be true"));
    }

    @Test
    public void shouldAuthenticateMissingClaimForExpectationCreationViaJavaClient() {
        // given
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().plus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "name", "John Doe",
                    "aud", "https://mock-server.com"
                )
            );

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () ->
            mockServerClient
                .withControlPlaneJWT(() -> jwt)
                .when(request())
                .respond(response().withBody("some_body")));

        // then
        assertThat(authenticationException.getMessage(), equalTo("Unauthorized for control plane - JWT missing required claims: [admin, scope]"));
    }

    @Test
    public void shouldAuthenticateMissingJWTViaSupplierForExpectationCreationViaJavaClient() {
        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () ->
            mockServerClient
                .withControlPlaneJWT((Supplier<String>) null)
                .when(request())
                .respond(response().withBody("some_body")));

        // then
        assertThat(authenticationException.getMessage(), equalTo("Unauthorized for control plane - no authorization header found"));
    }

    @Test
    public void shouldAuthenticateMissingJWTViaStringForExpectationCreationViaJavaClient() {
        // when
        IllegalArgumentException authenticationException = assertThrows(IllegalArgumentException.class, () ->
            mockServerClient
                .withControlPlaneJWT((String) null)
                .when(request())
                .respond(response().withBody("some_body")));

        // then
        assertThat(authenticationException.getMessage(), equalTo("Control plane jwt supplier returned invalid JWT \"null\""));
    }

    @Test
    public void shouldAuthenticateExpectationCreationViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .withControlPlaneJWT(wrongKeyJWTSupplier())
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
                .withHeader(header(AUTHORIZATION.toString(), "Bearer " + wrongKeyJWTSupplier().get()))
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
                .withControlPlaneJWT(wrongKeyJWTSupplier())
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
                .withHeader(header(AUTHORIZATION.toString(), "Bearer " + wrongKeyJWTSupplier().get()))
                .withPath(addContextToPath("mockserver/reset"))
        );
    }

    @Test
    public void shouldAuthenticateClearViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .withControlPlaneJWT(wrongKeyJWTSupplier())
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
                .withHeader(header(AUTHORIZATION.toString(), "Bearer " + wrongKeyJWTSupplier().get()))
                .withPath(addContextToPath("mockserver/clear"))
        );
    }

    @Test
    public void shouldAuthenticateClearWithRequestViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .withControlPlaneJWT(wrongKeyJWTSupplier())
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
                .withHeader(header(AUTHORIZATION.toString(), "Bearer " + wrongKeyJWTSupplier().get()))
                .withPath(addContextToPath("mockserver/clear"))
                .withBody(requestDefinitionSerializer.serialize(request()))
        );
    }

    @Test
    public void shouldAuthenticateVerifyZeroInteractionsViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .withControlPlaneJWT(wrongKeyJWTSupplier())
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
                .withHeader(header(AUTHORIZATION.toString(), "Bearer " + wrongKeyJWTSupplier().get()))
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
                .withControlPlaneJWT(wrongKeyJWTSupplier())
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
                .withHeader(header(AUTHORIZATION.toString(), "Bearer " + wrongKeyJWTSupplier().get()))
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
                .withControlPlaneJWT(wrongKeyJWTSupplier())
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
                .withHeader(header(AUTHORIZATION.toString(), "Bearer " + wrongKeyJWTSupplier().get()))
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
                .withControlPlaneJWT(wrongKeyJWTSupplier())
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
                .withHeader(header(AUTHORIZATION.toString(), "Bearer " + wrongKeyJWTSupplier().get()))
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
        );
    }

    @Test
    public void shouldAuthenticateRetrieveRecordedRequestsAndResponsesViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .withControlPlaneJWT(wrongKeyJWTSupplier())
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
                .withHeader(header(AUTHORIZATION.toString(), "Bearer " + wrongKeyJWTSupplier().get()))
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.REQUEST_RESPONSES.name())
        );
    }

    @Test
    public void shouldAuthenticateRetrieveRecordedExpectationsViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .withControlPlaneJWT(wrongKeyJWTSupplier())
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
                .withHeader(header(AUTHORIZATION.toString(), "Bearer " + wrongKeyJWTSupplier().get()))
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
        );
    }

    @Test
    public void shouldAuthenticateRetrieveActiveExpectationsViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .withControlPlaneJWT(wrongKeyJWTSupplier())
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
                .withHeader(header(AUTHORIZATION.toString(), "Bearer " + wrongKeyJWTSupplier().get()))
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
        );
    }

    @Test
    public void shouldAuthenticateRetrieveLogMessagesViaJavaClient() {
        clientOperationIsAuthenticated(() ->
            mockServerClient
                .withControlPlaneJWT(wrongKeyJWTSupplier())
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
                .withHeader(header(AUTHORIZATION.toString(), "Bearer " + wrongKeyJWTSupplier().get()))
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.LOGS.name())
        );
    }

    private void clientOperationIsAuthenticated(ThrowingRunnable throwingRunnable) {
        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, throwingRunnable);

        // then
        assertThat(authenticationException.getMessage(), equalTo("Unauthorized for control plane - Signed JWT rejected: Another algorithm expected, or no matching key(s) found"));
    }

    private void httpAPIOperationIsAuthenticated(HttpRequest httpRequest) {
        // when
        HttpResponse httpResponse = makeRequest(httpRequest, Collections.emptyList());

        // then
        assertThat(httpResponse.getStatusCode(), equalTo(401));
        assertThat(httpResponse.getBodyAsString(), equalTo("Unauthorized for control plane - Signed JWT rejected: Another algorithm expected, or no matching key(s) found"));
    }

}
