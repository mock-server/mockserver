package org.mockserver.netty.http3;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.SegmentedDatagramPacket;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.quic.SegmentedDatagramPacketAllocator;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.action.http.HttpActionHandler;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * The HTTP/3 listener's segmented (GSO) sends: in place on epoll, absent on NIO, and given up for single datagrams,
 * without losing the response, once the kernel refuses one.
 */
@SuppressWarnings("deprecation") // NioEventLoopGroup deprecation in Netty 4.2
public class Http3ServerSegmentedSendTest {

    // many full-sized packets per flight, so the codec has equal-sized packets to segment
    private static final byte[] BODY = new byte[256 * 1024];

    static {
        new Random(541).nextBytes(BODY);
    }

    private final List<LogEntry> logged = new CopyOnWriteArrayList<>();
    private Configuration configuration;
    private MockServerLogger mockServerLogger;
    private Scheduler scheduler;
    private HttpState httpState;
    private HttpActionHandler httpActionHandler;
    private NioEventLoopGroup clientGroup;
    private Http3Server server;

    @Before
    public void createPipeline() {
        Assume.assumeTrue("native QUIC not available on this platform", Http3Server.isQuicAvailable());
        clientGroup = new NioEventLoopGroup(1);
    }

    @After
    public void stop() {
        if (server != null) {
            server.stop();
        }
        if (httpState != null) {
            httpState.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (clientGroup != null) {
            clientGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void shouldSendSegmentedOnEpoll() throws Exception {
        Assume.assumeTrue("epoll GSO not available on this platform", Epoll.isAvailable() && SegmentedDatagramPacket.isSupported());
        pipeline(configuration().useNativeTransport(true));
        start(new Http3Server(configuration, mockServerLogger, httpState, httpActionHandler, null, null));
        SegmentedSends sends = new SegmentedSends(false);
        server.listener().pipeline().addFirst(sends);

        assertThat(download(), is(BODY));

        assertThat("segmented sends", sends.seen.get(), greaterThan(0));
        assertThat(gsoWarnings(), hasSize(0));
    }

    @Test
    public void shouldSendOneDatagramPerPacketOnNio() throws Exception {
        pipeline(configuration().useNativeTransport(false));
        start(new Http3Server(configuration, mockServerLogger, httpState, httpActionHandler, null, null));
        SegmentedSends sends = new SegmentedSends(false);
        server.listener().pipeline().addFirst(sends);

        assertThat(download(), is(BODY));

        assertThat("segmented sends", sends.seen.get(), is(0));
    }

    @Test
    public void shouldSendOneDatagramPerPacketOnceASegmentedSendFailsAndStillDeliverEveryByte() throws Exception {
        pipeline(configuration());
        start(new Http3Server(configuration, mockServerLogger, httpState, httpActionHandler, null, null) {
            @Override
            SegmentedDatagramPacketAllocator segmentedAllocator(boolean epoll) {
                return new SegmentedDatagramPacketAllocator() {
                    @Override
                    public int maxNumSegments() {
                        return 16;
                    }

                    @Override
                    public DatagramPacket newPacket(ByteBuf buffer, int segmentSize, InetSocketAddress remoteAddress) {
                        return new io.netty.channel.unix.SegmentedDatagramPacket(buffer, segmentSize, remoteAddress);
                    }
                };
            }
        });
        // nearest the socket, so it refuses each segmented send as the kernel would
        SegmentedSends refused = new SegmentedSends(true);
        server.listener().pipeline().addFirst(refused);

        assertThat("QUIC resends what the refused sends lost", download(), is(BODY));
        int refusedSends = refused.seen.get();
        assertThat("refused segmented sends", refusedSends, greaterThan(0));
        assertThat(download(), is(BODY));

        assertThat("no segmented send after the first refused", refused.seen.get(), is(refusedSends));
        List<LogEntry> warnings = gsoWarnings();
        assertThat(warnings, hasSize(1));
        assertThat(warnings.get(0).getLogLevel(), is(Level.WARN));
        assertThat(warnings.get(0).getArguments()[1], is("java.io.IOException"));
        assertThat(server.listener().isOpen(), is(true));
    }

    private void start(Http3Server server) throws Exception {
        this.server = server;
        Http3TestServer.startWithHttp3(server);
    }

    private byte[] download() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", server.getPort());
        try (Http3TestClient client = Http3TestClient.open(clientGroup, address, TimeUnit.SECONDS.toMillis(15))) {
            Http3TestClient.Exchange exchange = client.send(new DefaultHttp3Headers()
                .method(HttpMethod.GET.asciiName())
                .scheme("https")
                .authority("127.0.0.1:" + address.getPort())
                .path("/big"));
            assertThat(exchange.status(), is(200));
            exchange.body();
            return exchange.receivedByteArray();
        }
    }

    private List<LogEntry> gsoWarnings() {
        return logged.stream()
            .filter(entry -> String.valueOf(entry.getMessageFormat()).startsWith("segmented (GSO) send failed"))
            .collect(Collectors.toList());
    }

    private static Configuration configuration() {
        return Configuration.configuration();
    }

    private void pipeline(Configuration configuration) {
        this.configuration = configuration;
        mockServerLogger = new MockServerLogger(configuration, Http3ServerSegmentedSendTest.class) {
            @Override
            public void logEvent(LogEntry logEntry) {
                logged.add(logEntry);
            }
        };
        scheduler = new Scheduler(configuration, mockServerLogger);
        httpState = new HttpState(configuration, mockServerLogger, scheduler);
        httpActionHandler = new HttpActionHandler(configuration, null, httpState, null, null);
        httpState.add(new Expectation(request().withPath("/big")).thenRespond(response().withBody(binary(BODY))));
    }

    private static final class SegmentedSends extends ChannelOutboundHandlerAdapter {

        private final boolean refuse;
        private final AtomicInteger seen = new AtomicInteger();

        private SegmentedSends(boolean refuse) {
            this.refuse = refuse;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof io.netty.channel.unix.SegmentedDatagramPacket) {
                seen.incrementAndGet();
                if (refuse) {
                    ReferenceCountUtil.release(msg);
                    promise.setFailure(new IOException("sendmsg(..) failed: Input/output error"));
                    return;
                }
            }
            ctx.write(msg, promise);
        }
    }
}
