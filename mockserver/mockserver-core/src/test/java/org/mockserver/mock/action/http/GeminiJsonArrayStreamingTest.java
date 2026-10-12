package org.mockserver.mock.action.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;
import org.mockserver.llm.StreamingFormat;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpSseResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.SseEvent;
import org.mockserver.scheduler.Scheduler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpLlmResponse.llmResponse;
import static org.mockserver.model.HttpRequest.request;

/**
 * Gemini's {@code streamGenerateContent} streams server-sent events only for {@code ?alt=sse}.
 * Without it the REST API returns one JSON array of {@code GenerateContentResponse} chunks, written
 * as {@code [}, the chunks separated by {@code ,\r\n}, then {@code ]}; Google's own REST clients
 * (google-api-core {@code rest_streaming}) reject anything that does not start with {@code [}.
 */
public class GeminiJsonArrayStreamingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEXT = "The capital of Wyoming is Cheyenne.";

    private final HttpLlmResponseActionHandler llmHandler = new HttpLlmResponseActionHandler(new MockServerLogger());
    private final HttpSseResponseActionHandler streamHandler =
        new HttpSseResponseActionHandler(new MockServerLogger(GeminiJsonArrayStreamingTest.class), mock(Scheduler.class), configuration());

    private static HttpRequest stream(String path) {
        return request().withMethod("POST").withPath(path).withBody("{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"Hi\"}]}]}");
    }

    @Test
    public void shouldStreamAJsonArrayWhenTheRequestDoesNotAskForSse() {
        assertThat(llmHandler.streamingFormatFor(Provider.GEMINI, stream("/v1beta/models/gemini-2.5-flash:streamGenerateContent")),
            is(StreamingFormat.JSON_ARRAY));
        assertThat(llmHandler.streamingFormatFor(Provider.GEMINI,
                stream("/v1/projects/p/locations/us-central1/publishers/google/models/gemini-2.5-flash:streamGenerateContent")),
            is(StreamingFormat.JSON_ARRAY));
        assertThat(llmHandler.streamingFormatFor(Provider.GEMINI,
                stream("/v1beta/models/gemini-2.5-flash:streamGenerateContent").withQueryStringParameter("alt", "json")),
            is(StreamingFormat.JSON_ARRAY));
    }

    @Test
    public void shouldStreamServerSentEventsWhenTheRequestAsksForSse() {
        assertThat(llmHandler.streamingFormatFor(Provider.GEMINI,
                stream("/v1beta/models/gemini-2.5-flash:streamGenerateContent").withQueryStringParameter("alt", "sse")),
            is(StreamingFormat.SSE));
    }

    @Test
    public void shouldKeepServerSentEventsForANonStreamingMethodOrNoRequest() {
        assertThat(llmHandler.streamingFormatFor(Provider.GEMINI, stream("/v1beta/models/gemini-2.5-flash:generateContent")),
            is(StreamingFormat.SSE));
        assertThat(llmHandler.streamingFormatFor(Provider.GEMINI, null), is(StreamingFormat.SSE));
    }

    @Test
    public void shouldWriteTheChunksAsOneJsonArray() throws Exception {
        HttpRequest request = stream("/v1beta/models/gemini-2.5-flash:streamGenerateContent");
        List<SseEvent> events = new ArrayList<>();
        // drop the streaming-physics delays so the mock scheduler is never needed
        for (SseEvent event : llmHandler.handleStreaming(
            llmResponse().withProvider(Provider.GEMINI).withModel("gemini-2.5-flash").withCompletion(completion().withText(TEXT).withStreaming(true)),
            request)) {
            events.add(SseEvent.sseEvent().withData(event.getData()));
        }

        Written written = write(HttpSseResponse.sseResponse().withEvents(events), request, StreamingFormat.JSON_ARRAY);

        assertThat(written.contentType, is("application/json"));
        assertThat(written.body, startsWith("[{"));
        assertThat(written.body, endsWith("}]"));
        assertThat(written.body, containsString("},\r\n{"));
        assertThat(written.body, not(containsString("data:")));
        JsonNode array = MAPPER.readTree(written.body);
        assertThat(array.isArray(), is(true));
        assertThat(array.size(), is(events.size()));
        StringBuilder text = new StringBuilder();
        for (JsonNode chunk : array) {
            text.append(chunk.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(""));
        }
        assertThat(text.toString(), is(TEXT));
        assertThat(array.get(array.size() - 1).path("candidates").path(0).path("finishReason").asText(), is("STOP"));
    }

    @Test
    public void shouldWriteAnEmptyArrayForAStreamWithNoChunks() {
        Written written = write(HttpSseResponse.sseResponse(), stream("/v1beta/models/gemini-2.5-flash:streamGenerateContent"), StreamingFormat.JSON_ARRAY);

        assertThat(written.body, is("[]"));
    }

    @Test
    public void shouldLeaveServerSentEventFramingUnchanged() {
        Written written = write(HttpSseResponse.sseResponse().withEvents(
                SseEvent.sseEvent().withData("{\"a\":1}"), SseEvent.sseEvent().withData("{\"b\":2}")),
            stream("/v1beta/models/gemini-2.5-flash:streamGenerateContent").withQueryStringParameter("alt", "sse"), StreamingFormat.SSE);

        assertThat(written.contentType, is("text/event-stream"));
        assertThat(written.body, is("data: {\"a\":1}\n\ndata: {\"b\":2}\n\n"));
    }

    private static final class Written {
        final String contentType;
        final String body;

        Written(String contentType, String body) {
            this.contentType = contentType;
            this.body = body;
        }
    }

    /** Events carry no delay, so the handler writes the whole stream synchronously. */
    private Written write(HttpSseResponse response, HttpRequest request, StreamingFormat format) {
        ChannelInboundHandlerAdapter dummy = new ChannelInboundHandlerAdapter();
        EmbeddedChannel channel = new EmbeddedChannel(dummy);
        ChannelHandlerContext ctx = channel.pipeline().context(dummy);
        streamHandler.handle(response, ctx, request.withKeepAlive(true), format);
        // Netty hands write listeners nested more than 8 deep to the event loop
        for (int i = 0; i < 100; i++) {
            channel.runPendingTasks();
        }

        String contentType = null;
        StringBuilder body = new StringBuilder();
        Object outbound;
        while ((outbound = channel.readOutbound()) != null) {
            if (outbound instanceof HttpResponse) {
                contentType = ((HttpResponse) outbound).headers().get(HttpHeaderNames.CONTENT_TYPE);
            }
            if (outbound instanceof HttpContent) {
                ByteBuf content = ((HttpContent) outbound).content();
                body.append(content.toString(StandardCharsets.UTF_8));
            }
            ReferenceCountUtil.release(outbound);
        }
        channel.finishAndReleaseAll();
        return new Written(contentType, body.toString());
    }
}
