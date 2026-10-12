package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.llm.ProviderCodec;
import org.mockserver.llm.ProviderCodecRegistry;
import org.mockserver.model.*;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.ToolUse.toolUse;
import static org.mockserver.model.Usage.usage;

/**
 * Structural wire-contract test for LLM provider codecs.
 * <p>
 * <strong>Why this exists — breaking the golden self-derivation.</strong> The
 * companion {@link LlmCodecGoldenFileTest} regenerates its golden bodies from
 * the codec itself ({@code -Dmockserver.updateLlmGoldens=true}). That makes the
 * golden bodies <em>self-derived</em>: a structural codec defect (a renamed
 * field, a wrong SSE event name, a dropped {@code finish_reason}) bakes straight
 * into its own golden, and the byte-for-byte drift test then passes forever,
 * confirming only that the codec is consistent with itself.
 * <p>
 * This test closes that blind spot with <strong>hand-authored structural
 * assertions sourced from each provider's published API schema</strong> — not
 * from the codec. It encodes the same canonical completions and asserts the
 * shape of the wire body and the SSE/NDJSON streaming framing against those
 * externally-authored expectations: required fields, JSON types, the enum
 * discriminators each provider uses ({@code object} / {@code type} /
 * {@code finish_reason} / {@code stop_reason} / {@code finishReason} /
 * {@code status} / {@code done}), the tool-call envelope shape, and the exact
 * SSE event-name sequence.
 * <p>
 * <strong>Crucially, this test never reads the golden files and is not affected
 * by {@code -Dmockserver.updateLlmGoldens=true}.</strong> It reads live codec
 * output only. Regenerating goldens therefore cannot silence it: a codec that
 * renamed {@code finish_reason}, emitted {@code content_delta} instead of
 * {@code content_block_delta}, or dropped its terminal marker would still match
 * its regenerated golden but fail here. A future maintainer must not "fix" a
 * failure of this test by regenerating goldens — the assertion encodes what the
 * provider's real API requires, independent of what our codec currently emits.
 * <p>
 * Residual streaming-over-the-wire behaviour (real client, real socket) is
 * additionally covered by {@code LlmAgentLoopE2eTest}; this test targets the
 * static wire <em>shape</em> that goldens alone cannot vouch for.
 */
public class LlmCodecStructuralContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Sentinel returned by {@link JsonNode#asText(String)} when a field is
     * absent. Any assertion comparing against a real value fails if the field
     * was renamed or dropped, rather than silently defaulting to {@code ""}.
     */
    private static final String ABSENT = "<absent>";

    private static final String TEXT = "Hello! How can I help you today?";
    private static final String TOOL_ARGS_JSON = "{\"city\":\"London\",\"units\":\"celsius\"}";

    private static final Completion TEXT_COMPLETION = completion()
        .withText(TEXT)
        .withUsage(usage().withInputTokens(12).withOutputTokens(8));

    private static final Completion TOOL_CALL_COMPLETION = completion()
        .withToolCall(toolUse("get_weather").withArguments(TOOL_ARGS_JSON))
        .withUsage(usage().withInputTokens(25).withOutputTokens(15));

    private static final Map<Provider, String> CANONICAL_MODELS;

    static {
        Map<Provider, String> m = new EnumMap<>(Provider.class);
        m.put(Provider.OPENAI, "gpt-4o");
        m.put(Provider.OPENAI_RESPONSES, "gpt-4o");
        m.put(Provider.ANTHROPIC, "claude-sonnet-4-20250514");
        m.put(Provider.GEMINI, "gemini-1.5-pro");
        m.put(Provider.BEDROCK, "anthropic.claude-sonnet-4-20250514-v1:0");
        m.put(Provider.AZURE_OPENAI, "gpt-4o");
        m.put(Provider.OLLAMA, "llama3.1");
        CANONICAL_MODELS = Collections.unmodifiableMap(m);
    }

    /**
     * The seven chat/completion providers whose wire body/streaming shape this
     * test pins. Rerank-only (Cohere, Voyage) and OpenAI-chat-compatible aliases
     * (Mistral, xAI, DeepSeek, Groq, OpenRouter, OrcaRouter) are excluded for the same
     * reasons documented in {@link LlmCodecGoldenFileTest}.
     */
    private static final Set<Provider> CHAT_PROVIDERS =
        Collections.unmodifiableSet(EnumSet.of(
            Provider.OPENAI, Provider.AZURE_OPENAI, Provider.ANTHROPIC, Provider.BEDROCK,
            Provider.GEMINI, Provider.OPENAI_RESPONSES, Provider.OLLAMA));

    // -----------------------------------------------------------------------
    // Non-streaming body structure
    // -----------------------------------------------------------------------

    @Test
    public void shouldEncodeCanonicalNonStreamingBodyStructure() {
        ProviderCodecRegistry registry = ProviderCodecRegistry.getInstance();
        List<String> asserted = new ArrayList<>();

        for (Provider provider : CHAT_PROVIDERS) {
            Optional<ProviderCodec> optCodec = registry.lookup(provider);
            assertThat("Chat provider " + provider + " must be registered", optCodec.isPresent(), is(true));
            ProviderCodec codec = optCodec.get();
            String model = CANONICAL_MODELS.get(provider);

            assertTextBodyStructure(provider, encodeTree(codec, TEXT_COMPLETION, model));
            assertToolBodyStructure(provider, encodeTree(codec, TOOL_CALL_COMPLETION, model));
            asserted.add(provider.name());
        }

        assertThat("Structural body assertions must cover all 7 chat/completion providers: " + asserted,
            asserted.size(), is(CHAT_PROVIDERS.size()));
    }

    private void assertTextBodyStructure(Provider provider, JsonNode root) {
        String ctx = provider + " text body";
        switch (provider) {
            case OPENAI:
            case AZURE_OPENAI: {
                assertThat(ctx + " object", root.path("object").asText(ABSENT), is("chat.completion"));
                assertThat(ctx + " id present", root.has("id"), is(true));
                assertThat(ctx + " model present", root.has("model"), is(true));
                JsonNode choice = root.path("choices").path(0);
                assertThat(ctx + " choices[0].index", choice.path("index").asInt(-1), is(0));
                JsonNode msg = choice.path("message");
                assertThat(ctx + " role", msg.path("role").asText(ABSENT), is("assistant"));
                assertThat(ctx + " content", msg.path("content").asText(ABSENT), is(TEXT));
                assertThat(ctx + " finish_reason", choice.path("finish_reason").asText(ABSENT), is("stop"));
                break;
            }
            case ANTHROPIC:
            case BEDROCK: {
                assertThat(ctx + " type", root.path("type").asText(ABSENT), is("message"));
                assertThat(ctx + " role", root.path("role").asText(ABSENT), is("assistant"));
                assertThat(ctx + " id present", root.has("id"), is(true));
                JsonNode block = root.path("content").path(0);
                assertThat(ctx + " content[0].type", block.path("type").asText(ABSENT), is("text"));
                assertThat(ctx + " content[0].text", block.path("text").asText(ABSENT), is(TEXT));
                assertThat(ctx + " stop_reason", root.path("stop_reason").asText(ABSENT), is("end_turn"));
                break;
            }
            case GEMINI: {
                JsonNode cand = root.path("candidates").path(0);
                assertThat(ctx + " content.role", cand.path("content").path("role").asText(ABSENT), is("model"));
                assertThat(ctx + " parts[0].text",
                    cand.path("content").path("parts").path(0).path("text").asText(ABSENT), is(TEXT));
                assertThat(ctx + " finishReason", cand.path("finishReason").asText(ABSENT), is("STOP"));
                assertThat(ctx + " usageMetadata present", root.has("usageMetadata"), is(true));
                break;
            }
            case OPENAI_RESPONSES: {
                assertThat(ctx + " object", root.path("object").asText(ABSENT), is("response"));
                assertThat(ctx + " status", root.path("status").asText(ABSENT), is("completed"));
                assertThat(ctx + " id present", root.has("id"), is(true));
                JsonNode item = root.path("output").path(0);
                assertThat(ctx + " output[0].type", item.path("type").asText(ABSENT), is("message"));
                assertThat(ctx + " output[0].role", item.path("role").asText(ABSENT), is("assistant"));
                JsonNode content = item.path("content").path(0);
                assertThat(ctx + " content[0].type", content.path("type").asText(ABSENT), is("output_text"));
                assertThat(ctx + " content[0].text", content.path("text").asText(ABSENT), is(TEXT));
                assertThat(ctx + " content[0].annotations is array", content.path("annotations").isArray(), is(true));
                assertThat(ctx + " output[0].status", item.path("status").asText(ABSENT), is("completed"));
                // Response fields openai-python declares without a default.
                assertThat(ctx + " parallel_tool_calls is boolean", root.path("parallel_tool_calls").isBoolean(), is(true));
                assertThat(ctx + " tool_choice present", root.has("tool_choice"), is(true));
                assertThat(ctx + " tools is array", root.path("tools").isArray(), is(true));
                assertThat(ctx + " created_at is number", root.path("created_at").isNumber(), is(true));
                assertThat(ctx + " usage.input_tokens_details.cached_tokens",
                    root.path("usage").path("input_tokens_details").path("cached_tokens").isInt(), is(true));
                assertThat(ctx + " usage.output_tokens_details.reasoning_tokens",
                    root.path("usage").path("output_tokens_details").path("reasoning_tokens").isInt(), is(true));
                break;
            }
            case OLLAMA: {
                assertThat(ctx + " model present", root.has("model"), is(true));
                assertThat(ctx + " done", root.path("done").asBoolean(false), is(true));
                assertThat(ctx + " done_reason", root.path("done_reason").asText(ABSENT), is("stop"));
                JsonNode msg = root.path("message");
                assertThat(ctx + " message.role", msg.path("role").asText(ABSENT), is("assistant"));
                assertThat(ctx + " message.content", msg.path("content").asText(ABSENT), is(TEXT));
                break;
            }
            default:
                fail("No non-streaming text structural contract defined for provider " + provider);
        }
    }

    private void assertToolBodyStructure(Provider provider, JsonNode root) {
        String ctx = provider + " tool body";
        switch (provider) {
            case OPENAI:
            case AZURE_OPENAI: {
                JsonNode choice = root.path("choices").path(0);
                assertThat(ctx + " finish_reason", choice.path("finish_reason").asText(ABSENT), is("tool_calls"));
                JsonNode msg = choice.path("message");
                assertThat(ctx + " content null", msg.path("content").isNull(), is(true));
                JsonNode call = msg.path("tool_calls").path(0);
                assertThat(ctx + " tool_calls[0].type", call.path("type").asText(ABSENT), is("function"));
                assertThat(ctx + " function.name", call.path("function").path("name").asText(ABSENT), is("get_weather"));
                // OpenAI serialises the arguments as a JSON *string*, not an object.
                assertThat(ctx + " function.arguments is string", call.path("function").path("arguments").isTextual(), is(true));
                assertThat(ctx + " function.arguments", call.path("function").path("arguments").asText(ABSENT), is(TOOL_ARGS_JSON));
                break;
            }
            case ANTHROPIC:
            case BEDROCK: {
                assertThat(ctx + " stop_reason", root.path("stop_reason").asText(ABSENT), is("tool_use"));
                JsonNode block = root.path("content").path(0);
                assertThat(ctx + " content[0].type", block.path("type").asText(ABSENT), is("tool_use"));
                assertThat(ctx + " content[0].name", block.path("name").asText(ABSENT), is("get_weather"));
                // Anthropic emits the arguments as a structured object under "input".
                assertThat(ctx + " input is object", block.path("input").isObject(), is(true));
                assertThat(ctx + " input.city", block.path("input").path("city").asText(ABSENT), is("London"));
                break;
            }
            case GEMINI: {
                JsonNode cand = root.path("candidates").path(0);
                JsonNode fn = cand.path("content").path("parts").path(0).path("functionCall");
                assertThat(ctx + " functionCall.name", fn.path("name").asText(ABSENT), is("get_weather"));
                assertThat(ctx + " functionCall.args is object", fn.path("args").isObject(), is(true));
                assertThat(ctx + " functionCall.args.city", fn.path("args").path("city").asText(ABSENT), is("London"));
                assertThat(ctx + " finishReason", cand.path("finishReason").asText(ABSENT), is("STOP"));
                break;
            }
            case OPENAI_RESPONSES: {
                assertThat(ctx + " status", root.path("status").asText(ABSENT), is("completed"));
                JsonNode item = root.path("output").path(0);
                assertThat(ctx + " output[0].type", item.path("type").asText(ABSENT), is("function_call"));
                assertThat(ctx + " output[0].name", item.path("name").asText(ABSENT), is("get_weather"));
                // Responses emits function-call arguments as a JSON *string*.
                assertThat(ctx + " arguments is string", item.path("arguments").isTextual(), is(true));
                assertThat(ctx + " arguments", item.path("arguments").asText(ABSENT), is(TOOL_ARGS_JSON));
                // function_call_output echoes call_id, which is distinct from the fc_ item id.
                assertThat(ctx + " output[0].call_id", item.path("call_id").asText(ABSENT), startsWith("call_"));
                assertThat(ctx + " output[0].id", item.path("id").asText(ABSENT), startsWith("fc_"));
                break;
            }
            case OLLAMA: {
                assertThat(ctx + " done", root.path("done").asBoolean(false), is(true));
                // Ollama has no tool-call done_reason: a tool call ends with stop.
                assertThat(ctx + " done_reason", root.path("done_reason").asText(ABSENT), is("stop"));
                JsonNode fn = root.path("message").path("tool_calls").path(0).path("function");
                assertThat(ctx + " function.name", fn.path("name").asText(ABSENT), is("get_weather"));
                // Ollama emits tool-call arguments as a structured object.
                assertThat(ctx + " arguments is object", fn.path("arguments").isObject(), is(true));
                assertThat(ctx + " arguments.city", fn.path("arguments").path("city").asText(ABSENT), is("London"));
                break;
            }
            default:
                fail("No non-streaming tool structural contract defined for provider " + provider);
        }
    }

    // -----------------------------------------------------------------------
    // Streaming framing structure
    // -----------------------------------------------------------------------

    @Test
    public void shouldEncodeCanonicalStreamingFramingStructure() {
        ProviderCodecRegistry registry = ProviderCodecRegistry.getInstance();
        List<String> asserted = new ArrayList<>();

        for (Provider provider : CHAT_PROVIDERS) {
            Optional<ProviderCodec> optCodec = registry.lookup(provider);
            assertThat("Chat provider " + provider + " must be registered", optCodec.isPresent(), is(true));
            ProviderCodec codec = optCodec.get();
            String model = CANONICAL_MODELS.get(provider);

            assertTextStreamingStructure(provider, streamEvents(codec, TEXT_COMPLETION, model));
            assertToolStreamingStructure(provider, streamEvents(codec, TOOL_CALL_COMPLETION, model));
            asserted.add(provider.name());
        }

        assertThat("Structural streaming assertions must cover all 7 chat/completion providers: " + asserted,
            asserted.size(), is(CHAT_PROVIDERS.size()));
    }

    private void assertTextStreamingStructure(Provider provider, List<StreamEvent> events) {
        String ctx = provider + " text stream";
        assertThat(ctx + " emitted events", events, is(not(empty())));
        switch (provider) {
            case OPENAI:
            case AZURE_OPENAI: {
                assertThat(ctx + " terminal [DONE] sentinel", lastDataLiteral(events), is("[DONE]"));
                List<JsonNode> chunks = jsonPayloads(events);
                for (JsonNode chunk : chunks) {
                    assertThat(ctx + " chunk object", chunk.path("object").asText(ABSENT), is("chat.completion.chunk"));
                }
                assertThat(ctx + " first chunk delta.role",
                    chunks.get(0).path("choices").path(0).path("delta").path("role").asText(ABSENT), is("assistant"));
                assertThat(ctx + " reassembled text",
                    concat(chunks, c -> c.path("choices").path(0).path("delta").path("content").asText("")), is(TEXT));
                assertThat(ctx + " terminal finish_reason",
                    lastNonNull(chunks, c -> textOrNull(c.path("choices").path(0).path("finish_reason"))), is("stop"));
                break;
            }
            case ANTHROPIC:
            case BEDROCK: {
                List<String> names = eventNames(events);
                assertThat(ctx + " first event", names.get(0), is("message_start"));
                assertThat(ctx + " last event", names.get(names.size() - 1), is("message_stop"));
                assertThat(ctx + " event sequence",
                    names, hasItems("content_block_start", "content_block_delta", "content_block_stop", "message_delta"));
                assertThat(ctx + " reassembled text",
                    concat(dataOf(events, "content_block_delta"),
                        d -> "text_delta".equals(d.path("delta").path("type").asText())
                            ? d.path("delta").path("text").asText("") : ""), is(TEXT));
                assertThat(ctx + " message_delta stop_reason",
                    dataOf(events, "message_delta").get(0).path("delta").path("stop_reason").asText(ABSENT), is("end_turn"));
                break;
            }
            case GEMINI: {
                List<JsonNode> chunks = jsonPayloads(events);
                for (JsonNode chunk : chunks) {
                    assertThat(ctx + " chunk has candidates", chunk.has("candidates"), is(true));
                }
                assertThat(ctx + " reassembled text",
                    concat(chunks, c -> c.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("")),
                    is(TEXT));
                JsonNode last = chunks.get(chunks.size() - 1);
                assertThat(ctx + " terminal finishReason",
                    last.path("candidates").path(0).path("finishReason").asText(ABSENT), is("STOP"));
                assertThat(ctx + " terminal usageMetadata present", last.has("usageMetadata"), is(true));
                break;
            }
            case OPENAI_RESPONSES: {
                List<String> names = eventNames(events);
                assertThat(ctx + " opens with response.created", names.get(0), is("response.created"));
                assertThat(ctx + " closes with response.completed", names.get(names.size() - 1), is("response.completed"));
                assertThat(ctx + " event sequence",
                    names, hasItems("response.output_item.added", "response.output_text.delta", "response.output_text.done"));
                assertThat(ctx + " reassembled delta text",
                    concat(dataOf(events, "response.output_text.delta"), d -> d.path("delta").asText("")), is(TEXT));
                assertThat(ctx + " output_text.done text",
                    dataOf(events, "response.output_text.done").get(0).path("text").asText(ABSENT), is(TEXT));
                assertResponsesStreamEnvelope(ctx, events);
                JsonNode completedOutput = dataOf(events, "response.completed").get(0).path("response").path("output");
                assertThat(ctx + " completed output[0].content[0].text",
                    completedOutput.path(0).path("content").path(0).path("text").asText(ABSENT), is(TEXT));
                break;
            }
            case OLLAMA: {
                List<JsonNode> chunks = jsonPayloads(events);
                assertThat(ctx + " reassembled text",
                    concat(chunks, c -> c.path("message").path("content").asText("")), is(TEXT));
                JsonNode last = chunks.get(chunks.size() - 1);
                assertThat(ctx + " terminal done=true", last.path("done").asBoolean(false), is(true));
                assertThat(ctx + " terminal done_reason", last.path("done_reason").asText(ABSENT), is("stop"));
                for (int i = 0; i < chunks.size() - 1; i++) {
                    assertThat(ctx + " non-terminal done=false", chunks.get(i).path("done").asBoolean(true), is(false));
                }
                break;
            }
            default:
                fail("No streaming-text structural contract defined for provider " + provider);
        }
    }

    private void assertToolStreamingStructure(Provider provider, List<StreamEvent> events) {
        String ctx = provider + " tool stream";
        assertThat(ctx + " emitted events", events, is(not(empty())));
        switch (provider) {
            case OPENAI:
            case AZURE_OPENAI: {
                assertThat(ctx + " terminal [DONE] sentinel", lastDataLiteral(events), is("[DONE]"));
                List<JsonNode> chunks = jsonPayloads(events);
                JsonNode fn = firstNonMissing(chunks,
                    c -> c.path("choices").path(0).path("delta").path("tool_calls").path(0).path("function"));
                assertThat(ctx + " tool_calls function.name", fn.path("name").asText(ABSENT), is("get_weather"));
                assertThat(ctx + " terminal finish_reason",
                    lastNonNull(chunks, c -> textOrNull(c.path("choices").path(0).path("finish_reason"))), is("tool_calls"));
                break;
            }
            case ANTHROPIC:
            case BEDROCK: {
                List<String> names = eventNames(events);
                assertThat(ctx + " first event", names.get(0), is("message_start"));
                assertThat(ctx + " last event", names.get(names.size() - 1), is("message_stop"));
                JsonNode start = dataOf(events, "content_block_start").get(0);
                assertThat(ctx + " content_block.type",
                    start.path("content_block").path("type").asText(ABSENT), is("tool_use"));
                assertThat(ctx + " content_block.name",
                    start.path("content_block").path("name").asText(ABSENT), is("get_weather"));
                JsonNode delta = dataOf(events, "content_block_delta").get(0).path("delta");
                assertThat(ctx + " delta.type", delta.path("type").asText(ABSENT), is("input_json_delta"));
                assertThat(ctx + " partial_json present", delta.has("partial_json"), is(true));
                assertThat(ctx + " message_delta stop_reason",
                    dataOf(events, "message_delta").get(0).path("delta").path("stop_reason").asText(ABSENT), is("tool_use"));
                break;
            }
            case GEMINI: {
                List<JsonNode> chunks = jsonPayloads(events);
                JsonNode fn = firstNonMissing(chunks,
                    c -> c.path("candidates").path(0).path("content").path("parts").path(0).path("functionCall"));
                assertThat(ctx + " functionCall.name", fn.path("name").asText(ABSENT), is("get_weather"));
                assertThat(ctx + " functionCall.args.city", fn.path("args").path("city").asText(ABSENT), is("London"));
                JsonNode last = chunks.get(chunks.size() - 1);
                assertThat(ctx + " terminal finishReason",
                    last.path("candidates").path(0).path("finishReason").asText(ABSENT), is("STOP"));
                break;
            }
            case OPENAI_RESPONSES: {
                List<String> names = eventNames(events);
                assertThat(ctx + " closes with response.completed", names.get(names.size() - 1), is("response.completed"));
                assertThat(ctx + " event sequence",
                    names, hasItems("response.output_item.added", "response.output_item.done"));
                JsonNode doneItem = dataOf(events, "response.output_item.done").get(0).path("item");
                assertThat(ctx + " item.type", doneItem.path("type").asText(ABSENT), is("function_call"));
                assertThat(ctx + " item.name", doneItem.path("name").asText(ABSENT), is("get_weather"));
                assertThat(ctx + " item.arguments", doneItem.path("arguments").asText(ABSENT), is(TOOL_ARGS_JSON));
                assertThat(ctx + " item.call_id", doneItem.path("call_id").asText(ABSENT), startsWith("call_"));
                assertThat(ctx + " reassembled argument deltas",
                    concat(dataOf(events, "response.function_call_arguments.delta"), d -> d.path("delta").asText("")),
                    is(TOOL_ARGS_JSON));
                assertThat(ctx + " function_call_arguments.done arguments",
                    dataOf(events, "response.function_call_arguments.done").get(0).path("arguments").asText(ABSENT),
                    is(TOOL_ARGS_JSON));
                assertResponsesStreamEnvelope(ctx, events);
                JsonNode completedItem = dataOf(events, "response.completed").get(0).path("response").path("output").path(0);
                assertThat(ctx + " completed output[0].call_id",
                    completedItem.path("call_id").asText(ABSENT), is(doneItem.path("call_id").asText()));
                break;
            }
            case OLLAMA: {
                List<JsonNode> chunks = jsonPayloads(events);
                JsonNode fn = firstNonMissing(chunks,
                    c -> c.path("message").path("tool_calls").path(0).path("function"));
                assertThat(ctx + " function.name", fn.path("name").asText(ABSENT), is("get_weather"));
                assertThat(ctx + " arguments.city", fn.path("arguments").path("city").asText(ABSENT), is("London"));
                JsonNode last = chunks.get(chunks.size() - 1);
                assertThat(ctx + " terminal done=true", last.path("done").asBoolean(false), is(true));
                assertThat(ctx + " terminal done_reason", last.path("done_reason").asText(ABSENT), is("stop"));
                break;
            }
            default:
                fail("No streaming-tool structural contract defined for provider " + provider);
        }
    }

    // -----------------------------------------------------------------------
    // Bedrock Converse / ConverseStream (selected by request path)
    // -----------------------------------------------------------------------

    /**
     * Bedrock's second wire API. Hand-authored from the AWS Bedrock Runtime API reference
     * (API_runtime_Converse / API_runtime_ConverseStream): the response is
     * {@code output.message.content[]} with union-keyed blocks ({@code {"text":..}},
     * {@code {"toolUse":{toolUseId,name,input}}}), a camelCase {@code stopReason}, and
     * {@code usage.{inputTokens,outputTokens,totalTokens}}; the stream is the
     * {@code messageStart … contentBlockDelta … contentBlockStop … messageStop … metadata}
     * event sequence whose payloads are the event bodies themselves.
     */
    @Test
    public void shouldEncodeBedrockConverseStructureOnConversePaths() {
        ProviderCodec codec = ProviderCodecRegistry.getInstance().lookup(Provider.BEDROCK)
            .orElseThrow(() -> new AssertionError("BEDROCK must be registered"));
        String model = CANONICAL_MODELS.get(Provider.BEDROCK);
        HttpRequest converse = HttpRequest.request().withPath("/model/" + model + "/converse");
        HttpRequest converseStream = HttpRequest.request().withPath("/model/" + model + "/converse-stream");

        // text body
        JsonNode text = parse(codec.encode(TEXT_COMPLETION, model, converse).getBodyAsString());
        JsonNode message = text.path("output").path("message");
        assertThat("converse text role", message.path("role").asText(ABSENT), is("assistant"));
        assertThat("converse text content[0].text", message.path("content").path(0).path("text").asText(ABSENT), is(TEXT));
        assertThat("converse text content[0] has no type discriminator", message.path("content").path(0).has("type"), is(false));
        assertThat("converse text stopReason", text.path("stopReason").asText(ABSENT), is("end_turn"));
        assertThat("converse usage.inputTokens", text.path("usage").path("inputTokens").asInt(-1), is(12));
        assertThat("converse usage.outputTokens", text.path("usage").path("outputTokens").asInt(-1), is(8));
        assertThat("converse usage.totalTokens", text.path("usage").path("totalTokens").asInt(-1), is(20));
        assertThat("converse metrics.latencyMs is a number", text.path("metrics").path("latencyMs").isNumber(), is(true));

        // tool body — input is a structured JSON document, not a string
        JsonNode tool = parse(codec.encode(TOOL_CALL_COMPLETION, model, converse).getBodyAsString());
        JsonNode toolUse = tool.path("output").path("message").path("content").path(0).path("toolUse");
        assertThat("converse toolUse.name", toolUse.path("name").asText(ABSENT), is("get_weather"));
        assertThat("converse toolUse.toolUseId is a string", toolUse.path("toolUseId").isTextual(), is(true));
        assertThat("converse toolUse.input is an object", toolUse.path("input").isObject(), is(true));
        assertThat("converse toolUse.input.city", toolUse.path("input").path("city").asText(ABSENT), is("London"));
        assertThat("converse tool stopReason", tool.path("stopReason").asText(ABSENT), is("tool_use"));

        // text stream
        List<StreamEvent> textEvents = streamEvents(codec, TEXT_COMPLETION, model, converseStream);
        List<String> names = eventNames(textEvents);
        assertThat("converse stream opens with messageStart", names.get(0), is("messageStart"));
        assertThat("converse stream event names", new LinkedHashSet<>(names),
            contains("messageStart", "contentBlockDelta", "contentBlockStop", "messageStop", "metadata"));
        assertThat("converse stream ends with metadata", names.get(names.size() - 1), is("metadata"));
        assertThat("converse messageStart.role", dataOf(textEvents, "messageStart").get(0).path("role").asText(ABSENT), is("assistant"));
        assertThat("converse reassembled text",
            concat(dataOf(textEvents, "contentBlockDelta"), n -> n.path("delta").path("text").asText("")), is(TEXT));
        assertThat("converse messageStop.stopReason",
            dataOf(textEvents, "messageStop").get(0).path("stopReason").asText(ABSENT), is("end_turn"));
        JsonNode metadataUsage = dataOf(textEvents, "metadata").get(0).path("usage");
        assertThat("converse metadata.usage.totalTokens", metadataUsage.path("totalTokens").asInt(-1), is(20));

        // tool stream — toolUse opens with contentBlockStart; its input delta is a JSON *string*
        List<StreamEvent> toolEvents = streamEvents(codec, TOOL_CALL_COMPLETION, model, converseStream);
        assertThat("converse tool stream event names", eventNames(toolEvents),
            contains("messageStart", "contentBlockStart", "contentBlockDelta", "contentBlockStop", "messageStop", "metadata"));
        JsonNode start = dataOf(toolEvents, "contentBlockStart").get(0).path("start").path("toolUse");
        assertThat("converse contentBlockStart.start.toolUse.name", start.path("name").asText(ABSENT), is("get_weather"));
        JsonNode inputDelta = dataOf(toolEvents, "contentBlockDelta").get(0).path("delta").path("toolUse").path("input");
        assertThat("converse toolUse delta input is a string", inputDelta.isTextual(), is(true));
        assertThat("converse toolUse delta input", inputDelta.asText(ABSENT), is(TOOL_ARGS_JSON));
        assertThat("converse tool messageStop.stopReason",
            dataOf(toolEvents, "messageStop").get(0).path("stopReason").asText(ABSENT), is("tool_use"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse encoded body for structural assertion", e);
        }
    }

    private JsonNode encodeTree(ProviderCodec codec, Completion completion, String model) {
        try {
            return MAPPER.readTree(codec.encode(completion, model).getBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse encoded body for structural assertion", e);
        }
    }

    private List<StreamEvent> streamEvents(ProviderCodec codec, Completion completion, String model) {
        return streamEvents(codec, completion, model, null);
    }

    private List<StreamEvent> streamEvents(ProviderCodec codec, Completion completion, String model, HttpRequest request) {
        List<SseEvent> raw = request == null
            ? codec.encodeStreaming(completion, model, null)
            : codec.encodeStreaming(completion, model, null, request);
        List<StreamEvent> out = new ArrayList<>();
        for (SseEvent event : raw) {
            String data = event.getData();
            if (data == null) {
                continue;
            }
            JsonNode json = null;
            try {
                JsonNode parsed = MAPPER.readTree(data);
                if (parsed != null && (parsed.isObject() || parsed.isArray())) {
                    json = parsed;
                }
            } catch (Exception ignored) {
                // non-JSON sentinel (e.g. [DONE]) — json stays null
            }
            out.add(new StreamEvent(event.getEvent(), data, json));
        }
        return out;
    }

    private List<String> eventNames(List<StreamEvent> events) {
        List<String> names = new ArrayList<>();
        for (StreamEvent e : events) {
            if (e.event != null && !e.event.isEmpty()) {
                names.add(e.event);
            }
        }
        return names;
    }

    /** JSON payloads for providers that do not use SSE event names. */
    private List<JsonNode> jsonPayloads(List<StreamEvent> events) {
        List<JsonNode> out = new ArrayList<>();
        for (StreamEvent e : events) {
            if (e.json != null) {
                out.add(e.json);
            }
        }
        return out;
    }

    /** Payload {@code data} of every event carrying the given SSE event name. */
    private List<JsonNode> dataOf(List<StreamEvent> events, String eventName) {
        List<JsonNode> out = new ArrayList<>();
        for (StreamEvent e : events) {
            if (eventName.equals(e.event) && e.json != null) {
                out.add(e.json);
            }
        }
        if (out.isEmpty()) {
            fail("Expected at least one SSE event named '" + eventName + "' but found none. "
                + "Present event names: " + eventNames(events));
        }
        return out;
    }

    /**
     * Every Responses stream event carries its type and a sequence_number counting from 0, and
     * response.created / in_progress / completed each carry a full Response object
     * (openai-python ResponseCreatedEvent / ResponseInProgressEvent / ResponseCompletedEvent).
     */
    private void assertResponsesStreamEnvelope(String ctx, List<StreamEvent> events) {
        for (int i = 0; i < events.size(); i++) {
            JsonNode json = events.get(i).json;
            assertThat(ctx + " event " + i + " type", json.path("type").asText(ABSENT), is(events.get(i).event));
            assertThat(ctx + " event " + i + " sequence_number", json.path("sequence_number").asInt(-1), is(i));
        }
        for (String name : new String[]{"response.created", "response.in_progress", "response.completed"}) {
            JsonNode response = dataOf(events, name).get(0).path("response");
            assertThat(ctx + " " + name + " object", response.path("object").asText(ABSENT), is("response"));
            assertThat(ctx + " " + name + " output is array", response.path("output").isArray(), is(true));
            assertThat(ctx + " " + name + " model", response.has("model"), is(true));
        }
        JsonNode completed = dataOf(events, "response.completed").get(0).path("response");
        assertThat(ctx + " completed status", completed.path("status").asText(ABSENT), is("completed"));
        assertThat(ctx + " completed output not empty", completed.path("output").size(), greaterThan(0));
        assertThat(ctx + " completed usage", completed.path("usage").has("total_tokens"), is(true));
    }

    private String lastDataLiteral(List<StreamEvent> events) {
        return events.get(events.size() - 1).data;
    }

    private String concat(List<JsonNode> nodes, java.util.function.Function<JsonNode, String> extractor) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode n : nodes) {
            sb.append(extractor.apply(n));
        }
        return sb.toString();
    }

    private String lastNonNull(List<JsonNode> nodes, java.util.function.Function<JsonNode, String> extractor) {
        String result = null;
        for (JsonNode n : nodes) {
            String v = extractor.apply(n);
            if (v != null) {
                result = v;
            }
        }
        return result;
    }

    private JsonNode firstNonMissing(List<JsonNode> nodes, java.util.function.Function<JsonNode, JsonNode> extractor) {
        for (JsonNode n : nodes) {
            JsonNode v = extractor.apply(n);
            if (v != null && !v.isMissingNode()) {
                return v;
            }
        }
        fail("Expected at least one streaming chunk to carry the projected field, but none did");
        return MAPPER.missingNode();
    }

    private static String textOrNull(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    private static final class StreamEvent {
        final String event;
        final String data;
        final JsonNode json;

        StreamEvent(String event, String data, JsonNode json) {
            this.event = event;
            this.data = data;
            this.json = json;
        }
    }
}
