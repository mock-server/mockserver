package org.mockserver.mock.action.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Unit tests for {@link HttpErrorActionHandler} applying {@code resetConnection}. That the client sees a TCP
 * RST is proven against real sockets in {@code ResetConnectionIntegrationTest} (mockserver-netty); these pin
 * down the pipeline behaviour an {@link EmbeddedChannel} can show: the close starts below every outbound
 * handler (so an {@code SslHandler} sends no {@code close_notify}), and it wins over the other options.
 */
public class HttpErrorActionHandlerResetConnectionTest {

    @Test
    public void shouldCloseBelowOutboundHandlersOnReset() {
        // given - an outbound handler standing in for SslHandler, which would send close_notify on close
        CloseRecorder closeRecorder = new CloseRecorder();
        EmbeddedChannel channel = new EmbeddedChannel(closeRecorder, new ChannelInboundHandlerAdapter());
        AtomicBoolean ended = new AtomicBoolean();

        // when
        new HttpErrorActionHandler().handle(HttpError.error().withResetConnection(true), new HttpRequest(), channel.pipeline().lastContext(), () -> ended.set(true));

        // then
        assertThat(channel.isOpen(), is(false));
        assertThat("the reset must not pass through outbound handlers", closeRecorder.closed, is(false));
        assertThat(ended.get(), is(true));
    }

    @Test
    public void shouldCloseThroughOutboundHandlersOnDropConnection() {
        // given
        CloseRecorder closeRecorder = new CloseRecorder();
        EmbeddedChannel channel = new EmbeddedChannel(closeRecorder, new ChannelInboundHandlerAdapter());

        // when
        new HttpErrorActionHandler().handle(HttpError.error().withDropConnection(true), new HttpRequest(), channel.pipeline().lastContext());

        // then - the unchanged clean close still goes through them
        assertThat(channel.isOpen(), is(false));
        assertThat(closeRecorder.closed, is(true));
    }

    @Test
    public void shouldResetAheadOfStreamErrorAndDropConnection() {
        // given
        CloseRecorder closeRecorder = new CloseRecorder();
        EmbeddedChannel channel = new EmbeddedChannel(closeRecorder, new ChannelInboundHandlerAdapter());
        HttpError httpError = HttpError.error()
            .withResetConnection(true)
            .withDropConnection(true)
            .withStreamError(HttpError.StreamErrorCode.REFUSED_STREAM);

        // when
        new HttpErrorActionHandler().handle(httpError, new HttpRequest(), channel.pipeline().lastContext());

        // then
        assertThat(channel.isOpen(), is(false));
        assertThat(closeRecorder.closed, is(false));
    }

    @Test
    public void shouldLeaveConnectionOpenWhenResetConnectionFalse() {
        // given
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());

        // when
        new HttpErrorActionHandler().handle(HttpError.error().withResetConnection(false), new HttpRequest(), channel.pipeline().lastContext());

        // then
        assertThat(channel.isOpen(), is(true));
        channel.finishAndReleaseAll();
    }

    private static class CloseRecorder extends ChannelOutboundHandlerAdapter {
        private volatile boolean closed;

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            closed = true;
            super.close(ctx, promise);
        }
    }
}
