package org.mockserver.mockserver.callback.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.collections.CircularHashMap;

import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class WebSocketServer {

    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private CircularHashMap<String, ChannelHandlerContext> clientRegistry = new CircularHashMap<String, ChannelHandlerContext>(100);

    public static void main(String[] args) throws InterruptedException {
        WebSocketServer webSocketServer = new WebSocketServer();
        TimeUnit.SECONDS.sleep(1);
        for (String clientId : webSocketServer.clientRegistry.keySet()) {
            webSocketServer.sendClientMessage(clientId, request().withBody("some body"));
        }
    }

    public WebSocketServer() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                            pipeline.addLast(new WebSocketServerHandler(WebSocketServer.this));
                        }
                    });

            Channel ch = b.bind(1234).sync().channel();

            ch.closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    void registerClient(String clientId, ChannelHandlerContext ctx) {
        clientRegistry.put(clientId, ctx);
        sendClientMessage(clientId, new WebSocketClientRegistrationResponse().withClientId(clientId));
    }

    public WebSocketServer sendClientMessage(String clientId, Object message) {
        if (clientRegistry.containsKey(clientId)) {
            try {
                clientRegistry.get(clientId).channel().writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(message)));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error serializing WebSocketClientRegistrationResponse for client: \"" + clientId + "\"", e);
            }
        }
        return this;
    }

    public WebSocketServer unregisterClient(String clientId) {
        clientRegistry.remove(clientId);
        return this;
    }
}
