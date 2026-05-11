package org.mockserver.templates.engine.helpers;

import org.mockserver.authentication.jwt.JWKGenerator;
import org.mockserver.authentication.jwt.JWTGenerator;
import org.mockserver.keys.AsymmetricKeyGenerator;
import org.mockserver.keys.AsymmetricKeyPair;
import org.mockserver.keys.AsymmetricKeyPairAlgorithm;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class JwtTemplateHelper {

    private final AsymmetricKeyPair asymmetricKeyPair;
    private final JWTGenerator jwtGenerator;
    private final JWKGenerator jwkGenerator;

    public JwtTemplateHelper() {
        this.asymmetricKeyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        this.jwtGenerator = new JWTGenerator(asymmetricKeyPair);
        this.jwkGenerator = new JWKGenerator();
    }

    public String generate() {
        return jwtGenerator.generateJWT();
    }

    public String generate(Map<String, Object> claims) {
        Map<String, Serializable> serializableClaims = new HashMap<>();
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            if (entry.getValue() instanceof Serializable) {
                serializableClaims.put(entry.getKey(), (Serializable) entry.getValue());
            } else {
                serializableClaims.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return jwtGenerator.signJWT(serializableClaims);
    }

    public String jwks() {
        return jwkGenerator.generateJWK(asymmetricKeyPair);
    }

    @Override
    public String toString() {
        return generate();
    }
}
