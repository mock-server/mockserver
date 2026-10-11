package org.mockserver.netty.connection;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.metrics.Metrics;

import java.nio.charset.StandardCharsets;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.netty.connection.RecordedDurations.READ_TO_DECODE;
import static org.mockserver.netty.connection.RecordedDurations.TRANSPORT;
import static org.mockserver.netty.connection.RecordedDurations.count;
import static org.mockserver.netty.connection.RecordedDurations.countOver;
import static org.mockserver.netty.connection.RecordedDurations.sum;

/**
 * {@code mock_server_request_read_to_decode_duration_seconds}: the read marker before a real {@link HttpServerCodec}
 * and the transport timer after it, fed raw bytes as the socket would deliver them.
 */
public class HttpReadToDecodeTimingTest {

    private static final long GAP_MILLIS = 60;
    private static final double UNDER_GAP_SECONDS = 0.05;
    private static final String GET = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n";

    private EmbeddedChannel channel;
    private long firstRequestHandlingMillis;

    @Before
    public void enableMetrics() {
        Metrics.resetAdditionalMetricsForTesting();
        new Metrics(configuration().metricsEnabled(true));
        HttpTransportTimer timer = new HttpTransportTimer();
        channel = new EmbeddedChannel(timer.readMarker(), new HttpServerCodec(), timer, new ChannelInboundHandlerAdapter() {
            private boolean handledOne;

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                // stands in for a request handled on the event loop before the next pipelined one is decoded
                if (msg instanceof HttpRequest && !handledOne) {
                    handledOne = true;
                    Thread.sleep(firstRequestHandlingMillis);
                }
                ReferenceCountUtil.release(msg);
            }
        });
    }

    @After
    public void resetMetrics() {
        channel.finishAndReleaseAll();
        Metrics.resetAdditionalMetricsForTesting();
    }

    @Test
    public void shouldTimeEachRequestOnceWithinTheWholeExchange() {
        long start = System.nanoTime();
        read(GET);
        respond();
        double exchangeSeconds = (System.nanoTime() - start) / 1_000_000_000.0;

        assertThat(count(READ_TO_DECODE), is(1L));
        assertThat(sum(READ_TO_DECODE), greaterThanOrEqualTo(0.0));
        assertThat("the read-to-decode span ends where the transport span starts, so both fit inside the exchange",
            sum(READ_TO_DECODE) + sum(TRANSPORT), lessThanOrEqualTo(exchangeSeconds));
    }

    @Test
    public void shouldStartAHeadSplitAcrossReadsAtItsFirstRead() throws InterruptedException {
        read("GET / HTTP/1.1\r\nHo");
        Thread.sleep(GAP_MILLIS);
        read("st: localhost\r\n\r\n");

        assertThat(count(READ_TO_DECODE), is(1L));
        assertThat(countOver(READ_TO_DECODE, UNDER_GAP_SECONDS), is(1L));
    }

    @Test
    public void shouldStartAKeepAliveRequestAtItsOwnRead() throws InterruptedException {
        read(GET);
        respond();
        Thread.sleep(GAP_MILLIS);
        read(GET);

        assertThat(count(READ_TO_DECODE), is(2L));
        assertThat(countOver(READ_TO_DECODE, UNDER_GAP_SECONDS), is(0L));
    }

    @Test
    public void shouldStartASplitHeadOfAKeepAliveRequestAtItsFirstRead() throws InterruptedException {
        read(GET);
        respond();
        read("GET / HTTP/1.1\r\nHo");
        Thread.sleep(GAP_MILLIS);
        read("st: localhost\r\n\r\n");

        assertThat(count(READ_TO_DECODE), is(2L));
        assertThat(countOver(READ_TO_DECODE, UNDER_GAP_SECONDS), is(1L));
    }

    @Test
    public void shouldStartARequestThatFollowsABodyInTheSameReadAtThatRead() throws InterruptedException {
        read("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\n");
        Thread.sleep(GAP_MILLIS);
        read("hello" + GET);

        assertThat(count(READ_TO_DECODE), is(2L));
        assertThat("neither waited: the first head was decoded from its read, the second from the read after the gap",
            countOver(READ_TO_DECODE, UNDER_GAP_SECONDS), is(0L));
    }

    @Test
    public void shouldNotStartTheNextRequestAtARequestBodyRead() throws InterruptedException {
        read("POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\n");
        read("hello");
        respond();
        Thread.sleep(GAP_MILLIS);
        read(GET);

        assertThat(count(READ_TO_DECODE), is(2L));
        assertThat("the second request started at its own read, not at the body read before the gap",
            countOver(READ_TO_DECODE, UNDER_GAP_SECONDS), is(0L));
    }

    @Test
    public void shouldIncludeHandlingOfAPipelinedRequestAheadInTheSameRead() {
        firstRequestHandlingMillis = GAP_MILLIS;

        read(GET + GET);

        assertThat(count(READ_TO_DECODE), is(2L));
        assertThat("the second waited for the first to be handled", countOver(READ_TO_DECODE, UNDER_GAP_SECONDS), is(1L));
        assertThat(count(TRANSPORT), is(0L));
    }

    @Test
    public void shouldRemoveTheReadMarkerWithTheTimerWhenTheConnectionSwitchesProtocols() {
        read(GET);

        channel.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS));

        assertThat(channel.pipeline().names(), everyItem(not(containsString("HttpTransportTimer"))));
        assertThat(count(READ_TO_DECODE), greaterThan(0L));
    }

    @Test
    public void shouldNotTimeReadToDecodeWithoutTheReadMarker() {
        EmbeddedChannel unmarked = new EmbeddedChannel(new HttpServerCodec(), new HttpTransportTimer());
        try {
            unmarked.writeInbound(Unpooled.copiedBuffer(GET, StandardCharsets.US_ASCII));
            unmarked.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK));

            assertThat(count(TRANSPORT), is(1L));
            assertThat(count(READ_TO_DECODE), is(0L));
        } finally {
            unmarked.finishAndReleaseAll();
        }
    }

    private void read(String bytes) {
        channel.writeInbound(Unpooled.copiedBuffer(bytes, StandardCharsets.US_ASCII));
    }

    private void respond() {
        channel.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK));
    }
}
