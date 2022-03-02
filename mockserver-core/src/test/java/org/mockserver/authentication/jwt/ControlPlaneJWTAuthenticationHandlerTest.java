package org.mockserver.authentication.jwt;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.ControlPlaneAuthenticationHandler;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.test.TempFileWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.HttpRequest.request;

public class ControlPlaneJWTAuthenticationHandlerTest {

    private static final MockServerLogger mockServerLogger = new MockServerLogger();

    @Test
    public void shouldValidateJWT() {
        // given
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPairSynchronously(AsymmetricKeyPair.KeyPairAlgorithm.RS256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair).generateJWT();

        ControlPlaneAuthenticationHandler authenticationHandler = new ControlPlaneJWTAuthenticationHandler(mockServerLogger, jwkFile);
        HttpRequest request = request().withHeader(AUTHORIZATION.toString(), "Bearer " + jwt);

        // when
        assertThat(authenticationHandler.controlPlaneRequestAuthenticated(request), equalTo(true));
    }

    @Test
    public void shouldValidateWithMatchingClaimsAndRequiredClaimsAndAudience() {
        // given
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPairSynchronously(AsymmetricKeyPair.KeyPairAlgorithm.RS256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().plus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "scope", "internal public",
                    "sub", "some_subject",
                    "aud", "some_audience"
                )
            );

        ControlPlaneAuthenticationHandler authenticationHandler = new ControlPlaneJWTAuthenticationHandler(mockServerLogger, jwkFile)
            .withExpectedAudience("some_audience")
            .withRequiredClaims(new HashSet<>(Arrays.asList("nbf", "scope")))
            .withMatchingClaims(ImmutableMap.of("sub", "some_subject"));
        HttpRequest request = request().withHeader(AUTHORIZATION.toString(), "Bearer " + jwt);

        // when
        assertThat(authenticationHandler.controlPlaneRequestAuthenticated(request), equalTo(true));
    }

    @Test
    public void shouldValidateForAuthorizationHeaderWithExtraSpaces() {
        // given
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPairSynchronously(AsymmetricKeyPair.KeyPairAlgorithm.RS256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair).generateJWT();

        ControlPlaneAuthenticationHandler authenticationHandler = new ControlPlaneJWTAuthenticationHandler(mockServerLogger, jwkFile);
        HttpRequest request = request().withHeader(AUTHORIZATION.toString(), "Bearer           " + jwt);

        // when
        assertThat(authenticationHandler.controlPlaneRequestAuthenticated(request), equalTo(true));
    }

    @Test
    public void shouldNotValidateExpiredJWT() {
        // given
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPairSynchronously(AsymmetricKeyPair.KeyPairAlgorithm.RS256);
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

        ControlPlaneAuthenticationHandler authenticationHandler = new ControlPlaneJWTAuthenticationHandler(mockServerLogger, jwkFile);
        HttpRequest request = request().withHeader(AUTHORIZATION.toString(), "Bearer " + jwt);

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("Expired JWT"));
    }

    @Test
    public void shouldNotValidateWrongAudience() {
        // given
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPairSynchronously(AsymmetricKeyPair.KeyPairAlgorithm.RS256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().plus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "scope", "internal public",
                    "sub", UUID.randomUUID().toString(),
                    "aud", "wrong_audience"
                )
            );

        ControlPlaneAuthenticationHandler authenticationHandler = new ControlPlaneJWTAuthenticationHandler(mockServerLogger, jwkFile)
            .withExpectedAudience("some_audience");
        HttpRequest request = request().withHeader(AUTHORIZATION.toString(), "Bearer " + jwt);

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("JWT audience rejected: [wrong_audience]"));
    }

    @Test
    public void shouldNotValidateWrongMatchingClaims() {
        // given
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPairSynchronously(AsymmetricKeyPair.KeyPairAlgorithm.RS256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().plus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "scope", "internal public",
                    "sub", "wrong_subject",
                    "aud", "wrong_audience"
                )
            );

        ControlPlaneAuthenticationHandler authenticationHandler = new ControlPlaneJWTAuthenticationHandler(mockServerLogger, jwkFile)
            .withMatchingClaims(ImmutableMap.of("sub", "some_subject"));
        HttpRequest request = request().withHeader(AUTHORIZATION.toString(), "Bearer " + jwt);

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("JWT sub claim has value wrong_subject, must be some_subject"));
    }

    @Test
    public void shouldNotValidateMissingRequiredClaims() {
        // given
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPairSynchronously(AsymmetricKeyPair.KeyPairAlgorithm.RS256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().plus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "scope", "internal public",
                    "sub", "wrong_subject",
                    "aud", "wrong_audience"
                )
            );

        ControlPlaneAuthenticationHandler authenticationHandler = new ControlPlaneJWTAuthenticationHandler(mockServerLogger, jwkFile)
            .withRequiredClaims(new HashSet<>(Arrays.asList("jti", "scopes")));
        HttpRequest request = request().withHeader(AUTHORIZATION.toString(), "Bearer " + jwt);

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("JWT missing required claims: [jti, scopes]"));
    }

    @Test
    public void shouldNotValidateWrongMatchingClaimsAndMissingRequiredClaimsAndWrongAudience() {
        // given
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPairSynchronously(AsymmetricKeyPair.KeyPairAlgorithm.RS256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair)
            .signJWT(
                ImmutableMap.of(
                    "exp", Clock.systemUTC().instant().plus(Duration.ofHours(1)).getEpochSecond(),
                    "iat", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "iss", RandomStringUtils.randomAlphanumeric(20),
                    "nbf", Clock.systemUTC().instant().minus(Duration.ofHours(2)).getEpochSecond(),
                    "scope", "internal public",
                    "sub", "wrong_subject",
                    "aud", "wrong_audience"
                )
            );

        ControlPlaneAuthenticationHandler authenticationHandler = new ControlPlaneJWTAuthenticationHandler(mockServerLogger, jwkFile)
            .withExpectedAudience("some_audience")
            .withRequiredClaims(new HashSet<>(Arrays.asList("jti", "scopes")))
            .withMatchingClaims(ImmutableMap.of("sub", "some_subject"));
        HttpRequest request = request().withHeader(AUTHORIZATION.toString(), "Bearer " + jwt);

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("JWT audience rejected: [wrong_audience]"));
    }

    @Test
    public void shouldNotValidateNoAuthorizationHeader() {
        // given
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPairSynchronously(AsymmetricKeyPair.KeyPairAlgorithm.RS256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));

        ControlPlaneAuthenticationHandler authenticationHandler = new ControlPlaneJWTAuthenticationHandler(mockServerLogger, jwkFile);
        HttpRequest request = request();

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("no authorization header found"));
    }

    @Test
    public void shouldNotValidateNotAJWT() {
        // given
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPairSynchronously(AsymmetricKeyPair.KeyPairAlgorithm.RS256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));

        ControlPlaneAuthenticationHandler authenticationHandler = new ControlPlaneJWTAuthenticationHandler(mockServerLogger, jwkFile);
        HttpRequest request = request().withHeader(AUTHORIZATION.toString(), "Bearer " + UUID.randomUUID());

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("Invalid JWT serialization: Missing dot delimiter(s)"));
    }

    @Test
    public void shouldNotValidateIncorrectSchemeForAuthorizationHeader() {
        // given
        AsymmetricKeyPair asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPairSynchronously(AsymmetricKeyPair.KeyPairAlgorithm.RS256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(asymmetricKeyPair));
        String jwt = new JWTGenerator(asymmetricKeyPair).generateJWT();

        ControlPlaneAuthenticationHandler authenticationHandler = new ControlPlaneJWTAuthenticationHandler(mockServerLogger, jwkFile);
        HttpRequest request = request().withHeader(AUTHORIZATION.toString(), "JWT " + jwt);

        // when
        AuthenticationException authenticationException = assertThrows(AuthenticationException.class, () -> authenticationHandler.controlPlaneRequestAuthenticated(request));
        assertThat(authenticationException.getMessage(), equalTo("only \"Bearer\" supported for authorization header"));
    }

}