package org.mockserver.netty.integration.proxy.direct;

import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Assert.assertContains;
import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.INFO;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.http.util.ByteArrayBuffer;
import org.junit.*;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.proxy.BinaryRequestProxyingHandler;
import org.mockserver.streams.IOStreamUtils;


public class NettyUnidirectionalBinaryForwardingIntegrationTest {

    public static final MockServerLogger LOGGER = new MockServerLogger(
        NettyUnidirectionalBinaryForwardingIntegrationTest.class);
    private static final String MESSAGE = "Hello not world!";
    private MockServer mockServer;

    @Before
    public void setupFixture() throws IOException {

    }

    @After
    public void shutdownFixture() {
    }

    @Test
    public void sendNonHttpTrafficWithoutResponseFromServer() throws Exception {
        try (FlexibleServer listenerServer = new FlexibleServer()) {
            mockServer = new MockServer(listenerServer.getLocalPort(), "127.0.0.1", 0);
            AtomicBoolean handlerCalled = new AtomicBoolean(false);
            AtomicBoolean serverCalled = new AtomicBoolean(false);
            BinaryRequestProxyingHandler.binaryExchangeCallback = desc -> {
                handlerCalled.set(true);
                log("call received to the binary handler");
            };
            listenerServer.setAcceptedConnectionConsumer(serverSocket -> {
                final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverSocket.getInputStream()));
                final String serverArrivedMessage = reader.readLine();
                log("listener server: read message");
                assertContains(serverArrivedMessage, MESSAGE);
                serverCalled.set(true);
            });
            try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                log("listenerServer on port: " + listenerServer.getLocalPort());

                OutputStream output = socket.getOutputStream();
                output.write((MESSAGE + "\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            Thread.sleep(100);
            assertContains(handlerCalled.toString(), "true");
            assertContains(serverCalled.toString(), "true");
        } finally {
            stopQuietly(mockServer);
        }
    }

    private static void log(String s) {
        LOGGER.logEvent(new LogEntry()
            .setType(SERVER_CONFIGURATION)
            .setLogLevel(INFO)
            .setMessageFormat(s));
    }

    //TODO test für websocket style (einmal client, mehrmals server)
    //TODO CETP style (mehrere client nachrichten OHNE close, keine server response)
    //TODO CETP monk style (mehrere client nachrichten MIT close, keine server response)

    private static class FlexibleServer implements AutoCloseable {

        private final ServerSocket listenerServer;
        private ThrowingConsumer<Socket> acceptedConnectionConsumer;

        public FlexibleServer() throws InterruptedException {
            try {
                listenerServer = new ServerSocket(0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            AtomicBoolean isServerReady = new AtomicBoolean(false);
            new Thread(() -> {
                try {
                    log("listener server: waiting for incoming...");
                    isServerReady.set(true);
                    final Socket serverSocket = listenerServer.accept();
                    log("listener server: got connection!");
                    acceptedConnectionConsumer.accept(serverSocket);
                    log("listener server: after assert");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                try {
                    listenerServer.close();
                    log("listener server: after close (success)");
                } catch (IOException e) {
                    log("listener server: after close (fail)");
                    throw new RuntimeException(e);
                }
            }).start();
            Thread.sleep(100);
            while (!isServerReady.get()) {
                Thread.sleep(1);
            }
        }

        @Override
        public void close() throws Exception {
            listenerServer.close();
        }

        public void setAcceptedConnectionConsumer(ThrowingConsumer<Socket> acceptedConnectionConsumer) {
            this.acceptedConnectionConsumer = acceptedConnectionConsumer;
        }

        public Integer getLocalPort() {
            return listenerServer.getLocalPort();
        }
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> extends Consumer<T> {

        @Override
        default void accept(final T elem) {
            try {
                acceptThrows(elem);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        void acceptThrows(T elem) throws Exception;
    }
}
