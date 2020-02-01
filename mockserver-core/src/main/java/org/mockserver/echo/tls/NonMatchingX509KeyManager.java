package org.mockserver.echo.tls;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.jdk.CertificateSigningRequest;
import org.mockserver.socket.tls.jdk.X509AndPrivateKey;
import org.mockserver.socket.tls.jdk.X509Generator;

import javax.net.ssl.*;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.socket.tls.KeyAndCertificateFactory.KEY_GENERATION_ALGORITHM;
import static org.mockserver.socket.tls.KeyAndCertificateFactory.SIGNING_ALGORITHM;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.*;
import static org.slf4j.event.Level.ERROR;

public class NonMatchingX509KeyManager {

    public static SSLContext invalidClientSSLContext() throws Exception {
        // ssl context
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(new KeyManager[]{new FakeX509KeyManager()}, InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
        return sslContext;
    }

    public static SslContext invalidServerSslContext() {
        try {
            SslContext serverSslContext = SslContextBuilder
                .forServer(new FakeX509KeyManager())
                .trustManager(new FakeTrustManagerFactory())
                .clientAuth(ClientAuth.REQUIRE)
                .build();
            rebuildServerTLSContext(false);
            return serverSslContext;
        } catch (Exception e) {
            throw new RuntimeException("Exception creating SSL context for server", e);
        }
    }

    private static class FakeX509KeyManager implements X509KeyManager {
        private static final String CLIENT_ALIAS = "client_alias";
        private static final String SERVER_ALIAS = "server_alias";
        private X509AndPrivateKey certificateAuthorityX509AndPrivateKey;
        private X509AndPrivateKey x509AndPrivateKey;

        private FakeX509KeyManager() {
            MockServerLogger mockServerLogger = new MockServerLogger();
            try {
                X509Generator x509Generator = new X509Generator(mockServerLogger);
                certificateAuthorityX509AndPrivateKey = x509Generator.generateRootX509AndPrivateKey(
                    new CertificateSigningRequest()
                        .setKeyPairAlgorithm(KEY_GENERATION_ALGORITHM)
                        .setSigningAlgorithm(SIGNING_ALGORITHM)
                        .setCommonName(ROOT_COMMON_NAME)
                        .setKeyPairSize(ROOT_KEY_SIZE)
                );
                x509AndPrivateKey = x509Generator.generateLeafX509AndPrivateKey(
                    new CertificateSigningRequest()
                        .setKeyPairAlgorithm(KEY_GENERATION_ALGORITHM)
                        .setSigningAlgorithm(SIGNING_ALGORITHM)
                        .setCommonName(ROOT_COMMON_NAME)
                        .setCommonName(sslCertificateDomainName())
                        .addSubjectAlternativeNames(sslSubjectAlternativeNameDomains())
                        .addSubjectAlternativeNames(sslSubjectAlternativeNameIps())
                        .setKeyPairSize(MOCK_KEY_SIZE),
                    buildDistinguishedName(ROOT_COMMON_NAME),
                    certificateAuthorityX509AndPrivateKey.getPrivateKey()
                );
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.EXCEPTION)
                        .setLogLevel(ERROR)
                        .setMessageFormat("exception create fake certificates and private keys")
                        .setThrowable(throwable)
                );
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
            return new X509Certificate[]{
                X509Generator.x509FromPEM(x509AndPrivateKey.getCert()),
                X509Generator.x509FromPEM(certificateAuthorityX509AndPrivateKey.getCert())
            };
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return X509Generator.privateKeyFromPEM(x509AndPrivateKey.getPrivateKey());
        }
    }

    public static class FakeTrustManagerFactory extends SimpleTrustManagerFactory {

        private static final X509Certificate[] EMPTY_X509_CERTIFICATES = new X509Certificate[0];

        private FakeTrustManagerFactory() {
        }

        @Override
        protected void engineInit(KeyStore keyStore) {
        }

        @Override
        protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
        }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String s) throws CertificateException {
                    System.out.println("Rejecting a client certificate: " + chain[0].getSubjectDN());
                    throw new CertificateException();
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String s) throws CertificateException {
                    System.out.println("Accepting a server certificate: " + chain[0].getSubjectDN());
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return EMPTY_X509_CERTIFICATES;
                }
            }};
        }
    }
}
