package org.mockserver.mock.action.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpLlmResponse;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.LlmChaosProfile;
import org.mockserver.model.Provider;
import org.mockserver.model.SseEvent;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpRequest.request;

/**
 * A chaos content-filter block on a streaming request: providers that answer a block with HTTP 200
 * (Anthropic, Bedrock, Gemini) stream it ending with their filtered stop reason, and providers that
 * reject the prompt with HTTP 400 before streaming (OpenAI family, Azure) keep the JSON error.
 */
public class HttpLlmResponseActionHandlerStreamedContentFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpLlmResponseActionHandler handler = new HttpLlmResponseActionHandler(new MockServerLogger());

    private HttpLlmResponseActionHandler.ChaosOutcome blocked(Provider provider, boolean streaming, HttpRequest request) {
        return handler.chaosOutcome(
            HttpLlmResponse.llmResponse()
                .withProvider(provider)
                .withModel("test-model")
                .withCompletion(completion().withText("this text must not be streamed").withStreaming(streaming))
                .withChaos(LlmChaosProfile.llmChaosProfile().withContentFilterBlockProbability(1.0)),
            request);
    }

    @Test
    public void anthropicStreamEndsWithRefusalStopReasonAndNoText() throws Exception {
        List<SseEvent> events = blocked(Provider.ANTHROPIC, true, request().withPath("/v1/messages")).getStreamedBlock();

        assertThat(eventNames(events), contains("message_start", "message_delta", "message_stop"));
        JsonNode messageDelta = MAPPER.readTree(events.get(1).getData());
        assertThat(messageDelta.at("/delta/stop_reason").asText(), is("refusal"));
        assertThat(MAPPER.readTree(events.get(0).getData()).at("/message/content").size(), is(0));
    }

    @Test
    public void bedrockInvokeModelStreamEndsWithAnthropicRefusal() throws Exception {
        List<SseEvent> events = blocked(Provider.BEDROCK, true,
            request().withPath("/model/anthropic.claude-3-5-sonnet-20241022-v2:0/invoke-with-response-stream")).getStreamedBlock();

        assertThat(eventNames(events), contains("message_start", "message_delta", "message_stop"));
        assertThat(MAPPER.readTree(events.get(1).getData()).at("/delta/stop_reason").asText(), is("refusal"));
    }

    @Test
    public void bedrockConverseStreamEndsWithContentFilteredMessageStop() throws Exception {
        List<SseEvent> events = blocked(Provider.BEDROCK, true,
            request().withPath("/model/anthropic.claude-3-5-sonnet-20241022-v2:0/converse-stream")).getStreamedBlock();

        assertThat(eventNames(events), contains("messageStart", "messageStop", "metadata"));
        assertThat(MAPPER.readTree(events.get(1).getData()).path("stopReason").asText(), is("content_filtered"));
    }

    @Test
    public void geminiStreamIsOneSafetyChunk() throws Exception {
        List<SseEvent> events = blocked(Provider.GEMINI, true,
            request().withPath("/v1beta/models/gemini-2.0-flash:streamGenerateContent").withQueryStringParameter("alt", "sse")).getStreamedBlock();

        assertThat(events, hasSize(1));
        JsonNode chunk = MAPPER.readTree(events.get(0).getData());
        assertThat(chunk.at("/candidates/0/finishReason").asText(), is("SAFETY"));
        assertThat(chunk.at("/promptFeedback/blockReason").asText(), is("SAFETY"));
    }

    @Test
    public void streamedBlockKeepsTheJsonBlockForAServerThatCannotStream() throws Exception {
        HttpLlmResponseActionHandler.ChaosOutcome outcome = blocked(Provider.ANTHROPIC, true, request().withPath("/v1/messages"));

        assertThat(outcome.getErrorResponse().getStatusCode(), is(200));
        assertThat(MAPPER.readTree(outcome.getErrorResponse().getBodyAsString()).path("stop_reason").asText(), is("refusal"));
    }

    @Test
    public void providersThatRejectThePromptBeforeStreamingKeepTheJsonError() throws Exception {
        for (Provider provider : List.of(Provider.OPENAI, Provider.OPENAI_RESPONSES, Provider.AZURE_OPENAI)) {
            HttpLlmResponseActionHandler.ChaosOutcome outcome = blocked(provider, true, request().withPath("/v1/chat/completions"));

            assertThat(provider.name(), outcome.getStreamedBlock(), is(nullValue()));
            assertThat(provider.name(), outcome.getErrorResponse().getStatusCode(), is(400));
            assertThat(provider.name(), MAPPER.readTree(outcome.getErrorResponse().getBodyAsString()).at("/error/code").asText(), is("content_filter"));
        }
    }

    @Test
    public void nonStreamingRequestGetsNoStreamedBlock() {
        HttpLlmResponseActionHandler.ChaosOutcome outcome = blocked(Provider.ANTHROPIC, false, request().withPath("/v1/messages"));

        assertThat(outcome.getStreamedBlock(), is(nullValue()));
        assertThat(outcome.getErrorResponse(), is(notNullValue()));
    }

    @Test
    public void streamedChaosErrorStaysAJsonError() {
        HttpLlmResponseActionHandler.ChaosOutcome outcome = handler.chaosOutcome(
            HttpLlmResponse.llmResponse()
                .withProvider(Provider.ANTHROPIC)
                .withCompletion(completion().withText("hi").withStreaming(true))
                .withChaos(LlmChaosProfile.llmChaosProfile().withErrorStatus(529)),
            request().withPath("/v1/messages"));

        assertThat(outcome.getStreamedBlock(), is(nullValue()));
        assertThat(outcome.getErrorResponse().getStatusCode(), is(529));
    }

    private static List<String> eventNames(List<SseEvent> events) {
        List<String> names = new ArrayList<>();
        for (SseEvent event : events) {
            names.add(event.getEvent());
        }
        return names;
    }
}
