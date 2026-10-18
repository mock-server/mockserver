package org.mockserver.lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.After;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.drift.DriftStore;
import org.mockserver.model.HttpRequest;
import org.mockserver.netty.MockServer;
import org.mockserver.serialization.ObjectMapperFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Behavioural proof that {@code driftAlertWebhookEnabled} and {@code driftSemanticAnalysisEnabled}, set on the
 * {@link Configuration} instance a server starts with, gate what {@link LifeCycle} wires into the drift pipeline.
 * Each test drives real drift (a forward whose upstream answer differs from a stub for the same request) and
 * observes the outcome from outside: a webhook POST reaching a sink server, and LLM-assigned severities on the
 * records served by {@code GET /mockserver/drift}. Only the flag differs between the on and off cases.
 *
 * <p>The notifier and analyzer behind these flags are process-wide, so the last tests pin down how the settings of
 * servers in one JVM interact: a stopped server's settings must not reach the next server, and a server that turns
 * nothing on must not switch off a running server's.
 */
public class LifeCycleDriftWiringTest {

    private static final String STUB_ID = "drift-wiring-stub";
    private static final String DRIFT_PATH = "/drift-wiring";
    private static final String WEBHOOK_PATH = "/drift-webhook";
    private static final String LLM_PATH = "/v1/chat/completions";
    private static final long DEADLINE_MILLIS = 15_000;
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();

    private final List<MockServer> servers = new ArrayList<>();

    @After
    public void stopServers() {
        for (MockServer server : servers) {
            server.stop();
        }
        DriftStore.getInstance().clear();
    }

    @Test
    public void shouldPostDriftAlertToTheWebhookWhenDriftAlertWebhookEnabled() throws Exception {
        MockServer sink = start(configuration());

        MockServer underTest = start(configuration().driftAlertWebhookEnabled(true).driftAlertWebhookUrl(webhookUrl(sink)));
        driveStatusDrift(underTest, STUB_ID);

        HttpRequest[] alerts = awaitAtLeastOne(clientFor(sink), request().withMethod("POST").withPath(WEBHOOK_PATH));
        assertThat(alerts[0].getBodyAsString(), containsString("\"event\" : \"mockserver.drift.alert\""));
        assertThat(alerts[0].getBodyAsString(), containsString(STUB_ID));
    }

    @Test
    public void shouldNotPostDriftAlertToTheWebhookWhenDriftAlertWebhookDisabled() throws Exception {
        MockServer sink = start(configuration());

        MockServer underTest = start(configuration().driftAlertWebhookEnabled(false).driftAlertWebhookUrl(webhookUrl(sink)));
        driveStatusDrift(underTest, STUB_ID);

        // the drift was stored (so the notifier was consulted); give a fire-and-forget POST time to land
        Thread.sleep(2_000);
        assertThat(clientFor(sink).retrieveRecordedRequests(request().withPath(WEBHOOK_PATH)).length, is(0));
    }

    @Test
    public void shouldClassifyDriftWithTheLlmWhenDriftSemanticAnalysisEnabled() throws Exception {
        MockServerClient llm = startLlm();

        MockServer underTest = start(llmBackedConfiguration(llm).driftSemanticAnalysisEnabled(true));
        JsonNode drifts = driveStatusDrift(underTest, STUB_ID);

        for (JsonNode drift : drifts) {
            assertThat(drift.toString(), drift.path("semanticSeverity").asText(), is("INFORMATIONAL"));
            assertThat(drift.toString(), drift.path("semanticExplanation").asText(), is("classified by the test LLM"));
        }
        assertThat(llm.retrieveRecordedRequests(request().withPath(LLM_PATH)).length, greaterThan(0));
    }

    @Test
    public void shouldNotClassifyDriftWhenDriftSemanticAnalysisDisabled() throws Exception {
        MockServerClient llm = startLlm();

        MockServer underTest = start(llmBackedConfiguration(llm).driftSemanticAnalysisEnabled(false));
        JsonNode drifts = driveStatusDrift(underTest, STUB_ID);

        // enrichment runs before a record is stored, so a stored record is final
        for (JsonNode drift : drifts) {
            assertThat(drift.toString(), drift.has("semanticSeverity"), is(false));
        }
        assertThat(llm.retrieveRecordedRequests(request().withPath(LLM_PATH)).length, is(0));
    }

    @Test
    public void shouldNotCarryAStoppedServersDriftSettingsIntoTheNextServer() throws Exception {
        MockServer sink = start(configuration());
        MockServerClient llm = startLlm();
        MockServer first = start(bothOn(llm, sink));
        driveStatusDrift(first, "first-server-stub");
        awaitAtLeastOne(clientFor(sink), request().withMethod("POST").withPath(WEBHOOK_PATH));
        int alertsToFirst = awaitSettledCount(clientFor(sink), request().withPath(WEBHOOK_PATH));
        int llmCallsByFirst = llm.retrieveRecordedRequests(request().withPath(LLM_PATH)).length;
        assertThat(llmCallsByFirst, greaterThan(0));
        stop(first);

        MockServer second = start(llmBackedConfiguration(llm).driftSemanticAnalysisEnabled(false)
            .driftAlertWebhookEnabled(false).driftAlertWebhookUrl(webhookUrl(sink)));
        JsonNode drifts = driveStatusDrift(second, "second-server-stub");

        for (JsonNode drift : drifts) {
            assertThat(drift.toString(), drift.has("semanticSeverity"), is(false));
        }
        assertThat(llm.retrieveRecordedRequests(request().withPath(LLM_PATH)).length, is(llmCallsByFirst));
        Thread.sleep(2_000);
        assertThat(clientFor(sink).retrieveRecordedRequests(request().withPath(WEBHOOK_PATH)).length, is(alertsToFirst));
    }

    @Test
    public void shouldKeepARunningServersDriftSettingsWhileAServerThatTurnsNothingOnStartsAndStops() throws Exception {
        MockServer sink = start(configuration());
        MockServerClient llm = startLlm();
        MockServer running = start(bothOn(llm, sink));
        stop(start(configuration()));

        JsonNode drifts = driveStatusDrift(running, STUB_ID);

        for (JsonNode drift : drifts) {
            assertThat(drift.toString(), drift.path("semanticSeverity").asText(), is("INFORMATIONAL"));
        }
        awaitAtLeastOne(clientFor(sink), request().withMethod("POST").withPath(WEBHOOK_PATH));
    }

    @Test
    public void shouldFallBackToTheEarlierRunningServersWebhookWhenTheLaterOneStops() throws Exception {
        MockServer earlierSink = start(configuration());
        MockServer laterSink = start(configuration());
        MockServer earlier = start(configuration().driftAlertWebhookEnabled(true).driftAlertWebhookUrl(webhookUrl(earlierSink)));
        stop(start(configuration().driftAlertWebhookEnabled(true).driftAlertWebhookUrl(webhookUrl(laterSink))));

        driveStatusDrift(earlier, STUB_ID);

        awaitAtLeastOne(clientFor(earlierSink), request().withMethod("POST").withPath(WEBHOOK_PATH));
        assertThat(clientFor(laterSink).retrieveRecordedRequests(request().withPath(WEBHOOK_PATH)).length, is(0));
    }

    /** Both flags on; alerts at every severity, as the test LLM classifies each drift INFORMATIONAL. */
    private static Configuration bothOn(MockServerClient llm, MockServer sink) {
        return llmBackedConfiguration(llm).driftSemanticAnalysisEnabled(true)
            .driftAlertWebhookEnabled(true).driftAlertWebhookUrl(webhookUrl(sink)).driftAlertSeverityThreshold("INFORMATIONAL");
    }

    /**
     * Forwards {@link #DRIFT_PATH} to an upstream answering 500 while a lower-priority stub for the same request
     * answers 200, sends one request, and waits for the resulting drift records.
     */
    private JsonNode driveStatusDrift(MockServer underTest, String stubId) throws Exception {
        MockServer upstream = start(configuration());
        clientFor(upstream).when(request().withPath(DRIFT_PATH)).respond(response().withStatusCode(500));

        MockServerClient client = clientFor(underTest);
        client.when(request().withPath(DRIFT_PATH), Times.unlimited(), TimeToLive.unlimited(), 10)
            .forward(forward().withHost("localhost").withPort(upstream.getLocalPort()));
        client.when(request().withPath(DRIFT_PATH), Times.unlimited(), TimeToLive.unlimited(), 0)
            .withId(stubId)
            .respond(response().withStatusCode(200));

        assertThat(get(underTest, DRIFT_PATH).statusCode(), is(500));

        long deadline = System.currentTimeMillis() + DEADLINE_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            JsonNode drifts = ObjectMapperFactory.createObjectMapper()
                .readTree(get(underTest, "/mockserver/drift?expectationId=" + stubId).body()).path("drifts");
            if (drifts.size() > 0) {
                assertThat(drifts.toString(), containsString("\"STATUS\""));
                return drifts;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("no drift was recorded for " + stubId);
    }

    private MockServerClient startLlm() {
        MockServerClient llm = clientFor(start(configuration()));
        llm.when(request().withMethod("POST").withPath(LLM_PATH))
            .respond(response().withStatusCode(200).withHeader("Content-Type", "application/json")
                .withBody("{\"model\":\"gpt-4o-mini\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                    + classification(8) + "\"},\"finish_reason\":\"stop\"}]}"));
        return llm;
    }

    /** One {@code <n>|INFORMATIONAL|...} line per possible drift record, JSON-escaped. */
    private static String classification(int lines) {
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            content.append(i).append("|INFORMATIONAL|classified by the test LLM\\n");
        }
        return content.toString();
    }

    private static Configuration llmBackedConfiguration(MockServerClient llm) {
        return configuration().llmProvider("openai").llmBaseUrl("http://localhost:" + llm.getPort())
            .llmApiKey("drift-wiring-key").llmRequestTimeoutMillis(10_000L);
    }

    private static String webhookUrl(MockServer sink) {
        return "http://localhost:" + sink.getLocalPort() + WEBHOOK_PATH;
    }

    private MockServer start(Configuration configuration) {
        MockServer server = new MockServer(configuration);
        servers.add(server);
        return server;
    }

    private void stop(MockServer server) {
        server.stop();
        servers.remove(server);
    }

    private static MockServerClient clientFor(MockServer server) {
        return new MockServerClient("localhost", server.getLocalPort());
    }

    private static HttpRequest[] awaitAtLeastOne(MockServerClient client, HttpRequest matcher) throws InterruptedException {
        long deadline = System.currentTimeMillis() + DEADLINE_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            HttpRequest[] received = client.retrieveRecordedRequests(matcher);
            if (received.length > 0) {
                return received;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("nothing matching " + matcher + " was received");
    }

    /** The number of matching requests once it has stopped changing for a second, as alerts are fire-and-forget. */
    private static int awaitSettledCount(MockServerClient client, HttpRequest matcher) throws InterruptedException {
        long deadline = System.currentTimeMillis() + DEADLINE_MILLIS;
        int count = client.retrieveRecordedRequests(matcher).length;
        long stableSince = System.currentTimeMillis();
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            int latest = client.retrieveRecordedRequests(matcher).length;
            if (latest != count) {
                count = latest;
                stableSince = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - stableSince >= 1_000) {
                return count;
            }
        }
        throw new AssertionError("the number of requests matching " + matcher + " did not settle");
    }

    private static java.net.http.HttpResponse<String> get(MockServer server, String pathAndQuery) throws Exception {
        return HTTP.send(java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + server.getLocalPort() + pathAndQuery))
            .timeout(Duration.ofSeconds(30)).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }
}
