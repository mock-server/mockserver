package org.mockserver.proxy.relay;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import org.slf4j.Logger;

public class UpstreamProxyRelayHandler extends ProxyRelayHandler<FullHttpRequest> {

    public UpstreamProxyRelayHandler(Channel inboundChannel, Logger logger) {
        super(inboundChannel, logger);
    }

}
