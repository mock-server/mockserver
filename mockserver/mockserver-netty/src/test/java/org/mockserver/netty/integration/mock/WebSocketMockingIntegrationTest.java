package org.mockserver.netty.integration.mock;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockserver.stop.Stop.stopQuietly;

public class WebSocketMockingIntegrationTest {

    private static int mockServerPort;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void startServer() {
        mockServerPort = new MockServer().getLocalPort();
        mockServerClient = new MockServerClient("localhost", mockServerPort);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
    }

    @Before
    public void resetServer() {
        mockServerClient.reset();
    }

    private void createExpectation(String json) throws Exception {
        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            output.write(("PUT /mockserver/expectation HTTP/1.1\r\n" +
                "Host: localhost:" + mockServerPort + "\r\n" +
                "Content-Type: application/json\r\n" +
                "Connection: close\r\n" +
                "Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(body);
            output.flush();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = socket.getInputStream().read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
        }
    }

    private List<String> connectWebSocket(String path, int expectedMessages) throws Exception {
        List<String> receivedMessages = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(expectedMessages);
        CompletableFuture<Boolean> handshakeComplete = new CompletableFuture<>();

        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            URI uri = new URI("ws://localhost:" + mockServerPort + path);
            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders(), Integer.MAX_VALUE
            );

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                            new HttpClientCodec(),
                            new HttpObjectAggregator(65536),
                            new SimpleChannelInboundHandler<Object>() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    handshaker.handshake(ctx.channel());
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                    if (!handshaker.isHandshakeComplete()) {
                                        try {
                                            handshaker.finishHandshake(ctx.channel(), (FullHttpResponse) msg);
                                            handshakeComplete.complete(true);
                                        } catch (Exception e) {
                                            handshakeComplete.completeExceptionally(e);
                                        }
                                        return;
                                    }

                                    if (msg instanceof TextWebSocketFrame) {
                                        receivedMessages.add(((TextWebSocketFrame) msg).text());
                                        latch.countDown();
                                    } else if (msg instanceof CloseWebSocketFrame) {
                                        ctx.close();
                                    }
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    handshakeComplete.completeExceptionally(cause);
                                    ctx.close();
                                }
                            }
                        );
                    }
                });

            Channel channel = bootstrap.connect("localhost", mockServerPort).sync().channel();
            handshakeComplete.get(5, TimeUnit.SECONDS);
            latch.await(5, TimeUnit.SECONDS);
            channel.close().sync();
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }

        return receivedMessages;
    }

    @Test
    public void shouldReturnWebSocketMessagesViaRestApi() throws Exception {
        createExpectation("{\n" +
            "  \"httpRequest\": {\n" +
            "    \"method\": \"GET\",\n" +
            "    \"path\": \"/ws\"\n" +
            "  },\n" +
            "  \"httpWebSocketResponse\": {\n" +
            "    \"messages\": [\n" +
            "      {\"text\": \"hello from mock\"},\n" +
            "      {\"text\": \"second message\"}\n" +
            "    ],\n" +
            "    \"closeConnection\": true\n" +
            "  }\n" +
            "}");

        List<String> messages = connectWebSocket("/ws", 2);

        assertThat(messages.size(), is(2));
        assertThat(messages.get(0), is("hello from mock"));
        assertThat(messages.get(1), is("second message"));
    }

    @Test
    public void shouldReturnSingleWebSocketMessage() throws Exception {
        createExpectation("{\n" +
            "  \"httpRequest\": {\n" +
            "    \"method\": \"GET\",\n" +
            "    \"path\": \"/ws-single\"\n" +
            "  },\n" +
            "  \"httpWebSocketResponse\": {\n" +
            "    \"messages\": [\n" +
            "      {\"text\": \"only message\"}\n" +
            "    ],\n" +
            "    \"closeConnection\": true\n" +
            "  }\n" +
            "}");

        List<String> messages = connectWebSocket("/ws-single", 1);

        assertThat(messages.size(), is(1));
        assertThat(messages.get(0), is("only message"));
    }

    @Test
    public void shouldHandleWebSocketWithSubprotocol() throws Exception {
        createExpectation("{\n" +
            "  \"httpRequest\": {\n" +
            "    \"method\": \"GET\",\n" +
            "    \"path\": \"/ws-proto\"\n" +
            "  },\n" +
            "  \"httpWebSocketResponse\": {\n" +
            "    \"subprotocol\": \"graphql-ws\",\n" +
            "    \"messages\": [\n" +
            "      {\"text\": \"{\\\"type\\\": \\\"connection_ack\\\"}\"}\n" +
            "    ],\n" +
            "    \"closeConnection\": true\n" +
            "  }\n" +
            "}");

        List<String> messages = connectWebSocket("/ws-proto", 1);

        assertThat(messages.size(), is(1));
        assertThat(messages.get(0), containsString("connection_ack"));
    }

    @Test
    public void shouldHandleWebSocketWithDelayBetweenMessages() throws Exception {
        createExpectation("{\n" +
            "  \"httpRequest\": {\n" +
            "    \"method\": \"GET\",\n" +
            "    \"path\": \"/ws-delay\"\n" +
            "  },\n" +
            "  \"httpWebSocketResponse\": {\n" +
            "    \"messages\": [\n" +
            "      {\"text\": \"fast\"},\n" +
            "      {\"text\": \"slow\", \"delay\": {\"timeUnit\": \"MILLISECONDS\", \"value\": 200}}\n" +
            "    ],\n" +
            "    \"closeConnection\": true\n" +
            "  }\n" +
            "}");

        long start = System.currentTimeMillis();
        List<String> messages = connectWebSocket("/ws-delay", 2);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(messages.size(), is(2));
        assertThat(messages.get(0), is("fast"));
        assertThat(messages.get(1), is("slow"));
        assertThat(elapsed, greaterThanOrEqualTo(150L));
    }

    @Test
    public void shouldReturnWebSocketMessagesViaJavaApi() throws Exception {
        mockServerClient.when(
            org.mockserver.model.HttpRequest.request()
                .withMethod("GET")
                .withPath("/ws-java")
        ).respondWithWebSocket(
            org.mockserver.model.HttpWebSocketResponse.webSocketResponse()
                .withMessage(org.mockserver.model.WebSocketMessage.webSocketMessage("from java api"))
                .withCloseConnection(true)
        );

        List<String> messages = connectWebSocket("/ws-java", 1);

        assertThat(messages.size(), is(1));
        assertThat(messages.get(0), is("from java api"));
    }

    @Test
    public void shouldReturnSseResponseViaJavaApi() throws Exception {
        mockServerClient.when(
            org.mockserver.model.HttpRequest.request()
                .withMethod("GET")
                .withPath("/sse-java")
        ).respondWithSse(
            org.mockserver.model.HttpSseResponse.sseResponse()
                .withEvent(org.mockserver.model.SseEvent.sseEvent()
                    .withEvent("message")
                    .withData("hello from java")
                    .withId("1"))
                .withCloseConnection(true)
        );

        try (Socket socket = new Socket("localhost", mockServerPort)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            output.write(("GET /sse-java HTTP/1.1\r\nHost: localhost:" + mockServerPort + "\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.flush();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = socket.getInputStream().read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            String response = baos.toString(StandardCharsets.UTF_8.name());

            assertThat(response, containsString("HTTP/1.1 200"));
            assertThat(response, containsString("text/event-stream"));
            assertThat(response, containsString("hello from java"));
        }
    }
}
