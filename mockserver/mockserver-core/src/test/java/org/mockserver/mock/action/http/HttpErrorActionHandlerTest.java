package org.mockserver.mock.action.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import org.junit.Test;
import org.mockserver.model.HttpError;
import org.mockserver.responsewriter.HttpExchangeEndedEvent;
import org.mockserver.responsewriter.RawResponseBytesEvent;
import org.mockserver.responsewriter.ResponseWrittenBeneathCodecEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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
        when(mockChannelHandlerContext.pipeline()).thenReturn(mock(io.netty.channel.ChannelPipeline.class));
        HttpError httpError = error().withDropConnection(true);

        // when
        new HttpErrorActionHandler().handle(httpError, mockChannelHandlerContext);

        // then
        verify(mockChannelHandlerContext).close();
    }

    @Test
    public void shouldEndTheExchangeAfterWritingRawBytes() {
        // given
        List<Object> seenAfterCodec = new ArrayList<>();
        EmbeddedChannel channel = httpChannel(seenAfterCodec);

        // when
        new HttpErrorActionHandler().handle(error().withResponseBytes("some_bytes".getBytes(StandardCharsets.UTF_8)), channel.pipeline().lastContext());

        // then
        ByteBuf written = channel.readOutbound();
        assertThat(written.toString(StandardCharsets.UTF_8), is("some_bytes"));
        written.release();
        assertThat("the handlers after the codec never see a response, so they are told the exchange ended",
            seenAfterCodec, hasItem((Object) HttpExchangeEndedEvent.RAW_RESPONSE_WRITTEN));
        assertThat(channel.isOpen(), is(true));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldAnnounceRawBytesAndTheirLengthBeforeWritingThem() {
        // given - one record of what the handlers after the codec are told and what reaches the socket, in order
        List<Object> seen = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                seen.add("written " + ((ByteBuf) msg).readableBytes() + " bytes");
                ctx.write(msg, promise);
            }
        }, new HttpServerCodec(), recorder(seen));

        // when
        new HttpErrorActionHandler().handle(error().withResponseBytes("some_bytes".getBytes(StandardCharsets.UTF_8)), channel.pipeline().lastContext());

        // then - a relay reading these bytes from a loopback must know of them before they can arrive
        assertThat(seen.size(), is(4));
        assertThat(seen.get(0), instanceOf(RawResponseBytesEvent.class));
        assertThat(((RawResponseBytesEvent) seen.get(0)).length(), is("some_bytes".length()));
        assertThat(seen.get(1), is((Object) "written 10 bytes"));
        // this write completes as it is issued
        assertThat(seen.subList(2, 4), containsInAnyOrder((Object) HttpExchangeEndedEvent.RAW_RESPONSE_WRITTEN, ResponseWrittenBeneathCodecEvent.INSTANCE));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldTellTheCodecOfRawBytesAsSoonAsTheirWriteIsIssued() {
        // given
        List<Object> seenAfterCodec = new ArrayList<>();
        HeldWrites heldWrites = new HeldWrites();
        EmbeddedChannel channel = httpChannel(seenAfterCodec, heldWrites);

        // when
        new HttpErrorActionHandler().handle(error().withResponseBytes("some_bytes".getBytes(StandardCharsets.UTF_8)), channel.pipeline().lastContext());

        // then - before the write completes, so a response encoded meanwhile is paired with its own request
        assertThat(seenAfterCodec.size(), is(2));
        assertThat(seenAfterCodec.get(0), instanceOf(RawResponseBytesEvent.class));
        assertThat(seenAfterCodec.get(1), is((Object) ResponseWrittenBeneathCodecEvent.INSTANCE));
        heldWrites.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldAnnounceNothingWhenNoRawBytesAreWritten() {
        // given
        List<Object> seenAfterCodec = new ArrayList<>();
        EmbeddedChannel channel = httpChannel(seenAfterCodec);

        // when
        new HttpErrorActionHandler().handle(error(), channel.pipeline().lastContext());
        new HttpErrorActionHandler().handle(error().withDropConnection(true), channel.pipeline().lastContext());

        // then
        assertThat(seenAfterCodec, not(hasItem(instanceOf(RawResponseBytesEvent.class))));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldEndTheExchangeWhenNothingIsWritten() {
        // given
        List<Object> seenAfterCodec = new ArrayList<>();
        EmbeddedChannel channel = httpChannel(seenAfterCodec);

        // when
        new HttpErrorActionHandler().handle(error(), channel.pipeline().lastContext());

        // then
        assertThat(channel.outboundMessages().isEmpty(), is(true));
        assertThat("an abandoned exchange must not hold its keep-alive connection busy", seenAfterCodec, contains((Object) HttpExchangeEndedEvent.INSTANCE));
        assertThat(channel.isOpen(), is(true));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldNotEndTheExchangeWhenDroppingTheConnection() {
        // given
        List<Object> seenAfterCodec = new ArrayList<>();
        EmbeddedChannel channel = httpChannel(seenAfterCodec);

        // when
        new HttpErrorActionHandler().handle(error().withDropConnection(true), channel.pipeline().lastContext());

        // then
        assertThat(channel.isOpen(), is(false));
        assertThat(seenAfterCodec, not(hasItem(instanceOf(HttpExchangeEndedEvent.class))));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldEndTheExchangeOnlyOnceTheRawWriteCompletesWithoutBlocking() {
        // given - a slow reader: the raw write stays pending until the test releases it
        List<Object> seenAfterCodec = new ArrayList<>();
        HeldWrites heldWrites = new HeldWrites();
        EmbeddedChannel channel = httpChannel(seenAfterCodec, heldWrites);

        // when - on the event loop, where waiting for the write would throw BlockingOperationException
        new HttpErrorActionHandler().handle(error().withResponseBytes(new byte[128 * 1024]), channel.pipeline().lastContext());

        // then
        assertThat("the exchange is still in progress while its bytes are being written", seenAfterCodec, not(hasItem(instanceOf(HttpExchangeEndedEvent.class))));
        heldWrites.release();
        assertThat(seenAfterCodec, hasItem((Object) HttpExchangeEndedEvent.RAW_RESPONSE_WRITTEN));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldDropTheConnectionOnlyAfterTheRawBytesAreWritten() {
        // given
        List<Object> seenAfterCodec = new ArrayList<>();
        HeldWrites heldWrites = new HeldWrites();
        EmbeddedChannel channel = httpChannel(seenAfterCodec, heldWrites);

        // when
        new HttpErrorActionHandler().handle(error().withResponseBytes("some_bytes".getBytes(StandardCharsets.UTF_8)).withDropConnection(true), channel.pipeline().lastContext());

        // then
        assertThat("closing now would discard the unwritten bytes", channel.isOpen(), is(true));
        heldWrites.release();
        ByteBuf written = channel.readOutbound();
        assertThat(written.toString(StandardCharsets.UTF_8), is("some_bytes"));
        written.release();
        assertThat(channel.isOpen(), is(false));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldEndTheExchangeAsTheRawWriteCompletesWhenCalledOffTheEventLoop() throws Exception {
        // given - a delayed error action runs on a scheduler thread, not the connection's event loop
        DefaultEventLoopGroup group = new DefaultEventLoopGroup(1);
        List<Object> seenAfterCodec = new CopyOnWriteArrayList<>();
        CompletableFuture<Boolean> endedWhenWriteCompleted = new CompletableFuture<>();
        CompletableFuture<Boolean> announcedBeforeWrite = new CompletableFuture<>();
        CompletableFuture<Channel> serverChannel = new CompletableFuture<>();
        LocalAddress address = new LocalAddress("http-error-action-" + UUID.randomUUID());
        Channel server = null;
        Channel client = null;
        try {
            server = new ServerBootstrap().group(group).channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    protected void initChannel(LocalChannel channel) {
                        channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
                            @Override
                            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                                announcedBeforeWrite.complete(seenAfterCodec.size() == 1 && seenAfterCodec.get(0) instanceof RawResponseBytesEvent);
                                // anything that runs as the write completes, such as reading the client's next request
                                promise.addListener(future -> endedWhenWriteCompleted.complete(seenAfterCodec.contains(HttpExchangeEndedEvent.RAW_RESPONSE_WRITTEN)));
                                ctx.write(msg, promise);
                            }
                        }, new HttpServerCodec(), recorder(seenAfterCodec));
                        serverChannel.complete(channel);
                    }
                }).bind(address).sync().channel();
            client = new Bootstrap().group(group).channel(LocalChannel.class).handler(new ChannelInboundHandlerAdapter()).connect(address).sync().channel();
            Channel connection = serverChannel.get(10, TimeUnit.SECONDS);

            // when
            new HttpErrorActionHandler().handle(error().withResponseBytes("some_bytes".getBytes(StandardCharsets.UTF_8)), connection.pipeline().lastContext());

            // then
            assertThat("the raw bytes are announced before they are written", announcedBeforeWrite.get(10, TimeUnit.SECONDS), is(true));
            assertThat("nothing on the event loop runs between the write completing and the exchange ending",
                endedWhenWriteCompleted.get(10, TimeUnit.SECONDS), is(true));
        } finally {
            if (client != null) {
                client.close().sync();
            }
            if (server != null) {
                server.close().sync();
            }
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }

    private static EmbeddedChannel httpChannel(List<Object> seenAfterCodec, HeldWrites heldWrites) {
        return new EmbeddedChannel(heldWrites, new HttpServerCodec(), recorder(seenAfterCodec));
    }

    private static ChannelInboundHandlerAdapter recorder(List<Object> seenAfterCodec) {
        return new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                seenAfterCodec.add(evt);
            }
        };
    }

    private static final class HeldWrites extends ChannelOutboundHandlerAdapter {
        private ChannelHandlerContext ctx;
        private final List<Object> messages = new ArrayList<>();
        private final List<ChannelPromise> promises = new ArrayList<>();

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            this.ctx = ctx;
            messages.add(msg);
            promises.add(promise);
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            // held until release()
        }

        void release() {
            for (int i = 0; i < messages.size(); i++) {
                ctx.write(messages.get(i), promises.get(i));
            }
            messages.clear();
            promises.clear();
            ctx.flush();
        }
    }

    private static EmbeddedChannel httpChannel(List<Object> seenAfterCodec) {
        return new EmbeddedChannel(new HttpServerCodec(), recorder(seenAfterCodec));
    }
}