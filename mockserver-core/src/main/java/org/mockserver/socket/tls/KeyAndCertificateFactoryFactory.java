package org.mockserver.socket.tls;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.jdk.JDKKeyAndCertificateFactory;
import org.slf4j.event.Level;

import java.lang.reflect.Constructor;

/**
 * @author jamesdbloom, ganskef
 */
public class KeyAndCertificateFactoryFactory {

    private static final ClassLoader CLASS_LOADER = KeyAndCertificateFactoryFactory.class.getClassLoader();

    public static KeyAndCertificateFactory createKeyAndCertificateFactory(MockServerLogger mockServerLogger) {
        if (ConfigurationProperties.useBouncyCastleForKeyAndCertificateGeneration()) {
            Class<?> bouncyCastleProvider = null;
            Class<?> bouncyCastleX509Holder = null;
            try {
                bouncyCastleProvider = CLASS_LOADER.loadClass("org.bouncycastle.jce.provider.BouncyCastleProvider");
                bouncyCastleX509Holder = CLASS_LOADER.loadClass("org.bouncycastle.cert.X509CertificateHolder");
            } catch (Throwable ignore) {
                // ignore
            }
            if (bouncyCastleProvider == null || bouncyCastleX509Holder == null) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("Failed to instantiate the BouncyCastle KeyAndCertificateFactory because BouncyCastle library is not available in classpath please ensure the following dependencies are available")
                        .setArguments("<dependency>\n" +
                            "    <groupId>org.bouncycastle</groupId>\n" +
                            "    <artifactId>bcprov-jdk15on</artifactId>\n" +
                            "    <version>1.65</version>\n" +
                            "</dependency>\n" +
                            "<dependency>\n" +
                            "    <groupId>org.bouncycastle</groupId>\n" +
                            "    <artifactId>bcpkix-jdk15on</artifactId>\n" +
                            "    <version>1.65</version>\n" +
                            "</dependency>")
                );
            }

            try {
                Class<KeyAndCertificateFactory> keyAndCertificateFactorClass = (Class<KeyAndCertificateFactory>) CLASS_LOADER.loadClass("org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory");
                Constructor<KeyAndCertificateFactory> keyAndCertificateFactorConstructor = keyAndCertificateFactorClass.getDeclaredConstructor(MockServerLogger.class);
                return keyAndCertificateFactorConstructor.newInstance(mockServerLogger);
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("Failed to instantiate the BouncyCastle KeyAndCertificateFactory")
                        .setThrowable(throwable)
                );
            }

            return null;
        } else {
            return new JDKKeyAndCertificateFactory(mockServerLogger);
        }
    }

}
