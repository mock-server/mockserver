package org.mockserver.socket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalChannel;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public class ConnectionClosingTest {

    @Test
    public void shouldNotCreateTheAttributeWhenAskedWhetherMarked() {
        EmbeddedChannel channel = new EmbeddedChannel();

        assertThat(ConnectionClosing.isMarked(channel), is(false));
        assertThat("reading the mark must never allocate its attribute", channel.hasAttr(io.netty.util.AttributeKey.valueOf("mockserver.connectionClosing")), is(false));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldFireTheEventOnceFromThePipelineHeadWhenMarkedOnTheEventLoop() {
        EventRecorder recorder = new EventRecorder();
        EmbeddedChannel channel = new EmbeddedChannel(recorder);

        ConnectionClosing.mark(channel);
        ConnectionClosing.mark(channel);

        assertThat(ConnectionClosing.isMarked(channel), is(true));
        assertThat(recorder.events, contains(ConnectionClosingEvent.INSTANCE));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldFireTheEventOnTheEventLoopWhenMarkedFromAnotherThreadAndPassALingeringDiscard() throws Exception {
        DefaultEventLoopGroup group = new DefaultEventLoopGroup(1);
        try {
            CountDownLatch fired = new CountDownLatch(1);
            List<Boolean> onEventLoop = new CopyOnWriteArrayList<>();
            Channel channel = new LocalChannel();
            channel.pipeline().addLast(LingeringClose.DiscardInbound.INSTANCE, new ChannelInboundHandlerAdapter() {
                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                    if (evt == ConnectionClosingEvent.INSTANCE) {
                        onEventLoop.add(ctx.channel().eventLoop().inEventLoop());
                        fired.countDown();
                    }
                }
            });
            group.register(channel).sync();

            ConnectionClosing.mark(channel);

            assertThat("visible at once to the marking thread", ConnectionClosing.isMarked(channel), is(true));
            assertThat(fired.await(5, TimeUnit.SECONDS), is(true));
            assertThat(onEventLoop, contains(true));
            channel.close().sync();
        } finally {
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }

    @Test
    public void shouldMarkALingeringClose() {
        EventRecorder recorder = new EventRecorder();
        EmbeddedChannel channel = new EmbeddedChannel(recorder);

        LingeringClose.close(channel);

        assertThat(ConnectionClosing.isMarked(channel), is(true));
        assertThat(recorder.events, contains(ConnectionClosingEvent.INSTANCE));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldMarkAReset() {
        EventRecorder recorder = new EventRecorder();
        EmbeddedChannel channel = new EmbeddedChannel(recorder);

        ConnectionReset.reset(channel);

        assertThat(ConnectionClosing.isMarked(channel), is(true));
        assertThat(recorder.events, contains(ConnectionClosingEvent.INSTANCE));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldNotFireForAChannelNotYetRegistered() {
        EmbeddedChannel channel = new EmbeddedChannel(false, false);
        EventRecorder recorder = new EventRecorder();
        channel.pipeline().addLast(recorder);

        ConnectionClosing.mark(channel);

        assertThat(ConnectionClosing.isMarked(channel), is(true));
        assertThat(recorder.events, empty());
    }

    private static final class EventRecorder extends ChannelInboundHandlerAdapter {
        private final List<Object> events = new CopyOnWriteArrayList<>();

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            events.add(evt);
            ctx.fireUserEventTriggered(evt);
        }
    }
}
