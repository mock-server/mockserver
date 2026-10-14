package org.mockserver.netty.integration.proxy.http;

import io.netty.buffer.ByteBufUtil;
import org.junit.After;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.scheduler.Scheduler;

import javax.net.ssl.SSLServerSocket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.testing.tls.SSLSocketFactory.sslSocketFactory;

/**
 * A binary client's bytes are forwarded to the upstream MockServer was started for, and the upstream's answer comes
 * back, in the clear and over TLS; with no upstream they are answered as an unknown message format.
 *
 * @author jamesdbloom
 */
public class BinaryProxyIntegrationTest {

    private ClientAndServer mockServer;

    @After
    public void stopMockServer() {
        stopQuietly(mockServer);
    }

    private static CompletableFuture<Socket> acceptOne(ServerSocket serverSocket) {
        CompletableFuture<Socket> socketFuture = new CompletableFuture<>();
        new Scheduler.SchedulerThreadFactory("MockServer Test " + BinaryProxyIntegrationTest.class.getSimpleName()).newThread(() -> {
            try {
                socketFuture.complete(serverSocket.accept());
            } catch (Throwable throwable) {
                socketFuture.completeExceptionally(throwable);
            }
        }).start();
        return socketFuture;
    }

    private static Socket upstreamConnection(CompletableFuture<Socket> socketFuture) throws Exception {
        try {
            Socket socket = socketFuture.get(10, SECONDS);
            socket.setSoTimeout(10_000);
            return socket;
        } catch (TimeoutException notForwarded) {
            throw new AssertionError("MockServer did not connect to the upstream within 10 seconds", notForwarded);
        }
    }

    private Socket clientOfMockServer(boolean overTls) throws IOException {
        Socket plain = new Socket("127.0.0.1", mockServer.getPort());
        plain.setSoTimeout(10_000);
        return overTls ? sslSocketFactory().wrapSocket(plain) : plain;
    }

    private void shouldForwardAndAnswer(ServerSocket upstream, boolean overTls) throws Exception {
        byte[] randomRequestBytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        byte[] randomResponseBytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        mockServer = startClientAndServer("127.0.0.1", upstream.getLocalPort());
        CompletableFuture<Socket> socketFuture = acceptOne(upstream);

        try (Socket client = clientOfMockServer(overTls)) {
            // when
            client.getOutputStream().write(randomRequestBytes);
            client.getOutputStream().flush();

            // then
            Socket socket = upstreamConnection(socketFuture);
            assertThat(ByteBufUtil.hexDump(socket.getInputStream().readNBytes(randomRequestBytes.length)), is(ByteBufUtil.hexDump(randomRequestBytes)));

            // when
            socket.getOutputStream().write(randomResponseBytes);
            socket.getOutputStream().flush();

            // then
            assertThat(ByteBufUtil.hexDump(client.getInputStream().readNBytes(randomResponseBytes.length)), is(ByteBufUtil.hexDump(randomResponseBytes)));
        }
    }

    @Test
    public void shouldForwardBinaryMessages() throws Exception {
        // 127.0.0.1 itself, where the proxy forwards: bound to the wildcard, the port can be one another process
        // listens on at 127.0.0.1
        try (ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}))) {
            shouldForwardAndAnswer(serverSocket, false);
        }
    }

    @Test
    public void shouldForwardBinaryMessagesOverTLS() throws Exception {
        try (SSLServerSocket serverSocket = sslSocketFactory().wrapSocket()) {
            shouldForwardAndAnswer(serverSocket, true);
        }
    }

    @Test
    public void shouldCloseConnectionForBinaryMessagesWithNoRemoteAddress() throws Exception {
        // given
        byte[] randomRequestBytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        mockServer = startClientAndServer();

        try (Socket client = clientOfMockServer(true)) {
            // when
            client.getOutputStream().write(randomRequestBytes);
            client.getOutputStream().flush();

            // then
            assertThat(readUntilClosed(client.getInputStream()), is("unknown message format, only HTTP requests are supported for mocking or HTTP & binary requests for proxying, but request is not being proxied and request is not valid HTTP"));
        }
    }

    private static String readUntilClosed(InputStream input) {
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        try {
            for (int read = input.read(); read != -1; read = input.read()) {
                received.write(read);
            }
        } catch (IOException closedWithoutCloseNotify) {
            // closed all the same
        }
        return received.toString(StandardCharsets.UTF_8);
    }
}
