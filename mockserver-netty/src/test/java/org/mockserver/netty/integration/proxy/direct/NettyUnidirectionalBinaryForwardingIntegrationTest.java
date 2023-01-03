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
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.*;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.BinaryMessage;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.proxy.BinaryRequestProxyingHandler;


public class NettyUnidirectionalBinaryForwardingIntegrationTest {

    public static final MockServerLogger LOGGER = new MockServerLogger(
        NettyUnidirectionalBinaryForwardingIntegrationTest.class);
    private static final String MESSAGE = "Hello not world!\n";
    private static final String RESPONSE_MESSAGE = "und einmal zurück!\n";
    private MockServer mockServer;

    @Before
    public void setupFixture() throws IOException {

    }

    @After
    public void shutdownFixture() {
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
            (requestCalls, responseCalls, serverCalled) -> {
                waitForCondition(() -> serverCalled.get() >= 2,
                    () -> "Timeout while waiting for server to receive two messages "
                        + "(got " + serverCalled.get() + ")");
                assertEquals(2, requestCalls.get());
                assertEquals(0, responseCalls.get());
                assertEquals(2, serverCalled.get());
            }
        );
    }

    @Test
    public void sendNonHttpTrafficWithResponseFromServer() throws Exception {
        executeTestRun(
            socket -> {
                writeSingleRequestMessage(socket);
                readSingleResponseMessage(socket);
                writeSingleRequestMessage(socket);
                readSingleResponseMessage(socket);
            },
            serverSocket -> {
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();
            },
            (requestCalls, responseCalls, serverCalled) -> {
                assertContains(requestCalls.toString(), "2");
                assertContains(responseCalls.toString(), "2");
                assertContains(serverCalled.toString(), "2");
            }
        );
    }

    @Test
    public void sendNonHttpTrafficWithMultipleResponsesFromServer() throws Exception {
        executeTestRun(
            socket -> {
                writeSingleRequestMessage(socket);
                readSingleResponseMessage(socket);
                readSingleResponseMessage(socket);
                writeSingleRequestMessage(socket);
                readSingleResponseMessage(socket);
                readSingleResponseMessage(socket);
            },
            serverSocket -> {
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();
            },
            (requestCalls, responseCalls, serverCalled) -> {
                assertEquals(2, requestCalls.get());
                assertEquals(2, responseCalls.get());
                assertEquals(2, serverCalled.get());
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
            (requestCalls, responseCalls, serverCalled) -> {
                waitForCondition(() -> serverCalled.get() >= 2,
                    () -> "Wait timed out. ServerCalled never reached 2, is currently at " + serverCalled.get());
                assertEquals(2, requestCalls.get());
                assertEquals(0, responseCalls.get());
                assertEquals(2, serverCalled.get());
            }
        );
    }


    @Test
    public void sendNonHttpTrafficWithMultipleResponsesFromServerForOnlyASingleRequest() throws Exception {
        AtomicInteger serverResponsesRead = new AtomicInteger(0);
        executeTestRun(
            socket -> {
                writeSingleRequestMessage(socket);
                readSingleResponseMessage(socket);
                serverResponsesRead.incrementAndGet();
                readSingleResponseMessage(socket);
                serverResponsesRead.incrementAndGet();
                readSingleResponseMessage(socket);
                serverResponsesRead.incrementAndGet();
                readSingleResponseMessage(socket);
                serverResponsesRead.incrementAndGet();
            },
            serverSocket -> {
                serverSocket.setTcpNoDelay(true);
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();
                serverSocket.getOutputStream().write(RESPONSE_MESSAGE.getBytes());
                serverSocket.getOutputStream().flush();

            },
            (requestCalls, responseCalls, serverCalled) -> {
                waitForCondition(() -> serverResponsesRead.get() >= 4,
                    () -> "Wait timed out. serverResponsesRead never reached 4, is currently at " + serverResponsesRead.get());
                assertEquals(1, requestCalls.get());
                assertEquals(1, responseCalls.get());
                assertEquals(1, serverCalled.get());
            }
        );
    }

    private static void writeSingleRequestMessage(Socket socket) throws IOException {
        socket.setSendBufferSize(MESSAGE.length());
        OutputStream output = socket.getOutputStream();
        output.write((MESSAGE).getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private static void readSingleResponseMessage(Socket socket) throws IOException, InterruptedException {
        InputStream input = socket.getInputStream();
        waitForCondition(() -> input.available() >= RESPONSE_MESSAGE.length(),
            () -> "Timeout while waiting for message (Found " + input.available()
                + " bytes available, wanted " + RESPONSE_MESSAGE.length() + ")");
        byte[] buffer = new byte[RESPONSE_MESSAGE.length() + 1];
        log("Before reading from buffer. Currently "+socket.getInputStream().available()+" bytes available");
        input.read(buffer, 0, buffer.length);
        log("After reading from buffer. Currently "+socket.getInputStream().available()+" bytes available");
        assertEquals(RESPONSE_MESSAGE, new String(buffer));
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
                listenerServer.getLocalPort(), "127.0.0.1", 50505);
            //BinaryRequestProxyingHandler.binaryRequestMapper = req -> {
            //    final ArrayList<BinaryMessage> binaryMessages = new ArrayList<>();
            //    for (String part : new String(req.getBytes()).split("\n")) {
            //        binaryMessages.add(new BinaryMessage()
            //            .withBytes((part + "\n").getBytes())
            //            .withTimestamp(req.getTimestamp()));
            //    }
            //    return binaryMessages;
            //};

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
            listenerServer.setAcceptedConnectionConsumer(serverSocket -> {
                final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverSocket.getInputStream()));
                final String serverArrivedMessage = reader.readLine();
                log("listener server: read message");
                serverActionCallback.accept(serverSocket);
                assertContains(MESSAGE, serverArrivedMessage);
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
                serverCalled
            );
        } finally {
            stopQuietly(mockServer);
        }
    }

    private static class FlexibleServer implements AutoCloseable {

        private final ServerSocket listenerServer;
        private ThrowingConsumer<Socket> acceptedConnectionConsumer;

        public FlexibleServer() throws InterruptedException {
            try {
                listenerServer = new ServerSocket(60606);
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
                        acceptedConnectionConsumer.accept(serverSocket);
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
            AtomicInteger serverCalls) throws Exception;
    }
}
