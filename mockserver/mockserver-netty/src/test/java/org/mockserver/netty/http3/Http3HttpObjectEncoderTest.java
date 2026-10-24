package org.mockserver.netty.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class Http3HttpObjectEncoderTest {

    @Test
    public void shouldWriteResponseHeadAsHeadersFrameWithoutConnectionSpecificFields() {
        EmbeddedChannel channel = new EmbeddedChannel(Http3HttpObjectEncoder.INSTANCE);
        DefaultHttpResponse head = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        head.headers()
            .set("Content-Type", "text/event-stream")
            .set("Cache-Control", "no-cache")
            .set("Connection", "close")
            .set("Transfer-Encoding", "chunked")
            .set("Keep-Alive", "timeout=5")
            .set("Content-Length", "10")
            .set("X-Custom", "value");

        assertThat(channel.writeOutbound(head), is(true));

        Http3HeadersFrame frame = channel.readOutbound();
        assertThat(frame.headers().status().toString(), is("200"));
        assertThat(frame.headers().get("content-type").toString(), is("text/event-stream"));
        assertThat(frame.headers().get("cache-control").toString(), is("no-cache"));
        assertThat(frame.headers().get("x-custom").toString(), is("value"));
        assertThat(frame.headers().get("connection"), is(nullValue()));
        assertThat(frame.headers().get("transfer-encoding"), is(nullValue()));
        assertThat(frame.headers().get("keep-alive"), is(nullValue()));
        assertThat(frame.headers().get("content-length"), is(nullValue()));
        assertThat((Object) channel.readOutbound(), is(nullValue()));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldWriteEachContentAsDataFrameAndTheLastAsAnEndingFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(Http3HttpObjectEncoder.INSTANCE);

        channel.writeOutbound(new DefaultHttpContent(Unpooled.copiedBuffer("data: one\n\n", StandardCharsets.UTF_8)));
        ChannelFuture last = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

        Http3DataFrame event = channel.readOutbound();
        assertThat(event.content().toString(StandardCharsets.UTF_8), is("data: one\n\n"));
        event.release();
        Http3DataFrame end = channel.readOutbound();
        assertThat(end.content().readableBytes(), is(0));
        end.release();
        assertThat(last.isSuccess(), is(true));
        assertThat((Object) channel.readOutbound(), is(nullValue()));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldWriteLastContentWithItsTrailers() {
        EmbeddedChannel channel = new EmbeddedChannel(Http3HttpObjectEncoder.INSTANCE);
        DefaultLastHttpContent last = new DefaultLastHttpContent(Unpooled.copiedBuffer("]", StandardCharsets.UTF_8));
        last.trailingHeaders().set("X-Trailer", "done");

        channel.writeOutbound(last);

        Http3DataFrame data = channel.readOutbound();
        assertThat(data.content().toString(StandardCharsets.UTF_8), is("]"));
        data.release();
        Http3HeadersFrame trailers = channel.readOutbound();
        assertThat(trailers.headers().get("x-trailer").toString(), is("done"));
        assertThat(last.refCnt(), is(0));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldPassHttp3FramesAndOtherMessagesThroughUnchanged() {
        EmbeddedChannel channel = new EmbeddedChannel(Http3HttpObjectEncoder.INSTANCE);
        DefaultHttp3DataFrame frame = new DefaultHttp3DataFrame(Unpooled.copiedBuffer("frame", StandardCharsets.UTF_8));
        ByteBuf raw = Unpooled.copiedBuffer("raw", StandardCharsets.UTF_8);

        channel.writeOutbound(frame, raw);

        assertThat(channel.readOutbound(), sameInstance(frame));
        assertThat(channel.readOutbound(), sameInstance(raw));
        frame.release();
        raw.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldFailTheWriteAndReleaseTheContentWhenTheStreamRefusesIt() {
        EmbeddedChannel channel = new EmbeddedChannel(new FailingWrites(), Http3HttpObjectEncoder.INSTANCE);
        DefaultHttpContent content = new DefaultHttpContent(Unpooled.copiedBuffer("data: one\n\n", StandardCharsets.UTF_8));
        DefaultLastHttpContent last = new DefaultLastHttpContent(Unpooled.copiedBuffer("]", StandardCharsets.UTF_8));

        ChannelFuture contentWrite = channel.writeAndFlush(content);
        ChannelFuture lastWrite = channel.writeAndFlush(last);

        assertThat(contentWrite.isSuccess(), is(false));
        assertThat(contentWrite.cause(), instanceOf(ClosedChannelException.class));
        assertThat(content.refCnt(), is(0));
        assertThat(lastWrite.isSuccess(), is(false));
        assertThat(last.refCnt(), is(0));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldFailTheWriteOfAnHttpObjectThatIsNotAResponse() {
        EmbeddedChannel channel = new EmbeddedChannel(Http3HttpObjectEncoder.INSTANCE);
        ChannelFuture write = channel.writeAndFlush(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        assertThat(write.isSuccess(), is(false));
        assertThat(write.cause(), instanceOf(UnsupportedMessageTypeException.class));
        assertThat((Object) channel.readOutbound(), is(nullValue()));
        channel.finishAndReleaseAll();
    }

    /**
     * A stream that refuses every write, as one whose client has stopped reading does.
     */
    private static final class FailingWrites extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ReferenceCountUtil.release(msg);
            promise.setFailure(new ClosedChannelException());
        }
    }
}
