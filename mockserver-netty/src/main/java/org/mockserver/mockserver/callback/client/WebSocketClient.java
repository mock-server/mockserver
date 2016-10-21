package org.mockserver.mockserver.callback.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class WebSocketClient {

    private Channel ch;
    private EventLoopGroup group = new NioEventLoopGroup();

    public WebSocketClient() {
        try {
            URI uri = new URI("ws://localhost:1234/websocket");

            // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
            // If you change it to V00, ping is not supported and remember to change
            // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
            final WebSocketClientHandler handler = new WebSocketClientHandler(uri);

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(
                                            new HttpClientCodec(),
                                            new HttpObjectAggregator(Integer.MAX_VALUE),
                                            handler
                                    );
                        }
                    });

            ch = b.connect(uri.getHost(), uri.getPort()).sync().channel();
            handler.handshakeFuture().sync();

            sendWebSocketFrame(new TextWebSocketFrame(MessageType.REGISTER.name()));
        } catch (InterruptedException e) {
            throw new WebSocketClientException(e);
        } catch (URISyntaxException e) {
            throw new WebSocketClientException(e);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        WebSocketClient webSocketClient = new WebSocketClient();

        TimeUnit.MINUTES.sleep(5);

        webSocketClient.stopClient();
    }

    public void stopClient() {
        sendWebSocketFrame(new CloseWebSocketFrame());
        group.shutdownGracefully();
    }

    public void sendWebSocketFrame(WebSocketFrame webSocketFrame) {
        ch.writeAndFlush(webSocketFrame);
        if (webSocketFrame instanceof CloseWebSocketFrame) {
            try {
                ch.closeFuture().sync();
            } catch (InterruptedException e) {
                throw new WebSocketClientException(e);
            }
        }
    }
}
