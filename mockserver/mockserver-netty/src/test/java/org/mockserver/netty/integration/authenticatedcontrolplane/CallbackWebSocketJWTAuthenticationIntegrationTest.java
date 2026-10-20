package org.mockserver.netty.integration.authenticatedcontrolplane;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.authentication.jwt.JWKGenerator;
import org.mockserver.authentication.jwt.JWTGenerator;
import org.mockserver.client.ClientException;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.keys.AsymmetricKeyGenerator;
import org.mockserver.keys.AsymmetricKeyPair;
import org.mockserver.keys.AsymmetricKeyPairAlgorithm;
import org.mockserver.test.TempFileWriter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * With control-plane JWT authentication on, the callback WebSocket upgrade must carry the JWT:
 * MockServerClient sends it on the callback and breakpoint WebSockets as it does on its HTTP requests.
 * The server pins its settings on its own Configuration.
 */
public class CallbackWebSocketJWTAuthenticationIntegrationTest {

    private static ClientAndServer server;
    private static String jwt;

    @BeforeClass
    public static void startServer() {
        AsymmetricKeyPair keyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(keyPair));
        jwt = new JWTGenerator(keyPair).generateJWT();
        server = ClientAndServer.startClientAndServer(
            configuration()
                .controlPlaneJWTAuthenticationRequired(true)
                .controlPlaneJWTAuthenticationJWKSource(jwkFile)
        );
        server.withControlPlaneJWT(() -> jwt);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(server);
    }

    @Test
    public void callbackWebSocketShouldSendTheControlPlaneJWT() throws Exception {
        server.when(request("/jwt-callback")).respond(httpRequest -> response().withBody("from-callback"));

        HttpResponse<String> response = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/jwt-callback")).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

        assertThat(response.body(), is("from-callback"));
    }

    @Test
    public void breakpointWebSocketShouldSendTheControlPlaneJWT() {
        String breakpointId = server.addBreakpoint(request("/jwt-breakpoint"), httpRequest -> null);
        try {
            assertThat(breakpointId, not(emptyOrNullString()));
        } finally {
            server.removeBreakpointMatcher(breakpointId);
        }
    }

    @Test
    public void callbackWebSocketShouldBeRefusedWithoutTheControlPlaneJWT() {
        ClientOnly withoutJwt = new ClientOnly(server.getPort());
        try {
            ClientException refused = assertThrows(ClientException.class, () -> withoutJwt.when(request("/no-jwt-callback")).respond(httpRequest -> response()));

            assertThat("refused at the WebSocket upgrade, before any expectation is sent",
                refused.getMessage(), allOf(containsString("handshake failure"), containsString("401")));
        } finally {
            withoutJwt.release();
        }
    }

    /**
     * A client whose clean-up releases only its own event loops: MockServerClient.stop() would stop the
     * shared server it points at.
     */
    private static class ClientOnly extends MockServerClient {
        ClientOnly(int port) {
            super(configuration(), "localhost", port);
        }

        void release() {
            releaseEventLoops();
        }
    }
}
