package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.matchers.LlmConversationMatcher;
import org.mockserver.model.Completion;
import org.mockserver.model.Provider;
import org.mockserver.model.SseEvent;
import org.mockserver.model.Usage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.llm.codec.CodecTestUtil.escapeForJson;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.ToolUse.toolUse;

/**
 * Pins the OpenAI Responses wire shape against the official API reference and the
 * openai-python types (Response, ResponseFunctionToolCall, ResponseCompletedEvent and the
 * streaming event models, each of which carries a sequence_number).
 */
public class OpenAiResponsesCodecWireShapeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ARGS = "{\"city\":\"Paris\",\"units\":\"celsius\"}";
    private final OpenAiResponsesCodec codec = new OpenAiResponsesCodec();

    private static Completion fullCompletion() {
        return completion()
            .withReasoningText("Thinking about the weather.")
            .withText("Checking the weather.")
            .withToolCall(toolUse("get_weather").withArguments(ARGS))
            .withToolCall(toolUse("get_time").withArguments("{\"tz\":\"CET\"}"))
            .withUsage(Usage.usage().withInputTokens(12).withOutputTokens(7));
    }

    // --- non-streaming body ---

    @Test
    public void bodyCarriesTheResponseFieldsTheSdkRequires() throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(codec.encode(completion().withText("hi"), "gpt-4o").getBodyAsString());

        assertThat(root.path("object").asText(), is("response"));
        assertThat(root.path("status").asText(), is("completed"));
        assertThat(root.path("completed_at").isNumber(), is(true));
        assertThat(root.path("parallel_tool_calls").isBoolean(), is(true));
        assertThat(root.path("tool_choice").asText(), is("auto"));
        assertThat(root.path("tools").isArray(), is(true));
        assertThat(root.path("metadata").isObject(), is(true));
        assertThat(root.get("error").isNull(), is(true));
        assertThat(root.get("incomplete_details").isNull(), is(true));
        JsonNode usage = root.path("usage");
        assertThat(usage.path("input_tokens_details").path("cached_tokens").isInt(), is(true));
        assertThat(usage.path("output_tokens_details").path("reasoning_tokens").isInt(), is(true));
        JsonNode message = root.path("output").path(0);
        assertThat(message.path("status").asText(), is("completed"));
        assertThat(message.path("content").path(0).path("annotations").isArray(), is(true));
    }

    @Test
    public void toolChoiceEchoesTheConfiguredDirective() throws Exception {
        JsonNode required = OBJECT_MAPPER.readTree(codec.encode(
            completion().withToolChoice("required").withToolCall(toolUse("get_weather")), "gpt-4o").getBodyAsString());
        assertThat(required.path("tool_choice").asText(), is("required"));

        JsonNode named = OBJECT_MAPPER.readTree(codec.encode(
            completion().withToolChoice("get_weather").withToolCall(toolUse("get_weather")), "gpt-4o").getBodyAsString());
        assertThat(named.path("tool_choice").path("type").asText(), is("function"));
        assertThat(named.path("tool_choice").path("name").asText(), is("get_weather"));
    }

    @Test
    public void everyFunctionCallHasItsOwnCallIdDistinctFromItsItemId() throws Exception {
        JsonNode output = OBJECT_MAPPER.readTree(codec.encode(fullCompletion(), "gpt-4o").getBodyAsString()).path("output");
        List<String> ids = new ArrayList<>();
        for (JsonNode item : output) {
            if ("function_call".equals(item.path("type").asText())) {
                assertThat(item.path("id").asText(), startsWith("fc_"));
                assertThat(item.path("call_id").asText(), startsWith("call_"));
                ids.add(item.path("id").asText());
                ids.add(item.path("call_id").asText());
            }
        }
        assertThat(ids, hasSize(4));
        assertThat(ids.stream().distinct().count(), is(4L));
    }

    @Test
    public void responseEchoesTheRequestFieldsTheRealApiEchoes() throws Exception {
        String requestBody = "{\"model\":\"gpt-4o\",\"instructions\":\"Be terse.\",\"parallel_tool_calls\":false,"
            + "\"previous_response_id\":\"resp_prev\",\"metadata\":{\"run\":\"42\"},\"tool_choice\":\"required\","
            + "\"tools\":[{\"type\":\"function\",\"name\":\"get_weather\",\"parameters\":{\"type\":\"object\"}}],"
            + "\"input\":\"Weather?\"}";

        JsonNode body = OBJECT_MAPPER.readTree(codec.encode(completion().withText("hi"), "gpt-4o",
            request().withBody(requestBody)).getBodyAsString());
        List<SseEvent> events = codec.encodeStreaming(completion().withText("hi"), "gpt-4o", null,
            request().withBody(requestBody));
        List<JsonNode> responses = new ArrayList<>(List.of(body));
        for (String name : List.of("response.created", "response.in_progress", "response.completed")) {
            List<JsonNode> named = dataOf(events, name);
            assertThat(name, named, hasSize(1));
            responses.add(named.get(0).path("response"));
        }

        for (JsonNode response : responses) {
            assertThat(response.path("instructions").asText(), is("Be terse."));
            assertThat(response.path("parallel_tool_calls").asBoolean(true), is(false));
            assertThat(response.path("previous_response_id").asText(), is("resp_prev"));
            assertThat(response.path("metadata").path("run").asText(), is("42"));
            assertThat(response.path("tool_choice").asText(), is("required"));
            assertThat(response.path("tools").path(0).path("name").asText(), is("get_weather"));
        }
    }

    @Test
    public void everyResponseObjectCarriesTheSpecRequiredNullableFields() throws Exception {
        for (JsonNode response : allResponseObjects(null)) {
            assertNullField(response, "access_programs");
            assertNullField(response, "temperature");
            assertNullField(response, "top_p");
        }
        for (JsonNode response : allResponseObjects("{\"temperature\":\"0.2\",\"top_p\":true,\"input\":\"x\"}")) {
            assertNullField(response, "temperature");
            assertNullField(response, "top_p");
        }
    }

    @Test
    public void everyResponseObjectEchoesTheRequestsSamplingSettings() throws Exception {
        for (JsonNode response : allResponseObjects("{\"temperature\":0.2,\"top_p\":0.9,\"input\":\"x\"}")) {
            assertThat(response.path("temperature").isNumber(), is(true));
            assertThat(response.path("temperature").asDouble(), is(0.2));
            assertThat(response.path("top_p").isNumber(), is(true));
            assertThat(response.path("top_p").asDouble(), is(0.9));
            assertNullField(response, "access_programs");
        }
    }

    @Test
    public void configuredToolChoiceWinsOverTheRequestsAndAnUnparseableRequestIsIgnored() throws Exception {
        JsonNode configured = OBJECT_MAPPER.readTree(codec.encode(completion().withToolChoice("none").withText("hi"), "gpt-4o",
            request().withBody("{\"tool_choice\":\"required\",\"input\":\"x\"}")).getBodyAsString());
        assertThat(configured.path("tool_choice").asText(), is("none"));

        JsonNode unparseable = OBJECT_MAPPER.readTree(codec.encode(completion().withText("hi"), "gpt-4o",
            request().withBody("not json")).getBodyAsString());
        assertThat(unparseable.path("tool_choice").asText(), is("auto"));
        assertThat(unparseable.path("tools").size(), is(0));
        assertThat(unparseable.get("instructions").isNull(), is(true));
        assertThat(unparseable.get("previous_response_id").isNull(), is(true));
    }

    @Test
    public void aConfiguredToolUseIdBecomesTheCallId() throws Exception {
        Completion configured = completion().withToolCall(toolUse("get_weather").withId("call_abc123").withArguments(ARGS));

        JsonNode item = OBJECT_MAPPER.readTree(codec.encode(configured, "gpt-4o").getBodyAsString()).path("output").path(0);
        assertThat(item.path("call_id").asText(), is("call_abc123"));
        assertThat(item.path("id").asText(), startsWith("fc_"));

        List<SseEvent> events = codec.encodeStreaming(configured, "gpt-4o", null);
        assertThat(dataOf(events, "response.output_item.added").get(0).path("item").path("call_id").asText(), is("call_abc123"));
        assertThat(dataOf(events, "response.output_item.done").get(0).path("item").path("call_id").asText(), is("call_abc123"));
        assertThat(dataOf(events, "response.completed").get(0).path("response").path("output").path(0).path("call_id").asText(),
            is("call_abc123"));
    }

    // --- streaming ---

    @Test
    public void everyStreamedEventHasAContiguousSequenceNumberFromZero() throws Exception {
        List<SseEvent> events = codec.encodeStreaming(fullCompletion(), "gpt-4o", null);
        for (int i = 0; i < events.size(); i++) {
            JsonNode data = OBJECT_MAPPER.readTree(events.get(i).getData());
            assertThat("event " + i + " type", data.path("type").asText(), is(events.get(i).getEvent()));
            assertThat("event " + i + " sequence_number", data.path("sequence_number").isInt(), is(true));
            assertThat("event " + i + " sequence_number", data.path("sequence_number").asInt(), is(i));
        }
    }

    @Test
    public void createdAndInProgressCarryTheFullResponseObject() throws Exception {
        List<SseEvent> events = codec.encodeStreaming(fullCompletion(), "gpt-4o", null);
        for (String name : new String[]{"response.created", "response.in_progress"}) {
            JsonNode response = dataOf(events, name).get(0).path("response");
            assertThat(name, response.path("status").asText(), is("in_progress"));
            assertThat(name, response.path("model").asText(), is("gpt-4o"));
            assertThat(name, response.path("output").isArray(), is(true));
            assertThat(name, response.path("output").size(), is(0));
            assertThat(name, response.path("parallel_tool_calls").isBoolean(), is(true));
            assertThat(name, response.path("tools").isArray(), is(true));
            assertThat(name, response.has("usage"), is(true));
        }
    }

    @Test
    public void functionCallArgumentsStreamAsDeltasThenDone() throws Exception {
        List<SseEvent> events = codec.encodeStreaming(
            completion().withToolCall(toolUse("get_weather").withArguments(ARGS)), "gpt-4o", null);

        JsonNode added = dataOf(events, "response.output_item.added").get(0);
        String itemId = added.path("item").path("id").asText();
        assertThat(added.path("item").path("call_id").asText(), startsWith("call_"));
        assertThat(added.path("item").path("arguments").asText(), is(""));

        List<JsonNode> deltas = dataOf(events, "response.function_call_arguments.delta");
        assertThat(deltas.size(), greaterThan(1));
        StringBuilder reassembled = new StringBuilder();
        for (JsonNode delta : deltas) {
            assertThat(delta.path("item_id").asText(), is(itemId));
            assertThat(delta.path("output_index").asInt(), is(0));
            reassembled.append(delta.path("delta").asText());
        }
        assertThat(reassembled.toString(), is(ARGS));

        JsonNode done = dataOf(events, "response.function_call_arguments.done").get(0);
        assertThat(done.path("item_id").asText(), is(itemId));
        assertThat(done.path("arguments").asText(), is(ARGS));
        assertThat(eventNames(events).indexOf("response.function_call_arguments.done"),
            lessThan(eventNames(events).indexOf("response.output_item.done")));
    }

    @Test
    public void textStreamsInsideContentPartEvents() throws Exception {
        List<String> names = eventNames(codec.encodeStreaming(completion().withText("Hello world"), "gpt-4o", null));
        assertThat(names.get(0), is("response.created"));
        assertThat(names.get(1), is("response.in_progress"));
        assertThat(names.get(2), is("response.output_item.added"));
        assertThat(names.get(3), is("response.content_part.added"));
        assertThat(names.get(4), is("response.output_text.delta"));
        int textDone = names.indexOf("response.output_text.done");
        assertThat(names.get(textDone + 1), is("response.content_part.done"));
        assertThat(names.get(textDone + 2), is("response.output_item.done"));
        assertThat(names.get(textDone + 3), is("response.completed"));
        assertThat(names, hasSize(textDone + 4));
    }

    @Test
    public void completedEventCarriesTheFullOutputMatchingTheStreamedItems() throws Exception {
        List<SseEvent> events = codec.encodeStreaming(fullCompletion(), "gpt-4o", null);
        JsonNode response = dataOf(events, "response.completed").get(0).path("response");
        assertThat(response.path("status").asText(), is("completed"));
        assertThat(response.path("usage").path("total_tokens").asInt(), is(19));

        List<JsonNode> doneItems = new ArrayList<>();
        for (JsonNode done : dataOf(events, "response.output_item.done")) {
            doneItems.add(done.path("item"));
        }
        JsonNode output = response.path("output");
        assertThat(output.size(), is(4));
        for (int i = 0; i < output.size(); i++) {
            assertThat("output[" + i + "]", output.get(i), is(doneItems.get(i)));
        }
        assertThat(output.get(0).path("type").asText(), is("reasoning"));
        assertThat(output.get(0).path("summary").path(0).path("text").asText(), is("Thinking about the weather."));
        assertThat(output.get(1).path("content").path(0).path("text").asText(), is("Checking the weather."));
        assertThat(output.get(2).path("call_id").asText(), startsWith("call_"));
        assertThat(output.get(2).path("arguments").asText(), is(ARGS));
        assertThat(output.get(3).path("name").asText(), is("get_time"));
    }

    @Test
    public void streamedOutputHasTheSameShapeAsTheNonStreamingBody() throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree(codec.encode(fullCompletion(), "gpt-4o").getBodyAsString());
        JsonNode completed = dataOf(codec.encodeStreaming(fullCompletion(), "gpt-4o", null), "response.completed")
            .get(0).path("response");
        assertThat(withoutIds(completed), is(withoutIds(body)));
    }

    // --- decode ---

    @Test
    public void toolResultForAnEchoedCallMatchesContainsToolResultFor() throws Exception {
        JsonNode call = OBJECT_MAPPER.readTree(codec.encode(
            completion().withToolCall(toolUse("get_weather").withArguments(ARGS)), "gpt-4o").getBodyAsString()).path("output").path(0);
        LlmConversationMatcher matcher = new LlmConversationMatcher()
            .withProvider(Provider.OPENAI_RESPONSES)
            .withContainsToolResultFor("get_weather");

        assertThat(matcher.matches(request().withBody(turnTwo(call, call.path("call_id").asText()))), is(true));
        // the fc_ item id is not the correlation key
        assertThat(matcher.matches(request().withBody(turnTwo(call, call.path("id").asText()))), is(false));
    }

    @Test
    public void developerRoleDecodesAsSystem() {
        ParsedConversation parsed = codec.decode(request().withBody(
            "{\"input\":[{\"role\":\"developer\",\"content\":\"Answer in French.\"},{\"role\":\"user\",\"content\":\"Hi\"}]}"));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.SYSTEM));
        assertThat(parsed.getMessages().get(0).getTextContent(), is("Answer in French."));
    }

    @Test
    public void instructionsDecodeAsALeadingSystemMessage() {
        ParsedConversation parsed = codec.decode(request().withBody(
            "{\"instructions\":\"You are terse.\",\"input\":\"Hi\"}"));
        assertThat(parsed.getMessages(), hasSize(2));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.SYSTEM));
        assertThat(parsed.getMessages().get(0).getTextContent(), is("You are terse."));
        assertThat(parsed.getMessages().get(1).getTextContent(), is("Hi"));
    }

    // --- helpers ---

    private static String turnTwo(JsonNode echoedCall, String outputCallId) {
        return "{\"input\":[{\"role\":\"user\",\"content\":\"Weather?\"}," + echoedCall.toString() + ","
            + "{\"type\":\"function_call_output\",\"call_id\":\"" + escapeForJson(outputCallId) + "\",\"output\":\"18C\"}]}";
    }

    private static JsonNode withoutIds(JsonNode node) {
        JsonNode copy = node.deepCopy();
        strip(copy);
        return copy;
    }

    private static void strip(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            for (String field : new String[]{"id", "call_id", "created_at", "completed_at"}) {
                if (object.has(field)) {
                    object.put(field, "<x>");
                }
            }
            Iterator<JsonNode> children = object.elements();
            while (children.hasNext()) {
                strip(children.next());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                strip(child);
            }
        }
    }

    /** The non-streaming body and the response of response.created, .in_progress and .completed. */
    private List<JsonNode> allResponseObjects(String requestBody) throws Exception {
        List<JsonNode> responses = new ArrayList<>();
        List<SseEvent> events;
        if (requestBody == null) {
            responses.add(OBJECT_MAPPER.readTree(codec.encode(completion().withText("hi"), "gpt-4o").getBodyAsString()));
            events = codec.encodeStreaming(completion().withText("hi"), "gpt-4o", null);
        } else {
            responses.add(OBJECT_MAPPER.readTree(codec.encode(completion().withText("hi"), "gpt-4o",
                request().withBody(requestBody)).getBodyAsString()));
            events = codec.encodeStreaming(completion().withText("hi"), "gpt-4o", null, request().withBody(requestBody));
        }
        for (String name : List.of("response.created", "response.in_progress", "response.completed")) {
            List<JsonNode> named = dataOf(events, name);
            assertThat(name, named, hasSize(1));
            responses.add(named.get(0).path("response"));
        }
        return responses;
    }

    private static void assertNullField(JsonNode response, String field) {
        assertThat(field + " present", response.has(field), is(true));
        assertThat(field + " null", response.get(field).isNull(), is(true));
    }

    private static List<String> eventNames(List<SseEvent> events) {
        List<String> names = new ArrayList<>();
        for (SseEvent event : events) {
            names.add(event.getEvent());
        }
        return names;
    }

    private static List<JsonNode> dataOf(List<SseEvent> events, String name) throws Exception {
        List<JsonNode> data = new ArrayList<>();
        for (SseEvent event : events) {
            if (name.equals(event.getEvent())) {
                data.add(OBJECT_MAPPER.readTree(event.getData()));
            }
        }
        return data;
    }
}
