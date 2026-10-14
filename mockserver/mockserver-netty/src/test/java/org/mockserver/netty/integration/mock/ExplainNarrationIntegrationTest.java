package org.mockserver.netty.integration.mock;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.log.model.LogEntry;
import org.mockserver.model.HttpRequest;
import org.mockserver.netty.MockServer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * End to end: {@code PUT /mockserver/explain?narrate=true} on a MockServer configured with an OpenAI backend calls a
 * second MockServer standing in for the LLM, returns its summary labelled as AI-generated beside the deterministic
 * facts, and the prompt that reaches the LLM carries none of the recorded request's credentials.
 */
public class ExplainNarrationIntegrationTest {

    private static final String SUMMARY = "Expectation gold-only wants X-Tenant gold but the request sent silver, so the catch-all served it.";
    private static final String AUTHORIZATION_SECRET = "Bearer sk-it-narration-secret";
    private static final String PASSWORD_SECRET = "it-narration-password";

    private static MockServer llm;
    private static MockServerClient llmClient;
    private static MockServer mockServer;
    private static MockServerClient client;
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeClass
    public static void startServers() {
        llm = new MockServer(configuration().logLevel(Level.INFO));
        llmClient = new MockServerClient("localhost", llm.getLocalPort());
        llmClient.when(request().withMethod("POST").withPath("/v1/chat/completions"))
            .respond(response().withStatusCode(200).withHeader("Content-Type", "application/json")
                .withBody("{\"model\":\"gpt-4o-mini\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"" + SUMMARY + "\"},\"finish_reason\":\"stop\"}]}"));

        mockServer = new MockServer(configuration().logLevel(Level.INFO).redactSecretsInLog(false)
            .llmExplainNarrationEnabled(true).llmProvider("openai").llmBaseUrl("http://localhost:" + llm.getLocalPort()).llmApiKey("it-llm-key").llmRequestTimeoutMillis(10_000L));
        client = new MockServerClient("localhost", mockServer.getLocalPort());
    }

    @AfterClass
    public static void stopServers() {
        if (mockServer != null) {
            mockServer.stop();
        }
        if (llm != null) {
            llm.stop();
        }
    }

    @Test
    public void shouldNarrateAnExplanationFromTheConfiguredLlmWithoutSendingCredentials() throws Exception {
        client.when(request().withMethod("POST").withPath("/narrate-it/orders").withHeader("X-Tenant", "gold"), org.mockserver.matchers.Times.unlimited(), org.mockserver.matchers.TimeToLive.unlimited(), 10)
            .withId("gold-only")
            .respond(response().withStatusCode(201));
        client.when(request().withMethod("POST").withPath("/narrate-it/orders"))
            .withId("catch-all")
            .respond(response().withStatusCode(200));

        java.net.http.HttpResponse<String> served = HTTP.send(java.net.http.HttpRequest.newBuilder(URI.create(base() + "/narrate-it/orders"))
                .header("X-Tenant", "silver").header("Authorization", AUTHORIZATION_SECRET)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"password\":\"" + PASSWORD_SECRET + "\"}")).build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(served.statusCode(), is(200));
        String correlationId = correlationIdOf("/narrate-it/orders");

        java.net.http.HttpResponse<String> plain = put("/mockserver/explain?correlationId=" + correlationId);
        java.net.http.HttpResponse<String> narrated = put("/mockserver/explain?narrate=true&correlationId=" + correlationId);

        assertThat(narrated.body(), narrated.statusCode(), is(200));
        JsonNode result = ObjectMapperFactory.createObjectMapper().readTree(narrated.body());
        assertThat(result.path("narration").path("available").asBoolean(), is(true));
        assertThat(result.path("narration").path("aiGenerated").asBoolean(), is(true));
        assertThat(result.path("narration").path("label").asText(), containsString("AI-generated"));
        assertThat(result.path("narration").path("text").asText(), is(SUMMARY));
        assertThat(result.path("matchedExpectation").path("expectationId").asText(), is("catch-all"));
        assertThat(result.path("skippedExpectations").get(0).path("expectationId").asText(), is("gold-only"));
        assertThat(plain.body(), not(containsString("\"narration\"")));

        HttpRequest[] llmCalls = llmClient.retrieveRecordedRequests(request().withPath("/v1/chat/completions"));
        assertThat(llmCalls.length, is(1));
        String prompt = ObjectMapperFactory.createObjectMapper().readTree(llmCalls[0].getBodyAsString()).path("messages").get(0).path("content").asText();
        assertThat(prompt, containsString("gold-only"));
        assertThat(prompt, not(containsString("sk-it-narration-secret")));
        assertThat(prompt, not(containsString(PASSWORD_SECRET)));
    }

    private static String base() {
        return "http://localhost:" + mockServer.getLocalPort();
    }

    private static java.net.http.HttpResponse<String> put(String pathAndQuery) throws Exception {
        return HTTP.send(java.net.http.HttpRequest.newBuilder(URI.create(base() + pathAndQuery)).timeout(Duration.ofSeconds(60))
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
}
