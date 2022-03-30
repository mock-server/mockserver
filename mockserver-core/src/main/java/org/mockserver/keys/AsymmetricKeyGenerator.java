package org.mockserver.keys;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.UUID;

public class AsymmetricKeyGenerator {

    public static AsymmetricKeyPair createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm algorithm) {
        return new AsymmetricKeyPair(UUID.randomUUID().toString(), algorithm, createKeyPair(algorithm));
    }

    public static KeyPair createKeyPair(AsymmetricKeyPairAlgorithm algorithm) {
        try {
            KeyPairGenerator generator;
            switch (algorithm) {
                case RSA2048_SHA256:
                case RSA3072_SHA384:
                case RSA4096_SHA512:
                    generator = KeyPairGenerator.getInstance(algorithm.getAlgorithm());
                    generator.initialize(algorithm.getKeyLength());
                    break;
                case EC256_SHA256:
                case EC384_SHA384:
                case ECP512_SHA512:
                    generator = KeyPairGenerator.getInstance(algorithm.getAlgorithm());
                    generator.initialize(new ECGenParameterSpec(algorithm.getECDomainParameters()));
                    break;
                default:
                    throw new IllegalArgumentException(algorithm + " is not a valid key algorithm");
            }
            return generator.generateKeyPair();
        } catch (Throwable throwable) {
            throw new RuntimeException("Exception generating key for algorithm \"" + algorithm + "\" " + throwable.getMessage(), throwable);
        }
    }

}