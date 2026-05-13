package org.mockserver.socket.tls;

import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;

/**
 * @author jamesdbloom
 */
public class KeyAndCertificateFactoryFactory {

    private static KeyAndCertificateFactorySupplier customKeyAndCertificateFactorySupplier = null;

    private static final ClassLoader CLASS_LOADER = KeyAndCertificateFactoryFactory.class.getClassLoader();

    public static KeyAndCertificateFactory createKeyAndCertificateFactory(Configuration configuration, MockServerLogger mockServerLogger) {
        return createKeyAndCertificateFactory(configuration, mockServerLogger, true);
    }

    public static KeyAndCertificateFactory createKeyAndCertificateFactory(Configuration configuration, MockServerLogger mockServerLogger, boolean forServer) {
        if (customKeyAndCertificateFactorySupplier != null) {
            return customKeyAndCertificateFactorySupplier
                .buildKeyAndCertificateFactory(mockServerLogger, forServer, configuration);
        } else {
            if (isBouncyCastleAvailable()) {
                return new BCKeyAndCertificateFactory(configuration, mockServerLogger);
            } else {
                throw new IllegalStateException(
                    "BouncyCastle (bcprov-jdk18on) is not available on the classpath. " +
                    "Either add bcprov-jdk18on to your dependencies, or provide a custom " +
                    "KeyAndCertificateFactory via KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(). " +
                    "If using bc-fips, provide a FIPS-compatible KeyAndCertificateFactory implementation."
                );
            }
        }
    }

    private static boolean isBouncyCastleAvailable() {
        try {
            CLASS_LOADER.loadClass("org.bouncycastle.jce.provider.BouncyCastleProvider");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static KeyAndCertificateFactorySupplier getCustomKeyAndCertificateFactorySupplier() {
        return customKeyAndCertificateFactorySupplier;
    }

    public static void setCustomKeyAndCertificateFactorySupplier(
        KeyAndCertificateFactorySupplier customKeyAndCertificateFactorySupplier) {
        KeyAndCertificateFactoryFactory.customKeyAndCertificateFactorySupplier = customKeyAndCertificateFactorySupplier;
    }
}
