package org.mockserver.benchmark;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import org.mockserver.codec.CoalescingHttpObjectAggregator;
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Decoding and aggregating one HTTP/1.1 request on a keep-alive connection, within-run A/B, at the 10 MiB
 * default {@code maxRequestBodySize} (component limit 10,240). {@code MERGE} is the production server aggregator
 * ({@link HttpObjectAggregators#httpObjectAggregator}, which past the limit merges only the components added since
 * its last merge); {@code NETTY} is the aggregator it replaced, a plain {@link HttpObjectAggregator} with the same
 * limit, which consolidates the whole body each time it passes the limit; {@code BLOCKS} also copies runs of pieces
 * under 1 KiB into 16 KiB blocks, the HTTP/2 stream rule, for comparison. The wire bytes go through the server's
 * {@link HttpRequestDecoder}: chunked, in reads of about 64 KiB, or with a {@code Content-Length} in reads of
 * {@code pieceBytes}.
 * <p>
 * On demand only; run with {@code -prof gc} and read {@code gc.alloc.rate.norm} alongside the time. The channel
 * allocates unpooled heap buffers, so the copies count in it: for a body of one-byte chunks the NETTY arm copies
 * about {@code bodyBytes^2 / 20,480} bytes; pieces of 1 KiB or more stay under the limit, where all three arms must
 * allocate about the same.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class Http1ChunkedAggregationBenchmark {

    private static final int MAX_CONTENT_LENGTH = 10 * 1024 * 1024;

    @Param({"MERGE", "NETTY", "BLOCKS"})
    public String aggregator;

    @Param({"CHUNKED", "CONTENT_LENGTH"})
    public String framing;

    @Param({"1", "100", "1024", "8192"})
    public int pieceBytes;

    @Param({"1024", "1048576"})
    public int bodyBytes;

    private byte[] body;
    private List<ByteBuf> reads;
    private EmbeddedChannel channel;

    @Setup
    public void setup() {
        body = new byte[bodyBytes];
        for (int i = 0; i < bodyBytes; i++) {
            body[i] = (byte) ('a' + i % 26);
        }
        reads = "CHUNKED".equals(framing) ? chunked() : contentLength();
        HttpObjectAggregator handler;
        if ("MERGE".equals(aggregator)) {
            handler = HttpObjectAggregators.httpObjectAggregator(MAX_CONTENT_LENGTH);
        } else if ("BLOCKS".equals(aggregator)) {
            CoalescingHttpObjectAggregator blocks = HttpObjectAggregators.limitComponents(new CoalescingHttpObjectAggregator(MAX_CONTENT_LENGTH));
            blocks.coalesceSmallContent();
            handler = blocks;
        } else {
            handler = HttpObjectAggregators.limitComponents(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
        }
        channel = new EmbeddedChannel();
        channel.config().setAllocator(new UnpooledByteBufAllocator(false));
        channel.pipeline().addLast(new HttpRequestDecoder(), handler);
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
        reads.forEach(ByteBuf::release);
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
        for (ByteBuf read : reads) {
            channel.writeInbound(read.retainedDuplicate());
        }
        return channel.readInbound();
    }

    private List<ByteBuf> chunked() {
        List<ByteBuf> chunked = new ArrayList<>();
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.writeBytes("POST /upload HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        for (int offset = 0; offset < bodyBytes; offset += pieceBytes) {
            int length = Math.min(pieceBytes, bodyBytes - offset);
            wire.writeBytes((Integer.toHexString(length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            wire.write(body, offset, length);
            wire.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
            if (wire.size() >= 64 * 1024) {
                chunked.add(Unpooled.wrappedBuffer(wire.toByteArray()));
                wire.reset();
            }
        }
        wire.writeBytes("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        chunked.add(Unpooled.wrappedBuffer(wire.toByteArray()));
        return chunked;
    }

    private List<ByteBuf> contentLength() {
        List<ByteBuf> contentLength = new ArrayList<>();
        contentLength.add(Unpooled.wrappedBuffer(("POST /upload HTTP/1.1\r\nHost: localhost\r\nContent-Length: " + bodyBytes + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII)));
        for (int offset = 0; offset < bodyBytes; offset += pieceBytes) {
            contentLength.add(Unpooled.wrappedBuffer(body, offset, Math.min(pieceBytes, bodyBytes - offset)));
        }
        return contentLength;
    }
}
