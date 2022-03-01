package org.mockserver.authentication.jwt;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableMap;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.Collections;
import java.util.Map;

public class JWKGenerator {

    private final ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);

    public String generateJWK(AsymmetricKeyPair asymmetricKeyPair) {
        try {
            Map<String, Object> singleKey;
            switch (asymmetricKeyPair.getAlgorithm()) {
                case ES256:
                    singleKey = getEllipticCurveJWK(asymmetricKeyPair, Curve.P_256);
                    break;
                case ES384:
                    singleKey = getEllipticCurveJWK(asymmetricKeyPair, Curve.P_384);
                    break;
                case ES512:
                    singleKey = getEllipticCurveJWK(asymmetricKeyPair, Curve.P_521);
                    break;
                case RS256:
                case RS384:
                case RS512:
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
            throw new RuntimeException("Exception createing JWK", throwable);
        }
    }

    private Map<String, Object> getRSAJWK(AsymmetricKeyPair asymmetricKeyPair) {
        return new RSAKey
            .Builder(AsymmetricKeyConverter.getRSAPublicKey(asymmetricKeyPair.getKeyPair().getPublic().getEncoded()))
            .keyID(asymmetricKeyPair.getKeyId())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(new Algorithm(asymmetricKeyPair.getAlgorithm().name()))
            .build()
            .toJSONObject();
    }

    private Map<String, Object> getEllipticCurveJWK(AsymmetricKeyPair asymmetricKeyPair, Curve curve) {
        return new ECKey
            .Builder(curve, AsymmetricKeyConverter.getECPublicKey(asymmetricKeyPair.getKeyPair().getPublic().getEncoded()))
            .keyID(asymmetricKeyPair.getKeyId())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(new Algorithm(asymmetricKeyPair.getAlgorithm().name()))
            .build()
            .toJSONObject();
    }
}
