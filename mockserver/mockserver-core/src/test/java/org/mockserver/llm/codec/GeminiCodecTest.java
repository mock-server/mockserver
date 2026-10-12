package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.model.*;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.ToolUse.toolUse;

public class GeminiCodecTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final GeminiCodec codec = new GeminiCodec();

    // --- Provider & Version ---

    @Test
    public void shouldReturnGeminiProviderAndVersion() {
        assertThat(codec.provider(), is(Provider.GEMINI));
        assertThat(codec.apiVersion(), is("v1beta-2025"));
    }

    // --- Encode Non-Streaming ---

    @Test
    public void shouldEncodeTextOnlyCompletion() throws Exception {
        Completion completion = completion()
            .withText("The answer is 42.")
            .withUsage(Usage.usage().withInputTokens(10).withOutputTokens(5));

        HttpResponse response = codec.encode(completion, "gemini-2.0-flash");

        assertThat(response.getStatusCode(), is(200));
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());

        JsonNode candidates = root.get("candidates");
        assertThat(candidates.size(), is(1));
        JsonNode candidate = candidates.get(0);
        assertThat(candidate.get("finishReason").asText(), is("STOP"));
        assertThat(candidate.get("index").asInt(), is(0));

        JsonNode content = candidate.get("content");
        assertThat(content.get("role").asText(), is("model"));
        JsonNode parts = content.get("parts");
        assertThat(parts.size(), is(1));
        assertThat(parts.get(0).get("text").asText(), is("The answer is 42."));

        JsonNode usageMetadata = root.get("usageMetadata");
        assertThat(usageMetadata.get("promptTokenCount").asInt(), is(10));
        assertThat(usageMetadata.get("candidatesTokenCount").asInt(), is(5));
        assertThat(usageMetadata.get("totalTokenCount").asInt(), is(15));

        assertThat(root.get("modelVersion").asText(), is("gemini-2.0-flash"));
    }

    @Test
    public void shouldEncodeFunctionCallInParts() throws Exception {
        Completion completion = completion()
            .withToolCall(toolUse("search").withArguments("{\"q\":\"foo\"}"));

        HttpResponse response = codec.encode(completion, "gemini-2.0-flash");

        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        JsonNode parts = root.get("candidates").get(0).get("content").get("parts");
        assertThat(parts.size(), is(1));
        assertThat(parts.get(0).has("functionCall"), is(true));
        assertThat(parts.get(0).get("functionCall").get("name").asText(), is("search"));
        assertThat(parts.get(0).get("functionCall").get("args").get("q").asText(), is("foo"));
    }

    @Test
    public void shouldEncodeTextAndFunctionCalls() throws Exception {
        Completion completion = completion()
            .withText("Let me check.")
            .withToolCall(toolUse("search").withArguments("{\"q\":\"test\"}"));

        HttpResponse response = codec.encode(completion, "gemini-2.0-flash");

        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        JsonNode parts = root.get("candidates").get(0).get("content").get("parts");
        assertThat(parts.size(), is(2));
        assertThat(parts.get(0).get("text").asText(), is("Let me check."));
        assertThat(parts.get(1).has("functionCall"), is(true));
    }

    @Test
    public void shouldMapStopReasonEndTurnToSTOP() throws Exception {
        Completion completion = completion().withText("test").withStopReason("end_turn");
        HttpResponse response = codec.encode(completion, "gemini-2.0-flash");
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("candidates").get(0).get("finishReason").asText(), is("STOP"));
    }

    @Test
    public void shouldMapStopReasonMaxTokensToMAX_TOKENS() throws Exception {
        Completion completion = completion().withText("test").withStopReason("max_tokens");
        HttpResponse response = codec.encode(completion, "gemini-2.0-flash");
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("candidates").get(0).get("finishReason").asText(), is("MAX_TOKENS"));
    }

    @Test
    public void shouldPassThroughGeminiNativeFinishReasons() throws Exception {
        for (String reason : new String[]{"STOP", "MAX_TOKENS", "SAFETY", "RECITATION", "OTHER"}) {
            Completion completion = completion().withText("test").withStopReason(reason);
            HttpResponse response = codec.encode(completion, "gemini-2.0-flash");
            JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
            assertThat(root.get("candidates").get(0).get("finishReason").asText(), is(reason));
        }
    }

    @Test
    public void shouldMapToolUseFinishReasonToSTOP() throws Exception {
        Completion completion = completion()
            .withToolCall(toolUse("fn").withArguments("{}"))
            .withStopReason("tool_use");
        HttpResponse response = codec.encode(completion, "gemini-2.0-flash");
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("candidates").get(0).get("finishReason").asText(), is("STOP"));
    }

    @Test
    public void shouldDefaultUsageToZeros() throws Exception {
        Completion completion = completion().withText("test");
        HttpResponse response = codec.encode(completion, "gemini-2.0-flash");
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        JsonNode usage = root.get("usageMetadata");
        assertThat(usage.get("promptTokenCount").asInt(), is(0));
        assertThat(usage.get("candidatesTokenCount").asInt(), is(0));
        assertThat(usage.get("totalTokenCount").asInt(), is(0));
    }

    @Test
    public void shouldSetContentTypeHeader() throws Exception {
        Completion completion = completion().withText("test");
        HttpResponse response = codec.encode(completion, "gemini-2.0-flash");
        assertThat(response.getFirstHeader("content-type"), is("application/json"));
    }

    // --- Encode Streaming ---

    @Test
    public void shouldProduceStreamingChunksForText() throws Exception {
        Completion completion = completion()
            .withText("Hello world")
            .withUsage(Usage.usage().withInputTokens(10).withOutputTokens(5));

        List<SseEvent> events = codec.encodeStreaming(completion, "gemini-2.0-flash", null);

        // Text chunks + final chunk with finishReason/usage
        assertThat(events.size(), is(greaterThanOrEqualTo(2)));

        // Verify text concatenation
        StringBuilder concatenated = new StringBuilder();
        for (int i = 0; i < events.size() - 1; i++) {
            JsonNode chunk = OBJECT_MAPPER.readTree(events.get(i).getData());
            JsonNode parts = chunk.get("candidates").get(0).get("content").get("parts");
            if (parts.get(0).has("text")) {
                concatenated.append(parts.get(0).get("text").asText());
            }
        }
        assertThat(concatenated.toString(), is("Hello world"));

        // Final chunk has finishReason and usageMetadata
        JsonNode finalChunk = OBJECT_MAPPER.readTree(events.get(events.size() - 1).getData());
        assertThat(finalChunk.get("candidates").get(0).get("finishReason").asText(), is("STOP"));
        assertThat(finalChunk.get("usageMetadata").get("promptTokenCount").asInt(), is(10));
        assertThat(finalChunk.get("usageMetadata").get("candidatesTokenCount").asInt(), is(5));
    }

    @Test
    public void shouldProduceStreamingChunksForToolCalls() throws Exception {
        Completion completion = completion()
            .withToolCall(toolUse("search").withArguments("{\"q\":\"test\"}"));

        List<SseEvent> events = codec.encodeStreaming(completion, "gemini-2.0-flash", null);

        // Tool call chunk + final chunk
        assertThat(events.size(), is(greaterThanOrEqualTo(2)));

        // Find tool call chunk
        boolean foundFunctionCall = false;
        for (SseEvent event : events) {
            JsonNode chunk = OBJECT_MAPPER.readTree(event.getData());
            JsonNode parts = chunk.get("candidates").get(0).get("content").get("parts");
            if (parts.size() > 0 && parts.get(0).has("functionCall")) {
                foundFunctionCall = true;
                assertThat(parts.get(0).get("functionCall").get("name").asText(), is("search"));
            }
        }
        assertThat("functionCall chunk expected", foundFunctionCall, is(true));
    }

    // --- Decode ---

    @Test
    public void shouldDecodeSimpleTextConversation() {
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"contents\": [\n" +
                "    {\"role\": \"user\", \"parts\": [{\"text\": \"Hello\"}]}\n" +
                "  ]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(1));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.USER));
        assertThat(parsed.getMessages().get(0).getTextContent(), is("Hello"));
    }

    @Test
    public void shouldDecodeModelAsAssistantRole() {
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"contents\": [\n" +
                "    {\"role\": \"user\", \"parts\": [{\"text\": \"Hi\"}]},\n" +
                "    {\"role\": \"model\", \"parts\": [{\"text\": \"Hello!\"}]}\n" +
                "  ]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(2));
        assertThat(parsed.getMessages().get(1).getRole(), is(ParsedMessage.Role.ASSISTANT));
        assertThat(parsed.getMessages().get(1).getTextContent(), is("Hello!"));
    }

    @Test
    public void shouldDecodeFunctionCallParts() {
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"contents\": [\n" +
                "    {\"role\": \"model\", \"parts\": [{\"functionCall\": {\"name\": \"search\", \"args\": {\"q\": \"test\"}}}]}\n" +
                "  ]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(1));
        ParsedMessage msg = parsed.getMessages().get(0);
        assertThat(msg.getRole(), is(ParsedMessage.Role.ASSISTANT));
        assertThat(msg.getToolCalls(), hasSize(1));
        assertThat(msg.getToolCalls().get(0).getName(), is("search"));
        assertThat(msg.getToolCalls().get(0).getArguments(), containsString("test"));
    }

    @Test
    public void shouldDecodeFunctionResponseAsToolRole() {
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"contents\": [\n" +
                "    {\"role\": \"user\", \"parts\": [{\"functionResponse\": {\"name\": \"search\", \"response\": {\"result\": \"data\"}}}]}\n" +
                "  ]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(1));
        ParsedMessage msg = parsed.getMessages().get(0);
        assertThat(msg.getRole(), is(ParsedMessage.Role.TOOL));
        assertThat(msg.getToolResults(), hasKey("search"));
        assertThat(msg.getToolResults().get("search"), containsString("data"));
    }

    @Test
    public void shouldReturnEmptyForMalformedJson() {
        HttpRequest request = request().withBody("not json");
        ParsedConversation parsed = codec.decode(request);
        assertThat(parsed.getMessages(), is(empty()));
    }

    @Test
    public void shouldReturnEmptyForNullBody() {
        ParsedConversation parsed = codec.decode(request());
        assertThat(parsed.getMessages(), is(empty()));
    }

    @Test
    public void shouldReturnEmptyForNullRequest() {
        ParsedConversation parsed = codec.decode(null);
        assertThat(parsed.getMessages(), is(empty()));
    }

    @Test
    public void shouldReturnEmptyForMissingContents() {
        HttpRequest request = request().withBody("{\"model\": \"gemini\"}");
        ParsedConversation parsed = codec.decode(request);
        assertThat(parsed.getMessages(), is(empty()));
    }

    @Test
    public void shouldEncodeEmbeddingInGeminiShape() throws Exception {
        // given
        EmbeddingResponse embedding = EmbeddingResponse.embedding()
            .withDimensions(8)
            .withDeterministicFromInput(true)
            .withSeed(7L);

        // when — Gemini embedContent shape: {"embedding":{"values":[...]}}
        HttpResponse response = codec.encodeEmbedding(embedding, "test input");

        // then
        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getFirstHeader("content-type"), is("application/json"));
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        JsonNode values = root.get("embedding").get("values");
        assertThat(values.isArray(), is(true));
        assertThat(values.size(), is(8));
    }

    @Test
    public void shouldDefaultGeminiEmbeddingDimensionsTo768() throws Exception {
        // given
        EmbeddingResponse embedding = EmbeddingResponse.embedding().withDeterministicFromInput(true);

        // when
        HttpResponse response = codec.encodeEmbedding(embedding, "test input");

        // then
        JsonNode values = OBJECT_MAPPER.readTree(response.getBodyAsString()).get("embedding").get("values");
        assertThat(values.size(), is(768));
    }

    @Test
    public void shouldDecodeFullFunctionCallLoop() {
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"contents\": [\n" +
                "    {\"role\": \"user\", \"parts\": [{\"text\": \"What is the weather?\"}]},\n" +
                "    {\"role\": \"model\", \"parts\": [{\"functionCall\": {\"name\": \"get_weather\", \"args\": {\"city\": \"Paris\"}}}]},\n" +
                "    {\"role\": \"user\", \"parts\": [{\"functionResponse\": {\"name\": \"get_weather\", \"response\": {\"temp\": \"18C\"}}}]},\n" +
                "    {\"role\": \"model\", \"parts\": [{\"text\": \"It is 18C in Paris.\"}]}\n" +
                "  ]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(4));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.USER));
        assertThat(parsed.getMessages().get(1).getRole(), is(ParsedMessage.Role.ASSISTANT));
        assertThat(parsed.getMessages().get(1).getToolCalls(), hasSize(1));
        assertThat(parsed.getMessages().get(2).getRole(), is(ParsedMessage.Role.TOOL));
        assertThat(parsed.getMessages().get(2).getToolResults(), hasKey("get_weather"));
        assertThat(parsed.getMessages().get(3).getRole(), is(ParsedMessage.Role.ASSISTANT));
        assertThat(parsed.getMessages().get(3).getTextContent(), is("It is 18C in Paris."));
    }

    @Test
    public void shouldProduceValidStreamingChunkWhenToolArgumentsAreNotJsonObject() throws Exception {
        // Regression: previously, raw non-object args were injected into the streaming
        // chunk JSON without parsing, producing malformed wire output. The fix wraps
        // non-object args in {"value":"..."} after attempting to parse.
        Completion completion = Completion.completion()
            .withToolCall(ToolUse.toolUse("search").withArguments("not_a_json_object"));

        List<SseEvent> events = codec.encodeStreaming(completion, "gemini-1.5-pro", null);

        SseEvent toolEvent = events.stream()
            .filter(e -> e.getData() != null && e.getData().contains("functionCall"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no function-call chunk emitted"));

        JsonNode chunk = new ObjectMapper().readTree(toolEvent.getData());
        JsonNode fnCall = chunk.path("candidates").path(0).path("content").path("parts").path(0).path("functionCall");
        assertThat(fnCall.path("name").asText(), is("search"));
        assertThat(fnCall.path("args").isObject(), is(true));
        assertThat(fnCall.path("args").path("value").asText(), is("not_a_json_object"));
    }

    @Test
    public void shouldProduceValidStreamingChunkWhenToolArgumentsAreInvalidJson() throws Exception {
        // Regression: malformed JSON in arguments must not corrupt the SSE chunk.
        Completion completion = Completion.completion()
            .withToolCall(ToolUse.toolUse("search").withArguments("{broken,json"));

        List<SseEvent> events = codec.encodeStreaming(completion, "gemini-1.5-pro", null);

        SseEvent toolEvent = events.stream()
            .filter(e -> e.getData() != null && e.getData().contains("functionCall"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no function-call chunk emitted"));

        // Must parse as valid JSON (this is the property that was broken).
        JsonNode chunk = new ObjectMapper().readTree(toolEvent.getData());
        assertThat(chunk.path("candidates").path(0).path("content").path("parts").path(0).path("functionCall").path("args").isObject(), is(true));
    }

    // --- Decode: systemInstruction (https://ai.google.dev/api/generate-content#request-body) ---

    @Test
    public void shouldDecodeSystemInstructionAsLeadingSystemMessage() {
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"systemInstruction\": {\"parts\": [{\"text\": \"You are a cat. \"}, {\"text\": \"Your name is Neko.\"}]},\n" +
                "  \"contents\": [{\"role\": \"user\", \"parts\": [{\"text\": \"Hello there\"}]}]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(2));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.SYSTEM));
        assertThat(parsed.getMessages().get(0).getTextContent(), is("You are a cat. Your name is Neko."));
        assertThat(parsed.getMessages().get(1).getRole(), is(ParsedMessage.Role.USER));
        assertThat(parsed.getMessages().get(1).getTextContent(), is("Hello there"));
    }

    @Test
    public void shouldDecodeSnakeCaseSystemInstructionWithSingleObjectPartsAndContents() {
        // the documented curl example: system_instruction, and parts / contents as single objects
        HttpRequest request = request()
            .withBody("{ \"system_instruction\": {\n" +
                "    \"parts\":\n" +
                "      { \"text\": \"You are a cat. Your name is Neko.\"}},\n" +
                "    \"contents\": {\n" +
                "      \"parts\": {\n" +
                "        \"text\": \"Hello there\"}}}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(2));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.SYSTEM));
        assertThat(parsed.getMessages().get(0).getTextContent(), is("You are a cat. Your name is Neko."));
        assertThat(parsed.getMessages().get(1).getRole(), is(ParsedMessage.Role.USER));
        assertThat(parsed.getMessages().get(1).getTextContent(), is("Hello there"));
    }

    @Test
    public void shouldNotAddSystemMessageWithoutSystemInstruction() {
        HttpRequest request = request()
            .withBody("{\"contents\": [{\"role\": \"user\", \"parts\": [{\"text\": \"Hello\"}]}]}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(1));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.USER));
    }
}
