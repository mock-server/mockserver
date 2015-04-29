package org.mockserver.socket;

import org.apache.commons.io.IOUtils;
import org.mockserver.configuration.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * @author jamesdbloom
 */
public class SSLFactory {

    public static final String KEY_STORE_PASSWORD = "changeit";
    public static final String CERTIFICATE_DOMAIN = "localhost";
    public static final String KEY_STORE_CERT_ALIAS = "certAlias";
    private static final Logger logger = LoggerFactory.getLogger(SSLFactory.class);
    private static final String KEY_STORE_CA_ALIAS = "caAlias";
    private static final SSLFactory SSL_FACTORY = new SSLFactory();
    private static final TrustManager DUMMY_TRUST_MANAGER = new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            logger.trace("Approving client certificate for: " + chain[0].getSubjectDN());
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            logger.trace("Approving server certificate for: " + chain[0].getSubjectDN());
        }
    };
    private KeyStore keystore;

    private SSLFactory() {

    }

    public static String defaultKeyStoreFileName() {
        if ("jks".equalsIgnoreCase(ConfigurationProperties.javaKeyStoreType())) {
            return "keystore.jks";
        } else if ("pkcs12".equalsIgnoreCase(ConfigurationProperties.javaKeyStoreType())) {
            return "keystore.p12";
        } else if ("jceks".equalsIgnoreCase(ConfigurationProperties.javaKeyStoreType())) {
            return "keystore.jceks";
        } else {
            throw new IllegalArgumentException(ConfigurationProperties.javaKeyStoreType() + " is not a supported keystore type");
        }
    }

    public static SSLFactory getInstance() {
        return SSL_FACTORY;
    }

    public static SSLEngine createClientSSLEngine() {
        SSLEngine engine = SSLFactory.getInstance().sslContext().createSSLEngine();
        engine.setUseClientMode(true);
        return engine;
    }

    public static SSLEngine createServerSSLEngine() {
        SSLEngine engine = SSLFactory.getInstance().sslContext().createSSLEngine();
        engine.setUseClientMode(false);
        return engine;
    }

    public SSLContext sslContext() {
        try {
            // key manager
            KeyManagerFactory keyManagerFactory = getKeyManagerFactoryInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(buildKeyStore(), ConfigurationProperties.javaKeyStorePassword().toCharArray());

            final X509ExtendedKeyManager sunKeyManager = (X509ExtendedKeyManager) keyManagerFactory.getKeyManagers()[0];

            X509ExtendedKeyManager keyManager = new X509ExtendedKeyManager() {

                @Override
                public String[] getClientAliases(String s, Principal[] principals) {
                    return sunKeyManager.getClientAliases(s, principals);
                }

                @Override
                public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
                    return sunKeyManager.chooseClientAlias(strings, principals, socket);
                }

                @Override
                public String[] getServerAliases(String s, Principal[] principals) {
                    return sunKeyManager.getServerAliases(s, principals);
                }

                @Override
                public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
                    return sunKeyManager.chooseServerAlias(s, principals, socket);
                }

                @Override
                public X509Certificate[] getCertificateChain(String s) {
                    return sunKeyManager.getCertificateChain(s);
                }

                @Override
                public PrivateKey getPrivateKey(String s) {
                    return sunKeyManager.getPrivateKey(s);
                }

                public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
                    return sunKeyManager.chooseEngineClientAlias(keyType, issuers, engine);
                }

                public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
                    return sunKeyManager.chooseEngineServerAlias(keyType, issuers, engine);
                }
            };

            // ssl context
            SSLContext sslContext = getSSLContextInstance("TLS");
            sslContext.init(new KeyManager[]{keyManager}, new TrustManager[]{DUMMY_TRUST_MANAGER}, null);
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize the SSLContext", e);
        }
    }

    public SSLSocket wrapSocket(Socket socket) throws Exception {
        // ssl socket factory
        SSLSocketFactory sslSocketFactory = sslContext().getSocketFactory();

        // ssl socket
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
        sslSocket.setUseClientMode(true);
        sslSocket.startHandshake();
        return sslSocket;
    }

    public KeyStore buildKeyStore() {
        return buildKeyStore(ConfigurationProperties.rebuildKeyStore());
    }

    public KeyStore buildKeyStore(boolean forceRebuild) {
        if (keystore == null || forceRebuild) {
            File keyStoreFile = new File(ConfigurationProperties.javaKeyStoreFilePath());
            System.setProperty("javax.net.ssl.trustStore", keyStoreFile.getAbsolutePath());
            if (keyStoreFile.exists() && !forceRebuild) {
                keystore = loadKeyStore(keyStoreFile);
            } else {
                dynamicallyCreateKeyStore();
            }
        }
        return keystore;
    }

    private SSLContext getSSLContextInstance(String protocol) throws NoSuchAlgorithmException {
        return SSLContext.getInstance(protocol);
    }

    private KeyManagerFactory getKeyManagerFactoryInstance(String algorithm) throws NoSuchAlgorithmException {
        return KeyManagerFactory.getInstance(algorithm);
    }

    private void dynamicallyCreateKeyStore() {
        try {
            keystore = new KeyStoreFactory().generateCertificate(
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

    private KeyStore loadKeyStore(File keyStoreFile) {
        try {
            FileInputStream fileInputStream = null;
            try {
                fileInputStream = new FileInputStream(ConfigurationProperties.javaKeyStoreFilePath());
                logger.trace("Loading key store from file [" + keyStoreFile + "]");
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                keystore.load(fileInputStream, ConfigurationProperties.javaKeyStorePassword().toCharArray());
                return keystore;
            } finally {
                IOUtils.closeQuietly(fileInputStream);
            }
        } catch (Exception e) {
            throw new RuntimeException("Exception while loading KeyStore from " + keyStoreFile.getAbsolutePath(), e);
        }
    }

}
