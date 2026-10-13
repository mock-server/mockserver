package org.mockserver.llm.client;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.CRC32;

/**
 * Streamed response bodies in the shape each provider documents, written from the provider's
 * API reference and SDK types rather than from what MockServer's own codecs emit.
 */
public final class ProviderStreamFixtures {

    private ProviderStreamFixtures() {
    }

    /** OpenAI Chat Completions with {@code stream_options.include_usage}: 19 in, 10 out, 4 cached, 3 reasoning. */
    public static final String OPENAI_CHAT_WITH_USAGE = ""
        + "data: {\"id\":\"chatcmpl-AJ2tUOPcPOlvJmFjy3nVbuLxKs1Yu\",\"object\":\"chat.completion.chunk\",\"created\":1728933352,\"model\":\"gpt-4o-2024-08-06\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_6b68a8204b\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\",\"refusal\":null},\"logprobs\":null,\"finish_reason\":null}],\"usage\":null}\n\n"
        + "data: {\"id\":\"chatcmpl-AJ2tUOPcPOlvJmFjy3nVbuLxKs1Yu\",\"object\":\"chat.completion.chunk\",\"created\":1728933352,\"model\":\"gpt-4o-2024-08-06\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_6b68a8204b\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"logprobs\":null,\"finish_reason\":null}],\"usage\":null}\n\n"
        + "data: {\"id\":\"chatcmpl-AJ2tUOPcPOlvJmFjy3nVbuLxKs1Yu\",\"object\":\"chat.completion.chunk\",\"created\":1728933352,\"model\":\"gpt-4o-2024-08-06\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_6b68a8204b\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" there, the \\\"usage\\\":{\\\"prompt_tokens\\\":999} you asked about\"},\"logprobs\":null,\"finish_reason\":null}],\"usage\":null}\n\n"
        + "data: {\"id\":\"chatcmpl-AJ2tUOPcPOlvJmFjy3nVbuLxKs1Yu\",\"object\":\"chat.completion.chunk\",\"created\":1728933352,\"model\":\"gpt-4o-2024-08-06\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_6b68a8204b\",\"choices\":[{\"index\":0,\"delta\":{},\"logprobs\":null,\"finish_reason\":\"stop\"}],\"usage\":null}\n\n"
        + "data: {\"id\":\"chatcmpl-AJ2tUOPcPOlvJmFjy3nVbuLxKs1Yu\",\"object\":\"chat.completion.chunk\",\"created\":1728933352,\"model\":\"gpt-4o-2024-08-06\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_6b68a8204b\",\"choices\":[],\"usage\":{\"prompt_tokens\":19,\"completion_tokens\":10,\"total_tokens\":29,\"prompt_tokens_details\":{\"cached_tokens\":4,\"audio_tokens\":0},\"completion_tokens_details\":{\"reasoning_tokens\":3,\"audio_tokens\":0,\"accepted_prediction_tokens\":0,\"rejected_prediction_tokens\":0}}}\n\n"
        + "data: [DONE]\n\n";

    /** OpenAI Chat Completions without {@code stream_options.include_usage}: no chunk has a usage field. */
    public static final String OPENAI_CHAT_WITHOUT_USAGE = ""
        + "data: {\"id\":\"chatcmpl-AJ2tUOPcPOlvJmFjy3nVbuLxKs1Yu\",\"object\":\"chat.completion.chunk\",\"created\":1728933352,\"model\":\"gpt-4o-2024-08-06\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_6b68a8204b\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\",\"refusal\":null},\"logprobs\":null,\"finish_reason\":null}]}\n\n"
        + "data: {\"id\":\"chatcmpl-AJ2tUOPcPOlvJmFjy3nVbuLxKs1Yu\",\"object\":\"chat.completion.chunk\",\"created\":1728933352,\"model\":\"gpt-4o-2024-08-06\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_6b68a8204b\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"logprobs\":null,\"finish_reason\":null}]}\n\n"
        + "data: {\"id\":\"chatcmpl-AJ2tUOPcPOlvJmFjy3nVbuLxKs1Yu\",\"object\":\"chat.completion.chunk\",\"created\":1728933352,\"model\":\"gpt-4o-2024-08-06\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_6b68a8204b\",\"choices\":[{\"index\":0,\"delta\":{},\"logprobs\":null,\"finish_reason\":\"stop\"}]}\n\n"
        + "data: [DONE]\n\n";

    /** Mistral sends usage on the chunk that carries the finish reason, with no opt-in: 16 in, 18 out. */
    public static final String MISTRAL_CHAT = ""
        + "data: {\"id\":\"cmpl-e5cc70bb28c444948073e77776eb30ef\",\"object\":\"chat.completion.chunk\",\"created\":1702256327,\"model\":\"mistral-small-latest\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\"},\"finish_reason\":null}]}\n\n"
        + "data: {\"id\":\"cmpl-e5cc70bb28c444948073e77776eb30ef\",\"object\":\"chat.completion.chunk\",\"created\":1702256327,\"model\":\"mistral-small-latest\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n\n"
        + "data: {\"id\":\"cmpl-e5cc70bb28c444948073e77776eb30ef\",\"object\":\"chat.completion.chunk\",\"created\":1702256327,\"model\":\"mistral-small-latest\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":16,\"total_tokens\":34,\"completion_tokens\":18}}\n\n"
        + "data: [DONE]\n\n";

    /** Groq reports usage under {@code x_groq} on the final chunk: 18 in, 25 out. */
    public static final String GROQ_CHAT = ""
        + "data: {\"id\":\"chatcmpl-0b4d6f2e-6a0f-4c0e-9a66-3c0f2a2e9d0c\",\"object\":\"chat.completion.chunk\",\"created\":1730241104,\"model\":\"llama-3.3-70b-versatile\",\"system_fingerprint\":\"fp_179b0f92c9\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\"},\"logprobs\":null,\"finish_reason\":null}],\"x_groq\":{\"id\":\"req_01jbd6g2qdfw2adyrt2az8hz4w\"}}\n\n"
        + "data: {\"id\":\"chatcmpl-0b4d6f2e-6a0f-4c0e-9a66-3c0f2a2e9d0c\",\"object\":\"chat.completion.chunk\",\"created\":1730241104,\"model\":\"llama-3.3-70b-versatile\",\"system_fingerprint\":\"fp_179b0f92c9\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"logprobs\":null,\"finish_reason\":null}]}\n\n"
        + "data: {\"id\":\"chatcmpl-0b4d6f2e-6a0f-4c0e-9a66-3c0f2a2e9d0c\",\"object\":\"chat.completion.chunk\",\"created\":1730241104,\"model\":\"llama-3.3-70b-versatile\",\"system_fingerprint\":\"fp_179b0f92c9\",\"choices\":[{\"index\":0,\"delta\":{},\"logprobs\":null,\"finish_reason\":\"stop\"}],\"x_groq\":{\"id\":\"req_01jbd6g2qdfw2adyrt2az8hz4w\",\"usage\":{\"queue_time\":0.037493756,\"prompt_tokens\":18,\"prompt_time\":0.000680594,\"completion_tokens\":25,\"completion_time\":0.463333333,\"total_tokens\":43,\"total_time\":0.464013927}}}\n\n"
        + "data: [DONE]\n\n";

    /** OpenRouter opens with comment lines and ends with a usage chunk, with no opt-in: 14 in, 9 out. */
    public static final String OPENROUTER_CHAT = ""
        + ": OPENROUTER PROCESSING\n\n"
        + "data: {\"id\":\"gen-1730000000-AbCdEfGhIjKlMnOpQrSt\",\"provider\":\"OpenAI\",\"model\":\"openai/gpt-4o\",\"object\":\"chat.completion.chunk\",\"created\":1730000000,\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"finish_reason\":null,\"native_finish_reason\":null,\"logprobs\":null}]}\n\n"
        + ": OPENROUTER PROCESSING\n\n"
        + "data: {\"id\":\"gen-1730000000-AbCdEfGhIjKlMnOpQrSt\",\"provider\":\"OpenAI\",\"model\":\"openai/gpt-4o\",\"object\":\"chat.completion.chunk\",\"created\":1730000000,\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\"},\"finish_reason\":\"stop\",\"native_finish_reason\":\"stop\",\"logprobs\":null}]}\n\n"
        + "data: {\"id\":\"gen-1730000000-AbCdEfGhIjKlMnOpQrSt\",\"provider\":\"OpenAI\",\"model\":\"openai/gpt-4o\",\"object\":\"chat.completion.chunk\",\"created\":1730000000,\"choices\":[],\"usage\":{\"prompt_tokens\":14,\"completion_tokens\":9,\"total_tokens\":23,\"cost\":0.000125,\"prompt_tokens_details\":{\"cached_tokens\":0},\"completion_tokens_details\":{\"reasoning_tokens\":0}}}\n\n"
        + "data: [DONE]\n\n";

    // a tool whose schema carries an example argument named usage, which is not the response's usage
    private static final String RESPONSES_TOOLS = "[{\"type\":\"function\",\"name\":\"report_usage\",\"description\":\"Report usage\",\"strict\":false,\"parameters\":{\"type\":\"object\",\"properties\":{\"usage\":{\"type\":\"object\"}},\"examples\":[{\"usage\":{\"input_tokens\":900000,\"output_tokens\":900000}}]}}]";

    /**
     * OpenAI Responses: usage is only on {@code response.completed}, inside a {@code response}
     * object that also echoes the request's tools. One tool's schema has a {@code usage} property,
     * which is not the response's usage. 37 in, 11 out, 5 cached, 2 reasoning.
     */
    public static final String OPENAI_RESPONSES = ""
        + "event: response.created\n"
        + "data: {\"type\":\"response.created\",\"sequence_number\":0,\"response\":{\"id\":\"resp_67c9fdcecf488190bdd9a0409de3a1ec07b8b0ad4e5eb654\",\"object\":\"response\",\"created_at\":1741290958,\"status\":\"in_progress\",\"error\":null,\"incomplete_details\":null,\"instructions\":\"You are a helpful assistant.\",\"max_output_tokens\":null,\"model\":\"gpt-4o-2024-08-06\",\"output\":[],\"parallel_tool_calls\":true,\"previous_response_id\":null,\"reasoning\":{\"effort\":null,\"summary\":null},\"store\":true,\"temperature\":1.0,\"text\":{\"format\":{\"type\":\"text\"}},\"tool_choice\":\"auto\",\"tools\":" + RESPONSES_TOOLS + ",\"top_p\":1.0,\"truncation\":\"disabled\",\"usage\":null,\"user\":null,\"metadata\":{}}}\n\n"
        + "event: response.output_item.added\n"
        + "data: {\"type\":\"response.output_item.added\",\"sequence_number\":2,\"output_index\":0,\"item\":{\"id\":\"msg_67c9fdcf37fc8190ba82116e33fb28c507b8b0ad4e5eb654\",\"type\":\"message\",\"status\":\"in_progress\",\"role\":\"assistant\",\"content\":[]}}\n\n"
        + "event: response.output_text.delta\n"
        + "data: {\"type\":\"response.output_text.delta\",\"sequence_number\":4,\"item_id\":\"msg_67c9fdcf37fc8190ba82116e33fb28c507b8b0ad4e5eb654\",\"output_index\":0,\"content_index\":0,\"delta\":\"Hello there\",\"logprobs\":[]}\n\n"
        + "event: response.completed\n"
        + "data: {\"type\":\"response.completed\",\"sequence_number\":9,\"response\":{\"id\":\"resp_67c9fdcecf488190bdd9a0409de3a1ec07b8b0ad4e5eb654\",\"object\":\"response\",\"created_at\":1741290958,\"status\":\"completed\",\"error\":null,\"incomplete_details\":null,\"instructions\":\"You are a helpful assistant.\",\"max_output_tokens\":null,\"model\":\"gpt-4o-2024-08-06\",\"output\":[{\"id\":\"msg_67c9fdcf37fc8190ba82116e33fb28c507b8b0ad4e5eb654\",\"type\":\"message\",\"status\":\"completed\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"Hello there\",\"annotations\":[]}]}],\"parallel_tool_calls\":true,\"previous_response_id\":null,\"reasoning\":{\"effort\":null,\"summary\":null},\"store\":true,\"temperature\":1.0,\"text\":{\"format\":{\"type\":\"text\"}},\"tool_choice\":\"auto\",\"tools\":" + RESPONSES_TOOLS + ",\"top_p\":1.0,\"truncation\":\"disabled\",\"usage\":{\"input_tokens\":37,\"input_tokens_details\":{\"cached_tokens\":5},\"output_tokens\":11,\"output_tokens_details\":{\"reasoning_tokens\":2},\"total_tokens\":48},\"user\":null,\"metadata\":{}}}\n\n";

    /** {@link #OPENAI_RESPONSES} cut off before {@code response.completed}: no usage was sent. */
    public static final String OPENAI_RESPONSES_CUT_OFF = OPENAI_RESPONSES.substring(0, OPENAI_RESPONSES.indexOf("event: response.completed"));

    private static final String ANTHROPIC_FINAL_EVENTS = ""
        + "event: message_delta\n"
        + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":15}}\n\n"
        + "event: message_stop\n"
        + "data: {\"type\":\"message_stop\"}\n\n";

    /** Anthropic Messages: input and cache counts on {@code message_start}, final output on {@code message_delta}: 25 in, 15 out, 7 cache read, 3 cache creation. */
    public static final String ANTHROPIC_MESSAGES = ""
        + "event: message_start\n"
        + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1nZdL29xx5MUA1yADyHTEsnR8uuvGzszyY\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[],\"model\":\"claude-sonnet-4-5-20250929\",\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":25,\"cache_creation_input_tokens\":3,\"cache_read_input_tokens\":7,\"output_tokens\":1}}}\n\n"
        + "event: content_block_start\n"
        + "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n"
        + "event: ping\n"
        + "data: {\"type\": \"ping\"}\n\n"
        + "event: content_block_delta\n"
        + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n\n"
        + "event: content_block_stop\n"
        + "data: {\"type\":\"content_block_stop\",\"index\":0}\n\n"
        + ANTHROPIC_FINAL_EVENTS;

    /** {@link #ANTHROPIC_MESSAGES} cut off before {@code message_delta}: only the opening counts were sent. */
    public static final String ANTHROPIC_MESSAGES_CUT_OFF = ANTHROPIC_MESSAGES.substring(0, ANTHROPIC_MESSAGES.indexOf("event: message_delta"));

    /** Gemini {@code streamGenerateContent?alt=sse}: every chunk has usageMetadata, the last one the full counts: 8 in, 12 candidates, 27 thoughts. */
    public static final String GEMINI_SSE = ""
        + "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello\"}],\"role\":\"model\"},\"index\":0}],\"usageMetadata\":{\"promptTokenCount\":8,\"totalTokenCount\":8,\"promptTokensDetails\":[{\"modality\":\"TEXT\",\"tokenCount\":8}]},\"modelVersion\":\"gemini-2.5-flash\",\"responseId\":\"mAitaL3kLPWtmtkPx5iY8Qk\"}\r\n\r\n"
        + "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" there\"}],\"role\":\"model\"},\"finishReason\":\"STOP\",\"index\":0}],\"usageMetadata\":{\"promptTokenCount\":8,\"candidatesTokenCount\":12,\"totalTokenCount\":47,\"promptTokensDetails\":[{\"modality\":\"TEXT\",\"tokenCount\":8}],\"thoughtsTokenCount\":27},\"modelVersion\":\"gemini-2.5-flash\",\"responseId\":\"mAitaL3kLPWtmtkPx5iY8Qk\"}\r\n\r\n";

    /** Gemini {@code streamGenerateContent} without {@code alt=sse}: one JSON array of the same chunks. */
    public static final String GEMINI_JSON_ARRAY = "[{\n"
        + "  \"candidates\": [\n    {\n      \"content\": {\n        \"parts\": [\n          {\n            \"text\": \"Hello\"\n          }\n        ],\n        \"role\": \"model\"\n      },\n      \"index\": 0\n    }\n  ],\n"
        + "  \"usageMetadata\": {\n    \"promptTokenCount\": 8,\n    \"totalTokenCount\": 8\n  },\n"
        + "  \"modelVersion\": \"gemini-2.5-flash\"\n}\n,\r\n{\n"
        + "  \"candidates\": [\n    {\n      \"content\": {\n        \"parts\": [\n          {\n            \"text\": \" there\"\n          }\n        ],\n        \"role\": \"model\"\n      },\n      \"finishReason\": \"STOP\",\n      \"index\": 0\n    }\n  ],\n"
        + "  \"usageMetadata\": {\n    \"promptTokenCount\": 8,\n    \"candidatesTokenCount\": 12,\n    \"totalTokenCount\": 47,\n    \"thoughtsTokenCount\": 27\n  },\n"
        + "  \"modelVersion\": \"gemini-2.5-flash\"\n}\n]";

    /** Ollama {@code /api/chat}: newline-delimited JSON, counts only on the {@code done: true} line: 26 in, 282 out. */
    public static final String OLLAMA_CHAT = ""
        + "{\"model\":\"llama3.2\",\"created_at\":\"2023-08-04T08:52:19.385406455-07:00\",\"message\":{\"role\":\"assistant\",\"content\":\"The\"},\"done\":false}\n"
        + "{\"model\":\"llama3.2\",\"created_at\":\"2023-08-04T08:52:19.412123455-07:00\",\"message\":{\"role\":\"assistant\",\"content\":\" sky\"},\"done\":false}\n"
        + "{\"model\":\"llama3.2\",\"created_at\":\"2023-08-04T19:22:45.499127Z\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\",\"total_duration\":4883583458,\"load_duration\":1334875,\"prompt_eval_count\":26,\"prompt_eval_duration\":342546000,\"eval_count\":282,\"eval_duration\":4535599000}\n";

    /** Bedrock ConverseStream as AWS event stream messages; usage is on the {@code metadata} event: 25 in, 15 out, 6 cache read. */
    public static byte[] bedrockConverseStream() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        writeEventStreamMessage(stream, "messageStart", "{\"p\":\"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVW\",\"role\":\"assistant\"}");
        writeEventStreamMessage(stream, "contentBlockDelta", "{\"contentBlockIndex\":0,\"delta\":{\"text\":\"Hello\"},\"p\":\"abcdefghijklmnopqrstuvwxyzABCD\"}");
        writeEventStreamMessage(stream, "contentBlockStop", "{\"contentBlockIndex\":0,\"p\":\"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQR\"}");
        writeEventStreamMessage(stream, "messageStop", "{\"p\":\"abcdefghijklmnopqrstuvwxyzABCDEFGHIJK\",\"stopReason\":\"end_turn\"}");
        writeEventStreamMessage(stream, "metadata", "{\"metrics\":{\"latencyMs\":623},\"p\":\"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN\",\"usage\":{\"cacheReadInputTokens\":6,\"cacheWriteInputTokens\":0,\"inputTokens\":25,\"outputTokens\":15,\"totalTokens\":46}}");
        return stream.toByteArray();
    }

    /** Bedrock InvokeModelWithResponseStream: each message's payload wraps one Anthropic event as base64. */
    public static byte[] bedrockInvokeWithResponseStream() {
        return bedrockChunks(anthropicEvents(ANTHROPIC_MESSAGES));
    }

    /**
     * {@link #bedrockInvokeWithResponseStream()} with the {@code amazon-bedrock-invocationMetrics} that Bedrock adds to
     * the last chunk, as langchain-aws reads it ({@code llms/bedrock.py}, {@code _get_invocation_metrics_chunk}).
     */
    public static byte[] bedrockInvokeWithResponseStreamAndInvocationMetrics() {
        return bedrockChunks(anthropicEvents(ANTHROPIC_MESSAGES.replace("{\"type\":\"message_stop\"}",
            "{\"type\":\"message_stop\",\"amazon-bedrock-invocationMetrics\":{\"inputTokenCount\":25,\"outputTokenCount\":15,\"invocationLatency\":623,\"firstByteLatency\":211}}")));
    }

    /**
     * Bedrock InvokeModelWithResponseStream from a Llama model, whose chunks follow the Meta Llama model parameters
     * page; only the last chunk's {@code amazon-bedrock-invocationMetrics} carries the whole call's counts: 8 in, 5 out.
     */
    public static byte[] bedrockInvokeWithResponseStreamOfLlama() {
        return bedrockChunks(
            "{\"generation\":\"Hello\",\"prompt_token_count\":8,\"generation_token_count\":1,\"stop_reason\":null}",
            "{\"generation\":\" there\",\"prompt_token_count\":null,\"generation_token_count\":5,\"stop_reason\":\"stop\","
                + "\"amazon-bedrock-invocationMetrics\":{\"inputTokenCount\":8,\"outputTokenCount\":5,\"invocationLatency\":300,\"firstByteLatency\":100}}");
    }

    private static String[] anthropicEvents(String serverSentEvents) {
        String[] events = serverSentEvents.split("\n\n");
        for (int i = 0; i < events.length; i++) {
            events[i] = events[i].substring(events[i].indexOf("data: ") + "data: ".length());
        }
        return events;
    }

    private static byte[] bedrockChunks(String... events) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (String json : events) {
            String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            writeEventStreamMessage(stream, "chunk", "{\"bytes\":\"" + base64 + "\",\"p\":\"abcdefghijklmnopqrstuvwxyzABCDEFGH\"}");
        }
        return stream.toByteArray();
    }

    /** One AWS event stream message: prelude, prelude CRC, string headers, payload, message CRC. */
    public static void writeEventStreamMessage(ByteArrayOutputStream stream, String eventType, String payloadJson) {
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        writeHeader(headers, ":event-type", eventType);
        writeHeader(headers, ":content-type", "application/json");
        writeHeader(headers, ":message-type", "event");
        byte[] headerBytes = headers.toByteArray();
        byte[] payload = payloadJson.getBytes(StandardCharsets.UTF_8);
        int totalLength = 12 + headerBytes.length + payload.length + 4;
        ByteBuffer message = ByteBuffer.allocate(totalLength);
        message.putInt(totalLength);
        message.putInt(headerBytes.length);
        message.putInt((int) crc32(message.array(), 8));
        message.put(headerBytes);
        message.put(payload);
        message.putInt((int) crc32(message.array(), totalLength - 4));
        stream.write(message.array(), 0, totalLength);
    }

    private static void writeHeader(ByteArrayOutputStream headers, String name, String value) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        headers.write(nameBytes.length);
        headers.write(nameBytes, 0, nameBytes.length);
        headers.write(7);
        headers.write(valueBytes.length >> 8);
        headers.write(valueBytes.length & 0xFF);
        headers.write(valueBytes, 0, valueBytes.length);
    }

    private static long crc32(byte[] bytes, int length) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, length);
        return crc.getValue();
    }
}
