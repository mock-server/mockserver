package org.mockserver.socket.tls;

import com.google.common.base.Joiner;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.socket.tls.KeyAndCertificateFactoryFactory.createKeyAndCertificateFactory;
import static org.mockserver.socket.tls.PEMToFile.privateKeyFromPEMFile;
import static org.mockserver.socket.tls.PEMToFile.x509ChainFromPEMFile;

/**
 * @author jamesdbloom
 */
public class NettySslContextFactory {

    private static final String[] TLS_PROTOCOLS = "TLSv1,TLSv1.1,TLSv1.2".split(",");
    public static Function<SslContextBuilder, SslContext> clientSslContextBuilderFunction =
        sslContextBuilder -> {
            try {
                return sslContextBuilder.build();
            } catch (SSLException e) {
                throw new RuntimeException(e);
            }
        };

    private final Configuration configuration;
    private final MockServerLogger mockServerLogger;
    private final KeyAndCertificateFactory keyAndCertificateFactory;
    private SslContext clientSslContext = null;
    private SslContext serverSslContext = null;
    private Function<SslContextBuilder, SslContext> instanceClientSslContextBuilderFunction = clientSslContextBuilderFunction;

    /**
     * @deprecated use constructor that specifies configuration explicitly
     */
    @Deprecated
    public NettySslContextFactory(MockServerLogger mockServerLogger) {
        this.configuration = configuration();
        this.mockServerLogger = mockServerLogger;
        keyAndCertificateFactory = createKeyAndCertificateFactory(configuration, mockServerLogger);
        System.setProperty("https.protocols", Joiner.on(",").join(TLS_PROTOCOLS));
        if (configuration.proactivelyInitialiseTLS()) {
            createServerSslContext();
        }
    }

    public NettySslContextFactory(Configuration configuration, MockServerLogger mockServerLogger) {
        this.configuration = configuration;
        this.mockServerLogger = mockServerLogger;
        keyAndCertificateFactory = createKeyAndCertificateFactory(configuration, mockServerLogger);
        System.setProperty("https.protocols", Joiner.on(",").join(TLS_PROTOCOLS));
        if (configuration.proactivelyInitialiseTLS()) {
            createServerSslContext();
        }
    }

    public NettySslContextFactory withClientSslContextBuilderFunction(Function<SslContextBuilder, SslContext> clientSslContextBuilderFunction) {
        this.instanceClientSslContextBuilderFunction = clientSslContextBuilderFunction;
        return this;
    }

    public synchronized SslContext createClientSslContext(boolean forwardProxyClient) {
        if (clientSslContext == null || configuration.rebuildTLSContext()) {
            try {
                // create x509 and private key if none exist yet
                if (keyAndCertificateFactory.certificateNotYetCreated()) {
                    keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate();
                }
                SslContextBuilder sslContextBuilder =
                    SslContextBuilder
                        .forClient()
                        .protocols(TLS_PROTOCOLS)
//                        .sslProvider(SslProvider.JDK)
                        .keyManager(
                            forwardProxyPrivateKey(),
                            forwardProxyCertificateChain()
                        );
                if (forwardProxyClient) {
                    switch (configuration.forwardProxyTLSX509CertificatesTrustManagerType()) {
                        case ANY:
                            sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
                            break;
                        case JVM:
                            List<X509Certificate> mockServerX509Certificates = new ArrayList<>();
                            mockServerX509Certificates.add(keyAndCertificateFactory.x509Certificate());
                            mockServerX509Certificates.add(keyAndCertificateFactory.certificateAuthorityX509Certificate());
                            sslContextBuilder.trustManager(jvmCAX509TrustCertificates(mockServerX509Certificates));
                            break;
                        case CUSTOM:
                            sslContextBuilder.trustManager(customCAX509TrustCertificates());
                            break;
                    }
                } else {
                    List<X509Certificate> mockServerX509Certificates = new ArrayList<>();
                    if (isNotBlank(configuration.tlsMutualAuthenticationCertificateChain())) {
                        mockServerX509Certificates.addAll(x509ChainFromPEMFile(configuration.tlsMutualAuthenticationCertificateChain()));
                        mockServerX509Certificates.add(keyAndCertificateFactory.certificateAuthorityX509Certificate());
                    } else {
                        mockServerX509Certificates.add(keyAndCertificateFactory.certificateAuthorityX509Certificate());
                    }
                    sslContextBuilder.trustManager(jvmCAX509TrustCertificates(mockServerX509Certificates));
                }
                clientSslContext = instanceClientSslContextBuilderFunction.apply(sslContextBuilder);
                configuration.rebuildTLSContext(false);
            } catch (Throwable throwable) {
                throw new RuntimeException("Exception creating SSL context for client", throwable);
            }
        }
        return clientSslContext;
    }

    private PrivateKey forwardProxyPrivateKey() {
        if (isNotBlank(configuration.forwardProxyPrivateKey())) {
            return privateKeyFromPEMFile(configuration.forwardProxyPrivateKey());
        } else {
            return keyAndCertificateFactory.privateKey();
        }
    }

    private X509Certificate[] forwardProxyCertificateChain() {
        if (isNotBlank(configuration.forwardProxyCertificateChain())) {
            return x509ChainFromPEMFile(configuration.forwardProxyCertificateChain()).toArray(new X509Certificate[0]);
        } else {
            return new X509Certificate[]{
                keyAndCertificateFactory.x509Certificate(),
                keyAndCertificateFactory.certificateAuthorityX509Certificate()
            };
        }
    }

    private X509Certificate[] jvmCAX509TrustCertificates(List<X509Certificate> additionalX509Certificates) throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
        return Arrays
            .stream(trustManagerFactory.getTrustManagers())
            .filter(trustManager -> trustManager instanceof X509TrustManager)
            .flatMap(trustManager -> Arrays.stream(((X509TrustManager) trustManager).getAcceptedIssuers()))
            .collect(() -> additionalX509Certificates, List::add, List::addAll)
            .toArray(new X509Certificate[0]);
    }

    private X509Certificate[] customCAX509TrustCertificates() {
        ArrayList<X509Certificate> x509Certificates = new ArrayList<>();
        x509Certificates.add(keyAndCertificateFactory.x509Certificate());
        x509Certificates.add(keyAndCertificateFactory.certificateAuthorityX509Certificate());
        x509Certificates.addAll(x509ChainFromPEMFile(configuration.forwardProxyTLSCustomTrustX509Certificates()));
        return x509Certificates.toArray(new X509Certificate[0]);
    }

    public synchronized SslContext createServerSslContext() {
        if (serverSslContext == null
            // create x509 and private key if none exist yet
            || keyAndCertificateFactory.certificateNotYetCreated()
            // re-create x509 and private key if SAN list has been updated and dynamic update has not been disabled
            || configuration.rebuildServerTLSContext() && !configuration.preventCertificateDynamicUpdate()) {
            try {
                keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate();
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.DEBUG)
                        .setMessageFormat("using certificate authority serial:{}issuer:{}subject:{}and certificate serial:{}issuer:{}subject:{}")
                        .setArguments(
                            keyAndCertificateFactory.certificateAuthorityX509Certificate().getSerialNumber(),
                            keyAndCertificateFactory.certificateAuthorityX509Certificate().getIssuerDN(),
                            keyAndCertificateFactory.certificateAuthorityX509Certificate().getSubjectDN(),
                            keyAndCertificateFactory.x509Certificate().getSerialNumber(),
                            keyAndCertificateFactory.x509Certificate().getIssuerDN(),
                            keyAndCertificateFactory.x509Certificate().getSubjectDN()
                        )
                );
                SslContextBuilder sslContextBuilder = SslContextBuilder
                    .forServer(
                        keyAndCertificateFactory.privateKey(),
                        keyAndCertificateFactory.x509Certificate(),
                        keyAndCertificateFactory.certificateAuthorityX509Certificate()
                    )
                    .protocols(TLS_PROTOCOLS)
//                    .sslProvider(SslProvider.JDK)
                    .clientAuth(configuration.tlsMutualAuthenticationRequired() ? ClientAuth.REQUIRE : ClientAuth.OPTIONAL);
                if (configuration.tlsMutualAuthenticationRequired()) {
                    sslContextBuilder.trustManager(trustCertificateChain());
                } else {
                    sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
                }
                serverSslContext = sslContextBuilder.build();
                configuration.rebuildServerTLSContext(false);
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
        return trustCertificateChain(configuration.tlsMutualAuthenticationCertificateChain());
    }

    public X509Certificate[] trustCertificateChain(String tlsMutualAuthenticationCertificateChain) {
        if (isNotBlank(tlsMutualAuthenticationCertificateChain)) {
            List<X509Certificate> x509Certificates = x509ChainFromPEMFile(tlsMutualAuthenticationCertificateChain);
            x509Certificates.add(keyAndCertificateFactory.certificateAuthorityX509Certificate());
            return x509Certificates.toArray(new X509Certificate[0]);
        } else {
            return Collections
                .singletonList(keyAndCertificateFactory.certificateAuthorityX509Certificate())
                .toArray(new X509Certificate[0]);
        }
    }

}
