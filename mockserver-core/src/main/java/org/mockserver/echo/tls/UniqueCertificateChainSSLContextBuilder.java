package org.mockserver.echo.tls;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.jdk.CertificateSigningRequest;
import org.mockserver.socket.tls.jdk.X509AndPrivateKey;
import org.mockserver.socket.tls.jdk.X509Generator;

import javax.net.ssl.*;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.socket.tls.KeyAndCertificateFactory.KEY_GENERATION_ALGORITHM;
import static org.mockserver.socket.tls.KeyAndCertificateFactory.SIGNING_ALGORITHM;
import static org.mockserver.socket.tls.PEMToFile.privateKeyFromPEM;
import static org.mockserver.socket.tls.PEMToFile.x509FromPEM;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.*;
import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.WARN;

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
        private X509AndPrivateKey certificateAuthorityX509AndPrivateKey;
        private X509AndPrivateKey x509AndPrivateKey;

        private UniqueCertificateChainX509KeyManager() {
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
                    certificateAuthorityX509AndPrivateKey.getPrivateKey(),
                    x509FromPEM(certificateAuthorityX509AndPrivateKey.getCert())
                );
            } catch (Throwable throwable) {
                if (MockServerLogger.isEnabled(ERROR)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(ERROR)
                            .setMessageFormat("exception create fake certificates and private keys")
                            .setThrowable(throwable)
                    );
                }
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
                x509FromPEM(x509AndPrivateKey.getCert()),
                x509FromPEM(certificateAuthorityX509AndPrivateKey.getCert())
            };
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return privateKeyFromPEM(x509AndPrivateKey.getPrivateKey());
        }
    }

}
