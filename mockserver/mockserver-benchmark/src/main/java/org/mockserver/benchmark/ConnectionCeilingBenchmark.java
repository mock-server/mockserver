package org.mockserver.benchmark;

import com.sun.management.OperatingSystemMXBean;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The <b>connection-scaling ceiling</b>: how many concurrent established
 * connections a MockServer holds before request latency degrades.
 *
 * <p><b>Why this needs its own driver.</b> k6 allocates a virtual user per connection and is not
 * built to park tens of thousands of idle ones, so this purpose-built holder opens N real
 * connections, proves they are established <em>on the server</em>, parks them idle, and then
 * measures request latency on a <b>separate</b> connection. The held connections
 * generate no traffic — the axis under test is connection <em>state</em>, not offered load.
 *
 * <p><b>The rig is measured before the server is.</b> A ceiling number can easily describe the
 * client or the harness instead of the server, so the client-side limits are established first and
 * every rung carries its own validity block:
 * <ul>
 *   <li><b>Two different client ceilings, and the first one is invisible.</b> {@code CEILING_MODE=calibrate}
 *       opens connections until the kernel refuses and reports <em>which</em> resource ran out, because
 *       file descriptors and source ports look alike in a stack trace and mean opposite things:
 *       <ul>
 *         <li><b>File descriptors.</b> The JDK does not simply inherit the shell's limit, and what it
 *             does instead is <b>opposite on the two platforms</b>, so do not carry the flag across:
 *             <ul>
 *               <li><b>macOS</b>: the JDK sets {@code RLIMIT_NOFILE} to {@code min(hard, OPEN_MAX)} =
 *                   <b>10,240</b> however high {@code ulimit -n} reads. That is the JDK, not the
 *                   server — and it binds the <b>server</b> JVM as hard as the driver, so <b>both</b>
 *                   need {@code -XX:-MaxFDLimit}, <em>paired with</em> a raised {@code ulimit -n}: the
 *                   flag leaves the inherited soft limit alone.</li>
 *               <li><b>Linux</b>: the JDK already raises the soft limit to the <b>hard</b> limit, so
 *                   no flag is needed. Passing {@code -XX:-MaxFDLimit} there <b>disables</b> that
 *                   raise and leaves 1,024.</li>
 *             </ul></li>
 *         <li><b>Source ports.</b> With the clamp lifted the wall moves to the ephemeral range
 *             (49152-65535 on macOS). On macOS source ports come from <em>one global range</em>, so
 *             giving the server more ports buys nothing. The multi-port argument is kept only because
 *             it costs nothing and does hold on kernels that key the range by destination.</li>
 *       </ul>
 *       {@link #ephemeralHeadroom} refuses a rung above the measured ceiling rather than letting it fail
 *       as connect errors that read like a server limit.</li>
 *   <li><b>Establishment is proven twice, client and server.</b> Client side: N distinct local
 *       <em>endpoints</em> — address+port pairs, not ports alone, because with more than one source
 *       address the same ephemeral port number legitimately recurs on a different local address and a
 *       port-only count would false-fail. Server side: the event log recorded N received requests —
 *       every parked connection completed a real request before going idle, so the server accepted,
 *       read, matched and answered on it. A connection that only completed a TCP handshake would pass
 *       the first proof and fail the second.</li>
 *   <li><b>Source addresses are counted from the sockets that were actually bound, not the ones asked
 *       for.</b> The macOS wall is the global ephemeral source-port range, and the only way past it
 *       is more client source <em>addresses</em> (loopback aliases or more load-generator hosts), not
 *       more server ports. So the driver can round-robin connections across a configured list of
 *       local source addresses ({@code CEILING_SOURCE_ADDRESSES}), giving each address its own
 *       ephemeral range. Every rung reports how many <b>distinct source addresses were actually used</b> and
 *       how many connections landed on <b>each</b>, tallied from {@link Channel#localAddress()} after
 *       the OS assigned it — and {@link #measureRung} throws if that tally does not match the
 *       round-robin distribution the run configured. A misconfiguration that silently collapsed onto
 *       one address (an alias that was never created) therefore fails loudly rather than being read as
 *       a raised ceiling; missing aliases are also caught up front by
 *       {@link #assertSourceAddressesBindable}, which refuses to start.</li>
 *   <li><b>Client CPU headroom</b> is sampled across the probe window. A saturated driver measures
 *       itself, so a rung whose driver CPU has no headroom is marked {@code rig_valid:false} and its
 *       latency is not a server figure.</li>
 * </ul>
 *
 * <p><b>Every rung is paired with its own baseline.</b> A rung's latency means nothing in isolation on
 * a laptop, so each rung is preceded by a zero-connection sample and reported as a ratio to <em>that</em>
 * one. A single baseline at the start of the run is not enough: a box can drift across one ladder by
 * more than any effect the ladder is looking for, and monotonic drift makes every higher rung look like
 * an improvement. Pairing cancels drift slower than a pair; the first-to-last baseline gap is still
 * printed, as a measure of how much the box moved. The discarded warm-up exists for the same reason at
 * a shorter timescale: the first sample of an un-warmed driver reads well above steady state.
 *
 * <p><b>Scope.</b> Plaintext HTTP/1.1 keep-alive ({@code h1}) and the same over TLS ({@code tls}),
 * where the extra axis is the server's per-connection session and {@code SSLEngine} state. HTTP/2 is
 * deliberately <b>not</b> covered here: its connection axis is streams-per-connection, which
 * {@link Http2ConnectionMemoryBenchmark} already measures on the memory axis, and mixing the two
 * would confuse "connections held" with "streams held".
 *
 * <p>Usage: {@code ConnectionCeilingBenchmark <host> <port[,port...]>}, configured by environment:
 * <pre>
 *   CEILING_MODE            h1 (default) | tls
 *   CEILING_LADDER          comma-separated connection counts, e.g. 250,1000,4000,8000
 *   CEILING_PROBE_REQUESTS  sequential requests per latency sample (default 300)
 *   CEILING_SETTLE_MS       pause after parking a rung before probing (default 500)
 *   CEILING_WARMUP_REQUESTS discarded requests run before the first rung (default 2000)
 *   CEILING_SOURCE_ADDRESSES comma-separated local source addresses to round-robin outgoing
 *                           connections across, e.g. 127.0.0.1,127.0.0.2,127.0.0.3,127.0.0.4
 *                           (default 127.0.0.1). Each address contributes its own ephemeral
 *                           source-port range, which is the only lever that moves the source-port
 *                           wall on macOS. Non-primary loopback aliases must be created with sudo BEFORE the
 *                           run and removed after (see run-connection-ceiling.sh); the driver refuses
 *                           to start if any configured address is not bindable.
 * </pre>
 * Results are printed as one JSON document on stdout, after a human-readable table.
 */
public class ConnectionCeilingBenchmark {

    /** Path the parked connections and the probe both request; served by one expectation. */
    private static final String PATH = "/ceiling";

    /**
     * Ephemeral range size assumed when {@code CEILING_CLIENT_CEILING} is not set — macOS's default
     * 49152-65535. Deliberately NOT multiplied by the number of destination ports: whether the kernel
     * keeps a separate source-port space per destination is a property of the kernel, not something to
     * assume, and {@code CEILING_MODE=calibrate} measures it. Assuming the multiplier lets a ladder
     * ask for more connections than the box can give and fail as a {@code BindException} mid-rung,
     * which reads like a server limit and is not one.
     */
    private static final int DEFAULT_EPHEMERAL_RANGE = 16_384;

    /**
     * Fraction of the ephemeral range a rung may claim. Well under 1.0 because the box has its own
     * connections and because closed sockets sit in TIME_WAIT holding their port for tens of seconds.
     */
    private static final double EPHEMERAL_SAFETY_FACTOR = 0.80;

    /** Below this fraction of one core free, the driver is too busy for its latency to be the server's. */
    private static final double MIN_CLIENT_CPU_HEADROOM = 0.15;

    /** Parked connections confirmed per control-plane read, keeping the event log (and the read) small. */
    private static final int CONFIRM_BATCH = 500;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: ConnectionCeilingBenchmark <host> <port[,port...]>");
            System.exit(2);
        }
        String host = args[0];
        int[] ports = parsePorts(args[1]);
        String mode = env("CEILING_MODE", "h1").toLowerCase(Locale.ROOT);
        if (!"h1".equals(mode) && !"tls".equals(mode) && !"calibrate".equals(mode)) {
            System.err.println("CEILING_MODE must be h1, tls or calibrate, was: " + mode);
            System.exit(2);
        }
        InetAddress[] sourceAddresses = parseSourceAddresses(env("CEILING_SOURCE_ADDRESSES", "127.0.0.1"));
        // Fail up front, naming the address and the sudo command, rather than letting a missing alias
        // surface mid-rung as a BindException that reads like ephemeral-port exhaustion.
        assertSourceAddressesBindable(sourceAddresses);
        if ("calibrate".equals(mode)) {
            calibrate(host, ports, sourceAddresses);
            return;
        }
        int[] ladder = parseLadder(env("CEILING_LADDER", "250,1000,4000,8000"));
        int probeRequests = Integer.parseInt(env("CEILING_PROBE_REQUESTS", "300"));
        long settleMs = Long.parseLong(env("CEILING_SETTLE_MS", "500"));
        int warmupRequests = Integer.parseInt(env("CEILING_WARMUP_REQUESTS", "2000"));

        preloadFailureReportingClasses();

        int headroom = ephemeralHeadroom(sourceAddresses.length);
        for (int rung : ladder) {
            if (rung > headroom) {
                System.err.println("RIG REFUSES RUNG: " + rung + " connections exceeds this driver's usable"
                    + " headroom of " + headroom + " — " + (int) (EPHEMERAL_SAFETY_FACTOR * 100) + "% of a"
                    + " ceiling of " + (int) (headroom / EPHEMERAL_SAFETY_FACTOR) + " across " + sourceAddresses.length
                    + " source address(es), the remainder left for the previous rung's TIME_WAIT sockets. Add more"
                    + " source addresses via CEILING_SOURCE_ADDRESSES (each adds its own ephemeral range), run"
                    + " CEILING_MODE=calibrate to measure this box's per-address ceiling and pass it as"
                    + " CEILING_CLIENT_CEILING, or lower the ladder — do not read the resulting connect failures"
                    + " as a server ceiling.");
                System.exit(2);
            }
        }

        // The control plane is spoken over raw HTTP rather than MockServerClient so the driver stays
        // usable against a server built from a different version of this repo.
        HttpClient controlPlane = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        controlPlanePut(controlPlane, host, ports[0], "/mockserver/expectation",
            "{\"httpRequest\":{\"path\":\"" + PATH + "\"},"
                + "\"httpResponse\":{\"statusCode\":200,\"body\":\"c\"},"
                + "\"times\":{\"unlimited\":true}}");

        SslContext sslContext = "tls".equals(mode)
            ? SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
            : null;

        NioEventLoopGroup group = new NioEventLoopGroup();
        List<Rung> results = new ArrayList<>();
        try {
            warmUp(group, sslContext, host, ports[0], warmupRequests);
            long drainMs = Long.parseLong(env("CEILING_TIMEWAIT_DRAIN_MS", "45000"));
            // Every rung is PAIRED with a zero-connection baseline measured immediately before it (see
            // the class Javadoc); the first-versus-last baseline printed below says how much the box drifted.
            for (int rung : ladder) {
                // A rung's closed sockets hold their source ports in TIME_WAIT for 2*MSL, so the next rung
                // starts with less of the range than the last one had. Waiting keeps the ladder from
                // exhausting the rig part-way up and calling it a server ceiling.
                System.err.println("draining TIME_WAIT for " + drainMs + " ms before rung " + rung);
                Thread.sleep(drainMs);
                System.err.println("rung " + rung + ": baseline");
                Rung pairedBaseline = measureRung(group, sslContext, controlPlane, host, ports, sourceAddresses, 0, probeRequests, settleMs);
                results.add(pairedBaseline);
                System.err.println("rung " + rung + ": parking connections");
                Rung measured = measureRung(group, sslContext, controlPlane, host, ports, sourceAddresses, rung, probeRequests, settleMs);
                measured.pairedBaselineP50Micros = pairedBaseline.p50Micros;
                results.add(measured);
                System.err.println("rung " + rung + ": done");
            }
        } finally {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        }

        report(results, mode, ports.length, sourceAddresses.length, probeRequests);
    }

    /**
     * Discarded requests on a throwaway connection, so the first measured rung is not the one paying for
     * class loading and JIT. Without this an un-warmed first rung-0 sample reads well above the later
     * ones, presenting every held-connection rung as faster than no connections at all.
     */
    private static void warmUp(NioEventLoopGroup group, SslContext sslContext, String host, int port, int requests)
        throws Exception {
        if (requests <= 0) {
            return;
        }
        Channel channel = connect(group, sslContext, host, port);
        try {
            for (int i = 0; i < requests; i++) {
                requestOnce(channel, host, port).get(30, TimeUnit.SECONDS);
            }
        } finally {
            channel.close().sync();
        }
    }

    /** One ladder rung: park {@code connections} idle, then time {@code probeRequests} on a fresh connection. */
    private static Rung measureRung(NioEventLoopGroup group, SslContext sslContext, HttpClient controlPlane,
                                    String host, int[] ports, InetAddress[] sourceAddresses, int connections,
                                    int probeRequests, long settleMs) throws Exception {
        List<Channel> parked = new ArrayList<>(connections);
        // Distinct local ENDPOINTS (address+port), not ports alone: with more than one source address the
        // same ephemeral port recurs on a different local address and is a genuinely distinct socket.
        Set<InetSocketAddress> distinctLocalEndpoints = new LinkedHashSet<>();
        // Connections that actually landed on each source address, keyed by the address the OS bound —
        // read back from the channel, never the address we asked for — so a silent collapse onto one
        // address is visible instead of being mistaken for a raised ceiling.
        Map<String, Integer> connectionsPerSourceAddress = new LinkedHashMap<>();
        for (InetAddress source : sourceAddresses) {
            connectionsPerSourceAddress.put(source.getHostAddress(), 0);
        }
        long serverConfirmed;
        try {
            controlPlanePut(controlPlane, host, ports[0], "/mockserver/clear?type=LOG", "");
            // The server-side proof is accumulated in batches rather than read once at the end. The event
            // log is byte-bounded, so at a large rung a single read would silently be short by however much
            // the log had evicted — a harness that reports fewer connections than it opened and cannot tell
            // that from a server that dropped them.
            serverConfirmed = 0;
            int confirmedSinceClear = 0;
            for (int i = 0; i < connections; i++) {
                int port = ports[i % ports.length];
                // Round-robin the source address as well as the destination port, so every configured
                // address contributes its own ephemeral range rather than the whole rung draining one.
                InetAddress source = sourceAddresses[i % sourceAddresses.length];
                Channel channel;
                try {
                    channel = connect(group, sslContext, host, port, new InetSocketAddress(source, 0));
                } catch (Exception e) {
                    // The rig ran out, not the server. Recording where it ran out is a result; dying with
                    // a stack trace is not, and a reader of the crash cannot tell the two apart.
                    Wall.Kind kind = classify(e);
                    System.err.println("RIG EXHAUSTED at " + i + " of " + connections + " connections ("
                        + kind + ": " + describe(e) + "). This rung and every higher one are the driver's"
                        + " limit, not the server's — the previous rungs above are still valid."
                        + (kind == Wall.Kind.PORTS
                            ? " Source ports are held in TIME_WAIT for 2*MSL after a rung closes, so raise"
                              + " CEILING_TIMEWAIT_DRAIN_MS or shorten the ladder."
                            : " Raise the file-descriptor limit — on macOS `ulimit -n` PLUS"
                              + " -XX:-MaxFDLimit; on Linux raise the HARD limit and pass no flag,"
                              + " because there the flag lowers the limit instead of raising it."));
                    return Rung.rigExhausted(connections, i, kind);
                }
                // One completed request per parked connection. This is what makes it a CONNECTION the
                // server has actually accepted, read from and answered on — not a socket that only
                // finished a TCP (or TLS) handshake and may be sitting in an accept backlog.
                int status = requestOnce(channel, host, port).get(30, TimeUnit.SECONDS);
                if (status != 200) {
                    throw new IllegalStateException("parked connection " + i + " got status " + status);
                }
                parked.add(channel);
                InetSocketAddress localAddress = (InetSocketAddress) channel.localAddress();
                distinctLocalEndpoints.add(localAddress);
                connectionsPerSourceAddress.merge(localAddress.getAddress().getHostAddress(), 1, Integer::sum);
                if (++confirmedSinceClear == CONFIRM_BATCH) {
                    serverConfirmed += drainRecordedRequests(controlPlane, host, ports[0], confirmedSinceClear);
                    confirmedSinceClear = 0;
                }
            }
            if (confirmedSinceClear > 0) {
                serverConfirmed += drainRecordedRequests(controlPlane, host, ports[0], confirmedSinceClear);
            }
            if (distinctLocalEndpoints.size() != connections) {
                throw new IllegalStateException("HARNESS VALIDATION FAILED: " + distinctLocalEndpoints.size()
                    + " distinct local endpoints (address+port) for " + connections
                    + " connections — sockets were reused");
            }
            // The whole point of the multi-address feature is defeated if connections silently collapse
            // onto one address, and that failure would read as a raised ceiling. Assert the actual bound
            // distribution matches the round-robin the run configured — both the count of addresses used
            // and the per-address totals — so a missing alias or an OS that ignored the bind fails loudly.
            assertSourceAddressDistribution(connectionsPerSourceAddress, sourceAddresses, connections);
            if (serverConfirmed != connections) {
                throw new IllegalStateException("HARNESS VALIDATION FAILED: server recorded " + serverConfirmed
                    + " requests, expected " + connections + " — connections are not established server-side");
            }
            // Drop the log before probing so a large event log cannot be what the probe is measuring.
            controlPlanePut(controlPlane, host, ports[0], "/mockserver/clear?type=LOG", "");
            for (long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(settleMs);
                 System.nanoTime() < deadline; ) {
                Thread.sleep(10);
            }

            // The establishment proofs above are taken at PARK time. Nothing so far shows the parked
            // connections are still open when the probe runs — and a rung whose connections quietly
            // dropped in between would still report rig_valid:true, biasing toward a "no degradation"
            // conclusion. Re-assert immediately before measuring.
            long stillParked = parked.stream().filter(Channel::isActive).count();
            if (stillParked != connections) {
                throw new IllegalStateException("HARNESS VALIDATION FAILED: " + stillParked + " of "
                    + connections + " parked connections were still open at probe time — the rung was not"
                    + " holding what it claims to measure");
            }

            // The PROBE connect needs the same exhaustion guard as the parking loop above. A rung can
            // park every connection it asked for and then fail on this ONE socket because the ephemeral
            // range is spent — and an unguarded throw here kills the whole run, discarding the lower
            // rungs that already completed. Those rungs are the result; losing them to a stack trace on
            // the measuring socket is exactly the outcome the parking loop's handler exists to prevent.
            Channel probe;
            try {
                probe = connect(group, sslContext, host, ports[0]);
            } catch (Exception e) {
                Wall.Kind kind = classify(e);
                System.err.println("RIG EXHAUSTED opening the probe connection for rung " + connections
                    + " (" + kind + ": " + describe(e) + "). The rung's " + connections + " parked"
                    + " connections were established, but the probe that measures latency could not be"
                    + " opened, so this rung reports no timing. This rung and every higher one are the"
                    + " driver's limit, not the server's — the previous rungs are still valid.");
                // No explicit close here: the finally below already closes every parked channel, the
                // same way the parking loop's own exhaustion path relies on it.
                return Rung.rigExhausted(connections, connections, kind);
            }
            try {
                OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                long cpuBefore = os.getProcessCpuTime();
                long wallBefore = System.nanoTime();

                long[] latenciesNanos = new long[probeRequests];
                int errors = 0;
                for (int i = 0; i < probeRequests; i++) {
                    long start = System.nanoTime();
                    int status = requestOnce(probe, host, ports[0]).get(30, TimeUnit.SECONDS);
                    latenciesNanos[i] = System.nanoTime() - start;
                    if (status != 200) {
                        errors++;
                    }
                }

                long cpuUsed = os.getProcessCpuTime() - cpuBefore;
                long wallUsed = System.nanoTime() - wallBefore;
                int cores = Runtime.getRuntime().availableProcessors();
                // Headroom as a fraction of the whole machine: 1.0 means the driver used no CPU at all.
                double clientCpuHeadroom = 1.0 - ((double) cpuUsed / (double) (wallUsed * cores));

                Arrays.sort(latenciesNanos);
                Rung rung = new Rung(connections, distinctLocalEndpoints.size(), serverConfirmed, errors,
                    percentileMicros(latenciesNanos, 0.50), percentileMicros(latenciesNanos, 0.99),
                    clientCpuHeadroom);
                rung.distinctSourceAddresses = (int) connectionsPerSourceAddress.values().stream()
                    .filter(count -> count > 0).count();
                rung.connectionsPerSourceAddress = new LinkedHashMap<>(connectionsPerSourceAddress);
                return rung;
            } finally {
                probe.close().sync();
            }
        } finally {
            for (Channel channel : parked) {
                channel.close();
            }
        }
    }

    /** Connect letting the OS pick the source address and port — used by the warm-up and probe connections. */
    private static Channel connect(NioEventLoopGroup group, SslContext sslContext, String host, int port)
        throws InterruptedException {
        return connect(group, sslContext, host, port, null);
    }

    /**
     * Connect, optionally binding the outgoing socket to {@code localAddress} (address with port 0 for an
     * OS-assigned ephemeral port). Binding an explicit source address is how a rung spreads its
     * connections across several ephemeral ranges; {@code null} lets the OS choose, which for a loopback
     * destination is 127.0.0.1 — the same address the default single {@code 127.0.0.1} source binds.
     */
    static Channel connect(NioEventLoopGroup group, SslContext sslContext, String host, int port,
                           SocketAddress localAddress) throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap()
            .group(group)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    if (sslContext != null) {
                        ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), host, port));
                    }
                    ch.pipeline().addLast(new HttpClientCodec());
                    ch.pipeline().addLast(new HttpObjectAggregator(65_536));
                    ch.pipeline().addLast(new ResponseHandler());
                }
            });
        ChannelFuture future = localAddress == null
            ? bootstrap.connect(host, port)
            : bootstrap.connect(new InetSocketAddress(host, port), localAddress);
        Channel channel = future.sync().channel();
        if (sslContext != null) {
            assertTlsNegotiated(channel);
        }
        return channel;
    }

    /**
     * Prove the TLS arm is actually TLS. MockServer detects TLS per connection, so a driver that failed
     * to install the handler would still get 200s over plaintext and report a full ladder of numbers for
     * the wrong protocol — the arm would measure h1 twice and nobody would see it. Waiting for the
     * handshake and rejecting the null cipher is the difference between the two.
     */
    private static void assertTlsNegotiated(Channel channel) throws InterruptedException {
        io.netty.handler.ssl.SslHandler handler = channel.pipeline().get(io.netty.handler.ssl.SslHandler.class);
        if (handler == null) {
            throw new IllegalStateException("HARNESS VALIDATION FAILED: TLS mode has no SslHandler in the pipeline");
        }
        handler.handshakeFuture().sync();
        String cipher = handler.engine().getSession().getCipherSuite();
        if (cipher == null || cipher.contains("NULL")) {
            throw new IllegalStateException("HARNESS VALIDATION FAILED: TLS mode negotiated " + cipher
                + " — the connection is not encrypted, so this arm is measuring plaintext");
        }
    }

    /** Send one keep-alive GET on an established channel; completes with the response status. */
    private static CompletableFuture<Integer> requestOnce(Channel channel, String host, int port) {
        ResponseHandler handler = channel.pipeline().get(ResponseHandler.class);
        CompletableFuture<Integer> pending = handler.expectOne();
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, PATH, Unpooled.EMPTY_BUFFER);
        request.headers().set(HttpHeaderNames.HOST, host + ":" + port);
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        channel.writeAndFlush(request).addListener(future -> {
            if (!future.isSuccess()) {
                pending.completeExceptionally(future.cause());
            }
        });
        return pending;
    }

    /** Completes the outstanding request future with each response status; one request in flight per channel. */
    private static final class ResponseHandler extends ChannelInboundHandlerAdapter {
        private volatile CompletableFuture<Integer> pending;

        CompletableFuture<Integer> expectOne() {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            pending = future;
            return future;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                CompletableFuture<Integer> future = pending;
                if (msg instanceof FullHttpResponse && future != null) {
                    future.complete(((FullHttpResponse) msg).status().code());
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            CompletableFuture<Integer> future = pending;
            if (future != null) {
                future.completeExceptionally(cause);
            }
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            CompletableFuture<Integer> future = pending;
            if (future != null && !future.isDone()) {
                future.completeExceptionally(new IllegalStateException("connection closed with a request in flight"));
            }
        }
    }

    /**
     * Link {@link Wall} and {@link Wall.Kind} while descriptors are still available.
     * <p>
     * Loading a class needs a descriptor to read the jar, so a JVM that exhausts them mid-loop cannot
     * then load the class it would use to report that, and dies with {@code NoClassDefFoundError}
     * instead of naming the wall. Called from {@code main} rather than from the
     * calibrate path alone, because the measuring path reaches {@code classify()} on the same failure.
     */
    private static void preloadFailureReportingClasses() {
        if (new Wall(0, Wall.Kind.OTHER, "preload").held != 0) {
            throw new IllegalStateException("unreachable — exists so the preload cannot be optimised away");
        }
    }

    /**
     * How many connections this driver may hold: {@code CEILING_CLIENT_CEILING} if set (take it from
     * what {@code CEILING_MODE=calibrate} measured on this box, not from a model of how the kernel
     * allocates ports), else the assumed ephemeral range — and in both cases reduced by
     * {@link #EPHEMERAL_SAFETY_FACTOR}, because a ladder must fit the previous rung's {@code TIME_WAIT}
     * sockets as well as its own. It takes no <em>port</em> count: whether destinations get separate
     * source-port spaces is a kernel property, and on macOS it is one global range. It does
     * scale by the number of source <em>addresses</em>, because each local address contributes its own
     * ephemeral range — that is the whole reason for binding across addresses, and the guard would
     * otherwise refuse the very rungs the feature exists to reach. This only raises the ceiling the
     * guard will <em>attempt</em>; a rung the box cannot actually hold still fails through the honest
     * rig-exhaustion path, never as a silent server figure.
     */
    private static int ephemeralHeadroom(int sourceAddressCount) {
        String measured = System.getenv("CEILING_CLIENT_CEILING");
        int perAddress = (int) ((measured == null || measured.isEmpty()
            ? DEFAULT_EPHEMERAL_RANGE
            : Integer.parseInt(measured)) * EPHEMERAL_SAFETY_FACTOR);
        return perAddress * Math.max(1, sourceAddressCount);
    }

    /**
     * Measure what this driver can actually hold, instead of modelling it. Opens plain TCP connections
     * until the kernel refuses, first against one destination port and then spread across all of them.
     * If the two numbers match, the kernel draws source ports from one range regardless of destination,
     * and giving the server more ports buys nothing — the assumption this mode exists to test.
     */
    private static void calibrate(String host, int[] ports, InetAddress[] sourceAddresses) throws Exception {
        long maxFd = maxFileDescriptors();
        boolean macOs = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
        if (sourceAddresses.length > 1) {
            System.out.printf("calibrate: round-robining across %d source addresses (%s) — each contributes"
                + " its own ephemeral range, so the numbers below are the COMBINED ceiling%n",
                sourceAddresses.length, describeAddresses(sourceAddresses));
        }
        System.out.printf("calibrate: this JVM's file-descriptor limit is %d%s%n", maxFd,
            maxFd > 10_240 ? ""
                : macOs
                    ? " — the JDK sets RLIMIT_NOFILE to min(hard, OPEN_MAX=10240) on macOS whatever the"
                      + " shell's ulimit says. Raise `ulimit -n` AND re-run both this driver and the"
                      + " server with -XX:-MaxFDLimit, or the ceiling you measure is the JDK's."
                    : " — low for a connection ladder. On Linux the JDK raises the soft limit to the HARD"
                      + " limit, so raise the HARD limit (systemd LimitNOFILE, docker --ulimit nofile)."
                      + " Do NOT pass -XX:-MaxFDLimit here: on Linux it disables that raise.");

        Wall one = holdUntilRefused(host, new int[]{ports[0]}, sourceAddresses);
        System.out.printf("calibrate: 1 destination port  -> %d connections, refused by %s%n", one.held, one.kind);
        if (ports.length > 1) {
            // TIME_WAIT from the first arm would steal from the second, so wait it out rather than
            // reporting a second number that is really the first arm's residue.
            long drainMs = Long.parseLong(env("CEILING_TIMEWAIT_DRAIN_MS", "45000"));
            System.out.printf("calibrate: draining TIME_WAIT for %d ms before the second arm%n", drainMs);
            Thread.sleep(drainMs);
            Wall many = holdUntilRefused(host, ports, sourceAddresses);
            System.out.printf("calibrate: %d destination ports -> %d connections, refused by %s%n",
                ports.length, many.held, many.kind);
            // Only a PORT wall says anything about how source ports are allocated. Two arms that both
            // stopped at the same file-descriptor limit are equal for a reason that has nothing to do
            // with destinations, and reading a port conclusion out of that ratio is exactly the mistake
            // this mode exists to prevent.
            if (one.kind != Wall.Kind.PORTS || many.kind != Wall.Kind.PORTS) {
                System.out.println("calibrate: INCONCLUSIVE about source-port allocation — at least one arm"
                    + " stopped at a file-descriptor limit, not a port limit. Raise the fd limit (see the"
                    + " per-platform note above) and re-run before drawing any conclusion about destinations.");
            } else {
                double ratio = (double) many.held / one.held;
                System.out.printf("calibrate: ratio %.2f — %s%n", ratio,
                    ratio > 1.5
                        ? "source ports are per-destination, so more server ports raise the ceiling"
                        : "one global source-port range; more server ports do NOT raise the ceiling");
            }
        }
        // CEILING_CLIENT_CEILING is a PER-ADDRESS figure (ephemeralHeadroom multiplies it back up by the
        // source-address count), so when this calibrate run round-robined across several addresses the
        // combined `one.held` must be divided down before it is suggested — otherwise a later multi-address
        // measuring run would multiply an already-combined number and over-estimate its headroom.
        // Integer division deliberately rounds DOWN: a suggested ceiling that is slightly too
        // small costs a few connections of headroom, while one that is too large lets a rung
        // run past the ephemeral range and report the client's wall as the server's.
        int perAddressCeiling = one.held / sourceAddresses.length;
        System.out.printf("calibrate: pass CEILING_CLIENT_CEILING=%d to a measuring run (per source address%s)"
            + " — each address then contributes %d usable (%d%%), leaving the rest for the previous rung's"
            + " TIME_WAIT sockets%n",
            perAddressCeiling, sourceAddresses.length > 1 ? ", from the combined " + one.held + " above" : "",
            (int) (perAddressCeiling * EPHEMERAL_SAFETY_FACTOR), (int) (EPHEMERAL_SAFETY_FACTOR * 100));
    }

    /** Where a connect loop stopped, and what stopped it — the two are different findings. */
    private static final class Wall {
        enum Kind { FILE_DESCRIPTORS, PORTS, OTHER }

        final int held;
        final Kind kind;
        final String detail;

        Wall(int held, Kind kind, String detail) {
            this.held = held;
            this.kind = kind;
            this.detail = detail;
        }

        @Override
        public String toString() {
            return kind + " (" + detail + ")";
        }
    }

    /**
     * Open connections round-robin across {@code ports} and {@code sourceAddresses} until one is refused;
     * returns where and why it stopped.
     */
    private static Wall holdUntilRefused(String host, int[] ports, InetAddress[] sourceAddresses) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup();
        List<Channel> held = new ArrayList<>();
        try {
            for (int i = 0; ; i++) {
                InetAddress source = sourceAddresses[i % sourceAddresses.length];
                try {
                    held.add(connect(group, null, host, ports[i % ports.length], new InetSocketAddress(source, 0)));
                } catch (Exception e) {
                    return new Wall(held.size(), classify(e), describe(e));
                }
            }
        } finally {
            for (Channel channel : held) {
                channel.close();
            }
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        }
    }

    /**
     * Which resource ran out. "Can't assign requested address" is the kernel refusing a source port;
     * a failure to create the channel at all is the process out of file descriptors. They look alike in
     * a stack trace and mean opposite things about what to fix.
     */
    private static Wall.Kind classify(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String message = t.getMessage() == null ? "" : t.getMessage();
            if (t instanceof java.net.BindException || message.contains("Can't assign requested address")) {
                return Wall.Kind.PORTS;
            }
            // Only the message is evidence. `ChannelException` wraps many unrelated failures, so
            // matching on the type would report "out of file descriptors" for a refused connection and
            // send the reader to the wrong fix. On a real exhaustion the chain is
            // ChannelException -> InvocationTargetException -> ChannelException -> SocketException,
            // and the last carries "Too many open files", so walking causes finds it without the type.
            if (message.contains("Too many open files")) {
                return Wall.Kind.FILE_DESCRIPTORS;
            }
        }
        return Wall.Kind.OTHER;
    }

    private static String describe(Throwable error) {
        return error.getClass().getSimpleName() + (error.getMessage() == null ? "" : ": " + error.getMessage());
    }

    private static long maxFileDescriptors() {
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        return os instanceof com.sun.management.UnixOperatingSystemMXBean
            ? ((com.sun.management.UnixOperatingSystemMXBean) os).getMaxFileDescriptorCount()
            : -1;
    }

    private static double percentileMicros(long[] sortedNanos, double percentile) {
        int index = (int) Math.min(sortedNanos.length - 1L, Math.round(percentile * (sortedNanos.length - 1)));
        return sortedNanos[index] / 1_000.0;
    }

    private static void report(List<Rung> results, String mode, int ports, int sourceAddresses, int probeRequests) {
        Rung firstBaseline = results.get(0);
        Rung lastBaseline = null;
        for (Rung rung : results) {
            if (rung.connections == 0 && rung.exhaustedBy == null) {
                lastBaseline = rung;
            }
        }
        double baselineDriftPct = lastBaseline == null ? 0.0
            : 100.0 * (lastBaseline.p50Micros - firstBaseline.p50Micros) / firstBaseline.p50Micros;

        System.out.println();
        System.out.printf("item 21 connection-scaling ceiling — mode=%s ports=%d source_addresses=%d probe_requests=%d%n",
            mode, ports, sourceAddresses, probeRequests);
        System.out.println("connections  established  server_confirmed  src_addrs  p50_us   p99_us   vs_paired_baseline  client_cpu_headroom  rig_valid");
        for (Rung rung : results) {
            if (rung.exhaustedBy != null) {
                System.out.printf("%11d  %11d  %16s  %9s  %7s  %7s  %18s  %19s  %s%n",
                    rung.connections, rung.established, "-", "-", "-", "-", "-", "-",
                    "false (rig exhausted: " + rung.exhaustedBy + ")");
                continue;
            }
            System.out.printf("%11d  %11d  %16d  %9d  %7.1f  %7.1f  %18s  %19.3f  %s%n",
                rung.connections, rung.established, rung.serverConfirmed, rung.distinctSourceAddresses,
                rung.p50Micros, rung.p99Micros,
                Double.isNaN(rung.pairedBaselineP50Micros) ? "(baseline)"
                    : String.format(Locale.ROOT, "%.2fx", rung.p50Micros / rung.pairedBaselineP50Micros),
                rung.clientCpuHeadroom, rung.rigValid());
        }

        // How many connections landed on EACH source address, printed per non-baseline rung. This is the
        // check that a ladder past the source-port wall really used several source addresses rather than
        // one — the headline number is worthless if the spread is not what was configured.
        if (sourceAddresses > 1) {
            System.out.println("\nconnections per source address (from the addresses the OS actually bound):");
            for (Rung rung : results) {
                if (rung.exhaustedBy == null && rung.connections > 0) {
                    System.out.printf("  %d connections -> %s%n", rung.connections,
                        formatAddressCounts(rung.connectionsPerSourceAddress));
                }
            }
        }
        System.out.printf("%nbaseline drift first-to-last: %.1f%% — reported only as a measure of how much the box"
            + " moved; the ratios above are each against their OWN immediately-preceding baseline, so this"
            + " drift does not enter them.%n", baselineDriftPct);

        StringBuilder json = new StringBuilder("{\"mode\":\"").append(mode).append("\",\"ports\":").append(ports)
            .append(",\"source_addresses\":").append(sourceAddresses)
            .append(",\"probe_requests\":").append(probeRequests)
            .append(",\"baseline_drift_pct\":").append(String.format(Locale.ROOT, "%.2f", baselineDriftPct))
            .append(",\"rungs\":[");
        for (int i = 0; i < results.size(); i++) {
            json.append(i == 0 ? "" : ",").append(results.get(i).toJson());
        }
        System.out.println(json.append("]}"));
    }

    private static final class Rung {
        final int connections;
        final int established;
        final long serverConfirmed;
        final int probeErrors;
        final double p50Micros;
        final double p99Micros;
        final double clientCpuHeadroom;
        final Wall.Kind exhaustedBy;
        /** p50 of the zero-connection sample taken immediately before this rung; NaN for a baseline itself. */
        double pairedBaselineP50Micros = Double.NaN;
        /** Distinct source addresses the OS actually bound this rung's connections to; 0 for a baseline. */
        int distinctSourceAddresses = 0;
        /** Connections that landed on each source address, keyed by the bound address; empty for a baseline. */
        Map<String, Integer> connectionsPerSourceAddress = java.util.Collections.emptyMap();

        Rung(int connections, int established, long serverConfirmed, int probeErrors,
             double p50Micros, double p99Micros, double clientCpuHeadroom) {
            this(connections, established, serverConfirmed, probeErrors, p50Micros, p99Micros,
                clientCpuHeadroom, null);
        }

        Rung(int connections, int established, long serverConfirmed, int probeErrors,
             double p50Micros, double p99Micros, double clientCpuHeadroom, Wall.Kind exhaustedBy) {
            this.connections = connections;
            this.established = established;
            this.serverConfirmed = serverConfirmed;
            this.probeErrors = probeErrors;
            this.p50Micros = p50Micros;
            this.p99Micros = p99Micros;
            this.clientCpuHeadroom = clientCpuHeadroom;
            this.exhaustedBy = exhaustedBy;
        }

        /** A rung the driver could not build. It carries no latency, and must never be read as one. */
        static Rung rigExhausted(int requested, int reached, Wall.Kind kind) {
            return new Rung(requested, reached, -1, 0, Double.NaN, Double.NaN, Double.NaN, kind);
        }

        /** Every rung carries its own validity: a latency measured on a saturated or lossy driver is not a server figure. */
        /**
         * Establishment is not re-checked here: a rung with fewer connections than requested never
         * reaches this object, because {@link #measureRung} throws on the distinct-local-port and
         * server-confirmed counts. Repeating it would read as a guard while being unreachable.
         */
        boolean rigValid() {
            return exhaustedBy == null && probeErrors == 0 && clientCpuHeadroom >= MIN_CLIENT_CPU_HEADROOM;
        }

        String toJson() {
            if (exhaustedBy != null) {
                return String.format(Locale.ROOT,
                    "{\"connections\":%d,\"reached\":%d,\"rig_valid\":false,\"exhausted_by\":\"%s\"}",
                    connections, established, exhaustedBy);
            }
            String ratio = Double.isNaN(pairedBaselineP50Micros) ? "null"
                : String.format(Locale.ROOT, "%.3f", p50Micros / pairedBaselineP50Micros);
            return String.format(Locale.ROOT,
                "{\"connections\":%d,\"established\":%d,\"server_confirmed\":%d,\"probe_errors\":%d,"
                    + "\"distinct_source_addresses\":%d,\"connections_per_source_address\":%s,"
                    + "\"p50_us\":%.1f,\"p99_us\":%.1f,\"vs_paired_baseline\":%s,"
                    + "\"client_cpu_headroom\":%.3f,\"rig_valid\":%s}",
                connections, established, serverConfirmed, probeErrors, distinctSourceAddresses,
                addressCountsToJson(connectionsPerSourceAddress), p50Micros, p99Micros,
                ratio, clientCpuHeadroom, rigValid());
        }
    }

    private static void controlPlanePut(HttpClient http, String host, int port, String path, String body) {
        try {
            HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://" + host + ":" + port + path))
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .header("content-type", "application/json")
                    .build(),
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("control plane " + path + " returned " + response.statusCode()
                    + ": " + response.body());
            }
        } catch (Exception e) {
            throw new IllegalStateException("control plane " + path + " failed", e);
        }
    }

    /**
     * Read and then clear the server's recorded requests, returning how many it held. Failing loudly when
     * the count is not the expected batch size keeps a short read from being averaged away into a total
     * that still looks plausible.
     */
    private static long drainRecordedRequests(HttpClient http, String host, int port, int expected) {
        long recorded = retrieveRequestCount(http, host, port);
        if (recorded != expected) {
            throw new IllegalStateException("HARNESS VALIDATION FAILED: server recorded " + recorded
                + " requests in this batch, expected " + expected
                + " — the event log evicted entries, or a parked connection was not served");
        }
        controlPlanePut(http, host, port, "/mockserver/clear?type=LOG", "");
        return recorded;
    }

    /** Number of requests the server has recorded — the server-side half of the establishment proof. */
    private static long retrieveRequestCount(HttpClient http, String host, int port) {
        try {
            HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://" + host + ":" + port + "/mockserver/retrieve?type=REQUESTS&format=JSON"))
                    .PUT(HttpRequest.BodyPublishers.ofString(""))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
            // Check the status BEFORE parsing. An error body is usually still valid JSON, and
            // readTree(...).size() on it returns 0 — which this harness would report as the server
            // having accepted none of the connections, i.e. a transport fault dressed up as a
            // measurement. This is the server-side half of the establishment proof, so it must fail
            // loudly rather than return a number it did not read.
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("retrieve returned " + response.statusCode()
                    + ": " + response.body());
            }
            // Parse the array rather than counting pretty-printed "path" lines: the retrieve endpoint
            // happens to pretty-print today, and a compact response would silently count zero — which
            // this harness would then report as the server having accepted none of the connections.
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body()).size();
        } catch (Exception e) {
            throw new IllegalStateException("could not read the server's recorded request count", e);
        }
    }

    private static int[] parsePorts(String csv) {
        String[] parts = csv.split(",");
        int[] ports = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            ports[i] = Integer.parseInt(parts[i].trim());
        }
        return ports;
    }

    private static int[] parseLadder(String csv) {
        String[] parts = csv.split(",");
        int[] ladder = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            ladder[i] = Integer.parseInt(parts[i].trim());
        }
        return ladder;
    }

    /**
     * Parse a comma-separated list of local source addresses into resolved {@link InetAddress}es,
     * preserving order and rejecting duplicates — two entries for the same address would silently halve
     * that address's apparent share and break the round-robin distribution check. Blank input yields the
     * single default {@code 127.0.0.1}.
     */
    static InetAddress[] parseSourceAddresses(String csv) throws java.net.UnknownHostException {
        String[] parts = (csv == null || csv.isBlank() ? "127.0.0.1" : csv).split(",");
        List<InetAddress> addresses = new ArrayList<>(parts.length);
        Set<String> seen = new LinkedHashSet<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            InetAddress address = InetAddress.getByName(trimmed);
            if (!seen.add(address.getHostAddress())) {
                throw new IllegalArgumentException("CEILING_SOURCE_ADDRESSES lists " + trimmed
                    + " (" + address.getHostAddress() + ") more than once — each source address must be unique");
            }
            addresses.add(address);
        }
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("CEILING_SOURCE_ADDRESSES resolved to no addresses");
        }
        return addresses.toArray(new InetAddress[0]);
    }

    /**
     * Refuse to start unless every configured source address can actually be bound. A non-primary
     * loopback alias that was never created (macOS needs {@code sudo ifconfig lo0 alias <ip> up}) would
     * otherwise surface mid-rung as a {@code BindException} that reads like ephemeral-port exhaustion, or
     * — worse — could be mistaken for having raised the ceiling. Failing up front with
     * the address named and the fix printed keeps a misconfiguration from ever reaching a latency number.
     */
    private static void assertSourceAddressesBindable(InetAddress[] sourceAddresses) {
        for (InetAddress address : sourceAddresses) {
            if (!isBindable(address)) {
                System.err.println("RIG REFUSES TO START: cannot bind to source address "
                    + address.getHostAddress() + " — it is not configured on this host. On macOS create a"
                    + " loopback alias first with `sudo ifconfig lo0 alias " + address.getHostAddress()
                    + " up` (remove it afterwards with `sudo ifconfig lo0 -alias " + address.getHostAddress()
                    + "`). Configured via CEILING_SOURCE_ADDRESSES. Do NOT read a run that silently used"
                    + " fewer addresses as a raised ceiling.");
                System.exit(2);
            }
        }
    }

    /** True if a socket can bind an ephemeral port on {@code address} — i.e. the address exists on this host. */
    static boolean isBindable(InetAddress address) {
        try (Socket probe = new Socket()) {
            probe.bind(new InetSocketAddress(address, 0));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The number of connections each source address should receive when {@code connections} are dealt
     * round-robin across {@code addressCount} addresses: address {@code j} gets {@code connections /
     * addressCount}, plus one for the first {@code connections % addressCount} addresses. This is the
     * exact distribution {@link #measureRung} produces and {@link #assertSourceAddressDistribution}
     * checks the bound sockets against.
     */
    static int[] expectedRoundRobinCounts(int connections, int addressCount) {
        int[] expected = new int[addressCount];
        int base = connections / addressCount;
        int remainder = connections % addressCount;
        for (int j = 0; j < addressCount; j++) {
            expected[j] = base + (j < remainder ? 1 : 0);
        }
        return expected;
    }

    /**
     * Assert the connections actually bound per source address match the round-robin distribution the run
     * configured — both that the expected number of addresses were used and that each got its expected
     * share. Reads the tally keyed by the address the OS bound (from {@link Channel#localAddress()}), so
     * a silent collapse onto one address, or an OS that ignored the requested bind, fails here rather
     * than being reported as a higher ceiling.
     */
    static void assertSourceAddressDistribution(Map<String, Integer> connectionsPerSourceAddress,
                                                InetAddress[] sourceAddresses, int connections) {
        int[] expected = expectedRoundRobinCounts(connections, sourceAddresses.length);
        int expectedDistinct = Math.min(connections, sourceAddresses.length);
        long actualDistinct = connectionsPerSourceAddress.values().stream().filter(count -> count > 0).count();
        if (actualDistinct != expectedDistinct) {
            throw new IllegalStateException("HARNESS VALIDATION FAILED: connections landed on " + actualDistinct
                + " source address(es), expected " + expectedDistinct + " for " + connections
                + " connections round-robined across " + sourceAddresses.length + " configured address(es) — "
                + formatAddressCounts(connectionsPerSourceAddress) + ". A run that silently used fewer"
                + " addresses is NOT a raised ceiling.");
        }
        for (int j = 0; j < sourceAddresses.length; j++) {
            String hostAddress = sourceAddresses[j].getHostAddress();
            int actual = connectionsPerSourceAddress.getOrDefault(hostAddress, 0);
            if (actual != expected[j]) {
                throw new IllegalStateException("HARNESS VALIDATION FAILED: source address " + hostAddress
                    + " bound " + actual + " connections, expected " + expected[j] + " from round-robin of "
                    + connections + " across " + sourceAddresses.length + " address(es) — "
                    + formatAddressCounts(connectionsPerSourceAddress));
            }
        }
    }

    private static String describeAddresses(InetAddress[] addresses) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < addresses.length; i++) {
            builder.append(i == 0 ? "" : ",").append(addresses[i].getHostAddress());
        }
        return builder.toString();
    }

    /** Human-readable "addr=count, addr=count" for the table and validation messages. */
    private static String formatAddressCounts(Map<String, Integer> counts) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            builder.append(first ? "" : ", ").append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.length() == 0 ? "(none)" : builder.toString();
    }

    /** {@code {"127.0.0.1":4000,...}} for the JSON document; empty map becomes {@code {}}. */
    private static String addressCountsToJson(Map<String, Integer> counts) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            builder.append(first ? "" : ",").append('"').append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        return builder.append('}').toString();
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}
