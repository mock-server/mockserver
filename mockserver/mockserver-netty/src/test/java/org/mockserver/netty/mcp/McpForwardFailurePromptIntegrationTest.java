package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.socket.PortFactory;
import org.slf4j.event.Level;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Follows the why_forward_failed prompt's tool chain over MockServer's own MCP endpoint after a real forward to a port
 * nothing listens on, and checks that each tool returns the fact the prompt says it does: the failed forward with its
 * 502 reason and correlationId, that request's lifecycle, and the forward expectation's target; and, when nothing
 * failed to forward, the response MockServer sent instead.
 */
public class McpForwardFailurePromptIntegrationTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.buildObjectMapperWithoutRemovingEmptyValues();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private ClientAndServer mockServer;
    private String sessionId;
    private int requestId;

    @Before
    public void startMockServer() throws Exception {
        // INFO, the default outside tests: below it MockServer records no forward failure for these tools to return
        mockServer = ClientAndServer.startClientAndServer(configuration().logLevel(Level.INFO), 0);
        HttpResponse<String> initialize = postMcp("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}");
        sessionId = initialize.headers().firstValue("Mcp-Session-Id").orElseThrow();
        postMcp("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
    }

    @After
    public void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Test
    public void eachToolTheForwardPromptNamesReturnsTheFactItDescribes() throws Exception {
        int closedPort = PortFactory.findFreePort();
        JsonNode created = callTool("create_forward_expectation", objectMapper.createObjectNode()
            .put("path", "/inventory").put("host", "127.0.0.1").put("port", closedPort).put("scheme", "HTTP"));
        assertThat(created.toString(), created.path("status").asText(), is("created"));

        HttpResponse<String> forwarded = send("/inventory");
        assertThat(forwarded.statusCode(), is(502));

        JsonNode failure = null;
        long deadline = System.currentTimeMillis() + 20_000;
        while (failure == null && System.currentTimeMillis() < deadline) {
            for (JsonNode candidate : callTool("explain_forward_failure", objectMapper.createObjectNode().put("limit", 20)).path("forwardFailures")) {
                if (candidate.path("method").asText().equals("GET") && candidate.path("path").asText().equals("/inventory")) {
                    failure = candidate;
                    break;
                }
            }
            if (failure == null) {
                Thread.sleep(100);
            }
        }
        assertThat("a failed forward of GET /inventory from explain_forward_failure", failure, notNullValue());
        assertThat(failure.toString(), failure.path("statusCode").asInt(), is(502));
        assertThat(failure.path("reason").asText(), containsString("upstream refused the connection"));
        assertThat(failure.path("reason").asText(), is(forwarded.body()));
        assertThat(failure.path("causeNamed").asBoolean(), is(true));
        assertThat(failure.path("forwardLoopSuspected").asBoolean(), is(false));

        String correlationId = failure.path("correlationId").asText();
        assertThat(failure.toString(), correlationId.isEmpty(), is(false));
        JsonNode byId = callTool("explain_forward_failure", objectMapper.createObjectNode().put("correlationId", correlationId));
        assertThat(byId.path("forwardFailures").get(0).path("reason").asText(), is(failure.path("reason").asText()));
        JsonNode lifecycle = callTool("retrieve_logs", objectMapper.createObjectNode().put("correlationId", correlationId)).path("logEntries");
        assertThat(lifecycle.toString(), lifecycle.size() >= 1, is(true));
        for (JsonNode entry : lifecycle) {
            assertThat(entry.toString(), entry.path("correlationId").asText(), is(correlationId));
        }

        JsonNode expectations = callTool("list_expectations", objectMapper.createObjectNode().put("path", "/inventory")).path("expectations");
        assertThat(expectations.toString(), expectations.size(), is(1));
        JsonNode target = expectations.get(0).path("httpForward");
        assertThat(target.toString(), target.path("host").asText(), is("127.0.0.1"));
        assertThat(target.path("port").asInt(), is(closedPort));
        assertThat(target.path("scheme").asText(), is("HTTP"));
    }

    @Test
    public void whenNoForwardFailedTheFallbackShowsTheResponseMockServerSent() throws Exception {
        callTool("create_expectation", objectMapper.createObjectNode().put("method", "GET").put("path", "/stock").put("statusCode", 200));
        assertThat(send("/stock").statusCode(), is(200));

        JsonNode pair = null;
        long deadline = System.currentTimeMillis() + 20_000;
        while (pair == null && System.currentTimeMillis() < deadline) {
            JsonNode pairs = callTool("retrieve_request_responses", objectMapper.createObjectNode()
                .put("method", "GET").put("path", "/stock")).path("requestResponses");
            if (pairs.size() > 0) {
                pair = pairs.get(0);
            } else {
                Thread.sleep(100);
            }
        }
        assertThat("the response for GET /stock from retrieve_request_responses", pair, notNullValue());
        assertThat(pair.toString(), pair.path("httpResponse").path("statusCode").asInt(), is(200));

        JsonNode failures = callTool("explain_forward_failure", objectMapper.createObjectNode().put("limit", 20));
        assertThat(failures.toString(), failures.path("forwardFailureCount").asInt(), is(0));
        assertThat(failures.path("note").asText(), containsString("no failed forward is recorded"));
    }

    private HttpResponse<String> send(String path) throws Exception {
        return httpClient.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + mockServer.getLocalPort() + path))
                .timeout(Duration.ofSeconds(60)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void aRequestWhoseForwardFailedIsNotExplainedAsUnmatchedButAGenuineMissStillIs() throws Exception {
        int closedPort = PortFactory.findFreePort();
        mockServer.when(request().withMethod("GET").withPath("/stock"))
            .forward(forward().withHost("127.0.0.1").withPort(closedPort));
        mockServer.when(request().withMethod("GET").withPath("/stock").withHeader("X-Tenant", "blue"))
            .respond(response().withBody("blue stock"));
        assertThat(get("/orders").statusCode(), is(404));
        assertThat(get("/stock").statusCode(), is(502));

        JsonNode newest = callTool("explain_unmatched_requests", objectMapper.createObjectNode().put("limit", 1));
        assertThat(newest.toString(), newest.path("unmatchedRequestCount").asInt(), is(1));
        assertThat(newest.toString(), newest.path("unmatchedRequests").get(0).path("path").asText(), is("/orders"));

        JsonNode fixes = callTool("suggest_expectation_fix", objectMapper.createObjectNode()).path("unmatchedRequests");
        assertThat(fixes.toString(), fixes.size(), is(1));
        assertThat(fixes.toString(), fixes.get(0).path("path").asText(), is("/orders"));

        // the dashboard's Explain Unmatched dialog sends this request
        HttpResponse<String> explained = httpClient.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + mockServer.getLocalPort() + "/mockserver/explainUnmatched"))
                .timeout(Duration.ofSeconds(30))
                .PUT(HttpRequest.BodyPublishers.ofString("{\"limit\":10,\"suggestFixes\":true}")).build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(explained.body(), explained.statusCode(), is(200));
        JsonNode unmatched = objectMapper.readTree(explained.body()).path("unmatchedRequests");
        assertThat(explained.body(), unmatched.size(), is(1));
        assertThat(explained.body(), unmatched.get(0).path("path").asText(), is("/orders"));

        JsonNode failures = callTool("explain_forward_failure", objectMapper.createObjectNode()).path("forwardFailures");
        assertThat(failures.toString(), failures.size(), is(1));
        assertThat(failures.get(0).path("path").asText(), is("/stock"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + mockServer.getLocalPort() + path))
                .timeout(Duration.ofSeconds(60)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode callTool(String name, ObjectNode arguments) throws Exception {
        ObjectNode call = objectMapper.createObjectNode().put("jsonrpc", "2.0").put("id", ++requestId + 1).put("method", "tools/call");
        call.putObject("params").put("name", name).set("arguments", arguments);
        HttpResponse<String> response = postMcp(objectMapper.writeValueAsString(call));
        JsonNode result = objectMapper.readTree(response.body()).path("result");
        assertThat(response.body(), result.path("isError").asBoolean(), is(false));
        return objectMapper.readTree(result.path("content").get(0).path("text").asText());
    }

    private HttpResponse<String> postMcp(String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + mockServer.getLocalPort() + "/mockserver/mcp"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionId != null) {
            request.header("Mcp-Session-Id", sessionId);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
