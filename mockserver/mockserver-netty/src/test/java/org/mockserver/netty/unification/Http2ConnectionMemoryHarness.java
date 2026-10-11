package org.mockserver.netty.unification;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledDirectByteBuf;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import org.mockito.Mockito;
import org.mockserver.codec.CoalescingHttpObjectAggregator;
import org.mockserver.codec.HttpObjectAggregators;
import org.mockserver.configuration.Configuration;
import org.mockserver.dashboard.DashboardWebSocketHandler;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.HttpRequestHandler;
import org.mockserver.netty.websocketregistry.CallbackWebSocketServerHandler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Drives an HTTP/2 upload through the server's real connection codec ({@code Http2FrameCodec} with production's
 * settings and {@code Http2MultiplexHandler}) and each stream's real re-aggregating chain, with the stream
 * aggregator swapped for the {@link Variant} under test. The client side honours the server's flow-control
 * windows and its wire bytes arrive in socket reads of a chosen size, each read into buffers sized by Netty's
 * {@link AdaptiveRecvByteBufAllocator} as a socket channel's are. A DATA frame is a retained slice of the read it
 * arrived in, so a read stays allocated while any frame in it, from any stream, is held.
 * <p>
 * With {@link TrackingAllocator}s every byte array and direct buffer the reads, the codec and the aggregators
 * allocate is counted, including capacity growth, as is the most held at once.
 */
final class Http2ConnectionMemoryHarness {

    enum Variant {
        /**
         * #46: the production stream aggregator, copying runs of small pieces into 16 KiB blocks.
         */
        BLOCKS {
            @Override
            HttpObjectAggregator aggregator(int maxContentLength) {
                CoalescingHttpObjectAggregator aggregator = new CoalescingHttpObjectAggregator(maxContentLength);
                aggregator.setMaxCumulationBufferComponents(HttpObjectAggregators.streamComponentLimit(maxContentLength));
                aggregator.coalesceSmallContent();
                return aggregator;
            }
        },
        /**
         * #53's rule alone: past the limit merge only the components since the last merge.
         */
        MERGE_ONLY {
            @Override
            HttpObjectAggregator aggregator(int maxContentLength) {
                CoalescingHttpObjectAggregator aggregator = new CoalescingHttpObjectAggregator(maxContentLength);
                aggregator.setMaxCumulationBufferComponents(HttpObjectAggregators.streamComponentLimit(maxContentLength));
                aggregator.mergeNewComponentsOnly();
                return aggregator;
            }
        },
        /**
         * Netty's aggregator, consolidating the whole body each time it passes the same limit.
         */
        NETTY {
            @Override
            HttpObjectAggregator aggregator(int maxContentLength) {
                HttpObjectAggregator aggregator = new HttpObjectAggregator(maxContentLength);
                aggregator.setMaxCumulationBufferComponents(HttpObjectAggregators.streamComponentLimit(maxContentLength));
                return aggregator;
            }
        },
        /**
         * Whatever the production chain installs, untouched.
         */
        PRODUCTION {
            @Override
            HttpObjectAggregator aggregator(int maxContentLength) {
                return null;
            }
        };

        abstract HttpObjectAggregator aggregator(int maxContentLength);
    }

    /**
     * A DATA frame of {@code length} bytes on the {@code stream}th stream of the upload (0-based).
     */
    static final class Frame {
        /**
         * Not a frame: the client's socket delivers everything written so far, before the next frame is written.
         */
        static final Frame DELIVER_NOW = new Frame(DELIVER, 0);

        final int stream;
        final int length;

        Frame(int stream, int length) {
            this.stream = stream;
            this.length = length;
        }
    }

    static final class Result {
        long bodyBytes;
        long wireBytes;
        long peakHeldBytes;
        /**
         * Allocated by the stream channels: the aggregators' blocks, merges and consolidations.
         */
        long aggregatorAllocatedBytes;
        /**
         * Allocated by the connection channel beyond the reads: cumulations the frame decoder grows or replaces to
         * join a frame split across reads, and the frames it writes.
         */
        long connectionAllocatedBytes;

        double peakTimesBody() {
            return (double) peakHeldBytes / bodyBytes;
        }

        double copiedTimesBody() {
            return (double) aggregatorAllocatedBytes / bodyBytes;
        }
    }

    private static final int DELIVER = -1;
    private static final int FIRST_STREAM_ID = 3;
    private static final int FRAME_HEADER_BYTES = 9;
    private static final int DEFAULT_WINDOW = 65_535;

    private final Configuration configuration;
    private final Variant variant;
    private final int readBytes;
    private final ByteBufAllocator readAllocator;
    private final ByteBufAllocator connectionAllocator;
    private final ByteBufAllocator streamAllocator;
    private final Supplier<Long> sampler;

    private final EmbeddedChannel connection = new EmbeddedChannel();
    // a socket channel's: adaptive, up to 16 reads per read event
    private final RecvByteBufAllocator.Handle readHandle = new AdaptiveRecvByteBufAllocator().maxMessagesPerRead(16).newHandle();
    private final EmbeddedChannel encoder = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
    private final DefaultHttp2FrameWriter frameWriter = new DefaultHttp2FrameWriter();
    private final ByteBuf pendingWire = Unpooled.buffer();
    private final ByteBuf serverWire = Unpooled.buffer();
    private final Map<Integer, Integer> streamWindows = new HashMap<>();
    private final Map<Integer, byte[]> received = new HashMap<>();
    private final List<Throwable> failures = new ArrayList<>();
    // a stub's last invocation holds its own context, so Mockito keeps it, and the whole connection, until cleared
    private final List<Object> stubs = new ArrayList<>();
    private final SslContext serverTls;
    private final SslContext clientTls;
    private final boolean coalesceRecords;
    private int connectionWindow = DEFAULT_WINDOW;
    private int initialStreamWindow = DEFAULT_WINDOW;
    private long wireBytes;
    private long peakSampled;

    /**
     * @param readBytes how many wire bytes each socket read event delivers
     * @param sampler   for an allocator that cannot count frees itself, a sample of what is held, taken after
     *                  every read; null otherwise
     */
    Http2ConnectionMemoryHarness(Configuration configuration, Variant variant, int readBytes,
                                 ByteBufAllocator readAllocator, ByteBufAllocator connectionAllocator,
                                 ByteBufAllocator streamAllocator, Supplier<Long> sampler) {
        this(configuration, variant, readBytes, readAllocator, connectionAllocator, streamAllocator, sampler, null, false);
    }

    /**
     * With a {@code tlsProvider}, the connection is behind TLS, as an {@code h2} connection over TLS is: the server's
     * {@code SslHandler} from that provider, built as {@code SniHandler} builds it, reads the socket and hands the
     * frame codec what it decrypts in buffers of its own, so a DATA frame is a slice of such a buffer instead of a
     * read. The client either flushes each frame into records of its own or, with {@code coalesceRecords}, writes
     * everything until the next delivery into records of up to 16 KiB, so a frame shares its records with the frames
     * before and after it.
     */
    Http2ConnectionMemoryHarness(Configuration configuration, Variant variant, int readBytes,
                                 ByteBufAllocator readAllocator, ByteBufAllocator connectionAllocator,
                                 ByteBufAllocator streamAllocator, Supplier<Long> sampler,
                                 SslProvider tlsProvider, boolean coalesceRecords) {
        this.configuration = configuration;
        this.variant = variant;
        this.readBytes = readBytes;
        this.readAllocator = readAllocator;
        this.connectionAllocator = connectionAllocator;
        this.streamAllocator = streamAllocator;
        this.sampler = sampler;
        connection.config().setAllocator(connectionAllocator);
        readHandle.reset(connection.config());
        this.coalesceRecords = coalesceRecords;
        serverTls = tlsProvider == null ? null : serverTlsContext(tlsProvider);
        clientTls = tlsProvider == null ? null : clientTlsContext();
        if (tlsProvider != null) {
            connection.pipeline().addLast("ssl", serverTls.newHandler(connectionAllocator));
            encoder.pipeline().addFirst("ssl", clientTls.newHandler(encoder.alloc()));
        }
        connection.pipeline().addLast(
            Http2RequestHeaderLimit.frameCodecBuilder(new MockServerLogger(Http2ConnectionMemoryHarness.class))
                // as PortUnificationHandler.switchToHttp2Multiplex builds it
                .validateHeaders(false)
                .initialSettings(Http2RequestHeaderLimit.serverSettings(configuration)
                    .maxConcurrentStreams(PortUnificationHandler.HTTP2_MAX_CONCURRENT_STREAMS)
                    .maxFrameSize(configuration.maxRequestBodySize() < Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND
                        ? Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND
                        : Math.min(configuration.maxRequestBodySize(), Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND)))
                .build(),
            new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                @Override
                protected void initChannel(Http2StreamChannel stream) {
                    initStream(stream);
                }
            })
        );
    }

    private static SslContext serverTlsContext(SslProvider provider) {
        try {
            return SslContextBuilder.forServer(ServerCertificate.INSTANCE.certificate(), ServerCertificate.INSTANCE.privateKey())
                .sslProvider(provider)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .build();
        } catch (Exception e) {
            throw new AssertionError("could not build the server's TLS context", e);
        }
    }

    private static SslContext clientTlsContext() {
        try {
            return SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(SslProvider.JDK)
                .build();
        } catch (Exception e) {
            throw new AssertionError("could not build the client's TLS context", e);
        }
    }

    private void handshake() {
        deliverPending();
        if (!encoder.pipeline().get(SslHandler.class).handshakeFuture().isSuccess()
            || !connection.pipeline().get(SslHandler.class).handshakeFuture().isSuccess()) {
            throw new AssertionError("the TLS handshake did not complete", encoder.pipeline().get(SslHandler.class).handshakeFuture().cause());
        }
    }

    private void initStream(Http2StreamChannel stream) {
        stream.config().setAllocator(streamAllocator);
        // fresh stubs per stream, since a mock's isSharable() is false
        Http2MultiplexChildInitializer.installReAggregatingChain(
            stream.pipeline(), configuration, new MockServerLogger(Http2ConnectionMemoryHarness.class), false, null, stream,
            stub(CallbackWebSocketServerHandler.class), stub(DashboardWebSocketHandler.class), null,
            stub(TraceContextHandler.class), null, null, null, stub(HttpRequestHandler.class)
        );
        String aggregatorName = stream.pipeline().context(HttpObjectAggregator.class).name();
        HttpObjectAggregator replacement = variant.aggregator(configuration.maxRequestBodySize());
        if (replacement != null) {
            stream.pipeline().replace(aggregatorName, aggregatorName, replacement);
        }
        int streamId = stream.stream().id();
        stream.pipeline().addAfter(aggregatorName, "capture", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof FullHttpRequest) {
                    FullHttpRequest request = (FullHttpRequest) msg;
                    try {
                        received.put(streamId, ByteBufUtil.getBytes(request.content()));
                    } finally {
                        request.release();
                    }
                    // answer, as the request handler would, so the stream closes and stops counting as concurrent
                    ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER));
                } else {
                    ReferenceCountUtil.release(msg);
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                failures.add(cause);
            }
        });
    }

    /**
     * A mock that records nothing: the handlers after the capture see every read-complete event.
     */
    private <T> T stub(Class<T> type) {
        T stub = mock(type, withSettings().stubOnly());
        stubs.add(stub);
        return stub;
    }

    /**
     * Uploads one request per stream, the frames sent in the order given, each stream's body ending with its last
     * frame; each request is released as soon as it is complete, as the request handler does once it has read it.
     */
    Result upload(List<Frame> frames, int streams) {
        Result result = new Result();
        int[] remaining = new int[streams];
        for (Frame frame : frames) {
            if (frame.stream != DELIVER) {
                remaining[frame.stream] += frame.length;
            }
        }
        byte[][] bodies = new byte[streams][];
        int[] offsets = new int[streams];
        for (int stream = 0; stream < streams; stream++) {
            bodies[stream] = body(stream, remaining[stream]);
            result.bodyBytes += remaining[stream];
        }
        try {
            ChannelHandlerContext ctx = encoder.pipeline().lastContext();
            // over TLS the client's SslHandler holds these until its handshake completes
            ctx.write(Http2CodecUtil.connectionPrefaceBuf());
            frameWriter.writeSettings(ctx, new Http2Settings(), ctx.newPromise());
            collectClientFrames();
            if (clientTls != null) {
                handshake();
            }
            boolean[] opened = new boolean[streams];
            for (Frame frame : frames) {
                if (frame.stream == DELIVER) {
                    deliverPending();
                    continue;
                }
                int streamId = FIRST_STREAM_ID + 2 * frame.stream;
                if (!opened[frame.stream]) {
                    opened[frame.stream] = true;
                    streamWindows.put(streamId, initialStreamWindow);
                    DefaultHttp2Headers headers = new DefaultHttp2Headers();
                    headers.method("POST").path("/upload/" + frame.stream).scheme("http").authority("localhost");
                    frameWriter.writeHeaders(ctx, streamId, headers, 0, false, ctx.newPromise());
                    collectUnlessCoalescing();
                }
                while (frame.length > connectionWindow || frame.length > streamWindows.get(streamId)) {
                    int before = connectionWindow + streamWindows.get(streamId);
                    deliverPending();
                    if (connectionWindow + streamWindows.get(streamId) == before) {
                        throw new AssertionError("flow control stalled on stream " + streamId + ": frame " + frame.length
                            + ", connection window " + connectionWindow + ", stream window " + streamWindows.get(streamId));
                    }
                }
                connectionWindow -= frame.length;
                streamWindows.merge(streamId, -frame.length, Integer::sum);
                remaining[frame.stream] -= frame.length;
                ByteBuf data = Unpooled.wrappedBuffer(bodies[frame.stream], offsets[frame.stream], frame.length);
                offsets[frame.stream] += frame.length;
                frameWriter.writeData(ctx, streamId, data, 0, remaining[frame.stream] == 0, ctx.newPromise());
                collectUnlessCoalescing();
            }
            deliverPending();
            if (!failures.isEmpty()) {
                throw new AssertionError("the stream chain raised an exception", failures.get(0));
            }
            for (int stream = 0; stream < streams; stream++) {
                byte[] body = received.get(FIRST_STREAM_ID + 2 * stream);
                if (body == null) {
                    throw new AssertionError("stream " + stream + " produced no request");
                }
                if (!Arrays.equals(body, bodies[stream])) {
                    throw new AssertionError("stream " + stream + " body differs from what was sent");
                }
            }
        } finally {
            close();
        }
        result.wireBytes = wireBytes;
        result.peakHeldBytes = peakSampled;
        for (ByteBufAllocator allocator : new ByteBufAllocator[]{readAllocator, connectionAllocator, streamAllocator}) {
            if (allocator instanceof TrackingAllocator) {
                result.peakHeldBytes = Math.max(result.peakHeldBytes, ((TrackingAllocator) allocator).usage.peakLiveBytes);
            } else if (allocator instanceof SampledPooledAllocator) {
                // sampled at every allocation too, not only between reads
                result.peakHeldBytes = Math.max(result.peakHeldBytes, ((SampledPooledAllocator) allocator).peakHeldBytes);
            }
        }
        if (streamAllocator instanceof TrackingAllocator) {
            result.aggregatorAllocatedBytes = ((TrackingAllocator) streamAllocator).allocatedBytes;
        }
        if (connectionAllocator instanceof TrackingAllocator) {
            result.connectionAllocatedBytes = ((TrackingAllocator) connectionAllocator).allocatedBytes;
        }
        return result;
    }

    private void close() {
        try {
            connection.finishAndReleaseAll();
        } finally {
            encoder.finishAndReleaseAll();
            pendingWire.release();
            serverWire.release();
            ReferenceCountUtil.release(serverTls);
            ReferenceCountUtil.release(clientTls);
            stubs.forEach(Mockito.framework()::clearInlineMock);
        }
    }

    private void collectUnlessCoalescing() {
        if (!coalesceRecords) {
            collectClientFrames();
        }
    }

    private void collectClientFrames() {
        encoder.flushOutbound();
        ByteBuf frame;
        while ((frame = encoder.readOutbound()) != null) {
            pendingWire.writeBytes(frame);
            frame.release();
        }
    }

    /**
     * Sends everything written so far in socket reads of {@link #readBytes}, each read event filling buffers the
     * size the adaptive allocator guesses, up to its messages per read, as a socket channel's read loop does; then
     * reads what the server wrote back.
     */
    private void deliverPending() {
        collectClientFrames();
        while (pendingWire.isReadable()) {
            int event = Math.min(readBytes, pendingWire.readableBytes());
            readHandle.reset(connection.config());
            while (event > 0) {
                ByteBuf read = readHandle.allocate(readAllocator);
                int bytes = Math.min(read.writableBytes(), event);
                readHandle.attemptedBytesRead(read.writableBytes());
                read.writeBytes(pendingWire, bytes);
                readHandle.lastBytesRead(bytes);
                readHandle.incMessagesRead(1);
                event -= bytes;
                wireBytes += bytes;
                connection.pipeline().fireChannelRead(read);
                sample();
                if (!readHandle.continueReading()) {
                    readHandle.readComplete();
                    connection.flushInbound();
                    readHandle.reset(connection.config());
                }
            }
            readHandle.readComplete();
            connection.flushInbound();
            connection.runPendingTasks();
            sample();
            readServerFrames();
            collectClientFrames();
            pendingWire.discardReadBytes();
        }
    }

    private void sample() {
        if (sampler != null) {
            peakSampled = Math.max(peakSampled, sampler.get());
        }
    }

    /**
     * Applies the server's WINDOW_UPDATE and SETTINGS frames to the client's view of its windows, acknowledging the
     * SETTINGS, and fails on RST_STREAM or GOAWAY.
     */
    private void readServerFrames() {
        Object written;
        while ((written = connection.readOutbound()) != null) {
            if (written instanceof ByteBuf && clientTls != null) {
                // the client's SslHandler decrypts it, taking ownership
                encoder.writeInbound(written);
                continue;
            }
            if (written instanceof ByteBuf) {
                serverWire.writeBytes((ByteBuf) written);
            }
            ReferenceCountUtil.release(written);
        }
        Object decrypted;
        while ((decrypted = encoder.readInbound()) != null) {
            if (decrypted instanceof ByteBuf) {
                serverWire.writeBytes((ByteBuf) decrypted);
            }
            ReferenceCountUtil.release(decrypted);
        }
        ChannelHandlerContext ctx = encoder.pipeline().lastContext();
        while (serverWire.readableBytes() >= FRAME_HEADER_BYTES) {
            int length = serverWire.getUnsignedMedium(serverWire.readerIndex());
            if (serverWire.readableBytes() < FRAME_HEADER_BYTES + length) {
                break;
            }
            int type = serverWire.getUnsignedByte(serverWire.readerIndex() + 3);
            int flags = serverWire.getUnsignedByte(serverWire.readerIndex() + 4);
            int streamId = serverWire.getInt(serverWire.readerIndex() + 5) & Integer.MAX_VALUE;
            int payload = serverWire.readerIndex() + FRAME_HEADER_BYTES;
            if (type == 0x8) {
                int increment = serverWire.getInt(payload) & Integer.MAX_VALUE;
                if (streamId == 0) {
                    connectionWindow += increment;
                } else {
                    streamWindows.merge(streamId, increment, Integer::sum);
                }
            } else if (type == 0x4 && (flags & 0x1) == 0) {
                for (int setting = payload; setting < payload + length; setting += 6) {
                    if (serverWire.getUnsignedShort(setting) == 0x4) {
                        int newInitial = serverWire.getInt(setting + 2);
                        int delta = newInitial - initialStreamWindow;
                        initialStreamWindow = newInitial;
                        streamWindows.replaceAll((id, window) -> window + delta);
                    }
                }
                frameWriter.writeSettingsAck(ctx, ctx.newPromise());
            } else if (type == 0x3 || type == 0x7) {
                throw new AssertionError((type == 0x3 ? "RST_STREAM on stream " + streamId : "GOAWAY")
                    + " error code " + serverWire.getInt(payload + (type == 0x7 ? 4 : 0))
                    + (failures.isEmpty() ? "" : ", first failure: " + failures.get(0)));
            }
            serverWire.skipBytes(FRAME_HEADER_BYTES + length);
        }
        serverWire.discardReadBytes();
    }

    /**
     * Uploads through a fresh connection at the default configuration, every buffer from a counting allocator, and
     * checks every one was released.
     */
    static Result tracked(Variant variant, int readSize, List<Frame> frames, int streams) {
        return trackedOverTls(variant, null, false, readSize, frames, streams);
    }

    /**
     * As {@link #tracked}, over TLS from the given server provider (none: cleartext); see the constructor.
     */
    static Result trackedOverTls(Variant variant, SslProvider provider, boolean coalesceRecords, int readSize, List<Frame> frames, int streams) {
        Usage usage = new Usage();
        TrackingAllocator reads = new TrackingAllocator(usage);
        TrackingAllocator connection = new TrackingAllocator(usage);
        TrackingAllocator stream = new TrackingAllocator(usage);
        Result result = new Http2ConnectionMemoryHarness(Configuration.configuration(), variant, readSize, reads, connection, stream, null,
            provider, coalesceRecords)
            .upload(frames, streams);
        if (reads.unreleased() + connection.unreleased() + stream.unreleased() != 0) {
            throw new AssertionError("a buffer was not released");
        }
        return result;
    }

    /**
     * Generated once: a key pair takes a while.
     */
    private static final class ServerCertificate {
        static final SelfSignedCertificate INSTANCE = generate();

        private static SelfSignedCertificate generate() {
            try {
                return new SelfSignedCertificate();
            } catch (Exception e) {
                throw new AssertionError("could not generate a certificate", e);
            }
        }
    }

    /**
     * Byte-fair interleaving, as HTTP/2 clients schedule DATA across streams: the next frame is always from the
     * stream that has sent the smallest share of its body, so the streams finish together.
     */
    static List<Frame> fair(int[] bodyBytes, int[][] patterns) {
        List<List<Frame>> perStream = new ArrayList<>();
        for (int stream = 0; stream < bodyBytes.length; stream++) {
            List<Frame> frames = new ArrayList<>();
            for (Frame frame : oneStream(bodyBytes[stream], patterns[stream])) {
                frames.add(new Frame(stream, frame.length));
            }
            perStream.add(frames);
        }
        List<Frame> frames = new ArrayList<>();
        int[] next = new int[bodyBytes.length];
        long[] sent = new long[bodyBytes.length];
        while (true) {
            int pick = -1;
            for (int stream = 0; stream < bodyBytes.length; stream++) {
                if (next[stream] < perStream.get(stream).size()
                    && (pick < 0 || sent[stream] * (double) bodyBytes[pick] < sent[pick] * (double) bodyBytes[stream])) {
                    pick = stream;
                }
            }
            if (pick < 0) {
                return frames;
            }
            Frame frame = perStream.get(pick).get(next[pick]++);
            sent[pick] += frame.length;
            frames.add(new Frame(pick, frame.length));
        }
    }

    static byte[] body(int stream, int length) {
        byte[] body = new byte[length];
        for (int i = 0; i < length; i++) {
            body[i] = (byte) ((i * 31 + i / 251 + stream * 17) % 256);
        }
        return body;
    }

    /**
     * Frames of the given sizes on one stream, cycling through the pattern until {@code bodyBytes} are sent.
     */
    static List<Frame> oneStream(int bodyBytes, int... pattern) {
        List<Frame> frames = new ArrayList<>();
        int sent = 0;
        for (int piece = 0; sent < bodyBytes; piece++) {
            int length = Math.min(pattern[piece % pattern.length], bodyBytes - sent);
            frames.add(new Frame(0, length));
            sent += length;
        }
        return frames;
    }

    /**
     * Each frame of stream 0, its sizes cycling through {@code pattern}, arrives in a socket read of its own, about
     * 32 KiB, filled by a whole request on a stream of its own that is answered and released at once: a client
     * pinning reads through one stream's held frames. Uses {@code cycles + 1} streams.
     */
    static List<Frame> pinning(int cycles, int... pattern) {
        List<Frame> frames = new ArrayList<>();
        for (int cycle = 0; cycle < cycles; cycle++) {
            int pieceBytes = pattern[cycle % pattern.length];
            frames.add(new Frame(0, pieceBytes));
            // the server tops the connection window up only once half of it is used, so a cycle can use half
            frames.add(new Frame(1 + cycle, DEFAULT_WINDOW / 2 - pieceBytes - 128));
            frames.add(Frame.DELIVER_NOW);
        }
        return frames;
    }

    /**
     * The frames, each delivered in a socket read of its own.
     */
    static List<Frame> eachInItsOwnRead(List<Frame> frames) {
        List<Frame> delivered = new ArrayList<>();
        for (Frame frame : frames) {
            delivered.add(frame);
            delivered.add(Frame.DELIVER_NOW);
        }
        return delivered;
    }

    /**
     * Pairs of (count, size): {@code count} pieces of {@code size} bytes, in order.
     */
    static int[] mix(int... countsAndSizes) {
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < countsAndSizes.length; i += 2) {
            for (int piece = 0; piece < countsAndSizes[i]; piece++) {
                sizes.add(countsAndSizes[i + 1]);
            }
        }
        return sizes.stream().mapToInt(Integer::intValue).toArray();
    }

    static final class Usage {
        long liveBytes;
        long peakLiveBytes;
    }

    /**
     * Counts every byte array or direct buffer it allocates, including those a buffer allocates to grow, and the
     * most allocated and not yet freed at once across every allocator sharing its {@link Usage}; composites and
     * derived buffers own no memory of their own. A buffer the leak detector samples is returned in its wrapper, as
     * the pooled allocator returns it (every buffer at the paranoid level this module's tests run at).
     */
    static final class TrackingAllocator extends AbstractByteBufAllocator {
        final Usage usage;
        long allocatedBytes;
        private long unreleased;

        TrackingAllocator(Usage usage) {
            super(true);
            this.usage = usage;
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            allocated(initialCapacity);
            ByteBuf buffer = new UnpooledHeapByteBuf(this, initialCapacity, maxCapacity) {
                @Override
                public ByteBuf capacity(int newCapacity) {
                    return grown(this, newCapacity, () -> super.capacity(newCapacity));
                }

                @Override
                protected void deallocate() {
                    usage.liveBytes -= capacity();
                    unreleased--;
                    super.deallocate();
                }
            };
            unreleased++;
            return toLeakAwareBuffer(buffer);
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            allocated(initialCapacity);
            ByteBuf buffer = new UnpooledDirectByteBuf(this, ByteBuffer.allocateDirect(initialCapacity), maxCapacity) {
                @Override
                public ByteBuf capacity(int newCapacity) {
                    return grown(this, newCapacity, () -> super.capacity(newCapacity));
                }

                @Override
                protected void deallocate() {
                    usage.liveBytes -= capacity();
                    unreleased--;
                    super.deallocate();
                }
            };
            // the wrapping constructor treats the buffer as already written
            buffer.clear();
            unreleased++;
            return toLeakAwareBuffer(buffer);
        }

        /**
         * A buffer grows (or shrinks) by allocating the new capacity, copying, then freeing the old.
         */
        private ByteBuf grown(ByteBuf buffer, int newCapacity, Supplier<ByteBuf> resize) {
            int oldCapacity = buffer.capacity();
            if (newCapacity == oldCapacity) {
                return buffer;
            }
            allocated(newCapacity);
            ByteBuf resized = resize.get();
            usage.liveBytes -= oldCapacity;
            return resized;
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

        long unreleased() {
            return unreleased;
        }
    }

    /**
     * Hands out buffers from a pooled allocator and samples what they hold: the sum of the capacities of those
     * still referenced, whenever {@link #held()} is called. A pooled buffer frees into its arena without telling
     * anyone, so this is sampled rather than counted.
     */
    static final class SampledPooledAllocator extends AbstractByteBufAllocator {
        private final ByteBufAllocator pooled;
        private final Map<ByteBuf, Boolean> live = new IdentityHashMap<>();
        long peakHeldBytes;

        SampledPooledAllocator(ByteBufAllocator pooled) {
            super(true);
            this.pooled = pooled;
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            return track(pooled.heapBuffer(initialCapacity, maxCapacity));
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            return track(pooled.directBuffer(initialCapacity, maxCapacity));
        }

        private ByteBuf track(ByteBuf buffer) {
            live.put(buffer, Boolean.TRUE);
            held();
            return buffer;
        }

        long held() {
            long held = 0;
            live.keySet().removeIf(buffer -> buffer.refCnt() == 0);
            for (ByteBuf buffer : live.keySet()) {
                held += buffer.capacity();
            }
            peakHeldBytes = Math.max(peakHeldBytes, held);
            return held;
        }

        @Override
        public boolean isDirectBufferPooled() {
            return true;
        }
    }
}
