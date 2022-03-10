package org.mockserver.authentication.jwt;

import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.file.FileReader;
import org.mockserver.keys.AsymmetricKeyConverter;
import org.mockserver.keys.AsymmetricKeyGenerator;
import org.mockserver.keys.AsymmetricKeyPair;
import org.mockserver.keys.AsymmetricKeyPairAlgorithm;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.*;

@SuppressWarnings("UnusedReturnValue")
public class JWTValidator {

    private final JWKSource<SecurityContext> jwkSource;
    private String expectedAudience;
    private Map<String, String> matchingClaims;
    private Set<String> requiredClaims;

    public JWTValidator(JWKSource<SecurityContext> jwkSource) {
        this.jwkSource = jwkSource;
    }

    public JWTValidator withExpectedAudience(String expectedAudience) {
        this.expectedAudience = expectedAudience;
        return this;
    }

    public JWTValidator withMatchingClaims(Map<String, String> matchingClaims) {
        this.matchingClaims = matchingClaims;
        return this;
    }

    public JWTValidator withRequiredClaims(Set<String> requiredClaims) {
        this.requiredClaims = requiredClaims;
        return this;
    }

    public JWTClaimsSet validate(String jwt) {
        try {
            ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(
                null,
                new JOSEObjectType("at+jwt"),
                JOSEObjectType.JOSE,
                JOSEObjectType.JOSE_JSON,
                JOSEObjectType.JWT
            ));
            Set<JWSAlgorithm> jwsAlgorithms = new HashSet<>(Arrays.asList(
                JWSAlgorithm.HS256,
                // HMAC using SHA-384 hash algorithm (optional).
                JWSAlgorithm.HS384,
                // HMAC using SHA-512 hash algorithm (optional).
                JWSAlgorithm.HS512,
                // RSASSA-PKCS-v1_5 using SHA-256 hash algorithm (recommended).
                JWSAlgorithm.RS256,
                // RSASSA-PKCS-v1_5 using SHA-384 hash algorithm (optional).
                JWSAlgorithm.RS384,
                // RSASSA-PKCS-v1_5 using SHA-512 hash algorithm (optional).
                JWSAlgorithm.RS512,
                // ECDSA using P-256 (secp256r1) curve and SHA-256 hash algorithm (recommended).
                JWSAlgorithm.ES256,
                // ECDSA using P-256K (secp256k1) curve and SHA-256 hash algorithm (optional).
                JWSAlgorithm.ES256K,
                // ECDSA using P-384 curve and SHA-384 hash algorithm (optional).
                JWSAlgorithm.ES384,
                // ECDSA using P-521 curve and SHA-512 hash algorithm (optional).
                JWSAlgorithm.ES512,
                // RSASSA-PSS using SHA-256 hash algorithm and MGF1 mask generation function with SHA-256 (optional).
                JWSAlgorithm.PS256,
                // RSASSA-PSS using SHA-384 hash algorithm and MGF1 mask generation function with SHA-384 (optional).
                JWSAlgorithm.PS384,
                // RSASSA-PSS using SHA-512 hash algorithm and MGF1 mask generation function with SHA-512 (optional).
                JWSAlgorithm.PS512,
                // EdDSA signature algorithms (optional).
                JWSAlgorithm.EdDSA
            ));
            jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(jwsAlgorithms, jwkSource));
            JWTClaimsSet.Builder matchingClaimsBuilder = new JWTClaimsSet.Builder();
            if (this.matchingClaims != null) {
                this.matchingClaims.forEach(matchingClaimsBuilder::claim);
            }
            jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
               this.expectedAudience,
                matchingClaimsBuilder.build(),
                requiredClaims
            ));
            return jwtProcessor.process(jwt, null);
        } catch (ParseException | BadJOSEException | JOSEException exception) {
            throw new AuthenticationException(exception.getMessage(), exception);
        }
    }

}
