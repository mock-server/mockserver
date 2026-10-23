package org.mockserver.netty.responsewriter;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.grpc.GrpcProtoDescriptorStore;
import org.mockserver.mock.action.http.GrpcStreamResponseActionHandler;
import org.mockserver.mock.action.http.HttpErrorActionHandler;
import org.mockserver.model.GrpcStreamResponse;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.StreamingBody;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.ConnectionClosing;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.Delay.delay;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Every response path that closes an HTTP/1.1 connection after its response tells the connection's pipelining gate
 * before writing that response, by a {@code Connection: close} head or a {@link ConnectionClosing} mark, so the gate
 * never releases a pipelined request into a connection about to close. A response that does not close marks nothing,
 * and one whose head says {@code Connection: close} needs no mark, so neither allocates one.
 */
public class ConnectionClosingCoverageTest {

    private final Scheduler scheduler = mock(Scheduler.class);

    @Test
    public void shouldMarkAClosingResponseWhoseHeadIsKeptAlive() {
        assertMarked(keepAlive(), closing().withKeepAliveOverride(true), true);
    }

    @Test
    public void shouldMarkAClosingResponseWithNoConnectionHeader() {
        assertMarked(keepAlive(), closing().withSuppressConnectionHeader(true), true);
    }

    @Test
    public void shouldMarkADelayedCloseWhoseHeadIsKeptAlive() {
        assertMarked(keepAlive(), closing().withKeepAliveOverride(true).withCloseSocketDelay(delay(TimeUnit.SECONDS, 1)), true);
    }

    @Test
    public void shouldMarkAChunkDelayedClosingResponseWhoseHeadIsKeptAlive() {
        assertMarked(keepAlive(), closing().withKeepAliveOverride(true).withChunkSize(2).withChunkDelay(delay(TimeUnit.MILLISECONDS, 1)), true);
    }

    @Test
    public void shouldMarkAClosingFinalInformationalResponseWhoseHeadIsKeptAlive() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter());
        try {
            writer(channel).writeResponse(keepAlive(), response().withStatusCode(102).withConnectionOptions(closing().withKeepAliveOverride(true)), false);
            assertThat(ConnectionClosing.isMarked(channel), is(true));
        } finally {
            close(channel);
        }
    }

    @Test
    public void shouldNotMarkWhatACloseHeadAlreadyAnnounces() {
        assertMarked(request("/notKeptAlive"), null, false);
        assertMarked(keepAlive(), closing().withKeepAliveOverride(false), false);
    }

    @Test
    public void shouldNotMarkAResponseThatKeepsTheConnection() {
        assertMarked(keepAlive(), null, false);
        assertMarked(keepAlive(), connectionOptions().withCloseSocket(false), false);
    }

    @Test
    public void shouldMarkAClosingStreamedResponseWhoseHeadIsKeptAlive() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter());
        try {
            StreamingBody streamingBody = new StreamingBody(1024);
            streamingBody.setEventLoop(channel.eventLoop());
            HttpResponse response = response()
                .withStreamingBody(streamingBody)
                .withConnectionOptions(closing().withKeepAliveOverride(true));
            channel.eventLoop().execute(() -> writer(channel).writeResponse(keepAlive(), response, false));
            channel.runPendingTasks();
            assertThat("not before the stream ends", ConnectionClosing.isMarked(channel), is(false));

            channel.eventLoop().execute(() -> streamingBody.addChunk(Unpooled.copiedBuffer("chunk", StandardCharsets.UTF_8)));
            channel.runPendingTasks();
            channel.eventLoop().execute(streamingBody::complete);
            channel.runPendingTasks();

            assertThat(ConnectionClosing.isMarked(channel), is(true));
        } finally {
            close(channel);
        }
    }

    @Test
    public void shouldMarkAStreamedResponseThatFailsMidStream() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter());
        try {
            StreamingBody streamingBody = new StreamingBody(1024);
            streamingBody.setEventLoop(channel.eventLoop());
            HttpResponse response = response().withStreamingBody(streamingBody);
            channel.eventLoop().execute(() -> writer(channel).writeResponse(keepAlive(), response, false));
            channel.runPendingTasks();
            assertThat("not before the stream fails", ConnectionClosing.isMarked(channel), is(false));

            channel.eventLoop().execute(() -> streamingBody.error(new IllegalStateException("upstream failed")));
            channel.runPendingTasks();

            assertThat("the failed stream's close follows its last write", ConnectionClosing.isMarked(channel), is(true));
        } finally {
            close(channel);
        }
    }

    @Test
    public void shouldMarkAGrpcStreamThatClosesTheConnectionAfterItsTrailers() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter());
        try {
            grpcStreamHandler().handle(GrpcStreamResponse.grpcStreamResponse().withCloseConnection(true), channel.pipeline().firstContext(), request("/some.Service/Method"));
            channel.runPendingTasks();
            assertThat(ConnectionClosing.isMarked(channel), is(true));
        } finally {
            close(channel);
        }
    }

    @Test
    public void shouldNotMarkAGrpcStreamThatKeepsTheConnection() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter());
        try {
            grpcStreamHandler().handle(GrpcStreamResponse.grpcStreamResponse(), channel.pipeline().firstContext(), request("/some.Service/Method"));
            channel.runPendingTasks();
            assertThat(ConnectionClosing.isMarked(channel), is(false));
        } finally {
            close(channel);
        }
    }

    private static GrpcStreamResponseActionHandler grpcStreamHandler() {
        return new GrpcStreamResponseActionHandler(
            new MockServerLogger(ConnectionClosingCoverageTest.class),
            mock(Scheduler.class),
            mock(GrpcProtoDescriptorStore.class),
            configuration(),
            mock(WebSocketClientRegistry.class));
    }

    @Test
    public void shouldMarkAnHttp1ErrorThatDropsTheConnection() {
        assertErrorMarked(error().withDropConnection(true), true);
        assertErrorMarked(error().withResetConnection(true), true);
        assertErrorMarked(error().withStreamError(8L), true);
        assertErrorMarked(error().withResponseBytes("raw".getBytes(StandardCharsets.US_ASCII)).withDropConnection(true), true);
    }

    @Test
    public void shouldNotMarkAnErrorThatLeavesTheConnectionOpen() {
        assertErrorMarked(error(), false);
        assertErrorMarked(error().withResponseBytes("raw".getBytes(StandardCharsets.US_ASCII)), false);
    }

    private void assertMarked(HttpRequest request, ConnectionOptions options, boolean marked) {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter());
        try {
            HttpResponse response = response("body");
            if (options != null) {
                response.withConnectionOptions(options);
            }
            writer(channel).writeResponse(request, response, false);
            channel.runPendingTasks();
            assertThat("marked closing for " + request.isKeepAlive() + " " + options, ConnectionClosing.isMarked(channel), is(marked));
        } finally {
            close(channel);
        }
    }

    private void assertErrorMarked(HttpError httpError, boolean marked) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerCodec(), new ChannelOutboundHandlerAdapter());
        try {
            new HttpErrorActionHandler().handle(httpError, keepAlive(), channel.pipeline().lastContext());
            channel.runPendingTasks();
            assertThat("marked closing for " + httpError, ConnectionClosing.isMarked(channel), is(marked));
        } finally {
            close(channel);
        }
    }

    private NettyResponseWriter writer(EmbeddedChannel channel) {
        return new NettyResponseWriter(configuration(), new MockServerLogger(), channel.pipeline().firstContext(), scheduler);
    }

    private static HttpRequest keepAlive() {
        return request("/keptAlive").withKeepAlive(true);
    }

    private static ConnectionOptions closing() {
        return connectionOptions().withCloseSocket(true);
    }

    private static void close(EmbeddedChannel channel) {
        for (Object outbound = channel.readOutbound(); outbound != null; outbound = channel.readOutbound()) {
            ReferenceCountUtil.release(outbound);
        }
        channel.finishAndReleaseAll();
    }
}
