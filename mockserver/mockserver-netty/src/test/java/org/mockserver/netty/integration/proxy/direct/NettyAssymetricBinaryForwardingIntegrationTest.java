package org.mockserver.netty.integration.proxy.direct;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.exception.ExceptionHandling;
import org.mockserver.exception.ExceptionHandling.ThrowingConsumer;
import org.mockserver.model.BinaryMessage;
import org.mockserver.netty.MockServer;
import org.mockserver.test.IsDebug;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.exception.ExceptionHandling.swallowThrowable;
import static org.mockserver.logging.BasicLogger.logInfo;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.streams.IOStreamUtils.readSocketToString;
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
            serverSocket -> {
            },
            (proxyListenerCalledWithNonNullRequestCounter, proxyListenerCalledWithNonNullResponseCounter, upstreamReceivedMessageCounter, accumulatedUpstreamReceivedText) ->
                tryWaitForSuccess(
                    () -> assertThat("Timeout while waiting for server to receive two messages (got " + accumulatedUpstreamReceivedText.get() + ")", accumulatedUpstreamReceivedText.get(), is(message + message))
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
            serverSocket -> {
            },
            (proxyListenerCalledWithNonNullRequestCounter, proxyListenerCalledWithNonNullResponseCounter, upstreamReceivedMessageCounter, accumulatedUpstreamReceivedText) ->
                tryWaitForSuccess(
                    () -> assertThat(
                        "Timeout while waiting for server to receive two messages (got " + accumulatedUpstreamReceivedText.get().length() + ", expected " + message.length() * 2 + ")",
                        accumulatedUpstreamReceivedText.get().equals(message + message)
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
            serverSocket -> {
            },
            (proxyListenerCalledWithNonNullRequestCounter, proxyListenerCalledWithNonNullResponseCounter, upstreamReceivedMessageCounter, accumulatedUpstreamReceivedText) -> {
                tryWaitForSuccess(
                    () -> assertThat(
                        "Timeout while waiting for server to receive two messages (got \n" + accumulatedUpstreamReceivedText.get() + ", expected \n" + message + ")",
                        accumulatedUpstreamReceivedText.get(),
                        is(message)
                    )
                );
                try (Socket clientSocket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                    writeSingleRequestMessage(clientSocket);
                }
                tryWaitForSuccess(
                    () -> assertThat(
                        "Timeout while waiting for server to receive two messages (got \n" + accumulatedUpstreamReceivedText.get() + ", expected \n" + message + message + ")",
                        accumulatedUpstreamReceivedText.get(),
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
            serverSocket -> {
            },
            (proxyListenerCalledWithNonNullRequestCounter, proxyListenerCalledWithNonNullResponseCounter, upstreamReceivedMessageCounter, accumulatedUpstreamReceivedText) ->
                tryWaitForSuccess(
                    () -> {
                        assertThat(
                            "Wait timed out. ServerCalled never reached 2, is currently at " + upstreamReceivedMessageCounter.get(),
                            upstreamReceivedMessageCounter.get(),
                            is(2)
                        );
                        assertThat("expect proxy listener to be called with non null request 2 times", proxyListenerCalledWithNonNullRequestCounter.get(), is(2));
                        assertThat("expect proxy listener to be called with non null response 2 times", proxyListenerCalledWithNonNullResponseCounter.get(), is(0));
                        assertThat("expect upstream to be called 2 times", upstreamReceivedMessageCounter.get(), is(2));
                    }
                ),
            true
        );
    }

    private void executeTestRun(
        ThrowingConsumer<Socket> clientActionCallback,
        ThrowingConsumer<Socket> upstreamActionCallback,
        VerifyInteractionsConsumer interactionsVerificationCallback,
        boolean waitForResponse) throws Exception {
        try (FlexibleServer upstream = new FlexibleServer()) {
            Configuration configuration = Configuration.configuration();

            // given - mockserver proxy listener
            AtomicInteger proxyListenerCalledWithNonNullRequestCounter = new AtomicInteger(0);
            AtomicInteger proxyListenerCalledWithNonNullResponseCounter = new AtomicInteger(0);
            AtomicInteger upstreamReceivedMessageCounter = new AtomicInteger(0);
            configuration
                .forwardBinaryRequestsWithoutWaitingForResponse(true)
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

            // and - upstream listener
            AtomicReference<String> accumulatedUpstreamReceivedText = new AtomicReference<>("");
            upstream.setAcceptedConnectionConsumer(serverSocket -> {
                accumulatedUpstreamReceivedText.getAndAccumulate(readSocketToString(serverSocket), (inputOne, inputTwo) -> inputOne + inputTwo);
                upstreamReceivedMessageCounter.incrementAndGet();
                upstreamActionCallback.accept(serverSocket);
            });

            // and - mockserver
            mockServer = new MockServer(configuration, upstream.getLocalPort(), "127.0.0.1", 0);

            // when
            try (Socket clientSocket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                logInfo("upstream on port: " + upstream.getLocalPort());
                clientActionCallback.accept(clientSocket);
            }

            // then
            logInfo("verifying interactions... (requests=" + proxyListenerCalledWithNonNullRequestCounter.get() + ", response=" + proxyListenerCalledWithNonNullResponseCounter.get() + ", upstreamReceivedMessage=" + upstreamReceivedMessageCounter.get() + ")");
            interactionsVerificationCallback.acceptThrows(
                proxyListenerCalledWithNonNullRequestCounter,
                proxyListenerCalledWithNonNullResponseCounter,
                upstreamReceivedMessageCounter,
                accumulatedUpstreamReceivedText
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

    private static class FlexibleServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private ThrowingConsumer<Socket> acceptedConnectionConsumer;

        public FlexibleServer() throws Exception {
            serverSocket = new ServerSocket(0);
            final CompletableFuture<Void> serverReady = new CompletableFuture<>();
            new Thread(() -> {
                logInfo("upstream: waiting for connection");
                serverReady.complete(null);
                while (!serverSocket.isClosed()) {
                    swallowThrowable(() -> {
                        Socket serverSocket = this.serverSocket.accept();
                        logInfo("upstream: got connection");
                        new Thread(() -> {
                            acceptedConnectionConsumer.accept(serverSocket);
                            logInfo("upstream: processed message");
                        }).start();
                    });
                }
            }).start();
            ExceptionHandling.handleThrowable(serverReady, 20, IsDebug.timeoutUnits());
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
        }

        public void setAcceptedConnectionConsumer(ThrowingConsumer<Socket> acceptedConnectionConsumer) {
            this.acceptedConnectionConsumer = acceptedConnectionConsumer;
        }

        public Integer getLocalPort() {
            return serverSocket.getLocalPort();
        }
    }

    public interface VerifyInteractionsConsumer {
        void acceptThrows(AtomicInteger proxyListenerCalledWithNonNullRequestCounter,
                          AtomicInteger proxyListenerCalledWithNonNullResponseCounter,
                          AtomicInteger upstreamReceivedMessageCounter,
                          AtomicReference<String> accumulatedUpstreamReceivedText) throws Exception;
    }
}
