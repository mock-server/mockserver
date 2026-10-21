package org.mockserver.netty.integration.authenticatedcontrolplane;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.tls.PEMToFile;
import org.mockserver.test.TestCertificates;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedKeyManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.configuration.ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY;
import static org.mockserver.configuration.ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Control-plane mTLS over real TLS against a client that chooses the certificate chain it presents. Only
 * the certificate whose private key the handshake proved may authenticate: appending the trusted CA
 * certificate to an untrusted client certificate must not.
 */
public class ControlPlaneMTLSPresentedChainIntegrationTest {

    private static final String CREATE_EXPECTATION = "{\"httpRequest\":{\"path\":\"/presented-chain\"},\"httpResponse\":{\"body\":\"ok\"}}";
    private static final String TLS_REJECTED = "tls-rejected";

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static TestCertificates.Issued trustedCa;
    private static TestCertificates.Issued trustedClient;
    private static TestCertificates.Issued attackerClient;
    private static TestCertificates.Issued selfSignedClient;
    private static TestCertificates.Issued expiredClient;
    private static TestCertificates.Issued signedByBundledCa;
    private static ClientAndServer controlPlaneMutualTlsOnly;
    private static ClientAndServer controlPlaneAndDataPlaneMutualTls;

    @BeforeClass
    public static void startServers() throws Exception {
        trustedCa = TestCertificates.certificateAuthority("Control Plane CA");
        trustedClient = TestCertificates.clientCertificate("trusted client", trustedCa);
        attackerClient = TestCertificates.clientCertificate("attacker", TestCertificates.certificateAuthority("Attacker CA"));
        selfSignedClient = TestCertificates.selfSignedClientCertificate("self-signed attacker");
        expiredClient = TestCertificates.clientCertificate("expired client", trustedCa, TestCertificates.daysFromNow(-10), TestCertificates.daysFromNow(-1), KeyPurposeId.id_kp_clientAuth);

        signedByBundledCa = TestCertificates.clientCertificate("bundled CA attacker", TestCertificates.issuer(
            PEMToFile.x509FromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE),
            PEMToFile.privateKeyFromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY)
        ));

        File caChain = temporaryFolder.newFile("control-plane-ca.pem");
        Files.write(caChain.toPath(), TestCertificates.toPem(trustedCa.certificate).getBytes(StandardCharsets.UTF_8));

        controlPlaneMutualTlsOnly = ClientAndServer.startClientAndServer(controlPlaneMutualTls(caChain.getAbsolutePath()));
        controlPlaneAndDataPlaneMutualTls = ClientAndServer.startClientAndServer(
            controlPlaneMutualTls(caChain.getAbsolutePath())
                .dynamicallyCreateCertificateAuthorityCertificate(false)
                .certificateAuthorityCertificate(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE)
                .certificateAuthorityPrivateKey(DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY)
                .tlsMutualAuthenticationRequired(true)
                .tlsMutualAuthenticationCertificateChain(caChain.getAbsolutePath())
        );
    }

    @AfterClass
    public static void stopServers() {
        stopQuietly(controlPlaneMutualTlsOnly);
        stopQuietly(controlPlaneAndDataPlaneMutualTls);
    }

    @Test
    public void shouldAuthenticateTrustedClientCertificate() throws Exception {
        assertThat(createExpectation(controlPlaneMutualTlsOnly, trustedClient), is("201"));
    }

    @Test
    public void shouldAuthenticateTrustedClientCertificateWithItsCertificateAuthorityAppended() throws Exception {
        assertThat(createExpectation(controlPlaneMutualTlsOnly, trustedClient, trustedCa.certificate), is("201"));
    }

    @Test
    public void shouldRejectRequestWithoutClientCertificate() throws Exception {
        assertThat(createExpectation(controlPlaneMutualTlsOnly, null), is("401"));
    }

    @Test
    public void shouldRejectAttackerClientCertificateWithTrustedCertificateAuthorityAppended() throws Exception {
        assertThat(createExpectation(controlPlaneMutualTlsOnly, attackerClient, trustedCa.certificate), is("401"));
    }

    @Test
    public void shouldRejectSelfSignedClientCertificateWithTrustedCertificateAuthorityAppended() throws Exception {
        assertThat(createExpectation(controlPlaneMutualTlsOnly, selfSignedClient, trustedCa.certificate), is("401"));
    }

    @Test
    public void shouldRejectAttackerClientCertificateAlone() throws Exception {
        assertThat(createExpectation(controlPlaneMutualTlsOnly, attackerClient), is("401"));
    }

    @Test
    public void shouldRejectExpiredClientCertificate() throws Exception {
        assertThat(createExpectation(controlPlaneMutualTlsOnly, expiredClient), is("401"));
    }

    @Test
    public void shouldAuthenticateTrustedClientCertificateWhenDataPlaneMutualTlsIsAlsoRequired() throws Exception {
        assertThat(createExpectation(controlPlaneAndDataPlaneMutualTls, trustedClient), is("201"));
    }

    @Test
    public void shouldRejectAttackerChainWhenDataPlaneMutualTlsIsAlsoRequired() throws Exception {
        assertThat(createExpectation(controlPlaneAndDataPlaneMutualTls, attackerClient, trustedCa.certificate), is(TLS_REJECTED));
    }

    @Test
    public void shouldRejectChainSignedByBundledCaWithTrustedCertificateAuthorityAppendedWhenDataPlaneMutualTlsIsAlsoRequired() throws Exception {
        // data-plane mTLS does not trust the bundled CA, so the TLS layer refuses it before the control plane sees it
        assertThat(createExpectation(controlPlaneAndDataPlaneMutualTls, signedByBundledCa, trustedCa.certificate), is(TLS_REJECTED));
    }

    private static Configuration controlPlaneMutualTls(String caChain) {
        return configuration()
            .controlPlaneTLSMutualAuthenticationRequired(true)
            .controlPlaneTLSMutualAuthenticationCAChain(caChain)
            .controlPlaneJWTAuthenticationRequired(false)
            .controlPlaneOidcAuthenticationRequired(false);
    }

    /**
     * @return the HTTP status code, or {@value #TLS_REJECTED} when the server ended the TLS session
     */
    private static String createExpectation(ClientAndServer server, TestCertificates.Issued client, X509Certificate... appended) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(client == null ? null : new KeyManager[]{new PresentedChainKeyManager(client, appended)}, InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
        byte[] body = CREATE_EXPECTATION.getBytes(StandardCharsets.UTF_8);
        try (SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket("localhost", server.getPort())) {
            socket.setSoTimeout(10_000);
            socket.startHandshake();
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(("PUT /mockserver/expectation HTTP/1.1\r\n"
                + "Host: localhost:" + server.getPort() + "\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            outputStream.write(body);
            outputStream.flush();
            String statusLine = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            if (statusLine == null) {
                return TLS_REJECTED;
            }
            return statusLine.split(" ")[1];
        } catch (IOException tlsFailure) {
            return TLS_REJECTED;
        }
    }


    /**
     * Presents the client certificate followed by any appended certificates, signing with the client key only.
     */
    private static final class PresentedChainKeyManager extends X509ExtendedKeyManager {
        private static final String ALIAS = "client";
        private final PrivateKey privateKey;
        private final X509Certificate[] chain;

        private PresentedChainKeyManager(TestCertificates.Issued client, X509Certificate... appended) {
            this.privateKey = client.keyPair.getPrivate();
            this.chain = new X509Certificate[appended.length + 1];
            this.chain[0] = client.certificate;
            System.arraycopy(appended, 0, this.chain, 1, appended.length);
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return ALIAS;
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            return ALIAS;
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return new String[]{ALIAS};
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return chain.clone();
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return privateKey;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return new String[0];
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return null;
        }
    }
}
