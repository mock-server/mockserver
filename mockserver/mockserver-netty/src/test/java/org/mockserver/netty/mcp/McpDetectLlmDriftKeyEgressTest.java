package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.authentication.authorization.ControlPlaneRole;
import org.mockserver.client.MockServerClient;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.netty.MockServer;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * {@code detect_llm_drift} is a READ tool, so a read-only principal may call it. It must only ever send the held
 * {@code llmApiKey} to the configured backend: a cassette for another provider is refused, never replayed with that
 * provider's client (which would carry the key to that provider's host).
 */
public class McpDetectLlmDriftKeyEgressTest {

    private static final String HELD_KEY = "held-anthropic-key";
    private static final String RECORDED_RESPONSE = "{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],\"stop_reason\":\"end_turn\","
        + "\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";

    private final ObjectMapper objectMapper = ObjectMapperFactory.buildObjectMapperWithoutRemovingEmptyValues();
    private MockServer llm;
    private MockServerClient llmClient;
    private HttpState httpState;
    private EmbeddedChannel channel;
    private Path cassette;

    @Before
    public void setUp() throws Exception {
        llm = new MockServer(configuration().logLevel(Level.WARN));
        llmClient = new MockServerClient("localhost", llm.getLocalPort());
        llmClient.when(request().withMethod("POST"))
            .respond(response().withStatusCode(200).withHeader("Content-Type", "application/json").withBody(RECORDED_RESPONSE));

        LifeCycle server = mock(LifeCycle.class);
        when(server.getScheduler()).thenReturn(mock(Scheduler.class));
        when(server.getLocalPorts()).thenReturn(List.of(1080));
        when(server.isRunning()).thenReturn(true);
        httpState = new HttpState(configuration()
            .controlPlaneAuthorizationEnabled(true)
            .controlPlaneScopeMapping(Collections.singletonMap("readers", ControlPlaneRole.READ))
            .llmProvider("anthropic")
            .llmBaseUrl("http://localhost:" + llm.getLocalPort())
            .llmApiKey(HELD_KEY)
            .llmRequestTimeoutMillis(10_000L), new MockServerLogger(), mock(Scheduler.class));
        httpState.setControlPlaneAuthenticationHandler(new AuthenticationHandler() {
            @Override
            public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
                return true;
            }

            @Override
            public AuthenticationResult authenticate(HttpRequest request) {
                return AuthenticationResult.authenticated("reader", "verified-oidc", Collections.emptyMap(), Set.of("readers"));
            }
        });
        channel = new EmbeddedChannel(new McpStreamableHttpHandler(httpState, server, new McpSessionManager(httpState.getMockServerLogger())));

        cassette = Files.createTempFile("drift-cassette", ".json");
        Files.write(cassette, ("[{\"httpRequest\":{\"method\":\"POST\",\"path\":\"/v1/messages\",\"body\":{\"type\":\"JSON\",\"json\":"
            + "{\"model\":\"claude-sonnet-4\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}}},"
            + "\"httpResponse\":{\"statusCode\":200,\"body\":" + objectMapper.writeValueAsString(RECORDED_RESPONSE) + "}}]")
            .getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void tearDown() throws Exception {
        channel.close();
        httpState.stop();
        llm.stop();
        Files.deleteIfExists(cassette);
    }

    @Test
    public void shouldRefuseAReadersCassetteForAnotherProviderAndSendNothing() throws Exception {
        String sessionId = initialize();

        JsonNode response = callDetectLlmDrift(sessionId, "OPENAI");

        assertThat(response.toString(), response.path("error").isMissingNode(), is(true));
        assertThat(response.toString(), containsString("nothing was sent"));
        assertThat("no request, so no key, reaches any LLM host",
            llmClient.retrieveRecordedRequests(request()).length, is(0));
    }

    @Test
    public void shouldStillReplayAReadersCassetteForTheConfiguredProvider() throws Exception {
        String sessionId = initialize();

        JsonNode response = callDetectLlmDrift(sessionId, "ANTHROPIC");

        JsonNode result = objectMapper.readTree(response.path("result").path("content").get(0).path("text").asText());
        assertThat(response.toString(), result.path("status").asText(), is("checked"));
        assertThat(result.path("checked").asInt(), is(1));
        HttpRequest[] sent = llmClient.retrieveRecordedRequests(request().withPath("/v1/messages"));
        assertThat(sent.length, is(1));
        assertThat(sent[0].getFirstHeader("x-api-key"), is(HELD_KEY));
    }

    private String initialize() {
        FullHttpResponse init = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}", null);
        assertThat(init, notNullValue());
        String sessionId = init.headers().get("Mcp-Session-Id");
        init.release();
        FullHttpResponse initialized = post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", sessionId);
        if (initialized != null) {
            initialized.release();
        }
        return sessionId;
    }

    private JsonNode callDetectLlmDrift(String sessionId, String provider) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"detect_llm_drift\","
            + "\"arguments\":{\"provider\":\"" + provider + "\",\"cassettePath\":" + objectMapper.writeValueAsString(cassette.toString()) + "}}}";
        FullHttpResponse response = post(body, sessionId);
        assertThat(response, notNullValue());
        try {
            return objectMapper.readTree(response.content().toString(StandardCharsets.UTF_8));
        } finally {
            response.release();
        }
    }

    private FullHttpResponse post(String body, String sessionId) {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/mockserver/mcp",
            Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        if (sessionId != null) {
            request.headers().set("Mcp-Session-Id", sessionId);
        }
        channel.writeInbound(request);
        // tool calls are answered from the MCP executor, not the calling thread
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            channel.runPendingTasks();
            FullHttpResponse response = channel.readOutbound();
            if (response != null) {
                return response;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }
}
