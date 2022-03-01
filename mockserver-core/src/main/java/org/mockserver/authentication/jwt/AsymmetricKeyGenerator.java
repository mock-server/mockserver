package org.mockserver.authentication.jwt;

import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.UUID;

public class AsymmetricKeyGenerator {

    public static AsymmetricKeyPair createAsymmetricKeyPairSynchronously(AsymmetricKeyPair.KeyPairAlgorithm algorithm) {
        try {
            KeyPairGenerator generator;
            switch (algorithm) {
                case RS256:
                    generator = KeyPairGenerator.getInstance("RSA");
                    generator.initialize(2048);
                    break;
                case RS384:
                    generator = KeyPairGenerator.getInstance("RSA");
                    generator.initialize(3072);
                    break;
                case RS512:
                    generator = KeyPairGenerator.getInstance("RSA");
                    generator.initialize(4096);
                    break;
                case ES256:
                    generator = KeyPairGenerator.getInstance("EC");
                    generator.initialize(new ECGenParameterSpec("secp256r1"));
                    break;
                case ES384:
                    generator = KeyPairGenerator.getInstance("EC");
                    generator.initialize(new ECGenParameterSpec("secp384r1"));
                    break;
                case ES512:
                    generator = KeyPairGenerator.getInstance("EC");
                    generator.initialize(new ECGenParameterSpec("secp521r1"));
                    break;
                default:
                    throw new IllegalArgumentException(algorithm + " is not a valid key algorithm");
            }
            return new AsymmetricKeyPair(UUID.randomUUID().toString(), algorithm, generator.generateKeyPair());
        } catch (Throwable throwable) {
            throw new RuntimeException("Exception generating key for algorithm \"" + algorithm + "\" " + throwable.getMessage(), throwable);
        }
    }
}