package org.mockserver.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.llm.codec.AnthropicCodec;
import org.mockserver.llm.codec.BedrockCodec;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;

/**
 * The chaos content-filter refusal body's usage follows the Messages API {@code Usage} type
 * (docs.anthropic.com/en/api/messages) and live responses recorded in pydantic-ai's cassettes: the
 * Claude API sends {@code inference_geo}, Bedrock InvokeModel does not.
 */
public class LlmRefusalPresetsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpRequest INVOKE = request().withPath("/model/anthropic.claude-3-5-sonnet-20241022-v2:0/invoke");

    @Test
    public void anthropicRefusalCompletionHasRefusalStopReason() {
        Completion completion = LlmRefusalPresets.anthropicRefusal();
        assertThat(completion.getStopReason(), is("refusal"));
        assertThat(completion.getText(), is(nullValue()));
    }

    @Test
    public void anthropicRefusalEncodesToRefusalStopReasonAndEmptyContent() throws Exception {
        HttpResponse encoded = new AnthropicCodec().encode(LlmRefusalPresets.anthropicRefusal(), "claude-sonnet-4-20250514");
        assertThat(encoded.getStatusCode(), is(200));
        JsonNode root = MAPPER.readTree(encoded.getBodyAsString());
        assertThat(root.get("stop_reason").asText(), is("refusal"));
        // Anthropic refusal carries no text content
        assertThat(root.get("content").size(), is(0));
    }

    @Test
    public void anthropicRefusalWithMessageCarriesText() throws Exception {
        HttpResponse encoded = new AnthropicCodec().encode(
            LlmRefusalPresets.anthropicRefusal("I can't help with that."), "claude-sonnet-4-20250514");
        JsonNode root = MAPPER.readTree(encoded.getBodyAsString());
        assertThat(root.get("stop_reason").asText(), is("refusal"));
        assertThat(root.get("content").get(0).get("type").asText(), is("text"));
        assertThat(root.get("content").get(0).get("text").asText(), is("I can't help with that."));
    }

    @Test
    public void anthropicContentFilterRefusalSendsTheFullUsage() throws Exception {
        JsonNode usage = MAPPER.readTree(LlmContentFilterBodies.blockFor(Provider.ANTHROPIC, null).getJsonBody()).path("usage");

        assertThat(fieldNames(usage), contains("input_tokens", "cache_creation_input_tokens", "cache_read_input_tokens",
            "cache_creation", "output_tokens", "service_tier", "inference_geo"));
        assertThat(usage, is(MAPPER.readTree("{\"input_tokens\":0,\"cache_creation_input_tokens\":0,\"cache_read_input_tokens\":0,"
            + "\"cache_creation\":{\"ephemeral_5m_input_tokens\":0,\"ephemeral_1h_input_tokens\":0},"
            + "\"output_tokens\":0,\"service_tier\":\"standard\",\"inference_geo\":\"global\"}")));
    }

    @Test
    public void bedrockInvokeModelContentFilterRefusalSendsTheFullUsageWithoutInferenceGeo() throws Exception {
        JsonNode usage = MAPPER.readTree(LlmContentFilterBodies.blockFor(Provider.BEDROCK, null, INVOKE).getJsonBody()).path("usage");

        assertThat(fieldNames(usage), contains("input_tokens", "cache_creation_input_tokens", "cache_read_input_tokens",
            "cache_creation", "output_tokens", "service_tier"));
        assertThat(usage, is(MAPPER.readTree("{\"input_tokens\":0,\"cache_creation_input_tokens\":0,\"cache_read_input_tokens\":0,"
            + "\"cache_creation\":{\"ephemeral_5m_input_tokens\":0,\"ephemeral_1h_input_tokens\":0},"
            + "\"output_tokens\":0,\"service_tier\":\"standard\"}")));
    }

    @Test
    public void contentFilterRefusalUsageMatchesWhatTheCodecSendsForAnEmptyRefusal() throws Exception {
        JsonNode anthropicCodecUsage = MAPPER.readTree(new AnthropicCodec().encode(LlmRefusalPresets.anthropicRefusal(), "m").getBodyAsString()).path("usage");
        JsonNode anthropicBlockUsage = MAPPER.readTree(LlmContentFilterBodies.blockFor(Provider.ANTHROPIC, null).getJsonBody()).path("usage");
        assertThat("anthropic usage, in field order", anthropicBlockUsage.toString(), is(anthropicCodecUsage.toString()));

        JsonNode bedrockCodecUsage = MAPPER.readTree(new BedrockCodec().encode(LlmRefusalPresets.anthropicRefusal(), "m", INVOKE).getBodyAsString()).path("usage");
        JsonNode bedrockBlockUsage = MAPPER.readTree(LlmContentFilterBodies.blockFor(Provider.BEDROCK, null, INVOKE).getJsonBody()).path("usage");
        assertThat("bedrock usage, in field order", bedrockBlockUsage.toString(), is(bedrockCodecUsage.toString()));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
