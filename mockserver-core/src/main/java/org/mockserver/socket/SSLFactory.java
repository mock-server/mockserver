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
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;

/**
 * @author jamesdbloom
 */
public class SSLFactory {

    public static final String KEY_STORE_PASSWORD = "changeit";
    public static final String CERTIFICATE_DOMAIN = "localhost";
    public static final String KEY_STORE_CERT_ALIAS = "mockserver-client-cert";
    public static final String KEY_STORE_CA_ALIAS = "mockserver-ca-cert";
    private static final Logger logger = LoggerFactory.getLogger(SSLFactory.class);
    private static final SSLFactory SSL_FACTORY = new SSLFactory();
    private static SSLContext sslContext;
    private KeyStore keystore;

    private SSLFactory() {

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

    public static SSLFactory getInstance() {
        return SSL_FACTORY;
    }

    public synchronized static SSLEngine createClientSSLEngine() {
        SSLEngine engine = SSLFactory.getInstance().sslContext().createSSLEngine();
        engine.setUseClientMode(true);
        return engine;
    }

    public synchronized static SSLEngine createServerSSLEngine() {
        SSLEngine engine = SSLFactory.getInstance().sslContext().createSSLEngine();
        engine.setUseClientMode(false);
        return engine;
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
                sslContext = getSSLContextInstance("TLS");
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

    private SSLContext getSSLContextInstance(String protocol) throws NoSuchAlgorithmException {
        return SSLContext.getInstance(protocol);
    }

    private KeyManagerFactory getKeyManagerFactoryInstance(String algorithm) throws NoSuchAlgorithmException {
        return KeyManagerFactory.getInstance(algorithm);
    }

    private void createNewKeyStore() {
        try {
            keystore = new KeyStoreFactory().generateCertificate(
                    null,
                    KEY_STORE_CERT_ALIAS,
                    KEY_STORE_CA_ALIAS,
                    ConfigurationProperties.javaKeyStorePassword().toCharArray(),
                    ConfigurationProperties.sslCertificateDomainName(),
                    ConfigurationProperties.sslSubjectAlternativeNameDomains(),
                    ConfigurationProperties.sslSubjectAlternativeNameIps()
            );
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
                new KeyStoreFactory().generateCertificate(
                        keystore,
                        KEY_STORE_CERT_ALIAS,
                        KEY_STORE_CA_ALIAS,
                        ConfigurationProperties.javaKeyStorePassword().toCharArray(),
                        ConfigurationProperties.sslCertificateDomainName(),
                        ConfigurationProperties.sslSubjectAlternativeNameDomains(),
                        ConfigurationProperties.sslSubjectAlternativeNameIps()
                );
                return keystore;
            } finally {
                IOUtils.closeQuietly(fileInputStream);
            }
        } catch (Exception e) {
            throw new RuntimeException("Exception while loading KeyStore from " + keyStoreFile.getAbsolutePath(), e);
        }
    }

}
