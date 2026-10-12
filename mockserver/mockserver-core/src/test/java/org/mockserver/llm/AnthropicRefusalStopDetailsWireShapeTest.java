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

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpRequest.request;

/**
 * Every Anthropic Messages response with {@code stop_reason} {@code refusal} carries a
 * {@code stop_details} object ({@code type} {@code refusal}, nullable {@code category} and
 * {@code explanation}); a stream carries it in the {@code message_delta} event's {@code delta}.
 * Sources: https://platform.claude.com/docs/en/build-with-claude/refusals-and-fallback and
 * anthropic-sdk-python {@code types/refusal_stop_details.py}, {@code types/raw_message_delta_event.py}.
 */
public class AnthropicRefusalStopDetailsWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpRequest INVOKE = request().withPath("/model/anthropic.claude-3-5-sonnet-20241022-v2:0/invoke");

    @Test
    public void refusalPresetCarriesRefusalStopDetails() throws Exception {
        for (Completion refusal : List.of(LlmRefusalPresets.anthropicRefusal(), LlmRefusalPresets.anthropicRefusal("I can't help with that."))) {
            JsonNode body = MAPPER.readTree(new AnthropicCodec().encode(refusal, "claude-sonnet-4-20250514").getBodyAsString());

            assertThat(body.path("stop_reason").asText(), is("refusal"));
            assertRefusalStopDetails(body.path("stop_details"));
        }
    }

    @Test
    public void otherProvidersFilterStopReasonsMapToARefusalWithStopDetails() throws Exception {
        for (String filtered : List.of("content_filter", "content_filtered", "guardrail_intervened", "SAFETY")) {
            JsonNode body = MAPPER.readTree(new AnthropicCodec().encode(completion().withStopReason(filtered), "m").getBodyAsString());

            assertThat(filtered, body.path("stop_reason").asText(), is("refusal"));
            assertRefusalStopDetails(body.path("stop_details"));
        }
    }

    @Test
    public void bedrockInvokeModelRefusalCarriesStopDetails() throws Exception {
        JsonNode body = MAPPER.readTree(new BedrockCodec().encode(LlmRefusalPresets.anthropicRefusal(), "m", INVOKE).getBodyAsString());

        assertThat(body.path("stop_reason").asText(), is("refusal"));
        assertRefusalStopDetails(body.path("stop_details"));
    }

    @Test
    public void nonRefusalMessageCarriesNoStopDetailsObject() throws Exception {
        JsonNode body = MAPPER.readTree(new AnthropicCodec().encode(completion().withText("hi"), "m").getBodyAsString());

        assertThat(body.path("stop_reason").asText(), is("end_turn"));
        assertThat(body.path("stop_details").isObject(), is(false));
    }

    @Test
    public void streamedRefusalCarriesStopDetailsOnTheMessageDeltaDelta() throws Exception {
        List<SseEvent> events = new AnthropicCodec().encodeStreaming(
            LlmRefusalPresets.anthropicRefusal("I can't help with that.").withStreaming(true), "m", null);

        JsonNode messageDelta = messageDelta(events);
        assertThat(messageDelta.at("/delta/stop_reason").asText(), is("refusal"));
        assertRefusalStopDetails(messageDelta.at("/delta/stop_details"));
        assertThat(messageDelta.has("stop_details"), is(false));
    }

    @Test
    public void streamedNonRefusalCarriesNoStopDetailsObject() throws Exception {
        JsonNode messageDelta = messageDelta(new AnthropicCodec().encodeStreaming(completion().withText("hi").withStreaming(true), "m", null));

        assertThat(messageDelta.at("/delta/stop_reason").asText(), is("end_turn"));
        assertThat(messageDelta.at("/delta/stop_details").isObject(), is(false));
    }

    @Test
    public void chaosContentFilterBlockBodyCarriesStopDetails() throws Exception {
        for (Provider provider : List.of(Provider.ANTHROPIC, Provider.BEDROCK)) {
            LlmContentFilterBodies.Block block = LlmContentFilterBodies.blockFor(provider, null, provider == Provider.BEDROCK ? INVOKE : null);

            assertThat(provider + " status", block.getStatusCode(), is(200));
            JsonNode body = MAPPER.readTree(block.getJsonBody());
            assertThat(provider + " stop_reason", body.path("stop_reason").asText(), is("refusal"));
            assertRefusalStopDetails(body.path("stop_details"));
        }
    }

    @Test
    public void streamedChaosContentFilterBlockCarriesStopDetails() throws Exception {
        assertStreamedBlockCarriesStopDetails(Provider.ANTHROPIC, new AnthropicCodec(), request().withPath("/v1/messages"));
        assertStreamedBlockCarriesStopDetails(Provider.BEDROCK, new BedrockCodec(),
            request().withPath("/model/anthropic.claude-3-5-sonnet-20241022-v2:0/invoke-with-response-stream"));
    }

    private static void assertStreamedBlockCarriesStopDetails(Provider provider, ProviderCodec codec, HttpRequest request) throws Exception {
        LlmContentFilterBodies.Block block = LlmContentFilterBodies.blockFor(provider, null, request);
        List<SseEvent> events = LlmContentFilterBodies.streamedBlockOrNull(provider, block, codec, "m", null, request);

        assertThat(provider + " events", events.stream().map(SseEvent::getEvent).toList(), contains("message_start", "message_delta", "message_stop"));
        JsonNode messageDelta = messageDelta(events);
        assertThat(provider + " stop_reason", messageDelta.at("/delta/stop_reason").asText(), is("refusal"));
        assertRefusalStopDetails(messageDelta.at("/delta/stop_details"));
    }

    private static JsonNode messageDelta(List<SseEvent> events) throws Exception {
        for (SseEvent event : events) {
            if ("message_delta".equals(event.getEvent())) {
                return MAPPER.readTree(event.getData());
            }
        }
        throw new AssertionError("no message_delta event in " + events);
    }

    private static void assertRefusalStopDetails(JsonNode stopDetails) {
        assertThat("stop_details is an object: " + stopDetails, stopDetails.isObject(), is(true));
        assertThat(stopDetails.path("type").asText(), is("refusal"));
        assertThat("category present and null", stopDetails.has("category") && stopDetails.get("category").isNull(), is(true));
        assertThat("explanation present and null", stopDetails.has("explanation") && stopDetails.get("explanation").isNull(), is(true));
        assertThat(stopDetails.size(), is(3));
    }
}
