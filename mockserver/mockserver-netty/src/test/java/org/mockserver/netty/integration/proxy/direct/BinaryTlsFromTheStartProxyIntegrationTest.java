package org.mockserver.netty.integration.proxy.direct;

import io.netty.buffer.ByteBufUtil;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.mockserver.socket.tls.PEMToFile;
import org.slf4j.event.Level;

import javax.net.ssl.SSLSocket;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Retries.tryWaitForSuccess;

/**
 * Proxying a binary client whose connection is TLS from its first byte: MockServer answers the client's handshake
 * and opens one upstream connection for the client connection, TLS from its first byte too, and relays what was
 * decrypted both ways. The upstream is {@link StartTlsUpstream} told every connection starts with TLS; the client is
 * the JDK's TLS.
 */
public class BinaryTlsFromTheStartProxyIntegrationTest {

    private static final byte[] STARTUP = {0, 0, 0, 23, 0, 3, 0, 0, 'u', 's', 'e', 'r', 0, 'p', 'o', 's', 't', 'g', 'r', 'e', 's', 0, 0};
    private static final byte[] AUTHENTICATION_OK_AND_READY = {'R', 0, 0, 0, 8, 0, 0, 0, 0, 'Z', 0, 0, 0, 5, 'I'};
    private static final byte[] QUERY = {'Q', 0, 0, 0, 13, 'S', 'E', 'L', 'E', 'C', 'T', ' ', '1', 0};
    private static final byte[] SYNC = {'S', 0, 0, 0, 4};
    private static final byte[] READY = {'Z', 0, 0, 0, 5, 'I'};
    private static final byte[] TERMINATE = {'X', 0, 0, 0, 4};

    private final List<LogEntry> logged = new CopyOnWriteArrayList<>();
    private ClientAndServer mockServer;
    private final List<Socket> sockets = new ArrayList<>();

    @After
    public void closeSocketsAndStopMockServer() {
        for (Socket socket : sockets) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing what a test may already have closed
            }
        }
        MockServerLogger.setGlobalLogEventListener(null);
        stopQuietly(mockServer);
    }

    private void startMockServer(Configuration configuration, StartTlsUpstream upstream) {
        startMockServer(configuration, upstream.port());
    }

    private void startMockServer(Configuration configuration, int upstreamPort) {
        // a clone: the event log clears the entry it is handed once it has copied it
        MockServerLogger.setGlobalLogEventListener(logEntry -> logged.add(logEntry.clone()));
        mockServer = startClientAndServer(configuration.logLevel("INFO"), "127.0.0.1", upstreamPort, PortFactory.findFreePort());
    }

    /** A client connection to MockServer, TLS from its first byte, sending the given name, if any, as SNI. */
    private SSLSocket client(String serverName, String protocol) throws Exception {
        Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort());
        sockets.add(socket);
        socket.setSoTimeout(20_000);
        socket.setTcpNoDelay(true);
        SSLSocket tls = StartTlsUpstream.startTlsAsClient(socket, "127.0.0.1", protocol, false, serverName);
        sockets.add(tls);
        return tls;
    }

    private static void exchange(Socket socket, byte[] message, byte[] expectedReply) throws IOException {
        socket.getOutputStream().write(message);
        socket.getOutputStream().flush();
        assertThat("reply to " + ByteBufUtil.hexDump(message), ByteBufUtil.hexDump(socket.getInputStream().readNBytes(expectedReply.length)), is(ByteBufUtil.hexDump(expectedReply)));
    }

    /** What a refused or closed connection gives a reader: nothing, an end of stream or an error. */
    private static String readAfterClose(Socket socket) {
        try {
            int read = socket.getInputStream().read();
            return read == -1 ? "closed" : "read " + read;
        } catch (IOException closed) {
            return "closed";
        }
    }

    private static StartTlsUpstream session() throws IOException {
        return new StartTlsUpstream()
            .startingWithTls()
            .answering(STARTUP, AUTHENTICATION_OK_AND_READY)
            .answering(QUERY, READY)
            .answering(SYNC, READY);
    }

    private void shouldRunASessionOnOneUpstreamConnectionWithTlsFromItsFirstByte(String protocol) throws Exception {
        try (StartTlsUpstream upstream = session().withTlsProtocols(protocol)) {
            startMockServer(configuration(), upstream);
            SSLSocket tls = client(null, protocol);

            exchange(tls, STARTUP, AUTHENTICATION_OK_AND_READY);
            exchange(tls, QUERY, READY);
            exchange(tls, SYNC, READY);

            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 10_000);
            assertThat("one upstream connection for the session", upstream.connections().size(), is(1));
            assertThat(connection.tlsProtocol(), is(protocol));
            assertThat("nothing in the clear", connection.receivedInTheClear().length, is(0));
            assertThat(connection.messages(), contains(
                "TLS " + ByteBufUtil.hexDump(STARTUP),
                "TLS " + ByteBufUtil.hexDump(QUERY),
                "TLS " + ByteBufUtil.hexDump(SYNC)
            ));
        }
    }

    @Test
    public void shouldRunATls13SessionOnOneUpstreamConnectionWithTlsFromItsFirstByte() throws Exception {
        shouldRunASessionOnOneUpstreamConnectionWithTlsFromItsFirstByte("TLSv1.3");
    }

    @Test
    public void shouldRunATls12SessionOnOneUpstreamConnectionWithTlsFromItsFirstByte() throws Exception {
        shouldRunASessionOnOneUpstreamConnectionWithTlsFromItsFirstByte("TLSv1.2");
    }

    @Test
    public void shouldGiveEachClientConnectionOneUpstreamConnectionOfItsOwn() throws Exception {
        try (StartTlsUpstream upstream = session()) {
            startMockServer(configuration(), upstream);
            SSLSocket first = client(null, "TLSv1.3");
            SSLSocket second = client(null, "TLSv1.3");

            exchange(first, STARTUP, AUTHENTICATION_OK_AND_READY);
            exchange(second, STARTUP, AUTHENTICATION_OK_AND_READY);
            exchange(first, QUERY, READY);
            exchange(second, SYNC, READY);
            exchange(first, SYNC, READY);

            upstream.awaitConnection(2, 10_000);
            assertThat(upstream.connections().size(), is(2));
            List<List<String>> sessions = upstream.connections().stream().map(StartTlsUpstream.Connection::messages).collect(Collectors.toList());
            assertThat("each client's messages on its own upstream connection", sessions.contains(Arrays.asList(
                "TLS " + ByteBufUtil.hexDump(STARTUP), "TLS " + ByteBufUtil.hexDump(QUERY), "TLS " + ByteBufUtil.hexDump(SYNC))), is(true));
            assertThat(sessions.contains(Arrays.asList(
                "TLS " + ByteBufUtil.hexDump(STARTUP), "TLS " + ByteBufUtil.hexDump(SYNC))), is(true));
        }
    }

    @Test
    public void shouldNameAnUpstreamGivenAsAnAddressAsTheClientNamedMockServer() throws Exception {
        try (StartTlsUpstream upstream = session()) {
            startMockServer(configuration(), upstream);

            exchange(client("db.example.test", "TLSv1.3"), STARTUP, AUTHENTICATION_OK_AND_READY);

            assertThat(upstream.awaitConnection(1, 10_000).serverNames(), contains("db.example.test"));
        }
    }

    @Test
    public void shouldCloseBothConnectionsAndSendNothingDecryptedToAnUpstreamWhoseCertificateIsNotTrusted() throws Exception {
        // a certificate from an authority neither the JVM nor MockServer trusts
        KeyStore untrusted = KeyStore.getInstance(KeyStore.getDefaultType());
        untrusted.load(null, null);
        untrusted.setKeyEntry("upstream", PEMToFile.privateKeyFromPEMFile("org/mockserver/netty/integration/tls/separateca/leaf-key-pkcs8.pem"), "changeit".toCharArray(),
            new Certificate[]{PEMToFile.x509FromPEMFile("org/mockserver/netty/integration/tls/separateca/leaf-cert.pem")});
        Configuration validating = configuration().forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.JVM);
        try (StartTlsUpstream upstream = session().withServerKeyStore(untrusted, "changeit".toCharArray())) {
            startMockServer(validating, upstream);

            String outcome;
            try {
                SSLSocket tls = client(null, "TLSv1.3");
                tls.getOutputStream().write(STARTUP);
                tls.getOutputStream().flush();
                outcome = readAfterClose(tls);
            } catch (IOException failed) {
                outcome = "closed";
            }

            assertThat(outcome, is("closed"));
            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 10_000);
            assertThat(connection.awaitEnded(20_000), is(true));
            assertThat("nothing decrypted reached it", connection.receivedOverTls().length, is(0));
            assertThat("nor anything in the clear", connection.receivedInTheClear().length, is(0));
            assertThat(upstream.connections().size(), is(1));
            tryWaitForSuccess(() -> assertThat(logged("unable to start TLS with upstream").size(), is(1)));
        }
    }

    @Test
    public void shouldReportAnUpstreamThatClosesTheConnectionDuringTheHandshake() throws Exception {
        try (ServerSocket upstream = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            Thread acceptor = new Thread(() -> {
                try (Socket accepted = upstream.accept()) {
                    accepted.setSoTimeout(20_000);
                    // the whole ClientHello: a socket closed with bytes unread is reset, not closed
                    DataInputStream input = new DataInputStream(accepted.getInputStream());
                    byte[] header = new byte[5];
                    input.readFully(header);
                    input.readFully(new byte[((header[3] & 0xff) << 8) | (header[4] & 0xff)]);
                } catch (IOException closedOrFailed) {
                    // the test fails on what MockServer logged
                }
            }, "closing-upstream");
            acceptor.setDaemon(true);
            acceptor.start();
            startMockServer(configuration(), upstream.getLocalPort());
            SSLSocket tls = client(null, "TLSv1.3");

            tls.getOutputStream().write(STARTUP);
            tls.getOutputStream().flush();

            assertThat(readAfterClose(tls), is("closed"));
            tryWaitForSuccess(() -> assertThat(logged("unable to start TLS with upstream").size(), is(1)));
            assertThat(logged("unable to start TLS with upstream").get(0).getMessage(), containsString("upstream closed the connection during the TLS handshake"));
        }
    }

    @Test
    public void shouldEndAnUpstreamHandshakeThatStallsAtTheConfiguredConnectionTimeout() throws Exception {
        List<Socket> accepted = new CopyOnWriteArrayList<>();
        try (ServerSocket upstream = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            Thread acceptor = new Thread(() -> {
                try {
                    // held open and never answered, so only the handshake timeout ends the handshake
                    accepted.add(upstream.accept());
                } catch (IOException closed) {
                    // the end of the test
                }
            }, "silent-upstream");
            acceptor.setDaemon(true);
            acceptor.start();
            startMockServer(configuration().socketConnectionTimeoutInMillis(1000L), upstream.getLocalPort());
            SSLSocket tls = client(null, "TLSv1.3");

            long start = System.nanoTime();
            tls.getOutputStream().write(STARTUP);
            tls.getOutputStream().flush();
            String outcome = readAfterClose(tls);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(outcome, is("closed"));
            assertThat("ended no earlier than the configured timeout", elapsedMillis, greaterThanOrEqualTo(800L));
            assertThat("ended by the configured timeout, not Netty's 10 second default (" + elapsedMillis + "ms)", elapsedMillis, lessThan(6000L));
            tryWaitForSuccess(() -> assertThat(logged("unable to start TLS with upstream").size(), is(1)));
            assertThat(logged("unable to start TLS with upstream").get(0).getMessage(), containsString("handshake timed out after 1000ms"));
        } finally {
            for (Socket socket : accepted) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldRelayLargeMessagesWholeAndInOrderBothWays() throws Exception {
        byte[] largeAnswer = new byte[200_000];
        for (int i = 0; i < largeAnswer.length; i++) {
            largeAnswer[i] = (byte) (i % 251);
        }
        byte[] largeMessage = new byte[300_000];
        largeMessage[0] = 'C';
        for (int i = 1; i < largeMessage.length; i++) {
            largeMessage[i] = (byte) (i % 241);
        }
        try (StartTlsUpstream upstream = session().answering(largeMessage, READY)) {
            startMockServer(configuration(), upstream);
            SSLSocket tls = client(null, "TLSv1.3");
            exchange(tls, STARTUP, AUTHENTICATION_OK_AND_READY);

            exchange(tls, largeMessage, READY);
            upstream.awaitConnection(1, 10_000).push(largeAnswer);

            assertThat(Arrays.equals(tls.getInputStream().readNBytes(largeAnswer.length), largeAnswer), is(true));
            assertThat(upstream.connections().size(), is(1));
        }
    }

    @Test
    public void shouldCloseTheClientWhenTheUpstreamCloses() throws Exception {
        try (StartTlsUpstream upstream = session()) {
            startMockServer(configuration(), upstream);
            SSLSocket tls = client(null, "TLSv1.3");
            exchange(tls, STARTUP, AUTHENTICATION_OK_AND_READY);

            upstream.awaitConnection(1, 10_000).close();

            assertThat(readAfterClose(tls), is("closed"));
        }
    }

    @Test
    public void shouldDeliverTheLastMessageThenEndTheUpstreamWhenTheClientCloses() throws Exception {
        try (StartTlsUpstream upstream = session()) {
            startMockServer(configuration(), upstream);
            SSLSocket tls = client(null, "TLSv1.3");
            exchange(tls, STARTUP, AUTHENTICATION_OK_AND_READY);

            tls.getOutputStream().write(TERMINATE);
            tls.getOutputStream().flush();
            tls.close();

            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 10_000);
            assertThat("the upstream connection is ended", connection.awaitEnded(20_000), is(true));
            assertThat(ByteBufUtil.hexDump(connection.receivedOverTls()), is(ByteBufUtil.hexDump(STARTUP) + ByteBufUtil.hexDump(TERMINATE)));
        }
    }

    @Test
    public void shouldAnswerAMatchedMessageAndRelayTheRestOverTlsOnOneUpstreamConnection() throws Exception {
        byte[] answeredLocally = {'R', 0, 0, 0, 8, 0, 0, 0, 3};
        try (StartTlsUpstream upstream = session()) {
            startMockServer(configuration().matchBinaryExpectationsWhenProxying(true), upstream);
            mockServer.upsert(new Expectation(binaryRequest(QUERY)).thenRespondWithBinary(binaryResponse(answeredLocally)));
            SSLSocket tls = client(null, "TLSv1.3");

            exchange(tls, STARTUP, AUTHENTICATION_OK_AND_READY);
            exchange(tls, QUERY, answeredLocally);
            exchange(tls, SYNC, READY);

            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 10_000);
            assertThat(upstream.connections().size(), is(1));
            assertThat("the relayed messages over TLS, the answered one not at all", connection.messages(), contains(
                "TLS " + ByteBufUtil.hexDump(STARTUP),
                "TLS " + ByteBufUtil.hexDump(SYNC)
            ));
        }
    }

    private List<LogEntry> logged(String containing) {
        return logged.stream()
            .filter(entry -> entry.getLogLevel() == Level.WARN || entry.getLogLevel() == Level.DEBUG)
            .filter(entry -> String.valueOf(entry.getMessageFormat()).contains(containing))
            .collect(Collectors.toList());
    }
}
