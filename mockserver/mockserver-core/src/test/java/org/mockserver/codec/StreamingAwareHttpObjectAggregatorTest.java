package org.mockserver.codec;

import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.StreamingResponseRelayHandler;
import org.mockserver.model.Message;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;

public class StreamingAwareHttpObjectAggregatorTest {

    @Test
    public void shouldDetectSseResponse() {
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
        HttpUtil.setTransferEncodingChunked(response, true);

        assertThat(StreamingAwareHttpObjectAggregator.isStreamingResponse(response), is(true));
    }

    @Test
    public void shouldDetectSseResponseWithCharset() {
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream; charset=utf-8");
        HttpUtil.setTransferEncodingChunked(response, true);

        assertThat(StreamingAwareHttpObjectAggregator.isStreamingResponse(response), is(true));
    }

    @Test
    public void shouldNotDetectChunkedResponseWithoutContentLengthWhenNotSse() {
        // Regression guard for WAR deployment: Tomcat uses chunked encoding without
        // Content-Length for all servlet responses. These must NOT be detected as streaming.
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        HttpUtil.setTransferEncodingChunked(response, true);

        assertThat(StreamingAwareHttpObjectAggregator.isStreamingResponse(response), is(false));
    }

    @Test
    public void shouldNotDetectChunkedResponseWithContentLength() {
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        HttpUtil.setTransferEncodingChunked(response, true);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 100);

        assertThat(StreamingAwareHttpObjectAggregator.isStreamingResponse(response), is(false));
    }

    @Test
    public void shouldDetectSseResponseWithoutChunkedEncoding() {
        // SSE responses should be detected even without explicit chunked transfer-encoding
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");

        assertThat(StreamingAwareHttpObjectAggregator.isStreamingResponse(response), is(true));
    }

    @Test
    public void shouldNotDetectOctetStreamAsStreaming() {
        // application/octet-stream with chunked encoding should NOT be detected as streaming
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
        HttpUtil.setTransferEncodingChunked(response, true);

        assertThat(StreamingAwareHttpObjectAggregator.isStreamingResponse(response), is(false));
    }

    @Test
    public void shouldNotDetectNormalResponseWithContentLength() {
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 42);

        assertThat(StreamingAwareHttpObjectAggregator.isStreamingResponse(response), is(false));
    }

    @Test
    public void shouldNotDetectResponseWithNoHeaders() {
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        assertThat(StreamingAwareHttpObjectAggregator.isStreamingResponse(response), is(false));
    }

    @Test
    public void shouldFullyAggregateWhenDisableResponseStreamingAttributeIsSet() {
        // When DISABLE_RESPONSE_STREAMING is set on the channel (e.g. FORWARD_REPLACE),
        // the aggregator should NOT switch to streaming mode even for a streaming response.
        Configuration configuration = new Configuration();
        configuration.streamingResponsesEnabled(true);

        StreamingAwareHttpObjectAggregator aggregator =
            new StreamingAwareHttpObjectAggregator(Integer.MAX_VALUE, configuration, null);

        EmbeddedChannel channel = new EmbeddedChannel(aggregator);

        // Set the disable attribute
        AttributeKey<Boolean> disableKey = AttributeKey.valueOf("DISABLE_RESPONSE_STREAMING");
        channel.attr(disableKey).set(true);

        // Send a streaming response head
        DefaultHttpResponse streamingHead = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        streamingHead.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
        HttpUtil.setTransferEncodingChunked(streamingHead, true);

        // Write the head - should be aggregated (not switched to streaming)
        channel.writeInbound(streamingHead);

        // Write a last content to complete aggregation
        channel.writeInbound(new DefaultLastHttpContent());

        // Should produce a FullHttpResponse (aggregated), not a bare HttpResponse
        Object outbound = channel.readInbound();
        assertThat("should produce FullHttpResponse when streaming is disabled on channel",
            outbound, instanceOf(FullHttpResponse.class));

        // Clean up
        if (outbound instanceof FullHttpResponse) {
            ((FullHttpResponse) outbound).release();
        }
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldStreamWhenDisableResponseStreamingAttributeIsNotSet() {
        // When DISABLE_RESPONSE_STREAMING is NOT set, streaming responses should be detected
        Configuration configuration = new Configuration();
        configuration.streamingResponsesEnabled(true);

        // Just test the static detection method (full pipeline test is in integration tests)
        DefaultHttpResponse streamingHead = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        streamingHead.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
        HttpUtil.setTransferEncodingChunked(streamingHead, true);

        assertThat(StreamingAwareHttpObjectAggregator.isStreamingResponse(streamingHead), is(true));
    }

    @Test
    public void shouldStreamWhenRequestExpectedStreamingEvenWithoutSseContentType() {
        // Resilience for backends that stream SSE with NO Content-Type (e.g. the OpenAI Codex
        // backend used by opencode): when the client asked for a stream (EXPECT_STREAMING_RESPONSE),
        // the head must be relayed immediately even though the response is not text/event-stream.
        Configuration configuration = new Configuration();
        configuration.streamingResponsesEnabled(true);

        StreamingAwareHttpObjectAggregator aggregator =
            new StreamingAwareHttpObjectAggregator(Integer.MAX_VALUE, configuration, null);
        EmbeddedChannel channel = new EmbeddedChannel(aggregator);

        channel.attr(AttributeKey.<Boolean>valueOf("EXPECT_STREAMING_RESPONSE")).set(true);
        CompletableFuture<Message> responseFuture = new CompletableFuture<>();
        channel.attr(AttributeKey.<CompletableFuture<Message>>valueOf("RESPONSE_FUTURE")).set(responseFuture);

        // Response head WITHOUT text/event-stream — no content type at all, like the Codex backend
        DefaultHttpResponse head = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpUtil.setTransferEncodingChunked(head, true);
        channel.writeInbound(head);

        // The head must be relayed immediately (future completed) before any body/last-content
        assertThat("head relayed immediately when client expected a stream", responseFuture.isDone(), is(true));
        Object relayed = responseFuture.getNow(null);
        assertThat(relayed, instanceOf(org.mockserver.model.HttpResponse.class));
        assertThat("relayed as a streaming body (head-only response with a StreamingBody attached)",
            ((org.mockserver.model.HttpResponse) relayed).getStreamingBody(), notNullValue());
        // No aggregated FullHttpResponse should be produced
        assertThat(channel.readInbound(), is(nullValue()));

        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldAggregateWhenNoRequestIntentAndNoSseContentType() {
        // Guard against over-triggering: a plain non-SSE response with no streaming request intent
        // must still be aggregated normally (so ordinary forward traffic is unaffected).
        Configuration configuration = new Configuration();
        configuration.streamingResponsesEnabled(true);

        StreamingAwareHttpObjectAggregator aggregator =
            new StreamingAwareHttpObjectAggregator(Integer.MAX_VALUE, configuration, null);
        EmbeddedChannel channel = new EmbeddedChannel(aggregator);

        DefaultHttpResponse head = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        head.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        HttpUtil.setTransferEncodingChunked(head, true);
        channel.writeInbound(head);
        channel.writeInbound(new DefaultLastHttpContent());

        Object outbound = channel.readInbound();
        assertThat("non-streaming response is aggregated when the client did not request a stream",
            outbound, instanceOf(FullHttpResponse.class));
        if (outbound instanceof FullHttpResponse) {
            ((FullHttpResponse) outbound).release();
        }
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldNotStreamWhenDisableSetEvenIfRequestExpectedStreaming() {
        // DISABLE_RESPONSE_STREAMING (e.g. FORWARD_REPLACE response override) must win over both the
        // request intent and an SSE content type, so the response can be fully aggregated and overridden.
        Configuration configuration = new Configuration();
        configuration.streamingResponsesEnabled(true);

        StreamingAwareHttpObjectAggregator aggregator =
            new StreamingAwareHttpObjectAggregator(Integer.MAX_VALUE, configuration, null);
        EmbeddedChannel channel = new EmbeddedChannel(aggregator);

        channel.attr(AttributeKey.<Boolean>valueOf("EXPECT_STREAMING_RESPONSE")).set(true);
        channel.attr(AttributeKey.<Boolean>valueOf("DISABLE_RESPONSE_STREAMING")).set(true);

        DefaultHttpResponse head = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        head.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
        HttpUtil.setTransferEncodingChunked(head, true);
        channel.writeInbound(head);
        channel.writeInbound(new DefaultLastHttpContent());

        Object outbound = channel.readInbound();
        assertThat("disable overrides request intent and SSE content type",
            outbound, instanceOf(FullHttpResponse.class));
        if (outbound instanceof FullHttpResponse) {
            ((FullHttpResponse) outbound).release();
        }
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldRemoveReadTimeoutHandlerWhenSwitchingToStreaming() {
        // The per-request socket read timeout (maxSocketTimeout, ~20s) would kill a streaming
        // response that pauses longer than that between chunks (LLM reasoning). On switching to
        // streaming it must be removed so the stream idle bound (streamIdleTimeoutSeconds) governs.
        Configuration configuration = new Configuration();
        configuration.streamingResponsesEnabled(true);
        configuration.streamIdleTimeoutSeconds(60);

        StreamingAwareHttpObjectAggregator aggregator =
            new StreamingAwareHttpObjectAggregator(Integer.MAX_VALUE, configuration, null);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast("readTimeout", new ReadTimeoutHandler(20, TimeUnit.SECONDS));
        channel.pipeline().addLast(aggregator);

        channel.attr(AttributeKey.<Boolean>valueOf("EXPECT_STREAMING_RESPONSE")).set(true);
        CompletableFuture<Message> responseFuture = new CompletableFuture<>();
        channel.attr(AttributeKey.<CompletableFuture<Message>>valueOf("RESPONSE_FUTURE")).set(responseFuture);

        DefaultHttpResponse head = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpUtil.setTransferEncodingChunked(head, true);
        channel.writeInbound(head);

        assertThat("read timeout removed so a long inter-chunk pause is governed by the stream idle bound",
            channel.pipeline().get(ReadTimeoutHandler.class), is(nullValue()));
        assertThat("stream idle state handler installed to bound the stream when streamIdleTimeoutSeconds>0",
            channel.pipeline().get("streamIdleStateHandler"), notNullValue());
        assertThat("streaming relay handler installed",
            channel.pipeline().get(StreamingResponseRelayHandler.class), notNullValue());

        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldRemoveReadTimeoutHandlerWhenStreamIdleTimeoutDisabled() {
        // Regression guard for the streamIdleTimeoutSeconds=0 footgun: 0 means "no idle bound"
        // (unbounded stream). The socket read timeout (maxSocketTimeout, ~20s) must STILL be removed
        // on switching to streaming — otherwise a disabled idle bound paradoxically left the 20s
        // socket timeout armed and truncated long-paused streams. With 0, no stream idle handler is
        // installed (the stream runs unbounded) but the socket timeout is gone.
        Configuration configuration = new Configuration();
        configuration.streamingResponsesEnabled(true);
        configuration.streamIdleTimeoutSeconds(0);

        StreamingAwareHttpObjectAggregator aggregator =
            new StreamingAwareHttpObjectAggregator(Integer.MAX_VALUE, configuration, null);
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast("readTimeout", new ReadTimeoutHandler(20, TimeUnit.SECONDS));
        channel.pipeline().addLast(aggregator);

        channel.attr(AttributeKey.<Boolean>valueOf("EXPECT_STREAMING_RESPONSE")).set(true);
        CompletableFuture<Message> responseFuture = new CompletableFuture<>();
        channel.attr(AttributeKey.<CompletableFuture<Message>>valueOf("RESPONSE_FUTURE")).set(responseFuture);

        DefaultHttpResponse head = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpUtil.setTransferEncodingChunked(head, true);
        channel.writeInbound(head);

        assertThat("socket read timeout removed even when the stream idle bound is disabled (0)",
            channel.pipeline().get(ReadTimeoutHandler.class), is(nullValue()));
        assertThat("no stream idle state handler installed when streamIdleTimeoutSeconds=0 (unbounded)",
            channel.pipeline().get("streamIdleStateHandler"), is(nullValue()));
        assertThat("streaming relay handler installed",
            channel.pipeline().get(StreamingResponseRelayHandler.class), notNullValue());

        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldSwitchToStreamingAfterMergingATinyChunkResponseOnTheSameConnection() {
        // a loopback relay connection carries one response after another through the same aggregator: the first, in
        // one-byte chunks, passes the 1,024-component limit about sixty times and is merged; the second is an event
        // stream, so the aggregator steps aside at its head and every chunk after it passes through as it arrived
        Configuration configuration = new Configuration();
        configuration.streamingResponsesEnabled(true);
        StreamingAwareHttpObjectAggregator aggregator = new StreamingAwareHttpObjectAggregator(64 * 1024, configuration, null, true);
        List<Object> received = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCodec(), aggregator, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                received.add(msg);
            }
        });
        try {
            channel.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/aggregated"));
            channel.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/streamed"));
            for (Object outbound = channel.readOutbound(); outbound != null; outbound = channel.readOutbound()) {
                ReferenceCountUtil.release(outbound);
            }
            int aggregatedBytes = 60_000;
            int events = 50;
            StringBuilder wire = new StringBuilder("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\n\r\n");
            StringBuilder aggregatedBody = new StringBuilder();
            for (int i = 0; i < aggregatedBytes; i++) {
                char character = (char) ('a' + i % 26);
                aggregatedBody.append(character);
                wire.append("1\r\n").append(character).append("\r\n");
            }
            wire.append("0\r\n\r\nHTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\n\r\n");
            for (int i = 0; i < events; i++) {
                wire.append("1\r\n").append((char) ('A' + i % 26)).append("\r\n");
            }
            wire.append("0\r\n\r\n");
            channel.writeInbound(Unpooled.copiedBuffer(wire, StandardCharsets.US_ASCII));

            assertThat(received.get(0), instanceOf(FullHttpResponse.class));
            FullHttpResponse aggregated = (FullHttpResponse) received.get(0);
            assertThat(aggregated.content().toString(StandardCharsets.US_ASCII), is(aggregatedBody.toString()));
            assertThat(((CompositeByteBuf) aggregated.content()).numComponents(), lessThanOrEqualTo(1024));
            assertThat(received.get(1), instanceOf(HttpResponse.class));
            assertThat(received.get(1), not(instanceOf(FullHttpResponse.class)));
            assertThat(((HttpResponse) received.get(1)).headers().get(HttpHeaderNames.CONTENT_TYPE), is("text/event-stream"));
            assertThat(received.size(), is(2 + events + 1));
            for (int i = 0; i < events; i++) {
                HttpContent chunk = (HttpContent) received.get(2 + i);
                assertThat(chunk.content().toString(StandardCharsets.US_ASCII), is(String.valueOf((char) ('A' + i % 26))));
            }
            assertThat(received.get(2 + events), instanceOf(LastHttpContent.class));
            assertThat("the aggregator stepped aside for the stream", channel.pipeline().get(StreamingAwareHttpObjectAggregator.class), is(nullValue()));
        } finally {
            received.forEach(ReferenceCountUtil::release);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void shouldRefuseAStreamedBodyAtAResponseLimitOfZeroAsItRefusesAnAggregatedOne() {
        for (int configured : new int[]{0, -1}) {
            Configuration configuration = new Configuration().streamingResponsesEnabled(true).maxResponseBodySize(configured);

            // aggregated: a body over the limit never reaches the next handler
            EmbeddedChannel aggregating = new EmbeddedChannel(new StreamingAwareHttpObjectAggregator(configuration.maxResponseBodySize(), configuration, null));
            DefaultHttpResponse aggregatedHead = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            HttpUtil.setTransferEncodingChunked(aggregatedHead, true);
            Throwable refused = null;
            try {
                aggregating.writeInbound(aggregatedHead, new DefaultHttpContent(Unpooled.copiedBuffer("ab", StandardCharsets.US_ASCII)), LastHttpContent.EMPTY_LAST_CONTENT);
            } catch (Exception tooLong) {
                refused = tooLong;
            }
            assertThat("limit " + configured, refused, instanceOf(TooLongHttpContentException.class));
            assertThat(aggregating.readInbound(), is(nullValue()));
            aggregating.finishAndReleaseAll();

            // streamed: the same body, not yet written to the client, fails the stream and closes the upstream
            EmbeddedChannel streaming = new EmbeddedChannel(new StreamingAwareHttpObjectAggregator(configuration.maxResponseBodySize(), configuration, null));
            CompletableFuture<Message> responseFuture = new CompletableFuture<>();
            streaming.attr(AttributeKey.<CompletableFuture<Message>>valueOf("RESPONSE_FUTURE")).set(responseFuture);
            DefaultHttpResponse streamedHead = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            streamedHead.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
            HttpUtil.setTransferEncodingChunked(streamedHead, true);
            streaming.writeInbound(streamedHead);
            org.mockserver.model.StreamingBody body = ((org.mockserver.model.HttpResponse) responseFuture.getNow(null)).getStreamingBody();
            body.subscribe(chunk -> {
            }, () -> {
            }, error -> {
            });

            streaming.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("ab", StandardCharsets.US_ASCII)));

            assertThat("limit " + configured, body.getError(), instanceOf(org.mockserver.model.StreamingBody.UnwrittenBytesLimitExceededException.class));
            assertThat(streaming.isOpen(), is(false));
            streaming.finishAndReleaseAll();
        }
    }

    @Test
    public void shouldDetectBedrockAndOllamaStreamsByTheirContentType() {
        for (String contentType : new String[]{"application/vnd.amazon.eventstream", "application/x-ndjson", "application/x-ndjson; charset=utf-8", "Application/X-NDJSON"}) {
            DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);

            assertThat(contentType, StreamingAwareHttpObjectAggregator.isStreamingResponse(response), is(true));
        }
    }

    @Test
    public void shouldRelayAnAwsEventStreamAndAnNdjsonResponseAsStreams() {
        for (String contentType : new String[]{"application/vnd.amazon.eventstream", "application/x-ndjson"}) {
            Configuration configuration = new Configuration();
            configuration.streamingResponsesEnabled(true);
            EmbeddedChannel channel = new EmbeddedChannel(new StreamingAwareHttpObjectAggregator(Integer.MAX_VALUE, configuration, null));
            CompletableFuture<Message> responseFuture = new CompletableFuture<>();
            channel.attr(AttributeKey.<CompletableFuture<Message>>valueOf("RESPONSE_FUTURE")).set(responseFuture);
            try {
                DefaultHttpResponse head = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                head.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
                HttpUtil.setTransferEncodingChunked(head, true);
                channel.writeInbound(head);

                assertThat(contentType + " head relayed before its body", responseFuture.isDone(), is(true));
                org.mockserver.model.StreamingBody body = ((org.mockserver.model.HttpResponse) responseFuture.getNow(null)).getStreamingBody();
                assertThat(contentType, body, notNullValue());
                List<String> relayed = new ArrayList<>();
                body.subscribe(chunk -> relayed.add(chunk.toString(StandardCharsets.UTF_8)), () -> {
                }, error -> {
                });
                channel.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("{\"done\":false}\n", StandardCharsets.UTF_8)));

                assertThat(contentType + " first chunk relayed before the response ends", relayed, contains("{\"done\":false}\n"));
                channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("{\"done\":true}\n", StandardCharsets.UTF_8)));
                assertThat(contentType, relayed, contains("{\"done\":false}\n", "{\"done\":true}\n"));
                assertThat(channel.readInbound(), is(nullValue()));
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }
}
