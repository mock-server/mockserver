package org.mockserver.benchmark;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.util.ReferenceCountUtil;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.netty.MockServer;
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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Bytes allocated per HTTP/2 request, end to end, through the multiplex pipeline: every op opens a fresh
 * stream on ONE h2c connection, sends a {@code POST} and waits for the matched {@code 200}. Each stream is
 * a child channel whose pipeline {@code Http2MultiplexChildInitializer} builds, so per-stream pipeline
 * construction is part of every op.
 *
 * <p>{@code bodySize}: {@code SMALL} (a 64-byte body) and {@code OVER_WINDOW} (256 KiB, larger than the
 * 65,535-byte HTTP/2 flow-control window, so the transfer needs WINDOW_UPDATEs).</p>
 *
 * <p>JMH's {@code -prof gc} counts allocation on ALL threads, so {@code gc.alloc.rate.norm} is the whole
 * in-process JVM per request: this driver, the server's event loops and its action thread. Use it as an
 * A/B figure (same harness, before and after a server change), not as the server's absolute cost.</p>
 *
 * <pre>./run.sh -prof gc Http2StreamAllocationBenchmark</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(1)
public class Http2StreamAllocationBenchmark {

    private static final String PATH = "/alloc";
    private static final String RESPONSE_BODY = "ok";
    private static final int OVER_WINDOW_BYTES = 256 * 1024;

    @Param({"SMALL", "OVER_WINDOW"})
    public String bodySize;

    private MockServer mockServer;
    private MockServerClient mockServerClient;
    private NioEventLoopGroup group;
    private Channel connection;
    private Http2Headers headers;
    private byte[] body;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        ConfigurationProperties.logLevel("WARN");
        mockServer = new MockServer();
        int port = mockServer.getLocalPort();
        mockServerClient = new MockServerClient("localhost", port);
        mockServerClient
            .when(request().withMethod("POST").withPath(PATH))
            .respond(response().withStatusCode(200).withBody(RESPONSE_BODY));

        body = new byte["SMALL".equals(bodySize) ? 64 : OVER_WINDOW_BYTES];
        Arrays.fill(body, (byte) 'x');
        if (body.length <= 65_535 && !"SMALL".equals(bodySize)) {
            throw new IllegalStateException("OVER_WINDOW body must exceed the 65,535-byte flow-control window");
        }
        headers = new DefaultHttp2Headers()
            .method(HttpMethod.POST.asciiName())
            .scheme(HttpScheme.HTTP.name())
            .authority("localhost:" + port)
            .path(PATH)
            .set("content-type", "text/plain");

        group = new NioEventLoopGroup(1);
        connection = new Bootstrap()
            .group(group)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build());
                    ch.pipeline().addLast(new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()));
                }
            })
            .connect("localhost", port)
            .sync()
            .channel();
        // fail the trial, rather than measure a broken harness, if the first request does not succeed
        postOnNewStream();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close().awaitUninterruptibly(2, TimeUnit.SECONDS);
        }
        if (group != null) {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        }
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
    }

    @Benchmark
    public int postOnNewStream() throws Exception {
        CompletableFuture<Integer> done = new CompletableFuture<>();
        Http2StreamChannel stream = new Http2StreamChannelBootstrap(connection)
            .handler(new ChannelInboundHandlerAdapter() {
                private int status;
                private final StringBuilder received = new StringBuilder();

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    try {
                        if (msg instanceof Http2HeadersFrame) {
                            Http2HeadersFrame frame = (Http2HeadersFrame) msg;
                            if (frame.headers().status() != null) {
                                status = Integer.parseInt(frame.headers().status().toString());
                            }
                            if (frame.isEndStream()) {
                                finish();
                            }
                        } else if (msg instanceof Http2DataFrame) {
                            Http2DataFrame data = (Http2DataFrame) msg;
                            received.append(data.content().toString(StandardCharsets.UTF_8));
                            if (data.isEndStream()) {
                                finish();
                            }
                        } else if (msg instanceof Http2ResetFrame) {
                            done.completeExceptionally(new IllegalStateException("RST_STREAM " + ((Http2ResetFrame) msg).errorCode()));
                        }
                    } finally {
                        ReferenceCountUtil.release(msg);
                    }
                }

                private void finish() {
                    if (status != 200 || !RESPONSE_BODY.contentEquals(received)) {
                        done.completeExceptionally(new IllegalStateException("unexpected response " + status + " <" + received + ">"));
                    } else {
                        done.complete(status);
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    done.completeExceptionally(cause);
                }
            })
            .open()
            .sync()
            .getNow();
        try {
            stream.write(new DefaultHttp2HeadersFrame(headers, false));
            stream.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(body), true));
            return done.get(30, TimeUnit.SECONDS);
        } finally {
            stream.close();
        }
    }
}
