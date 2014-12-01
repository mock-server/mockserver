package org.mockserver.proxy.relay;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import org.slf4j.Logger;

public class DownstreamProxyRelayHandler extends ProxyRelayHandler<FullHttpResponse> {

    public DownstreamProxyRelayHandler(Channel inboundChannel, Logger logger) {
        super(inboundChannel, logger);
    }

}
