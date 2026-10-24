package org.mockserver.netty.http3;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3ErrorCode;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.StreamingBody;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * A response the scheduler releases off the QUIC event loop (after a delay) is written on the stream's loop as one
 * task, by the plain and the gRPC writer; a failure in that task resets the stream, and a loop that has shut down
 * closes a streamed body's upstream.
 */
public class Http3StreamEventLoopHopTest {

    private static final Configuration CONFIGURATION = configuration();
    private static final MockServerLogger LOGGER = new MockServerLogger(Http3StreamEventLoopHopTest.class);

    private final List<Runnable> tasks = new ArrayList<>();
    private boolean onLoop;
    private final List<Object> written = new ArrayList<>();
    private final QuicStreamChannel stream = mock(QuicStreamChannel.class);

    private ChannelHandlerContext offLoopContext() {
        EventExecutor executor = mock(EventExecutor.class);
        when(executor.inEventLoop()).thenAnswer(invocation -> onLoop);
        doAnswer(invocation -> tasks.add(invocation.getArgument(0))).when(executor).execute(any(Runnable.class));
        return contextOn(executor);
    }

    private ChannelHandlerContext contextOn(EventExecutor executor) {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.executor()).thenReturn(executor);
        when(ctx.channel()).thenReturn(stream);
        when(stream.isActive()).thenReturn(true);
        when(stream.closeFuture()).thenReturn(mock(ChannelFuture.class));
        ChannelFuture future = mock(ChannelFuture.class);
        when(future.addListener(any())).thenReturn(future);
        doAnswer(invocation -> {
            written.add(invocation.getArgument(0));
            return future;
        }).when(ctx).write(any());
        doAnswer(invocation -> {
            written.add(invocation.getArgument(0));
            return future;
        }).when(ctx).writeAndFlush(any());
        return ctx;
    }

    /**
     * Runs a task the writer handed to the stream's event loop, as that loop would: with {@code inEventLoop()} true.
     */
    private void runOnLoop(int task) {
        onLoop = true;
        try {
            tasks.get(task).run();
        } finally {
            onLoop = false;
        }
    }

    private void releaseWritten() {
        for (Object frame : written) {
            ReferenceCountUtil.release(frame);
        }
    }

    @Test
    public void shouldWriteAResponseReleasedOffTheEventLoopAsOneTaskOnTheStreamsLoop() {
        ChannelHandlerContext ctx = offLoopContext();
        Http3ResponseWriter writer = new Http3ResponseWriter(CONFIGURATION, LOGGER, ctx);

        writer.sendResponse(request("/delayed"), response("delayed"));

        assertThat("started before it is written, so a failure is not answered twice", writer.isResponseStarted(), is(true));
        assertThat("nothing is written from the scheduler's thread", written.isEmpty(), is(true));
        assertThat(tasks.size(), is(1));

        runOnLoop(0);

        assertThat(written.size(), is(2));
        assertThat(written.get(0), instanceOf(DefaultHttp3HeadersFrame.class));
        assertThat(((DefaultHttp3HeadersFrame) written.get(0)).headers().status().toString(), is("200"));
        assertThat(written.get(1), instanceOf(DefaultHttp3DataFrame.class));
        releaseWritten();
    }

    @Test
    public void shouldResetTheStreamWhenTheHoppedWriteFails() {
        ChannelHandlerContext ctx = offLoopContext();
        doThrow(new IllegalStateException("write failed")).when(ctx).write(any());
        Http3ResponseWriter writer = new Http3ResponseWriter(CONFIGURATION, LOGGER, ctx);

        writer.sendResponse(request("/delayed"), response("delayed"));
        runOnLoop(0);

        verify(stream).shutdownOutput((int) Http3ErrorCode.H3_INTERNAL_ERROR.code());
    }

    @Test
    public void shouldCloseAStreamedBodysUpstreamWhenTheEventLoopHasShutDown() {
        EventExecutor executor = mock(EventExecutor.class);
        when(executor.inEventLoop()).thenReturn(false);
        doThrow(new RejectedExecutionException("event loop shut down")).when(executor).execute(any(Runnable.class));
        ChannelHandlerContext ctx = contextOn(executor);
        StreamingBody streamingBody = new StreamingBody(8192);
        boolean[] upstreamClosed = {false};
        streamingBody.setUpstreamCloser(() -> upstreamClosed[0] = true);

        new Http3ResponseWriter(CONFIGURATION, LOGGER, ctx).sendResponse(request("/stream"), response().withStreamingBody(streamingBody));

        assertThat(upstreamClosed[0], is(true));
        assertThat(written.isEmpty(), is(true));
        ByteBuf late = io.netty.buffer.Unpooled.buffer(4).writeZero(4);
        streamingBody.addChunk(late);
        late.release();
    }

    @Test
    public void shouldResetTheStreamOnItsEventLoopWhenAskedFromAnotherThread() {
        ChannelHandlerContext ctx = offLoopContext();
        Http3ResponseWriter writer = new Http3ResponseWriter(CONFIGURATION, LOGGER, ctx);

        writer.writeStreamError(Http3ErrorCode.H3_REQUEST_CANCELLED.code());

        assertThat(writer.isResponseStarted(), is(true));
        verify(stream, never()).shutdownOutput(anyInt());
        runOnLoop(0);
        verify(stream).shutdownOutput((int) Http3ErrorCode.H3_REQUEST_CANCELLED.code());
    }

    @Test
    public void shouldWriteADelayedGrpcResponseOnTheStreamsLoop() {
        ChannelHandlerContext ctx = offLoopContext();
        Http3GrpcResponseWriter writer = new Http3GrpcResponseWriter(CONFIGURATION, LOGGER, ctx, null, null, null);

        writer.sendResponse(request("/svc/Method"), response().withHeader("grpc-status", "0"));

        assertThat(written.isEmpty(), is(true));
        assertThat(tasks.size(), is(1));
        runOnLoop(0);
        assertThat(written.size(), is(1));
        assertThat(((DefaultHttp3HeadersFrame) written.get(0)).headers().get("grpc-status").toString(), is("0"));

        // answered once: a second, late response is dropped on the loop
        writer.sendResponse(request("/svc/Method"), response().withHeader("grpc-status", "0"));
        runOnLoop(1);
        assertThat(written.size(), is(1));
        releaseWritten();
    }

    @Test
    public void shouldNotHopAnUndelayedResponse() {
        EventExecutor executor = mock(EventExecutor.class);
        when(executor.inEventLoop()).thenReturn(true);
        ChannelHandlerContext ctx = contextOn(executor);

        new Http3ResponseWriter(CONFIGURATION, LOGGER, ctx).sendResponse(request("/now"), response("now"));

        verify(executor, never()).execute(any(Runnable.class));
        assertThat(written.size(), is(2));
        releaseWritten();
    }
}
