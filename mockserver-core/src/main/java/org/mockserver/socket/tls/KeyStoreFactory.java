package org.mockserver.socket.tls;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom, ganskef
 */
public class KeyStoreFactory {

    public static final String KEY_STORE_PASSWORD = "changeit";
    public static final String CERTIFICATE_DOMAIN = "localhost";
    public static final String KEY_STORE_CERT_ALIAS = "mockserver-client-cert";

    private final MockServerLogger mockServerLogger;
    private static final String KEY_STORE_CA_ALIAS = "mockserver-ca-cert";

    /**
     * Enforce TLS 1.2 if available, since it's not default up to Java 8.
     * <p>
     * Java 7 disables TLS 1.1 and 1.2 for clients. From <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/security/SunProviders.html">Java Cryptography Architecture Oracle Providers Documentation:</a>
     * Although SunJSSE in the Java SE 7 release supports TLS 1.1 and TLS 1.2,
     * neither version is enabled by default for client connections. Some
     * servers do not implement forward compatibility correctly and refuse to
     * talk to TLS 1.1 or TLS 1.2 clients. For interoperability, SunJSSE does
     * not enable TLS 1.1 or TLS 1.2 by default for client connections.
     */
    private static final String SSL_CONTEXT_PROTOCOL = "TLSv1.2";
    /**
     * {@link SSLContext}: Every implementation of the Java platform is required
     * to support the following standard SSLContext protocol: TLSv1
     */
    private static final String SSL_CONTEXT_FALLBACK_PROTOCOL = "TLSv1";
    private static SSLContext sslContext;
    private final KeyAndCertificateFactory keyAndCertificateFactory;

    public KeyStoreFactory(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        keyAndCertificateFactory = new KeyAndCertificateFactory(mockServerLogger);
    }

    public static String defaultKeyStoreFileName() {
        if ("jks".equalsIgnoreCase(ConfigurationProperties.javaKeyStoreType())) {
            return "mockserver_keystore.jks";
        } else if ("pkcs12".equalsIgnoreCase(ConfigurationProperties.javaKeyStoreType())) {
            return "mockserver_keystore.p12";
        } else if ("jceks".equalsIgnoreCase(ConfigurationProperties.javaKeyStoreType())) {
            return "mockserver_keystore.jceks";
        } else {
            throw new IllegalArgumentException(ConfigurationProperties.javaKeyStoreType() + " is not a supported keystore type");
        }
    }

    /**
     * Save X509Certificate in KeyStore file.
     */
    private KeyStore saveCertificateAsKeyStore(KeyStore existingKeyStore, boolean deleteOnExit, String keyStoreFileName, String certificationAlias, Key privateKey, char[] keyStorePassword, Certificate[] chain, X509Certificate caCert) {
        try {
            KeyStore keyStore = existingKeyStore;
            if (keyStore == null) {
                // create new key store
                keyStore = KeyStore.getInstance(ConfigurationProperties.javaKeyStoreType());
                keyStore.load(null, keyStorePassword);
            }

            // add certificate
            try {
                keyStore.deleteEntry(certificationAlias);
            } catch (KeyStoreException kse) {
                // ignore as may not exist in keystore yet
            }
            keyStore.setKeyEntry(certificationAlias, privateKey, keyStorePassword, chain);

            // add CA certificate
            try {
                keyStore.deleteEntry(KEY_STORE_CA_ALIAS);
            } catch (KeyStoreException kse) {
                // ignore as may not exist in keystore yet
            }
            keyStore.setCertificateEntry(KEY_STORE_CA_ALIAS, caCert);

            // save as JKS file
            String keyStoreFileAbsolutePath = new File(keyStoreFileName).getAbsolutePath();
            try (FileOutputStream fileOutputStream = new FileOutputStream(keyStoreFileAbsolutePath)) {
                keyStore.store(fileOutputStream, keyStorePassword);
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.TRACE)
                        .setLogLevel(TRACE)
                        .setMessageFormat("Saving key store to file [" + keyStoreFileAbsolutePath + "]")
                );
            }
            if (deleteOnExit) {
                new File(keyStoreFileAbsolutePath).deleteOnExit();
            }
            return keyStore;
        } catch (Exception e) {
            throw new RuntimeException("Exception while saving KeyStore", e);
        }
    }

    public synchronized SSLContext sslContext() {
        if (sslContext == null || ConfigurationProperties.rebuildKeyStore()) {
            try {
                // key manager
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(loadOrCreateKeyStore(), ConfigurationProperties.javaKeyStorePassword().toCharArray());

                // ssl context
                sslContext = getSSLContextInstance();
                sslContext.init(keyManagerFactory.getKeyManagers(), InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize the SSLContext", e);
            }
        }
        return sslContext;
    }

    private SSLContext getSSLContextInstance() throws NoSuchAlgorithmException {
        try {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(SERVER_CONFIGURATION)
                    .setLogLevel(DEBUG)
                    .setMessageFormat("Using protocol {}")
                    .setArguments(SSL_CONTEXT_PROTOCOL)
            );
            return SSLContext.getInstance(SSL_CONTEXT_PROTOCOL);
        } catch (NoSuchAlgorithmException e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.WARN)
                    .setLogLevel(WARN)
                    .setMessageFormat("Protocol {} not available, falling back to {}")
                    .setArguments(SSL_CONTEXT_PROTOCOL, SSL_CONTEXT_FALLBACK_PROTOCOL)
            );
            return SSLContext.getInstance(SSL_CONTEXT_FALLBACK_PROTOCOL);
        }
    }

    public KeyStore loadOrCreateKeyStore() {
        KeyStore keystore = null;
        File keyStoreFile = new File(ConfigurationProperties.javaKeyStoreFilePath());
        if (keyStoreFile.exists()) {
            try (FileInputStream fileInputStream = new FileInputStream(keyStoreFile)) {
                keystore = KeyStore.getInstance(ConfigurationProperties.javaKeyStoreType());
                keystore.load(fileInputStream, ConfigurationProperties.javaKeyStorePassword().toCharArray());
            } catch (Exception e) {
                throw new RuntimeException("Exception while loading KeyStore from " + keyStoreFile.getAbsolutePath(), e);
            }
        }
        System.setProperty("javax.net.ssl.trustStore", keyStoreFile.getAbsolutePath());
        return populateKeyStore(keystore);
    }

    private KeyStore populateKeyStore(KeyStore keyStore) {
        keyAndCertificateFactory.buildAndSaveCertificates();

        return saveCertificateAsKeyStore(
            keyStore,
            ConfigurationProperties.deleteGeneratedKeyStoreOnExit(),
            ConfigurationProperties.javaKeyStoreFilePath(),
            KEY_STORE_CERT_ALIAS,
            keyAndCertificateFactory.mockServerPrivateKey(),
            ConfigurationProperties.javaKeyStorePassword().toCharArray(),
            new X509Certificate[]{
                keyAndCertificateFactory.mockServerX509Certificate(),
                keyAndCertificateFactory.mockServerCertificateAuthorityX509Certificate()
            },
            keyAndCertificateFactory.mockServerCertificateAuthorityX509Certificate()
        );
    }

}
