package org.mockserver.netty.integration.authenticatedcontrolplane;

import org.junit.After;
import org.junit.Test;
import org.mockserver.authentication.ControlPlaneMutualTlsConfigurationException;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.netty.MockServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 * Control-plane mTLS with no controlPlaneTLSMutualAuthenticationCAChain while MockServer's CA is the bundled CA is
 * refused at start. A PUT /mockserver/configuration cannot enable it, because control-plane mTLS is fixed at startup.
 * Every server pins its settings on its own Configuration so the global store cannot move them.
 */
public class ControlPlaneMTLSStartRefusalIntegrationTest {

    private static final String CONTROL_PLANE_CA_CHAIN = "org/mockserver/netty/integration/tls/ca.pem";
    private static final String CONTROL_PLANE_CA_PRIVATE_KEY = "org/mockserver/netty/integration/tls/ca-key-pkcs8.pem";
    private static final String FIX = "set controlPlaneTLSMutualAuthenticationCAChain";

    private final List<ClientAndServer> servers = new ArrayList<>();

    @After
    public void stopServers() {
        servers.forEach(server -> stopQuietly(server));
    }

    @Test
    public void mockServerShouldRefuseToStartWithMutualTlsWithoutChainAndTheBundledCa() {
        ControlPlaneMutualTlsConfigurationException exception = assertThrows(ControlPlaneMutualTlsConfigurationException.class,
            () -> new MockServer(mutualTlsWithoutChain(withBundledCa(configuration()))));

        assertThat(exception.getMessage(), containsString(FIX));
    }

    @Test
    public void clientAndServerShouldRefuseToStartWithMutualTlsWithoutChainAndTheBundledCa() {
        ControlPlaneMutualTlsConfigurationException exception = assertThrows(ControlPlaneMutualTlsConfigurationException.class,
            () -> ClientAndServer.startClientAndServer(mutualTlsWithoutChain(withBundledCa(configuration()))));

        assertThat(exception.getMessage(), containsString(FIX));
    }

    @Test
    public void shouldStartWithMutualTlsWithoutChainWhenMockServerHasItsOwnCa() {
        ClientAndServer server = start(mutualTlsWithoutChain(configuration()
            .dynamicallyCreateCertificateAuthorityCertificate(false)
            .certificateAuthorityCertificate(CONTROL_PLANE_CA_CHAIN)
            .certificateAuthorityPrivateKey(CONTROL_PLANE_CA_PRIVATE_KEY)));

        assertThat(server.hasStarted(), is(true));
    }

    @Test
    public void shouldStartWithMutualTlsAndChainWhenMockServerCaIsTheBundledCa() {
        ClientAndServer server = start(withBundledCa(configuration())
            .controlPlaneTLSMutualAuthenticationRequired(true)
            .controlPlaneTLSMutualAuthenticationCAChain(CONTROL_PLANE_CA_CHAIN));

        assertThat(server.hasStarted(), is(true));
    }

    @Test
    public void shouldIgnoreAConfigurationUpdateThatEnablesMutualTls() throws Exception {
        ClientAndServer server = start(withoutControlPlaneAuthentication(withBundledCa(configuration())));

        // control-plane mTLS is fixed at startup, so the update is answered but neither applied nor refused
        for (String update : new String[]{
            "{\"controlPlaneTLSMutualAuthenticationRequired\": true}",
            "{\"controlPlaneTLSMutualAuthenticationRequired\": true, \"controlPlaneTLSMutualAuthenticationCAChain\": \"" + CONTROL_PLANE_CA_CHAIN + "\"}"
        }) {
            HttpResponse<String> response = putConfiguration(server, update);

            assertThat(update, response.statusCode(), is(200));
            assertThat(update, send(server, "GET", null).body(),
                matchesPattern("(?s).*\"controlPlaneTLSMutualAuthenticationRequired\"\\s*:\\s*false.*"));
        }
    }

    private ClientAndServer start(Configuration configuration) {
        ClientAndServer server = ClientAndServer.startClientAndServer(configuration);
        servers.add(server);
        return server;
    }

    private static Configuration mutualTlsWithoutChain(Configuration configuration) {
        return withoutControlPlaneAuthentication(configuration)
            .controlPlaneTLSMutualAuthenticationRequired(true)
            .controlPlaneTLSMutualAuthenticationCAChain("");
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

    private static HttpResponse<String> putConfiguration(ClientAndServer server, String body) throws Exception {
        return send(server, "PUT", body);
    }

    private static HttpResponse<String> send(ClientAndServer server, String method, String body) throws Exception {
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
