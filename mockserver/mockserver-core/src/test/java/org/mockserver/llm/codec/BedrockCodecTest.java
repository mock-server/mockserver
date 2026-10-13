package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.llm.StreamingFormat;
import org.mockserver.model.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.ToolUse.toolUse;

/**
 * Tests for the Bedrock codec (Anthropic-on-Bedrock wire format).
 * Since Bedrock delegates to AnthropicCodec, these tests verify delegation
 * is correct and the provider/version metadata is distinct. Streaming tests
 * verify that chunks can be decoded via the AWS event-stream binary format.
 */
public class BedrockCodecTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final BedrockCodec codec = new BedrockCodec();

    // --- Provider & Version ---

    @Test
    public void shouldReturnBedrockProviderAndVersion() {
        assertThat(codec.provider(), is(Provider.BEDROCK));
        assertThat(codec.apiVersion(), is("bedrock-2023-05-31"));
    }

    // --- Encode Non-Streaming (Anthropic format) ---

    @Test
    public void shouldEncodeTextCompletionInAnthropicFormat() throws Exception {
        Completion completion = completion()
            .withText("Hello from Bedrock.")
            .withUsage(Usage.usage().withInputTokens(20).withOutputTokens(4));

        HttpResponse response = codec.encode(completion, "anthropic.claude-3-7-sonnet-20250219-v1:0");

        assertThat(response.getStatusCode(), is(200));
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("id").asText(), startsWith("msg_"));
        assertThat(root.get("type").asText(), is("message"));
        assertThat(root.get("role").asText(), is("assistant"));
        assertThat(root.get("model").asText(), is("anthropic.claude-3-7-sonnet-20250219-v1:0"));

        JsonNode content = root.get("content");
        assertThat(content.size(), is(1));
        assertThat(content.get(0).get("type").asText(), is("text"));
        assertThat(content.get(0).get("text").asText(), is("Hello from Bedrock."));

        assertThat(root.get("stop_reason").asText(), is("end_turn"));
        JsonNode usage = root.get("usage");
        assertThat(usage.get("input_tokens").asInt(), is(20));
        assertThat(usage.get("output_tokens").asInt(), is(4));
    }

    @Test
    public void shouldEncodeToolCallInAnthropicFormat() throws Exception {
        Completion completion = completion()
            .withToolCall(toolUse("search").withArguments("{\"q\":\"test\"}"))
            .withStopReason("tool_use");

        HttpResponse response = codec.encode(completion, "anthropic.claude-3-7-sonnet-20250219-v1:0");

        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        JsonNode content = root.get("content");
        assertThat(content.size(), is(1));
        assertThat(content.get(0).get("type").asText(), is("tool_use"));
        assertThat(content.get(0).get("name").asText(), is("search"));
        assertThat(root.get("stop_reason").asText(), is("tool_use"));
    }

    @Test
    public void shouldSetContentTypeHeader() throws Exception {
        Completion completion = completion().withText("test");
        HttpResponse response = codec.encode(completion, "model");
        assertThat(response.getFirstHeader("content-type"), is("application/json"));
    }

    @Test
    public void shouldDefaultUsageToZeros() throws Exception {
        Completion completion = completion().withText("test");
        HttpResponse response = codec.encode(completion, "model");
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("usage").get("input_tokens").asInt(), is(0));
        assertThat(root.get("usage").get("output_tokens").asInt(), is(0));
    }

    // --- Encode Streaming (Anthropic SSE events) ---

    @Test
    public void shouldProduceAnthropicStreamingEvents() throws Exception {
        Completion completion = completion()
            .withText("Hello world")
            .withUsage(Usage.usage().withInputTokens(10).withOutputTokens(5));

        List<SseEvent> events = codec.encodeStreaming(completion, "anthropic.claude-3-7-sonnet-20250219-v1:0", null);

        assertThat(events.size(), is(greaterThanOrEqualTo(6)));
        assertThat(events.get(0).getEvent(), is("message_start"));
        assertThat(events.get(events.size() - 1).getEvent(), is("message_stop"));
    }

    @Test
    public void shouldProduceCorrectStopReasonInStreamingDelta() throws Exception {
        Completion completion = completion()
            .withText("Hello")
            .withStopReason("end_turn");

        List<SseEvent> events = codec.encodeStreaming(completion, "model", null);

        SseEvent messageDelta = null;
        for (SseEvent event : events) {
            if ("message_delta".equals(event.getEvent())) {
                messageDelta = event;
            }
        }
        assertThat(messageDelta, is(notNullValue()));
        JsonNode data = OBJECT_MAPPER.readTree(messageDelta.getData());
        assertThat(data.get("delta").get("stop_reason").asText(), is("end_turn"));
    }

    // --- Decode (Anthropic messages format) ---

    @Test
    public void shouldDecodeAnthropicFormatMessages() {
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"model\": \"anthropic.claude-3-7-sonnet-20250219-v1:0\",\n" +
                "  \"anthropic_version\": \"bedrock-2023-05-31\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"user\", \"content\": \"What is 2+2?\"}\n" +
                "  ]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(1));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.USER));
        assertThat(parsed.getMessages().get(0).getTextContent(), is("What is 2+2?"));
    }

    @Test
    public void shouldDecodeToolUseAndToolResult() {
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"model\": \"anthropic.claude-3-7-sonnet-20250219-v1:0\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"user\", \"content\": \"Search\"},\n" +
                "    {\"role\": \"assistant\", \"content\": [\n" +
                "      {\"type\": \"tool_use\", \"id\": \"toolu_abc\", \"name\": \"search\", \"input\": {\"q\": \"test\"}}\n" +
                "    ]},\n" +
                "    {\"role\": \"user\", \"content\": [\n" +
                "      {\"type\": \"tool_result\", \"tool_use_id\": \"toolu_abc\", \"content\": \"result data\"}\n" +
                "    ]}\n" +
                "  ]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(3));
        assertThat(parsed.getMessages().get(1).getToolCalls(), hasSize(1));
        assertThat(parsed.getMessages().get(1).getToolCalls().get(0).getName(), is("search"));
        assertThat(parsed.getMessages().get(2).getRole(), is(ParsedMessage.Role.TOOL));
        assertThat(parsed.getMessages().get(2).getToolResults(), hasEntry("toolu_abc", "result data"));
    }

    @Test
    public void shouldReturnEmptyForMalformedJson() {
        ParsedConversation parsed = codec.decode(request().withBody("not json"));
        assertThat(parsed.getMessages(), is(empty()));
    }

    @Test
    public void shouldReturnEmptyForNullRequest() {
        ParsedConversation parsed = codec.decode(null);
        assertThat(parsed.getMessages(), is(empty()));
    }

    @Test
    public void shouldReturnEmptyForMissingMessages() {
        ParsedConversation parsed = codec.decode(request().withBody("{\"model\": \"m\"}"));
        assertThat(parsed.getMessages(), is(empty()));
    }

    @Test
    public void shouldEncodeEmbeddingInTitanShapeByDefault() throws Exception {
        // given
        EmbeddingResponse embedding = EmbeddingResponse.embedding()
            .withDimensions(8)
            .withDeterministicFromInput(true)
            .withSeed(1L);

        // when — no model hint defaults to Amazon Titan: {"embedding":[...],"inputTextTokenCount":N}
        HttpResponse response = codec.encodeEmbedding(embedding, "test input");

        // then
        assertThat(response.getStatusCode(), is(200));
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("embedding").isArray(), is(true));
        assertThat(root.get("embedding").size(), is(8));
        assertThat(root.has("inputTextTokenCount"), is(true));
    }

    @Test
    public void shouldEncodeEmbeddingInCohereShapeForCohereModel() throws Exception {
        // given
        EmbeddingResponse embedding = EmbeddingResponse.embedding()
            .withDimensions(8)
            .withDeterministicFromInput(true)
            .withSeed(1L);

        // when — cohere model selects the Cohere shape: {"embeddings":[[...]]}
        HttpResponse response = codec.encodeEmbedding(embedding, "test input", "cohere.embed-english-v3");

        // then
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.has("embedding"), is(false));
        JsonNode embeddings = root.get("embeddings");
        assertThat(embeddings.isArray(), is(true));
        assertThat(embeddings.size(), is(1));
        assertThat(embeddings.get(0).size(), is(8));
    }

    @Test
    public void shouldDefaultBedrockEmbeddingDimensionsTo1024() throws Exception {
        // given
        EmbeddingResponse embedding = EmbeddingResponse.embedding().withDeterministicFromInput(true);

        // when
        HttpResponse response = codec.encodeEmbedding(embedding, "test input");

        // then
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("embedding").size(), is(1024));
    }

    // --- InvokeModel token-count headers, as recorded from real Bedrock responses ---

    private static final String INPUT_TOKEN_COUNT = "X-Amzn-Bedrock-Input-Token-Count";
    private static final String OUTPUT_TOKEN_COUNT = "X-Amzn-Bedrock-Output-Token-Count";

    private static HttpRequest invoke(String model, String body) {
        return request().withMethod("POST").withPath("/model/" + model + "/invoke").withBody(body);
    }

    /** The values of the header with exactly this name, so the casing Bedrock sends is asserted too. */
    private static List<String> headerValues(HttpResponse response, String name) {
        return response.getHeaderList().stream()
            .filter(header -> header.getName().getValue().equals(name))
            .flatMap(header -> header.getValues().stream().map(NottableString::getValue))
            .collect(java.util.stream.Collectors.toList());
    }

    private static List<String> tokenCountHeaderNames(HttpResponse response) {
        return response.getHeaderList().stream()
            .map(header -> header.getName().getValue())
            .filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains("token-count"))
            .collect(java.util.stream.Collectors.toList());
    }

    @Test
    public void shouldSendTheBodyUsageInTheTokenCountHeadersOfAnInvokeModelCompletion() throws Exception {
        Completion completion = completion()
            .withText("Hello from Bedrock.")
            .withUsage(Usage.usage().withInputTokens(14).withOutputTokens(10));

        HttpResponse response = codec.encode(completion, "anthropic.claude-3-7-sonnet-20250219-v1:0",
            invoke("anthropic.claude-3-7-sonnet-20250219-v1:0", "{\"anthropic_version\":\"bedrock-2023-05-31\",\"max_tokens\":10,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"));

        JsonNode usage = OBJECT_MAPPER.readTree(response.getBodyAsString()).get("usage");
        assertThat(usage.get("input_tokens").asInt(), is(14));
        assertThat(usage.get("output_tokens").asInt(), is(10));
        assertThat(headerValues(response, INPUT_TOKEN_COUNT), contains("14"));
        assertThat(headerValues(response, OUTPUT_TOKEN_COUNT), contains("10"));
        assertThat(tokenCountHeaderNames(response), containsInAnyOrder(INPUT_TOKEN_COUNT, OUTPUT_TOKEN_COUNT));
    }

    @Test
    public void shouldSendZeroTokenCountHeadersForAnInvokeModelCompletionWithNoUsage() throws Exception {
        HttpResponse response = codec.encode(completion().withText("no usage"), "anthropic.claude-3-haiku-20240307-v1:0");

        JsonNode usage = OBJECT_MAPPER.readTree(response.getBodyAsString()).get("usage");
        assertThat(usage.get("input_tokens").asInt(), is(0));
        assertThat(usage.get("output_tokens").asInt(), is(0));
        assertThat(headerValues(response, INPUT_TOKEN_COUNT), contains("0"));
        assertThat(headerValues(response, OUTPUT_TOKEN_COUNT), contains("0"));
    }

    // Values from the DataDog dd-trace-js cassettes of us.anthropic.claude-sonnet-4-20250514-v1:0 invoke calls
    private static final String CACHE_READ_INPUT_TOKEN_COUNT = "X-Amzn-Bedrock-Cache-Read-Input-Token-Count";
    private static final String CACHE_WRITE_INPUT_TOKEN_COUNT = "X-Amzn-Bedrock-Cache-Write-Input-Token-Count";

    @Test
    public void shouldSendTheCacheTokenCountHeadersOfAnInvokeModelCompletionThatWritesTheCache() throws Exception {
        Completion completion = completion()
            .withText("The capital of France is Paris.")
            .withUsage(Usage.usage().withInputTokens(4).withOutputTokens(10).withCacheCreationTokens(1209));

        HttpResponse response = codec.encode(completion, "us.anthropic.claude-sonnet-4-20250514-v1:0",
            invoke("us.anthropic.claude-sonnet-4-20250514-v1:0", "{\"anthropic_version\":\"bedrock-2023-05-31\",\"max_tokens\":10,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"));

        JsonNode usage = OBJECT_MAPPER.readTree(response.getBodyAsString()).get("usage");
        assertThat(usage.get("cache_creation_input_tokens").asInt(), is(1209));
        assertThat(headerValues(response, INPUT_TOKEN_COUNT), contains("4"));
        assertThat(headerValues(response, OUTPUT_TOKEN_COUNT), contains("10"));
        assertThat(headerValues(response, CACHE_READ_INPUT_TOKEN_COUNT), contains("0"));
        assertThat(headerValues(response, CACHE_WRITE_INPUT_TOKEN_COUNT), contains("1209"));
        assertThat(tokenCountHeaderNames(response), containsInAnyOrder(INPUT_TOKEN_COUNT, OUTPUT_TOKEN_COUNT, CACHE_READ_INPUT_TOKEN_COUNT, CACHE_WRITE_INPUT_TOKEN_COUNT));
    }

    @Test
    public void shouldSendAZeroCacheWriteHeaderWhenAnInvokeModelCompletionOnlyReadsTheCache() throws Exception {
        Completion completion = completion()
            .withText("The capital of Italy is Rome.")
            .withUsage(Usage.usage().withInputTokens(4).withOutputTokens(10).withCachedInputTokens(1209));

        HttpResponse response = codec.encode(completion, "us.anthropic.claude-sonnet-4-20250514-v1:0");

        JsonNode usage = OBJECT_MAPPER.readTree(response.getBodyAsString()).get("usage");
        assertThat(usage.get("cache_read_input_tokens").asInt(), is(1209));
        assertThat(headerValues(response, CACHE_READ_INPUT_TOKEN_COUNT), contains("1209"));
        assertThat(headerValues(response, CACHE_WRITE_INPUT_TOKEN_COUNT), contains("0"));
    }

    @Test
    public void shouldSendNoTokenCountHeadersOnAConverseResponseWithCacheUsage() {
        HttpResponse response = codec.encode(
            completion().withText("hi").withUsage(Usage.usage().withInputTokens(5).withOutputTokens(2).withCachedInputTokens(7).withCacheCreationTokens(3)),
            "anthropic.claude-3-haiku-20240307-v1:0",
            request().withMethod("POST").withPath("/model/anthropic.claude-3-haiku-20240307-v1:0/converse")
                .withBody("{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"hi\"}]}]}"));

        assertThat(response.getBodyAsString(), containsString("\"cacheReadInputTokens\":7"));
        assertThat(tokenCountHeaderNames(response), is(empty()));
    }

    @Test
    public void shouldSendNoTokenCountHeadersOnAConverseResponse() {
        HttpResponse response = codec.encode(
            completion().withText("hi").withUsage(Usage.usage().withInputTokens(5).withOutputTokens(2)),
            "anthropic.claude-3-haiku-20240307-v1:0",
            request().withMethod("POST").withPath("/model/anthropic.claude-3-haiku-20240307-v1:0/converse")
                .withBody("{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"hi\"}]}]}"));

        assertThat(response.getBodyAsString(), containsString("\"inputTokens\":5"));
        assertThat(tokenCountHeaderNames(response), is(empty()));
    }

    @Test
    public void shouldSendTheTitanInputTextTokenCountAsTheInputTokenCountHeaderAndNoOutputCount() throws Exception {
        EmbeddingResponse embedding = EmbeddingResponse.embedding().withDimensions(8).withDeterministicFromInput(true);
        String[][] cases = {
            {"amazon.titan-embed-text-v1", "{\"inputText\":\"a sentence for titan g1 to embed\"}"},
            {"amazon.titan-embed-text-v2:0", "{\"inputText\":\"a longer sentence for titan v2 to embed today\"}"},
            {"amazon.titan-embed-image-v1", "{\"inputText\":\"a caption\"}"}
        };
        for (String[] invocation : cases) {
            HttpResponse response = codec.encodeEmbedding(embedding, invoke(invocation[0], invocation[1]), null);

            JsonNode tokenCount = OBJECT_MAPPER.readTree(response.getBodyAsString()).get("inputTextTokenCount");
            assertThat(invocation[0], tokenCount.asInt(), greaterThan(0));
            assertThat(invocation[0], headerValues(response, INPUT_TOKEN_COUNT), contains(String.valueOf(tokenCount.asInt())));
            assertThat(invocation[0], tokenCountHeaderNames(response), contains(INPUT_TOKEN_COUNT));
        }
    }

    @Test
    public void shouldSendTheApproximateTokensOfTheCohereTextsAsTheInputTokenCountHeaderAndNoOutputCount() {
        EmbeddingResponse embedding = EmbeddingResponse.embedding().withDimensions(8).withDeterministicFromInput(true);

        // 12 and 20 characters, at the four characters a token every codec estimates
        HttpResponse texts = codec.encodeEmbedding(embedding,
            invoke("cohere.embed-english-v3", "{\"texts\":[\"Hello World!\",\"Goodbye cruel world!\"],\"input_type\":\"search_document\"}"), null);
        assertThat(headerValues(texts, INPUT_TOKEN_COUNT), contains("8"));
        assertThat(tokenCountHeaderNames(texts), contains(INPUT_TOKEN_COUNT));

        // a v4 input's text parts are counted and its image parts are not
        HttpResponse inputs = codec.encodeEmbedding(embedding, invoke("cohere.embed-v4:0",
            "{\"inputs\":[{\"content\":[{\"type\":\"text\",\"text\":\"sixteen chars ok\"},"
                + "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,AAAA\"}}]},"
                + "{\"content\":[{\"type\":\"text\",\"text\":\"eight ch\"}]}],\"input_type\":\"search_document\"}"), null);
        assertThat(headerValues(inputs, INPUT_TOKEN_COUNT), contains("6"));

        HttpResponse image = codec.encodeEmbedding(embedding,
            invoke("cohere.embed-english-v3", "{\"images\":[\"data:image/png;base64,AAAA\"],\"input_type\":\"image\"}"), null);
        assertThat(headerValues(image, INPUT_TOKEN_COUNT), contains("0"));
        assertThat(tokenCountHeaderNames(image), contains(INPUT_TOKEN_COUNT));
    }

    @Test
    public void shouldSendTheInputTokenCountHeaderOnARequestLessEmbedding() throws Exception {
        EmbeddingResponse embedding = EmbeddingResponse.embedding().withDimensions(8);

        HttpResponse titan = codec.encodeEmbedding(embedding, "test input");
        assertThat(headerValues(titan, INPUT_TOKEN_COUNT),
            contains(OBJECT_MAPPER.readTree(titan.getBodyAsString()).get("inputTextTokenCount").asText()));

        HttpResponse cohere = codec.encodeEmbedding(embedding, "twelve chars", "cohere.embed-english-v3");
        assertThat(headerValues(cohere, INPUT_TOKEN_COUNT), contains("3"));
    }

    // --- Streaming Format ---

    @Test
    public void shouldDeclareAwsEventStreamStreamingFormat() {
        assertThat(codec.streamingFormat(), is(StreamingFormat.AWS_EVENT_STREAM));
    }

    // --- Event-Stream Streaming Integration ---

    @Test
    public void shouldProduceStreamingChunksThatDecodeViaEventStream() throws Exception {
        Completion completion = completion()
            .withText("Hello world")
            .withUsage(Usage.usage().withInputTokens(10).withOutputTokens(5));

        List<SseEvent> events = codec.encodeStreaming(completion,
            "anthropic.claude-3-7-sonnet-20250219-v1:0", null);

        assertThat(events.size(), is(greaterThanOrEqualTo(6)));
        assertThat(events.get(0).getEvent(), is("message_start"));
        assertThat(events.get(events.size() - 1).getEvent(), is("message_stop"));

        // Simulate the event-stream binary encoding that HttpSseResponseActionHandler performs.
        // Each event's data is wrapped via BedrockEventStreamEncoder.encodeChunk.
        for (SseEvent event : events) {
            String chunkJson = event.getData();
            assertThat("event data must not be null", chunkJson, is(notNullValue()));

            byte[] encoded = BedrockEventStreamEncoder.encodeChunk(chunkJson);
            List<BedrockEventStreamEncoder.DecodedMessage> decoded =
                BedrockEventStreamEncoder.decode(encoded);

            assertThat(decoded, hasSize(1));

            // Verify headers
            assertThat(decoded.get(0).getHeaders().get(":event-type"), is("chunk"));
            assertThat(decoded.get(0).getHeaders().get(":content-type"), is("application/json"));
            assertThat(decoded.get(0).getHeaders().get(":message-type"), is("event"));

            // Verify base64 payload round-trips to the original chunk JSON
            JsonNode payloadNode = OBJECT_MAPPER.readTree(decoded.get(0).getPayloadAsString());
            String base64 = payloadNode.get("bytes").asText();
            String roundTripped = new String(
                Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            assertThat(roundTripped, is(chunkJson));
        }
    }

    @Test
    public void shouldProduceFullStreamThatDecodesIntoOrderedChunks() throws Exception {
        Completion completion = completion()
            .withText("Test stream")
            .withUsage(Usage.usage().withInputTokens(5).withOutputTokens(3));

        List<SseEvent> events = codec.encodeStreaming(completion,
            "anthropic.claude-3-7-sonnet-20250219-v1:0", null);

        // Build full event-stream byte array (simulating HttpSseResponseActionHandler)
        java.io.ByteArrayOutputStream fullStream = new java.io.ByteArrayOutputStream();
        for (SseEvent event : events) {
            fullStream.write(BedrockEventStreamEncoder.encodeChunk(event.getData()));
        }

        // Decode the full stream
        List<BedrockEventStreamEncoder.DecodedMessage> decoded =
            BedrockEventStreamEncoder.decode(fullStream.toByteArray());

        assertThat("decoded count must match event count",
            decoded.size(), is(events.size()));

        // First chunk should be message_start
        JsonNode firstPayload = OBJECT_MAPPER.readTree(
            new String(Base64.getDecoder().decode(
                OBJECT_MAPPER.readTree(decoded.get(0).getPayloadAsString())
                    .get("bytes").asText()), StandardCharsets.UTF_8));
        assertThat(firstPayload.get("type").asText(), is("message_start"));

        // Last chunk should be message_stop
        JsonNode lastPayload = OBJECT_MAPPER.readTree(
            new String(Base64.getDecoder().decode(
                OBJECT_MAPPER.readTree(decoded.get(decoded.size() - 1).getPayloadAsString())
                    .get("bytes").asText()), StandardCharsets.UTF_8));
        assertThat(lastPayload.get("type").asText(), is("message_stop"));
    }
}
