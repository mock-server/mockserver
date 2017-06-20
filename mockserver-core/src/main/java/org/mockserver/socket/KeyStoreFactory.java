package org.mockserver.socket;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.configuration.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * @author jamesdbloom
 */
public class KeyStoreFactory {

    public static final String KEY_STORE_PASSWORD = "changeit";
    public static final String CERTIFICATE_DOMAIN = "localhost";
    public static final String KEY_STORE_CERT_ALIAS = "mockserver-client-cert";
    public static final String KEY_STORE_CA_ALIAS = "mockserver-ca-cert";
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
    private static final Logger logger = LoggerFactory.getLogger(KeyStoreFactory.class);
    private static final KeyStoreFactory SSL_FACTORY = new KeyStoreFactory();
    private static SSLContext sslContext;
    private KeyStore keystore;

    private KeyStoreFactory() {

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

    public static KeyStoreFactory getInstance() {
        return SSL_FACTORY;
    }

    public static void addSubjectAlternativeName(String host) {
        if (host != null) {
            String hostWithoutPort = StringUtils.substringBefore(host, ":");

            if (!ConfigurationProperties.containsSslSubjectAlternativeName(hostWithoutPort)) {
                try {
                    // resolve host name for subject alternative name in case host name is ip address
                    for (InetAddress addr : InetAddress.getAllByName(hostWithoutPort)) {
                        ConfigurationProperties.addSslSubjectAlternativeNameIps(addr.getHostAddress());
                        ConfigurationProperties.addSslSubjectAlternativeNameDomains(addr.getHostName());
                        ConfigurationProperties.addSslSubjectAlternativeNameDomains(addr.getCanonicalHostName());
                    }
                } catch (UnknownHostException uhe) {
                    ConfigurationProperties.addSslSubjectAlternativeNameDomains(hostWithoutPort);
                }
            }
        }
    }

    public synchronized SSLSocket wrapSocket(Socket socket) throws Exception {
        // ssl socket factory
        SSLSocketFactory sslSocketFactory = sslContext().getSocketFactory();

        // ssl socket
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
        sslSocket.setUseClientMode(true);
        sslSocket.startHandshake();
        return sslSocket;
    }

    public SSLContext sslContext() {
        if (sslContext == null || ConfigurationProperties.rebuildKeyStore()) {
            try {
                // key manager
                KeyManagerFactory keyManagerFactory = getKeyManagerFactoryInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(buildKeyStore(), ConfigurationProperties.javaKeyStorePassword().toCharArray());

                // ssl context
                sslContext = getSSLContextInstance();
                sslContext.init(keyManagerFactory.getKeyManagers(), InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize the SSLContext", e);
            }
        }
        return sslContext;
    }

    public KeyStore buildKeyStore() {
        if (keystore == null || ConfigurationProperties.rebuildKeyStore()) {
            File keyStoreFile = new File(ConfigurationProperties.javaKeyStoreFilePath());
            System.setProperty("javax.net.ssl.trustStore", keyStoreFile.getAbsolutePath());
            if (keyStoreFile.exists()) {
                keystore = updateExistingKeyStore(keyStoreFile);
            } else {
                createNewKeyStore();
            }
            // don't rebuild again and again and again
            ConfigurationProperties.rebuildKeyStore(false);
        }
        return keystore;
    }

    private SSLContext getSSLContextInstance() throws NoSuchAlgorithmException {
        try {
            logger.debug("Using protocol {}", SSL_CONTEXT_PROTOCOL);
            return SSLContext.getInstance(SSL_CONTEXT_PROTOCOL);
        } catch (NoSuchAlgorithmException e) {
            logger.warn("Protocol {} not available, falling back to {}", SSL_CONTEXT_PROTOCOL, SSL_CONTEXT_FALLBACK_PROTOCOL);
            return SSLContext.getInstance(SSL_CONTEXT_FALLBACK_PROTOCOL);
        }
    }

    KeyManagerFactory getKeyManagerFactoryInstance(String algorithm) throws NoSuchAlgorithmException {
        return KeyManagerFactory.getInstance(algorithm);
    }

    private void createNewKeyStore() {
        try {
            keystore = new KeyAndCertificateFactory().generateCertificate(null);
        } catch (Exception e) {
            throw new RuntimeException("Exception while building KeyStore dynamically", e);
        }
    }

    private KeyStore updateExistingKeyStore(File keyStoreFile) {
        try {
            FileInputStream fileInputStream = null;
            try {
                fileInputStream = new FileInputStream(ConfigurationProperties.javaKeyStoreFilePath());
                logger.trace("Loading key store from file [" + keyStoreFile + "]");
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                keystore.load(fileInputStream, ConfigurationProperties.javaKeyStorePassword().toCharArray());
                new KeyAndCertificateFactory().generateCertificate(keystore);
                return keystore;
            } finally {
                IOUtils.closeQuietly(fileInputStream);
            }
        } catch (Exception e) {
            throw new RuntimeException("Exception while loading KeyStore from " + keyStoreFile.getAbsolutePath(), e);
        }
    }

    /**
     * Save X509Certificate in KeyStore file.
     */
    static KeyStore saveCertificateAsKeyStore(KeyStore existingKeyStore, boolean deleteOnExit, String keyStoreFileName, String certificationAlias, Key privateKey, char[] keyStorePassword, Certificate[] chain, X509Certificate caCert) {
        try {
            KeyStore keyStore = existingKeyStore;
            if (keyStore == null) {
                // create new key store
                keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
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
            File keyStoreFile = new File(keyStoreFileName);
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(keyStoreFile);
                keyStore.store(fileOutputStream, keyStorePassword);
                logger.trace("Saving key store to file [" + keyStoreFileName + "]");
            } finally {
                IOUtils.closeQuietly(fileOutputStream);
            }
            if (deleteOnExit) {
                keyStoreFile.deleteOnExit();
            }
            return keyStore;
        } catch (Exception e) {
            throw new RuntimeException("Exception while saving KeyStore", e);
        }
    }

}
