package org.mockserver.netty.integration.proxy.direct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Assert.assertContains;
import static org.slf4j.event.Level.INFO;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.*;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.proxy.BinaryRequestProxyingHandler;

public class NettyAssymetricBinaryForwardingIntegrationTest {

    public static final MockServerLogger LOGGER = new MockServerLogger(
        NettyAssymetricBinaryForwardingIntegrationTest.class);
    private String message;
    private MockServer mockServer;

    @Before
    public void setupFixture() throws IOException {
        message = "Hello not world!\n";
    }

    @Test
    public void sendNonHttpTrafficWithoutResponseFromServer() throws Exception {
        executeTestRun(
            socket -> {
                writeSingleRequestMessage(socket);
                writeSingleRequestMessage(socket);
            },
            serverSocket -> {
            },
            (requestCalls, responseCalls, serverCalled, serverReceivedText) -> {
                waitForCondition(() -> serverReceivedText.get().equals(message + message),
                    () -> "Timeout while waiting for server to receive two messages "
                        + "(got " + serverReceivedText.get() + ")");
            }
        );
    }

    @Test
    public void sendNonHttpTrafficWithLongMessageWithoutResponseFromServer() throws Exception {
        message = StringUtils.repeat("LongMessage", 1000) + "\n";
        executeTestRun(
            socket -> {
                writeSingleRequestMessage(socket);
                writeSingleRequestMessage(socket);
            },
            serverSocket -> {
            },
            (requestCalls, responseCalls, serverCalled, serverReceivedText) ->
                waitForCondition(() -> serverReceivedText.get().equals(message + message),
                    () -> "Timeout while waiting for server to receive two messages "
                        + "(got " + serverReceivedText.get().length() + ", expected " + message.length() * 2 + ")")
        );
    }

    @Test
    public void sendNonHttpTrafficWithLongMessageWithoutResponseAndWithSocketCloseBetweenEachMessage()
        throws Exception {
        executeTestRun(
            socket -> {
                socket.close();
                try (Socket clientSocket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                    writeSingleRequestMessage(clientSocket);
                }

            },
            serverSocket -> {
            },
            (requestCalls, responseCalls, serverCalled, serverReceivedText) -> {
                waitForCondition(() -> serverReceivedText.get().equals(message),
                    () -> "Timeout while waiting for server to receive two messages "
                        + "(got \n" + serverReceivedText.get() + ", expected \n" + message + ")");
                try (Socket clientSocket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                    writeSingleRequestMessage(clientSocket);
                }
                waitForCondition(() -> serverReceivedText.get().equals(message + message),
                    () -> "Timeout while waiting for server to receive two messages "
                        + "(got \n" + serverReceivedText.get() + ", expected \n" + message + message + ")");
            }
        );
    }

    @Test
    public void sendNonHttpTrafficWithoutResponseAndWithSocketCloseBetweenEachMessage() throws Exception {
        executeTestRun(
            socket -> {
                socket.close();
                try (Socket clientSocket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                    writeSingleRequestMessage(clientSocket);
                }
                try (Socket clientSocket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                    writeSingleRequestMessage(clientSocket);
                }
            },
            serverSocket -> {
            },
            (requestCalls, responseCalls, serverCalled, serverReceivedText) -> {
                waitForCondition(() -> serverCalled.get() >= 2,
                    () -> "Wait timed out. ServerCalled never reached 2, is currently at " + serverCalled.get());
                assertEquals(2, requestCalls.get());
                assertEquals(0, responseCalls.get());
                assertEquals(2, serverCalled.get());
            }
        );
    }

    private void writeSingleRequestMessage(Socket socket) throws IOException {
        socket.setSendBufferSize(message.length());
        OutputStream output = socket.getOutputStream();
        output.write((message).getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private static void log(String s) {
        LOGGER.logEvent(new LogEntry()
            .setType(SERVER_CONFIGURATION)
            .setLogLevel(INFO)
            .setMessageFormat(s));
    }

    private static void waitForCondition(ThrowingSupplier<Boolean> condition, ThrowingSupplier<String> errorMessage)
        throws InterruptedException {
        int millisWaited = 0;
        while (!condition.get()) {
            Thread.sleep(1);
            if (++millisWaited > 1000) {
                throw new AssertionError(errorMessage.get());
            }
        }
    }

    private void executeTestRun(
        ThrowingConsumer<Socket> clientActionCallback,
        ThrowingConsumer<Socket> serverActionCallback,
        VerifyInteractionsConsumer interactionsVerificationCallback
    ) throws Exception {
        try (FlexibleServer listenerServer = new FlexibleServer()) {
            mockServer = new MockServer(Configuration.configuration().forwardBinaryRequestsAsynchronously(true),
                listenerServer.getLocalPort(), "127.0.0.1", 0);

            AtomicInteger handlerCalledRequest = new AtomicInteger(0);
            AtomicInteger handlerCalledResponse = new AtomicInteger(0);
            AtomicInteger serverCalled = new AtomicInteger(0);
            BinaryRequestProxyingHandler.binaryExchangeCallback = desc -> {
                if (desc.getBinaryResponse() != null) {
                    handlerCalledResponse.incrementAndGet();
                    log("call received to the binary handler. resp is '" +
                        new String(desc.getBinaryResponse().getBytes()));
                }
                if (desc.getBinaryRequest() != null) {
                    handlerCalledRequest.incrementAndGet();
                    log("call received to the binary handler. req is '" +
                        new String(desc.getBinaryRequest().getBytes()) + "'");
                }
            };
            AtomicReference<String> accumulatedServerReceivedText = new AtomicReference<>("");
            listenerServer.setAcceptedConnectionConsumer(serverSocket -> {
                accumulatedServerReceivedText.getAndAccumulate(drainStream(serverSocket), (o1, o2) -> o1 + o2);
                serverActionCallback.accept(serverSocket);
                serverCalled.incrementAndGet();
            });
            try (Socket clientSocket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                log("listenerServer on port: " + listenerServer.getLocalPort());
                clientActionCallback.accept(clientSocket);
            }

            log("Verifying interactions... (requests=" + handlerCalledRequest.get() + ", response="
                + handlerCalledResponse.get() + ", serverCalled=" + serverCalled.get() + ")");
            interactionsVerificationCallback.acceptThrows(
                handlerCalledRequest,
                handlerCalledResponse,
                serverCalled,
                accumulatedServerReceivedText
            );
        } finally {
            stopQuietly(mockServer);
        }
    }

    private String drainStream(Socket serverSocket) {
        String result = "";
        try {
            InputStream inputStream = serverSocket.getInputStream();
            do {
                final byte[] buffer = new byte[10000];
                final int readBytes = inputStream.read(buffer);
                result = result + new String(Arrays.copyOfRange(buffer, 0, readBytes),
                    StandardCharsets.UTF_8);
            } while (inputStream.available() > 0);
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

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
                isServerReady.set(true);
                log("listener server: waiting for incoming... ");
                while (true) {
                    try {
                        final Socket serverSocket = listenerServer.accept();
                        log("listener server: got connection!");
                        new Thread(() -> acceptedConnectionConsumer.accept(serverSocket)).start();
                        log("listener server: after assert");
                    } catch (IOException e) {
                        // swallow. makes for a less confusing test run output
                    }
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

    @FunctionalInterface
    public interface ThrowingSupplier<T> extends Supplier<T> {

        @Override
        default T get() {
            try {
                return getThrows();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        T getThrows() throws Exception;
    }

    public interface VerifyInteractionsConsumer {

        void acceptThrows(AtomicInteger request,
            AtomicInteger responses,
            AtomicInteger serverCalls,
            AtomicReference<String> serverReceivedMessage) throws Exception;
    }
}
