package org.mockserver.socket;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.mockserver.configuration.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.security.cert.X509Certificate;

import static org.mockserver.socket.KeyAndCertificateFactory.keyAndCertificateFactory;

/**
 * @author jamesdbloom
 */
public class NettySslContextFactory {

    private static final Logger logger = LoggerFactory.getLogger(KeyStoreFactory.class);

    private SslContext clientSslContext = null;
    private SslContext serverSslContext = null;

    private static final NettySslContextFactory NETTY_SSL_CONTEXT_FACTORY = new NettySslContextFactory();

    private NettySslContextFactory() {

    }

    public static NettySslContextFactory nettySslContextFactory() {
        return NETTY_SSL_CONTEXT_FACTORY;
    }

    public synchronized SslContext createClientSslContext() {
        if (clientSslContext == null || ConfigurationProperties.rebuildKeyStore()) {
            try {
                clientSslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
            } catch (SSLException e) {
                throw new RuntimeException("Exception creating SSL context for client", e);
            }
        }
        return clientSslContext;
    }

    public synchronized SslContext createServerSslContext() {
        if (serverSslContext == null || ConfigurationProperties.rebuildKeyStore()) {
            try {
                serverSslContext = buildSslContext();
            } catch (Exception e) {
                throw new RuntimeException("Exception creating SSL context for server", e);
            }
        }
        return serverSslContext;
    }

    /**
     * Create a KeyStore with a server certificate for the given domain and subject alternative names.
     */
    private SslContext buildSslContext() throws Exception {
        keyAndCertificateFactory().buildAndSaveCertificates();

        return SslContextBuilder.forServer(
                keyAndCertificateFactory().mockServerPrivateKey(),
                // do we need this password??
                ConfigurationProperties.javaKeyStorePassword(),
                new X509Certificate[]{
                        keyAndCertificateFactory().mockServerX509Certificate(),
                        keyAndCertificateFactory().mockServerCertificateAuthorityX509Certificate()
                }
        ).build();
    }

}
