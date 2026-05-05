package org.mockserver.netty.unification;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

public class HttpContentLengthRemoverTest {

    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        channel = new EmbeddedChannel(new HttpContentLengthRemover());
    }

    @After
    public void tearDown() {
        if (channel != null) {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void shouldRemoveContentLengthHeader_fromHttpRequest_whenValueIsEmpty() {
        DefaultHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/test",
            Unpooled.EMPTY_BUFFER
        );
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, "");

        channel.writeOutbound(request);

        DefaultHttpRequest output = channel.readOutbound();
        assertThat(output.headers().contains(HttpHeaderNames.CONTENT_LENGTH), is(false));
        ReferenceCountUtil.release(output);
    }

    @Test
    public void shouldRemoveContentLengthHeader_fromHttpResponse_whenValueIsEmpty() {
        DefaultHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.EMPTY_BUFFER
        );
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, "");

        channel.writeOutbound(response);

        DefaultHttpResponse output = channel.readOutbound();
        assertThat(output.headers().contains(HttpHeaderNames.CONTENT_LENGTH), is(false));
        ReferenceCountUtil.release(output);
    }

    @Test
    public void shouldPreserveContentLengthHeader_whenValueIsNotEmpty() {
        DefaultHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "/test",
            Unpooled.EMPTY_BUFFER
        );
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, "100");

        channel.writeOutbound(request);

        DefaultHttpRequest output = channel.readOutbound();
        assertThat(output.headers().get(HttpHeaderNames.CONTENT_LENGTH), is("100"));
        ReferenceCountUtil.release(output);
    }

    @Test
    public void shouldNotFailWhenContentLengthAbsent() {
        DefaultHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/test",
            Unpooled.EMPTY_BUFFER
        );

        channel.writeOutbound(request);

        DefaultHttpRequest output = channel.readOutbound();
        assertThat(output.headers().contains(HttpHeaderNames.CONTENT_LENGTH), is(false));
        ReferenceCountUtil.release(output);
    }

    @Test
    public void shouldNotLeakByteBuf_whenExceptionDuringAdd() {
        HttpContentLengthRemover encoder = new HttpContentLengthRemover();
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/test",
            Unpooled.EMPTY_BUFFER
        );
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, "100");

        int initialRefCount = request.refCnt();

        List<Object> out = new ArrayList<Object>() {
            @Override
            public boolean add(Object o) {
                throw new RuntimeException("Simulated failure during add");
            }
        };

        try {
            encoder.encode(null, request, out);
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("Simulated failure during add"));
        }

        int finalRefCount = request.refCnt();
        assertEquals("ByteBuf reference count should be unchanged after exception", initialRefCount, finalRefCount);
        
        ReferenceCountUtil.release(request);
    }

    @Test
    public void shouldPreserveOtherHeaders() {
        DefaultHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.POST,
            "/test",
            Unpooled.EMPTY_BUFFER
        );
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, "");
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        request.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer token");

        channel.writeOutbound(request);

        DefaultHttpRequest output = channel.readOutbound();
        assertThat(output.headers().contains(HttpHeaderNames.CONTENT_LENGTH), is(false));
        assertThat(output.headers().get(HttpHeaderNames.CONTENT_TYPE), is("application/json"));
        assertThat(output.headers().get(HttpHeaderNames.AUTHORIZATION), is("Bearer token"));
        ReferenceCountUtil.release(output);
    }

    @Test
    public void shouldHandleCaseInsensitiveContentLengthHeader() {
        DefaultHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/test",
            Unpooled.EMPTY_BUFFER
        );
        request.headers().set("content-length", "");

        channel.writeOutbound(request);

        DefaultHttpRequest output = channel.readOutbound();
        assertThat(output.headers().contains(HttpHeaderNames.CONTENT_LENGTH), is(false));
        assertThat(output.headers().contains("content-length"), is(false));
        ReferenceCountUtil.release(output);
    }
}
