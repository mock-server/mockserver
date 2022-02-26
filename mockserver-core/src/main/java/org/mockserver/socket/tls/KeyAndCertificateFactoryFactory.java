package org.mockserver.socket.tls;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.java.JDKVersion;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.jdk.JDKKeyAndCertificateFactory;
import org.slf4j.event.Level;

import java.lang.reflect.Constructor;
import java.util.function.Function;

/**
 * @author jamesdbloom
 */
public class KeyAndCertificateFactoryFactory {

    private static Function<MockServerLogger, KeyAndCertificateFactory> customKeyAndCertificateFactorySupplier = null;

    private static final ClassLoader CLASS_LOADER = KeyAndCertificateFactoryFactory.class.getClassLoader();

    @SuppressWarnings("unchecked")
    public static KeyAndCertificateFactory createKeyAndCertificateFactory(MockServerLogger mockServerLogger) {
        if (customKeyAndCertificateFactorySupplier != null) {
            return customKeyAndCertificateFactorySupplier.apply(mockServerLogger);
        } else {
            if (ConfigurationProperties.useBouncyCastleForKeyAndCertificateGeneration() || canNotLoadSunSecurityPackages()) {
                if (canNotLoadBouncyCastleClasses()) {
                    if (ConfigurationProperties.useBouncyCastleForKeyAndCertificateGeneration()) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(Level.ERROR)
                                .setMessageFormat("failed to instantiate the BouncyCastle KeyAndCertificateFactory because BouncyCastle library is not available in classpath please ensure the following dependencies are available")
                                .setArguments("<dependency>\n" +
                                    "    <groupId>org.bouncycastle</groupId>\n" +
                                    "    <artifactId>bcprov-jdk15on</artifactId>\n" +
                                    "    <version>1.70</version>\n" +
                                    "</dependency>\n" +
                                    "<dependency>\n" +
                                    "    <groupId>org.bouncycastle</groupId>\n" +
                                    "    <artifactId>bcpkix-jdk15on</artifactId>\n" +
                                    "    <version>1.70</version>\n" +
                                    "</dependency>")
                        );
                    } else {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(Level.WARN)
                                .setMessageFormat("can not load classes in 'sun.security.x509' or 'sun.security.util' so falling back to BouncyCastle KeyAndCertificateFactory but failed to instantiate BouncyCastle; for " + (JDKVersion.getVersion() >= 16 ? "this Java version " + JDKVersion.getVersion() + " (which is >= 16)" : "Java versions >= 16") + " access to packages 'sun.security.x509' and 'sun.security.util' is denied by default at runtime EITHER (1) allow this by adding JVM arguments '--add-exports=java.base/sun.security.x509=ALL-UNNAMED' and '--add-exports=java.base/sun.security.util=ALL-UNNAMED' OR (2) ensure the following dependencies for BouncyCastle are available")
                                .setArguments("<dependency>\n" +
                                    "    <groupId>org.bouncycastle</groupId>\n" +
                                    "    <artifactId>bcprov-jdk15on</artifactId>\n" +
                                    "    <version>1.70</version>\n" +
                                    "</dependency>\n" +
                                    "<dependency>\n" +
                                    "    <groupId>org.bouncycastle</groupId>\n" +
                                    "    <artifactId>bcpkix-jdk15on</artifactId>\n" +
                                    "    <version>1.70</version>\n" +
                                    "</dependency>")
                        );
                    }
                }
                try {
                    Class<KeyAndCertificateFactory> keyAndCertificateFactorClass = (Class<KeyAndCertificateFactory>) CLASS_LOADER.loadClass("org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory");
                    Constructor<KeyAndCertificateFactory> keyAndCertificateFactorConstructor = keyAndCertificateFactorClass.getDeclaredConstructor(MockServerLogger.class);
                    if (MockServerLogger.isEnabled(Level.TRACE)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(Level.TRACE)
                                .setMessageFormat("using Bouncy Castle for X.509 Certificate and Private Key generation")
                        );
                    }
                    return keyAndCertificateFactorConstructor.newInstance(mockServerLogger);
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("failed to instantiate the BouncyCastle KeyAndCertificateFactory")
                            .setThrowable(throwable)
                    );
                    throw new RuntimeException("failed to instantiate the BouncyCastle KeyAndCertificateFactory");
                }
            } else {
                return new JDKKeyAndCertificateFactory(mockServerLogger);
            }
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
