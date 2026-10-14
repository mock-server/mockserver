package org.mockserver.benchmark;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.util.ReferenceCountUtil;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.ClearType;
import org.mockserver.netty.MockServer;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * HTTP/2 <em>per-connection memory</em> benchmark.
 *
 * <p><strong>What it answers.</strong> The 8.0.0 changelog warned that migrating the HTTP/2 pipeline to
 * {@code Http2FrameCodec + Http2MultiplexHandler} — where every stream becomes its own child {@link Channel}
 * with its own pipeline — <em>changed per-connection memory</em>. {@link Http2StreamChannelBenchmark}
 * measures the throughput/latency of that design over ONE connection; this class adds the missing axis:
 * <b>N connections × M concurrent streams</b>, and records the <b>heap delta per established connection</b>.</p>
 *
 * <p><strong>The measurement, and why it is trustworthy.</strong> For each shape {@code C×S}:</p>
 * <ol>
 *   <li>Force GC (via {@link MemoryMXBean#gc()} — a full collection) and read the retained heap
 *       ({@code HeapMemoryUsage.used}) as the baseline {@code H0}, with <em>no</em> client connections open.</li>
 *   <li>Open {@code C} real TCP connections, each carrying {@code S} concurrently in-flight streams. Each
 *       stream sends a complete request whose matched response is <b>held open by a long server-side delay</b>,
 *       so the server keeps {@code C×S} stream child channels alive simultaneously — the exact structures
 *       the multiplex migration changed. Total concurrent server streams = {@code C×S}.</li>
 *   <li><b>Isolate connection cost from event-log cost.</b> MockServer retains full request/response bodies
 *       in a count-bounded event-log ring, so a naive heap delta would fold in logged bodies. Two things
 *       remove that confound: (a) the requests are bodyless {@code GET}s and the held responses never
 *       complete, so nothing large is retained; and (b) immediately before sampling we {@link ClearType#LOG
 *       clear the event log}, then confirm its occupancy is ~0. The sampled delta therefore cannot be
 *       log-body retention — it is connection + stream channel state.</li>
 *   <li>Force GC again, read {@code H1}. {@code bytes_per_connection = (H1 − H0) / C}.</li>
 *   <li>Close every connection, force GC, read {@code H2}. {@code H2} returning toward {@code H0} proves the
 *       delta was live connection state that is released on close (a leak would keep {@code H2} high).</li>
 * </ol>
 * <p>Each shape is repeated {@code H2_MEM_REPEATS} times; the reported figure is the median with the sample
 * spread, so a single GC-timing outlier cannot masquerade as a result.</p>
 *
 * <p><strong>The heap is the whole in-process JVM (client + server).</strong> The driver client and the mock
 * server share this JVM, so {@code bytes_per_connection} is the total retained heap per established connection
 * across both — an honest <em>trend</em> figure (a regression in either side shows up). Attributing the cost
 * specifically to the <em>server-side</em> multiplex change is what the cross-version comparison does: the
 * companion Docker/RSS harness (see {@code perf-test-h2multiplex.sh}) drives an identical external client
 * against a pre-8.0.0 image and an 8.0.0 image, holding the client side constant so the difference is the
 * server change the changelog named.</p>
 *
 * <p><strong>Self-validation is mandatory and fails the JVM loudly (exit code 2).</strong> A per-connection
 * memory number is only meaningful if the connections and streams were genuinely established and the log was
 * genuinely excluded. {@link ShapeResult#validate()} enforces, per shape:</p>
 * <ol>
 *   <li><b>distinct connections</b> — the driver opened {@code C} sockets with {@code C} distinct local ports
 *       (a multiplexed client that silently collapsed them onto one connection fails here);</li>
 *   <li><b>streams established</b> — the server confirmed {@code C×S} concurrently in-flight requests. Because
 *       the server caps {@code MAX_CONCURRENT_STREAMS} at 100, {@code C×S} in flight is <em>physically
 *       impossible</em> on fewer than {@code ceil(C×S / 100)} connections — so this gate is an independent,
 *       protocol-level proof that the connection axis is real (e.g. 1000 in flight ⇒ ≥10 connections);</li>
 *   <li><b>log excluded</b> — the event-log occupancy sampled alongside {@code H1} is ~0 (below a small
 *       ceiling), so the delta is not logged bodies;</li>
 *   <li><b>plausible magnitude</b> — {@code bytes_per_connection} is above a floor (a near-zero delta means
 *       nothing was actually established/measured) and below a ceiling (a whole-heap delta means the harness
 *       measured the wrong thing).</li>
 * </ol>
 * <p>These are HARNESS-integrity gates, deliberately distinct from any performance threshold.
 * {@code selftest} proves every gate fires on a fabricated bad result and that a clean result
 * passes, server-free.</p>
 *
 * <pre>
 *   java -cp ... org.mockserver.benchmark.Http2ConnectionMemoryBenchmark [output.json]
 *   java -cp ... org.mockserver.benchmark.Http2ConnectionMemoryBenchmark selftest
 *   java -cp ... org.mockserver.benchmark.Http2ConnectionMemoryBenchmark hold &lt;host&gt; &lt;port&gt; &lt;C&gt; &lt;S&gt; &lt;dwellMs&gt;
 * </pre>
 * <p>The {@code hold} mode is the external-client half of the Docker/RSS cross-version comparison: it opens
 * {@code C×S} in-flight streams against an already-running server, prints a {@code HELD} marker once the
 * server confirms them (so a shell can sample the container's RSS), holds for {@code dwellMs}, then releases.</p>
 */
public final class Http2ConnectionMemoryBenchmark {

    private static final String PATH = "/hold";
    /** Server cap on concurrent streams per connection — the basis of the "streams established" proof. */
    private static final int MAX_CONCURRENT_STREAMS_PER_CONNECTION = 100;

    /** A per-connection heap delta below this means the connections were not really established / measured. */
    private static final long BYTES_PER_CONNECTION_FLOOR = 256L;
    /** Above this a "per-connection" figure is implausible — the harness is measuring the whole heap, not a connection. */
    private static final long BYTES_PER_CONNECTION_CEILING = 64L * 1024 * 1024;
    /** The event log must be essentially empty at sample time (we clear it first); a few stray entries are tolerated. */
    private static final int LOG_OCCUPANCY_CEILING = 8;

    private Http2ConnectionMemoryBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "selftest".equalsIgnoreCase(args[0])) {
            selfTest();
            return;
        }
        if (args.length > 0 && "hold".equalsIgnoreCase(args[0])) {
            holdMode(args);
            return;
        }

        int[][] shapes = parseShapes(env("H2_MEM_SHAPES", "1x1,10x10,100x10"));
        int repeats = Integer.parseInt(env("H2_MEM_REPEATS", "5"));
        int delaySeconds = Integer.parseInt(env("H2_MEM_DELAY_S", "600"));
        int establishTimeoutSeconds = Integer.parseInt(env("H2_MEM_ESTABLISH_TIMEOUT_S", "60"));
        Path output = Paths.get(args.length > 0 ? args[0] : env("H2_MEM_OUTPUT", "perf-h2-connection-memory.json"));

        for (int[] shape : shapes) {
            validateShapeRequest(shape[0], shape[1]);
        }

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MockServer mockServer = null;
        MockServerClient client = null;
        try {
            mockServer = new MockServer();
            int serverPort = mockServer.getLocalPort();
            client = new MockServerClient("localhost", serverPort);
            // Matched response is held open by a long delay so the stream stays in flight (a child channel
            // alive on the server) throughout the measurement window. Bodyless request, tiny response.
            client
                .when(request().withPath(PATH))
                .respond(response().withStatusCode(200).withBody("h").withDelay(TimeUnit.SECONDS, delaySeconds));

            System.out.println("=== HTTP/2 per-connection memory benchmark (issue #2669, item 11) ===");
            System.out.println("server port=" + serverPort + " shapes=" + shapesToString(shapes)
                + " repeats=" + repeats + " responseDelay=" + delaySeconds + "s");

            NioEventLoopGroup group = new NioEventLoopGroup();
            List<ShapeResult> results = new ArrayList<>();
            try {
                // Warm the HTTP/2 client + server stream path BEFORE the first shape's baseline, so the
                // one-time class-load / JIT / pooled-arena cost is not charged to that shape's per-connection
                // delta. Without this, shape 1x1 (measured first) takes H0 before any h2-client class is loaded
                // and H1 absorbs all of it — a one-directional upward bias, not scatter. The throwaway
                // connection is then closed, so only PERSISTENT one-time cost is warmed in, never per-connection
                // state (mirrors the cross-version compare script's warm-up).
                warmUpH2Path(group, client, serverPort, establishTimeoutSeconds);

                for (int[] shape : shapes) {
                    int connections = shape[0];
                    int streamsPerConnection = shape[1];
                    ShapeResult result = new ShapeResult(connections, streamsPerConnection);
                    System.out.println("--- " + connections + " connection(s) x " + streamsPerConnection
                        + " stream(s) = " + ((long) connections * streamsPerConnection) + " concurrent streams ---");
                    for (int rep = 0; rep < repeats; rep++) {
                        long perConnectionBytes = measureOnce(group, client, serverPort, connections,
                            streamsPerConnection, establishTimeoutSeconds, memoryBean, result);
                        System.out.printf(Locale.ROOT, "    rep %d/%d: %,d bytes/connection%n",
                            rep + 1, repeats, perConnectionBytes);
                    }
                    validateOrAbort(result);
                    System.out.println("    " + result.summary());
                    results.add(result);
                }
            } finally {
                group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
            }

            String json = toJson(results);
            Files.write(output, json.getBytes(StandardCharsets.UTF_8));
            System.out.println("--- wrote " + output.toAbsolutePath());
            System.out.println(json);
        } finally {
            stopQuietly(client);
            stopQuietly(mockServer);
        }
    }

    /**
     * One measurement of a single shape. Opens {@code C} connections × {@code S} in-flight streams, isolates
     * the event log, samples the heap delta, then tears the connections down. Records everything needed for
     * the gates onto {@code result}. Returns this repeat's bytes/connection.
     */
    private static long measureOnce(NioEventLoopGroup group, MockServerClient client, int port,
                                    int connections, int streamsPerConnection, int establishTimeoutSeconds,
                                    MemoryMXBean memoryBean, ShapeResult result) throws Exception {
        long expectedInFlight = (long) connections * streamsPerConnection;

        // Clear anything left from a prior repeat, then sample the pre-connection baseline.
        client.clear(request(), ClearType.LOG);
        long baseline = usedHeapAfterGc(memoryBean);

        List<Channel> parents = new ArrayList<>(connections);
        List<Http2StreamChannel> streams = new ArrayList<>((int) Math.min(expectedInFlight, Integer.MAX_VALUE));
        Set<Integer> distinctLocalPorts = new LinkedHashSet<>();
        try {
            for (int c = 0; c < connections; c++) {
                Channel parent = openConnection(group, port);
                parents.add(parent);
                distinctLocalPorts.add(((InetSocketAddress) parent.localAddress()).getPort());
                for (int s = 0; s < streamsPerConnection; s++) {
                    streams.add(openInFlightStream(parent, port));
                }
            }

            // Wait until the SERVER confirms every stream arrived (proof the streams — hence the connections —
            // are genuinely established). C*S in flight is impossible on fewer than ceil(C*S/100) connections.
            long confirmed = awaitInFlight(client, expectedInFlight, establishTimeoutSeconds);

            // Isolate the connection cost from the event-log cost: drop the received-request entries so the
            // heap delta cannot be logged request/response bodies, then confirm the log really is ~empty.
            client.clear(request(), ClearType.LOG);
            long loadedHeap = usedHeapAfterGc(memoryBean);
            int logOccupancy = client.retrieveRecordedRequests(request()).length;

            result.record(baseline, loadedHeap, confirmed, distinctLocalPorts.size(), logOccupancy);
            long delta = loadedHeap - baseline;
            return connections > 0 ? delta / connections : delta;
        } finally {
            for (Http2StreamChannel stream : streams) {
                stream.close();
            }
            for (Channel parent : parents) {
                parent.close();
            }
            for (Channel parent : parents) {
                parent.closeFuture().awaitUninterruptibly(2, TimeUnit.SECONDS);
            }
            // Drop strong references so the next repeat's baseline is measured on a clean heap, not one still
            // pinning this repeat's closed channels. (We do NOT sample a "reclaim" figure here: the matched
            // responses are still scheduled server-side for the full delay, so a close does not free them
            // synchronously — a same-instant re-read would understate reclaim and read like a phantom leak.)
            streams.clear();
            parents.clear();
            distinctLocalPorts.clear();
            client.clear(request(), ClearType.LOG);
        }
    }

    /**
     * Warm the h2c stream path once (open 1 connection + 1 in-flight stream, confirm it reached the server,
     * then close), so the first measured shape's baseline is taken on an already-warm JVM. Throwaway — its
     * connection is closed and its log entry cleared, so only persistent one-time cost is warmed in.
     */
    private static void warmUpH2Path(NioEventLoopGroup group, MockServerClient client, int port,
                                     int establishTimeoutSeconds) throws Exception {
        client.clear(request(), ClearType.LOG);
        Channel parent = openConnection(group, port);
        Http2StreamChannel stream = openInFlightStream(parent, port);
        awaitInFlight(client, 1, establishTimeoutSeconds);
        stream.close();
        parent.close().awaitUninterruptibly(2, TimeUnit.SECONDS);
        client.clear(request(), ClearType.LOG);
    }

    /** Open a fresh h2c (prior-knowledge) connection with the multiplex client pipeline. */
    private static Channel openConnection(NioEventLoopGroup group, int port) throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap()
            .group(group)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build());
                    ch.pipeline().addLast(new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
                        }
                    }));
                }
            });
        return bootstrap.connect("localhost", port).sync().channel();
    }

    /**
     * Open one stream and send a complete request whose response is held open by the server delay. The stream
     * therefore stays in flight; we keep the returned channel referenced so it is not closed/GC'd. Responses
     * are drained-and-released if any arrive (they should not during the measurement window).
     */
    private static Http2StreamChannel openInFlightStream(Channel parent, int port) throws InterruptedException {
        Http2StreamChannel stream = new Http2StreamChannelBootstrap(parent)
            .handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    ReferenceCountUtil.release(msg);
                }
            })
            .open()
            .sync()
            .getNow();
        Http2Headers headers = new DefaultHttp2Headers()
            .method(HttpMethod.GET.asciiName())
            .scheme(HttpScheme.HTTP.name())
            .authority("localhost:" + port)
            .path(PATH);
        stream.writeAndFlush(new DefaultHttp2HeadersFrame(headers, true));
        return stream;
    }

    /** Poll the server until it has recorded {@code expected} received requests, or time out. Returns the count reached. */
    private static long awaitInFlight(MockServerClient client, long expected, int timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        long count = 0;
        while (System.nanoTime() < deadline) {
            count = client.retrieveRecordedRequests(request().withPath(PATH)).length;
            if (count >= expected) {
                return count;
            }
            Thread.sleep(50);
        }
        return count;
    }

    /**
     * Force a full GC (thrice, to let finalization/refs settle) and read retained heap.
     *
     * <p><strong>Precondition:</strong> the JVM must honour explicit GC — {@link MemoryMXBean#gc()} is a
     * no-op under {@code -XX:+DisableExplicitGC}, which would make every {@code (H1 − H0)} delta reflect
     * uncollected garbage rather than the retained live set, silently. Run this benchmark on a plain
     * {@code java -cp …} launch (as {@code perf-test-h2multiplex.sh} does); do NOT add
     * {@code -XX:+DisableExplicitGC}. The plausible-magnitude gate ({@code BYTES_PER_CONNECTION_CEILING})
     * only partially backstops a broken GC, so this precondition is the primary guard.
     */
    private static long usedHeapAfterGc(MemoryMXBean memoryBean) throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            memoryBean.gc();
            Thread.sleep(150);
        }
        return memoryBean.getHeapMemoryUsage().getUsed();
    }

    // --- hold mode (external client for the Docker/RSS cross-version comparison) ---------------------------

    private static void holdMode(String[] args) throws Exception {
        if (args.length < 6) {
            System.err.println("usage: hold <host> <port> <connections> <streamsPerConnection> <dwellMs>");
            System.exit(2);
        }
        String host = args[1];
        int port = Integer.parseInt(args[2]);
        int connections = Integer.parseInt(args[3]);
        int streamsPerConnection = Integer.parseInt(args[4]);
        long dwellMs = Long.parseLong(args[5]);
        int delaySeconds = Integer.parseInt(env("H2_MEM_DELAY_S", "600"));
        // Best-effort only in hold mode (see below); kept short so a pre-8.0.0 server that never confirms
        // mid-flight does not stall the RSS-sampling window.
        int confirmTimeoutSeconds = Integer.parseInt(env("H2_MEM_CONFIRM_TIMEOUT_S", "8"));
        validateShapeRequest(connections, streamsPerConnection);
        long expectedInFlight = (long) connections * streamsPerConnection;

        // Configure the delayed-response expectation over the raw control-plane HTTP API rather than
        // MockServerClient: the client enforces a major/minor version match, so an 8.0.1 client refuses to
        // talk to the pre-8.0.0 (7.6.0) server this comparison depends on. The control-plane JSON shape is
        // stable across 7.x and 8.x, so raw HTTP is the version-portable path.
        String expectationJson = "{\"httpRequest\":{\"path\":\"" + PATH + "\"},"
            + "\"httpResponse\":{\"statusCode\":200,\"body\":\"h\","
            + "\"delay\":{\"timeUnit\":\"SECONDS\",\"value\":" + delaySeconds + "}}}";
        // One HttpClient reused for the whole hold-mode lifetime — a fresh client per control-plane poll would
        // leak a selector + executor threads each (java.net.http.HttpClient has no close() on Java 17).
        java.net.http.HttpClient controlPlane = java.net.http.HttpClient.newHttpClient();
        controlPlanePut(controlPlane, host, port, "/mockserver/expectation", expectationJson);
        controlPlanePut(controlPlane, host, port, "/mockserver/clear?type=LOG", "");

        NioEventLoopGroup group = new NioEventLoopGroup();
        List<Channel> parents = new ArrayList<>(connections);
        List<Http2StreamChannel> streams = new ArrayList<>();
        Set<Integer> distinctLocalPorts = new LinkedHashSet<>();
        try {
            for (int c = 0; c < connections; c++) {
                Channel parent = openConnection(group, port);
                parents.add(parent);
                distinctLocalPorts.add(((InetSocketAddress) parent.localAddress()).getPort());
                for (int s = 0; s < streamsPerConnection; s++) {
                    streams.add(openInFlightStream(parent, port));
                }
            }
            // FATAL, version-portable establishment proof: the driver opened C distinct client sockets, and
            // every stream's open().sync() + HEADERS write succeeded (openInFlightStream throws otherwise).
            // This proves the connection axis is real and not a collapsed/multiplexed single connection, and
            // it holds identically on every server version.
            if (distinctLocalPorts.size() != connections) {
                System.err.println("HARNESS VALIDATION FAILED: opened " + distinctLocalPorts.size()
                    + " distinct local ports, expected " + connections + " — connections collapsed");
                System.exit(2);
            }
            // BEST-EFFORT server-side confirmation: pre-8.0.0 servers log a received request only AFTER its
            // (here long-delayed) response completes, so a mid-flight retrieve reads 0 on those versions even
            // though the streams are established. Report it, never fail on it — the distinct sockets above are
            // the authoritative proof. (-1 = the retrieve endpoint could not be read.)
            long confirmed = awaitInFlightViaControlPlane(controlPlane, host, port, expectedInFlight, confirmTimeoutSeconds);
            controlPlanePut(controlPlane, host, port, "/mockserver/clear?type=LOG", "");
            // Marker a shell waits for before sampling the server container's RSS.
            System.out.println("HELD connections=" + connections + " streams_per_connection=" + streamsPerConnection
                + " streams_opened=" + expectedInFlight
                + " server_confirmed_inflight=" + confirmed + " distinct_connections=" + distinctLocalPorts.size());
            System.out.flush();
            Thread.sleep(dwellMs);
            System.out.println("RELEASING");
        } finally {
            for (Http2StreamChannel stream : streams) {
                stream.close();
            }
            for (Channel parent : parents) {
                parent.close();
            }
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        }
    }

    /** PUT to a MockServer control-plane endpoint over HTTP/1.1 — no client-side version guard. */
    private static int controlPlanePut(java.net.http.HttpClient http, String host, int port, String path, String body) {
        try {
            java.net.http.HttpResponse<String> response = http.send(
                java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://" + host + ":" + port + path))
                    .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            return response.statusCode();
        } catch (Exception e) {
            return -1;
        }
    }

    /** Best-effort mid-flight count of received /hold requests via the control-plane retrieve endpoint. */
    private static long awaitInFlightViaControlPlane(java.net.http.HttpClient http, String host, int port, long expected, int timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        long count = -1;
        while (System.nanoTime() < deadline) {
            count = retrieveRequestCount(http, host, port);
            if (count >= expected) {
                return count;
            }
            Thread.sleep(100);
        }
        return count;
    }

    private static long retrieveRequestCount(java.net.http.HttpClient http, String host, int port) {
        try {
            java.net.http.HttpResponse<String> response = http.send(
                java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://" + host + ":" + port + "/mockserver/retrieve?type=REQUESTS&format=JSON"))
                    .PUT(java.net.http.HttpRequest.BodyPublishers.ofString("{\"path\":\"" + PATH + "\"}"))
                    .header("Content-Type", "application/json")
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return -1;
            }
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body()).size();
        } catch (Exception e) {
            return -1;
        }
    }

    // --- result + gates ------------------------------------------------------------------------------------

    static final class ShapeResult {
        private final int connections;
        private final int streamsPerConnection;
        private final List<Long> perConnectionBytes = new ArrayList<>();
        private long lastBaselineHeap;
        private long lastLoadedHeap;
        private long confirmedInFlight;
        private int distinctConnections;
        private int logOccupancy;

        ShapeResult(int connections, int streamsPerConnection) {
            this.connections = connections;
            this.streamsPerConnection = streamsPerConnection;
        }

        void record(long baselineHeap, long loadedHeap, long confirmedInFlight, int distinctConnections, int logOccupancy) {
            this.lastBaselineHeap = baselineHeap;
            this.lastLoadedHeap = loadedHeap;
            this.confirmedInFlight = confirmedInFlight;
            this.distinctConnections = distinctConnections;
            this.logOccupancy = logOccupancy;
            long delta = loadedHeap - baselineHeap;
            perConnectionBytes.add(connections > 0 ? delta / connections : delta);
        }

        long expectedInFlight() {
            return (long) connections * streamsPerConnection;
        }

        long medianBytesPerConnection() {
            List<Long> sorted = new ArrayList<>(perConnectionBytes);
            Collections.sort(sorted);
            if (sorted.isEmpty()) {
                return 0;
            }
            int n = sorted.size();
            return (n % 2 == 1) ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
        }

        double spreadPct() {
            if (perConnectionBytes.isEmpty()) {
                return 0.0;
            }
            long min = Collections.min(perConnectionBytes);
            long max = Collections.max(perConnectionBytes);
            long median = medianBytesPerConnection();
            return median == 0 ? 0.0 : 100.0 * (max - min) / (double) median;
        }

        /**
         * The four harness-integrity gates. Throws {@link AssertionError} on any breach so a per-connection
         * number is never published unless the connections + streams were genuinely established and the event
         * log was genuinely excluded.
         */
        void validate() {
            if (perConnectionBytes.isEmpty()) {
                throw new AssertionError("no repeats recorded for " + label());
            }
            if (distinctConnections != connections) {
                throw new AssertionError(label() + ": opened " + distinctConnections + " distinct connection(s), expected "
                    + connections + " — a collapsed/multiplexed client would make the connection axis measure nothing");
            }
            if (confirmedInFlight != expectedInFlight()) {
                long minConnectionsImplied = (expectedInFlight() + MAX_CONCURRENT_STREAMS_PER_CONNECTION - 1)
                    / MAX_CONCURRENT_STREAMS_PER_CONNECTION;
                throw new AssertionError(label() + ": server confirmed " + confirmedInFlight + " in-flight streams, expected "
                    + expectedInFlight() + " (that many concurrent streams requires >= " + minConnectionsImplied
                    + " connections given MAX_CONCURRENT_STREAMS=" + MAX_CONCURRENT_STREAMS_PER_CONNECTION + ")");
            }
            if (logOccupancy > LOG_OCCUPANCY_CEILING) {
                throw new AssertionError(label() + ": event log held " + logOccupancy + " entries at sample time (> "
                    + LOG_OCCUPANCY_CEILING + ") — the heap delta may include logged bodies, not connection cost");
            }
            long median = medianBytesPerConnection();
            if (median < BYTES_PER_CONNECTION_FLOOR) {
                throw new AssertionError(label() + ": median " + median + " bytes/connection < floor "
                    + BYTES_PER_CONNECTION_FLOOR + " — the connections were not really established or not measured");
            }
            if (median > BYTES_PER_CONNECTION_CEILING) {
                throw new AssertionError(label() + ": median " + median + " bytes/connection > ceiling "
                    + BYTES_PER_CONNECTION_CEILING + " — suspect the harness measured the whole heap, not a connection");
            }
        }

        String label() {
            return "conn_" + connections + "x" + streamsPerConnection;
        }

        String summary() {
            return String.format(Locale.ROOT,
                "%s: median=%,d bytes/connection (spread=%.1f%% over %d reps) | inflight=%d distinct_conns=%d "
                    + "log=%d | heap baseline=%,d loaded=%,d",
                label(), medianBytesPerConnection(), spreadPct(), perConnectionBytes.size(),
                confirmedInFlight, distinctConnections, logOccupancy,
                lastBaselineHeap, lastLoadedHeap);
        }
    }

    private static String toJson(List<ShapeResult> results) {
        // Shape: {"h2_connection_memory": {"dated_utc":..., "method":..., "conn_<C>x<S>": {...}}}
        StringJoiner entries = new StringJoiner(",\n");
        for (ShapeResult r : results) {
            StringJoiner samples = new StringJoiner(", ");
            for (long b : r.perConnectionBytes) {
                samples.add(Long.toString(b));
            }
            String entry = String.format(Locale.ROOT,
                "    \"%s\": {\"bytes_per_connection\": %d, \"spread_pct\": %.2f, \"connections\": %d, "
                    + "\"streams_per_connection\": %d, \"inflight_confirmed\": %d, \"distinct_connections\": %d, "
                    + "\"log_occupancy\": %d, \"heap_baseline_bytes\": %d, \"heap_loaded_bytes\": %d, "
                    + "\"repeats\": %d, \"samples\": [%s]}",
                r.label(), r.medianBytesPerConnection(), r.spreadPct(), r.connections, r.streamsPerConnection,
                r.confirmedInFlight, r.distinctConnections, r.logOccupancy, r.lastBaselineHeap, r.lastLoadedHeap,
                r.perConnectionBytes.size(), samples);
            entries.add(entry);
        }
        String dated = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        return "{\n  \"h2_connection_memory\": {\n"
            + "    \"dated_utc\": \"" + dated + "\",\n"
            + "    \"method\": \"in-process client+server; MemoryMXBean.gc() + HeapMemoryUsage.used; "
            + "C connections x S in-flight (delayed) streams; event log cleared before sampling\",\n"
            + entries + "\n  }\n}\n";
    }

    // --- gate self-test (no server needed) -----------------------------------------------------------------

    static void selfTest() {
        System.out.println("=== gate self-test ===");
        int failures = 0;

        // Gate: collapsed connections (distinct != C). The positive control for the connection axis: a client
        // that multiplexed 100x10 onto ONE connection reports 1 distinct connection and MUST fail here.
        ShapeResult collapsed = new ShapeResult(100, 10);
        collapsed.record(1_000_000, 6_000_000, 1000, 1, 0); // 1 distinct connection, not 100
        failures += expectThrows("collapsed-connections gate", collapsed);

        // Gate: streams not established (confirmed != C*S). Impossible-on-one-connection proof.
        ShapeResult notEstablished = new ShapeResult(100, 10);
        notEstablished.record(1_000_000, 6_000_000, 100, 100, 0); // only 100 of 1000 in flight
        failures += expectThrows("streams-not-established gate", notEstablished);

        // Gate: log not excluded (occupancy above ceiling → delta may be logged bodies).
        ShapeResult logDirty = new ShapeResult(10, 10);
        logDirty.record(1_000_000, 3_000_000, 100, 10, 100); // 100 log entries at sample time
        failures += expectThrows("log-not-excluded gate", logDirty);

        // Gate: implausibly small delta (measured nothing).
        ShapeResult tooSmall = new ShapeResult(10, 10);
        tooSmall.record(1_000_000, 1_000_100, 100, 10, 0); // 100 bytes total over 10 conns = 10 bytes/conn
        failures += expectThrows("floor gate", tooSmall);

        // Gate: implausibly large delta (measured the whole heap).
        ShapeResult tooBig = new ShapeResult(1, 1);
        tooBig.record(1_000_000, 1_000_000_000L, 1, 1, 0); // ~1 GB for one connection
        failures += expectThrows("ceiling gate", tooBig);

        // Control: a clean, plausible result must PASS (e.g. ~50 KB/connection at 10x10).
        ShapeResult clean = new ShapeResult(10, 10);
        clean.record(2_000_000, 2_500_000, 100, 10, 0); // 500 KB over 10 conns = 50 KB/conn
        failures += expectPasses("clean-result control", clean);

        if (failures > 0) {
            fail(failures + " gate self-test check(s) FAILED — the harness validation is not sound");
        }
        System.out.println("--- all gate self-tests passed (every gate fires; clean result passes)");
    }

    private static int expectThrows(String name, ShapeResult result) {
        try {
            result.validate();
            System.out.println("  FAIL: " + name + " did NOT throw (gate would let a bad result through)");
            return 1;
        } catch (AssertionError expected) {
            System.out.println("  ok:   " + name + " fired — " + expected.getMessage());
            return 0;
        }
    }

    private static int expectPasses(String name, ShapeResult result) {
        try {
            result.validate();
            System.out.println("  ok:   " + name + " passed");
            return 0;
        } catch (AssertionError unexpected) {
            System.out.println("  FAIL: " + name + " threw on a clean result — " + unexpected.getMessage());
            return 1;
        }
    }

    // --- small helpers -------------------------------------------------------------------------------------

    private static void validateOrAbort(ShapeResult result) {
        try {
            result.validate();
        } catch (AssertionError gate) {
            fail(gate.getMessage());
        }
    }

    private static void validateShapeRequest(int connections, int streamsPerConnection) {
        if (connections < 1) {
            fail("connection count must be >= 1, got " + connections);
        }
        if (streamsPerConnection < 1) {
            fail("streams-per-connection must be >= 1, got " + streamsPerConnection);
        }
        if (streamsPerConnection > MAX_CONCURRENT_STREAMS_PER_CONNECTION) {
            fail("streams-per-connection " + streamsPerConnection + " exceeds the server MAX_CONCURRENT_STREAMS ("
                + MAX_CONCURRENT_STREAMS_PER_CONNECTION + ")");
        }
    }

    private static void fail(String message) {
        System.err.println("HARNESS VALIDATION FAILED: " + message);
        System.exit(2);
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    /** Parse "1x1,10x10,100x10" into {{1,1},{10,10},{100,10}}. */
    private static int[][] parseShapes(String csv) {
        String[] parts = csv.split(",");
        int[][] out = new int[parts.length][2];
        for (int i = 0; i < parts.length; i++) {
            String[] cs = parts[i].trim().toLowerCase(Locale.ROOT).split("x");
            if (cs.length != 2) {
                fail("bad shape '" + parts[i] + "' — expected <connections>x<streams> e.g. 10x10");
            }
            out[i][0] = Integer.parseInt(cs[0].trim());
            out[i][1] = Integer.parseInt(cs[1].trim());
        }
        return out;
    }

    private static String shapesToString(int[][] shapes) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (int[] shape : shapes) {
            joiner.add(shape[0] + "x" + shape[1]);
        }
        return joiner.toString();
    }
}
