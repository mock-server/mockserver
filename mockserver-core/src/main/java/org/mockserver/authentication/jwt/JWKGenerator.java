package org.mockserver.authentication.jwt;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableMap;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.mockserver.keys.AsymmetricKeyConverter;
import org.mockserver.keys.AsymmetricKeyPair;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.Collections;
import java.util.Map;

public class JWKGenerator {

    private final ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true, false);

    public String generateJWK(AsymmetricKeyPair asymmetricKeyPair) {
        try {
            Map<String, Object> singleKey;
            switch (asymmetricKeyPair.getAlgorithm()) {
                case EC256_SHA256:
                    singleKey = getEllipticCurveJWK(asymmetricKeyPair, Curve.P_256);
                    break;
                case EC384_SHA384:
                    singleKey = getEllipticCurveJWK(asymmetricKeyPair, Curve.P_384);
                    break;
                case ECP512_SHA512:
                    singleKey = getEllipticCurveJWK(asymmetricKeyPair, Curve.P_521);
                    break;
                case RSA2048_SHA256:
                case RSA3072_SHA384:
                case RSA4096_SHA512:
                    singleKey = getRSAJWK(asymmetricKeyPair);
                    break;
                default:
                    throw new IllegalArgumentException("Error invalid algorithm has been provided");
            }
            return objectWriter.writeValueAsString(
                ImmutableMap.of(
                    "keys", Collections.singletonList(singleKey)
                )
            );
        } catch (Throwable throwable) {
            throw new RuntimeException("Exception creating JWK", throwable);
        }
    }

    private Map<String, Object> getRSAJWK(AsymmetricKeyPair asymmetricKeyPair) {
        return new RSAKey
            .Builder(AsymmetricKeyConverter.getRSAPublicKey(asymmetricKeyPair.getKeyPair().getPublic().getEncoded()))
            .keyID(asymmetricKeyPair.getKeyId())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(new Algorithm(asymmetricKeyPair.getAlgorithm().getJwtAlgorithm()))
            .build()
            .toJSONObject();
    }

    private Map<String, Object> getEllipticCurveJWK(AsymmetricKeyPair asymmetricKeyPair, Curve curve) {
        return new ECKey
            .Builder(curve, AsymmetricKeyConverter.getECPublicKey(asymmetricKeyPair.getKeyPair().getPublic().getEncoded()))
            .keyID(asymmetricKeyPair.getKeyId())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(new Algorithm(asymmetricKeyPair.getAlgorithm().getJwtAlgorithm()))
            .build()
            .toJSONObject();
    }
}
