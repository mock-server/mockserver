package org.mockserver.codec;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;
import org.mockserver.model.Header;

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

}