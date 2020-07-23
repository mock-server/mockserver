package org.mockserver.socket.tls;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.socket.tls.KeyAndCertificateFactoryFactory.createKeyAndCertificateFactory;
import static org.mockserver.socket.tls.PEMToFile.privateKeyFromPEMFile;
import static org.mockserver.socket.tls.PEMToFile.x509ChainFromPEMFile;

/**
 * @author jamesdbloom
 */
public class NettySslContextFactory {

    private final MockServerLogger mockServerLogger;
    private final KeyAndCertificateFactory keyAndCertificateFactory;
    private SslContext clientSslContext = null;
    private SslContext serverSslContext = null;

    public NettySslContextFactory(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        keyAndCertificateFactory = createKeyAndCertificateFactory(mockServerLogger);
        System.setProperty("https.protocols", "SSLv3,TLSv1,TLSv1.1,TLSv1.2");
    }

    public synchronized SslContext createClientSslContext(boolean forwardProxyClient) {
        if (clientSslContext == null || ConfigurationProperties.rebuildTLSContext()) {
            try {
                // create x509 and private key if none exist yet
                if (keyAndCertificateFactory.certificateNotYetCreated()) {
                    keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate();
                }
                SslContextBuilder sslContextBuilder =
                    SslContextBuilder
                        .forClient()
                        .keyManager(
                            forwardProxyPrivateKey(),
                            forwardProxyCertificateChain()
                        );
                if (forwardProxyClient) {
                    switch (ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType()) {
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
                    sslContextBuilder.trustManager(trustCertificateChain());
                }
                clientSslContext = sslContextBuilder.build();
                ConfigurationProperties.rebuildTLSContext(false);
            } catch (Throwable throwable) {
                throw new RuntimeException("Exception creating SSL context for client", throwable);
            }
        }
        return clientSslContext;
    }

    private PrivateKey forwardProxyPrivateKey() {
        if (isNotBlank(ConfigurationProperties.forwardProxyPrivateKey())) {
            return privateKeyFromPEMFile(ConfigurationProperties.forwardProxyPrivateKey());
        } else {
            return keyAndCertificateFactory.privateKey();
        }
    }

    private X509Certificate[] forwardProxyCertificateChain() {
        if (isNotBlank(ConfigurationProperties.forwardProxyCertificateChain())) {
            return x509ChainFromPEMFile(ConfigurationProperties.forwardProxyCertificateChain()).toArray(new X509Certificate[0]);
        } else {
            return new X509Certificate[]{
                keyAndCertificateFactory.x509Certificate(),
                keyAndCertificateFactory.certificateAuthorityX509Certificate()
            };
        }
    }

    private X509Certificate[] jvmCAX509TrustCertificates() throws NoSuchAlgorithmException, KeyStoreException {
        ArrayList<X509Certificate> x509Certificates = new ArrayList<>();
        x509Certificates.add(keyAndCertificateFactory.x509Certificate());
        x509Certificates.add(keyAndCertificateFactory.certificateAuthorityX509Certificate());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
        return Arrays
            .stream(trustManagerFactory.getTrustManagers())
            .filter(trustManager -> trustManager instanceof X509TrustManager)
            .flatMap(trustManager -> Arrays.stream(((X509TrustManager) trustManager).getAcceptedIssuers()))
            .collect((Supplier<List<X509Certificate>>) () -> x509Certificates, List::add, List::addAll)
            .toArray(new X509Certificate[0]);
    }

    private X509Certificate[] customCAX509TrustCertificates() {
        ArrayList<X509Certificate> x509Certificates = new ArrayList<>();
        x509Certificates.add(keyAndCertificateFactory.x509Certificate());
        x509Certificates.add(keyAndCertificateFactory.certificateAuthorityX509Certificate());
        x509Certificates.addAll(x509ChainFromPEMFile(ConfigurationProperties.forwardProxyTLSCustomTrustX509Certificates()));
        return x509Certificates.toArray(new X509Certificate[0]);
    }

    public synchronized SslContext createServerSslContext() {
        if (serverSslContext == null
            // create x509 and private key if none exist yet
            || keyAndCertificateFactory.certificateNotYetCreated()
            // re-create x509 and private key if SAN list has been updated and dynamic update has not been disabled
            || ConfigurationProperties.rebuildServerTLSContext() && !ConfigurationProperties.preventCertificateDynamicUpdate()) {
            try {
                keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate();
                serverSslContext = SslContextBuilder
                    .forServer(
                        keyAndCertificateFactory.privateKey(),
                        keyAndCertificateFactory.x509Certificate(),
                        keyAndCertificateFactory.certificateAuthorityX509Certificate()
                    )
                    .trustManager(trustCertificateChain())
                    .clientAuth(ConfigurationProperties.tlsMutualAuthenticationRequired() ? ClientAuth.REQUIRE : ClientAuth.NONE)
                    .build();
                ConfigurationProperties.rebuildServerTLSContext(false);
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception creating SSL context for server" + throwable.getMessage())
                        .setThrowable(throwable)
                );
            }
        }
        return serverSslContext;
    }

    private X509Certificate[] trustCertificateChain() {
        if (isNotBlank(ConfigurationProperties.tlsMutualAuthenticationCertificateChain())) {
            List<X509Certificate> x509Certificates = x509ChainFromPEMFile(ConfigurationProperties.tlsMutualAuthenticationCertificateChain());
            x509Certificates.add(keyAndCertificateFactory.x509Certificate());
            x509Certificates.add(keyAndCertificateFactory.certificateAuthorityX509Certificate());
            return x509Certificates.toArray(new X509Certificate[0]);
        } else {
            return Arrays.asList(
                keyAndCertificateFactory.x509Certificate(),
                keyAndCertificateFactory.certificateAuthorityX509Certificate()
            ).toArray(new X509Certificate[0]);
        }
    }

}
