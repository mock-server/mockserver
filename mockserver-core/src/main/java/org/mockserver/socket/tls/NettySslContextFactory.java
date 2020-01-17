package org.mockserver.socket.tls;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;

import javax.net.ssl.SSLException;

/**
 * @author jamesdbloom
 */
public class NettySslContextFactory {

    private final KeyAndCertificateFactory keyAndCertificateFactory;
    private SslContext clientSslContext = null;
    private SslContext serverSslContext = null;

    public NettySslContextFactory(MockServerLogger mockServerLogger) {
        keyAndCertificateFactory = new BCKeyAndCertificateFactory(mockServerLogger);
        System.setProperty("https.protocols", "SSLv3,TLSv1,TLSv1.1,TLSv1.2");
    }

    public synchronized SslContext createClientSslContext() {
        if (clientSslContext == null || ConfigurationProperties.rebuildKeyStore()) {
            try {
                clientSslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
                ConfigurationProperties.rebuildKeyStore(false);
            } catch (SSLException e) {
                throw new RuntimeException("Exception creating SSL context for client", e);
            }
        }
        return clientSslContext;
    }

    public synchronized SslContext createServerSslContext() {
        if (serverSslContext == null
            || !keyAndCertificateFactory.certificateCreated()
            || !ConfigurationProperties.preventCertificateDynamicUpdate() && ConfigurationProperties.rebuildServerKeyStore()) {
            try {
                keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate();
                serverSslContext = SslContextBuilder.forServer(
                    keyAndCertificateFactory.privateKey(),
                    // do we need this password??
                    ConfigurationProperties.javaKeyStorePassword(),
                    keyAndCertificateFactory.x509Certificate(),
                    keyAndCertificateFactory.certificateAuthorityX509Certificate()
                ).build();
                ConfigurationProperties.rebuildServerKeyStore(false);
            } catch (Exception e) {
                throw new RuntimeException("Exception creating SSL context for server", e);
            }
        }
        return serverSslContext;
    }

}
