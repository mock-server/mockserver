package org.mockserver.authentication.basic;

import org.junit.Test;
import org.mockserver.authentication.AnyOfAuthenticationHandler;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.authentication.ControlPlaneAuthenticationHandlerFactory;
import org.mockserver.authentication.jwt.JWKGenerator;
import org.mockserver.authentication.jwt.JWTAuthenticationHandler;
import org.mockserver.authentication.jwt.JWTGenerator;
import org.mockserver.configuration.Configuration;
import org.mockserver.keys.AsymmetricKeyGenerator;
import org.mockserver.keys.AsymmetricKeyPair;
import org.mockserver.keys.AsymmetricKeyPairAlgorithm;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.test.TempFileWriter;

import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.mockserver.authentication.basic.BasicAuthenticationHandlerTest.basic;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * Basic composes with the other control-plane mechanisms as an alternative: either valid Basic credentials or
 * the other mechanisms authenticate a request, and with Basic off the handler chain is unchanged.
 */
public class ControlPlaneBasicAuthenticationChainTest {

    private static final MockServerLogger LOGGER = new MockServerLogger(ControlPlaneBasicAuthenticationChainTest.class);
    private static final String HASH = PasswordHash.pbkdf2Sha256("s3cret", 10_000);

    private static Configuration basicAndJwt(String jwkFile) {
        return configuration()
            .controlPlaneBasicAuthenticationRequired(true)
            .controlPlaneBasicAuthenticationCredentials("admin:" + HASH + ":platform-admins")
            .controlPlaneJWTAuthenticationRequired(true)
            .controlPlaneJWTAuthenticationJWKSource(jwkFile);
    }

    @Test
    public void shouldAcceptEitherBasicOrJwtWhenBothAreConfigured() {
        AsymmetricKeyPair keyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(keyPair));
        String jwt = new JWTGenerator(keyPair).generateJWT();
        AuthenticationHandler handler = ControlPlaneAuthenticationHandlerFactory.build(basicAndJwt(jwkFile), LOGGER);

        assertThat(handler, instanceOf(AnyOfAuthenticationHandler.class));
        AuthenticationResult viaBasic = handler.authenticate(request().withSecure(true).withHeader(AUTHORIZATION.toString(), basic("admin", "s3cret")));
        assertThat(viaBasic.getPrincipal(), equalTo("admin"));
        assertThat(viaBasic.getPrincipalSource(), equalTo(BasicAuthenticationHandler.PRINCIPAL_SOURCE));
        AuthenticationResult viaJwt = handler.authenticate(request().withSecure(true).withHeader(AUTHORIZATION.toString(), "Bearer " + jwt));
        assertThat(viaJwt.isAuthenticated(), equalTo(true));
        assertThat(viaJwt.getPrincipalSource(), equalTo("none"));
    }

    @Test
    public void shouldReportTheReasonOfTheMechanismTheClientTried() {
        AsymmetricKeyPair keyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        AuthenticationHandler handler = ControlPlaneAuthenticationHandlerFactory.build(basicAndJwt(TempFileWriter.write(new JWKGenerator().generateJWK(keyPair))), LOGGER);

        assertThat(assertThrows(AuthenticationException.class,
                () -> handler.authenticate(request().withSecure(true).withHeader(AUTHORIZATION.toString(), basic("admin", "wrong")))).getMessage(),
            equalTo("control plane request failed basic authentication"));
        assertThat(assertThrows(AuthenticationException.class,
                () -> handler.authenticate(request().withSecure(true).withHeader(AUTHORIZATION.toString(), basic("admin", "s3cret")).withSecure(false))).getMessage(),
            equalTo("basic authentication requires TLS"));
        assertThat(assertThrows(AuthenticationException.class,
                () -> handler.authenticate(request().withSecure(true))).getMessage(),
            equalTo("no authorization header found"));
    }

    @Test
    public void shouldUseOnlyBasicWhenItIsTheOnlyMechanism() {
        AuthenticationHandler handler = ControlPlaneAuthenticationHandlerFactory.build(configuration()
            .controlPlaneBasicAuthenticationRequired(true)
            .controlPlaneBasicAuthenticationCredentials("admin:" + HASH), LOGGER);

        assertThat(handler, instanceOf(BasicAuthenticationHandler.class));
        assertThat(handler.authenticate(request().withSecure(true).withHeader(AUTHORIZATION.toString(), basic("admin", "s3cret"))).getPrincipal(), equalTo("admin"));
    }

    @Test
    public void shouldLeaveTheChainUnchangedWhenBasicIsOff() {
        AsymmetricKeyPair keyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(keyPair));

        assertThat(ControlPlaneAuthenticationHandlerFactory.build(configuration(), LOGGER), nullValue());
        assertThat(ControlPlaneAuthenticationHandlerFactory.build(configuration()
            .controlPlaneJWTAuthenticationRequired(true)
            .controlPlaneJWTAuthenticationJWKSource(jwkFile), LOGGER), instanceOf(JWTAuthenticationHandler.class));
    }

    @Test
    public void shouldFailClosedWhenBasicIsRequiredWithoutUsableUsers() {
        Configuration noUsers = configuration().controlPlaneBasicAuthenticationRequired(true).controlPlaneBasicAuthenticationCredentials("");
        Configuration plainText = configuration().controlPlaneBasicAuthenticationRequired(true).controlPlaneBasicAuthenticationCredentials("admin:hunter2");

        assertThat(ControlPlaneAuthenticationHandlerFactory.build(noUsers, LOGGER), instanceOf(ControlPlaneAuthenticationHandlerFactory.DenyAllAuthenticationHandler.class));
        assertThat(ControlPlaneAuthenticationHandlerFactory.build(plainText, LOGGER), instanceOf(ControlPlaneAuthenticationHandlerFactory.DenyAllAuthenticationHandler.class));
        assertThrows(IllegalArgumentException.class, () -> ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneBasicAuthentication(noUsers, LOGGER));
        assertThrows(IllegalArgumentException.class, () -> ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneBasicAuthentication(plainText, LOGGER));
        ControlPlaneAuthenticationHandlerFactory.requireUsableControlPlaneBasicAuthentication(configuration(), LOGGER);
    }

    @Test
    public void shouldNameTheCallerByTheMechanismThatAuthenticatedIt() {
        AsymmetricKeyPair keyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        AuthenticationHandler handler = ControlPlaneAuthenticationHandlerFactory.build(basicAndJwt(TempFileWriter.write(new JWKGenerator().generateJWK(keyPair))), LOGGER);
        HttpRequest viaBasic = request().withSecure(true).withHeader(AUTHORIZATION.toString(), basic("admin", "s3cret"));

        assertThat(handler.principalIdentity(viaBasic, handler.authenticate(viaBasic)), equalTo("basic:admin"));
    }

    @Test
    public void shouldRebuildTheHandlerWhenBasicSettingsChange() {
        Configuration configuration = configuration().controlPlaneBasicAuthenticationRequired(true).controlPlaneBasicAuthenticationCredentials("admin:" + HASH);
        String before = ControlPlaneAuthenticationHandlerFactory.signature(configuration);

        configuration.controlPlaneBasicAuthenticationCredentials("admin:" + PasswordHash.pbkdf2Sha256("rotated", 10_000));

        assertThat(ControlPlaneAuthenticationHandlerFactory.signature(configuration).equals(before), equalTo(false));
        assertThat(ControlPlaneAuthenticationHandlerFactory.signature(configuration).contains("pbkdf2"), equalTo(false));
    }
}
