package org.mockserver.httpclient;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import org.mockserver.codec.MockServerBinaryClientCodec;
import org.mockserver.codec.MockServerHttpClientCodec;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Protocol;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.socket.tls.NettySslContextFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.httpclient.NettyHttpClient.REMOTE_SOCKET;
import static org.mockserver.httpclient.NettyHttpClient.SECURE;
import static org.slf4j.event.Level.TRACE;

@ChannelHandler.Sharable
public class HttpClientInitializer extends ChannelInitializer<SocketChannel> {

    private final MockServerLogger mockServerLogger;
    private final boolean forwardProxyClient;
    private final Protocol httpProtocol;
    private final HttpClientConnectionErrorHandler httpClientConnectionHandler;
    private final CompletableFuture<Protocol> protocolFuture;
    private final HttpClientHandler httpClientHandler;
    private final Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations;
    private final NettySslContextFactory nettySslContextFactory;

    HttpClientInitializer(Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations, MockServerLogger mockServerLogger, boolean forwardProxyClient, NettySslContextFactory nettySslContextFactory, Protocol httpProtocol) {
        this.proxyConfigurations = proxyConfigurations;
        this.mockServerLogger = mockServerLogger;
        this.forwardProxyClient = forwardProxyClient;
        this.httpProtocol = httpProtocol;
        this.protocolFuture = new CompletableFuture<>();
        this.httpClientHandler = new HttpClientHandler();
        this.httpClientConnectionHandler = new HttpClientConnectionErrorHandler();
        this.nettySslContextFactory = nettySslContextFactory;
    }

    public void whenComplete(BiConsumer<? super Protocol, ? super Throwable> action) {
        protocolFuture.whenComplete(action);
    }

    @Override
    public void initChannel(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        boolean secure = channel.attr(SECURE) != null && channel.attr(SECURE).get() != null && channel.attr(SECURE).get();

        if (proxyConfigurations != null) {
            if (secure && proxyConfigurations.containsKey(ProxyConfiguration.Type.HTTPS)) {
                ProxyConfiguration proxyConfiguration = proxyConfigurations.get(ProxyConfiguration.Type.HTTPS);
                if (isNotBlank(proxyConfiguration.getUsername()) && isNotBlank(proxyConfiguration.getPassword())) {
                    pipeline.addLast(new HttpProxyHandler(proxyConfiguration.getProxyAddress(), proxyConfiguration.getUsername(), proxyConfiguration.getPassword()));
                } else {
                    pipeline.addLast(new HttpProxyHandler(proxyConfiguration.getProxyAddress()));
                }
            } else if (proxyConfigurations.containsKey(ProxyConfiguration.Type.SOCKS5)) {
                ProxyConfiguration proxyConfiguration = proxyConfigurations.get(ProxyConfiguration.Type.SOCKS5);
                if (isNotBlank(proxyConfiguration.getUsername()) && isNotBlank(proxyConfiguration.getPassword())) {
                    pipeline.addLast(new Socks5ProxyHandler(proxyConfiguration.getProxyAddress(), proxyConfiguration.getUsername(), proxyConfiguration.getPassword()));
                } else {
                    pipeline.addLast(new Socks5ProxyHandler(proxyConfiguration.getProxyAddress()));
                }
            }
        }
        pipeline.addLast(httpClientConnectionHandler);

        if (secure) {
            InetSocketAddress remoteAddress = channel.attr(REMOTE_SOCKET).get();
            pipeline.addLast(nettySslContextFactory.createClientSslContext(forwardProxyClient, httpProtocol != null && httpProtocol.equals(Protocol.HTTP_2)).newHandler(channel.alloc(), remoteAddress.getHostName(), remoteAddress.getPort()));
        }

        // add logging
        if (MockServerLogger.isEnabled(TRACE)) {
            pipeline.addLast(new LoggingHandler(HttpClientHandler.class.getName()));
        }

        if (httpProtocol == null) {
            configureBinaryPipeline(pipeline);
        } else if (secure) {
            // use ALPN to determine http1 or http2
            pipeline.addLast(new HttpOrHttp2Initializer(this::configureHttp1Pipeline, this::configureHttp2Pipeline));
        } else {
            // default to http1 without TLS
            configureHttp1Pipeline(pipeline);
        }
    }

    private void configureHttp1Pipeline(ChannelPipeline pipeline) {
        pipeline.addLast(new HttpClientCodec());
        pipeline.addLast(new HttpContentDecompressor());
        pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
        pipeline.addLast(new MockServerHttpClientCodec(mockServerLogger, proxyConfigurations));
        pipeline.addLast(httpClientHandler);
        protocolFuture.complete(Protocol.HTTP_1_1);
    }

    private void configureHttp2Pipeline(ChannelPipeline pipeline) {
        final Http2Connection connection = new DefaultHttp2Connection(false);
        final HttpToHttp2ConnectionHandlerBuilder http2ConnectionHandlerBuilder = new HttpToHttp2ConnectionHandlerBuilder()
            .frameListener(
                new DelegatingDecompressorFrameListener(
                    connection,
                    new InboundHttp2ToHttpAdapterBuilder(connection)
                        .maxContentLength(Integer.MAX_VALUE)
                        .propagateSettings(true)
                        .validateHttpHeaders(false)
                        .build()
                )
            )
            .connection(connection)
            .flushPreface(true);
        if (MockServerLogger.isEnabled(TRACE)) {
            http2ConnectionHandlerBuilder.frameLogger(new Http2FrameLogger(LogLevel.TRACE, HttpClientHandler.class.getName()));
        }
        pipeline.addLast(http2ConnectionHandlerBuilder.build());
        pipeline.addLast(new Http2SettingsHandler(protocolFuture));
        pipeline.addLast(new MockServerHttpClientCodec(mockServerLogger, proxyConfigurations));
        pipeline.addLast(httpClientHandler);
    }

    private void configureBinaryPipeline(ChannelPipeline pipeline) {
        pipeline.addLast(new MockServerBinaryClientCodec());
        pipeline.addLast(httpClientHandler);
        protocolFuture.complete(null);
    }
}
