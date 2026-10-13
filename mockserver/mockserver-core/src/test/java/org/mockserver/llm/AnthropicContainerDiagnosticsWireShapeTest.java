package org.mockserver.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.llm.codec.AnthropicCodec;
import org.mockserver.llm.codec.BedrockCodec;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Provider;
import org.mockserver.model.SseEvent;
import org.mockserver.model.ToolUse;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpRequest.request;

/**
 * The Claude API sends {@code "container": null} on every message (whole, in {@code message_start} and in
 * the {@code message_delta} delta) unless a container tool ran, and {@code "diagnostics": null} on every
 * message unless the request opted in to cache diagnostics. Bedrock InvokeModel sends the same
 * {@code container} but no {@code diagnostics}, which it does not support. Sources: the Messages API
 * reference (Message.container, Message.diagnostics), the API release notes of 2026-09-23, and responses
 * recorded on 2026-09-28/29 in pydantic-ai's cassettes (Claude API and Bedrock invoke-with-response-stream).
 */
public class AnthropicContainerDiagnosticsWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpRequest INVOKE = request().withPath("/model/anthropic.claude-3-5-sonnet-20241022-v2:0/invoke");
    private static final HttpRequest INVOKE_STREAM = request().withPath("/model/anthropic.claude-3-5-sonnet-20241022-v2:0/invoke-with-response-stream");

    @Test
    public void anthropicMessageCarriesNullContainerAndDiagnostics() throws Exception {
        for (Completion completion : completions()) {
            JsonNode body = MAPPER.readTree(new AnthropicCodec().encode(completion, "m").getBodyAsString());

            String stopReason = body.path("stop_reason").asText();
            assertPresentAndNull(stopReason, body, "container");
            assertPresentAndNull(stopReason, body, "diagnostics");
        }
    }

    @Test
    public void anthropicStreamCarriesNullContainerAndDiagnosticsOnMessageStartAndContainerOnTheDelta() throws Exception {
        for (Completion completion : completions()) {
            List<SseEvent> events = new AnthropicCodec().encodeStreaming(completion.withStreaming(true), "m", null);

            JsonNode message = event("message_start", events).path("message");
            assertPresentAndNull("message_start", message, "container");
            assertPresentAndNull("message_start", message, "diagnostics");
            JsonNode messageDelta = event("message_delta", events);
            String stopReason = messageDelta.at("/delta/stop_reason").asText();
            assertPresentAndNull(stopReason, messageDelta.path("delta"), "container");
            assertThat(stopReason + " delta diagnostics", messageDelta.path("delta").has("diagnostics"), is(false));
            assertThat(stopReason + " top-level container", messageDelta.has("container"), is(false));
            assertThat(stopReason + " top-level diagnostics", messageDelta.has("diagnostics"), is(false));
        }
    }

    @Test
    public void bedrockInvokeModelMessageCarriesNullContainerButNoDiagnostics() throws Exception {
        for (Completion completion : completions()) {
            JsonNode body = MAPPER.readTree(new BedrockCodec().encode(completion, "m", INVOKE).getBodyAsString());

            String stopReason = body.path("stop_reason").asText();
            assertPresentAndNull("bedrock " + stopReason, body, "container");
            assertThat("bedrock " + stopReason + " diagnostics", body.has("diagnostics"), is(false));
        }
    }

    @Test
    public void bedrockInvokeModelStreamCarriesNullContainerButNoDiagnostics() throws Exception {
        List<SseEvent> events = new BedrockCodec().encodeStreaming(completion().withText("hi").withStreaming(true), "m", null, INVOKE_STREAM);

        JsonNode message = event("message_start", events).path("message");
        assertPresentAndNull("bedrock message_start", message, "container");
        assertThat("bedrock message_start diagnostics", message.has("diagnostics"), is(false));
        assertPresentAndNull("bedrock message_delta", event("message_delta", events).path("delta"), "container");
    }

    @Test
    public void contentFilterBlockBodyMatchesEachProvider() throws Exception {
        JsonNode anthropic = MAPPER.readTree(LlmContentFilterBodies.blockFor(Provider.ANTHROPIC, null).getJsonBody());
        assertPresentAndNull("anthropic block", anthropic, "container");
        assertPresentAndNull("anthropic block", anthropic, "diagnostics");

        JsonNode bedrock = MAPPER.readTree(LlmContentFilterBodies.blockFor(Provider.BEDROCK, null, INVOKE).getJsonBody());
        assertPresentAndNull("bedrock block", bedrock, "container");
        assertThat("bedrock block diagnostics", bedrock.has("diagnostics"), is(false));
    }

    private static List<Completion> completions() {
        return List.of(
            completion().withText("hi"),
            completion().withText("hi").withReasoningText("thinking").withStopReason("max_tokens"),
            completion().withToolCalls(List.of(new ToolUse().withName("get_weather").withArguments("{}"))),
            LlmRefusalPresets.anthropicRefusal("I can't help with that.")
        );
    }

    private static JsonNode event(String name, List<SseEvent> events) throws Exception {
        for (SseEvent event : events) {
            if (name.equals(event.getEvent())) {
                return MAPPER.readTree(event.getData());
            }
        }
        throw new AssertionError("no " + name + " event in " + events);
    }

    private static void assertPresentAndNull(String context, JsonNode node, String field) {
        assertThat(context + " has " + field, node.has(field), is(true));
        assertThat(context + " " + field + " is null", node.get(field).isNull(), is(true));
    }
}
