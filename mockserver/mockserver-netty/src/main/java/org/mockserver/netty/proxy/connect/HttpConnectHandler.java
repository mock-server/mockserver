package org.mockserver.netty.proxy.connect;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import org.mockserver.netty.connection.HttpTransportTimer;
import org.mockserver.netty.unification.HttpServerCodecResponsePairing;
import org.mockserver.netty.unification.PacedLargeWriteHandler;
import org.mockserver.configuration.Configuration;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.netty.proxy.relay.RelayConnectHandler;
import org.mockserver.codec.Http1PipeliningGate;
import org.mockserver.codec.HttpChunkLineLimiter;
import org.mockserver.codec.HttpLineEndSplitGuard;
import org.mockserver.codec.MockServerHttpServerCodec;
import org.mockserver.logging.RawNetworkTrafficLog;

import static org.mockserver.model.HttpResponse.response;

@ChannelHandler.Sharable
public final class HttpConnectHandler extends RelayConnectHandler<HttpRequest> {

    public HttpConnectHandler(Configuration configuration, LifeCycle server, MockServerLogger mockServerLogger, String host, int port) {
        super(configuration, server, mockServerLogger, host, port);
    }

    protected void removeCodecSupport(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        removeHandler(pipeline, HttpServerCodec.class);
        HttpChunkLineLimiter.removeFrom(pipeline);
        HttpLineEndSplitGuard.removeFrom(pipeline);
        HttpServerCodecResponsePairing.removeFrom(pipeline);
        removeHandler(pipeline, HttpContentDecompressor.class);
        removeHandler(pipeline, HttpObjectAggregator.class);
        removeHandler(pipeline, MockServerHttpServerCodec.class);
        removeHandler(pipeline, PacedLargeWriteHandler.class);
        HttpTransportTimer.removeFrom(pipeline);
        Http1PipeliningGate.removeFrom(pipeline);
        // the tunnel's bytes are logged on its loopback leg, where they are decrypted
        RawNetworkTrafficLog.removeFrom(pipeline);
        if (pipeline.get(this.getClass()) != null) {
            pipeline.remove(this);
        }
    }

    protected Object successResponse(Object request) {
        return response();
    }

    protected Object failureResponse(Object request) {
        return response().withStatusCode(HttpResponseStatus.BAD_GATEWAY.code());
    }
}
