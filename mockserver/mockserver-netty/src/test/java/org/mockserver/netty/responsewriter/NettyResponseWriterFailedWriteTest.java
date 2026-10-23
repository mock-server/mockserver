package org.mockserver.netty.responsewriter;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.InFlightRequest;
import org.mockserver.scheduler.Scheduler;

import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.Delay.delay;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * A keep-alive response whose write fails leaves the client waiting on a connection whose framing is unknown, so
 * the connection is closed.
 */
public class NettyResponseWriterFailedWriteTest {

    private final MockServerLogger mockServerLogger = new MockServerLogger(NettyResponseWriterFailedWriteTest.class);

    @Test
    public void shouldCloseKeepAliveConnectionAndCompleteInFlightRequestWhenResponseWriteFails() {
        // given
        WriteOutcome outcome = new WriteOutcome(false);
        EmbeddedChannel connection = new EmbeddedChannel(outcome, new ChannelInboundHandlerAdapter());
        AtomicInteger inFlight = new AtomicInteger();

        // when
        writer(connection, InFlightRequest.started(counterBackedServer(inFlight))).writeResponse(request("/").withKeepAlive(true), response("body"), false);

        // then
        assertThat(connection.isOpen(), is(false));
        assertThat(inFlight.get(), is(0));
        connection.finishAndReleaseAll();
    }

    @Test
    public void shouldCloseKeepAliveConnectionWhenChunkDelayedResponseHeadWriteFails() {
        // given
        WriteOutcome outcome = new WriteOutcome(false);
        EmbeddedChannel connection = new EmbeddedChannel(outcome, new ChannelInboundHandlerAdapter());
        AtomicInteger inFlight = new AtomicInteger();
        HttpResponse chunked = response("a body long enough to be written as several chunks")
            .withConnectionOptions(connectionOptions().withChunkSize(5).withChunkDelay(delay(MILLISECONDS, 1)));

        // when
        writer(connection, InFlightRequest.started(counterBackedServer(inFlight))).writeResponse(request("/").withKeepAlive(true), chunked, false);

        // then
        assertThat(connection.isOpen(), is(false));
        assertThat(inFlight.get(), is(0));
        connection.finishAndReleaseAll();
    }

    @Test
    public void shouldKeepKeepAliveConnectionOpenWhenResponseWriteSucceeds() {
        // given
        WriteOutcome outcome = new WriteOutcome(true);
        EmbeddedChannel connection = new EmbeddedChannel(outcome, new ChannelInboundHandlerAdapter());
        AtomicInteger inFlight = new AtomicInteger();

        // when
        writer(connection, InFlightRequest.started(counterBackedServer(inFlight))).writeResponse(request("/").withKeepAlive(true), response("body"), false);

        // then
        assertThat(connection.isOpen(), is(true));
        assertThat(inFlight.get(), is(0));
        connection.finishAndReleaseAll();
    }

    private NettyResponseWriter writer(EmbeddedChannel connection, InFlightRequest inFlightRequest) {
        ChannelHandlerContext ctx = connection.pipeline().lastContext();
        return new NettyResponseWriter(configuration(), mockServerLogger, ctx, mock(Scheduler.class), inFlightRequest);
    }

    private static LifeCycle counterBackedServer(AtomicInteger inFlight) {
        LifeCycle server = mock(LifeCycle.class);
        doAnswer(invocation -> inFlight.incrementAndGet()).when(server).requestProcessingStarted();
        doAnswer(invocation -> inFlight.decrementAndGet()).when(server).requestProcessingComplete();
        return server;
    }

    /**
     * Completes every write as an encoder would: releasing what it was given, then succeeding or failing.
     */
    private static final class WriteOutcome extends ChannelOutboundHandlerAdapter {
        private final boolean succeed;

        private WriteOutcome(boolean succeed) {
            this.succeed = succeed;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ReferenceCountUtil.release(msg);
            if (succeed) {
                promise.setSuccess();
            } else {
                promise.setFailure(new EncoderException("unable to encode response"));
            }
        }
    }
}
