package org.mockserver.netty.integration.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.llm.codec.BedrockEventStreamEncoder;
import org.mockserver.model.Usage;
import org.mockserver.netty.MockServer;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.client.LlmConversationBuilder.conversation;
import static org.mockserver.client.LlmMockBuilder.llmMock;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.Provider.ANTHROPIC;
import static org.mockserver.model.Provider.AZURE_OPENAI;
import static org.mockserver.model.Provider.BEDROCK;
import static org.mockserver.model.Provider.DEEPSEEK;
import static org.mockserver.model.Provider.GEMINI;
import static org.mockserver.model.Provider.OLLAMA;
import static org.mockserver.model.Provider.OPENAI;
import static org.mockserver.model.Provider.OPENAI_RESPONSES;
import static org.mockserver.model.ToolUse.toolUse;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * End-to-end agent loop test for all 7 providers (Bedrock on both InvokeModel and Converse). For each provider:
 * <ul>
 *   <li>Turn 1: register a response that returns a tool_use / function_call</li>
 *   <li>Turn 2: after the client sends a tool_result, return the final answer</li>
 * </ul>
 * Non-streaming only. Each test exercises the full codec encode path,
 * the scenario state machine, and the conversation-aware matcher pipeline.
 */
public class LlmAgentLoopE2eTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static int mockServerPort;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void startServer() {
        mockServerPort = new MockServer().getLocalPort();
        mockServerClient = new MockServerClient("localhost", mockServerPort);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
    }

    @Before
    public void resetServer() {
        mockServerClient.reset();
    }

    // ---- Anthropic ----

    @Test
    public void shouldCompleteAgentLoopForAnthropic() throws Exception {
        conversation()
            .withPath("/v1/messages")
            .withProvider(ANTHROPIC)
            .withModel("claude-sonnet-4-20250514")
            .turn()
                .whenTurnIndex(0)
                .respondingWith(completion()
                    .withText("Let me search for that.")
                    .withToolCall(toolUse("search").withArguments("{\"q\":\"weather paris\"}"))
                    .withStopReason("tool_use"))
            .andThen()
            .turn()
                .whenContainsToolResultFor("search")
                .respondingWith(completion()
                    .withText("It is 18C and sunny in Paris.")
                    .withStopReason("end_turn"))
            .andThen()
            .applyTo(mockServerClient);

        // Turn 1
        String turn1Body = "{\"model\":\"claude-sonnet-4-20250514\",\"messages\":[{\"role\":\"user\",\"content\":\"What is the weather in Paris?\"}]}";
        String turn1Response = sendPost("/v1/messages", turn1Body);
        assertThat(turn1Response, containsString("200"));
        JsonNode turn1 = OBJECT_MAPPER.readTree(extractJsonBody(turn1Response));
        assertThat(turn1.get("type").asText(), is("message"));
        assertThat(turn1.get("stop_reason").asText(), is("tool_use"));
        assertToolUsePresent(turn1, "search");

        // Turn 2
        String turn2Body = "{\"model\":\"claude-sonnet-4-20250514\",\"messages\":["
            + "{\"role\":\"user\",\"content\":\"What is the weather in Paris?\"},"
            + "{\"role\":\"assistant\",\"content\":["
            + "{\"type\":\"text\",\"text\":\"Let me search for that.\"},"
            + "{\"type\":\"tool_use\",\"id\":\"toolu_123\",\"name\":\"search\",\"input\":{\"q\":\"weather paris\"}}"
            + "]},"
            + "{\"role\":\"user\",\"content\":["
            + "{\"type\":\"tool_result\",\"tool_use_id\":\"toolu_123\",\"content\":\"18C and sunny\"}"
            + "]}"
            + "]}";
        String turn2Response = sendPost("/v1/messages", turn2Body);
        assertThat(turn2Response, containsString("200"));
        JsonNode turn2 = OBJECT_MAPPER.readTree(extractJsonBody(turn2Response));
        assertThat(turn2.get("type").asText(), is("message"));
        assertThat(turn2.get("stop_reason").asText(), is("end_turn"));
        assertTextBlockContains(turn2, "18C and sunny");
    }

    // ---- OpenAI Chat Completions ----

    @Test
    public void shouldCompleteAgentLoopForOpenAi() throws Exception {
        conversation()
            .withPath("/v1/chat/completions")
            .withProvider(OPENAI)
            .withModel("gpt-4o")
            .turn()
                .whenTurnIndex(0)
                .respondingWith(completion()
                    .withToolCall(toolUse("get_weather").withArguments("{\"city\":\"Paris\"}"))
                    .withStopReason("tool_use"))
            .andThen()
            .turn()
                .whenContainsToolResultFor("get_weather")
                .respondingWith(completion()
                    .withText("The weather in Paris is 18C and sunny.")
                    .withStopReason("end_turn"))
            .andThen()
            .applyTo(mockServerClient);

        // Turn 1
        String turn1Body = "{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"What is the weather in Paris?\"}]}";
        String turn1Response = sendPost("/v1/chat/completions", turn1Body);
        assertThat(turn1Response, containsString("200"));
        JsonNode turn1 = OBJECT_MAPPER.readTree(extractJsonBody(turn1Response));
        assertThat(turn1.get("object").asText(), is("chat.completion"));
        assertThat(turn1.path("choices").get(0).get("finish_reason").asText(), is("tool_calls"));
        assertOpenAiToolCallPresent(turn1, "get_weather");

        // Turn 2 with tool result
        String callId = turn1.path("choices").get(0).path("message").path("tool_calls").get(0).path("id").asText();
        String turn2Body = "{\"model\":\"gpt-4o\",\"messages\":["
            + "{\"role\":\"user\",\"content\":\"What is the weather in Paris?\"},"
            + "{\"role\":\"assistant\",\"content\":null,\"tool_calls\":["
            + "{\"id\":\"" + callId + "\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\",\"arguments\":\"{\\\"city\\\":\\\"Paris\\\"}\"}}"
            + "]},"
            + "{\"role\":\"tool\",\"tool_call_id\":\"" + callId + "\",\"content\":\"18C and sunny\"}"
            + "]}";
        String turn2Response = sendPost("/v1/chat/completions", turn2Body);
        assertThat(turn2Response, containsString("200"));
        JsonNode turn2 = OBJECT_MAPPER.readTree(extractJsonBody(turn2Response));
        assertThat(turn2.path("choices").get(0).get("finish_reason").asText(), is("stop"));
        assertThat(turn2.path("choices").get(0).path("message").get("content").asText(), containsString("18C and sunny"));
    }

    // ---- OpenAI Responses ----

    @Test
    public void shouldCompleteAgentLoopForOpenAiResponses() throws Exception {
        conversation()
            .withPath("/v1/responses")
            .withProvider(OPENAI_RESPONSES)
            .withModel("gpt-4o")
            .turn()
                .whenTurnIndex(0)
                .respondingWith(completion()
                    .withToolCall(toolUse("get_weather").withArguments("{\"city\":\"Paris\"}"))
                    .withStopReason("tool_use"))
            .andThen()
            .turn()
                .whenContainsToolResultFor("get_weather")
                .respondingWith(completion()
                    .withText("It is 18C and sunny in Paris.")
                    .withStopReason("end_turn"))
            .andThen()
            .applyTo(mockServerClient);

        // Turn 1: Responses API uses "input" array instead of "messages"
        String turn1Body = "{\"model\":\"gpt-4o\",\"input\":[{\"role\":\"user\",\"content\":\"What is the weather in Paris?\"}]}";
        String turn1Response = sendPost("/v1/responses", turn1Body);
        assertThat(turn1Response, containsString("200"));
        JsonNode turn1 = OBJECT_MAPPER.readTree(extractJsonBody(turn1Response));
        assertThat(turn1.get("object").asText(), is("response"));
        // Status is the field Responses-API clients check to know a response is finished.
        assertThat(turn1.get("status").asText(), is("completed"));
        // Verify function_call in output
        boolean hasFunctionCall = false;
        for (JsonNode item : turn1.get("output")) {
            if ("function_call".equals(item.get("type").asText())) {
                assertThat(item.get("name").asText(), is("get_weather"));
                hasFunctionCall = true;
            }
        }
        assertThat("Turn 1 should have function_call output", hasFunctionCall, is(true));

        // Turn 2: send function_call_output, answering the call by its call_id (distinct from the fc_ item id)
        String fcId = "";
        String callId = "";
        for (JsonNode item : turn1.get("output")) {
            if ("function_call".equals(item.get("type").asText())) {
                fcId = item.get("id").asText();
                callId = item.get("call_id").asText();
            }
        }
        assertThat(callId, startsWith("call_"));
        String turn2Body = "{\"model\":\"gpt-4o\",\"input\":["
            + "{\"role\":\"user\",\"content\":\"What is the weather in Paris?\"},"
            + "{\"type\":\"function_call\",\"id\":\"" + fcId + "\",\"call_id\":\"" + callId + "\",\"name\":\"get_weather\",\"arguments\":\"{\\\"city\\\":\\\"Paris\\\"}\"},"
            + "{\"type\":\"function_call_output\",\"call_id\":\"" + callId + "\",\"output\":\"18C and sunny\"}"
            + "]}";
        String turn2Response = sendPost("/v1/responses", turn2Body);
        assertThat(turn2Response, containsString("200"));
        JsonNode turn2 = OBJECT_MAPPER.readTree(extractJsonBody(turn2Response));
        assertThat(turn2.get("object").asText(), is("response"));
        assertThat(turn2.get("status").asText(), is("completed"));
        // Verify text in output
        boolean hasText = false;
        for (JsonNode item : turn2.get("output")) {
            if ("message".equals(item.get("type").asText())) {
                for (JsonNode content : item.get("content")) {
                    if ("output_text".equals(content.get("type").asText())) {
                        assertThat(content.get("text").asText(), containsString("18C and sunny"));
                        hasText = true;
                    }
                }
            }
        }
        assertThat("Turn 2 should have text output", hasText, is(true));
    }

    // ---- Gemini ----

    @Test
    public void shouldCompleteAgentLoopForGemini() throws Exception {
        // Gemini uses "contents" and "parts" instead of "messages".
        // Turn ordering is handled by the scenario state machine; conversation
        // predicates are verified separately in LlmConversationMatcherTest.
        conversation()
            .withPath("/v1beta/models/gemini-2.0-flash/generateContent")
            .withProvider(GEMINI)
            .withModel("gemini-2.0-flash")
            .turn()
                .respondingWith(completion()
                    .withToolCall(toolUse("get_weather").withArguments("{\"city\":\"Paris\"}"))
                    .withStopReason("end_turn"))
            .andThen()
            .turn()
                .respondingWith(completion()
                    .withText("It is 18C and sunny in Paris.")
                    .withStopReason("end_turn"))
            .andThen()
            .applyTo(mockServerClient);

        // Turn 1: Gemini uses "contents" array with "parts"
        String turn1Body = "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"What is the weather in Paris?\"}]}]}";
        String turn1Response = sendPost("/v1beta/models/gemini-2.0-flash/generateContent", turn1Body);
        assertThat(turn1Response, containsString("200"));
        JsonNode turn1 = OBJECT_MAPPER.readTree(extractJsonBody(turn1Response));
        assertThat(turn1.has("candidates"), is(true));
        // Gemini collapses every stop reason without a dedicated tool-call category
        // into STOP (see GeminiCodec.mapFinishReason); pin the wire contract.
        assertThat(turn1.path("candidates").get(0).get("finishReason").asText(), is("STOP"));
        // Verify functionCall in parts
        boolean hasFc = false;
        for (JsonNode part : turn1.path("candidates").get(0).path("content").path("parts")) {
            if (part.has("functionCall")) {
                assertThat(part.path("functionCall").get("name").asText(), is("get_weather"));
                hasFc = true;
            }
        }
        assertThat("Turn 1 should have functionCall part", hasFc, is(true));

        // Turn 2: send functionResponse
        String turn2Body = "{\"contents\":["
            + "{\"role\":\"user\",\"parts\":[{\"text\":\"What is the weather in Paris?\"}]},"
            + "{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"get_weather\",\"args\":{\"city\":\"Paris\"}}}]},"
            + "{\"role\":\"user\",\"parts\":[{\"functionResponse\":{\"name\":\"get_weather\",\"response\":\"18C and sunny\"}}]}"
            + "]}";
        String turn2Response = sendPost("/v1beta/models/gemini-2.0-flash/generateContent", turn2Body);
        assertThat(turn2Response, containsString("200"));
        JsonNode turn2 = OBJECT_MAPPER.readTree(extractJsonBody(turn2Response));
        assertThat(turn2.has("candidates"), is(true));
        assertThat(turn2.path("candidates").get(0).get("finishReason").asText(), is("STOP"));
        boolean hasTextPart = false;
        for (JsonNode part : turn2.path("candidates").get(0).path("content").path("parts")) {
            if (part.has("text")) {
                assertThat(part.get("text").asText(), containsString("18C and sunny"));
                hasTextPart = true;
            }
        }
        assertThat("Turn 2 should have text part", hasTextPart, is(true));
    }

    @Test
    public void shouldMatchContainsToolResultForGeminiEndToEnd() throws Exception {
        // Reproduction for the documented E2E false-negative: drive turn 2 via the
        // containsToolResultFor predicate (not scenario ordering) for Gemini.
        conversation()
            .withPath("/v1beta/models/gemini-2.0-flash/generateContent")
            .withProvider(GEMINI)
            .withModel("gemini-2.0-flash")
            .turn()
                .whenTurnIndex(0)
                .respondingWith(completion()
                    .withToolCall(toolUse("get_weather").withArguments("{\"city\":\"Paris\"}"))
                    .withStopReason("end_turn"))
            .andThen()
            .turn()
                .whenContainsToolResultFor("get_weather")
                .respondingWith(completion()
                    .withText("It is 18C and sunny in Paris.")
                    .withStopReason("end_turn"))
            .andThen()
            .applyTo(mockServerClient);

        String turn1Body = "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"What is the weather in Paris?\"}]}]}";
        sendPost("/v1beta/models/gemini-2.0-flash/generateContent", turn1Body);

        String turn2Body = "{\"contents\":["
            + "{\"role\":\"user\",\"parts\":[{\"text\":\"What is the weather in Paris?\"}]},"
            + "{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"get_weather\",\"args\":{\"city\":\"Paris\"}}}]},"
            + "{\"role\":\"user\",\"parts\":[{\"functionResponse\":{\"name\":\"get_weather\",\"response\":\"18C and sunny\"}}]}"
            + "]}";
        String turn2Response = sendPost("/v1beta/models/gemini-2.0-flash/generateContent", turn2Body);
        JsonNode turn2 = OBJECT_MAPPER.readTree(extractJsonBody(turn2Response));
        boolean hasTextPart = false;
        for (JsonNode part : turn2.path("candidates").get(0).path("content").path("parts")) {
            if (part.has("text") && part.get("text").asText().contains("18C and sunny")) {
                hasTextPart = true;
            }
        }
        assertThat("Turn 2 (containsToolResultFor) should return the final answer", hasTextPart, is(true));
    }

    // ---- AWS Bedrock ----

    @Test
    public void shouldCompleteAgentLoopForBedrock() throws Exception {
        conversation()
            .withPath("/model/anthropic.claude-3-5-sonnet-20241022-v2:0/invoke")
            .withProvider(BEDROCK)
            .withModel("anthropic.claude-3-5-sonnet-20241022-v2:0")
            .turn()
                .whenTurnIndex(0)
                .respondingWith(completion()
                    .withText("Let me search.")
                    .withToolCall(toolUse("search").withArguments("{\"q\":\"weather\"}"))
                    .withStopReason("tool_use"))
            .andThen()
            .turn()
                .whenContainsToolResultFor("search")
                .respondingWith(completion()
                    .withText("It is 18C and sunny in Paris.")
                    .withStopReason("end_turn"))
            .andThen()
            .applyTo(mockServerClient);

        // Bedrock uses the same Anthropic Messages body format
        String turn1Body = "{\"messages\":[{\"role\":\"user\",\"content\":\"What is the weather?\"}]}";
        String turn1Response = sendPost("/model/anthropic.claude-3-5-sonnet-20241022-v2:0/invoke", turn1Body);
        assertThat(turn1Response, containsString("200"));
        JsonNode turn1 = OBJECT_MAPPER.readTree(extractJsonBody(turn1Response));
        assertThat(turn1.get("type").asText(), is("message"));
        assertThat(turn1.get("stop_reason").asText(), is("tool_use"));
        assertToolUsePresent(turn1, "search");

        // Turn 2
        String turn2Body = "{\"messages\":["
            + "{\"role\":\"user\",\"content\":\"What is the weather?\"},"
            + "{\"role\":\"assistant\",\"content\":["
            + "{\"type\":\"text\",\"text\":\"Let me search.\"},"
            + "{\"type\":\"tool_use\",\"id\":\"toolu_abc\",\"name\":\"search\",\"input\":{\"q\":\"weather\"}}"
            + "]},"
            + "{\"role\":\"user\",\"content\":["
            + "{\"type\":\"tool_result\",\"tool_use_id\":\"toolu_abc\",\"content\":\"18C and sunny\"}"
            + "]}"
            + "]}";
        String turn2Response = sendPost("/model/anthropic.claude-3-5-sonnet-20241022-v2:0/invoke", turn2Body);
        assertThat(turn2Response, containsString("200"));
        JsonNode turn2 = OBJECT_MAPPER.readTree(extractJsonBody(turn2Response));
        assertThat(turn2.get("stop_reason").asText(), is("end_turn"));
        assertTextBlockContains(turn2, "18C and sunny");
    }

    // ---- AWS Bedrock Converse (GitHub discussion #2757) ----

    @Test
    public void shouldReturnBedrockConverseEnvelopeForDiscussion2757Expectation() throws Exception {
        // the exact expectation from the discussion, registered as raw JSON over the REST API
        String expectation = "{"
            + "\"httpRequest\": { \"method\": \"POST\", \"path\": \"/model/amazon.titan-text-express-v1/converse\" },"
            + "\"httpLlmResponse\": {"
            + "  \"provider\": \"BEDROCK\","
            + "  \"model\": \"amazon.titan-text-express-v1\","
            + "  \"completion\": { \"text\": \"mocked response\", \"usage\": { \"inputTokens\": 5, \"outputTokens\": 5 } }"
            + "}}";
        assertThat(sendRequest("PUT", "/mockserver/expectation", expectation), containsString("201"));

        String converseRequest = "{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"Hello\"}]}]}";
        String rawResponse = sendPost("/model/amazon.titan-text-express-v1/converse", converseRequest);

        assertThat(rawResponse, containsString("200"));
        JsonNode body = OBJECT_MAPPER.readTree(extractJsonBody(rawResponse));
        // the JSON Pointer Kuadrant's TokenRateLimitPolicy uses by default: /usage/totalTokens
        assertThat(body.at("/usage/totalTokens").asInt(-1), is(10));
        assertThat(body.at("/usage/inputTokens").asInt(-1), is(5));
        assertThat(body.at("/usage/outputTokens").asInt(-1), is(5));
        assertThat(body.at("/output/message/content/0/text").asText(), is("mocked response"));
        assertThat(body.at("/output/message/role").asText(), is("assistant"));
        assertThat(body.path("stopReason").asText(), is("end_turn"));
        assertThat(body.has("type"), is(false));
        assertThat(body.has("id"), is(false));
    }

    @Test
    public void shouldCompleteAgentLoopForBedrockConverse() throws Exception {
        String path = "/model/anthropic.claude-3-5-sonnet-20241022-v2:0/converse";
        conversation()
            .withPath(path)
            .withProvider(BEDROCK)
            .withModel("anthropic.claude-3-5-sonnet-20241022-v2:0")
            .turn()
                .whenTurnIndex(0)
                .respondingWith(completion()
                    .withToolCall(toolUse("search").withArguments("{\"q\":\"weather\"}")))
            .andThen()
            .turn()
                .whenContainsToolResultFor("search")
                .respondingWith(completion()
                    .withText("It is 18C and sunny in Paris."))
            .andThen()
            .applyTo(mockServerClient);

        String turn1Body = "{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"What is the weather?\"}]}],"
            + "\"toolConfig\":{\"tools\":[{\"toolSpec\":{\"name\":\"search\",\"inputSchema\":{\"json\":{\"type\":\"object\"}}}}]}}";
        JsonNode turn1 = OBJECT_MAPPER.readTree(extractJsonBody(sendPost(path, turn1Body)));
        assertThat(turn1.path("stopReason").asText(), is("tool_use"));
        JsonNode toolUse = turn1.at("/output/message/content/0/toolUse");
        assertThat(toolUse.path("name").asText(), is("search"));
        assertThat(toolUse.at("/input/q").asText(), is("weather"));
        String toolUseId = toolUse.path("toolUseId").asText();

        // turn 2 sends the toolResult back in Converse shape; the matcher must decode it
        String turn2Body = "{\"messages\":["
            + "{\"role\":\"user\",\"content\":[{\"text\":\"What is the weather?\"}]},"
            + "{\"role\":\"assistant\",\"content\":[{\"toolUse\":{\"toolUseId\":\"" + toolUseId + "\",\"name\":\"search\",\"input\":{\"q\":\"weather\"}}}]},"
            + "{\"role\":\"user\",\"content\":[{\"toolResult\":{\"toolUseId\":\"" + toolUseId + "\",\"content\":[{\"text\":\"18C and sunny\"}]}}]}"
            + "]}";
        JsonNode turn2 = OBJECT_MAPPER.readTree(extractJsonBody(sendPost(path, turn2Body)));
        assertThat(turn2.path("stopReason").asText(), is("end_turn"));
        assertThat(turn2.at("/output/message/content/0/text").asText(), containsString("18C and sunny"));
    }

    // ---- Azure OpenAI ----

    @Test
    public void shouldCompleteAgentLoopForAzureOpenAi() throws Exception {
        conversation()
            .withPath("/openai/deployments/my-deployment/chat/completions")
            .withProvider(AZURE_OPENAI)
            .withModel("gpt-4o")
            .turn()
                .whenTurnIndex(0)
                .respondingWith(completion()
                    .withToolCall(toolUse("get_weather").withArguments("{\"city\":\"Paris\"}"))
                    .withStopReason("tool_use"))
            .andThen()
            .turn()
                .whenContainsToolResultFor("get_weather")
                .respondingWith(completion()
                    .withText("18C and sunny in Paris.")
                    .withStopReason("end_turn"))
            .andThen()
            .applyTo(mockServerClient);

        // Azure OpenAI uses the same request format as OpenAI Chat Completions
        String turn1Body = "{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"What is the weather?\"}]}";
        String turn1Response = sendPost("/openai/deployments/my-deployment/chat/completions", turn1Body);
        assertThat(turn1Response, containsString("200"));
        JsonNode turn1 = OBJECT_MAPPER.readTree(extractJsonBody(turn1Response));
        assertThat(turn1.get("object").asText(), is("chat.completion"));
        assertThat(turn1.path("choices").get(0).get("finish_reason").asText(), is("tool_calls"));
        assertOpenAiToolCallPresent(turn1, "get_weather");

        // Turn 2
        String callId = turn1.path("choices").get(0).path("message").path("tool_calls").get(0).path("id").asText();
        String turn2Body = "{\"model\":\"gpt-4o\",\"messages\":["
            + "{\"role\":\"user\",\"content\":\"What is the weather?\"},"
            + "{\"role\":\"assistant\",\"content\":null,\"tool_calls\":["
            + "{\"id\":\"" + callId + "\",\"type\":\"function\",\"function\":{\"name\":\"get_weather\",\"arguments\":\"{\\\"city\\\":\\\"Paris\\\"}\"}}"
            + "]},"
            + "{\"role\":\"tool\",\"tool_call_id\":\"" + callId + "\",\"content\":\"18C and sunny\"}"
            + "]}";
        String turn2Response = sendPost("/openai/deployments/my-deployment/chat/completions", turn2Body);
        assertThat(turn2Response, containsString("200"));
        JsonNode turn2 = OBJECT_MAPPER.readTree(extractJsonBody(turn2Response));
        assertThat(turn2.path("choices").get(0).get("finish_reason").asText(), is("stop"));
        assertThat(turn2.path("choices").get(0).path("message").get("content").asText(), containsString("18C and sunny"));
    }

    // ---- Ollama ----

    @Test
    public void shouldCompleteAgentLoopForOllama() throws Exception {
        conversation()
            .withPath("/api/chat")
            .withProvider(OLLAMA)
            .withModel("llama3.2")
            .turn()
                .respondingWith(completion()
                    .withToolCall(toolUse("get_weather").withArguments("{\"city\":\"Paris\"}"))
                    .withStopReason("tool_use"))
            .andThen()
            .turn()
                .respondingWith(completion()
                    .withText("It is 18C and sunny in Paris.")
                    .withStopReason("end_turn"))
            .andThen()
            .applyTo(mockServerClient);

        // Turn 1: Ollama uses "messages" array like OpenAI
        String turn1Body = "{\"model\":\"llama3.2\",\"messages\":[{\"role\":\"user\",\"content\":\"What is the weather?\"}]}";
        String turn1Response = sendPost("/api/chat", turn1Body);
        assertThat(turn1Response, containsString("200"));
        JsonNode turn1 = OBJECT_MAPPER.readTree(extractJsonBody(turn1Response));
        assertThat(turn1.get("done").asBoolean(), is(true));
        // Verify tool_calls in message
        boolean hasToolCall = false;
        for (JsonNode tc : turn1.path("message").path("tool_calls")) {
            if ("get_weather".equals(tc.path("function").path("name").asText())) {
                hasToolCall = true;
            }
        }
        assertThat("Ollama turn 1 should have tool_calls", hasToolCall, is(true));

        // Turn 2: Ollama tool results sent as role=tool messages
        String turn2Body = "{\"model\":\"llama3.2\",\"messages\":["
            + "{\"role\":\"user\",\"content\":\"What is the weather?\"},"
            + "{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":["
            + "{\"function\":{\"name\":\"get_weather\",\"arguments\":{\"city\":\"Paris\"}}}"
            + "]},"
            + "{\"role\":\"tool\",\"content\":\"18C and sunny\"}"
            + "]}";
        String turn2Response = sendPost("/api/chat", turn2Body);
        assertThat(turn2Response, containsString("200"));
        JsonNode turn2 = OBJECT_MAPPER.readTree(extractJsonBody(turn2Response));
        assertThat(turn2.get("done").asBoolean(), is(true));
        assertThat(turn2.path("message").get("content").asText(), containsString("18C and sunny"));
    }

    @Test
    public void shouldMatchContainsToolResultForOllamaEndToEnd() throws Exception {
        // Reproduction for the documented E2E false-negative: drive turn 2 via the
        // containsToolResultFor predicate (not scenario ordering) for Ollama.
        conversation()
            .withPath("/api/chat")
            .withProvider(OLLAMA)
            .withModel("llama3.2")
            .turn()
                .whenTurnIndex(0)
                .respondingWith(completion()
                    .withToolCall(toolUse("get_weather").withArguments("{\"city\":\"Paris\"}"))
                    .withStopReason("tool_use"))
            .andThen()
            .turn()
                .whenContainsToolResultFor("get_weather")
                .respondingWith(completion()
                    .withText("It is 18C and sunny in Paris.")
                    .withStopReason("end_turn"))
            .andThen()
            .applyTo(mockServerClient);

        String turn1Body = "{\"model\":\"llama3.2\",\"messages\":[{\"role\":\"user\",\"content\":\"What is the weather?\"}]}";
        sendPost("/api/chat", turn1Body);

        String turn2Body = "{\"model\":\"llama3.2\",\"messages\":["
            + "{\"role\":\"user\",\"content\":\"What is the weather?\"},"
            + "{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":["
            + "{\"function\":{\"name\":\"get_weather\",\"arguments\":{\"city\":\"Paris\"}}}"
            + "]},"
            + "{\"role\":\"tool\",\"content\":\"18C and sunny\"}"
            + "]}";
        String turn2Response = sendPost("/api/chat", turn2Body);
        JsonNode turn2 = OBJECT_MAPPER.readTree(extractJsonBody(turn2Response));
        assertThat("Turn 2 (containsToolResultFor) should return the final answer",
            turn2.path("message").get("content").asText(), containsString("18C and sunny"));
    }

    // ---- Streaming E2E ----

    @Test
    public void shouldStreamAnthropicResponseThroughNettyPipeline() throws Exception {
        // Gap 5: verify streaming reaches the client correctly via the Netty
        // pipeline, not just the codec unit tests
        llmMock("/v1/messages/stream")
            .withProvider(ANTHROPIC)
            .withModel("claude-sonnet-4-20250514")
            .respondingWith(completion()
                .withText("Hello streaming world")
                .withStreaming(true))
            .applyTo(mockServerClient);

        String body = "{\"model\":\"claude-sonnet-4-20250514\",\"stream\":true,"
            + "\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}";
        String rawResponse = sendPost("/v1/messages/stream", body);

        // Should get SSE events, not a regular JSON body
        assertThat(rawResponse, containsString("200"));
        assertThat(rawResponse, containsString("text/event-stream"));
        assertThat(rawResponse, containsString("event: message_start"));
        assertThat(rawResponse, containsString("event: content_block_delta"));
        assertThat(rawResponse, containsString("event: message_stop"));

        // The streamed text arrives as (subword-sized by default) text_delta fragments,
        // so it is not present as contiguous substrings on the raw SSE stream. Reconstruct
        // it from the delta events before asserting on the content.
        StringBuilder reconstructed = new StringBuilder();
        java.util.regex.Matcher deltaText =
            java.util.regex.Pattern.compile("\"text\":\"([^\"]*)\"").matcher(rawResponse);
        while (deltaText.find()) {
            reconstructed.append(deltaText.group(1));
        }
        assertThat(reconstructed.toString(), containsString("Hello"));
        assertThat(reconstructed.toString(), containsString("streaming"));
        assertThat(reconstructed.toString(), containsString("world"));
    }

    @Test
    public void shouldStreamOpenAiResponseThroughNettyPipeline() throws Exception {
        llmMock("/v1/chat/completions/stream")
            .withProvider(OPENAI)
            .withModel("gpt-4o")
            .respondingWith(completion()
                .withText("Hello from streaming OpenAI")
                .withStreaming(true))
            .applyTo(mockServerClient);

        String body = "{\"model\":\"gpt-4o\",\"stream\":true,"
            + "\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}";
        String rawResponse = sendPost("/v1/chat/completions/stream", body);

        // OpenAI streaming uses data: lines with chat.completion.chunk objects
        assertThat(rawResponse, containsString("200"));
        assertThat(rawResponse, containsString("text/event-stream"));
        assertThat(rawResponse, containsString("chat.completion.chunk"));
        assertThat(rawResponse, containsString("[DONE]"));
    }

    @Test
    public void shouldStreamOpenAiUsageChunkThroughNettyPipelineWhenRequestSetsIncludeUsage() throws Exception {
        // AI SDKs (Vercel AI SDK, LangChain stream_usage, LiteLLM) send stream_options.include_usage
        // and read token counts only from the final choices:[] chunk before [DONE].
        for (org.mockserver.model.Provider provider : new org.mockserver.model.Provider[]{OPENAI, AZURE_OPENAI}) {
            mockServerClient.reset();
            String path = "/v1/chat/completions/" + provider.name().toLowerCase();
            llmMock(path)
                .withProvider(provider)
                .withModel("gpt-4o")
                .respondingWith(completion()
                    .withText("Hello from streaming usage")
                    .withUsage(Usage.usage().withInputTokens(11).withOutputTokens(7).withCachedInputTokens(5))
                    .withStreaming(true))
                .applyTo(mockServerClient);

            String body = "{\"model\":\"gpt-4o\",\"stream\":true,\"stream_options\":{\"include_usage\":true},"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}";
            List<String> dataLines = sseDataLines(sendPostRaw(path, body));

            String ctx = provider + " ";
            assertThat(ctx + "ends with [DONE]", dataLines.get(dataLines.size() - 1), is("[DONE]"));
            JsonNode usageChunk = OBJECT_MAPPER.readTree(dataLines.get(dataLines.size() - 2));
            assertThat(ctx + "usage chunk has empty choices", usageChunk.get("choices").size(), is(0));
            assertThat(ctx + "prompt_tokens", usageChunk.path("usage").path("prompt_tokens").asInt(-1), is(11));
            assertThat(ctx + "completion_tokens", usageChunk.path("usage").path("completion_tokens").asInt(-1), is(7));
            assertThat(ctx + "total_tokens", usageChunk.path("usage").path("total_tokens").asInt(-1), is(18));
            assertThat(ctx + "cached_tokens", usageChunk.path("usage").path("prompt_tokens_details").path("cached_tokens").asInt(-1), is(5));
            for (String line : dataLines.subList(0, dataLines.size() - 2)) {
                JsonNode chunk = OBJECT_MAPPER.readTree(line);
                assertThat(ctx + "usage is null on content chunks", chunk.has("usage") && chunk.get("usage").isNull(), is(true));
            }
        }
    }

    @Test
    public void shouldStreamDeepSeekUsageOnTheFinishChunkWithOrWithoutIncludeUsage() throws Exception {
        // DeepSeek's API reference: the usage is on the last chunk before [DONE], the chunk whose one
        // choice has the finish_reason, whether or not the request sets stream_options.include_usage
        llmMock("/chat/completions")
            .withProvider(DEEPSEEK)
            .withModel("deepseek-flash")
            .respondingWith(completion()
                .withText("Hello from DeepSeek")
                .withUsage(Usage.usage().withInputTokens(17).withOutputTokens(9))
                .withStreaming(true))
            .applyTo(mockServerClient);

        for (boolean includeUsage : new boolean[]{false, true}) {
            String body = "{\"model\":\"deepseek-flash\",\"stream\":true,"
                + (includeUsage ? "\"stream_options\":{\"include_usage\":true}," : "")
                + "\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}";
            List<String> dataLines = sseDataLines(sendPostRaw("/chat/completions", body));

            String ctx = "include_usage=" + includeUsage + " ";
            assertThat(ctx + "ends with [DONE]", dataLines.get(dataLines.size() - 1), is("[DONE]"));
            JsonNode last = OBJECT_MAPPER.readTree(dataLines.get(dataLines.size() - 2));
            assertThat(ctx + "finish chunk has one choice", last.get("choices").size(), is(1));
            assertThat(ctx + "finish_reason", last.path("choices").path(0).path("finish_reason").asText(), is("stop"));
            assertThat(ctx + "prompt_tokens", last.path("usage").path("prompt_tokens").asInt(-1), is(17));
            assertThat(ctx + "completion_tokens", last.path("usage").path("completion_tokens").asInt(-1), is(9));
            assertThat(ctx + "prompt_cache_miss_tokens", last.path("usage").path("prompt_cache_miss_tokens").asInt(-1), is(17));
            for (String line : dataLines.subList(0, dataLines.size() - 2)) {
                JsonNode chunk = OBJECT_MAPPER.readTree(line);
                assertThat(ctx + "one choice", chunk.get("choices").size(), is(1));
                assertThat(ctx + "usage on earlier chunks",
                    chunk.has("usage"), is(includeUsage));
                if (includeUsage) {
                    assertThat(ctx + "usage is null on earlier chunks", chunk.get("usage").isNull(), is(true));
                }
            }
        }
    }

    @Test
    public void shouldNotStreamOpenAiUsageChunkWhenRequestDoesNotSetIncludeUsage() throws Exception {
        llmMock("/v1/chat/completions/no-usage")
            .withProvider(OPENAI)
            .withModel("gpt-4o")
            .respondingWith(completion()
                .withText("Hello without usage")
                .withUsage(Usage.usage().withInputTokens(11).withOutputTokens(7))
                .withStreaming(true))
            .applyTo(mockServerClient);

        String body = "{\"model\":\"gpt-4o\",\"stream\":true,"
            + "\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}";
        List<String> dataLines = sseDataLines(sendPostRaw("/v1/chat/completions/no-usage", body));

        assertThat(dataLines.get(dataLines.size() - 1), is("[DONE]"));
        for (String line : dataLines.subList(0, dataLines.size() - 1)) {
            JsonNode chunk = OBJECT_MAPPER.readTree(line);
            assertThat(chunk.has("usage"), is(false));
            assertThat(chunk.get("choices").size(), is(1));
        }
    }

    /** The {@code data:} payloads of an HTTP/1.1 SSE response, de-chunking the body when it is chunked. */
    private static List<String> sseDataLines(byte[] rawBytes) {
        int headerEnd = indexOfSequence(rawBytes, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), 0);
        assertThat("response must have a header/body boundary", headerEnd, greaterThanOrEqualTo(0));
        String headerText = new String(rawBytes, 0, headerEnd, StandardCharsets.US_ASCII);
        assertThat(headerText, containsString("200"));
        assertThat(headerText, containsString("text/event-stream"));
        byte[] body = new byte[rawBytes.length - (headerEnd + 4)];
        System.arraycopy(rawBytes, headerEnd + 4, body, 0, body.length);
        if (headerText.toLowerCase().contains("transfer-encoding: chunked")) {
            body = deChunkHttpBody(body);
        }
        List<String> dataLines = new ArrayList<>();
        for (String line : new String(body, StandardCharsets.UTF_8).split("\r?\n")) {
            if (line.startsWith("data:")) {
                dataLines.add(line.substring("data:".length()).trim());
            }
        }
        return dataLines;
    }

    @Test
    public void shouldKeepHttp1ConnectionAliveForStreamingLlmResponseByDefault() throws Exception {
        // GitHub issue #2641 - the path the reporter actually hit. A streaming httpLlmResponse is
        // served through HttpSseResponseActionHandler (HttpActionHandler builds the HttpSseResponse
        // WITHOUT closeConnection), so it used to take the null -> always-close default and drop the
        // HTTP/1.1 connection even though the response head promised keep-alive. A streaming client
        // that reuses the connection (the norm for an OpenAI-style client) then failed on its next
        // request. This proves the connection now survives by default.
        llmMock("/v1/chat/completions/keepalive")
            .withProvider(OPENAI)
            .withModel("gpt-4o")
            .respondingWith(completion()
                .withText("Hello from a reusable stream")
                .withStreaming(true))
            .applyTo(mockServerClient);

        String body = "{\"model\":\"gpt-4o\",\"stream\":true,"
            + "\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}";

        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            InputStream input = socket.getInputStream();

            // when - a keep-alive client reads the whole first stream
            writeKeepAlivePost(output, "/v1/chat/completions/keepalive", body);
            String first = readOneChunkedResponse(input);

            // then - the stream completed, and the Connection header told the truth (keep-alive)
            assertThat(first, containsString("HTTP/1.1 200 OK"));
            assertThat(first, containsString("text/event-stream"));
            assertThat(first, containsString("connection: keep-alive"));
            assertThat(first, containsString("chat.completion.chunk"));
            assertThat(first, containsString("[DONE]"));

            // when - a SECOND streaming request is sent on the SAME socket
            writeKeepAlivePost(output, "/v1/chat/completions/keepalive", body);
            String second = readOneChunkedResponse(input);

            // then - it is served, proving the connection survived the first stream. Before the fix
            // the first stream closed the socket, so this read hit EOF / RemoteDisconnected.
            assertThat(second, containsString("HTTP/1.1 200 OK"));
            assertThat(second, containsString("[DONE]"));
        }
    }

    private void writeKeepAlivePost(OutputStream output, String path, String body) throws IOException {
        byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
        StringBuilder request = new StringBuilder();
        request.append("POST ").append(path).append(" HTTP/1.1\r\n");
        request.append("Host: localhost:").append(mockServerPort).append("\r\n");
        request.append("Content-Type: application/json\r\n");
        request.append("Connection: keep-alive\r\n");
        request.append("Content-Length: ").append(bodyBytes.length).append("\r\n\r\n");
        output.write(request.toString().getBytes(StandardCharsets.UTF_8));
        if (bodyBytes.length > 0) {
            output.write(bodyBytes);
        }
        output.flush();
    }

    /**
     * Read exactly one HTTP/1.1 chunked response off a persistent socket without over-reading into
     * the next response, so a second request can be issued on the SAME connection.
     */
    private String readOneChunkedResponse(InputStream in) throws IOException {
        StringBuilder response = new StringBuilder();
        String line;
        while (!(line = readRawLine(in)).isEmpty()) {
            response.append(line).append("\n");
        }
        response.append("\n");
        while (true) {
            String sizeLine = readRawLine(in);
            int semicolon = sizeLine.indexOf(';');
            String hex = (semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine).trim();
            int size = Integer.parseInt(hex, 16);
            if (size == 0) {
                readRawLine(in); // trailing CRLF terminating the final (empty) chunk
                break;
            }
            byte[] chunk = new byte[size];
            int offset = 0;
            while (offset < size) {
                int read = in.read(chunk, offset, size - offset);
                if (read == -1) {
                    throw new EOFException("connection closed mid-chunk - the stream was not kept alive");
                }
                offset += read;
            }
            response.append(new String(chunk, StandardCharsets.UTF_8));
            readRawLine(in); // trailing CRLF after the chunk data
        }
        return response.toString();
    }

    private String readRawLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                buffer.write(c);
            }
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    @Test
    public void shouldStreamGeminiResponseThroughNettyPipeline() throws Exception {
        // Gap 56: Gemini streaming physics over the wire — SSE framing with the
        // native candidates/parts delta shape (not just codec unit + golden JSONL).
        String completionText = "Hello from streaming Gemini";
        llmMock("/v1beta/models/gemini-1.5-pro:streamGenerateContent")
            .withProvider(GEMINI)
            .withModel("gemini-1.5-pro")
            .respondingWith(completion()
                .withText(completionText)
                .withStreaming(true))
            .applyTo(mockServerClient);

        String body = "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"Hi\"}]}]}";
        String rawResponse = sendPost("/v1beta/models/gemini-1.5-pro:streamGenerateContent?alt=sse", body);

        // (a) With ?alt=sse Gemini streams as Server-Sent Events (text/event-stream).
        assertThat(rawResponse, containsString("200"));
        assertThat(rawResponse, containsString("text/event-stream"));
        assertThat(rawResponse, containsString("\"candidates\""));

        // (b) The completion text arrives split across candidates[].content.parts[].text
        // deltas (subword-sized by default). Concatenating them must reconstruct the
        // completion text exactly.
        assertThat(reconstructFromJsonStringField(rawResponse, "text"), is(completionText));
    }

    @Test
    public void shouldStreamGeminiJsonArrayWithoutAltSseThroughNettyPipeline() throws Exception {
        // Without ?alt=sse the Gemini REST API streams one JSON array of response chunks.
        String completionText = "Hello from streaming Gemini";
        llmMock("/v1beta/models/gemini-1.5-pro:streamGenerateContent")
            .withProvider(GEMINI)
            .withModel("gemini-1.5-pro")
            .respondingWith(completion()
                .withText(completionText)
                .withStreaming(true))
            .applyTo(mockServerClient);

        String body = "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"Hi\"}]}]}";
        byte[] rawBytes = sendPostRaw("/v1beta/models/gemini-1.5-pro:streamGenerateContent", body);

        int headerEnd = indexOfSequence(rawBytes, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), 0);
        assertThat("response must have a header/body boundary", headerEnd, greaterThanOrEqualTo(0));
        String headerText = new String(rawBytes, 0, headerEnd, StandardCharsets.US_ASCII);
        assertThat(headerText, containsString("200"));
        assertThat(headerText.toLowerCase(), containsString("content-type: application/json"));

        byte[] payload = deChunkHttpBody(java.util.Arrays.copyOfRange(rawBytes, headerEnd + 4, rawBytes.length));
        String json = new String(payload, StandardCharsets.UTF_8);
        assertThat(json, startsWith("["));
        JsonNode chunks = new ObjectMapper().readTree(json);
        assertThat(chunks.isArray(), is(true));
        StringBuilder text = new StringBuilder();
        for (JsonNode chunk : chunks) {
            text.append(chunk.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(""));
        }
        assertThat(text.toString(), is(completionText));
        assertThat(chunks.get(chunks.size() - 1).path("candidates").path(0).path("finishReason").asText(), is("STOP"));
    }

    @Test
    public void shouldStreamOllamaResponseThroughNettyPipeline() throws Exception {
        // Gap 56: Ollama streaming physics over the wire — NDJSON framing
        // (application/x-ndjson), one JSON object per line, message.content deltas.
        String completionText = "Hello from streaming Ollama";
        llmMock("/api/chat/stream")
            .withProvider(OLLAMA)
            .withModel("llama3.1")
            .respondingWith(completion()
                .withText(completionText)
                .withStreaming(true))
            .applyTo(mockServerClient);

        String body = "{\"model\":\"llama3.1\",\"stream\":true,"
            + "\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}";
        String rawResponse = sendPost("/api/chat/stream", body);

        // (a) Ollama streams as newline-delimited JSON, not SSE.
        assertThat(rawResponse, containsString("200"));
        assertThat(rawResponse, containsString("application/x-ndjson"));
        assertThat(rawResponse, containsString("\"done\":true"));

        // (b) Each line carries a message.content delta; concatenating them reconstructs
        // the completion text exactly (the final done:true chunk contributes an empty content).
        assertThat(reconstructFromJsonStringField(rawResponse, "content"), is(completionText));
    }

    @Test
    public void shouldStreamBedrockResponseThroughNettyPipeline() throws Exception {
        // Gap 56: Bedrock streaming physics over the wire — AWS event-stream binary
        // framing (application/vnd.amazon.eventstream). Each chunk is a binary message
        // whose payload is {"bytes":"<base64(anthropicChunkJson)>"}.
        String completionText = "Hello from streaming Bedrock";
        llmMock("/model/anthropic.claude-sonnet-4-20250514-v1:0/invoke-with-response-stream")
            .withProvider(BEDROCK)
            .withModel("anthropic.claude-sonnet-4-20250514-v1:0")
            .respondingWith(completion()
                .withText(completionText)
                .withStreaming(true))
            .applyTo(mockServerClient);

        String body = "{\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}";
        byte[] rawBytes = sendPostRaw("/model/anthropic.claude-sonnet-4-20250514-v1:0/invoke-with-response-stream", body);

        int headerEnd = indexOfSequence(rawBytes, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), 0);
        assertThat("response must have a header/body boundary", headerEnd, greaterThanOrEqualTo(0));
        String headerText = new String(rawBytes, 0, headerEnd, StandardCharsets.US_ASCII);

        // (a) Bedrock streams as AWS event-stream binary framing.
        assertThat(headerText, containsString("200"));
        assertThat(headerText.toLowerCase(), containsString("application/vnd.amazon.eventstream"));

        // The body is HTTP/1.1 chunked; de-chunk it, then decode the AWS event-stream
        // binary messages (CRC32-validated by the decoder).
        byte[] body2 = new byte[rawBytes.length - (headerEnd + 4)];
        System.arraycopy(rawBytes, headerEnd + 4, body2, 0, body2.length);
        byte[] eventStream = deChunkHttpBody(body2);

        // (b) Each event-stream message wraps a base64 Anthropic chunk; the
        // content_block_delta text_delta fragments reconstruct the completion text exactly.
        StringBuilder reconstructed = new StringBuilder();
        for (BedrockEventStreamEncoder.DecodedMessage message : BedrockEventStreamEncoder.decode(eventStream)) {
            JsonNode wrapper = OBJECT_MAPPER.readTree(message.getPayloadAsString());
            JsonNode bytesNode = wrapper.get("bytes");
            if (bytesNode == null) {
                continue;
            }
            String chunkJson = new String(Base64.getDecoder().decode(bytesNode.asText()), StandardCharsets.UTF_8);
            JsonNode chunk = OBJECT_MAPPER.readTree(chunkJson);
            JsonNode delta = chunk.path("delta");
            if ("text_delta".equals(delta.path("type").asText())) {
                reconstructed.append(delta.path("text").asText());
            }
        }
        assertThat(reconstructed.toString(), is(completionText));
    }

    @Test
    public void shouldStreamBedrockConverseStreamEventsThroughNettyPipeline() throws Exception {
        // ConverseStream: AWS event-stream frames whose :event-type is the Converse event name and
        // whose payload is the event JSON itself (no {"bytes":"<base64>"} wrapper as in InvokeModel)
        String completionText = "Hello from Converse streaming";
        String path = "/model/amazon.nova-pro-v1:0/converse-stream";
        llmMock(path)
            .withProvider(BEDROCK)
            .withModel("amazon.nova-pro-v1:0")
            .respondingWith(completion()
                .withText(completionText)
                .withUsage(org.mockserver.model.Usage.usage().withInputTokens(9).withOutputTokens(4))
                .withStreaming(true))
            .applyTo(mockServerClient);

        byte[] rawBytes = sendPostRaw(path, "{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"Hi\"}]}]}");
        int headerEnd = indexOfSequence(rawBytes, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), 0);
        assertThat("response must have a header/body boundary", headerEnd, greaterThanOrEqualTo(0));
        String headerText = new String(rawBytes, 0, headerEnd, StandardCharsets.US_ASCII);
        assertThat(headerText, containsString("200"));
        assertThat(headerText.toLowerCase(), containsString("application/vnd.amazon.eventstream"));

        byte[] chunked = new byte[rawBytes.length - (headerEnd + 4)];
        System.arraycopy(rawBytes, headerEnd + 4, chunked, 0, chunked.length);
        java.util.List<BedrockEventStreamEncoder.DecodedMessage> messages = BedrockEventStreamEncoder.decode(deChunkHttpBody(chunked));

        java.util.List<String> eventTypes = new java.util.ArrayList<>();
        StringBuilder reconstructed = new StringBuilder();
        JsonNode metadata = null;
        for (BedrockEventStreamEncoder.DecodedMessage message : messages) {
            String eventType = message.getHeaders().get(":event-type");
            eventTypes.add(eventType);
            assertThat(message.getHeaders().get(":message-type"), is("event"));
            JsonNode payload = OBJECT_MAPPER.readTree(message.getPayloadAsString());
            assertThat("payload must be the raw event, not base64-wrapped", payload.has("bytes"), is(false));
            if ("contentBlockDelta".equals(eventType)) {
                reconstructed.append(payload.at("/delta/text").asText());
            } else if ("metadata".equals(eventType)) {
                metadata = payload;
            }
        }
        assertThat(eventTypes.get(0), is("messageStart"));
        assertThat(eventTypes.get(eventTypes.size() - 2), is("messageStop"));
        assertThat(eventTypes.get(eventTypes.size() - 1), is("metadata"));
        assertThat(reconstructed.toString(), is(completionText));
        assertThat(metadata.at("/usage/totalTokens").asInt(-1), is(13));
    }

    @Test
    public void shouldDeliverMalformedSseChaosChunkAsNamedConverseStreamEvent() throws Exception {
        // the malformed-SSE chaos chunk has no event name; an empty :event-type would be dropped
        // silently by AWS SDKs, so it must arrive as a contentBlockDelta carrying the broken JSON
        String path = "/model/amazon.nova-pro-v1:0/converse-stream";
        String expectation = "{"
            + "\"httpRequest\": { \"method\": \"POST\", \"path\": \"" + path + "\" },"
            + "\"httpLlmResponse\": {"
            + "  \"provider\": \"BEDROCK\","
            + "  \"model\": \"amazon.nova-pro-v1:0\","
            + "  \"completion\": { \"text\": \"Hi\", \"streaming\": true },"
            + "  \"chaos\": { \"malformedSse\": true }"
            + "}}";
        assertThat(sendRequest("PUT", "/mockserver/expectation", expectation), containsString("201"));

        byte[] rawBytes = sendPostRaw(path, "{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"Hi\"}]}]}");
        int headerEnd = indexOfSequence(rawBytes, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), 0);
        byte[] chunked = new byte[rawBytes.length - (headerEnd + 4)];
        System.arraycopy(rawBytes, headerEnd + 4, chunked, 0, chunked.length);
        java.util.List<BedrockEventStreamEncoder.DecodedMessage> messages = BedrockEventStreamEncoder.decode(deChunkHttpBody(chunked));

        BedrockEventStreamEncoder.DecodedMessage last = messages.get(messages.size() - 1);
        assertThat(last.getHeaders().get(":event-type"), is("contentBlockDelta"));
        assertThat(last.getPayloadAsString(), is("{\"malformed\":true"));
    }

    // ---- Helpers ----

    /**
     * Reconstructs streamed text by concatenating every {@code "<field>":"<value>"}
     * occurrence in a raw SSE / NDJSON response. Used for text-based streaming formats
     * whose per-token deltas carry the text under a single JSON string field
     * ({@code text} for Gemini parts, {@code content} for Ollama messages).
     */
    private String reconstructFromJsonStringField(String rawResponse, String field) {
        StringBuilder reconstructed = new StringBuilder();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\"" + field + "\":\"([^\"]*)\"")
            .matcher(rawResponse);
        while (matcher.find()) {
            reconstructed.append(matcher.group(1));
        }
        return reconstructed.toString();
    }

    /**
     * De-frames an HTTP/1.1 chunked message body into its raw payload bytes.
     * Each chunk is {@code <hex-length>CRLF<data>CRLF}, terminated by a zero-length chunk.
     */
    private static byte[] deChunkHttpBody(byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = 0;
        while (pos < body.length) {
            int lineEnd = indexOfSequence(body, "\r\n".getBytes(StandardCharsets.US_ASCII), pos);
            if (lineEnd < 0) {
                break;
            }
            String sizeLine = new String(body, pos, lineEnd - pos, StandardCharsets.US_ASCII).trim();
            int semi = sizeLine.indexOf(';');
            if (semi >= 0) {
                sizeLine = sizeLine.substring(0, semi).trim();
            }
            if (sizeLine.isEmpty()) {
                break;
            }
            int chunkSize = Integer.parseInt(sizeLine, 16);
            pos = lineEnd + 2;
            if (chunkSize == 0) {
                break;
            }
            if (pos + chunkSize > body.length) {
                out.write(body, pos, body.length - pos);
                break;
            }
            out.write(body, pos, chunkSize);
            pos += chunkSize + 2; // skip data and its trailing CRLF
        }
        return out.toByteArray();
    }

    /** Returns the index of the first occurrence of {@code needle} in {@code haystack} at or after {@code from}, or -1. */
    private static int indexOfSequence(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = Math.max(from, 0); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private byte[] sendPostRaw(String path, String body) throws Exception {
        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
            StringBuilder request = new StringBuilder();
            request.append("POST ").append(path).append(" HTTP/1.1\r\n");
            request.append("Host: localhost:").append(mockServerPort).append("\r\n");
            request.append("Content-Type: application/json\r\n");
            request.append("Connection: close\r\n");
            request.append("Content-Length: ").append(bodyBytes.length).append("\r\n\r\n");
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            if (bodyBytes.length > 0) {
                output.write(bodyBytes);
            }
            output.flush();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = socket.getInputStream().read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        }
    }

    private void assertToolUsePresent(JsonNode anthropicResponse, String toolName) {
        boolean found = false;
        for (JsonNode block : anthropicResponse.get("content")) {
            if ("tool_use".equals(block.get("type").asText())) {
                if (toolName.equals(block.get("name").asText())) {
                    found = true;
                }
            }
        }
        assertThat("Expected tool_use block for " + toolName, found, is(true));
    }

    private void assertTextBlockContains(JsonNode anthropicResponse, String expected) {
        boolean found = false;
        for (JsonNode block : anthropicResponse.get("content")) {
            if ("text".equals(block.get("type").asText())) {
                if (block.get("text").asText().contains(expected)) {
                    found = true;
                }
            }
        }
        assertThat("Expected text block containing '" + expected + "'", found, is(true));
    }

    private void assertOpenAiToolCallPresent(JsonNode chatCompletion, String funcName) {
        JsonNode toolCalls = chatCompletion.path("choices").get(0).path("message").path("tool_calls");
        assertThat("Expected tool_calls array", toolCalls.isArray(), is(true));
        boolean found = false;
        for (JsonNode tc : toolCalls) {
            if (funcName.equals(tc.path("function").get("name").asText())) {
                found = true;
            }
        }
        assertThat("Expected tool call for " + funcName, found, is(true));
    }

    private String sendPost(String path, String body) throws Exception {
        return sendRequest("POST", path, body);
    }

    private String sendRequest(String method, String path, String body) throws Exception {
        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
            StringBuilder request = new StringBuilder();
            request.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
            request.append("Host: localhost:").append(mockServerPort).append("\r\n");
            request.append("Content-Type: application/json\r\n");
            request.append("Connection: close\r\n");
            request.append("Content-Length: ").append(bodyBytes.length).append("\r\n\r\n");
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            if (bodyBytes.length > 0) {
                output.write(bodyBytes);
            }
            output.flush();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = socket.getInputStream().read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String extractJsonBody(String httpResponse) {
        int bodyStart = httpResponse.indexOf("\r\n\r\n");
        if (bodyStart < 0) {
            bodyStart = httpResponse.indexOf("\n\n");
            if (bodyStart < 0) {
                return httpResponse;
            }
            return httpResponse.substring(bodyStart + 2);
        }
        return httpResponse.substring(bodyStart + 4);
    }
}
