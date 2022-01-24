package org.mockserver.echo.tls;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyAndCertificateFactory;
import org.mockserver.socket.tls.KeyAndCertificateFactoryFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;
import java.io.File;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.UUID;

import static org.slf4j.event.Level.ERROR;

public class UniqueCertificateChainSSLContextBuilder {

    public static SSLContext uniqueCertificateChainSSLContext() throws Exception {
        // ssl context
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(new KeyManager[]{new UniqueCertificateChainX509KeyManager()}, InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
        return sslContext;
    }

    private static class UniqueCertificateChainX509KeyManager implements X509KeyManager {
        private static final String CLIENT_ALIAS = "client_alias";
        private static final String SERVER_ALIAS = "server_alias";
        X509Certificate[] x509Certificates;
        PrivateKey privateKey;

        private UniqueCertificateChainX509KeyManager() {
            MockServerLogger mockServerLogger = new MockServerLogger();
            boolean originalDynamicallyCreateCertificateAuthorityCertificate = ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate();
            String originalDirectoryToSaveDynamicSSLCertificate = ConfigurationProperties.directoryToSaveDynamicSSLCertificate();
            try {
                File tempDirectory = new File(File.createTempFile("prefix", "suffix").getParentFile().getAbsolutePath() + "/" + UUID.randomUUID());
                if (!tempDirectory.mkdir()) {
                    throw new RuntimeException("Exception creating temporary directory for test certificates " + tempDirectory);
                }
                ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate(true);
                ConfigurationProperties.directoryToSaveDynamicSSLCertificate(tempDirectory.getAbsolutePath());
                KeyAndCertificateFactory keyAndCertificateFactory = KeyAndCertificateFactoryFactory.createKeyAndCertificateFactory(mockServerLogger);
                keyAndCertificateFactory.buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
                keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate();
                x509Certificates = new X509Certificate[]{
                    keyAndCertificateFactory.x509Certificate(),
                    keyAndCertificateFactory.certificateAuthorityX509Certificate()
                };
                privateKey = keyAndCertificateFactory.privateKey();
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(ERROR)
                        .setMessageFormat("exception create fake certificates and private keys")
                        .setThrowable(throwable)
                );
            } finally {
                ConfigurationProperties.dynamicallyCreateCertificateAuthorityCertificate(originalDynamicallyCreateCertificateAuthorityCertificate);
                ConfigurationProperties.directoryToSaveDynamicSSLCertificate(originalDirectoryToSaveDynamicSSLCertificate);
            }
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return new String[]{CLIENT_ALIAS};
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return CLIENT_ALIAS;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return new String[]{SERVER_ALIAS};
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return SERVER_ALIAS;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return x509Certificates;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return privateKey;
        }
    }

}
