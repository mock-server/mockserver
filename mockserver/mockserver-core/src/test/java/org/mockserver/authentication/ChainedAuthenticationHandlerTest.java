package org.mockserver.authentication;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.mockserver.authentication.jwt.*;
import org.mockserver.authentication.mtls.MTLSAuthenticationHandler;
import org.mockserver.keys.AsymmetricKeyGenerator;
import org.mockserver.keys.AsymmetricKeyPair;
import org.mockserver.keys.AsymmetricKeyPairAlgorithm;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.JDKCertificateToMockServerX509Certificate;
import org.mockserver.model.HttpRequest;
import org.mockserver.socket.tls.PEMToFile;
import org.mockserver.test.TempFileWriter;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;
import static org.mockserver.model.HttpRequest.request;

public class ChainedAuthenticationHandlerTest {

    private static final MockServerLogger mockServerLogger = new MockServerLogger();

    private static AuthenticationHandler stub(AuthenticationResult result) {
        return new AuthenticationHandler() {
            @Override
            public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
                return result.isAuthenticated();
            }

            @Override
            public AuthenticationResult authenticate(HttpRequest request) {
                return result;
            }
        };
    }

    @Test
    public void authenticateSelectsFirstVerifiedPrincipalAndUnionsScopes() {
        // mTLS-style handler: authenticated but no principal
        AuthenticationHandler mtlsStyle = stub(AuthenticationResult.authenticated(null, "verified-mtls", java.util.Map.of(), java.util.Set.of("mesh")));
        // OIDC-style handler: authenticated with a verified principal
        AuthenticationHandler oidcStyle = stub(AuthenticationResult.authenticated("alice", "verified-oidc", java.util.Map.of("sub", "alice"), java.util.Set.of("read", "write")));

        AuthenticationHandler chained = new ChainedAuthenticationHandler(mtlsStyle, oidcStyle);
        AuthenticationResult result = chained.authenticate(request());

        assertThat(result.isAuthenticated(), equalTo(true));
        assertThat(result.getPrincipal(), equalTo("alice"));
        assertThat(result.getPrincipalSource(), equalTo("verified-oidc"));
        assertThat(result.getScopes(), org.hamcrest.Matchers.containsInAnyOrder("mesh", "read", "write"));
    }

    @Test
    public void authenticatePrefersTokenPrincipalOverEarlierClientCertificatePrincipal() {
        AuthenticationHandler mtls = stub(AuthenticationResult.authenticated("CN=client", MTLSAuthenticationHandler.PRINCIPAL_SOURCE, java.util.Map.of(), java.util.Set.of()));
        AuthenticationHandler oidc = stub(AuthenticationResult.authenticated("alice", "verified-oidc", java.util.Map.of("sub", "alice"), java.util.Set.of("read")));

        AuthenticationResult result = new ChainedAuthenticationHandler(mtls, oidc).authenticate(request());

        assertThat(result.getPrincipal(), equalTo("alice"));
        assertThat(result.getPrincipalSource(), equalTo("verified-oidc"));
    }

    @Test
    public void authenticateFallsBackToClientCertificatePrincipalWithoutTokenPrincipal() {
        AuthenticationHandler mtls = stub(AuthenticationResult.authenticated("CN=client", MTLSAuthenticationHandler.PRINCIPAL_SOURCE, java.util.Map.of(), java.util.Set.of()));
        AuthenticationHandler jwt = stub(AuthenticationResult.authenticated(null, "none", java.util.Map.of(), java.util.Set.of("y")));

        AuthenticationResult result = new ChainedAuthenticationHandler(mtls, jwt).authenticate(request());

        assertThat(result.getPrincipal(), equalTo("CN=client"));
        assertThat(result.getPrincipalSource(), equalTo(MTLSAuthenticationHandler.PRINCIPAL_SOURCE));
    }

    @Test
    public void authenticateFailsWhenAnyDelegateUnauthenticated() {
        AuthenticationHandler ok = stub(AuthenticationResult.authenticated("alice", "verified-oidc", java.util.Map.of(), java.util.Set.of("read")));
        AuthenticationHandler no = stub(AuthenticationResult.unauthenticated());

        AuthenticationHandler chained = new ChainedAuthenticationHandler(ok, no);
        AuthenticationResult result = chained.authenticate(request());

        assertThat(result.isAuthenticated(), equalTo(false));
        assertThat(result.getPrincipal(), org.hamcrest.Matchers.nullValue());
    }

    @Test
    public void authenticateWithNoPrincipalsIsAnonymousButAuthenticated() {
        AuthenticationHandler a = stub(AuthenticationResult.authenticated(null, "verified-mtls", java.util.Map.of(), java.util.Set.of("x")));
        AuthenticationHandler b = stub(AuthenticationResult.authenticated(null, "none", java.util.Map.of(), java.util.Set.of("y")));

        AuthenticationResult result = new ChainedAuthenticationHandler(a, b).authenticate(request());
        assertThat(result.isAuthenticated(), equalTo(true));
        assertThat(result.getPrincipal(), org.hamcrest.Matchers.nullValue());
        assertThat(result.getScopes(), org.hamcrest.Matchers.containsInAnyOrder("x", "y"));
    }

    @Test
    public void shouldValidateMTLSAndJWTWithMTLSFirst() {
        // given certificate
        AuthenticationHandler mtlsAuthenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/ca.pem").toArray(new X509Certificate[0])
        );
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/leaf-cert.pem").toArray(new X509Certificate[0])
        );

        // and jwt
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair).generateJWT();

        AuthenticationHandler jwtAuthenticationHandler = new JWTAuthenticationHandler(mockServerLogger, jwkFile);
        request.withHeader(AUTHORIZATION.toString(), "Bearer " + jwt);

        // and chained
        AuthenticationHandler chainedAuthenticationHandler = new ChainedAuthenticationHandler(mtlsAuthenticationHandler, jwtAuthenticationHandler);

        // when
        assertThat(chainedAuthenticationHandler.controlPlaneRequestAuthenticated(request), equalTo(true));
    }

    @Test
    public void shouldValidateMTLSAndJWTWithJWTFirst() {
        // given certificate
        AuthenticationHandler mtlsAuthenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/ca.pem").toArray(new X509Certificate[0])
        );
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/leaf-cert.pem").toArray(new X509Certificate[0])
        );

        // and jwt
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair).generateJWT();

        AuthenticationHandler jwtAuthenticationHandler = new JWTAuthenticationHandler(mockServerLogger, jwkFile);
        request.withHeader(AUTHORIZATION.toString(), "Bearer " + jwt);

        // and chained
        AuthenticationHandler chainedAuthenticationHandler = new ChainedAuthenticationHandler(jwtAuthenticationHandler, mtlsAuthenticationHandler);

        // when
        assertThat(chainedAuthenticationHandler.controlPlaneRequestAuthenticated(request), equalTo(true));
    }

    @Test
    public void shouldValidateMTLSAndJWTWithJWTFailing() {
        // given certificate
        AuthenticationHandler mtlsAuthenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/ca.pem").toArray(new X509Certificate[0])
        );
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/leaf-cert.pem").toArray(new X509Certificate[0])
        );

        // and jwt
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().minus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "scope", "internal public",
                    "sub", UUID.randomUUID().toString()
                )
            );
        AuthenticationHandler jwtAuthenticationHandler = new JWTAuthenticationHandler(mockServerLogger, jwkFile);
        request.withHeader(AUTHORIZATION.toString(), "Bearer " + jwt);

        // and chained
        AuthenticationHandler chainedAuthenticationHandler = new ChainedAuthenticationHandler(jwtAuthenticationHandler, mtlsAuthenticationHandler);

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> chainedAuthenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("Expired JWT"));
    }

    @Test
    public void shouldValidateMTLSAndJWTWithMTLSFailing() {
        // given certificate
        AuthenticationHandler mtlsAuthenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/separateca/ca.pem").toArray(new X509Certificate[0])
        );
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/leaf-cert.pem").toArray(new X509Certificate[0])
        );

        // and jwt
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair).generateJWT();

        AuthenticationHandler jwtAuthenticationHandler = new JWTAuthenticationHandler(mockServerLogger, jwkFile);
        request.withHeader(AUTHORIZATION.toString(), "Bearer " + jwt);

        // and chained
        AuthenticationHandler chainedAuthenticationHandler = new ChainedAuthenticationHandler(jwtAuthenticationHandler, mtlsAuthenticationHandler);

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> chainedAuthenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("control plane request failed authentication no client certificates can be validated by control plane CA"));
    }

    @Test
    public void shouldValidateMTLSAndJWTWithBothFailing() {
        // given certificate
        AuthenticationHandler mtlsAuthenticationHandler = new MTLSAuthenticationHandler(
            mockServerLogger,
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/separateca/ca.pem").toArray(new X509Certificate[0])
        );
        HttpRequest request = new JDKCertificateToMockServerX509Certificate(mockServerLogger).setClientCertificates(
            request(),
            PEMToFile.x509ChainFromPEMFile("org/mockserver/authentication/mtls/leaf-cert.pem").toArray(new X509Certificate[0])
        );

        // and jwt
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().minus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "scope", "internal public",
                    "sub", UUID.randomUUID().toString()
                )
            );
        AuthenticationHandler jwtAuthenticationHandler = new JWTAuthenticationHandler(mockServerLogger, jwkFile);
        request.withHeader(AUTHORIZATION.toString(), "Bearer " + jwt);

        // and chained
        AuthenticationHandler chainedAuthenticationHandler = new ChainedAuthenticationHandler(mtlsAuthenticationHandler, jwtAuthenticationHandler);

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> chainedAuthenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("control plane request failed authentication no client certificates can be validated by control plane CA"));
    }


    private static AuthenticationHandler named(String identity) {
        return new AuthenticationHandler() {
            @Override
            public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
                return true;
            }

            @Override
            public String principalIdentity(HttpRequest request, AuthenticationResult result) {
                return identity;
            }
        };
    }

    @Test
    public void principalIdentityJoinsEveryDelegateThatCanNameTheCaller() {
        AuthenticationResult result = AuthenticationResult.authenticated(null, "none", java.util.Map.of(), java.util.Set.of());

        assertThat(new ChainedAuthenticationHandler(named("mtls:a"), named(null), named("jwt:b")).principalIdentity(request(), result), equalTo("mtls:a & jwt:b"));
        assertThat(new ChainedAuthenticationHandler(named(null), named(null)).principalIdentity(request(), result), equalTo(null));
    }
}
