package org.mockserver.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpRequest;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.FullHttpRequestToMockServerHttpRequest;
import org.mockserver.model.Header;

import java.net.SocketAddress;
import java.security.cert.Certificate;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class NettyHttpToMockServerHttpRequestDecoder extends MessageToMessageDecoder<FullHttpRequest> {

    private final FullHttpRequestToMockServerHttpRequest fullHttpRequestToMockServerRequest;

    public NettyHttpToMockServerHttpRequestDecoder(Configuration configuration, MockServerLogger mockServerLogger, boolean isSecure, Certificate[] clientCertificates, Integer port) {
        fullHttpRequestToMockServerRequest = new FullHttpRequestToMockServerHttpRequest(configuration, mockServerLogger, isSecure, clientCertificates, port);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, List<Object> out) {
        List<Header> preservedHeaders = null;
        SocketAddress localAddress = null;
        SocketAddress remoteAddress = null;
        if (ctx != null && ctx.channel() != null) {
            preservedHeaders = PreserveHeadersNettyRemoves.preservedHeaders(ctx.channel());
            localAddress = ctx.channel().localAddress();
            remoteAddress = ctx.channel().remoteAddress();
        }
        out.add(fullHttpRequestToMockServerRequest.mapFullHttpRequestToMockServerRequest(fullHttpRequest, preservedHeaders, localAddress, remoteAddress));
    }

}
