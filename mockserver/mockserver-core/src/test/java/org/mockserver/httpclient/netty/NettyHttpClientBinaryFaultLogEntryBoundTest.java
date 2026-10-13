package org.mockserver.httpclient.netty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.Test;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.slf4j.event.Level;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.BinaryMessage.bytes;

/**
 * A binary request over TLS to an upstream that answers with bytes that are not a TLS record fails with the failure
 * the caller logs, once, with the message's correlation id: the forward client logs no entry for it, so none quotes
 * those bytes. The forward client's TLS handler is the JDK's here, as it is without the OpenSSL native: OpenSSL's
 * message holds no dump. What the client logs of the message it sends is bounded by maxLoggedBodyBytes.
 */
public class NettyHttpClientBinaryFaultLogEntryBoundTest {

    private static final int BYTES_SENT = 20_000;

    private final List<LogEntry> logged = new CopyOnWriteArrayList<>();
    private final MockServerLogger mockServerLogger = new MockServerLogger(NettyHttpClientBinaryFaultLogEntryBoundTest.class) {
        @Override
        public boolean isEnabledForInstance(Level level) {
            return true;
        }

        @Override
        public void logEvent(LogEntry logEntry) {
            logged.add(logEntry);
        }
    };

    @Test(timeout = 60_000)
    public void shouldLogABinaryRequestToAnUpstreamThatIsNotTlsWithoutItsBytes() throws Exception {
        EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(1, new Scheduler.SchedulerThreadFactory(NettyHttpClientBinaryFaultLogEntryBoundTest.class.getSimpleName() + "-client"));
        try (ServerSocket upstream = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            Thread answer = new Thread(() -> {
                try (Socket socket = upstream.accept()) {
                    socket.setSoTimeout(30_000);
                    socket.getInputStream().read();
                    byte[] notTls = new byte[BYTES_SENT];
                    Arrays.fill(notTls, (byte) 'A');
                    socket.getOutputStream().write(notTls);
                    socket.getOutputStream().flush();
                    while (socket.getInputStream().read() != -1) {
                        continue;
                    }
                } catch (Exception closed) {
                    // the client has gone
                }
            });
            answer.setDaemon(true);
            answer.start();
            NettySslContextFactory jdkTls = new NettySslContextFactory(configuration(), mockServerLogger, false) {
                @Override
                public SslContext createClientSslContext(boolean forwardProxyClient, boolean enableHttp2, String host) {
                    try {
                        return SslContextBuilder.forClient().sslProvider(SslProvider.JDK).trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                    } catch (javax.net.ssl.SSLException e) {
                        throw new IllegalStateException(e);
                    }
                }
            };
            NettyHttpClient client = new NettyHttpClient(configuration(), mockServerLogger, clientEventLoopGroup, null, true, jdkTls);

            ExecutionException failed = assertThrows(ExecutionException.class, () -> client
                .sendRequest(bytes("binary request".getBytes()), true, new InetSocketAddress("127.0.0.1", upstream.getLocalPort()), 5_000L)
                .get(30, TimeUnit.SECONDS));

            assertThat(failed.getCause().toString(), containsString("not an SSL/TLS record"));
            // the response future fails as the channel's pipeline does, so what it logged is already in the list
            List<LogEntry> aboutTheFailure = logged.stream()
                .filter(entry -> entry.getThrowable() != null || entry.getMessage(configuration()).contains("not an SSL/TLS record"))
                .collect(Collectors.toList());
            assertThat(aboutTheFailure.toString(), aboutTheFailure, is(empty()));
            for (LogEntry entry : logged) {
                // eight hex digits, so a five-digit port such as 41415 cannot match
                assertThat(entry.getMessage(configuration()), not(containsString("41".repeat(4))));
            }
        } finally {
            clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
        }
    }
    @Test(timeout = 60_000)
    public void shouldLogAtMostTheFirstMaxLoggedBodyBytesOfABinaryRequestItSends() throws Exception {
        EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(1, new Scheduler.SchedulerThreadFactory(NettyHttpClientBinaryFaultLogEntryBoundTest.class.getSimpleName() + "-client"));
        try (ServerSocket upstream = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            byte[] request = new byte[1_000];
            Arrays.fill(request, (byte) 'A');
            NettyHttpClient client = new NettyHttpClient(configuration().maxLoggedBodyBytes(16), mockServerLogger, clientEventLoopGroup, null, false);

            client.sendRequest(bytes(request), false, new InetSocketAddress("127.0.0.1", upstream.getLocalPort()), 5_000L);
            upstream.setSoTimeout(30_000);
            try (Socket accepted = upstream.accept()) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                while (sent().isEmpty() && System.nanoTime() < deadline) {
                    Thread.sleep(20);
                }
            }

            List<LogEntry> sent = sent();
            assertThat(sent, hasSize(1));
            String text = sent.get(0).getMessage(configuration());
            assertThat(text, containsString("41".repeat(16) + "...(1000 bytes, only the first 16 logged, maxLoggedBodyBytes)"));
            assertThat(text, not(containsString("41".repeat(17))));
        } finally {
            clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
        }
    }

    private List<LogEntry> sent() {
        return logged.stream().filter(entry -> entry.getLogLevel() == Level.DEBUG && "sending bytes hex{}to{}".equals(entry.getMessageFormat())).collect(Collectors.toList());
    }
}
