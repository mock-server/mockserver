package org.mockserver.benchmark;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandRequest;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4ClientEncoder;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v4.Socks4ServerDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ServerEncoder;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.util.ReferenceCountUtil;
import org.mockserver.netty.proxy.socks.SocksDetector;
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
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Per-connection cost of MockServer's <b>SOCKS proxy handshake</b> — the one proxy dimension the k6 arms
 * and the other JMH benchmarks leave entirely uncovered.
 *
 * <p><b>Why this is a JMH benchmark and not a k6 arm.</b> k6 can route through an <i>HTTP</i> proxy but
 * has no SOCKS transport, so a SOCKS k6 arm would need a custom {@code xk6} binary or an
 * HTTP&rarr;SOCKS bridge whose cost would dominate the number. And <b>the SOCKS steady-state relay is
 * already covered:</b> once a SOCKS {@code CONNECT} is established,
 * {@code SocksConnectHandler extends RelayConnectHandler} and the tunnel is pumped by the same
 * {@code Upstream/DownstreamProxyRelayHandler} pair measured by {@code RelayByteCopyBenchmark}. The
 * <i>only</i> SOCKS-specific cost is the per-connection negotiation, which is what JMH measures well.
 *
 * <p><b>What is actually measured — the real handshake codecs, not an imitation.</b> The SOCKS handshake
 * is parsed and emitted by the exact Netty codec instances {@code PortUnificationHandler.enableSocks4/5}
 * installs — {@link Socks5InitialRequestDecoder}, {@link Socks5PasswordAuthRequestDecoder},
 * {@link Socks5CommandRequestDecoder}, {@link Socks4ServerDecoder}, and the response encoders
 * {@link Socks5ServerEncoder#DEFAULT} / {@link Socks4ServerEncoder#INSTANCE}. {@link #handshake} drives
 * that real codec sequence through a fresh {@link EmbeddedChannel} per op (a fresh op = a fresh
 * connection, which is exactly the handshake's unit), decoding each client message and encoding the
 * <i>same</i> server responses MockServer's {@code Socks5ProxyHandler} / {@code Socks4ProxyHandler} build
 * ({@code DefaultSocks5InitialResponse}, {@code DefaultSocks5PasswordAuthResponse},
 * {@code DefaultSocks5CommandResponse(SUCCESS, DOMAIN, host, port)} — the literal
 * {@code Socks5ConnectHandler.successResponse}, and {@code DefaultSocks4CommandResponse(SUCCESS, ip,
 * port)} — the {@code Socks4ConnectHandler} IPv4 branch), swapping decoders across the negotiation steps
 * precisely as the handler does.
 *
 * <p>{@link #detect} isolates the <b>front-door classifier</b> {@link SocksDetector} that
 * {@code PortUnificationHandler.decode} runs on the very first bytes of every connection (it calls
 * {@code isSocks4} then {@code isSocks5}); it reads bytes with {@code getByte} and allocates nothing, so
 * its {@code gc.alloc.rate.norm} is a near-zero <b>floor reference</b> proving detection is not where the
 * handshake cost lives.
 *
 * <p><b>Measurement-validity — the two controls that stop this number lying.</b> A raw
 * per-op figure from an {@link EmbeddedChannel} cannot, on its own, tell "SOCKS protocol work" apart from
 * "EmbeddedChannel/pipeline construction plumbing", because building a fresh channel per op allocates a
 * channel, pipeline and config every time. Two independent controls discriminate the mechanism:
 * <ul>
 *   <li><b>{@link #channelPlumbingOnly} (the rival-variable control).</b> It constructs and closes a
 *       fresh {@link EmbeddedChannel} holding a single no-op handler and does <i>no</i> SOCKS work at all.
 *       {@code channelPlumbingOnly ÷ handshake} is therefore the share of {@link #handshake}'s allocation
 *       that is pure channel construction rather than the SOCKS codecs — the analogue of
 *       {@code RelayByteCopyBenchmark.fragmentWrappersOnly}. This is a LOWER BOUND on the plumbing
 *       share, for TWO independent
 *       reasons that both push the same way: the control's channel does not escape into codec work, so
 *       the JIT has more freedom to elide it; and the control holds ONE handler where {@link #handshake}
 *       starts with two (encoder + initial decoder), each of which costs a
 *       {@code DefaultChannelHandlerContext}. So the real plumbing share is somewhat above what the
 *       control reports and the SOCKS-attributed remainder is correspondingly an upper bound. The subtraction is directional
 *       evidence that the climb is codec work, not an exact split.</li>
 *   <li><b>The {@link #scenario} sweep (the protocol-complexity discriminator).</b> {@code SOCKS4} does a
 *       single request/response; {@code SOCKS5_NO_AUTH} does an initial greeting + a command (two
 *       round-trips, a decoder swap); {@code SOCKS5_PASSWORD} adds a whole password-auth round-trip and a
 *       second decoder swap. Run through the <i>identical</i> harness, more protocol steps <b>must</b>
 *       cost more time and allocation. If the three scenarios came out flat, the number would be
 *       measuring plumbing, not the handshake — so their ordering (PASSWORD &gt; NO_AUTH &gt; SOCKS4) is
 *       itself the evidence that the figure reflects real protocol work.</li>
 * </ul>
 * A free arithmetic sanity check: the SOCKS negotiation messages are tiny (a SOCKS5 greeting is 3-4
 * bytes, a command request ~10 bytes, a SOCKS4 request ~15 bytes), so a per-op allocation of a few
 * hundred bytes to low-single-kB is physically plausible; a figure orders of magnitude larger than that
 * would signal plumbing (or a leak) dominating, not "an expensive handshake", and would be investigated
 * rather than reported.
 *
 * <p><b>What this does NOT cover</b> (be explicit — do not read more into the number than is here):
 * <ul>
 *   <li><b>The socket read/write copies are omitted entirely.</b> The handshake bytes are handed to the
 *       decoders as {@code wrappedBuffer} views over pre-built arrays; the kernel&harr;userspace copy a
 *       real socket pays on every read and write is not measured. The figure is the handshake's
 *       codec/object cost above the socket, not its total per-byte cost (the messages are tiny, so this
 *       omission is small in absolute terms — unlike the relay).</li>
 *   <li><b>The {@code Socks5ProxyHandler} / {@code Socks4ProxyHandler} control flow and the relay handoff
 *       are out of scope.</b> Those handlers are thin routers: a few branch comparisons, one or two
 *       {@code pipeline().addFirst/replace} edits, and (PASSWORD) a constant-time credential compare —
 *       and their terminal step, {@code forwardConnection}, initiates a <i>real upstream connection</i>
 *       and installs the relay handler, which cannot run in an {@link EmbeddedChannel} and is
 *       {@code RelayByteCopyBenchmark}'s territory. This benchmark measures the substantive per-connection cost (the codecs the
 *       handlers drive), not the routing shim around them and not the relay after handoff. Concretely,
 *       the {@code SOCKS5_PASSWORD} scenario emits the auth SUCCESS response WITHOUT performing the two
 *       {@code ConstantTimeEquals} comparisons production runs over the supplied username and password,
 *       so that scenario's figure is a lower bound on the handler's total per-connection work.</li>
 *   <li>no sockets, no TLS (SOCKS-over-TLS adds an {@code SslHandler} leg), no event-loop hand-off, no
 *       flow control — a single-threaded, in-JVM codec measurement, not an end-to-end figure;</li>
 *   <li>SOCKS4a (domain-name destination) and SOCKS5 GSSAPI auth are not swept; SOCKS4 here uses a
 *       classic IPv4 destination and SOCKS5 a DOMAIN destination, the representative common paths;</li>
 *   <li>connection <i>churn</i> under concurrency (many handshakes/s across event-loop threads) is an
 *       end-to-end concern for a load arm, not this micro-benchmark.</li>
 * </ul>
 *
 * <pre>./run.sh -prof gc SocksHandshakeBenchmark
 * # the two controls, read against handshake:
 * ./run.sh -prof gc SocksHandshakeBenchmark -p scenario=SOCKS4,SOCKS5_NO_AUTH,SOCKS5_PASSWORD</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SocksHandshakeBenchmark {

    /** The three real handshake shapes, ordered by protocol complexity — the mechanism discriminator. */
    public enum Scenario {SOCKS4, SOCKS5_NO_AUTH, SOCKS5_PASSWORD}

    @Param({"SOCKS4", "SOCKS5_NO_AUTH", "SOCKS5_PASSWORD"})
    public Scenario scenario;

    // Client-side handshake bytes, encoded ONCE in setup with Netty's real client encoders so the server
    // decoders see exactly the wire bytes a genuine SOCKS client sends. Per op these are wrapped (not
    // copied) and fed to the decoders.

    /** SOCKS5 initial greeting, or (SOCKS4) the whole connect request — also the input to {@link #detect}. */
    private byte[] greetingBytes;
    /** SOCKS5 password-auth request bytes (SOCKS5_PASSWORD only). */
    private byte[] authBytes;
    /** SOCKS5 command (CONNECT) request bytes (SOCKS5 scenarios only). */
    private byte[] commandBytes;

    /** Reusable, read-only wrapper over {@link #greetingBytes} for the allocation-free {@link #detect}. */
    private ByteBuf detectBuf;

    private boolean socks5;

    private static final String SOCKS5_HOST = "example.com";
    private static final String SOCKS4_IP = "93.184.216.34";
    private static final int DST_PORT = 443;
    private static final String USER = "mockserver-user";
    private static final String PASS = "mockserver-pass";

    @Setup(Level.Trial)
    public void setup() {
        socks5 = scenario != Scenario.SOCKS4;
        if (socks5) {
            Socks5AuthMethod method = scenario == Scenario.SOCKS5_PASSWORD
                ? Socks5AuthMethod.PASSWORD
                : Socks5AuthMethod.NO_AUTH;
            greetingBytes = encodeClient(new DefaultSocks5InitialRequest(method));
            commandBytes = encodeClient(new DefaultSocks5CommandRequest(
                Socks5CommandType.CONNECT, Socks5AddressType.DOMAIN, SOCKS5_HOST, DST_PORT));
            if (scenario == Scenario.SOCKS5_PASSWORD) {
                authBytes = encodeClient(new DefaultSocks5PasswordAuthRequest(USER, PASS));
            }
        } else {
            greetingBytes = encodeClient(new DefaultSocks4CommandRequest(
                Socks4CommandType.CONNECT, SOCKS4_IP, DST_PORT, USER));
        }
        // wrappedBuffer shares the array (no copy); SocksDetector reads with getByte, so readerIndex never
        // moves and this single buffer is reusable across all detect() ops with zero per-op allocation.
        detectBuf = Unpooled.wrappedBuffer(greetingBytes);
    }

    /** Run a client message through the matching @Sharable client encoder to capture its exact wire bytes. */
    private byte[] encodeClient(Object clientMessage) {
        ChannelHandler encoder = socks5 ? Socks5ClientEncoder.DEFAULT : Socks4ClientEncoder.INSTANCE;
        EmbeddedChannel ch = new EmbeddedChannel(encoder);
        try {
            ch.writeOutbound(clientMessage);
            ByteBuf out = ch.readOutbound();
            byte[] bytes = new byte[out.readableBytes()];
            out.readBytes(bytes);
            out.release();
            return bytes;
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    /**
     * Front-door classification only — {@link SocksDetector} as {@code PortUnificationHandler.decode} runs
     * it on the first bytes of every connection (isSocks4 then isSocks5). Allocation-free by construction,
     * so its {@code gc.alloc.rate.norm} is the near-zero floor the handshake figure is read against.
     *
     * <p><b>Do not read this method's cross-scenario ORDERING as detection difficulty.</b> It is an
     * artefact of how many bytes each input makes the scanner walk. A SOCKS4 input matches in
     * {@code isSocks4}, which walks the version and command bytes, skips the port, reads four address
     * bytes and then scans a null-terminated username byte by byte; a SOCKS5 input fails
     * {@code isSocks4} on the very first byte and then needs only three bytes of {@code isSocks5}. So
     * SOCKS4 reads slower here purely because it is the longer scan. The only claim this method supports
     * is the one it is here for: detection is far cheaper than the handshake and allocates nothing,
     * so the front door is not where SOCKS cost lives.
     */
    @Benchmark
    public boolean detect() {
        int readable = detectBuf.readableBytes();
        return SocksDetector.isSocks4(detectBuf, readable) || SocksDetector.isSocks5(detectBuf, readable);
    }

    /**
     * The full per-connection handshake: decode each client message and encode each server reply through
     * the real Netty SOCKS codecs in a fresh {@link EmbeddedChannel}, exactly as the MockServer proxy
     * handlers drive them. Returns the total encoded reply-byte count as the sink so nothing is eliminated.
     */
    @Benchmark
    public long handshake() {
        return socks5 ? socks5Handshake() : socks4Handshake();
    }

    private long socks4Handshake() {
        EmbeddedChannel ch = new EmbeddedChannel(Socks4ServerEncoder.INSTANCE, new Socks4ServerDecoder());
        try {
            long replyBytes = 0;
            ch.writeInbound(Unpooled.wrappedBuffer(greetingBytes));
            ReferenceCountUtil.release(ch.readInbound()); // Socks4CommandRequest
            ch.writeOutbound(new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS, SOCKS4_IP, DST_PORT));
            replyBytes += drainOutbound(ch);
            return replyBytes;
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    private long socks5Handshake() {
        Socks5AuthMethod method = scenario == Scenario.SOCKS5_PASSWORD
            ? Socks5AuthMethod.PASSWORD
            : Socks5AuthMethod.NO_AUTH;
        EmbeddedChannel ch = new EmbeddedChannel(Socks5ServerEncoder.DEFAULT, new Socks5InitialRequestDecoder());
        try {
            long replyBytes = 0;

            // 1. initial greeting -> method-selection response
            ch.writeInbound(Unpooled.wrappedBuffer(greetingBytes));
            ReferenceCountUtil.release(ch.readInbound()); // Socks5InitialRequest
            ch.writeOutbound(new DefaultSocks5InitialResponse(method));
            replyBytes += drainOutbound(ch);

            // 2. (PASSWORD only) username/password auth -> success, swapping in the auth decoder
            if (scenario == Scenario.SOCKS5_PASSWORD) {
                ch.pipeline().replace(Socks5InitialRequestDecoder.class, "authDecoder", new Socks5PasswordAuthRequestDecoder());
                ch.writeInbound(Unpooled.wrappedBuffer(authBytes));
                ReferenceCountUtil.release(ch.readInbound()); // Socks5PasswordAuthRequest
                ch.writeOutbound(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
                replyBytes += drainOutbound(ch);
                ch.pipeline().replace(Socks5PasswordAuthRequestDecoder.class, "commandDecoder", new Socks5CommandRequestDecoder());
            } else {
                ch.pipeline().replace(Socks5InitialRequestDecoder.class, "commandDecoder", new Socks5CommandRequestDecoder());
            }

            // 3. CONNECT command -> the exact success response Socks5ConnectHandler builds
            ch.writeInbound(Unpooled.wrappedBuffer(commandBytes));
            ReferenceCountUtil.release(ch.readInbound()); // Socks5CommandRequest
            ch.writeOutbound(new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS, Socks5AddressType.DOMAIN, SOCKS5_HOST, DST_PORT));
            replyBytes += drainOutbound(ch);

            return replyBytes;
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    /**
     * Control experiment: construct and close a fresh {@link EmbeddedChannel} with a single no-op handler
     * and do NO SOCKS work. Its {@code gc.alloc.rate.norm} is the per-op channel/pipeline construction
     * cost baked into {@link #handshake}; {@code channelPlumbingOnly ÷ handshake} is the plumbing share
     * (a lower bound — the channel does not escape here, so the JIT may elide more of it than it can when
     * the channel feeds the codecs).
     */
    @Benchmark
    public boolean channelPlumbingOnly() {
        EmbeddedChannel ch = new EmbeddedChannel(NoOpHandler.INSTANCE);
        try {
            return ch.isOpen();
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    /** Drain and release every outbound wire buffer, returning the total reply-byte count. */
    private static long drainOutbound(EmbeddedChannel ch) {
        long bytes = 0;
        ByteBuf out;
        while ((out = ch.readOutbound()) != null) {
            bytes += out.readableBytes();
            out.release();
        }
        return bytes;
    }

    @ChannelHandler.Sharable
    private static final class NoOpHandler extends ChannelInboundHandlerAdapter {
        static final NoOpHandler INSTANCE = new NoOpHandler();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ReferenceCountUtil.release(msg);
        }
    }
}
