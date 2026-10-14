package org.mockserver.netty.integration.proxy.direct;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.exception.ExceptionHandling.ThrowingConsumer;
import org.mockserver.model.BinaryMessage;
import org.mockserver.netty.MockServer;
import org.mockserver.test.IsDebug;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.mockserver.logging.BasicLogger.logInfo;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Retries.tryWaitForSuccess;

public class NettyAssymetricBinaryForwardingIntegrationTest {

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
            (proxyListenerCalledWithNonNullRequestCounter, proxyListenerCalledWithNonNullResponseCounter, upstream) ->
                tryWaitForSuccess(
                    () -> assertThat("Timeout while waiting for server to receive two messages (got " + upstream.receivedText() + ")", upstream.receivedText(), is(message + message))
                ),
            true
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
            (proxyListenerCalledWithNonNullRequestCounter, proxyListenerCalledWithNonNullResponseCounter, upstream) ->
                tryWaitForSuccess(
                    () -> assertThat(
                        "Timeout while waiting for server to receive two messages (got " + upstream.receivedText().length() + ", expected " + message.length() * 2 + ")",
                        upstream.receivedText().equals(message + message)
                    ), 150, 100, TimeUnit.MILLISECONDS
                ),
            false
        );
    }

    @Test
    public void sendNonHttpTrafficWithLongMessageWithoutResponseAndWithSocketCloseBetweenEachMessage() throws Exception {
        executeTestRun(
            socket -> {
                socket.close();
                try (Socket clientSocket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                    writeSingleRequestMessage(clientSocket);
                }

            },
            (proxyListenerCalledWithNonNullRequestCounter, proxyListenerCalledWithNonNullResponseCounter, upstream) -> {
                tryWaitForSuccess(
                    () -> assertThat(
                        "Timeout while waiting for server to receive two messages (got \n" + upstream.receivedText() + ", expected \n" + message + ")",
                        upstream.receivedText(),
                        is(message)
                    )
                );
                try (Socket clientSocket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                    writeSingleRequestMessage(clientSocket);
                }
                tryWaitForSuccess(
                    () -> assertThat(
                        "Timeout while waiting for server to receive two messages (got \n" + upstream.receivedText() + ", expected \n" + message + message + ")",
                        upstream.receivedText(),
                        is(message + message)
                    )
                );
            },
            true
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
            (proxyListenerCalledWithNonNullRequestCounter, proxyListenerCalledWithNonNullResponseCounter, upstream) ->
                tryWaitForSuccess(
                    () -> {
                        assertThat(
                            "Wait timed out. ServerCalled never reached 2, is currently at " + upstream.connectionsThatReceivedData(),
                            upstream.connectionsThatReceivedData(),
                            is(2)
                        );
                        assertThat("expect proxy listener to be called with non null request 2 times", proxyListenerCalledWithNonNullRequestCounter.get(), is(2));
                        assertThat("expect proxy listener to be called with non null response 2 times", proxyListenerCalledWithNonNullResponseCounter.get(), is(0));
                        assertThat("expect upstream to be called 2 times", upstream.connectionsThatReceivedData(), is(2));
                    }
                ),
            true
        );
    }

    @Test
    public void shouldForwardTheNextMessageWhileTheListenerStillWaitsOnTheFirst() throws Exception {
        CountDownLatch listenerCalled = new CountDownLatch(1);
        CountDownLatch listenerMayReturn = new CountDownLatch(1);
        List<String> reported = new CopyOnWriteArrayList<>();
        AtomicInteger callsInProgress = new AtomicInteger(0);
        AtomicInteger mostCallsInProgress = new AtomicInteger(0);
        try (FlexibleServer upstream = new FlexibleServer()) {
            Configuration configuration = Configuration.configuration()
                .binaryProxyListener((binaryRequest, binaryResponse, serverAddress, clientAddress) -> {
                    mostCallsInProgress.accumulateAndGet(callsInProgress.incrementAndGet(), Math::max);
                    reported.add(new String(binaryRequest.getBytes(), StandardCharsets.UTF_8));
                    listenerCalled.countDown();
                    try {
                        listenerMayReturn.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        callsInProgress.decrementAndGet();
                    }
                });
            mockServer = new MockServer(configuration, upstream.getLocalPort(), "127.0.0.1", 0);

            try (Socket clientSocket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                OutputStream output = clientSocket.getOutputStream();
                output.write("first message\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                assertThat("the listener is called for the first message", listenerCalled.await(10, TimeUnit.SECONDS), is(true));

                // the listener has not returned, as one waiting for a response that never comes would not
                output.write("second message\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                tryWaitForSuccess(
                    () -> assertThat("the second message is forwarded while the listener still holds the first", upstream.receivedText(), is("first message\nsecond message\n"))
                );

                listenerMayReturn.countDown();
                tryWaitForSuccess(
                    () -> assertThat("the listener is told of every message, in the order they arrived", reported, contains("first message\n", "second message\n"))
                );
                assertThat("one connection's messages are reported one at a time", mostCallsInProgress.get(), is(1));
            }
        } finally {
            listenerMayReturn.countDown();
            stopQuietly(mockServer);
        }
    }

    @Test
    public void shouldCloseTheConnectionWhenTheListenerThrows() throws Exception {
        try (FlexibleServer upstream = new FlexibleServer()) {
            Configuration configuration = Configuration.configuration()
                .binaryProxyListener((binaryRequest, binaryResponse, serverAddress, clientAddress) -> {
                    throw new IllegalStateException("the listener failed");
                });
            mockServer = new MockServer(configuration, upstream.getLocalPort(), "127.0.0.1", 0);

            try (Socket clientSocket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                clientSocket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10));
                writeSingleRequestMessage(clientSocket);

                assertThat("MockServer closes the connection", clientSocket.getInputStream().read(), is(-1));
            }
        } finally {
            stopQuietly(mockServer);
        }
    }

    private void executeTestRun(
        ThrowingConsumer<Socket> clientActionCallback,
        VerifyInteractionsConsumer interactionsVerificationCallback,
        boolean waitForResponse) throws Exception {
        try (FlexibleServer upstream = new FlexibleServer()) {
            Configuration configuration = Configuration.configuration();

            // given - mockserver proxy listener
            AtomicInteger proxyListenerCalledWithNonNullRequestCounter = new AtomicInteger(0);
            AtomicInteger proxyListenerCalledWithNonNullResponseCounter = new AtomicInteger(0);
            configuration
                .binaryProxyListener((binaryRequest, binaryResponse, serverAddress, clientAddress) -> {
                    try {
                        if (binaryRequest != null) {
                            proxyListenerCalledWithNonNullRequestCounter.incrementAndGet();
                            logInfo("call received to the binary handler. req is '" + new String(binaryRequest.getBytes()) + "'");
                        }
                        BinaryMessage binaryMessage = waitForResponse ? binaryResponse.get(10, IsDebug.timeoutUnits()) : null;
                        if (binaryMessage != null) {
                            proxyListenerCalledWithNonNullResponseCounter.incrementAndGet();
                            logInfo("call received to the binary handler. resp is '" + new String(binaryMessage.getBytes()));
                        }
                    } catch (Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                });

            // and - mockserver
            mockServer = new MockServer(configuration, upstream.getLocalPort(), "127.0.0.1", 0);

            // when
            try (Socket clientSocket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                logInfo("upstream on port: " + upstream.getLocalPort());
                clientActionCallback.accept(clientSocket);
            }

            // then
            logInfo("verifying interactions... (requests=" + proxyListenerCalledWithNonNullRequestCounter.get() + ", response=" + proxyListenerCalledWithNonNullResponseCounter.get() + ", upstreamReceivedMessage=" + upstream.connectionsThatReceivedData() + ")");
            interactionsVerificationCallback.acceptThrows(
                proxyListenerCalledWithNonNullRequestCounter,
                proxyListenerCalledWithNonNullResponseCounter,
                upstream
            );
        } finally {
            stopQuietly(mockServer);
        }
    }

    private void writeSingleRequestMessage(Socket socket) throws IOException {
        socket.setSendBufferSize(message.length());
        OutputStream output = socket.getOutputStream();
        output.write((message).getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    /**
     * An upstream that never answers. What it has received is every connection's bytes, joined in the order the
     * connections were accepted.
     */
    private static class FlexibleServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final List<Socket> connections = new CopyOnWriteArrayList<>();
        private final List<ByteArrayOutputStream> receivedByConnection = new CopyOnWriteArrayList<>();

        public FlexibleServer() throws IOException {
            // 127.0.0.1 itself, where MockServer connects: bound to the wildcard, the port can be one another
            // process listens on at 127.0.0.1, and that process then receives MockServer's connections
            serverSocket = new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
            new Thread(this::acceptConnections, "upstream-accept").start();
        }

        private void acceptConnections() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket connection = serverSocket.accept();
                    ByteArrayOutputStream received = new ByteArrayOutputStream();
                    connections.add(connection);
                    receivedByConnection.add(received);
                    if (serverSocket.isClosed()) {
                        // accepted while close() was closing the connections it could see
                        connection.close();
                    }
                    new Thread(() -> readUntilClosed(connection, received), "upstream-read").start();
                } catch (IOException closed) {
                    // close() ends the accept
                }
            }
        }

        private static void readUntilClosed(Socket connection, ByteArrayOutputStream received) {
            byte[] buffer = new byte[10000];
            try {
                InputStream inputStream = connection.getInputStream();
                for (int read; (read = inputStream.read(buffer)) != -1; ) {
                    received.write(buffer, 0, read);
                }
            } catch (IOException closed) {
                // MockServer, or close(), ended the connection
            }
        }

        public String receivedText() {
            StringBuilder text = new StringBuilder();
            for (ByteArrayOutputStream received : receivedByConnection) {
                text.append(new String(received.toByteArray(), StandardCharsets.UTF_8));
            }
            return text.toString();
        }

        public int connectionsThatReceivedData() {
            int count = 0;
            for (ByteArrayOutputStream received : receivedByConnection) {
                if (received.size() > 0) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            for (Socket connection : connections) {
                connection.close();
            }
        }

        public Integer getLocalPort() {
            return serverSocket.getLocalPort();
        }
    }

    public interface VerifyInteractionsConsumer {
        void acceptThrows(AtomicInteger proxyListenerCalledWithNonNullRequestCounter,
                          AtomicInteger proxyListenerCalledWithNonNullResponseCounter,
                          FlexibleServer upstream) throws Exception;
    }
}
