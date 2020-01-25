package org.mockserver.socket.tls;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.jdk.JDKKeyAndCertificateFactory;
import org.mockserver.socket.tls.jdk.X509Generator;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.mockserver.configuration.ConfigurationProperties.*;

/**
 * @author jamesdbloom
 */
public class NettySslContextFactory {

    private final KeyAndCertificateFactory keyAndCertificateFactory;
    private SslContext clientSslContext = null;
    private SslContext serverSslContext = null;

    public NettySslContextFactory(MockServerLogger mockServerLogger) {
        keyAndCertificateFactory = new JDKKeyAndCertificateFactory(mockServerLogger);
        System.setProperty("https.protocols", "SSLv3,TLSv1,TLSv1.1,TLSv1.2");
    }

    public synchronized SslContext createClientSslContext(boolean forwardProxyClient) {
        if (clientSslContext == null || rebuildKeyStore()) {
            try {
                // create x509 and private key if none exist yet
                if (keyAndCertificateFactory.certificateNotYetCreated()) {
                    keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate();
                }
                SslContextBuilder sslContextBuilder =
                    SslContextBuilder
                        .forClient()
                        .keyManager(
                            keyAndCertificateFactory.privateKey(),
                            keyAndCertificateFactory.x509Certificate(),
                            keyAndCertificateFactory.certificateAuthorityX509Certificate()
                        );
                if (forwardProxyClient) {
                    switch (forwardProxyTLSX509CertificatesTrustManager()) {
                        case ANY:
                            sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
                            break;
                        case JVM:
                            sslContextBuilder.trustManager(jvmCAX509TrustCertificates());
                            break;
                        case CUSTOM:
                            sslContextBuilder.trustManager(customCAX509TrustCertificates());
                            break;
                    }
                } else {
                    sslContextBuilder.trustManager(Arrays.asList(
                        keyAndCertificateFactory.x509Certificate(),
                        keyAndCertificateFactory.certificateAuthorityX509Certificate()
                    ));
                }
                clientSslContext = sslContextBuilder.build();
                rebuildKeyStore(false);
            } catch (Throwable throwable) {
                throw new RuntimeException("Exception creating SSL context for client", throwable);
            }
        }
        return clientSslContext;
    }

    private List<X509Certificate> jvmCAX509TrustCertificates() throws NoSuchAlgorithmException, KeyStoreException {
        ArrayList<X509Certificate> x509Certificates = new ArrayList<>();
        x509Certificates.add(keyAndCertificateFactory.x509Certificate());
        x509Certificates.add(keyAndCertificateFactory.certificateAuthorityX509Certificate());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
        return Arrays
            .stream(trustManagerFactory.getTrustManagers())
            .filter(trustManager -> trustManager instanceof X509TrustManager)
            .flatMap(trustManager -> Arrays.stream(((X509TrustManager) trustManager).getAcceptedIssuers()))
            .collect((Supplier<List<X509Certificate>>) () -> x509Certificates, List::add, List::addAll);
    }

    private List<X509Certificate> customCAX509TrustCertificates() {
        ArrayList<X509Certificate> x509Certificates = new ArrayList<>();
        x509Certificates.add(keyAndCertificateFactory.x509Certificate());
        x509Certificates.add(keyAndCertificateFactory.certificateAuthorityX509Certificate());
        x509Certificates.addAll(X509Generator.x509ChainFromPEMFile(forwardProxyTLSCustomTrustX509Certificates()));
        return x509Certificates;
    }

    public synchronized SslContext createServerSslContext() {
        if (serverSslContext == null
            // create x509 and private key if none exist yet
            || keyAndCertificateFactory.certificateNotYetCreated()
            // re-create x509 and private key if SAN list has been updated and dynamic update has not been disabled
            || rebuildServerKeyStore() && !preventCertificateDynamicUpdate()) {
            try {
                keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate();
                serverSslContext = SslContextBuilder
                    .forServer(
                        keyAndCertificateFactory.privateKey(),
                        keyAndCertificateFactory.x509Certificate(),
                        keyAndCertificateFactory.certificateAuthorityX509Certificate()
                    )
                    .trustManager(
                        keyAndCertificateFactory.x509Certificate(),
                        keyAndCertificateFactory.certificateAuthorityX509Certificate()
                    )
                    .clientAuth(tlsMutualAuthenticationRequired() ? ClientAuth.REQUIRE : ClientAuth.NONE)
                    .build();
                rebuildServerKeyStore(false);
            } catch (Exception e) {
                throw new RuntimeException("Exception creating SSL context for server", e);
            }
        }
        return serverSslContext;
    }

}
