package org.mockserver.netty.integration.mock;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.ClientException;
import org.mockserver.client.MockServerClient;
import org.mockserver.client.RequestExplanation;
import org.mockserver.log.model.LogEntry;
import org.mockserver.netty.MockServer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * End to end: a real request is served by a low-priority expectation, and {@code PUT /mockserver/explain} with that
 * request's correlation id names it and explains why the higher-priority expectation lost.
 */
public class ExplainRequestIntegrationTest {

    private static MockServer mockServer;
    private static MockServerClient client;
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeClass
    public static void startServer() {
        mockServer = new MockServer(configuration().logLevel(Level.INFO));
        client = new MockServerClient("localhost", mockServer.getLocalPort());
    }

    @AfterClass
    public static void stopServer() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Test
    public void shouldExplainWhyAHigherPriorityExpectationLostForARealRequest() throws Exception {
        client.when(request().withMethod("GET").withPath("/explain-it/orders").withHeader("X-Tenant", "gold"), org.mockserver.matchers.Times.unlimited(), org.mockserver.matchers.TimeToLive.unlimited(), 10)
            .withId("gold-tenant")
            .respond(response().withStatusCode(201));
        client.when(request().withMethod("GET").withPath("/explain-it/orders"))
            .withId("catch-all")
            .respond(response().withStatusCode(200));

        java.net.http.HttpResponse<String> served = HTTP.send(java.net.http.HttpRequest.newBuilder(URI.create(base() + "/explain-it/orders"))
            .header("X-Tenant", "silver").GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(served.statusCode(), is(200));

        String correlationId = correlationIdOf("/explain-it/orders");
        JsonNode result = explain(correlationId);

        assertThat(result.path("matchedExpectation").path("expectationId").asText(), is("catch-all"));
        assertThat(result.path("atArrival").path("outcome").asText(), is("MATCHED"));
        assertThat(result.path("atArrival").path("expectationId").asText(), is("catch-all"));
        assertThat(result.path("sameOutcomeAsAtArrival").asBoolean(), is(true));
        JsonNode loser = result.path("skippedExpectations").get(0);
        assertThat(loser.path("expectationId").asText(), is("gold-tenant"));
        assertThat(loser.path("reason").asText(), is("FIELDS_DIFFER"));
        assertThat(loser.path("explanation").asText(), containsString("headers"));
    }

    @Test
    public void shouldExplainThroughTheTypedJavaClient() throws Exception {
        // priorities below the other tests' highest (10) keep this server-wide state out of their first skipped entry
        client.when(request().withMethod("GET").withPath("/explain-typed/orders").withHeader("X-Tenant", "gold"), org.mockserver.matchers.Times.unlimited(), org.mockserver.matchers.TimeToLive.unlimited(), 7)
            .withId("typed-gold-tenant")
            .respond(response().withStatusCode(201));
        client.when(request().withMethod("GET").withPath("/explain-typed/orders"), org.mockserver.matchers.Times.unlimited(), org.mockserver.matchers.TimeToLive.unlimited(), 5)
            .withId("typed-catch-all")
            .respond(response().withStatusCode(200));
        client.when(request().withPath("/explain-typed/orders"))
            .withId("typed-shadowed")
            .respond(response().withStatusCode(202));

        java.net.http.HttpResponse<String> served = HTTP.send(java.net.http.HttpRequest.newBuilder(URI.create(base() + "/explain-typed/orders"))
            .header("X-Tenant", "silver").GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(served.statusCode(), is(200));

        RequestExplanation explanation = client.explain(correlationIdOf("/explain-typed/orders"));

        assertThat(explanation.request().path(), is("/explain-typed/orders"));
        assertThat(explanation.atArrival().outcome(), is(RequestExplanation.Outcome.MATCHED));
        assertThat(explanation.atArrival().expectationId(), is("typed-catch-all"));
        assertThat(explanation.sameOutcomeAsAtArrival(), is(true));
        assertThat(explanation.matchedExpectation().expectationId(), is("typed-catch-all"));
        assertThat(explanation.matchedExpectation().reason(), is(nullValue()));
        RequestExplanation.ExplainedExpectation loser = explanation.skippedExpectations().stream()
            .filter(skipped -> "typed-gold-tenant".equals(skipped.expectationId())).findFirst().orElseThrow();
        assertThat(loser.reason(), is(RequestExplanation.Reason.FIELDS_DIFFER));
        assertThat(loser.priority(), is(7));
        assertThat(loser.differences().containsKey("headers"), is(true));
        RequestExplanation.ExplainedExpectation shadowed = explanation.shadowedExpectations().stream()
            .filter(candidate -> "typed-shadowed".equals(candidate.expectationId())).findFirst().orElseThrow();
        assertThat(shadowed.reason(), is(RequestExplanation.Reason.LOWER_PRIORITY));
        assertThat(explanation.evaluatedExpectations(), is(explanation.totalExpectations()));
        assertThat(explanation.note(), containsString("recomputed"));
    }

    @Test
    public void shouldRaiseTheServersReasonThroughTheJavaClientForAnUnknownCorrelationId() {
        ClientException failure = assertThrows(ClientException.class, () -> client.explain("never-recorded-typed"));

        assertThat(failure.getMessage(), containsString("no request is recorded"));
    }

    @Test
    public void shouldAnswer404ForACorrelationIdThatWasNeverRecorded() throws Exception {
        java.net.http.HttpResponse<String> response = put("/mockserver/explain?correlationId=never-recorded");

        assertThat(response.statusCode(), is(404));
        assertThat(response.body(), containsString("no request is recorded"));
    }

    @Test
    public void shouldNotTreatABarePathAsTheControlPlaneRoute() throws Exception {
        client.when(request().withMethod("PUT").withPath("/explain")).respond(response().withStatusCode(299));

        assertThat(put("/explain?correlationId=anything").statusCode(), is(299));
    }

    private static String base() {
        return "http://localhost:" + mockServer.getLocalPort();
    }

    private static java.net.http.HttpResponse<String> put(String pathAndQuery) throws Exception {
        return HTTP.send(java.net.http.HttpRequest.newBuilder(URI.create(base() + pathAndQuery))
            .PUT(java.net.http.HttpRequest.BodyPublishers.noBody()).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private static String correlationIdOf(String path) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            LogEntry[] entries = client.retrieveLogEntries(request().withPath(path));
            String correlationId = Arrays.stream(entries)
                .filter(entry -> entry.getType() == LogEntry.LogMessageType.EXPECTATION_RESPONSE)
                .map(LogEntry::getCorrelationId)
                .findFirst().orElse(null);
            if (correlationId != null) {
                return correlationId;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("no EXPECTATION_RESPONSE entry was recorded for " + path);
    }

    private static JsonNode explain(String correlationId) throws Exception {
        java.net.http.HttpResponse<String> response = put("/mockserver/explain?correlationId=" + correlationId);
        assertThat(response.body(), response.statusCode(), is(200));
        return ObjectMapperFactory.createObjectMapper().readTree(response.body());
    }
}
