package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.llm.StreamingFormat;
import org.mockserver.model.*;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.ToolUse.toolUse;

public class OllamaCodecTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final OllamaCodec codec = new OllamaCodec();

    // --- Provider & Version ---

    @Test
    public void shouldReturnOllamaProviderAndVersion() {
        assertThat(codec.provider(), is(Provider.OLLAMA));
        assertThat(codec.apiVersion(), is("ollama-2025"));
    }

    // --- Encode Non-Streaming ---

    @Test
    public void shouldEncodeTextOnlyCompletion() throws Exception {
        Completion completion = completion()
            .withText("The answer is 42.")
            .withUsage(Usage.usage().withInputTokens(10).withOutputTokens(5));

        HttpResponse response = codec.encode(completion, "llama3.1");

        assertThat(response.getStatusCode(), is(200));
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("model").asText(), is("llama3.1"));
        assertThat(root.has("created_at"), is(true));
        assertThat(root.get("done").asBoolean(), is(true));

        JsonNode message = root.get("message");
        assertThat(message.get("role").asText(), is("assistant"));
        assertThat(message.get("content").asText(), is("The answer is 42."));
        assertThat(message.has("tool_calls"), is(false));

        assertThat(root.get("prompt_eval_count").asInt(), is(10));
        assertThat(root.get("eval_count").asInt(), is(5));
    }

    @Test
    public void shouldEncodeToolCallWithJsonObjectArguments() throws Exception {
        Completion completion = completion()
            .withToolCall(toolUse("search").withArguments("{\"q\":\"foo\"}"));

        HttpResponse response = codec.encode(completion, "llama3.1");

        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        JsonNode message = root.get("message");
        assertThat(message.has("tool_calls"), is(true));
        JsonNode toolCalls = message.get("tool_calls");
        assertThat(toolCalls.size(), is(1));
        JsonNode tc = toolCalls.get(0);
        assertThat(tc.get("function").get("name").asText(), is("search"));
        // Ollama uses arguments as a JSON object, not a string
        assertThat(tc.get("function").get("arguments").isObject(), is(true));
        assertThat(tc.get("function").get("arguments").get("q").asText(), is("foo"));
    }

    @Test
    public void shouldEncodeTextAndToolCalls() throws Exception {
        Completion completion = completion()
            .withText("Let me check.")
            .withToolCall(toolUse("search").withArguments("{\"q\":\"test\"}"));

        HttpResponse response = codec.encode(completion, "llama3.1");

        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("message").get("content").asText(), is("Let me check."));
        assertThat(root.get("message").get("tool_calls").size(), is(1));
    }

    @Test
    public void shouldDefaultTokenCountsToZeros() throws Exception {
        Completion completion = completion().withText("test");

        HttpResponse response = codec.encode(completion, "llama3.1");

        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("prompt_eval_count").asInt(), is(0));
        assertThat(root.get("eval_count").asInt(), is(0));
    }

    @Test
    public void shouldSetContentTypeHeader() throws Exception {
        Completion completion = completion().withText("test");
        HttpResponse response = codec.encode(completion, "llama3.1");
        assertThat(response.getFirstHeader("content-type"), is("application/json"));
    }

    @Test
    public void shouldHandleNullText() throws Exception {
        Completion completion = completion()
            .withToolCall(toolUse("fn").withArguments("{}"));

        HttpResponse response = codec.encode(completion, "llama3.1");

        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.get("message").get("content").asText(), is(""));
    }

    @Test
    public void shouldIncludeDurationFields() throws Exception {
        Completion completion = completion().withText("test");

        HttpResponse response = codec.encode(completion, "llama3.1");

        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.has("total_duration"), is(true));
        assertThat(root.has("load_duration"), is(true));
        assertThat(root.has("prompt_eval_duration"), is(true));
        assertThat(root.has("eval_duration"), is(true));
    }

    // --- Encode Streaming ---

    @Test
    public void shouldProduceStreamingChunksForText() throws Exception {
        Completion completion = completion()
            .withText("Hello world")
            .withUsage(Usage.usage().withInputTokens(10).withOutputTokens(5));

        List<SseEvent> events = codec.encodeStreaming(completion, "llama3.1", null);

        // Text chunks + final done chunk
        assertThat(events.size(), is(greaterThanOrEqualTo(2)));

        // All but last should have done: false
        for (int i = 0; i < events.size() - 1; i++) {
            JsonNode chunk = OBJECT_MAPPER.readTree(events.get(i).getData());
            assertThat(chunk.get("done").asBoolean(), is(false));
            assertThat(chunk.get("message").get("role").asText(), is("assistant"));
        }

        // Last should have done: true
        JsonNode finalChunk = OBJECT_MAPPER.readTree(events.get(events.size() - 1).getData());
        assertThat(finalChunk.get("done").asBoolean(), is(true));
        assertThat(finalChunk.get("prompt_eval_count").asInt(), is(10));
        assertThat(finalChunk.get("eval_count").asInt(), is(5));
    }

    @Test
    public void shouldConcatenateStreamedTextTokens() throws Exception {
        Completion completion = completion().withText("Hello world");

        List<SseEvent> events = codec.encodeStreaming(completion, "llama3.1", null);

        StringBuilder concatenated = new StringBuilder();
        for (SseEvent event : events) {
            JsonNode chunk = OBJECT_MAPPER.readTree(event.getData());
            String content = chunk.get("message").get("content").asText("");
            if (!content.isEmpty()) {
                concatenated.append(content);
            }
        }
        assertThat(concatenated.toString(), is("Hello world"));
    }

    @Test
    public void shouldNotUseNamedSseEvents() throws Exception {
        Completion completion = completion().withText("test");

        List<SseEvent> events = codec.encodeStreaming(completion, "llama3.1", null);

        // Ollama uses NDJSON, represented as SSE without event names
        for (SseEvent event : events) {
            assertThat(event.getEvent(), is(nullValue()));
        }
    }

    // --- Decode ---

    @Test
    public void shouldDecodeSimpleTextConversation() {
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"model\": \"llama3.1\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"user\", \"content\": \"Hello\"}\n" +
                "  ]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(1));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.USER));
        assertThat(parsed.getMessages().get(0).getTextContent(), is("Hello"));
    }

    @Test
    public void shouldDecodeToolCallsOnAssistantMessages() {
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"model\": \"llama3.1\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"user\", \"content\": \"Search\"},\n" +
                "    {\"role\": \"assistant\", \"content\": \"\", \"tool_calls\": [\n" +
                "      {\"function\": {\"name\": \"search\", \"arguments\": {\"q\": \"test\"}}}\n" +
                "    ]}\n" +
                "  ]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(2));
        ParsedMessage assistant = parsed.getMessages().get(1);
        assertThat(assistant.getRole(), is(ParsedMessage.Role.ASSISTANT));
        assertThat(assistant.getToolCalls(), hasSize(1));
        assertThat(assistant.getToolCalls().get(0).getName(), is("search"));
        assertThat(assistant.getToolCalls().get(0).getArguments(), containsString("test"));
    }

    @Test
    public void shouldDecodeToolRoleMessages() {
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"model\": \"llama3.1\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"tool\", \"content\": \"tool result data\"}\n" +
                "  ]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(1));
        ParsedMessage msg = parsed.getMessages().get(0);
        assertThat(msg.getRole(), is(ParsedMessage.Role.TOOL));
        assertThat(msg.getTextContent(), is("tool result data"));
        assertThat(msg.getToolResults(), hasEntry("", "tool result data"));
    }

    @Test
    public void shouldDecodeSystemMessages() {
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"system\", \"content\": \"You are helpful.\"},\n" +
                "    {\"role\": \"user\", \"content\": \"Hi\"}\n" +
                "  ]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(2));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.SYSTEM));
        assertThat(parsed.getMessages().get(0).getTextContent(), is("You are helpful."));
    }

    @Test
    public void shouldReturnEmptyForMalformedJson() {
        ParsedConversation parsed = codec.decode(request().withBody("not json"));
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
    public void shouldReturnEmptyForMissingMessages() {
        ParsedConversation parsed = codec.decode(request().withBody("{\"model\": \"llama3.1\"}"));
        assertThat(parsed.getMessages(), is(empty()));
    }

    @Test
    public void shouldEncodeEmbeddingInOllamaEmbedShape() throws Exception {
        // given
        EmbeddingResponse embedding = EmbeddingResponse.embedding()
            .withDimensions(6)
            .withDeterministicFromInput(true)
            .withSeed(3L);

        // when — Ollama /api/embed shape: {"embeddings":[[...]]}
        HttpResponse response = codec.encodeEmbedding(embedding, "test input");

        // then
        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getFirstHeader("content-type"), is("application/json"));
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        JsonNode embeddings = root.get("embeddings");
        assertThat(embeddings.isArray(), is(true));
        assertThat(embeddings.size(), is(1));
        assertThat(embeddings.get(0).size(), is(6));
        assertThat(root.get("model").asText(), is("nomic-embed-text"));
        assertThat(root.get("prompt_eval_count").asInt(), is(greaterThanOrEqualTo(0)));
    }

    @Test
    public void shouldDefaultOllamaEmbeddingDimensionsTo768() throws Exception {
        // given
        EmbeddingResponse embedding = EmbeddingResponse.embedding().withDeterministicFromInput(true);

        // when
        HttpResponse response = codec.encodeEmbedding(embedding, "test input");

        // then
        JsonNode embeddings = OBJECT_MAPPER.readTree(response.getBodyAsString()).get("embeddings");
        assertThat(embeddings.get(0).size(), is(768));
    }

    @Test
    public void shouldDecodeFullToolCallLoop() {
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"model\": \"llama3.1\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"user\", \"content\": \"What is the weather?\"},\n" +
                "    {\"role\": \"assistant\", \"content\": \"\", \"tool_calls\": [\n" +
                "      {\"function\": {\"name\": \"get_weather\", \"arguments\": {\"city\": \"Paris\"}}}\n" +
                "    ]},\n" +
                "    {\"role\": \"tool\", \"content\": \"18C and sunny\"},\n" +
                "    {\"role\": \"assistant\", \"content\": \"It is 18C and sunny in Paris.\"}\n" +
                "  ]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(4));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.USER));
        assertThat(parsed.getMessages().get(1).getRole(), is(ParsedMessage.Role.ASSISTANT));
        assertThat(parsed.getMessages().get(1).getToolCalls(), hasSize(1));
        assertThat(parsed.getMessages().get(2).getRole(), is(ParsedMessage.Role.TOOL));
        assertThat(parsed.getMessages().get(3).getRole(), is(ParsedMessage.Role.ASSISTANT));
        assertThat(parsed.getMessages().get(3).getTextContent(), is("It is 18C and sunny in Paris."));
    }

    @Test
    public void shouldStreamToolCallsInADoneFalseMessageThenAnEmptyFinalMessage() throws Exception {
        // Ollama docs/api.md "Chat request (Streaming with tools)": the tool calls arrive in a
        // done:false message; the done:true message is empty and carries done_reason and the counts.
        Completion completion = Completion.completion()
            .withToolCall(ToolUse.toolUse("search").withArguments("{\"q\":\"weather\"}"))
            .withToolCall(ToolUse.toolUse("get_weather").withArguments("{\"city\":\"Paris\"}"))
            .withUsage(Usage.usage().withInputTokens(169).withOutputTokens(15));

        List<SseEvent> events = codec.encodeStreaming(completion, "llama3.1", null);

        assertThat(events, hasSize(2));
        JsonNode toolCallChunk = OBJECT_MAPPER.readTree(events.get(0).getData());
        assertThat(toolCallChunk.path("done").isBoolean(), is(true));
        assertThat(toolCallChunk.path("done").asBoolean(), is(false));
        assertThat(toolCallChunk.has("done_reason"), is(false));
        assertThat(toolCallChunk.has("eval_count"), is(false));
        assertThat(toolCallChunk.path("message").path("role").asText(), is("assistant"));
        assertThat(toolCallChunk.path("message").path("content").asText(), is(""));
        JsonNode toolCalls = toolCallChunk.path("message").path("tool_calls");
        assertThat(toolCalls.size(), is(2));
        assertThat(toolCalls.path(0).path("function").path("name").asText(), is("search"));
        assertThat(toolCalls.path(0).path("function").path("arguments").path("q").asText(), is("weather"));
        assertThat(toolCalls.path(1).path("function").path("name").asText(), is("get_weather"));
        assertThat(toolCalls.path(1).path("function").path("arguments").path("city").asText(), is("Paris"));

        JsonNode finalChunk = OBJECT_MAPPER.readTree(events.get(1).getData());
        assertThat(finalChunk.path("done").asBoolean(), is(true));
        assertThat(finalChunk.path("done_reason").asText(), is("stop"));
        assertThat(finalChunk.path("prompt_eval_count").asInt(), is(169));
        assertThat(finalChunk.path("eval_count").asInt(), is(15));
        assertThat(finalChunk.path("message"), is(OBJECT_MAPPER.readTree("{\"role\":\"assistant\",\"content\":\"\"}")));
    }

    @Test
    public void shouldFallBackForNonObjectToolArgumentsInStreaming() throws Exception {
        // Non-JSON-object arguments are wrapped in {"value": "..."} for safety.
        Completion completion = Completion.completion()
            .withToolCall(ToolUse.toolUse("search").withArguments("raw_string_args"));

        List<SseEvent> events = codec.encodeStreaming(completion, "llama3.1", null);

        JsonNode toolCallChunk = OBJECT_MAPPER.readTree(events.get(0).getData());
        JsonNode args = toolCallChunk.path("message").path("tool_calls").path(0).path("function").path("arguments");
        assertThat(args.isObject(), is(true));
        assertThat(args.path("value").asText(), is("raw_string_args"));
    }

    // --- NDJSON Streaming Format ---

    @Test
    public void shouldDeclareNdjsonStreamingFormat() {
        assertThat(codec.streamingFormat(), is(StreamingFormat.NDJSON));
    }

    @Test
    public void shouldProduceValidNdjsonWhenFormattedAsLines() throws Exception {
        // Verifies that each SseEvent's data field is valid JSON that can be
        // assembled into a valid NDJSON stream (one JSON object per line).
        Completion completion = completion()
            .withText("Hello world")
            .withUsage(Usage.usage().withInputTokens(10).withOutputTokens(5));

        List<SseEvent> events = codec.encodeStreaming(completion, "llama3.1", null);

        // Simulate NDJSON wire format: each event.getData() + "\n"
        StringBuilder ndjsonStream = new StringBuilder();
        for (SseEvent event : events) {
            ndjsonStream.append(event.getData()).append("\n");
        }

        // Parse the assembled NDJSON: every line must be valid JSON
        String ndjson = ndjsonStream.toString();
        BufferedReader reader = new BufferedReader(new StringReader(ndjson));
        List<JsonNode> parsedLines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                parsedLines.add(OBJECT_MAPPER.readTree(line));
            }
        }

        assertThat("each line should parse as JSON", parsedLines.size(), is(events.size()));

        // No SSE framing should be present in the data
        assertThat("NDJSON must not contain SSE data: prefix",
            ndjson, not(containsString("data: ")));
        assertThat("NDJSON must not contain SSE event: prefix",
            ndjson, not(containsString("event: ")));
    }

    @Test
    public void shouldHaveDoneTrueInFinalNdjsonLine() throws Exception {
        Completion completion = completion()
            .withText("test")
            .withUsage(Usage.usage().withInputTokens(5).withOutputTokens(2));

        List<SseEvent> events = codec.encodeStreaming(completion, "llama3.1", null);

        // Simulate NDJSON: parse each line
        List<JsonNode> lines = new ArrayList<>();
        for (SseEvent event : events) {
            lines.add(OBJECT_MAPPER.readTree(event.getData()));
        }

        // Final line must have done:true
        JsonNode lastLine = lines.get(lines.size() - 1);
        assertThat(lastLine.get("done").asBoolean(), is(true));
        assertThat(lastLine.get("prompt_eval_count").asInt(), is(5));
        assertThat(lastLine.get("eval_count").asInt(), is(2));

        // All preceding lines must have done:false
        for (int i = 0; i < lines.size() - 1; i++) {
            assertThat("line " + i + " should have done:false",
                lines.get(i).get("done").asBoolean(), is(false));
        }
    }

    @Test
    public void shouldNotContainSseFramingInStreamingData() throws Exception {
        // Ensures that the raw data in each SseEvent does not accidentally
        // include SSE framing characters that would corrupt NDJSON output.
        Completion completion = completion().withText("Hello world test");

        List<SseEvent> events = codec.encodeStreaming(completion, "llama3.1", null);

        for (int i = 0; i < events.size(); i++) {
            String data = events.get(i).getData();
            assertThat("event " + i + " data should not start with 'data:'",
                data, not(startsWith("data:")));
            // Each data value should be parseable as standalone JSON
            JsonNode parsed = OBJECT_MAPPER.readTree(data);
            assertThat("event " + i + " should be a JSON object",
                parsed.isObject(), is(true));
        }
    }

    @Test
    public void shouldStreamTextThenToolCallsThenAnEmptyFinalLine() throws Exception {
        Completion completion = Completion.completion()
            .withText("checking")
            .withToolCall(ToolUse.toolUse("search").withArguments("{\"q\":\"test\"}"))
            .withUsage(Usage.usage().withInputTokens(3).withOutputTokens(2));

        List<SseEvent> events = codec.encodeStreaming(completion, "llama3.1", null);

        List<JsonNode> lines = new ArrayList<>();
        for (SseEvent event : events) {
            lines.add(OBJECT_MAPPER.readTree(event.getData()));
        }
        assertThat(lines.size(), greaterThanOrEqualTo(3));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines.size() - 2; i++) {
            assertThat(lines.get(i).path("done").asBoolean(), is(false));
            assertThat(lines.get(i).path("message").has("tool_calls"), is(false));
            text.append(lines.get(i).path("message").path("content").asText());
        }
        assertThat(text.toString(), is("checking"));

        JsonNode toolCallLine = lines.get(lines.size() - 2);
        assertThat(toolCallLine.path("done").asBoolean(), is(false));
        assertThat(toolCallLine.path("message").path("content").asText(), is(""));
        assertThat(toolCallLine.path("message").path("tool_calls").size(), is(1));
        assertThat(toolCallLine.path("message").path("tool_calls").path(0)
            .path("function").path("name").asText(), is("search"));

        JsonNode finalLine = lines.get(lines.size() - 1);
        assertThat(finalLine.path("done").asBoolean(), is(true));
        assertThat(finalLine.path("message").has("tool_calls"), is(false));
        assertThat(finalLine.path("message").path("content").asText(), is(""));
        assertThat(finalLine.path("prompt_eval_count").asInt(), is(3));
        assertThat(finalLine.path("eval_count").asInt(), is(2));
    }

    // --- Decode: POST /api/generate (https://github.com/ollama/ollama/blob/main/docs/api.md) ---

    @Test
    public void shouldDecodeGenerateRequestPromptAndSystem() {
        HttpRequest request = request()
            .withPath("/api/generate")
            .withBody("{\"model\": \"llama3.2\", \"system\": \"You are terse.\", \"prompt\": \"Why is the sky blue?\", \"stream\": false}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(2));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.SYSTEM));
        assertThat(parsed.getMessages().get(0).getTextContent(), is("You are terse."));
        assertThat(parsed.getMessages().get(1).getRole(), is(ParsedMessage.Role.USER));
        assertThat(parsed.getMessages().get(1).getTextContent(), is("Why is the sky blue?"));
    }

    @Test
    public void shouldDecodeGenerateRequestWithoutSystem() {
        HttpRequest request = request()
            .withPath("/api/generate")
            .withBody("{\"model\": \"llama3.2\", \"prompt\": \"Why is the sky blue?\"}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(1));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.USER));
        assertThat(parsed.getMessages().get(0).getTextContent(), is("Why is the sky blue?"));
    }

    @Test
    public void shouldDecodeBodyWithNeitherMessagesNorPromptAsEmpty() {
        HttpRequest request = request()
            .withPath("/api/generate")
            .withBody("{\"model\": \"llama3.2\"}");

        assertThat(codec.decode(request).getMessages(), is(empty()));
    }

    // --- Encode: POST /api/generate (docs/api.md "Generate a completion"; api/types.go GenerateResponse) ---

    private static HttpRequest generateRequest(String body) {
        return request().withMethod("POST").withPath("/api/generate").withBody(body);
    }

    private List<JsonNode> streamedLines(List<SseEvent> events) throws Exception {
        List<JsonNode> lines = new ArrayList<>();
        for (SseEvent event : events) {
            assertThat(event.getEvent(), is(nullValue()));
            lines.add(OBJECT_MAPPER.readTree(event.getData()));
        }
        return lines;
    }

    @Test
    public void shouldEncodeGenerateReplyWithTextInTopLevelResponseField() throws Exception {
        Completion completion = completion()
            .withText("The sky is blue because it is the color of the sky.")
            .withUsage(Usage.usage().withInputTokens(26).withOutputTokens(290));

        HttpResponse response = codec.encode(completion, "llama3.2",
            generateRequest("{\"model\":\"llama3.2\",\"prompt\":\"Why is the sky blue?\",\"stream\":false}"));

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getFirstHeader("content-type"), is("application/json"));
        JsonNode root = OBJECT_MAPPER.readTree(response.getBodyAsString());
        assertThat(root.path("model").asText(), is("llama3.2"));
        assertThat(root.path("created_at").isTextual(), is(true));
        assertThat(root.path("response").asText(), is("The sky is blue because it is the color of the sky."));
        assertThat(root.has("message"), is(false));
        assertThat(root.has("thinking"), is(false));
        assertThat(root.has("tool_calls"), is(false));
        assertThat(root.path("done").isBoolean(), is(true));
        assertThat(root.path("done").asBoolean(), is(true));
        assertThat(root.path("done_reason").asText(), is("stop"));
        assertThat(root.path("context").isArray(), is(true));
        assertThat(root.path("context").size(), greaterThan(0));
        for (JsonNode token : root.path("context")) {
            assertThat(token.isInt(), is(true));
        }
        assertThat(root.path("prompt_eval_count").asInt(), is(26));
        assertThat(root.path("eval_count").asInt(), is(290));
        for (String duration : new String[]{"total_duration", "load_duration", "prompt_eval_duration", "eval_duration"}) {
            assertThat(duration, root.path(duration).isIntegralNumber(), is(true));
        }
    }

    @Test
    public void shouldEncodeGenerateReplyWithLengthDoneReasonThinkingAndToolCalls() throws Exception {
        Completion completion = completion()
            .withText("partial")
            .withReasoningText("Let me think.")
            .withStopReason("max_tokens")
            .withToolCall(toolUse("get_weather").withArguments("{\"city\":\"Tokyo\"}"));

        JsonNode root = OBJECT_MAPPER.readTree(codec.encode(completion, "qwen3",
            generateRequest("{\"model\":\"qwen3\",\"prompt\":\"hi\",\"think\":true,\"stream\":false}")).getBodyAsString());

        assertThat(root.path("response").asText(), is("partial"));
        assertThat(root.path("thinking").asText(), is("Let me think."));
        assertThat(root.path("done_reason").asText(), is("length"));
        assertThat(root.path("tool_calls").size(), is(1));
        assertThat(root.path("tool_calls").path(0).path("function").path("name").asText(), is("get_weather"));
        assertThat(root.path("tool_calls").path(0).path("function").path("arguments").path("city").asText(), is("Tokyo"));
        assertThat(root.has("message"), is(false));
    }

    @Test
    public void shouldOmitGenerateContextForRawPrompt() throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(codec.encode(completion().withText("x"), "llama3.2",
            generateRequest("{\"model\":\"llama3.2\",\"prompt\":\"[INST] hi [/INST]\",\"raw\":true,\"stream\":false}")).getBodyAsString());

        assertThat(root.path("response").asText(), is("x"));
        assertThat(root.has("context"), is(false));
    }

    @Test
    public void shouldEncodeGenerateReplyForPrefixedPathAndAnyCase() throws Exception {
        HttpRequest request = request().withPath("/ollama/API/Generate").withBody("{\"prompt\":\"hi\"}");

        JsonNode root = OBJECT_MAPPER.readTree(codec.encode(completion().withText("hello"), "llama3.2", request).getBodyAsString());

        assertThat(root.path("response").asText(), is("hello"));
    }

    @Test
    public void shouldKeepChatShapeForChatPathAndPathlessEncode() throws Exception {
        HttpRequest chat = request().withPath("/api/chat").withBody("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");

        JsonNode viaChat = OBJECT_MAPPER.readTree(codec.encode(completion().withText("hello"), "llama3.2", chat).getBodyAsString());
        JsonNode pathless = OBJECT_MAPPER.readTree(codec.encode(completion().withText("hello"), "llama3.2").getBodyAsString());

        for (JsonNode root : new JsonNode[]{viaChat, pathless}) {
            assertThat(root.path("message").path("content").asText(), is("hello"));
            assertThat(root.has("response"), is(false));
            assertThat(root.has("context"), is(false));
        }
        assertThat(codec.encodeStreaming(completion().withText("hello"), "llama3.2", null, chat).get(0).getData(),
            containsString("\"message\""));
    }

    @Test
    public void shouldStreamGenerateReplyAsResponsePiecesThenAFinalLine() throws Exception {
        Completion completion = completion()
            .withText("The sky is blue")
            .withUsage(Usage.usage().withInputTokens(26).withOutputTokens(259))
            .withStreaming(true);

        List<JsonNode> lines = streamedLines(codec.encodeStreaming(completion, "llama3.2", null,
            generateRequest("{\"model\":\"llama3.2\",\"prompt\":\"Why is the sky blue?\"}")));

        assertThat(lines.size(), greaterThanOrEqualTo(2));
        StringBuilder text = new StringBuilder();
        for (JsonNode line : lines.subList(0, lines.size() - 1)) {
            assertThat(line.path("model").asText(), is("llama3.2"));
            assertThat(line.path("created_at").isTextual(), is(true));
            assertThat(line.path("response").isTextual(), is(true));
            assertThat(line.path("done").asBoolean(true), is(false));
            assertThat(line.has("message"), is(false));
            assertThat(line.has("done_reason"), is(false));
            assertThat(line.has("context"), is(false));
            assertThat(line.has("eval_count"), is(false));
            text.append(line.path("response").asText());
        }
        assertThat(text.toString(), is("The sky is blue"));

        JsonNode last = lines.get(lines.size() - 1);
        assertThat(last.path("response").isTextual(), is(true));
        assertThat(last.path("response").asText(), is(""));
        assertThat(last.path("done").asBoolean(), is(true));
        assertThat(last.path("done_reason").asText(), is("stop"));
        assertThat(last.path("context").isArray(), is(true));
        assertThat(last.path("prompt_eval_count").asInt(), is(26));
        assertThat(last.path("eval_count").asInt(), is(259));
        assertThat(last.has("message"), is(false));
        assertThat(last.has("tool_calls"), is(false));
    }

    @Test
    public void shouldStreamGenerateThinkingFirstAndToolCallsOnTheFinalLine() throws Exception {
        Completion completion = completion()
            .withReasoningText("Thinking it over.")
            .withText("ok")
            .withToolCall(toolUse("get_weather").withArguments("{\"city\":\"Tokyo\"}"))
            .withStreaming(true);

        List<JsonNode> lines = streamedLines(codec.encodeStreaming(completion, "qwen3", null,
            generateRequest("{\"model\":\"qwen3\",\"prompt\":\"hi\",\"think\":true,\"raw\":true}")));

        JsonNode first = lines.get(0);
        assertThat(first.path("thinking").asText(), is("Thinking it over."));
        assertThat(first.path("response").asText("missing"), is(""));
        assertThat(first.path("done").asBoolean(true), is(false));
        for (JsonNode line : lines.subList(0, lines.size() - 1)) {
            assertThat(line.has("tool_calls"), is(false));
        }
        JsonNode last = lines.get(lines.size() - 1);
        assertThat(last.path("done").asBoolean(), is(true));
        assertThat(last.path("tool_calls").path(0).path("function").path("name").asText(), is("get_weather"));
        assertThat(last.path("tool_calls").path(0).path("function").path("arguments").path("city").asText(), is("Tokyo"));
        assertThat(last.has("context"), is(false));
    }
}
