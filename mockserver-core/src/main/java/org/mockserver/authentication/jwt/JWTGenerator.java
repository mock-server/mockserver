package org.mockserver.authentication.jwt;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableMap;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import org.mockserver.keys.AsymmetricKeyConverter;
import org.mockserver.keys.AsymmetricKeyPair;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

public class JWTGenerator {

    private final ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);

    private final JWKGenerator jwkGenerator;
    private final AsymmetricKeyPair asymmetricKeyPair;

    public JWTGenerator(AsymmetricKeyPair asymmetricKeyPair) {
        this.asymmetricKeyPair = asymmetricKeyPair;
        this.jwkGenerator = new JWKGenerator();
    }

    public String generateJWT() {
        try {
            this.jwkGenerator.generateJWK(asymmetricKeyPair);
            Instant now = Instant.now();
            JWSObject jwt = new JWSObject(
                new JWSHeader
                    .Builder(JWSAlgorithm.RS256)
                    .keyID(asymmetricKeyPair.getKeyId())
                    .type(JOSEObjectType.JWT)
                    .build(),
                new Payload(ImmutableMap.of(
                    "sub", UUID.randomUUID().toString(),
                    "aud", "https://www.mock-server.com",
                    "iss", "https://www.mock-server.com",
                    "nbf", now.minus(4, ChronoUnit.HOURS).getEpochSecond(),
                    "exp", now.plus(4, ChronoUnit.HOURS).getEpochSecond(),
                    "iat", now.getEpochSecond()
                ))
            );
            RSASSASigner signer = new RSASSASigner(asymmetricKeyPair.getKeyPair().getPrivate());
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Error while generating JWT", e);
        }
    }

    public String signJWT(final Map<String, Serializable> claims) {
        try {
            final JWSSigner signer;
            byte[] privateKeyBytes = asymmetricKeyPair.getKeyPair().getPrivate().getEncoded();
            switch (asymmetricKeyPair.getAlgorithm()) {
                case EC256_SHA256:
                    signer = new ECDSASigner(
                        AsymmetricKeyConverter.getECPrivateKey(privateKeyBytes),
                        Curve.P_256
                    );
                    break;
                case EC384_SHA384:
                    signer = new ECDSASigner(
                        AsymmetricKeyConverter.getECPrivateKey(privateKeyBytes),
                        Curve.P_384
                    );
                    break;
                case ECP512_SHA512:
                    signer = new ECDSASigner(
                        AsymmetricKeyConverter.getECPrivateKey(privateKeyBytes),
                        Curve.P_521
                    );
                    break;
                case RSA2048_SHA256:
                case RSA3072_SHA384:
                case RSA4096_SHA512:
                    signer = new RSASSASigner(AsymmetricKeyConverter.getRSAPrivateKey(privateKeyBytes));
                    break;
                default:
                    throw new IllegalArgumentException("Error invalid algorithm has been provided");
            }

            JWSAlgorithm signingAlgorithm = JWSAlgorithm.parse(asymmetricKeyPair.getAlgorithm().getJwtAlgorithm());
            JWSObject jwt = new JWSObject(
                new JWSHeader
                    .Builder(signingAlgorithm)
                    .keyID(asymmetricKeyPair.getKeyId())
                    .build(),
                new Payload(objectWriter.writeValueAsString(claims))
            );

            jwt.sign(signer);
            return jwt.serialize();
        } catch (Throwable throwable) {
            throw new RuntimeException("Exception signing JWT", throwable);
        }
    }

}
