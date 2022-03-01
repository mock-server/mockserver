package org.mockserver.authentication.jwt;

import com.nimbusds.jose.Algorithm;

import java.security.KeyPair;

public class AsymmetricKeyPair {

    private final String keyId;
    private final KeyPairAlgorithm algorithm;
    private final KeyPair keyPair;

    public AsymmetricKeyPair(String keyId, KeyPairAlgorithm algorithm, KeyPair keyPair) {
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.keyPair = keyPair;
    }

    public String getKeyId() {
        return keyId;
    }

    public KeyPairAlgorithm getAlgorithm() {
        return algorithm;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public static enum KeyPairAlgorithm {
        ES256,
        ES384,
        ES512,
        RS256,
        RS384,
        RS512,
    }
}
