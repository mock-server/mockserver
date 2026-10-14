package org.mockserver.netty.integration.proxy.direct;

import io.netty.buffer.ByteBufUtil;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.mock.Expectation;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.mockserver.socket.tls.PEMToFile;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.netty.integration.proxy.direct.StartTlsUpstream.SSL_REQUEST;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Proxying a protocol that turns TLS on part way through, as PostgreSQL does, by default: the client's
 * {@code SSLRequest} and the upstream's answer are relayed in the clear on the one upstream connection, MockServer
 * answers the client's handshake and makes its own with the upstream on that same connection, and the session
 * continues over TLS on both. The upstream is {@link StartTlsUpstream}; the client is the JDK's TLS.
 */
public class BinaryInBandTlsUpgradeProxyIntegrationTest {

    private static final byte[] STARTUP = {0, 0, 0, 23, 0, 3, 0, 0, 'u', 's', 'e', 'r', 0, 'p', 'o', 's', 't', 'g', 'r', 'e', 's', 0, 0};
    private static final byte[] AUTHENTICATION_OK_AND_READY = {'R', 0, 0, 0, 8, 0, 0, 0, 0, 'Z', 0, 0, 0, 5, 'I'};
    private static final byte[] QUERY = {'Q', 0, 0, 0, 13, 'S', 'E', 'L', 'E', 'C', 'T', ' ', '1', 0};
    private static final byte[] SYNC = {'S', 0, 0, 0, 4};
    private static final byte[] READY = {'Z', 0, 0, 0, 5, 'I'};
    private static final byte[] NOTIFICATION = {'A', 0, 0, 0, 10, 0, 0, 0, 1, 'c', 0, 0};

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
        stopQuietly(mockServer);
    }

    private Socket connectThrough(Configuration configuration, StartTlsUpstream upstream) throws IOException {
        mockServer = startClientAndServer(configuration, "127.0.0.1", upstream.port(), PortFactory.findFreePort());
        Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort());
        sockets.add(socket);
        socket.setSoTimeout(20_000);
        socket.setTcpNoDelay(true);
        return socket;
    }

    private SSLSocket startTls(Socket socket, String protocol) throws Exception {
        SSLSocket tls = StartTlsUpstream.startTlsAsClient(socket, "127.0.0.1", protocol, false);
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
            .answering(STARTUP, AUTHENTICATION_OK_AND_READY)
            .answering(QUERY, READY)
            .answering(SYNC, READY);
    }

    private void shouldRunASessionUpgradedOnOneUpstreamConnection(String protocol) throws Exception {
        try (StartTlsUpstream upstream = session().withTlsProtocols(protocol)) {
            Socket client = connectThrough(configuration(), upstream);

            exchange(client, SSL_REQUEST, new byte[]{'S'});
            SSLSocket tls = startTls(client, protocol);
            exchange(tls, STARTUP, AUTHENTICATION_OK_AND_READY);
            exchange(tls, QUERY, READY);
            exchange(tls, SYNC, READY);

            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 10_000);
            assertThat("one upstream connection for the session", upstream.connections().size(), is(1));
            assertThat(connection.tlsProtocol(), is(protocol));
            assertThat("only the SSLRequest in the clear", ByteBufUtil.hexDump(connection.receivedInTheClear()), is(ByteBufUtil.hexDump(SSL_REQUEST)));
            assertThat(connection.messages(), contains(
                "clear " + ByteBufUtil.hexDump(SSL_REQUEST),
                "TLS " + ByteBufUtil.hexDump(STARTUP),
                "TLS " + ByteBufUtil.hexDump(QUERY),
                "TLS " + ByteBufUtil.hexDump(SYNC)
            ));
        }
    }

    @Test
    public void shouldRunASessionUpgradedToTls13OnOneUpstreamConnection() throws Exception {
        shouldRunASessionUpgradedOnOneUpstreamConnection("TLSv1.3");
    }

    @Test
    public void shouldRunASessionUpgradedToTls12OnOneUpstreamConnection() throws Exception {
        shouldRunASessionUpgradedOnOneUpstreamConnection("TLSv1.2");
    }

    @Test
    public void shouldStayInTheClearOnTheSameConnectionWhenTheUpstreamRefusesTls() throws Exception {
        try (StartTlsUpstream upstream = session().answeringSslRequestWith('N')) {
            Socket client = connectThrough(configuration(), upstream);

            exchange(client, SSL_REQUEST, new byte[]{'N'});
            exchange(client, STARTUP, AUTHENTICATION_OK_AND_READY);
            exchange(client, SYNC, READY);

            assertThat(upstream.connections().size(), is(1));
            assertThat(upstream.awaitConnection(1, 10_000).upgraded(), is(false));
        }
    }

    @Test
    public void shouldCloseBothConnectionsWhenTheClientStartsTlsAfterTheUpstreamRefusedIt() throws Exception {
        try (StartTlsUpstream upstream = session().answeringSslRequestWith('N')) {
            // the upstream never answers MockServer's handshake, which then times out
            Socket client = connectThrough(configuration().socketConnectionTimeoutInMillis(3_000L), upstream);
            exchange(client, SSL_REQUEST, new byte[]{'N'});

            String outcome;
            try {
                SSLSocket tls = startTls(client, "TLSv1.3");
                tls.getOutputStream().write(STARTUP);
                tls.getOutputStream().flush();
                outcome = readAfterClose(tls);
            } catch (IOException failed) {
                outcome = "closed";
            }

            assertThat(outcome, is("closed"));
            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 10_000);
            assertThat("the upstream connection is ended", connection.awaitEnded(20_000), is(true));
            assertThat("it saw MockServer's handshake in the clear and nothing the client sent over TLS", connection.messages().get(1), containsString("clear unanswered 16"));
            assertThat(Arrays.equals(Arrays.copyOf(connection.receivedInTheClear(), SSL_REQUEST.length), SSL_REQUEST), is(true));
            assertThat(ByteBufUtil.hexDump(connection.receivedInTheClear()).contains(ByteBufUtil.hexDump(STARTUP)), is(false));
        }
    }

    @Test
    public void shouldRelayWhatTheUpstreamSendsUnpromptedOverTls() throws Exception {
        try (StartTlsUpstream upstream = session()) {
            Socket client = connectThrough(configuration(), upstream);
            exchange(client, SSL_REQUEST, new byte[]{'S'});
            SSLSocket tls = startTls(client, "TLSv1.3");
            exchange(tls, STARTUP, AUTHENTICATION_OK_AND_READY);

            upstream.awaitConnection(1, 10_000).push(NOTIFICATION);

            assertThat(ByteBufUtil.hexDump(tls.getInputStream().readNBytes(NOTIFICATION.length)), is(ByteBufUtil.hexDump(NOTIFICATION)));
        }
    }

    @Test
    public void shouldRelayAnAnswerLargerThan64KiBWholeAndInOrder() throws Exception {
        byte[] large = new byte[200_000];
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) (i % 251);
        }
        try (StartTlsUpstream upstream = new StartTlsUpstream().answering(STARTUP, AUTHENTICATION_OK_AND_READY).answering(QUERY, large)) {
            Socket client = connectThrough(configuration(), upstream);
            exchange(client, SSL_REQUEST, new byte[]{'S'});
            SSLSocket tls = startTls(client, "TLSv1.3");
            exchange(tls, STARTUP, AUTHENTICATION_OK_AND_READY);

            tls.getOutputStream().write(QUERY);
            tls.getOutputStream().flush();

            assertThat(Arrays.equals(tls.getInputStream().readNBytes(large.length), large), is(true));
        }
    }

    @Test
    public void shouldCloseTheClientWhenTheUpgradedUpstreamCloses() throws Exception {
        try (StartTlsUpstream upstream = session()) {
            Socket client = connectThrough(configuration(), upstream);
            exchange(client, SSL_REQUEST, new byte[]{'S'});
            SSLSocket tls = startTls(client, "TLSv1.3");
            exchange(tls, STARTUP, AUTHENTICATION_OK_AND_READY);

            upstream.awaitConnection(1, 10_000).close();

            assertThat(readAfterClose(tls), is("closed"));
        }
    }

    @Test
    public void shouldPresentMockServersClientCertificateToAnUpstreamThatRequiresOne() throws Exception {
        try (StartTlsUpstream upstream = session().requiringClientCertificate()) {
            Socket client = connectThrough(configuration(), upstream);
            exchange(client, SSL_REQUEST, new byte[]{'S'});
            exchange(startTls(client, "TLSv1.3"), STARTUP, AUTHENTICATION_OK_AND_READY);

            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 10_000);
            assertThat(connection.clientCertificates(), is(notNullValue()));
            assertThat(connection.clientCertificates()[0].getIssuerX500Principal().getName(), containsString("MockServer"));
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
            Socket client = connectThrough(validating, upstream);
            exchange(client, SSL_REQUEST, new byte[]{'S'});

            String outcome;
            try {
                SSLSocket tls = startTls(client, "TLSv1.3");
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
        }
    }

    /**
     * The first message after the upgrade is answered by MockServer, so it never reaches the relay's own check for a
     * client over TLS: the upgrade must already have started from the handshake event, or the next message would go
     * upstream in the clear.
     */
    @Test
    public void shouldUpgradeTheUpstreamWhenTheFirstMessageOverTlsIsAnsweredLocally() throws Exception {
        byte[] answeredLocally = {'R', 0, 0, 0, 8, 0, 0, 0, 3};
        try (StartTlsUpstream upstream = session()) {
            Socket client = connectThrough(configuration().matchBinaryExpectationsWhenProxying(true), upstream);
            mockServer.upsert(new Expectation(binaryRequest(STARTUP)).thenRespondWithBinary(binaryResponse(answeredLocally)));

            exchange(client, SSL_REQUEST, new byte[]{'S'});
            SSLSocket tls = startTls(client, "TLSv1.3");
            exchange(tls, STARTUP, answeredLocally);
            exchange(tls, QUERY, READY);

            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 10_000);
            assertThat(upstream.connections().size(), is(1));
            assertThat("only the SSLRequest in the clear", ByteBufUtil.hexDump(connection.receivedInTheClear()), is(ByteBufUtil.hexDump(SSL_REQUEST)));
            assertThat("the relayed message over TLS, the answered one not at all", connection.messages(), contains(
                "clear " + ByteBufUtil.hexDump(SSL_REQUEST),
                "TLS " + ByteBufUtil.hexDump(QUERY)
            ));
        }
    }

    @Test
    public void shouldRelayAClientThatStartsWithTlsOnOneUpstreamConnectionThatStartsWithTls() throws Exception {
        try (StartTlsUpstream upstream = session().startingWithTls()) {
            Socket client = connectThrough(configuration(), upstream);

            SSLSocket tls = startTls(client, "TLSv1.3");
            exchange(tls, STARTUP, AUTHENTICATION_OK_AND_READY);
            exchange(tls, SYNC, READY);

            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 10_000);
            assertThat("one upstream connection, not one a message", upstream.connections().size(), is(1));
            assertThat(connection.upgraded(), is(true));
            assertThat("nothing in the clear", connection.receivedInTheClear().length, is(0));
            assertThat(connection.messages(), contains("TLS " + ByteBufUtil.hexDump(STARTUP), "TLS " + ByteBufUtil.hexDump(SYNC)));
        }
    }
}
