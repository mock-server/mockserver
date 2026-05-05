package org.mockserver.keys;

import java.security.KeyPair;

public class AsymmetricKeyPair {

    private final String keyId;
    private final AsymmetricKeyPairAlgorithm algorithm;
    private final KeyPair keyPair;

    public AsymmetricKeyPair(String keyId, AsymmetricKeyPairAlgorithm algorithm, KeyPair keyPair) {
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.keyPair = keyPair;
    }

    public String getKeyId() {
        return keyId;
    }

    public AsymmetricKeyPairAlgorithm getAlgorithm() {
        return algorithm;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

}
