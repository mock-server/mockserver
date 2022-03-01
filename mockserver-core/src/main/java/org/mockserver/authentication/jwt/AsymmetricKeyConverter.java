package org.mockserver.authentication.jwt;

import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.Callable;

public class AsymmetricKeyConverter {

    public static RSAPublicKey getRSAPublicKey(byte[] publicKey) {
        return convertKey(
            "RSA",
            "public",
            () -> (RSAPublicKey) KeyFactory
                .getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(publicKey))
        );
    }

    static RSAPrivateKey getRSAPrivateKey(byte[] privateKey) {
        return convertKey(
            "RSA",
            "private",
            () -> (RSAPrivateKey) KeyFactory
                .getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(privateKey))
        );
    }

    static ECPublicKey getECPublicKey(byte[] publicKey) {
        return convertKey(
            "EC",
            "public",
            () -> (ECPublicKey) KeyFactory
                .getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(publicKey))
        );
    }

    static ECPrivateKey getECPrivateKey(byte[] privateKey) {
        return convertKey(
            "EC",
            "private",
            () -> (ECPrivateKey) KeyFactory
                .getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(privateKey))
        );
    }

    private static <T> T convertKey(String algorithm, String keyType, Callable<T> callable) {
        try {
            return callable.call();
        } catch (Throwable throwable) {
            throw new RuntimeException("Exception converting " + keyType + " key for algorithm \"" + algorithm + "\" " + throwable.getMessage(), throwable);
        }
    }

}
