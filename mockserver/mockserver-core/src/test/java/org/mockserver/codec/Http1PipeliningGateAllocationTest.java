package org.mockserver.codec;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;

import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.mockserver.testing.AllocationMeasurement.allocatedBytes;
import static org.mockserver.testing.AllocationMeasurement.assumeC1Only;

/**
 * A connection that never pipelines must allocate exactly what it did before the gate existed: per exchange, the gated
 * pipeline allocates the same bytes as one with a pass-through handler in its place, for keep-alive exchanges and for
 * exchanges whose request and response say {@code Connection: close}.
 */
public class Http1PipeliningGateAllocationTest {

    private static final int EXCHANGES = 10_000;
    // the least of several interleaved measurements per arm: background work can only add bytes, never remove the code's own
    private static final int ATTEMPTS = 5;

    @Test
    public void shouldAllocateNothingPerKeepAliveExchange() {
        assertNoAllocationAddedByTheGate(false);
    }

    @Test
    public void shouldAllocateNothingPerNonKeepAliveExchange() {
        assertNoAllocationAddedByTheGate(true);
    }

    @Test
    public void shouldSeeAnAllocationOfOneObjectPerExchange() {
        assumeC1Only("the measurement's sensitivity");
        Exchange plain = new Exchange(ChannelDuplexHandler::new, false);
        Exchange allocating = new Exchange(AllocatingHandler::new, false);
        warmUp(plain, allocating);

        long difference = least(allocating) - least(plain);

        assertThat("one 16-byte object per exchange must show", difference, greaterThanOrEqualTo(16L));
        plain.close();
        allocating.close();
    }

    private static void assertNoAllocationAddedByTheGate(boolean connectionClose) {
        // C2 inlines the two pipelines differently, so escape analysis could remove an object in one and keep it in the other
        assumeC1Only("the gate's per-exchange cost against a pass-through handler");
        Exchange plain = new Exchange(ChannelDuplexHandler::new, connectionClose);
        Exchange gated = new Exchange(Http1PipeliningGateAllocationTest::gate, connectionClose);
        warmUp(plain, gated);

        long plainBytes = Long.MAX_VALUE;
        long gatedBytes = Long.MAX_VALUE;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            plainBytes = Math.min(plainBytes, plain.perExchange());
            gatedBytes = Math.min(gatedBytes, gated.perExchange());
        }

        assertThat("bytes per exchange with the gate " + gatedBytes + " vs without " + plainBytes, gatedBytes - plainBytes, is(0L));
        assertThat("every exchange took the gate's hot path", Http1PipeliningGate.isSlowPath(gated.channel), is(false));
        plain.close();
        gated.close();
    }

    private static Http1PipeliningGate gate() {
        return new Http1PipeliningGate(Configuration.configuration(), HeldRequestAccounting.NONE, new MockServerLogger(Http1PipeliningGateAllocationTest.class));
    }

    private static void warmUp(Exchange... exchanges) {
        for (int round = 0; round < 3; round++) {
            for (Exchange exchange : exchanges) {
                exchange.perExchange();
            }
        }
    }

    private static long least(Exchange exchange) {
        long least = Long.MAX_VALUE;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            least = Math.min(least, exchange.perExchange());
        }
        return least;
    }

    /**
     * One request read and one response written per exchange, half of them whole and half streamed (a head, then its
     * last content), all through one connection's pipeline, with void promises so the channel's own promises add nothing.
     */
    private static final class Exchange {
        private final EmbeddedChannel channel;
        private final DefaultFullHttpRequest fullRequest;
        private final DefaultHttpRequest requestHead;
        private final DefaultFullHttpResponse fullResponse;
        private final DefaultHttpResponse responseHead;

        Exchange(Supplier<ChannelHandler> handler, boolean connectionClose) {
            channel = new EmbeddedChannel(handler.get());
            fullRequest = connection(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER), connectionClose);
            requestHead = connection(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/"), connectionClose);
            fullResponse = connection(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER), connectionClose);
            responseHead = connection(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), connectionClose);
        }

        private static <T extends HttpMessage> T connection(T message, boolean connectionClose) {
            message.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            if (connectionClose) {
                message.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }
            return message;
        }

        long perExchange() {
            long before = allocatedBytes();
            for (int i = 0; i < EXCHANGES; i += 2) {
                channel.pipeline().fireChannelRead(fullRequest);
                channel.inboundMessages().poll();
                channel.writeAndFlush(fullResponse, channel.voidPromise());
                channel.outboundMessages().poll();

                channel.pipeline().fireChannelRead(requestHead);
                channel.pipeline().fireChannelRead(LastHttpContent.EMPTY_LAST_CONTENT);
                channel.inboundMessages().poll();
                channel.inboundMessages().poll();
                channel.write(responseHead, channel.voidPromise());
                channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, channel.voidPromise());
                channel.outboundMessages().poll();
                channel.outboundMessages().poll();
            }
            return (allocatedBytes() - before) / EXCHANGES;
        }

        void close() {
            channel.finishAndReleaseAll();
        }
    }

    /**
     * The negative control: allocates one small object per request head it passes on.
     */
    private static final class AllocatingHandler extends ChannelDuplexHandler {
        private static volatile Object sink;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof io.netty.handler.codec.http.HttpRequest) {
                sink = new Object();
            }
            ctx.fireChannelRead(msg);
        }
    }
}
