package org.mockserver.socket.tls;

import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;

import java.lang.reflect.Constructor;

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
            return new BCKeyAndCertificateFactory(configuration, mockServerLogger);
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
