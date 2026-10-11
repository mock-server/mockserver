package org.mockserver.benchmark;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * The read-buffer cost of one message on a binary (non-HTTP) proxied connection, at the read size binary
 * connections use ({@code BinaryAwareRecvByteBufAllocator.BINARY_READ_SIZE}, 64 KiB) against 32 KiB.
 *
 * <p>Each op is one read loop as a binary connection runs it: {@code ceil(messageBytes / readBufferSize)}
 * reads, each taking a direct buffer of {@code readBufferSize} from the pooled allocator and filling it (the
 * socket read copy), the pieces held without copying as {@code BinaryMessageGatherer} holds them (the first
 * alone, then a {@link CompositeByteBuf}), and the message released once delivered.</p>
 *
 * <p>Why the two sizes can differ: {@link PooledByteBufAllocator} caches a freed buffer per thread only up to
 * {@code io.netty.allocator.maxCachedBufferCapacity} (32 KiB by default). A 32 KiB read buffer comes from the
 * thread cache; a 64 KiB one goes to the arena and back, under the arena's lock, on every read. The arms are
 * the {@code readBufferSize} param; {@code allocateOnly} drops the fill, so the allocator's share of
 * {@code readLoop} is visible.</p>
 *
 * <p>The allocator is built with the shipped defaults except that it caches for every thread. Netty 4.2 caches
 * only for its own event-loop threads by default, and JMH's threads are not those, so
 * {@link PooledByteBufAllocator#DEFAULT} here would never use a thread cache and both arms would take the arena
 * path, unlike a server's event loops.</p>
 *
 * <p>When the last read fills its buffer the loop reads once more, and that read finds nothing: its buffer is
 * taken and released too (a 256 KiB message is five 64 KiB reads, or nine 32 KiB ones).</p>
 *
 * <p>Not covered: the socket itself, TLS, and contention between event loops for one arena (each thread here has an arena of its own, as each event loop has while arenas outnumber them).
 * {@code gc.alloc.rate.norm} counts heap allocation only: the buffer wrappers and the composite, not the
 * direct memory.</p>
 *
 * <pre>./run.sh -prof gc BinaryReadBufferBenchmark</pre>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BinaryReadBufferBenchmark {

    /** Size of each read's buffer: the thread-cache limit, and the binary read size. */
    @Param({"32768", "65536"})
    public int readBufferSize;

    /** Bytes in one message: one read's worth, and the most one gathered message holds. */
    @Param({"16384", "262144"})
    public int messageBytes;

    private PooledByteBufAllocator allocator;
    private byte[] source;

    @Setup(Level.Trial)
    public void setup() {
        allocator = new PooledByteBufAllocator(
            true,
            PooledByteBufAllocator.defaultNumHeapArena(),
            PooledByteBufAllocator.defaultNumDirectArena(),
            PooledByteBufAllocator.defaultPageSize(),
            PooledByteBufAllocator.defaultMaxOrder(),
            PooledByteBufAllocator.defaultSmallCacheSize(),
            PooledByteBufAllocator.defaultNormalCacheSize(),
            true
        );
        source = new byte[messageBytes];
        ThreadLocalRandom.current().nextBytes(source);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        System.out.println("[BinaryReadBufferBenchmark] readBufferSize=" + readBufferSize
            + " messageBytes=" + messageBytes
            + " maxCachedBufferCapacity=" + System.getProperty("io.netty.allocator.maxCachedBufferCapacity", "32768 (default)")
            + " usedDirectMemory=" + allocator.metric().usedDirectMemory());
    }

    @Benchmark
    public int readLoop() {
        return gatherAndRelease(true);
    }

    @Benchmark
    public int allocateOnly() {
        return gatherAndRelease(false);
    }

    private int gatherAndRelease(boolean fill) {
        ByteBuf first = null;
        CompositeByteBuf joined = null;
        for (int offset = 0; offset < messageBytes; offset += readBufferSize) {
            ByteBuf piece = allocator.ioBuffer(readBufferSize);
            int length = Math.min(readBufferSize, messageBytes - offset);
            if (fill) {
                piece.writeBytes(source, offset, length);
            } else {
                piece.writerIndex(length);
            }
            if (first == null) {
                first = piece;
            } else {
                if (joined == null) {
                    joined = allocator.compositeBuffer(Integer.MAX_VALUE).addComponent(true, first);
                }
                joined.addComponent(true, piece);
            }
        }
        if (messageBytes % readBufferSize == 0) {
            allocator.ioBuffer(readBufferSize).release();
        }
        ByteBuf message = joined != null ? joined : first;
        int readable = message.readableBytes();
        message.release();
        return readable;
    }
}
