package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.GraphQLBody.graphQL;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.Jwt.jwt;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * The GraphQL body matcher, the JWT-claims matcher and numeric-comparison header values, registered over the
 * REST API — once through {@link MockServerClient} and once as hand-written JSON to
 * {@code PUT /mockserver/expectation} — and then discriminated over a real socket: a matching request is
 * served 200 and a non-matching one falls through to 404. This exercises the expectation JSON codec and the
 * netty decode path end to end, which in-memory {@code RequestMatchers} tests do not.
 */
public class GraphQLJwtNumericMatcherOverTheWireIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

    private static MockServer mockServer;
    private static MockServerClient client;

    @BeforeClass
    public static void startServer() {
        mockServer = new MockServer();
        client = new MockServerClient("localhost", mockServer.getLocalPort());
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServer);
    }

    @Before
    public void reset() {
        client.reset();
    }

    // ---- GraphQL ----

    @Test
    public void shouldMatchGraphQLBodyRegisteredThroughTheClient() throws Exception {
        client.when(request().withMethod("POST").withPath("/graphql").withBody(graphQL("{ hero { name } }")))
            .respond(response().withBody("hero"));

        assertGraphQLDiscriminated();
    }

    @Test
    public void shouldMatchGraphQLBodyRegisteredAsRawJson() throws Exception {
        putExpectation("{"
            + "\"httpRequest\":{\"method\":\"POST\",\"path\":\"/graphql\",\"body\":{\"type\":\"GRAPHQL\",\"query\":\"{ hero { name } }\"}},"
            + "\"httpResponse\":{\"body\":\"hero\"}"
            + "}");

        assertGraphQLDiscriminated();
    }

    private void assertGraphQLDiscriminated() throws Exception {
        HttpResponse<String> matching = post("/graphql", "{\"query\":\"{ hero { name } }\"}");
        assertThat(matching.body(), matching.statusCode(), is(200));
        assertThat(matching.body(), is("hero"));
        assertThat(post("/graphql", "{\"query\":\"{ villain { name } }\"}").statusCode(), is(404));
    }

    // ---- JWT claims ----

    @Test
    public void shouldMatchJwtClaimsRegisteredThroughTheClient() throws Exception {
        client.when(request().withPath("/admin").withJwt(jwt().withClaim("scope", ".*admin.*")))
            .respond(response().withBody("admin"));

        assertJwtDiscriminated();
    }

    @Test
    public void shouldMatchJwtClaimsRegisteredAsRawJson() throws Exception {
        putExpectation("{"
            + "\"httpRequest\":{\"path\":\"/admin\",\"jwt\":{\"claims\":{\"scope\":\".*admin.*\"}}},"
            + "\"httpResponse\":{\"body\":\"admin\"}"
            + "}");

        assertJwtDiscriminated();
    }

    private void assertJwtDiscriminated() throws Exception {
        HttpResponse<String> matching = get("/admin", "Authorization", "Bearer " + token("{\"scope\":\"read admin\"}"));
        assertThat(matching.body(), matching.statusCode(), is(200));
        assertThat(matching.body(), is("admin"));
        assertThat(get("/admin", "Authorization", "Bearer " + token("{\"scope\":\"read\"}")).statusCode(), is(404));
    }

    // ---- numeric comparison ----

    @Test
    public void shouldMatchNumericComparisonRegisteredThroughTheClient() throws Exception {
        client.when(request().withPath("/age").withHeader("age", "> 60"))
            .respond(response().withBody("senior"));

        assertNumericComparisonDiscriminated();
    }

    @Test
    public void shouldMatchNumericComparisonRegisteredAsRawJson() throws Exception {
        putExpectation("{"
            + "\"httpRequest\":{\"path\":\"/age\",\"headers\":{\"age\":[\"> 60\"]}},"
            + "\"httpResponse\":{\"body\":\"senior\"}"
            + "}");

        assertNumericComparisonDiscriminated();
    }

    private void assertNumericComparisonDiscriminated() throws Exception {
        HttpResponse<String> matching = get("/age", "age", "70");
        assertThat(matching.body(), matching.statusCode(), is(200));
        assertThat(matching.body(), is("senior"));
        assertThat(get("/age", "age", "50").statusCode(), is(404));
    }

    // ---- helpers ----

    private static void putExpectation(String json) throws Exception {
        HttpResponse<String> response = HTTP.send(java.net.http.HttpRequest.newBuilder(uri("/mockserver/expectation"))
            .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
            .PUT(BodyPublishers.ofString(json)).build(), BodyHandlers.ofString());
        assertThat(response.body(), response.statusCode(), is(201));
    }

    private static HttpResponse<String> post(String path, String json) throws Exception {
        return HTTP.send(java.net.http.HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json").POST(BodyPublishers.ofString(json)).build(), BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(String path, String headerName, String headerValue) throws Exception {
        return HTTP.send(java.net.http.HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30))
            .header(headerName, headerValue).GET().build(), BodyHandlers.ofString());
    }

    private static URI uri(String path) {
        return URI.create("http://localhost:" + mockServer.getLocalPort() + path);
    }

    private static String token(String payloadJson) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8))
            + "." + encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }
}
