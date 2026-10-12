package org.mockserver.llm.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import org.mockserver.model.Provider;
import org.mockserver.model.Usage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.model.HttpResponse.response;

public class LlmStreamUsageScannerTest {

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static LlmStreamUsageScanner scan(byte[]... chunks) {
        LlmStreamUsageScanner scanner = new LlmStreamUsageScanner();
        for (byte[] chunk : chunks) {
            scanner.accept(chunk);
        }
        return scanner;
    }

    private static Usage usage(Provider provider, LlmStreamUsageScanner scanner) {
        LlmClient client = LlmClientRegistry.getInstance().lookup(provider).orElseThrow();
        return scanner.usage() == null ? null : client.parseUsage(scanner.usage());
    }

    private static Usage expected(Integer input, Integer output, Integer cachedInput, Integer cacheCreation, Integer reasoning) {
        return Usage.usage()
            .withInputTokens(input)
            .withOutputTokens(output)
            .withCachedInputTokens(cachedInput)
            .withCacheCreationTokens(cacheCreation)
            .withReasoningTokens(reasoning);
    }

    /**
     * The stream gives the same usage however the network splits it: whole, one byte at a time, and
     * cut in two at every offset (so every token of the usage event straddles a chunk boundary once).
     */
    private static void assertUsageHoweverChunked(Provider provider, byte[] stream, Usage expected, String model, String stopReason) {
        LlmStreamUsageScanner whole = scan(stream);
        assertThat("whole", usage(provider, whole), is(expected));
        assertThat(whole.model(), is(model));
        assertThat(whole.stopReason(), is(stopReason));
        assertThat(whole.finalUsageSeen(), is(true));

        LlmStreamUsageScanner byteAtATime = new LlmStreamUsageScanner();
        for (byte b : stream) {
            byteAtATime.accept(new byte[]{b});
        }
        assertThat("one byte at a time", usage(provider, byteAtATime), is(expected));
        assertThat(byteAtATime.model(), is(model));
        assertThat(byteAtATime.stopReason(), is(stopReason));

        for (int split = 1; split < stream.length; split++) {
            LlmStreamUsageScanner twoChunks = scan(Arrays.copyOfRange(stream, 0, split), Arrays.copyOfRange(stream, split, stream.length));
            assertThat("split at " + split, usage(provider, twoChunks), is(expected));
        }
    }

    @Test
    public void shouldReadOpenAiChatUsageChunkSentWithIncludeUsage() {
        assertUsageHoweverChunked(Provider.OPENAI, utf8(ProviderStreamFixtures.OPENAI_CHAT_WITH_USAGE),
            expected(19, 10, 4, null, 3), "gpt-4o-2024-08-06", "stop");
    }

    @Test
    public void shouldFindNoUsageInOpenAiChatStreamSentWithoutIncludeUsage() {
        LlmStreamUsageScanner scanner = scan(utf8(ProviderStreamFixtures.OPENAI_CHAT_WITHOUT_USAGE));

        assertThat(scanner.usage(), is(nullValue()));
        assertThat(scanner.finalUsageSeen(), is(false));
        assertThat(scanner.model(), is("gpt-4o-2024-08-06"));
        assertThat(scanner.streamed(), is(true));
    }

    @Test
    public void shouldReadUsageThatOpenAiCompatibleProvidersSendWithoutOptIn() {
        assertUsageHoweverChunked(Provider.MISTRAL, utf8(ProviderStreamFixtures.MISTRAL_CHAT),
            expected(16, 18, null, null, null), "mistral-small-latest", "stop");
        assertUsageHoweverChunked(Provider.OPENROUTER, utf8(ProviderStreamFixtures.OPENROUTER_CHAT),
            expected(14, 9, 0, null, 0), "openai/gpt-4o", "stop");
    }

    @Test
    public void shouldReadGroqUsageFromXGroq() {
        assertUsageHoweverChunked(Provider.GROQ, utf8(ProviderStreamFixtures.GROQ_CHAT),
            expected(18, 25, null, null, null), "llama-3.3-70b-versatile", "stop");
    }

    @Test
    public void shouldReadOpenAiResponsesUsageFromResponseCompleted() {
        assertUsageHoweverChunked(Provider.OPENAI_RESPONSES, utf8(ProviderStreamFixtures.OPENAI_RESPONSES),
            expected(37, 11, 5, null, 2), "gpt-4o-2024-08-06", "completed");
    }

    @Test
    public void shouldMergeAnthropicMessageStartAndMessageDeltaUsage() {
        assertUsageHoweverChunked(Provider.ANTHROPIC, utf8(ProviderStreamFixtures.ANTHROPIC_MESSAGES),
            expected(25, 15, 7, 3, null), "claude-sonnet-4-5-20250929", "end_turn");
    }

    @Test
    public void shouldTakeCumulativeCountsFromAnAnthropicMessageDeltaThatRepeatsThem() {
        String stream = ProviderStreamFixtures.ANTHROPIC_MESSAGES.replace(
            "\"usage\":{\"output_tokens\":15}",
            "\"usage\":{\"input_tokens\":25,\"cache_creation_input_tokens\":3,\"cache_read_input_tokens\":9,\"output_tokens\":15,\"server_tool_use\":null}");

        assertThat(usage(Provider.ANTHROPIC, scan(utf8(stream))), is(expected(25, 15, 9, 3, null)));
    }

    @Test
    public void shouldReadGeminiUsageMetadataFromTheLastSseChunk() {
        assertUsageHoweverChunked(Provider.GEMINI, utf8(ProviderStreamFixtures.GEMINI_SSE),
            expected(8, 39, null, null, 27), "gemini-2.5-flash", "STOP");
    }

    @Test
    public void shouldReadGeminiUsageMetadataFromAStreamedJsonArray() {
        assertUsageHoweverChunked(Provider.GEMINI, utf8(ProviderStreamFixtures.GEMINI_JSON_ARRAY),
            expected(8, 39, null, null, 27), "gemini-2.5-flash", "STOP");
        assertThat(scan(utf8(ProviderStreamFixtures.GEMINI_JSON_ARRAY)).streamed(), is(true));
    }

    @Test
    public void shouldCountAGeminiStreamExactlyAsTheSameUsageInAWholeResponse() {
        String lastChunk = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello there\"}],\"role\":\"model\"},\"finishReason\":\"STOP\",\"index\":0}],"
            + "\"usageMetadata\":{\"promptTokenCount\":8,\"candidatesTokenCount\":12,\"totalTokenCount\":47,\"thoughtsTokenCount\":27},\"modelVersion\":\"gemini-2.5-flash\"}";
        Usage wholeResponse = new GeminiLlmClient().parseCompletionResponse(response().withBody(lastChunk)).getUsage();

        assertThat(usage(Provider.GEMINI, scan(utf8(ProviderStreamFixtures.GEMINI_SSE))), is(wholeResponse));
    }

    @Test
    public void shouldReadOllamaCountsFromTheDoneLine() {
        assertUsageHoweverChunked(Provider.OLLAMA, utf8(ProviderStreamFixtures.OLLAMA_CHAT),
            expected(26, 282, null, null, null), "llama3.2", "stop");
        assertThat(scan(utf8(ProviderStreamFixtures.OLLAMA_CHAT)).streamed(), is(true));
    }

    @Test
    public void shouldReadBedrockConverseStreamUsageFromTheMetadataEvent() {
        assertUsageHoweverChunked(Provider.BEDROCK, ProviderStreamFixtures.bedrockConverseStream(),
            expected(25, 15, 6, 0, null), null, "end_turn");
    }

    @Test
    public void shouldNotReadBedrockGuardrailUsageAsTokenUsage() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ProviderStreamFixtures.writeEventStreamMessage(stream, "metadata",
            "{\"metrics\":{\"latencyMs\":623},\"usage\":{\"inputTokens\":25,\"outputTokens\":15,\"totalTokens\":40},"
                + "\"trace\":{\"guardrail\":{\"inputAssessment\":{\"gr-1\":{\"invocationMetrics\":{\"guardrailProcessingLatency\":21,"
                + "\"usage\":{\"inputTokens\":777,\"outputTokens\":777,\"contentPolicyUnits\":1,\"topicPolicyUnits\":1}}}}}}}");

        assertThat(usage(Provider.BEDROCK, scan(stream.toByteArray())), is(expected(25, 15, null, null, null)));
    }

    @Test
    public void shouldReadAnthropicEventsWrappedByBedrockInvokeWithResponseStream() {
        assertUsageHoweverChunked(Provider.BEDROCK, ProviderStreamFixtures.bedrockInvokeWithResponseStream(),
            expected(25, 15, 7, 3, null), "claude-sonnet-4-5-20250929", "end_turn");
    }

    @Test
    public void shouldReportOnlyOpeningCountsWhenAnAnthropicStreamIsCutOffBeforeMessageDelta() {
        LlmStreamUsageScanner scanner = scan(utf8(ProviderStreamFixtures.ANTHROPIC_MESSAGES_CUT_OFF));

        assertThat(usage(Provider.ANTHROPIC, scanner), is(expected(25, 1, 7, 3, null)));
        assertThat(scanner.finalUsageSeen(), is(false));
    }

    @Test
    public void shouldFindNoUsageInAStreamCutOffBeforeItsUsageEvent() {
        String openAi = ProviderStreamFixtures.OPENAI_CHAT_WITH_USAGE;
        String ollama = ProviderStreamFixtures.OLLAMA_CHAT;
        byte[] bedrock = ProviderStreamFixtures.bedrockConverseStream();

        // cut inside the usage object itself, and before the event that carries it
        assertThat(scan(utf8(openAi.substring(0, openAi.indexOf("\"completion_tokens\":10")))).usage(), is(nullValue()));
        assertThat(scan(utf8(ollama.substring(0, ollama.indexOf("\"done\":true")))).usage(), is(nullValue()));
        assertThat(scan(Arrays.copyOf(bedrock, bedrock.length - 60)).usage(), is(nullValue()));
        // the tool schema echoed by response.created has a field named usage: it is not the response's
        assertThat(scan(utf8(ProviderStreamFixtures.OPENAI_RESPONSES_CUT_OFF)).usage(), is(nullValue()));
    }

    @Test
    public void shouldNotReadUsageFromAServerSentEventStreamThatIsNotAnLlmResponse() {
        String metrics = ""
            + "retry: 3000\n\n"
            + "event: metric\nid: 1\ndata: {\"host\":\"web-1\",\"usage\":\"87%\",\"cpu\":{\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":6}}}\n\n"
            + "event: metric\nid: 2\ndata: {\"hosts\":[{\"name\":\"web-2\",\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":8}}],\"usage\":[1,2,3]}\n\n"
            + "event: tick\ndata: usage: {\"prompt_tokens\":9}\n\n"
            + "data: plain text, not JSON\n\n";
        String jsonRpc = ""
            + "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"usage\\\":{\\\"input_tokens\\\":5}}\"}],\"isError\":false}}\n\n";

        assertThat(scan(utf8(metrics)).usage(), is(nullValue()));
        assertThat(scan(utf8(jsonRpc)).usage(), is(nullValue()));
    }

    @Test
    public void shouldCountNothingWhenAUsageObjectHasNoneOfTheProvidersTokenFields() {
        LlmStreamUsageScanner scanner = scan(utf8("data: {\"usage\":{\"cpu\":87,\"memory\":40}}\n\n"));

        assertThat(usage(Provider.OPENAI, scanner), is(expected(null, null, null, null, null)));
        // and a usage object with no whole-number count at all is not kept
        assertThat(scan(utf8("data: {\"usage\":{\"cpu\":0.87,\"tier\":\"gold\",\"hosts\":[1,2],\"none\":null,\"empty\":{}}}\n\n")).usage(), is(nullValue()));
    }

    @Test
    public void shouldKeepAnEarlierCountWhenALaterEventSendsNullForIt() {
        // Anthropic types a message_delta's input and cache counts as nullable
        String stream = ProviderStreamFixtures.ANTHROPIC_MESSAGES.replace(
            "\"usage\":{\"output_tokens\":15}",
            "\"usage\":{\"input_tokens\":null,\"cache_creation_input_tokens\":null,\"cache_read_input_tokens\":null,\"output_tokens\":15}");

        assertThat(usage(Provider.ANTHROPIC, scan(utf8(stream))), is(expected(25, 15, 7, 3, null)));
    }

    @Test
    public void shouldKeepABoundedUsageHoweverManyDistinctFieldsTheStreamSends() {
        LlmStreamUsageScanner scanner = new LlmStreamUsageScanner();
        scanner.accept(utf8(ProviderStreamFixtures.MISTRAL_CHAT));
        int key = 0;
        for (int event = 0; event < 2_000; event++) {
            StringBuilder usage = new StringBuilder("data: {\"usage\":{");
            // growth one object down, and under names and numbers too long to keep
            usage.append("\"details").append(event).append("\":{");
            for (int field = 0; field < 200; field++) {
                usage.append("\"d").append(key++).append("\":1,");
            }
            usage.append("\"").append("n".repeat(200)).append("\":1},");
            usage.append("\"").append("n".repeat(200)).append(event).append("\":1,");
            usage.append("\"huge").append(event).append("\":").append("9".repeat(400)).append(",");
            for (int field = 0; field < 200; field++) {
                usage.append("\"k").append(key++).append("\":1,");
            }
            usage.append("\"completion_tokens\":").append(event).append("}}\n\n");
            scanner.accept(utf8(usage.toString()));
        }

        assertThat(scanner.usage().size(), is(LlmStreamUsageScanner.MAX_USAGE_FIELDS));
        scanner.usage().forEach(value -> assertThat(value.size(), lessThanOrEqualTo(LlmStreamUsageScanner.MAX_USAGE_DETAIL_FIELDS)));
        assertThat(scanner.usage().toString().length(), lessThan(64 * 1024));
        // a field already kept is still replaced once the usage is full
        assertThat(usage(Provider.MISTRAL, scanner), is(expected(16, 1_999, null, null, null)));
    }

    @Test
    public void shouldIgnoreAModelNameOrStopReasonLongerThanTheBound() {
        String longName = "m".repeat(LlmStreamUsageScanner.MAX_TEXT_CHARS + 1);
        LlmStreamUsageScanner scanner = scan(utf8(
            "data: {\"model\":\"" + longName + "\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"" + longName + "\"}]}\n\n"));

        assertThat(scanner.model(), is(nullValue()));
        assertThat(scanner.stopReason(), is(nullValue()));
    }

    @Test
    public void shouldNotTreatGeminiCountsAsFinalBeforeTheChunkThatCarriesTheFinishReason() {
        String stream = ProviderStreamFixtures.GEMINI_SSE;
        LlmStreamUsageScanner cutOff = scan(utf8(stream.substring(0, stream.indexOf("\r\n\r\n") + 4)));

        assertThat(usage(Provider.GEMINI, cutOff), is(expected(8, null, null, null, null)));
        assertThat(cutOff.usageOnEveryChunk(), is(true));
        assertThat(cutOff.finalUsageSeen(), is(false));
        assertThat(scan(utf8(stream)).finalUsageSeen(), is(true));
    }

    @Test
    public void shouldDropAUsageValueLargerThanTheBound() {
        StringBuilder padding = new StringBuilder();
        while (padding.length() < LlmStreamUsageScanner.MAX_VALUE_BYTES) {
            padding.append("0123456789abcdef");
        }
        String oversized = "data: {\"usage\":{\"prompt_tokens\":19,\"completion_tokens\":10,\"note\":\"" + padding + "\"}}\n\n";

        assertThat(scan(utf8(oversized)).usage(), is(nullValue()));
        // and a later usage event of ordinary size is still read
        assertThat(usage(Provider.OPENAI, scan(utf8(oversized), utf8(ProviderStreamFixtures.MISTRAL_CHAT))),
            is(expected(16, 18, null, null, null)));
    }

    @Test
    public void shouldReadUsageAfterAnEventFarLargerThanAnyBufferItKeeps() {
        byte[] text = new byte[4 * 1024 * 1024];
        Arrays.fill(text, (byte) 'x');
        LlmStreamUsageScanner scanner = scan(
            utf8("data: {\"id\":\"chatcmpl-1\",\"model\":\"gpt-4o-2024-08-06\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\""),
            text,
            utf8("\"},\"finish_reason\":null}],\"usage\":null}\n\n"),
            utf8(ProviderStreamFixtures.OPENAI_CHAT_WITH_USAGE));

        assertThat(usage(Provider.OPENAI, scanner), is(expected(19, 10, 4, null, 3)));
    }

    @Test
    public void shouldRecoverAtTheNextEventFromJsonLeftUnfinished() {
        String stream = "data: {\"choices\":[{\"delta\":{\"content\":\"never closed\n\n" + ProviderStreamFixtures.MISTRAL_CHAT;

        assertThat(usage(Provider.MISTRAL, scan(utf8(stream))), is(expected(16, 18, null, null, null)));
    }

    @Test
    public void shouldNeverThrowOnMalformedOrHostileBytes() {
        Random random = new Random(42);
        byte[] noise = new byte[64 * 1024];
        random.nextBytes(noise);
        byte[] nesting = new byte[256 * 1024];
        Arrays.fill(nesting, (byte) '[');
        byte[] nestedUsage = utf8("{\"usage\":" + "[".repeat(3000) + "]".repeat(3000) + "}");
        byte[] eventStreamWithAbsurdLength = {0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0, 0, 0, 0, 1, 2, 3, 4, '{', '"', 'u', 's'};
        byte[] eventStreamWithHeadersLongerThanMessage = {0, 0, 0, 32, 0, 0, 1, 0, 1, 2, 3, 4, '{', '}'};

        assertThat(scan(noise).usage(), is(nullValue()));
        assertThat(scan(nesting).usage(), is(nullValue()));
        assertThat(scan(nestedUsage).usage(), is(nullValue()));
        assertThat(scan(eventStreamWithAbsurdLength, noise).usage(), is(nullValue()));
        assertThat(scan(eventStreamWithHeadersLongerThanMessage, noise).usage(), is(nullValue()));
        assertThat(scan(utf8("data: {\"usage\":}\n\ndata: {\"usage\":{\"prompt_tokens\":\n\ndata: {\"usage\":{{{{\n\n")).usage(), is(nullValue()));
        assertThat(scan(utf8("{\"prompt_eval_count\":\"26\",\"eval_count\":{\"n\":282}}\n")).usage(), is(nullValue()));
        assertThat(scan(utf8("")).usage(), is(nullValue()));
    }

    @Test
    public void shouldIgnoreBase64ThatIsNotAnEventInABedrockChunk() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ProviderStreamFixtures.writeEventStreamMessage(stream, "chunk", "{\"bytes\":\"not base64 !!\"}");
        ProviderStreamFixtures.writeEventStreamMessage(stream, "chunk", "{\"bytes\":\"AAEC/w==\"}");

        assertThat(scan(stream.toByteArray()).usage(), is(nullValue()));
    }

    @Test
    public void shouldNotMoveTheReaderIndexOrFailOnAReleasedBuffer() {
        ByteBuf chunk = Unpooled.copiedBuffer(utf8(ProviderStreamFixtures.MISTRAL_CHAT));
        LlmStreamUsageScanner scanner = new LlmStreamUsageScanner();

        scanner.accept(chunk);

        assertThat(chunk.readerIndex(), is(0));
        assertThat(usage(Provider.MISTRAL, scanner), is(expected(16, 18, null, null, null)));

        chunk.release();
        scanner.accept(chunk);
        assertThat(usage(Provider.MISTRAL, scanner), is(expected(16, 18, null, null, null)));
    }

    @Test
    public void shouldNotTreatASingleJsonDocumentAsAStream() {
        LlmStreamUsageScanner scanner = scan(utf8("{\"model\":\"gpt-4o\",\"choices\":[],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2}}"));

        assertThat(scanner.streamed(), is(false));
        assertThat(usage(Provider.OPENAI, scanner), is(expected(1, 2, null, null, null)));
    }
}
