package org.mockserver.netty.integration.proxy.http;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.apache.commons.lang3.RandomUtils;
import org.junit.AfterClass;
import org.junit.Test;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.BinaryMessage;
import org.mockserver.scheduler.Scheduler;

import javax.net.ssl.SSLServerSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.BinaryMessage.bytes;
import static org.mockserver.testing.tls.SSLSocketFactory.sslSocketFactory;
import static org.slf4j.event.Level.ERROR;

/**
 * @author jamesdbloom
 */
public class BinaryProxyIntegrationTest {

    private static final EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(BinaryProxyIntegrationTest.class.getSimpleName() + "-eventLoop"));

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Test
    public void shouldForwardBinaryMessages() throws Exception {
        // given
        byte[] randomRequestBytes = RandomUtils.nextBytes(100);
        byte[] randomResponseBytes = RandomUtils.nextBytes(100);
        try {
            try (ServerSocket serverSocket = new ServerSocket(0)) {
                // and
                int serverSocketPort = serverSocket.getLocalPort();
                ClientAndServer proxyClientAndServer = startClientAndServer("127.0.0.1", serverSocketPort);

                CompletableFuture<Socket> socketFuture = new CompletableFuture<>();
                new Scheduler.SchedulerThreadFactory("MockServer Test " + this.getClass().getSimpleName()).newThread(() -> {
                    try {
                        socketFuture.complete(serverSocket.accept());
                    } catch (Throwable throwable) {
                        socketFuture.completeExceptionally(throwable);
                    }
                }).start();

                // when
                CompletableFuture<BinaryMessage> binaryResponseFuture = new NettyHttpClient(new MockServerLogger(), clientEventLoopGroup, null, false)
                    .sendRequest(
                        bytes(randomRequestBytes),
                        false,
                        new InetSocketAddress(proxyClientAndServer.getLocalPort()),
                        (int) SECONDS.toMillis(10)
                    );

                // then
                Socket socket = socketFuture.get(5, MINUTES);
                byte[] receivedBytes = new byte[randomRequestBytes.length];
                int bytesRead = socket.getInputStream().read(receivedBytes);
                assertThat(bytesRead, is(randomRequestBytes.length));
                assertThat(ByteBufUtil.hexDump(receivedBytes), is(ByteBufUtil.hexDump(randomRequestBytes)));

                // when
                socket.getOutputStream().write(randomResponseBytes);
                BinaryMessage binaryResponse = binaryResponseFuture.get(10, SECONDS);

                // then
                assertThat(ByteBufUtil.hexDump(binaryResponse.getBytes()), is(ByteBufUtil.hexDump(randomResponseBytes)));
            }
        } catch (java.net.SocketException se) {
            new MockServerLogger().logEvent(
                new LogEntry()
                    .setLogLevel(ERROR)
                    .setMessageFormat("Exception sending bytes")
                    .setThrowable(se)
            );
            throw se;
        }
    }

    // TODO(jamesdbloom) make this test more stable:
    //      org.mockserver.client.SocketConnectionException: Channel handler removed before valid response has been received
    //        at org.mockserver.client.HttpClientConnectionErrorHandler.handlerRemoved(HttpClientConnectionErrorHandler.java:20)
    //        at io.netty.channel.AbstractChannelHandlerContext.callHandlerRemoved(AbstractChannelHandlerContext.java:946)
    //        at io.netty.channel.DefaultChannelPipeline.callHandlerRemoved0(DefaultChannelPipeline.java:637)
    //        at io.netty.channel.DefaultChannelPipeline.destroyDown(DefaultChannelPipeline.java:876)
    //        at io.netty.channel.DefaultChannelPipeline.destroyUp(DefaultChannelPipeline.java:844)
    //        at io.netty.channel.DefaultChannelPipeline.destroy(DefaultChannelPipeline.java:836)
    //        at io.netty.channel.DefaultChannelPipeline.access$700(DefaultChannelPipeline.java:46)
    //        at io.netty.channel.DefaultChannelPipeline$HeadContext.channelUnregistered(DefaultChannelPipeline.java:1392)
    //        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelUnregistered(AbstractChannelHandlerContext.java:198)
    //        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelUnregistered(AbstractChannelHandlerContext.java:184)
    //        at io.netty.channel.DefaultChannelPipeline.fireChannelUnregistered(DefaultChannelPipeline.java:821)
    //        at io.netty.channel.AbstractChannel$AbstractUnsafe$8.run(AbstractChannel.java:826)
    //        at io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:164)
    //        at io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:472)
    //        at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:497)
    //        at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:989)
    //        at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
    //        at java.lang.Thread.run(Thread.java:748)
    @Test
    public void shouldForwardBinaryMessagesOverTLS() throws Exception {
        // given
        byte[] randomRequestBytes = RandomUtils.nextBytes(100);
        byte[] randomResponseBytes = RandomUtils.nextBytes(100);
        try {
            try (SSLServerSocket serverSocket = sslSocketFactory().wrapSocket()) {
                // and
                ClientAndServer proxyClientAndServer = startClientAndServer("127.0.0.1", serverSocket.getLocalPort());

                CompletableFuture<Socket> socketFuture = new CompletableFuture<>();
                new Scheduler.SchedulerThreadFactory("MockServer Test " + this.getClass().getSimpleName()).newThread(() -> {
                    try {
                        socketFuture.complete(serverSocket.accept());
                    } catch (Throwable throwable) {
                        socketFuture.completeExceptionally(throwable);
                    }
                }).start();

                // when
                CompletableFuture<BinaryMessage> binaryResponseFuture = new NettyHttpClient(new MockServerLogger(), clientEventLoopGroup, null, false)
                    .sendRequest(
                        bytes(randomRequestBytes),
                        true,
                        new InetSocketAddress(proxyClientAndServer.getPort()),
                        (int) SECONDS.toMillis(10)
                    );

                // then
                Socket socket = socketFuture.get(5, MINUTES);
                byte[] receivedBytes = new byte[randomRequestBytes.length];
                int bytesRead = socket.getInputStream().read(receivedBytes);
                assertThat(bytesRead, is(randomRequestBytes.length));
                assertThat(ByteBufUtil.hexDump(receivedBytes), is(ByteBufUtil.hexDump(randomRequestBytes)));

                // when
                socket.getOutputStream().write(randomResponseBytes);
                BinaryMessage binaryResponse = binaryResponseFuture.get(10, SECONDS);

                // then
                assertThat(ByteBufUtil.hexDump(binaryResponse.getBytes()), is(ByteBufUtil.hexDump(randomResponseBytes)));
            }
        } catch (java.net.SocketException se) {
            new MockServerLogger().logEvent(
                new LogEntry()
                    .setLogLevel(ERROR)
                    .setMessageFormat("Exception sending bytes")
                    .setThrowable(se)
            );
            throw se;
        }
    }

    @Test
    public void shouldCloseConnectionForBinaryMessagesWithNoRemoteAddress() throws Exception {
        // given
        byte[] randomRequestBytes = RandomUtils.nextBytes(100);
        ClientAndServer clientAndServer = startClientAndServer();

        // when
        BinaryMessage binaryResponse = new NettyHttpClient(new MockServerLogger(), clientEventLoopGroup, null, false)
            .sendRequest(
                bytes(randomRequestBytes),
                true,
                new InetSocketAddress(clientAndServer.getPort()),
                (int) SECONDS.toMillis(10)
            )
            .get(10, SECONDS);

        // then
        assertThat(ByteBufUtil.hexDump(binaryResponse.getBytes()), is(ByteBufUtil.hexDump("unknown message format".getBytes(StandardCharsets.UTF_8))));
    }

}
