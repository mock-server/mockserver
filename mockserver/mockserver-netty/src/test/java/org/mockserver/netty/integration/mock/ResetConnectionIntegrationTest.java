package org.mockserver.netty.integration.mock;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.testing.tls.SSLSocketFactory.sslSocketFactory;

/**
 * Real-socket proof, directly and through CONNECT and SOCKS tunnels, that an {@code HttpError} with {@code resetConnection} answers with a TCP RST, so the
 * client's read fails with "connection reset" ({@code ECONNRESET}), where {@code dropConnection} is a clean
 * close the client reads as end of stream, and an ordinary response on the same server is unchanged.
 */
public class ResetConnectionIntegrationTest {

    private static MockServer mockServer;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void startServer() {
        mockServer = new MockServer();
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());
        mockServerClient.when(request().withPath("/reset")).error(error().withResetConnection(true));
        mockServerClient.when(request().withPath("/drop")).error(error().withDropConnection(true));
        mockServerClient.when(request().withPath("/ok")).respond(response().withBody("still fine"));
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
    }

    @Test(timeout = 30000)
    public void shouldResetHttp1Connection() throws Exception {
        try (Socket socket = new Socket("localhost", mockServer.getLocalPort())) {
            assertConnectionReset(socket, "/reset");
        }
    }

    @Test(timeout = 30000)
    public void shouldResetTlsConnectionWithoutCloseNotify() throws Exception {
        try (Socket socket = sslSocketFactory().wrapSocket(new Socket("localhost", mockServer.getLocalPort()))) {
            assertConnectionReset(socket, "/reset");
        }
    }

    @Test(timeout = 30000)
    public void shouldStillCloseCleanlyOnDropConnection() throws Exception {
        try (Socket socket = new Socket("localhost", mockServer.getLocalPort())) {
            socket.setSoTimeout(10000);
            send(socket, "/drop");
            assertThat("dropConnection must stay a clean close (end of stream)", socket.getInputStream().read(), is(-1));
        }
    }

    @Test(timeout = 30000)
    public void shouldStillServeOrdinaryResponses() throws Exception {
        try (Socket socket = new Socket("localhost", mockServer.getLocalPort())) {
            socket.setSoTimeout(10000);
            send(socket, "/ok");
            String received = readUntil(socket.getInputStream(), "still fine");
            assertThat(received, startsWith("HTTP/1.1 200 OK"));
            assertThat(received, containsString("still fine"));
        }
    }

    @Test(timeout = 30000)
    public void shouldResetWholeHttp2Connection() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            CompletableFuture<Throwable> connectionError = new CompletableFuture<>();
            Channel connection = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build());
                        ch.pipeline().addLast(new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()));
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                connectionError.complete(cause);
                            }
                        });
                    }
                })
                .connect("localhost", mockServer.getLocalPort())
                .sync()
                .channel();
            String authority = "localhost:" + mockServer.getLocalPort();
            CompletableFuture<String> resetStatus = new CompletableFuture<>();
            openStream(connection, authority, "/reset", resetStatus);

            assertThat("the TCP connection must be torn down", connection.closeFuture().await(15, TimeUnit.SECONDS), is(true));
            assertThat("the client must see a reset, not a clean close", describe(connectionError.getNow(null)), containsString("reset"));
            assertThat("no response is sent", resetStatus.isDone(), is(false));
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    @Test(timeout = 30000)
    public void shouldResetClientConnectionThroughConnectTunnel() throws Exception {
        try (Socket socket = new Socket("localhost", mockServer.getLocalPort())) {
            openConnectTunnel(socket);
            assertConnectionReset(socket, "/reset");
        }
    }

    @Test(timeout = 30000)
    public void shouldResetClientTlsConnectionThroughConnectTunnel() throws Exception {
        try (Socket socket = new Socket("localhost", mockServer.getLocalPort())) {
            openConnectTunnel(socket);
            assertConnectionReset(sslSocketFactory().wrapSocket(socket), "/reset");
        }
    }

    @Test(timeout = 30000)
    public void shouldStillCloseCleanlyOnDropConnectionThroughConnectTunnel() throws Exception {
        try (Socket socket = new Socket("localhost", mockServer.getLocalPort())) {
            openConnectTunnel(socket);
            socket.setSoTimeout(10000);
            send(socket, "/drop");
            assertThat("dropConnection must stay a clean close (end of stream)", socket.getInputStream().read(), is(-1));
        }
    }

    @Test(timeout = 30000)
    public void shouldResetClientConnectionThroughSocks5Tunnel() throws Exception {
        try (Socket socket = new Socket("localhost", mockServer.getLocalPort())) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            out.write(new byte[]{5, 1, 0});
            out.flush();
            byte[] method = new byte[2];
            in.readFully(method);
            assertThat(method[1], is((byte) 0));
            int port = mockServer.getLocalPort();
            out.write(new byte[]{5, 1, 0, 1, 127, 0, 0, 1, (byte) (port >> 8), (byte) port});
            out.flush();
            byte[] reply = new byte[5];
            in.readFully(reply);
            assertThat("SOCKS5 connect must succeed", reply[1], is((byte) 0));
            // the bound address is a domain name: its length byte, the name, then the port
            in.readFully(new byte[(reply[4] & 0xff) + 2]);
            assertConnectionReset(socket, "/reset");
        }
    }

    @Test(timeout = 30000)
    public void shouldResetClientHttp2ConnectionThroughConnectTunnel() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            CompletableFuture<Throwable> connectionError = new CompletableFuture<>();
            SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2))
                .build();
            HttpProxyHandler proxyHandler = new HttpProxyHandler(new InetSocketAddress("localhost", mockServer.getLocalPort()));
            Channel connection = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(proxyHandler);
                        ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), "localhost", mockServer.getLocalPort()));
                        ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build());
                        ch.pipeline().addLast(new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()));
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                connectionError.complete(cause);
                            }
                        });
                    }
                })
                .connect(InetSocketAddress.createUnresolved("localhost", mockServer.getLocalPort()))
                .sync()
                .channel();
            proxyHandler.connectFuture().sync();
            SslHandler sslHandler = connection.pipeline().get(SslHandler.class);
            sslHandler.handshakeFuture().sync();
            assertThat(sslHandler.applicationProtocol(), is(ApplicationProtocolNames.HTTP_2));
            CompletableFuture<String> resetStatus = new CompletableFuture<>();
            openStream(connection, "localhost:" + mockServer.getLocalPort(), "/reset", resetStatus);

            assertThat("the client's TCP connection must be torn down", connection.closeFuture().await(15, TimeUnit.SECONDS), is(true));
            assertThat("the client must see a reset, not a clean close or a stream error", describe(connectionError.getNow(null)), containsString("reset"));
            assertThat("no response is sent", resetStatus.isDone(), is(false));
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    private static void openConnectTunnel(Socket socket) throws IOException {
        socket.setSoTimeout(10000);
        String target = "localhost:" + mockServer.getLocalPort();
        socket.getOutputStream().write(("CONNECT " + target + " HTTP/1.1\r\nHost: " + target + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        InputStream in = socket.getInputStream();
        StringBuilder head = new StringBuilder();
        while (!head.toString().endsWith("\r\n\r\n")) {
            int read = in.read();
            if (read == -1) {
                throw new AssertionError("CONNECT response ended early: " + head);
            }
            head.append((char) read);
        }
        assertThat(head.toString(), startsWith("HTTP/1.1 200"));
    }

    private static void openStream(Channel connection, String authority, String path, CompletableFuture<String> status) throws Exception {
        Http2StreamChannel stream = new Http2StreamChannelBootstrap(connection)
            .handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    if (msg instanceof Http2HeadersFrame) {
                        status.complete(String.valueOf(((Http2HeadersFrame) msg).headers().status()));
                    }
                    ReferenceCountUtil.release(msg);
                }
            })
            .open()
            .sync()
            .getNow();
        stream.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()
            .method(HttpMethod.GET.asciiName())
            .scheme(HttpScheme.HTTP.name())
            .authority(authority)
            .path(path), true));
    }

    private static void assertConnectionReset(Socket socket, String path) throws IOException {
        socket.setSoTimeout(10000);
        send(socket, path);
        InputStream in = socket.getInputStream();
        try {
            int read = in.read();
            throw new AssertionError("expected a connection reset but read " + (read == -1 ? "a clean end of stream" : "data"));
        } catch (IOException expected) {
            assertThat(describe(expected), containsString("reset"));
        }
    }

    private static String describe(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            messages.append(cause).append(' ');
        }
        return messages.toString().toLowerCase();
    }

    private static void send(Socket socket, String path) throws IOException {
        socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost:" + mockServer.getLocalPort() + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static String readUntil(InputStream in, String marker) throws IOException {
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while (!received.toString(StandardCharsets.UTF_8).contains(marker) && (read = in.read(buffer)) != -1) {
            received.write(buffer, 0, read);
        }
        return received.toString(StandardCharsets.UTF_8);
    }
}
