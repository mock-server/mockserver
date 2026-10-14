package org.mockserver.benchmark;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import org.mockserver.codec.HttpObjectAggregators;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Aggregating one HTTP/2 request stream's body, within-run A/B. {@code STREAM} is the production stream
 * aggregator ({@link HttpObjectAggregators#streamHttpObjectAggregator}, which copies runs of pieces under 1 KiB
 * into 16 KiB blocks and merges only new components at the limit); {@code NETTY} is a plain {@link HttpObjectAggregator} with the same stream component limit, the
 * aggregator the stream used before, which consolidates the whole body each time it passes the limit. Frames are
 * fed as {@link io.netty.handler.codec.http.HttpContent}, as {@code Http2StreamFrameToHttpObjectCodec} emits them.
 * <p>
 * On demand only; run with {@code -prof gc} and read {@code gc.alloc.rate.norm} alongside the time. At one-byte
 * frames the NETTY arm copies about {@code bodyBytes^2 / 2,048} bytes; at 1 KiB frames and above, under the component
 * limit, both arms must allocate the same.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class Http2StreamAggregationBenchmark {

    private static final int MAX_CONTENT_LENGTH = 10 * 1024 * 1024;

    @Param({"STREAM", "NETTY"})
    public String aggregator;

    @Param({"1", "100", "1024", "8192", "16384"})
    public int frameBytes;

    @Param({"1048576"})
    public int bodyBytes;

    private byte[] body;
    private EmbeddedChannel channel;

    @Setup
    public void setup() {
        body = new byte[bodyBytes];
        for (int i = 0; i < bodyBytes; i++) {
            body[i] = (byte) (i % 251);
        }
        HttpObjectAggregator handler;
        if ("STREAM".equals(aggregator)) {
            handler = HttpObjectAggregators.streamHttpObjectAggregator(MAX_CONTENT_LENGTH);
        } else {
            handler = new HttpObjectAggregator(MAX_CONTENT_LENGTH);
            handler.setMaxCumulationBufferComponents(HttpObjectAggregators.streamComponentLimit(MAX_CONTENT_LENGTH));
        }
        channel = new EmbeddedChannel(handler);
        FullHttpRequest request = aggregateOnce();
        try {
            if (!Arrays.equals(ByteBufUtil.getBytes(request.content()), body)) {
                throw new IllegalStateException(aggregator + " did not aggregate the body unchanged");
            }
        } finally {
            request.release();
        }
    }

    @TearDown
    public void tearDown() {
        channel.finishAndReleaseAll();
    }

    @Benchmark
    public int aggregate() {
        FullHttpRequest request = aggregateOnce();
        try {
            return request.content().readableBytes();
        } finally {
            request.release();
        }
    }

    private FullHttpRequest aggregateOnce() {
        channel.writeInbound(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload"));
        for (int offset = 0; offset < bodyBytes; offset += frameBytes) {
            channel.writeInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(body, offset, Math.min(frameBytes, bodyBytes - offset))));
        }
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
        return channel.readInbound();
    }
}
