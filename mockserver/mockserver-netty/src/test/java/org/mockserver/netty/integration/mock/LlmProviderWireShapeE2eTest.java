package org.mockserver.netty.integration.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpLlmResponse;
import org.mockserver.model.LlmChaosProfile;
import org.mockserver.model.Provider;
import org.mockserver.netty.MockServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.Usage.usage;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Provider wire shapes that real SDKs read, asserted over HTTP: stop reasons translated from an
 * expectation shared across providers, Gemini thoughts token accounting, and Bedrock chaos
 * responses that follow the wire API (InvokeModel or Converse) the request used.
 */
public class LlmProviderWireShapeE2eTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONVERSE_PATH = "/model/anthropic.claude-3-5-sonnet-20241022-v2:0/converse";
    private static final String INVOKE_PATH = "/model/anthropic.claude-3-5-sonnet-20241022-v2:0/invoke";
    private static final String CONVERSE_BODY = "{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"Hello\"}]}]}";
    private static final String INVOKE_BODY = "{\"anthropic_version\":\"bedrock-2023-05-31\",\"max_tokens\":64,"
        + "\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}";

    private static int port;
    private static MockServerClient mockServerClient;
    private static HttpClient httpClient;

    @BeforeClass
    public static void startServer() {
        port = new MockServer().getLocalPort();
        mockServerClient = new MockServerClient("localhost", port);
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
    }

    @Before
    public void resetServer() {
        mockServerClient.reset();
    }

    @Test
    public void shouldTranslateASharedStopReasonForAnthropicAndOllama() throws Exception {
        Completion truncated = completion().withText("partial").withStopReason("length");
        expect("/v1/messages", Provider.ANTHROPIC, truncated, null);
        expect("/api/chat", Provider.OLLAMA, truncated, null);

        JsonNode anthropic = json(post("/v1/messages", "{\"model\":\"claude\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"));
        JsonNode ollama = json(post("/api/chat", "{\"model\":\"llama3.1\",\"stream\":false,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"));

        assertThat(anthropic.path("stop_reason").asText(), is("max_tokens"));
        assertThat(ollama.path("done").asBoolean(false), is(true));
        assertThat(ollama.path("done_reason").asText(), is("length"));
    }

    @Test
    public void shouldReportGeminiThoughtsSeparatelyFromCandidates() throws Exception {
        expect("/v1beta/models/gemini-2.5-flash:generateContent", Provider.GEMINI,
            completion().withText("hi").withUsage(usage().withInputTokens(8).withOutputTokens(39).withReasoningTokens(27)), null);

        JsonNode usageMetadata = json(post("/v1beta/models/gemini-2.5-flash:generateContent",
            "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"hi\"}]}]}")).path("usageMetadata");

        assertThat(usageMetadata.path("promptTokenCount").asInt(-1), is(8));
        assertThat(usageMetadata.path("candidatesTokenCount").asInt(-1), is(12));
        assertThat(usageMetadata.path("thoughtsTokenCount").asInt(-1), is(27));
        assertThat(usageMetadata.path("totalTokenCount").asInt(-1), is(47));
    }

    @Test
    public void shouldReturnAConverseContentFilteredResponseForAConverseRequest() throws Exception {
        expect(CONVERSE_PATH, Provider.BEDROCK, completion().withText("hi"),
            LlmChaosProfile.llmChaosProfile().withContentFilterBlockProbability(1.0));

        HttpResponse<String> response = post(CONVERSE_PATH, CONVERSE_BODY);

        assertThat(response.statusCode(), is(200));
        JsonNode body = json(response);
        assertThat(body.path("stopReason").asText(), is("content_filtered"));
        assertThat(body.at("/output/message/role").asText(), is("assistant"));
        assertThat(body.at("/output/message/content").isArray(), is(true));
        assertThat(body.at("/usage/totalTokens").isInt(), is(true));
        assertThat(body.has("stop_reason"), is(false));
        assertThat(body.has("type"), is(false));
    }

    @Test
    public void shouldKeepTheAnthropicRefusalForAnInvokeModelRequest() throws Exception {
        expect(INVOKE_PATH, Provider.BEDROCK, completion().withText("hi"),
            LlmChaosProfile.llmChaosProfile().withContentFilterBlockProbability(1.0));

        HttpResponse<String> response = post(INVOKE_PATH, INVOKE_BODY);

        assertThat(response.statusCode(), is(200));
        assertThat(json(response).path("stop_reason").asText(), is("refusal"));
        assertThat(json(response).at("/stop_details/type").asText(), is("refusal"));
    }

    @Test
    public void shouldReturnAnAwsErrorForAChaosErrorOnEitherBedrockApi() throws Exception {
        LlmChaosProfile throttled = LlmChaosProfile.llmChaosProfile().withErrorStatus(429);
        expect(CONVERSE_PATH, Provider.BEDROCK, completion().withText("hi"), throttled);
        expect(INVOKE_PATH, Provider.BEDROCK, completion().withText("hi"), throttled);

        for (HttpResponse<String> response : List.of(post(CONVERSE_PATH, CONVERSE_BODY), post(INVOKE_PATH, INVOKE_BODY))) {
            assertThat(response.statusCode(), is(429));
            JsonNode body = json(response);
            assertThat(body.path("__type").asText(), is("ThrottlingException"));
            assertThat(body.path("message").isTextual(), is(true));
            assertThat(body.has("error"), is(false));
        }
    }

    private static void expect(String path, Provider provider, Completion completion, LlmChaosProfile chaos) {
        HttpLlmResponse llmResponse = HttpLlmResponse.llmResponse().withProvider(provider).withModel("test-model").withCompletion(completion);
        if (chaos != null) {
            llmResponse.withChaos(chaos);
        }
        mockServerClient.when(request().withMethod("POST").withPath(path)).respondWithLlm(llmResponse);
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> response) throws Exception {
        return OBJECT_MAPPER.readTree(response.body());
    }
}
