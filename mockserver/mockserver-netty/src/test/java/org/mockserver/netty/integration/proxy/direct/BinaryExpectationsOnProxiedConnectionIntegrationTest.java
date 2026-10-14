package org.mockserver.netty.integration.proxy.direct;

import io.netty.buffer.ByteBufUtil;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.BinaryMessage;
import org.slf4j.event.Level;

import javax.net.ssl.SSLSocket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.integration.proxy.direct.StartTlsUpstream.SSL_REQUEST;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Retries.tryWaitForSuccess;

/**
 * With {@code matchBinaryExpectationsWhenProxying}, over real sockets: on a binary connection relayed on one
 * upstream connection, a message whose bytes equal a binary expectation's is answered by MockServer and never
 * reaches the upstream, and every other message is relayed as without the setting.
 */
public class BinaryExpectationsOnProxiedConnectionIntegrationTest {

    private static final byte[] STARTUP = {0, 0, 0, 23, 0, 3, 0, 0, 'u', 's', 'e', 'r', 0, 'p', 'o', 's', 't', 'g', 'r', 'e', 's', 0, 0};
    private static final byte[] AUTHENTICATION_OK_AND_READY = {'R', 0, 0, 0, 8, 0, 0, 0, 0, 'Z', 0, 0, 0, 5, 'I'};
    private static final byte[] QUERY = {'Q', 0, 0, 0, 13, 'S', 'E', 'L', 'E', 'C', 'T', ' ', '1', 0};
    private static final byte[] READY = {'Z', 0, 0, 0, 5, 'I'};
    private static final byte[] MOCKED_QUERY = {'Q', 0, 0, 0, 13, 'S', 'E', 'L', 'E', 'C', 'T', ' ', '2', 0};
    private static final byte[] CANNED = {'C', 0, 0, 0, 13, 'S', 'E', 'L', 'E', 'C', 'T', ' ', '1', 0, 'Z', 0, 0, 0, 5, 'I'};

    private final List<LogEntry> logged = new CopyOnWriteArrayList<>();
    private ClientAndServer mockServer;

    @After
    public void stopMockServer() {
        MockServerLogger.setGlobalLogEventListener(null);
        stopQuietly(mockServer);
    }

    private static Configuration matchingExpectations() {
        return configuration().logLevel("INFO").matchBinaryExpectationsWhenProxying(true);
    }

    private Socket clientOf(Configuration configuration, Upstream upstream) throws IOException {
        // a clone: the event log clears the entry it is handed once it has copied it
        MockServerLogger.setGlobalLogEventListener(logEntry -> logged.add(logEntry.clone()));
        mockServer = upstream == null ? startClientAndServer(configuration) : startClientAndServer(configuration, "127.0.0.1", upstream.port());
        return connect();
    }

    private Socket clientThrough(Configuration configuration, StartTlsUpstream upstream) throws IOException {
        MockServerLogger.setGlobalLogEventListener(logEntry -> logged.add(logEntry.clone()));
        mockServer = startClientAndServer(configuration, "127.0.0.1", upstream.port());
        return connect();
    }

    private Socket connect() throws IOException {
        Socket client = new Socket("127.0.0.1", mockServer.getPort());
        client.setSoTimeout(20_000);
        client.setTcpNoDelay(true);
        return client;
    }

    private void mock(String request, String response) {
        mockServer.upsert(new Expectation(binaryRequest(bytes(request))).thenRespondWithBinary(binaryResponse(bytes(response))));
    }

    private void mockOnce(String request, String response) {
        mockServer.upsert(new Expectation(binaryRequest(bytes(request)), Times.once(), TimeToLive.unlimited(), 0).thenRespondWithBinary(binaryResponse(bytes(response))));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void send(Socket socket, String text) throws IOException {
        socket.getOutputStream().write(bytes(text));
        socket.getOutputStream().flush();
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int read; (read = input.read()) != -1; ) {
            line.write(read);
            if (read == '\n') {
                break;
            }
        }
        return line.toString(StandardCharsets.UTF_8.name());
    }

    private static void exchange(Socket socket, byte[] message, byte[] expectedReply) throws IOException {
        socket.getOutputStream().write(message);
        socket.getOutputStream().flush();
        assertThat("reply to " + ByteBufUtil.hexDump(message), ByteBufUtil.hexDump(socket.getInputStream().readNBytes(expectedReply.length)), is(ByteBufUtil.hexDump(expectedReply)));
    }

    private static StartTlsUpstream postgresLikeUpstream() throws IOException {
        return new StartTlsUpstream()
            .answering(STARTUP, AUTHENTICATION_OK_AND_READY)
            .answering(QUERY, READY);
    }

    private void mock(byte[] request, byte[] response) {
        mockServer.upsert(new Expectation(binaryRequest(request)).thenRespondWithBinary(binaryResponse(response)));
    }

    private List<LogEntry> logged(String containing) {
        return logged.stream()
            .filter(entry -> String.valueOf(entry.getMessageFormat()).contains(containing))
            .collect(Collectors.toList());
    }

    private List<String> warnings(String containing) {
        return logged.stream()
            .filter(entry -> entry.getLogLevel() == Level.WARN && String.valueOf(entry.getMessageFormat()).contains(containing))
            .map(LogEntry::getMessageFormat)
            .collect(Collectors.toList());
    }

    @Test
    public void shouldAnswerAMatchedMessageWithoutForwardingItAndRelayTheRest() throws Exception {
        try (Upstream upstream = new Upstream(Upstream::numberEachLineOfTheConnection);
             Socket client = clientOf(matchingExpectations(), upstream)) {
            mock("mocked\n", "canned\n");

            send(client, "real\n");
            assertThat(readLine(client.getInputStream()), is("1:real\n"));
            send(client, "mocked\n");
            assertThat(readLine(client.getInputStream()), is("canned\n"));
            send(client, "real\n");
            assertThat("the upstream's session went on without the mocked message", readLine(client.getInputStream()), is("2:real\n"));

            assertThat(upstream.connections(), is(1));
            assertThat(upstream.receivedText(), is("real\nreal\n"));
        }
    }

    @Test
    public void shouldAnswerOnceThenForwardAndRemoveTheUsedUpExpectation() throws Exception {
        try (Upstream upstream = new Upstream(Upstream::numberEachLineOfTheConnection);
             Socket client = clientOf(matchingExpectations(), upstream)) {
            mockOnce("insert\n", "error\n");

            send(client, "insert\n");
            assertThat(readLine(client.getInputStream()), is("error\n"));
            assertThat("used up and removed", mockServer.retrieveActiveExpectations(null).length, is(0));

            send(client, "insert\n");
            assertThat("the retry reaches the upstream", readLine(client.getInputStream()), is("1:insert\n"));
        }
    }

    @Test
    public void shouldRemoveAUsedUpExpectationWithoutATarget() throws Exception {
        try (Socket client = clientOf(configuration().logLevel("INFO"), null)) {
            mockOnce("query\n", "answer\n");

            send(client, "query\n");
            assertThat(readLine(client.getInputStream()), is("answer\n"));

            assertThat(mockServer.retrieveActiveExpectations(null).length, is(0));
        }
    }

    @Test
    public void shouldSwallowAMessageWhoseResponseIsEmpty() throws Exception {
        try (Upstream upstream = new Upstream(Upstream::numberEachLineOfTheConnection);
             Socket client = clientOf(matchingExpectations(), upstream)) {
            mockServer.upsert(new Expectation(binaryRequest(bytes("drop\n"))).thenRespondWithBinary(binaryResponse(new byte[0])));

            send(client, "drop\n");
            // sent together, the two would be one message, which no expectation matches
            tryWaitForSuccess(() -> assertThat(logged("returning nothing"), hasSize(1)));
            send(client, "real\n");

            assertThat(readLine(client.getInputStream()), is("1:real\n"));
            assertThat(upstream.receivedText(), is("real\n"));
        }
    }

    @Test
    public void shouldRelayWhatTheUpstreamSendsUnpromptedAfterALocalReply() throws Exception {
        CountDownLatch notify = new CountDownLatch(1);
        try (Upstream upstream = new Upstream((socket, received) -> {
            OutputStream output = socket.getOutputStream();
            received.write(bytes(readLine(socket.getInputStream())));
            output.write(bytes("ready\n"));
            output.flush();
            assertThat(notify.await(20, TimeUnit.SECONDS), is(true));
            output.write(bytes("notification\n"));
            output.flush();
            readLine(socket.getInputStream());
        }); Socket client = clientOf(matchingExpectations(), upstream)) {
            mock("mocked\n", "canned\n");

            send(client, "listen\n");
            assertThat(readLine(client.getInputStream()), is("ready\n"));
            send(client, "mocked\n");
            assertThat(readLine(client.getInputStream()), is("canned\n"));
            notify.countDown();

            assertThat(readLine(client.getInputStream()), is("notification\n"));
        }
    }

    @Test
    public void shouldTellTheListenerOnlyOfForwardedMessagesAndWarnWhenALocalReplyOvertakes() throws Exception {
        List<String> reported = new CopyOnWriteArrayList<>();
        List<CompletableFuture<BinaryMessage>> responses = new CopyOnWriteArrayList<>();
        Configuration configuration = matchingExpectations().binaryProxyListener((binaryRequest, binaryResponse, serverAddress, clientAddress) -> {
            reported.add(new String(binaryRequest.getBytes(), StandardCharsets.UTF_8));
            responses.add(binaryResponse);
        });
        CountDownLatch answer = new CountDownLatch(1);
        try (Upstream upstream = new Upstream((socket, received) -> {
            String line = readLine(socket.getInputStream());
            synchronized (received) {
                received.write(bytes(line));
            }
            assertThat(answer.await(20, TimeUnit.SECONDS), is(true));
            socket.getOutputStream().write(bytes("answer\n"));
            socket.getOutputStream().flush();
            readLine(socket.getInputStream());
        }); Socket client = clientOf(configuration, upstream)) {
            mock("mocked\n", "canned\n");

            send(client, "slow\n");
            tryWaitForSuccess(() -> assertThat(upstream.receivedText(), is("slow\n")));
            send(client, "mocked\n");
            assertThat("the local reply overtakes the upstream's", readLine(client.getInputStream()), is("canned\n"));
            answer.countDown();
            assertThat(readLine(client.getInputStream()), is("answer\n"));

            tryWaitForSuccess(() -> assertThat(warnings("may reach the client out of order"), hasSize(1)));
            tryWaitForSuccess(() -> assertThat(reported, contains("slow\n")));
            assertThat(new String(responses.get(0).get(20, TimeUnit.SECONDS).getBytes(), StandardCharsets.UTF_8), is("answer\n"));
        }
    }

    @Test
    public void shouldForwardTwoMessagesReadTogetherWhenOnlyOneHasAnExpectation() throws Exception {
        try (Upstream upstream = new Upstream(Upstream::echo);
             Socket client = clientOf(matchingExpectations(), upstream)) {
            mock("second\n", "canned\n");

            send(client, "first\nsecond\n");

            assertThat(readLine(client.getInputStream()), is("first\n"));
            assertThat(readLine(client.getInputStream()), is("second\n"));
        }
    }

    @Test
    public void shouldOpenNoUpstreamConnectionWhileEveryMessageIsAnswered() throws Exception {
        try (Upstream upstream = new Upstream(Upstream::numberEachLineOfTheConnection);
             Socket client = clientOf(matchingExpectations(), upstream)) {
            mock("mocked\n", "canned\n");

            for (int message = 0; message < 3; message++) {
                send(client, "mocked\n");
                assertThat(readLine(client.getInputStream()), is("canned\n"));
            }

            assertThat(upstream.connections(), is(0));
        }
    }

    @Test
    public void shouldForwardAMessageWhoseExpectationIsNotABinaryResponseAndSaySo() throws Exception {
        try (Upstream upstream = new Upstream(Upstream::numberEachLineOfTheConnection);
             Socket client = clientOf(matchingExpectations(), upstream)) {
            mockServer.upsert(new Expectation(binaryRequest(bytes("http\n"))).thenRespond(response("not binary")));

            send(client, "http\n");

            assertThat(readLine(client.getInputStream()), is("1:http\n"));
            tryWaitForSuccess(() -> assertThat(warnings("has no binary response"), hasSize(1)));
        }
    }

    @Test
    public void shouldAnswerAMatchedMessageByDefault() throws Exception {
        try (Upstream upstream = new Upstream(Upstream::numberEachLineOfTheConnection);
             Socket client = clientOf(configuration().logLevel("INFO"), upstream)) {
            mock("mocked\n", "canned\n");

            send(client, "mocked\n");
            assertThat(readLine(client.getInputStream()), is("canned\n"));
            send(client, "one\n");
            assertThat(readLine(client.getInputStream()), is("1:one\n"));

            assertThat(upstream.receivedText(), is("one\n"));
        }
    }

    @Test
    public void shouldForwardAMatchingMessageWhenTheSettingIsOff() throws Exception {
        try (Upstream upstream = new Upstream(Upstream::numberEachLineOfTheConnection);
             Socket client = clientOf(configuration().logLevel("INFO").matchBinaryExpectationsWhenProxying(false), upstream)) {
            mock("mocked\n", "canned\n");

            send(client, "mocked\n");

            assertThat(readLine(client.getInputStream()), is("1:mocked\n"));
            assertThat(warnings("binary expectations are not matched"), is(empty()));
        }
    }

    @Test
    public void shouldForwardEachMessageAndWarnOnceWhenEachHasAnUpstreamConnectionOfItsOwn() throws Exception {
        try (Upstream upstream = new Upstream(Upstream::numberEachLineOfTheConnection);
             Socket client = clientOf(matchingExpectations().forwardBinaryRequestsUseSingleConnection(false), upstream)) {
            mock("mocked\n", "canned\n");

            for (int message = 0; message < 2; message++) {
                send(client, "mocked\n");
                assertThat("forwarded as in 8.0.0", readLine(client.getInputStream()), is("1:mocked\n"));
            }

            tryWaitForSuccess(() -> assertThat(warnings("binary expectations are not matched"), hasSize(1)));
        }
    }

    @Test
    public void shouldMatchOneExpectationBothBeforeAndAfterTheUpstreamIsUpgradedToTls() throws Exception {
        try (StartTlsUpstream upstream = postgresLikeUpstream();
             Socket client = clientThrough(matchingExpectations(), upstream)) {
            mock(MOCKED_QUERY, CANNED);

            exchange(client, MOCKED_QUERY, CANNED);
            exchange(client, SSL_REQUEST, new byte[]{'S'});
            SSLSocket tls = StartTlsUpstream.startTlsAsClient(client, "127.0.0.1", "TLSv1.3", false);
            exchange(tls, MOCKED_QUERY, CANNED);
            exchange(tls, QUERY, READY);

            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 10_000);
            assertThat(upstream.connections().size(), is(1));
            assertThat("the answered message reached the upstream neither in the clear nor over TLS", connection.messages(), contains(
                "clear " + ByteBufUtil.hexDump(SSL_REQUEST),
                "TLS " + ByteBufUtil.hexDump(QUERY)
            ));
            assertThat(ByteBufUtil.hexDump(connection.receivedInTheClear()), is(ByteBufUtil.hexDump(SSL_REQUEST)));
            tryWaitForSuccess(() -> assertThat(logged("returning binary mock response"), hasSize(2)));
        }
    }

    /**
     * Answering the {@code SSLRequest} locally means the upstream never agreed to TLS, so MockServer's handshake with
     * it fails: both connections close and nothing the client sent over TLS reaches the upstream. The consumer
     * documentation says not to answer it.
     */
    @Test
    public void shouldCloseBothConnectionsWhenTheSslRequestIsAnsweredLocally() throws Exception {
        try (StartTlsUpstream upstream = postgresLikeUpstream();
             Socket client = clientThrough(matchingExpectations().socketConnectionTimeoutInMillis(3_000L), upstream)) {
            mock(SSL_REQUEST, new byte[]{'S'});
            exchange(client, SSL_REQUEST, new byte[]{'S'});

            String outcome;
            try {
                SSLSocket tls = StartTlsUpstream.startTlsAsClient(client, "127.0.0.1", "TLSv1.3", false);
                tls.getOutputStream().write(STARTUP);
                tls.getOutputStream().flush();
                int read = tls.getInputStream().read();
                outcome = read == -1 ? "closed" : "read " + read;
            } catch (IOException closed) {
                outcome = "closed";
            }

            assertThat(outcome, is("closed"));
            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 10_000);
            assertThat("the upstream connection is ended", connection.awaitEnded(20_000), is(true));
            assertThat("nothing decrypted reached it", connection.receivedOverTls().length, is(0));
            byte[] inTheClear = connection.receivedInTheClear();
            assertThat("it saw no SSLRequest, only MockServer's handshake", inTheClear.length > 0 && inTheClear[0] == 0x16, is(true));
            assertThat(ByteBufUtil.hexDump(inTheClear).contains(ByteBufUtil.hexDump(STARTUP)), is(false));
            tryWaitForSuccess(() -> assertThat(warnings("unable to start TLS with upstream"), hasSize(1)));
        }
    }

    /**
     * While MockServer's handshake with the upstream runs, a message that matches an expectation is answered at once;
     * a forwarded one is held, and with it the client's connection, until the handshake has completed.
     */
    @Test
    public void shouldAnswerAMatchedMessageWhileTheUpstreamHandshakeRunsAndHoldOnlyAForwardedOne() throws Exception {
        CountDownLatch handshake = new CountDownLatch(1);
        try (StartTlsUpstream upstream = postgresLikeUpstream().holdingTlsUntil(handshake);
             Socket client = clientThrough(matchingExpectations(), upstream)) {
            mock(MOCKED_QUERY, CANNED);
            exchange(client, SSL_REQUEST, new byte[]{'S'});
            SSLSocket tls = StartTlsUpstream.startTlsAsClient(client, "127.0.0.1", "TLSv1.3", false);
            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 10_000);

            exchange(tls, MOCKED_QUERY, CANNED);
            assertThat("answered while the upstream's handshake was held", connection.upgraded(), is(false));

            // the log is written off the event loop: once the reply's entry is in, so is its message's
            tryWaitForSuccess(() -> assertThat(logged("returning binary mock response"), hasSize(1)));
            int received = logged("received binary request").size();
            tls.getOutputStream().write(QUERY);
            tls.getOutputStream().flush();
            tryWaitForSuccess(() -> assertThat(logged("received binary request"), hasSize(received + 1)));
            tls.getOutputStream().write(MOCKED_QUERY);
            tls.getOutputStream().flush();
            TimeUnit.MILLISECONDS.sleep(500);
            assertThat("not read behind the held message", logged("returning binary mock response"), hasSize(1));
            assertThat(connection.upgraded(), is(false));

            handshake.countDown();

            String replies = ByteBufUtil.hexDump(tls.getInputStream().readNBytes(CANNED.length + READY.length));
            assertThat("the two in either order: nothing orders a local reply against the upstream's", replies, is(oneOf(
                ByteBufUtil.hexDump(concat(CANNED, READY)),
                ByteBufUtil.hexDump(concat(READY, CANNED))
            )));
            assertThat(connection.messages(), contains(
                "clear " + ByteBufUtil.hexDump(SSL_REQUEST),
                "TLS " + ByteBufUtil.hexDump(QUERY)
            ));
        }
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }

    /**
     * An upstream on 127.0.0.1 that serves each connection as the test says, and keeps what each one sent.
     */
    private static final class Upstream implements AutoCloseable {

        interface Behaviour {
            void serve(Socket socket, ByteArrayOutputStream received) throws Exception;
        }

        private final ServerSocket serverSocket;
        private final Behaviour behaviour;
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private final List<ByteArrayOutputStream> receivedByConnection = new CopyOnWriteArrayList<>();

        Upstream(Behaviour behaviour) throws IOException {
            this.behaviour = behaviour;
            serverSocket = new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
            Thread accept = new Thread(this::acceptConnections, "mixing-upstream-accept");
            accept.setDaemon(true);
            accept.start();
        }

        private void acceptConnections() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    ByteArrayOutputStream received = new ByteArrayOutputStream();
                    receivedByConnection.add(received);
                    sockets.add(socket);
                    Thread serve = new Thread(() -> {
                        try (Socket served = socket) {
                            behaviour.serve(served, received);
                        } catch (Exception closed) {
                            // MockServer, or close(), ended the connection
                        }
                    }, "mixing-upstream-serve");
                    serve.setDaemon(true);
                    serve.start();
                } catch (IOException closed) {
                    // close() ends the accept
                }
            }
        }

        /** Answers each line with its position among the lines of its connection, and keeps each line. */
        static void numberEachLineOfTheConnection(Socket socket, ByteArrayOutputStream received) throws IOException {
            int lines = 0;
            for (String line; !(line = readLine(socket.getInputStream())).isEmpty(); ) {
                synchronized (received) {
                    received.write(bytes(line));
                }
                socket.getOutputStream().write(bytes(++lines + ":" + line));
                socket.getOutputStream().flush();
            }
        }

        static void echo(Socket socket, ByteArrayOutputStream received) throws IOException {
            byte[] buffer = new byte[16 * 1024];
            for (int read; (read = socket.getInputStream().read(buffer)) != -1; ) {
                socket.getOutputStream().write(buffer, 0, read);
            }
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        int connections() {
            return sockets.size();
        }

        String receivedText() {
            StringBuilder all = new StringBuilder();
            for (ByteArrayOutputStream received : receivedByConnection) {
                synchronized (received) {
                    all.append(new String(received.toByteArray(), StandardCharsets.UTF_8));
                }
            }
            return all.toString();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            for (Socket socket : sockets) {
                socket.close();
            }
        }
    }
}
