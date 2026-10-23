package org.mockserver.socket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Reads stay paused until every holder has released its hold, and no handler can read past a pause by
 * requesting a read itself.
 */
public class ChannelReadPauseTest {

    @Test
    public void shouldResumeOnlyWhenTheLastHoldIsReleased() {
        ReadCountingChannel channel = new ReadCountingChannel();

        ChannelReadPause.pause(channel);
        ChannelReadPause.pause(channel);
        assertThat(channel.config().isAutoRead(), is(false));

        ChannelReadPause.resume(channel);
        assertThat("one holder remains", channel.config().isAutoRead(), is(false));

        int readsBefore = channel.reads;
        ChannelReadPause.resume(channel);
        assertThat(channel.config().isAutoRead(), is(true));
        assertThat("resuming issues a read", channel.reads, is(readsBefore + 1));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldReadWhenTheLastHoldIsReleasedAfterAutoReadWasTurnedOnUnderIt() {
        // a lingering close or connection reset turns auto-read on while a hold remains; the read
        // that triggers is dropped by the gate, so the last resume must issue one itself
        ReadCountingChannel channel = new ReadCountingChannel();
        ChannelReadPause.pause(channel);
        channel.config().setAutoRead(true);

        int readsBefore = channel.reads;
        ChannelReadPause.resume(channel);

        assertThat(channel.config().isAutoRead(), is(true));
        assertThat("resuming issues a read", channel.reads, is(readsBefore + 1));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldIgnoreAnUnbalancedResume() {
        ReadCountingChannel channel = new ReadCountingChannel();

        ChannelReadPause.resume(channel);
        ChannelReadPause.pause(channel);
        ChannelReadPause.resume(channel);
        ChannelReadPause.resume(channel);
        ChannelReadPause.pause(channel);

        assertThat(channel.config().isAutoRead(), is(false));
        assertThat(ChannelReadPause.holds(channel), is(1));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldDropReadsRequestedByHandlersWhilePaused() {
        ReadCountingChannel channel = new ReadCountingChannel();
        ChannelReadPause.pause(channel);

        int readsBefore = channel.reads;
        // e.g. a decoder that saw no complete message, or an HttpContentDecoder in a WebSocket pipeline
        channel.pipeline().lastContext().read();
        channel.read();
        assertThat(channel.reads, is(readsBefore));

        ChannelReadPause.resume(channel);
        int readsAfterResume = channel.reads;
        channel.pipeline().lastContext().read();
        assertThat(channel.reads, is(readsAfterResume + 1));
        channel.finishAndReleaseAll();
    }

    private static final class ReadCountingChannel extends EmbeddedChannel {
        private int reads;

        private ReadCountingChannel() {
            super(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    ctx.fireChannelRead(msg);
                }
            });
        }

        @Override
        protected void doBeginRead() throws Exception {
            reads++;
            super.doBeginRead();
        }
    }
}
