package org.mockserver.netty.integration.tls.inbound;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.tls.DataPlaneMutualTlsConfigurationException;
import org.mockserver.socket.tls.PEMToFile;
import org.mockserver.test.TestCertificates;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.configuration.ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY;
import static org.mockserver.configuration.ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Data-plane mTLS must not accept a client certificate anyone can create with the bundled CA's published private key.
 * With control-plane authentication off, a client that passes data-plane mTLS can create expectations, so a
 * {@code 201} for such a certificate means the server is open to anyone who has the MockServer jar. Every server pins
 * its settings on its own Configuration so the global store cannot move them.
 */
public class DataPlaneMutualTlsBundledCaIntegrationTest {

    private static final String TLS_REJECTED = "tls-rejected";
    private static final String OWN_CA = "org/mockserver/netty/integration/tls/ca.pem";
    private static final String OWN_CA_PRIVATE_KEY = "org/mockserver/netty/integration/tls/ca-key-pkcs8.pem";
    private static final String CREATE_EXPECTATION = "{\"httpRequest\": {\"path\": \"/attacker\"}, \"httpResponse\": {\"statusCode\": 200}}";

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static TestCertificates.Issued trustedCa;
    private static TestCertificates.Issued trustedClient;
    private static TestCertificates.Issued attackerClient;
    private static String trustedCaChain;

    private final List<ClientAndServer> servers = new ArrayList<>();

    @BeforeClass
    public static void createCertificates() throws Exception {
        trustedCa = TestCertificates.certificateAuthority("Data Plane CA");
        trustedClient = TestCertificates.clientCertificate("trusted client", trustedCa);
        attackerClient = TestCertificates.clientCertificate("attacker", TestCertificates.issuer(
            PEMToFile.x509FromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE),
            PEMToFile.privateKeyFromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY)
        ));
        File chain = temporaryFolder.newFile("data-plane-ca.pem");
        Files.write(chain.toPath(), TestCertificates.toPem(trustedCa.certificate).getBytes(StandardCharsets.UTF_8));
        trustedCaChain = chain.getAbsolutePath();
    }

    @After
    public void stopServers() {
        servers.forEach(server -> stopQuietly(server));
    }

    @Test
    public void shouldRejectCertificateSignedWithBundledKeyWhenChainIsConfiguredAndMockServerCaIsTheBundledCa() throws Exception {
        ClientAndServer server = start(withBundledCa(dataPlaneMutualTls()).tlsMutualAuthenticationCertificateChain(trustedCaChain));

        assertThat(createExpectation(server, trustedClient), is("201"));
        assertThat(createExpectation(server, attackerClient), is(TLS_REJECTED));
    }

    @Test
    public void shouldRefuseToStartWithMutualTlsAndTheBundledCa() {
        DataPlaneMutualTlsConfigurationException exception = assertThrows(DataPlaneMutualTlsConfigurationException.class,
            () -> ClientAndServer.startClientAndServer(withBundledCa(dataPlaneMutualTls())));

        assertThat(exception.getMessage(), containsString("dynamicallyCreateCertificateAuthorityCertificate=true"));
    }

    @Test
    public void shouldRefuseToStartWithChainThatContainsTheBundledCa() throws Exception {
        File chain = temporaryFolder.newFile();
        Files.write(chain.toPath(), (TestCertificates.toPem(trustedCa.certificate) + "\n"
            + TestCertificates.toPem(PEMToFile.x509FromPEMFile(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE))).getBytes(StandardCharsets.UTF_8));

        assertThrows(DataPlaneMutualTlsConfigurationException.class,
            () -> ClientAndServer.startClientAndServer(withOwnCa(dataPlaneMutualTls()).tlsMutualAuthenticationCertificateChain(chain.getAbsolutePath())));
    }

    @Test
    public void shouldAcceptCertificateSignedWithBundledKeyOnlyWithTheOptIn() throws Exception {
        ClientAndServer server = start(withBundledCa(dataPlaneMutualTls()).tlsMutualAuthenticationAllowBundledCa(true));

        assertThat(createExpectation(server, attackerClient), is("201"));
    }

    @Test
    public void shouldTrustTheDynamicallyCreatedCaAndNotTheBundledCa() throws Exception {
        Configuration configuration = dataPlaneMutualTls()
            .dynamicallyCreateCertificateAuthorityCertificate(true)
            .directoryToSaveDynamicSSLCertificate(temporaryFolder.newFolder().getAbsolutePath());
        ClientAndServer server = start(configuration);

        // the first handshake creates the CA
        assertThat(createExpectation(server, attackerClient), is(TLS_REJECTED));
        TestCertificates.Issued dynamicCa = TestCertificates.issuer(
            PEMToFile.x509FromPEMFile(configuration.certificateAuthorityCertificate()),
            PEMToFile.privateKeyFromPEMFile(configuration.certificateAuthorityPrivateKey())
        );
        assertThat(createExpectation(server, TestCertificates.clientCertificate("dynamic CA client", dynamicCa)), is("201"));
    }

    @Test
    public void shouldTrustACustomCaAndNotTheBundledCa() throws Exception {
        ClientAndServer server = start(withOwnCa(dataPlaneMutualTls()));
        TestCertificates.Issued ownCa = TestCertificates.issuer(PEMToFile.x509FromPEMFile(OWN_CA), PEMToFile.privateKeyFromPEMFile(OWN_CA_PRIVATE_KEY));

        assertThat(createExpectation(server, TestCertificates.clientCertificate("custom CA client", ownCa)), is("201"));
        assertThat(createExpectation(server, attackerClient), is(TLS_REJECTED));
    }

    @Test
    public void shouldRejectAConfigurationUpdateThatEnablesMutualTlsWithTheBundledCa() throws Exception {
        ClientAndServer server = start(withBundledCa(withoutControlPlaneAuthentication(configuration())).tlsMutualAuthenticationRequired(false));

        HttpResponse<String> response = sendConfiguration(server, "PUT", "{\"tlsMutualAuthenticationRequired\": true}");

        assertThat(response.statusCode(), is(400));
        assertThat(response.body(), containsString("tlsMutualAuthenticationAllowBundledCa=true"));
        assertThat("nothing in the refused update is applied", sendConfiguration(server, "GET", null).body(),
            matchesPattern("(?s).*\"tlsMutualAuthenticationRequired\"\\s*:\\s*false.*"));
    }

    private ClientAndServer start(Configuration configuration) {
        ClientAndServer server = ClientAndServer.startClientAndServer(configuration);
        servers.add(server);
        return server;
    }

    private static Configuration dataPlaneMutualTls() {
        return withoutControlPlaneAuthentication(configuration())
            .tlsMutualAuthenticationRequired(true)
            .tlsMutualAuthenticationCertificateChain("")
            .tlsMutualAuthenticationAllowBundledCa(false);
    }

    private static Configuration withoutControlPlaneAuthentication(Configuration configuration) {
        return configuration
            .controlPlaneTLSMutualAuthenticationRequired(false)
            .controlPlaneTLSMutualAuthenticationCAChain("")
            .controlPlaneJWTAuthenticationRequired(false)
            .controlPlaneOidcAuthenticationRequired(false);
    }

    private static Configuration withBundledCa(Configuration configuration) {
        return configuration
            .dynamicallyCreateCertificateAuthorityCertificate(false)
            .certificateAuthorityCertificate(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE)
            .certificateAuthorityPrivateKey(DEFAULT_CERTIFICATE_AUTHORITY_PRIVATE_KEY);
    }

    private static Configuration withOwnCa(Configuration configuration) {
        return configuration
            .dynamicallyCreateCertificateAuthorityCertificate(false)
            .certificateAuthorityCertificate(OWN_CA)
            .certificateAuthorityPrivateKey(OWN_CA_PRIVATE_KEY);
    }

    /**
     * @return the HTTP status code, or {@value #TLS_REJECTED} when the server ended the TLS session
     */
    private static String createExpectation(ClientAndServer server, TestCertificates.Issued client) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", client.keyPair.getPrivate(), new char[0], new X509Certificate[]{client.certificate});
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, new char[0]);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
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

    private static HttpResponse<String> sendConfiguration(ClientAndServer server, String method, String body) throws Exception {
        return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/mockserver/configuration"))
                    .timeout(Duration.ofSeconds(30))
                    .method(method, body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
    }
}
