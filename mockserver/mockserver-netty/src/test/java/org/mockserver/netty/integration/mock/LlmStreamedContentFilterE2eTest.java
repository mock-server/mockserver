package org.mockserver.netty.integration.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.llm.codec.BedrockEventStreamEncoder;
import org.mockserver.model.HttpLlmResponse;
import org.mockserver.model.LlmChaosProfile;
import org.mockserver.model.Provider;
import org.mockserver.netty.MockServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A chaos content-filter block on a streaming request arrives over HTTP as the provider's own
 * event stream ending with its filtered stop reason, which is what a streaming SDK parses.
 */
public class LlmStreamedContentFilterE2eTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MODEL_PATH = "/model/anthropic.claude-3-5-sonnet-20241022-v2:0";

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
    public void anthropicStreamEndsWithRefusal() throws Exception {
        expectBlock("/v1/messages", Provider.ANTHROPIC);

        HttpResponse<byte[]> response = post("/v1/messages", "{\"model\":\"m\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}");

        assertThat(response.statusCode(), is(200));
        assertThat(contentType(response), startsWith("text/event-stream"));
        List<JsonNode> data = sseData(response);
        assertThat(data.get(0).path("type").asText(), is("message_start"));
        JsonNode messageDelta = data.get(data.size() - 2);
        assertThat(messageDelta.path("type").asText(), is("message_delta"));
        assertThat(messageDelta.at("/delta/stop_reason").asText(), is("refusal"));
        assertThat(messageDelta.at("/delta/stop_details/type").asText(), is("refusal"));
        assertThat(data.get(data.size() - 1).path("type").asText(), is("message_stop"));
    }

    @Test
    public void geminiStreamIsOneSafetyChunk() throws Exception {
        String path = "/v1beta/models/gemini-2.0-flash:streamGenerateContent";
        expectBlock(path, Provider.GEMINI);

        HttpResponse<byte[]> response = post(path + "?alt=sse", "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"Hi\"}]}]}");

        assertThat(response.statusCode(), is(200));
        assertThat(contentType(response), startsWith("text/event-stream"));
        List<JsonNode> data = sseData(response);
        assertThat(data, hasSize(1));
        assertThat(data.get(0).at("/candidates/0/finishReason").asText(), is("SAFETY"));
        assertThat(data.get(0).at("/promptFeedback/blockReason").asText(), is("SAFETY"));
    }

    @Test
    public void geminiStreamWithoutAltSseIsAOneElementJsonArray() throws Exception {
        String path = "/v1beta/models/gemini-2.0-flash:streamGenerateContent";
        expectBlock(path, Provider.GEMINI);

        HttpResponse<byte[]> response = post(path, "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"Hi\"}]}]}");

        assertThat(response.statusCode(), is(200));
        assertThat(contentType(response), startsWith("application/json"));
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        assertThat(body.isArray(), is(true));
        assertThat(body.size(), is(1));
        assertThat(body.at("/0/candidates/0/finishReason").asText(), is("SAFETY"));
        assertThat(body.at("/0/promptFeedback/blockReason").asText(), is("SAFETY"));
    }

    @Test
    public void bedrockConverseStreamEndsWithContentFilteredMessageStop() throws Exception {
        expectBlock(MODEL_PATH + "/converse-stream", Provider.BEDROCK);

        HttpResponse<byte[]> response = post(MODEL_PATH + "/converse-stream", "{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"Hi\"}]}]}");

        assertThat(response.statusCode(), is(200));
        assertThat(contentType(response), is(BedrockEventStreamEncoder.CONTENT_TYPE));
        List<String> eventTypes = new ArrayList<>();
        JsonNode messageStop = null;
        for (BedrockEventStreamEncoder.DecodedMessage message : BedrockEventStreamEncoder.decode(response.body())) {
            String eventType = message.getHeaders().get(":event-type");
            eventTypes.add(eventType);
            if ("messageStop".equals(eventType)) {
                messageStop = OBJECT_MAPPER.readTree(message.getPayloadAsString());
            }
        }
        assertThat(eventTypes, contains("messageStart", "messageStop", "metadata"));
        assertThat(messageStop.path("stopReason").asText(), is("content_filtered"));
    }

    @Test
    public void bedrockInvokeModelStreamEndsWithRefusalChunk() throws Exception {
        expectBlock(MODEL_PATH + "/invoke-with-response-stream", Provider.BEDROCK);

        HttpResponse<byte[]> response = post(MODEL_PATH + "/invoke-with-response-stream",
            "{\"anthropic_version\":\"bedrock-2023-05-31\",\"max_tokens\":64,\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}");

        assertThat(response.statusCode(), is(200));
        assertThat(contentType(response), is(BedrockEventStreamEncoder.CONTENT_TYPE));
        List<String> stopReasons = new ArrayList<>();
        List<String> stopDetailsTypes = new ArrayList<>();
        for (BedrockEventStreamEncoder.DecodedMessage message : BedrockEventStreamEncoder.decode(response.body())) {
            assertThat(message.getHeaders().get(":event-type"), is("chunk"));
            String chunkJson = new String(Base64.getDecoder().decode(
                OBJECT_MAPPER.readTree(message.getPayloadAsString()).path("bytes").asText()), StandardCharsets.UTF_8);
            JsonNode chunk = OBJECT_MAPPER.readTree(chunkJson);
            if ("message_delta".equals(chunk.path("type").asText())) {
                stopReasons.add(chunk.at("/delta/stop_reason").asText());
                stopDetailsTypes.add(chunk.at("/delta/stop_details/type").asText());
            }
        }
        assertThat(stopReasons, contains("refusal"));
        assertThat(stopDetailsTypes, contains("refusal"));
    }

    @Test
    public void openAiStreamingRequestStillGetsTheContentFilterError() throws Exception {
        expectBlock("/v1/chat/completions", Provider.OPENAI);

        HttpResponse<byte[]> response = post("/v1/chat/completions", "{\"model\":\"m\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}");

        assertThat(response.statusCode(), is(400));
        assertThat(contentType(response), containsString("application/json"));
        assertThat(OBJECT_MAPPER.readTree(response.body()).at("/error/code").asText(), is("content_filter"));
    }

    private static void expectBlock(String path, Provider provider) {
        mockServerClient.when(request().withMethod("POST").withPath(path)).respondWithLlm(
            HttpLlmResponse.llmResponse()
                .withProvider(provider)
                .withModel("test-model")
                .withCompletion(completion().withText("this text must not be streamed").withStreaming(true))
                .withChaos(LlmChaosProfile.llmChaosProfile().withContentFilterBlockProbability(1.0)));
    }

    private static HttpResponse<byte[]> post(String pathAndQuery, String body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + pathAndQuery))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String contentType(HttpResponse<byte[]> response) {
        return response.headers().firstValue("content-type").orElse("");
    }

    private static List<JsonNode> sseData(HttpResponse<byte[]> response) throws Exception {
        List<JsonNode> data = new ArrayList<>();
        for (String line : new String(response.body(), StandardCharsets.UTF_8).split("\n")) {
            if (line.startsWith("data:")) {
                data.add(OBJECT_MAPPER.readTree(line.substring("data:".length()).trim()));
            }
        }
        assertThat("text must not be streamed", new String(response.body(), StandardCharsets.UTF_8).contains("must not be streamed"), is(false));
        return data;
    }
}
