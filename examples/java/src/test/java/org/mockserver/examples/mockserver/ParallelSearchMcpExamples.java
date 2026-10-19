package org.mockserver.examples.mockserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Body;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.SocketAddress;
import org.mockserver.serialization.ExpectationSerializer;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.TemplateType.VELOCITY;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.JsonPathBody.jsonPath;
import static org.mockserver.model.JsonRpcBody.jsonRpc;

/**
 * Capture a remote MCP server (Parallel Search, {@code https://search.parallel.ai/mcp}) through
 * MockServer, then replay it offline from expectations that match on the JSON-RPC method and echo
 * the caller's JSON-RPC id with a Velocity template.
 * <p>
 * {@link #replaySharedFixture()} always runs and needs no network. {@link #captureThenReplay()} runs
 * only when {@code MCP_CAPTURE=true} (an optional {@code PARALLEL_API_KEY} is sent upstream).
 * Starts an embedded MockServer unless {@code MOCKSERVER_PORT} (and optionally
 * {@code MOCKSERVER_HOST}) point at a running one.
 */
public class ParallelSearchMcpExamples {

    private static final String UPSTREAM_HOST = "search.parallel.ai";
    private static final Path FIXTURE = Paths.get("..", "json", "parallel_search_mcp", "replay_expectations.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private ClientAndServer embeddedServer;
    private MockServerClient client;
    private String mcpUrl;

    @Before
    public void connectToMockServer() {
        String port = System.getenv("MOCKSERVER_PORT");
        if (port == null) {
            embeddedServer = ClientAndServer.startClientAndServer();
            client = embeddedServer;
            mcpUrl = "http://localhost:" + embeddedServer.getPort() + "/mcp";
        } else {
            String host = System.getenv().getOrDefault("MOCKSERVER_HOST", "localhost");
            client = new MockServerClient(host, Integer.parseInt(port));
            mcpUrl = "http://" + host + ":" + port + "/mcp";
        }
    }

    @After
    public void stopEmbeddedServer() {
        // not client.close() for a remote server: MockServerClient.close() stops the server it points at
        if (embeddedServer != null) {
            embeddedServer.stop();
        }
    }

    @Test
    public void replaySharedFixture() throws Exception {
        String fixture = new String(Files.readAllBytes(FIXTURE), StandardCharsets.UTF_8);
        replay(new ExpectationSerializer(new MockServerLogger()).deserializeArray(fixture, false), FIXTURE.getFileName().toString());
    }

    @Test
    public void captureThenReplay() throws Exception {
        assumeTrue("set MCP_CAPTURE=true to capture from the live Parallel Search MCP server", "true".equals(System.getenv("MCP_CAPTURE")));
        replay(capture(), "the live capture");
    }

    private Expectation[] capture() throws Exception {
        client.reset();
        // keep credentials (e.g. the forwarded Authorization header) out of MockServer's own log
        client.updateConfiguration("{\"redactSecretsInLog\": true}");
        client.when(request().withPath("/mcp")).forward(forwardOverriddenRequest(request()
            .withSocketAddress(UPSTREAM_HOST, 443, SocketAddress.Scheme.HTTPS)
            .withHeader("Host", UPSTREAM_HOST)));
        System.out.println("capture: forwarding /mcp to https://" + UPSTREAM_HOST + "/mcp");
        Session session = runSession(new McpClient(mcpUrl, System.getenv("PARALLEL_API_KEY")));
        System.out.println("capture: " + session.server + ", tools " + session.tools + ", " + session.search.size()
            + " search results, fetched " + session.fetch.get(0).path("url").asText());
        // MockServer records every proxied exchange as an expectation; rewrite each one for replay
        Expectation[] recorded = client.retrieveRecordedExpectations(request().withPath("/mcp"));
        List<Expectation> expectations = new ArrayList<>();
        for (Expectation exchange : recorded) {
            expectations.add(replayExpectation(exchange));
        }
        System.out.println("capture: recorded " + recorded.length + " exchanges -> " + expectations.size() + " replay expectations");
        return expectations.toArray(new Expectation[0]);
    }

    private void replay(Expectation[] expectations, String source) throws Exception {
        client.reset(); // drops the forward expectation and the recorded log
        client.upsert(expectations);
        System.out.println("replay: loaded " + expectations.length + " expectations from " + source);
        Session session = runSession(new McpClient(mcpUrl, null));
        assertTrue("tools/list returned " + session.tools, session.tools.contains("web_search") && session.tools.contains("web_fetch"));
        assertTrue("web_search returned no results", !session.search.isEmpty());
        for (JsonNode result : session.search) {
            assertTrue("web_search result without a url: " + result, !result.path("url").asText().isEmpty());
        }
        assertTrue("web_fetch returned no content", !session.fetch.isEmpty() && !session.fetch.get(0).path("excerpts").path(0).asText().isEmpty());
        Expectation[] forwarded = client.retrieveRecordedExpectations(request().withPath("/mcp")); // only proxied traffic is recorded
        assertEquals(forwarded.length + " requests were forwarded upstream during replay", 0, forwarded.length);
        String excerpt = session.fetch.get(0).path("excerpts").path(0).asText();
        System.out.println("replay: " + session.server + ", tools " + session.tools);
        System.out.println("replay: web_search -> " + session.search.size() + " results, first " + session.search.get(0).path("url").asText());
        System.out.println("replay: web_fetch -> " + session.fetch.get(0).path("url").asText() + " (" + excerpt.codePointCount(0, excerpt.length()) + " chars)");
        System.out.println("replay: 0 requests forwarded to " + UPSTREAM_HOST + " -- answered from recorded fixtures");
    }

    /**
     * Turn one recorded JSON-RPC exchange into a replay expectation that echoes the caller's id.
     */
    private static Expectation replayExpectation(Expectation recorded) throws Exception {
        JsonNode requestBody = JSON.readTree(((HttpRequest) recorded.getHttpRequest()).getBodyAsString());
        HttpResponse recordedResponse = recorded.getHttpResponse();
        int statusCode = recordedResponse.getStatusCode() != null ? recordedResponse.getStatusCode() : 200;
        String responseBody = recordedResponse.getBodyAsString();
        String method = requestBody.path("method").asText();
        Body<?> matcher = "tools/call".equals(method)
            ? jsonPath("$[?(@.method == 'tools/call' && @.params.name == '" + requestBody.path("params").path("name").asText() + "')]")
            : jsonRpc(method);
        Expectation expectation = new Expectation(request().withMethod("POST").withPath("/mcp").withBody(matcher));
        if (responseBody == null || responseBody.isEmpty()) { // a notification: replay the bare status (202 Accepted)
            return expectation.thenRespond(response().withStatusCode(statusCode));
        }
        JsonNode reply = JSON.readTree(responseBody);
        String key = reply.has("result") ? "result" : "error";
        // escape Velocity metacharacters so recorded text (e.g. markdown "#") renders literally
        // "\" goes last because the $ and # replacements contain none, while its own contains a "$"
        String recordedJson = JSON.writeValueAsString(reply.get(key))
            .replace("$", "${esc.d}").replace("#", "${esc.h}").replace("\\", "${esc.b}");
        String responseTemplate = "{\"statusCode\": " + statusCode + ", \"headers\": [{\"name\": \"Content-Type\", \"values\": [\"application/json\"]}], "
            + "\"body\": {\"jsonrpc\": \"2.0\", \"id\": $!{request.jsonRpcRawId}, \"" + key + "\": " + recordedJson + "}}";
        return expectation.thenRespond(template(VELOCITY, responseTemplate));
    }

    /**
     * The application under test: initialize, list tools, search, then fetch.
     */
    private static Session runSession(McpClient mcp) throws Exception {
        ObjectNode initParams = JSON.createObjectNode().put("protocolVersion", "2025-06-18");
        initParams.putObject("capabilities");
        initParams.putObject("clientInfo").put("name", "mockserver-example").put("version", "1.0.0");
        Session session = new Session();
        session.server = mcp.send("initialize", initParams).path("serverInfo").path("name").asText();
        mcp.notify("notifications/initialized");
        for (JsonNode tool : mcp.send("tools/list", JSON.createObjectNode()).path("tools")) {
            session.tools.add(tool.path("name").asText());
        }
        ObjectNode searchArguments = JSON.createObjectNode().put("objective", "What is MockServer?");
        searchArguments.putArray("search_queries").add("MockServer mock HTTP server");
        mcp.callTool("web_search", searchArguments).path("results").forEach(session.search::add);
        ObjectNode fetchArguments = JSON.createObjectNode();
        fetchArguments.putArray("urls").add("https://www.mock-server.com/");
        fetchArguments.put("objective", "What is MockServer?");
        mcp.callTool("web_fetch", fetchArguments).path("results").forEach(session.fetch::add);
        return session;
    }

    private static class Session {
        String server;
        final List<String> tools = new ArrayList<>();
        final List<JsonNode> search = new ArrayList<>();
        final List<JsonNode> fetch = new ArrayList<>();
    }

    /**
     * Minimal MCP Streamable HTTP client: one JSON-RPC message per POST.
     */
    private static class McpClient {
        private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        private final String url;
        private final String apiKey;
        private int nextId = 1;

        McpClient(String url, String apiKey) {
            this.url = url;
            this.apiKey = apiKey;
        }

        JsonNode send(String method, JsonNode params) throws Exception {
            int id = nextId++;
            ObjectNode message = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", method);
            message.set("params", params);
            message.put("id", id);
            JsonNode reply = JSON.readTree(post(message, 200));
            assertTrue(method + ": id " + reply.get("id") + " != " + id, reply.path("id").isIntegralNumber() && reply.path("id").asInt() == id);
            return reply.path("result");
        }

        void notify(String method) throws Exception {
            // the MCP Streamable HTTP transport acknowledges a notification with a bare 202 Accepted
            post(JSON.createObjectNode().put("jsonrpc", "2.0").put("method", method), 202);
        }

        JsonNode callTool(String name, JsonNode arguments) throws Exception {
            ObjectNode params = JSON.createObjectNode().put("name", name);
            params.set("arguments", arguments);
            // Parallel returns its payload as JSON text
            return JSON.readTree(send("tools/call", params).path("content").path(0).path("text").asText());
        }

        private String post(JsonNode message, int expectedStatus) throws Exception {
            // an explicit User-Agent: Parallel's CDN rejects some library defaults
            java.net.http.HttpRequest.Builder request = java.net.http.HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("User-Agent", "mockserver-example/1.0")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(message)));
            if (apiKey != null && !apiKey.isEmpty()) {
                request.header("Authorization", "Bearer " + apiKey);
            }
            java.net.http.HttpResponse<String> response = httpClient.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(message.path("method").asText() + ": HTTP status", expectedStatus, response.statusCode());
            return response.body();
        }
    }
}
