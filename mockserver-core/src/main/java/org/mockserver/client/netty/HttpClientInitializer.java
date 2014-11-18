package org.mockserver.client.netty;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.mockserver.client.netty.codec.MockServerClientCodec;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;

public class HttpClientInitializer extends ChannelInitializer<SocketChannel> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final boolean secure;
    private HttpClientHandler httpClientHandler = new HttpClientHandler();

    public HttpClientInitializer(boolean secure) {
        this.secure = secure;
    }

    @Override
    public void initChannel(SocketChannel channel) throws SSLException {
        ChannelPipeline pipeline = channel.pipeline();

        if (secure) {
            pipeline.addLast(SslContext.newClientContext(InsecureTrustManagerFactory.INSTANCE).newHandler(channel.alloc()));
        }

        // add logging
        if (logger.isDebugEnabled()) {
            pipeline.addLast(new LoggingHandler());
        }

        pipeline.addLast(new HttpClientCodec());

        pipeline.addLast(new HttpContentDecompressor());

        pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

        pipeline.addLast(new MockServerClientCodec());

        pipeline.addLast(httpClientHandler);
    }

    public SettableFuture<HttpResponse> getResponseFuture() {
        return httpClientHandler.getResponseFuture();
    }
}