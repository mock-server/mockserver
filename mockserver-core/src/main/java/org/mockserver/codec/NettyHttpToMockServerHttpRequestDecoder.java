package org.mockserver.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpRequest;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.FullHttpRequestToMockServerHttpRequest;

import java.security.cert.Certificate;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class NettyHttpToMockServerHttpRequestDecoder extends MessageToMessageDecoder<FullHttpRequest> {

    private final FullHttpRequestToMockServerHttpRequest fullHttpRequestToMockServerRequest;

    public NettyHttpToMockServerHttpRequestDecoder(MockServerLogger mockServerLogger, boolean isSecure, Certificate[] clientCertificates, Integer port) {
        fullHttpRequestToMockServerRequest = new FullHttpRequestToMockServerHttpRequest(mockServerLogger, isSecure, clientCertificates, port);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, List<Object> out) {
        out.add(fullHttpRequestToMockServerRequest.mapFullHttpRequestToMockServerRequest(fullHttpRequest));
    }

}
