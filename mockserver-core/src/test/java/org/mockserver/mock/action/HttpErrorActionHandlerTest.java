package org.mockserver.mock.action;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpServerCodec;
import org.junit.Test;
import org.mockserver.model.HttpError;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockserver.model.HttpError.error;

/**
 * @author jamesdbloom
 */
public class HttpErrorActionHandlerTest {

    @Test
    public void shouldDropConnection() {
        // given
        ChannelHandlerContext mockChannelHandlerContext = mock(ChannelHandlerContext.class);
        HttpError httpError = error().withDropConnection(true);

        // when
        new HttpErrorActionHandler().handle(httpError, mockChannelHandlerContext);

        // then
        verify(mockChannelHandlerContext).close();
    }

    @Test
    public void shouldReturnBytes() {
        // given
        ChannelHandlerContext mockChannelHandlerContext = mock(ChannelHandlerContext.class);
        ChannelPipeline mockChannelPipeline = mock(ChannelPipeline.class);
        ChannelFuture mockChannelFuture = mock(ChannelFuture.class);

        when(mockChannelHandlerContext.pipeline()).thenReturn(mockChannelPipeline);
        when(mockChannelPipeline.context(HttpServerCodec.class)).thenReturn(mockChannelHandlerContext);
        when(mockChannelHandlerContext.writeAndFlush(any(ByteBuf.class))).thenReturn(mockChannelFuture);

        // when
        new HttpErrorActionHandler().handle(
                error()
                        .withResponseBytes("some_bytes".getBytes()),
                mockChannelHandlerContext
        );

        // then
        verify(mockChannelHandlerContext).pipeline();
        verify(mockChannelPipeline).context(HttpServerCodec.class);
        verify(mockChannelHandlerContext).writeAndFlush(Unpooled.wrappedBuffer("some_bytes".getBytes()));
        verify(mockChannelFuture).awaitUninterruptibly();
    }
}