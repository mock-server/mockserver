package org.mockserver.socket.tls;

import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;

import java.lang.reflect.Constructor;
import java.util.function.Function;

/**
 * @author jamesdbloom
 */
public class KeyAndCertificateFactoryFactory {

    private static Function<MockServerLogger, KeyAndCertificateFactory> customKeyAndCertificateFactorySupplier = null;

    private static final ClassLoader CLASS_LOADER = KeyAndCertificateFactoryFactory.class.getClassLoader();

    public static KeyAndCertificateFactory createKeyAndCertificateFactory(Configuration configuration, MockServerLogger mockServerLogger) {
        if (customKeyAndCertificateFactorySupplier != null) {
            return customKeyAndCertificateFactorySupplier.apply(mockServerLogger);
        } else {
            return new BCKeyAndCertificateFactory(configuration, mockServerLogger);
        }
    }

    private static boolean canNotLoadSunSecurityPackages() {
        Object derValue = null;
        Object x500Name = null;
        try {
            Class<?> derValueClass = CLASS_LOADER.loadClass("sun.security.util.DerValue");
            Constructor<?> derValueConstructor = derValueClass.getDeclaredConstructor(byte.class, String.class);
            derValue = derValueConstructor.newInstance((byte) 0x16, "www.mockserver.com");

            Class<?> x500NameClass = CLASS_LOADER.loadClass("sun.security.x509.X500Name");
            Constructor<?> x500NameConstructor = x500NameClass.getDeclaredConstructor(String.class);
            x500Name = x500NameConstructor.newInstance("C=UK, ST=England, L=London, O=MockServer, CN=www.mockserver.com");
        } catch (Throwable ignore) {
            // ignore
        }
        return derValue == null || x500Name == null;
    }

    private static boolean canNotLoadBouncyCastleClasses() {
        Class<?> bouncyCastleProvider = null;
        Class<?> bouncyCastleX509Holder = null;
        try {
            bouncyCastleProvider = CLASS_LOADER.loadClass("org.bouncycastle.jce.provider.BouncyCastleProvider");
            bouncyCastleX509Holder = CLASS_LOADER.loadClass("org.bouncycastle.cert.X509CertificateHolder");
        } catch (Throwable ignore) {
            // ignore
        }
        return bouncyCastleProvider == null || bouncyCastleX509Holder == null;
    }

    public static Function<MockServerLogger, KeyAndCertificateFactory> getCustomKeyAndCertificateFactorySupplier() {
        return customKeyAndCertificateFactorySupplier;
    }

    public static void setCustomKeyAndCertificateFactorySupplier(Function<MockServerLogger, KeyAndCertificateFactory> customKeyAndCertificateFactorySupplier) {
        KeyAndCertificateFactoryFactory.customKeyAndCertificateFactorySupplier = customKeyAndCertificateFactorySupplier;
    }
}
