package org.mockserver.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpRequest;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.FullHttpRequestToMockServerHttpRequest;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class NettyHttpToMockServerHttpRequestDecoder extends MessageToMessageDecoder<FullHttpRequest> {

    private final FullHttpRequestToMockServerHttpRequest fullHttpRequestToMockServerRequest;

    public NettyHttpToMockServerHttpRequestDecoder(MockServerLogger mockServerLogger, boolean isSecure, SSLEngine sslEngine, Integer port) {
        fullHttpRequestToMockServerRequest = new FullHttpRequestToMockServerHttpRequest(mockServerLogger, isSecure, sslEngine, port);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, List<Object> out) {
        out.add(fullHttpRequestToMockServerRequest.mapFullHttpRequestToMockServerRequest(fullHttpRequest));
    }

}
