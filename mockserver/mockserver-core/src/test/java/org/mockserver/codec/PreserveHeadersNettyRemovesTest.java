package org.mockserver.codec;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;
import org.mockserver.model.Header;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class PreserveHeadersNettyRemovesTest {

    @Test
    public void shouldPreserveContentEncodingIfExists() {
        // given
        PreserveHeadersNettyRemoves preserveHeadersNettyRemoves = new PreserveHeadersNettyRemoves();
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(preserveHeadersNettyRemoves);
        DefaultFullHttpRequest fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "/uri");
        fullHttpRequest.headers().add(HttpHeaderNames.CONTENT_ENCODING, "some_value");

        // when
        embeddedChannel.writeInbound(fullHttpRequest);

        // then
        assertThat(PreserveHeadersNettyRemoves.preservedHeaders(embeddedChannel), equalTo(Collections.singletonList(
            new Header(HttpHeaderNames.CONTENT_ENCODING.toString(), "some_value")
        )));
    }

    @Test
    public void shouldNotPreserveContentEncodingIfDoesNotExists() {
        // given
        PreserveHeadersNettyRemoves preserveHeadersNettyRemoves = new PreserveHeadersNettyRemoves();
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(preserveHeadersNettyRemoves);
        DefaultFullHttpRequest fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "/uri");

        // when
        embeddedChannel.writeInbound(fullHttpRequest);

        // then
        assertThat(PreserveHeadersNettyRemoves.preservedHeaders(embeddedChannel), equalTo(Collections.emptyList()));
    }

    @Test
    public void shouldPreserveTransferEncodingIfExists() {
        // given
        PreserveHeadersNettyRemoves preserveHeadersNettyRemoves = new PreserveHeadersNettyRemoves();
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(preserveHeadersNettyRemoves);
        DefaultFullHttpRequest fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/uri");
        fullHttpRequest.headers().add(HttpHeaderNames.TRANSFER_ENCODING, "chunked");

        // when
        embeddedChannel.writeInbound(fullHttpRequest);

        // then
        assertThat(PreserveHeadersNettyRemoves.preservedHeaders(embeddedChannel), equalTo(Collections.singletonList(
            new Header(HttpHeaderNames.TRANSFER_ENCODING.toString(), "chunked")
        )));
    }

    @Test
    public void shouldPreserveBothContentEncodingAndTransferEncoding() {
        // given
        PreserveHeadersNettyRemoves preserveHeadersNettyRemoves = new PreserveHeadersNettyRemoves();
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(preserveHeadersNettyRemoves);
        DefaultFullHttpRequest fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/uri");
        fullHttpRequest.headers().add(HttpHeaderNames.CONTENT_ENCODING, "gzip");
        fullHttpRequest.headers().add(HttpHeaderNames.TRANSFER_ENCODING, "chunked");

        // when
        embeddedChannel.writeInbound(fullHttpRequest);

        // then
        assertThat(PreserveHeadersNettyRemoves.preservedHeaders(embeddedChannel), equalTo(Arrays.asList(
            new Header(HttpHeaderNames.CONTENT_ENCODING.toString(), "gzip"),
            new Header(HttpHeaderNames.TRANSFER_ENCODING.toString(), "chunked")
        )));
    }

}