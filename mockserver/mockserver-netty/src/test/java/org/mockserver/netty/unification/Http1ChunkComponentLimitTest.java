package org.mockserver.netty.unification;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledDirectByteBuf;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;
import org.mockserver.codec.CoalescingHttpObjectAggregator;
import org.mockserver.codec.HttpObjectAggregators;
import org.mockserver.configuration.Configuration;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.responsewriter.HttpExchangeEndedEvent;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.action.http.HttpActionHandler;
import org.mockserver.netty.MockServerUnificationInitializer;
import org.mockserver.scheduler.Scheduler;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * An HTTP/1.1 connection's aggregator holds up to {@code maxRequestBodySize} / 1 KiB components (10,240 at the
 * 10 MiB default) and serves every request on a keep-alive connection. Wire bytes go through the chain
 * {@link PortUnificationHandler} builds for HTTP/1.1 (codec, decompressor, early matching, aggregator); the requests
 * are captured straight after the aggregator.
 */
public class Http1ChunkComponentLimitTest {

    private static final int MIB = 1024 * 1024;
    private static final String HEAD = "POST /upload HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n";

    @Test
    public void shouldInstallAMergingAggregatorWithTheConnectionLimit() {
        for (int[] maxAndLimit : new int[][]{{10 * MIB, 10_240}, {64 * MIB, 65_536}}) {
            Chain chain = new Chain(configuration().maxRequestBodySize(maxAndLimit[0]), null);
            try {
                chain.write(List.of(HEAD.getBytes(StandardCharsets.US_ASCII)));
                HttpObjectAggregator aggregator = chain.channel.pipeline().get(HttpObjectAggregator.class);
                assertThat(aggregator, instanceOf(CoalescingHttpObjectAggregator.class));
                assertThat(((CoalescingHttpObjectAggregator) aggregator).isMergingNewComponentsOnly(), is(true));
                assertThat(aggregator.maxCumulationBufferComponents(), is(maxAndLimit[1]));
            } finally {
                chain.finish();
            }
        }
    }

    @Test(timeout = 120_000)
    public void shouldCopyABodyOfOneByteChunksAboutOnce() {
        // consolidating the whole body every 10,240 chunks would copy about chunks^2 / 20,480 bytes: 214 MB here;
        // merging only the chunks since the last merge copies each byte once, and the chunks a read still holds after
        // a merge, under half of it, are copied once more when the body moves on to the next read (about 6% here)
        int bodyBytes = 2 * MIB;
        TrackingAllocator allocator = new TrackingAllocator();
        Chain chain = new Chain(configuration(), allocator);
        try {
            byte[] body = body(0, bodyBytes);
            chain.write(chunked(body, new int[]{1}));
            CompositeByteBuf content = chain.capture.content(0);
            assertThat(ByteBufUtil.getBytes(content), is(body));
            assertThat(content.numComponents(), lessThanOrEqualTo(10_240));
            assertThat(allocator.allocatedBytes, allOf(lessThanOrEqualTo((long) bodyBytes + bodyBytes / 8), greaterThan((long) bodyBytes - 10_240)));
        } finally {
            chain.finish();
        }
        allocator.assertAllReleased();
    }

    @Test(timeout = 120_000)
    public void shouldCopyABodySentInOneByteReadsAboutOnce() {
        // a Content-Length body arrives in whatever the socket reads, one piece per read
        int bodyBytes = 512 * 1024;
        TrackingAllocator allocator = new TrackingAllocator();
        Chain chain = new Chain(configuration(), allocator);
        try {
            byte[] body = body(0, bodyBytes);
            List<byte[]> reads = new ArrayList<>();
            reads.add(("POST /upload HTTP/1.1\r\nHost: localhost\r\nContent-Length: " + bodyBytes + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            for (int i = 0; i < bodyBytes; i++) {
                reads.add(new byte[]{body[i]});
            }
            chain.write(reads);
            CompositeByteBuf content = chain.capture.content(0);
            assertThat(ByteBufUtil.getBytes(content), is(body));
            assertThat(allocator.allocatedBytes, allOf(lessThanOrEqualTo((long) bodyBytes), greaterThan((long) bodyBytes - 10_240)));
        } finally {
            chain.finish();
        }
        allocator.assertAllReleased();
    }

    @Test(timeout = 120_000)
    public void shouldHoldAtMostTwiceTheBodyPlusTheChunkFramingAndOneRead() {
        // a chunk is a slice of a read, and a read stays allocated while any chunk in it is held, so what is held at
        // once counts the reads; a merge releases every chunk it covers, so only reads holding chunks since the last
        // merge (and one read partly merged) are held besides the merged body. Copying runs of small chunks into
        // blocks, as HTTP/2 streams do, would add the copies on top of those reads
        int[][] patterns = {
            mix(31, 100, 1, 1024), mix(16, 64, 1, 1024, 5, 10, 1, 1024), mix(16, 1023, 1, 1024, 15, 1, 1, 1024),
            mix(64, 1023, 1, 1024, 15, 1, 1, 1024, 15, 1, 1, 1024, 15, 1, 1, 1024, 15, 1, 1, 1024)
        };
        for (int maxRequestBodySize : new int[]{256 * 1024, MIB, 10 * MIB}) {
            int limit = HttpObjectAggregators.componentLimit(maxRequestBodySize);
            // one-byte chunks past two merges, then 1,000-byte chunks up to just before Netty's third merge
            int[][] withPhaseShift = Arrays.copyOf(patterns, patterns.length + 1);
            withPhaseShift[patterns.length] = mix(2 * limit + 1, 1, limit - 1, 1000);
            for (int[] pattern : withPhaseShift) {
                Usage usage = new Usage();
                TrackingAllocator allocator = new TrackingAllocator(usage);
                TrackingAllocator readAllocator = new TrackingAllocator(usage);
                Chain chain = new Chain(configuration().maxRequestBodySize(maxRequestBodySize), allocator);
                byte[] body = body(0, maxRequestBodySize);
                List<byte[]> reads = chunked(body, pattern);
                long framing = reads.stream().mapToLong(read -> read.length).sum() - HEAD.length() - body.length;
                long largestRead = reads.stream().mapToLong(read -> read.length).max().orElse(0);
                try {
                    chain.write(reads, readAllocator);
                    CompositeByteBuf content = chain.capture.content(0);
                    assertThat(ByteBufUtil.getBytes(content), is(body));
                } finally {
                    chain.finish();
                }
                allocator.assertAllReleased();
                readAllocator.assertAllReleased();
                String description = "maxRequestBodySize " + maxRequestBodySize + " pieces " + Arrays.toString(Arrays.copyOf(pattern, 8));
                assertThat(description, usage.peakLiveBytes, lessThanOrEqualTo(2L * maxRequestBodySize + framing + largestRead));
                // merged about once, plus the chunks of reads used under half, copied when the body moves on
                assertThat(description, allocator.allocatedBytes, lessThanOrEqualTo((long) maxRequestBodySize + maxRequestBodySize / 8));
            }
        }
    }

    @Test(timeout = 120_000)
    public void shouldKeepEachKeepAliveRequestIntactOnThePooledAllocatorAcrossMerges() {
        // about 2.6 chunks per KiB, so at 4 MiB (limit 4,096) each request is merged from about 1.6 MB on, several
        // times, with reads, merged components and the next request's reads all from the pooled allocator: a merged
        // component used after its memory went back to the pool, or merge state carried into the next request, is
        // another body's bytes
        int[] pattern = new int[16 + 1 + 5 + 1];
        Arrays.fill(pattern, 0, 16, 64);
        pattern[16] = 1024;
        Arrays.fill(pattern, 17, 22, 10);
        pattern[22] = 1024;
        int maxRequestBodySize = 4 * MIB;
        int bodyBytes = maxRequestBodySize - 1000;
        Chain chain = new Chain(configuration().maxRequestBodySize(maxRequestBodySize), PooledByteBufAllocator.DEFAULT);
        try {
            for (int message = 0; message < 3; message++) {
                byte[] body = body(message, bodyBytes);
                chain.write(chunked(body, pattern), PooledByteBufAllocator.DEFAULT);
                CompositeByteBuf content = chain.capture.content(message);
                try {
                    assertThat("request " + message, ByteBufUtil.getBytes(content), is(body));
                    assertThat("request " + message, content.numComponents(), lessThanOrEqualTo(4096));
                } finally {
                    content.release();
                }
            }
            // pipelined: the first request is still held while the second is aggregated, so their merged components coexist
            byte[] first = body(3, bodyBytes);
            byte[] second = body(4, bodyBytes);
            List<byte[]> reads = chunked(first, pattern);
            reads.addAll(chunked(second, pattern));
            chain.write(reads, PooledByteBufAllocator.DEFAULT);
            CompositeByteBuf firstContent = chain.capture.content(3);
            CompositeByteBuf secondContent = chain.capture.content(4);
            try {
                assertThat(ByteBufUtil.getBytes(firstContent), is(first));
                assertThat(ByteBufUtil.getBytes(secondContent), is(second));
            } finally {
                firstContent.release();
                secondContent.release();
            }
        } finally {
            chain.finish();
        }
    }

    @Test(timeout = 120_000)
    public void shouldAcceptABodyOfOneByteChunksAtTheLimitAndAnswer413OneByteOver() {
        int maxRequestBodySize = 64 * 1024;
        TrackingAllocator allocator = new TrackingAllocator();
        Chain chain = new Chain(configuration().maxRequestBodySize(maxRequestBodySize), allocator);
        try {
            byte[] body = body(0, maxRequestBodySize);
            chain.write(chunked(body, new int[]{1}));
            CompositeByteBuf content = chain.capture.content(0);
            assertThat(ByteBufUtil.getBytes(content), is(body));
            content.release();
            assertThat(chain.outbound(), is(""));

            chain.write(chunked(body(1, maxRequestBodySize + 1), new int[]{1}));
            assertThat(chain.capture.requests.size(), is(1));
            assertThat(chain.outbound(), startsWith("HTTP/1.1 413 Request Entity Too Large"));
            assertThat("a body cut off by the limit cannot be resumed", chain.channel.isOpen(), is(false));
        } finally {
            chain.finish();
        }
        allocator.assertAllReleased();
    }

    /**
     * The request head, then the body in chunks of the pattern's sizes, in reads that each end at a chunk boundary
     * (so the decoder emits each chunk whole and keeps nothing between reads), of about 64 KiB.
     */
    private static List<byte[]> chunked(byte[] body, int[] pattern) {
        List<byte[]> reads = new ArrayList<>();
        reads.add(HEAD.getBytes(StandardCharsets.US_ASCII));
        ByteArrayOutputStream read = new ByteArrayOutputStream();
        int offset = 0;
        for (int piece = 0; offset < body.length; piece++) {
            int length = Math.min(pattern[piece % pattern.length], body.length - offset);
            byte[] size = (Integer.toHexString(length) + "\r\n").getBytes(StandardCharsets.US_ASCII);
            read.write(size, 0, size.length);
            read.write(body, offset, length);
            read.write('\r');
            read.write('\n');
            offset += length;
            if (read.size() >= 64 * 1024) {
                reads.add(read.toByteArray());
                read.reset();
            }
        }
        read.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII), 0, 5);
        reads.add(read.toByteArray());
        return reads;
    }

    /**
     * Pairs of (count, size): {@code count} chunks of {@code size} bytes, in order.
     */
    private static int[] mix(int... countsAndSizes) {
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < countsAndSizes.length; i += 2) {
            for (int piece = 0; piece < countsAndSizes[i]; piece++) {
                sizes.add(countsAndSizes[i + 1]);
            }
        }
        return sizes.stream().mapToInt(Integer::intValue).toArray();
    }

    private static byte[] body(int message, int length) {
        byte[] body = new byte[length];
        for (int i = 0; i < length; i++) {
            body[i] = (byte) ((i * 31 + i / 251 + message * 17) % 256);
        }
        return body;
    }

    /**
     * The HTTP/1.1 server chain, which the first read (a whole request head) switches to, with the capturing handler
     * added straight after the aggregator once it has.
     */
    private static final class Chain {
        private final EmbeddedChannel channel = new EmbeddedChannel();
        private final CapturingHandler capture = new CapturingHandler();
        private final HttpState httpState;
        private boolean switched;

        Chain(Configuration configuration, ByteBufAllocator allocator) {
            if (allocator != null) {
                channel.config().setAllocator(allocator);
            }
            httpState = new HttpState(configuration, new MockServerLogger(), mock(Scheduler.class));
            channel.pipeline().addLast(new MockServerUnificationInitializer(
                configuration,
                mock(LifeCycle.class),
                httpState,
                mock(HttpActionHandler.class),
                null
            ));
        }

        void write(List<byte[]> reads) {
            write(reads, null);
        }

        /**
         * @param readAllocator the allocator for each read, as a socket's reads come from the channel's allocator;
         *                      null to wrap them
         */
        void write(List<byte[]> reads, ByteBufAllocator readAllocator) {
            int first = 0;
            if (!switched) {
                // the head switches the pipeline and starts the aggregation; the capture is added before any body
                channel.writeInbound(Unpooled.wrappedBuffer(reads.get(0)));
                channel.pipeline().addAfter(channel.pipeline().context(HttpObjectAggregator.class).name(), "capture", capture);
                switched = true;
                first = 1;
            }
            for (byte[] read : reads.subList(first, reads.size())) {
                ByteBuf buffer = readAllocator != null ? readAllocator.buffer(read.length).writeBytes(read) : Unpooled.wrappedBuffer(read);
                if (channel.isOpen()) {
                    channel.writeInbound(buffer);
                } else {
                    buffer.release();
                }
            }
        }

        String outbound() {
            StringBuilder outbound = new StringBuilder();
            Object message;
            while ((message = channel.readOutbound()) != null) {
                if (message instanceof ByteBuf) {
                    outbound.append(((ByteBuf) message).toString(StandardCharsets.US_ASCII));
                }
                ReferenceCountUtil.release(message);
            }
            return outbound.toString();
        }

        void finish() {
            outbound();
            for (FullHttpRequest request : capture.requests) {
                if (request.refCnt() > 0) {
                    request.release();
                }
            }
            channel.finishAndReleaseAll();
            httpState.stop();
        }
    }

    /**
     * Captures each aggregated request, and any exception the aggregator raised, which the handlers after it would
     * otherwise turn into a response. Each exchange is ended as it is captured, as a response would end it, so the
     * connection's in-order pipelining gate passes the next request on rather than holding it behind this one.
     */
    private static final class CapturingHandler extends ChannelInboundHandlerAdapter {
        private final List<FullHttpRequest> requests = new ArrayList<>();
        private Throwable failure;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            requests.add((FullHttpRequest) msg);
            HttpExchangeEndedEvent.fire(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (failure == null) {
                failure = cause;
            }
        }

        CompositeByteBuf content(int request) {
            if (failure != null) {
                throw new AssertionError("the aggregator raised an exception", failure);
            }
            assertThat("the aggregator should have produced request " + request, requests.size(), greaterThan(request));
            assertThat(requests.get(request).content(), instanceOf(CompositeByteBuf.class));
            return (CompositeByteBuf) requests.get(request).content();
        }
    }

    private static final class Usage {
        private long liveBytes;
        private long peakLiveBytes;
    }

    /**
     * Counts every byte it allocates and the most it had allocated and not yet freed at once, and checks every
     * buffer it handed out was released.
     */
    private static final class TrackingAllocator extends AbstractByteBufAllocator {
        private final List<ByteBuf> buffers = new ArrayList<>();
        private final Usage usage;
        private long allocatedBytes;

        TrackingAllocator() {
            this(new Usage());
        }

        TrackingAllocator(Usage usage) {
            super(false);
            this.usage = usage;
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            allocated(initialCapacity);
            ByteBuf buffer = new UnpooledHeapByteBuf(this, initialCapacity, maxCapacity) {
                @Override
                protected void deallocate() {
                    usage.liveBytes -= initialCapacity;
                    super.deallocate();
                }
            };
            buffers.add(buffer);
            return buffer;
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            allocated(initialCapacity);
            ByteBuf buffer = new UnpooledDirectByteBuf(this, initialCapacity, maxCapacity) {
                @Override
                protected void deallocate() {
                    usage.liveBytes -= initialCapacity;
                    super.deallocate();
                }
            };
            buffers.add(buffer);
            return buffer;
        }

        private void allocated(int bytes) {
            allocatedBytes += bytes;
            usage.liveBytes += bytes;
            usage.peakLiveBytes = Math.max(usage.peakLiveBytes, usage.liveBytes);
        }

        @Override
        public boolean isDirectBufferPooled() {
            return false;
        }

        void assertAllReleased() {
            for (ByteBuf buffer : buffers) {
                assertThat("allocated buffer released", buffer.refCnt(), is(0));
            }
        }
    }
}
