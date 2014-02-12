package org.mockserver.socket;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

/**
 * @author jamesdbloom
 */
public class SSLFactory {

    public static final String KEY_STORE_CERT_ALIAS = "certAlias";
    public static final String KEY_STORE_CA_ALIAS = "caAlias";
    public static final String KEY_STORE_PASSWORD = "changeit";
    public static final String KEY_STORE_FILENAME = "keystore.jks";
    private static final SSLFactory sslFactory = new SSLFactory();
    private final Logger logger = LoggerFactory.getLogger(SSLFactory.class);
    private final TrustManager DUMMY_TRUST_MANAGER = new X509TrustManager() {
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

    @VisibleForTesting
    SSLFactory() {

    }

    public static SSLFactory getInstance() {
        return sslFactory;
    }

    public SSLContext sslContext() {
        try {
            // key manager
            KeyManagerFactory keyManagerFactory = getKeyManagerFactoryInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(buildKeyStore(), KEY_STORE_PASSWORD.toCharArray());

            // ssl context
            SSLContext sslContext = getSSLContextInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), new TrustManager[]{DUMMY_TRUST_MANAGER}, null);
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
        if (keystore == null) {
            File keyStoreFile = new File(KEY_STORE_FILENAME);
            if (keyStoreFile.exists()) {
                loadKeyStore(keyStoreFile);
            } else {
                dynamicallyCreateKeyStore();
                saveKeyStore();
            }
        }
        return keystore;
    }

    @VisibleForTesting
    SSLContext getSSLContextInstance(String protocol) throws NoSuchAlgorithmException {
        return SSLContext.getInstance(protocol);
    }

    @VisibleForTesting
    KeyManagerFactory getKeyManagerFactoryInstance(String algorithm) throws NoSuchAlgorithmException {
        return KeyManagerFactory.getInstance(algorithm);
    }

    private void dynamicallyCreateKeyStore() {
        try {
            keystore = new KeyStoreFactory().generateCertificate(
                    KEY_STORE_CERT_ALIAS,
                    KEY_STORE_CA_ALIAS,
                    KEY_STORE_PASSWORD.toCharArray(),
                    "CN=www.mockserver.com, O=MockServer, L=London, S=England, C=UK"
            );
        } catch (Exception e) {
            throw new RuntimeException("Exception while building KeyStore dynamically", e);
        }
    }

    private void loadKeyStore(File keyStoreFile) {
        try {
            FileInputStream fileInputStream = null;
            try {
                fileInputStream = new FileInputStream(KEY_STORE_FILENAME);
                logger.trace("Loading key store from file [" + keyStoreFile + "]");
                keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                keystore.load(fileInputStream, KEY_STORE_PASSWORD.toCharArray());
            } finally {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Exception while loading KeyStore from " + keyStoreFile.getAbsolutePath(), e);
        }
    }

    private void saveKeyStore() {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            keystore.store(bout, KEY_STORE_PASSWORD.toCharArray());
            File keyStoreFile = new File(KEY_STORE_FILENAME);
            logger.trace("Saving key store to file [" + keyStoreFile + "]");
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(keyStoreFile);
                fileOutputStream.write(bout.toByteArray());
            } finally {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            }
            keyStoreFile.deleteOnExit();
        } catch (Exception e) {
            throw new RuntimeException("Exception while saving KeyStore", e);
        }
    }


}
