package org.mockserver.netty.integration.proxy.direct;

import io.netty.buffer.ByteBufUtil;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.tls.KeyStoreFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A protocol that turns TLS on part way through a connection, as PostgreSQL does: the client opens in the clear
 * with an {@code SSLRequest}, is answered {@code S}, and starts a TLS handshake on the same connection. MockServer
 * takes the connection for binary from its first message; when the handshake begins it answers as a TLS server,
 * and what it decrypts from then on is binary too. The messages are PostgreSQL's, the client is the JDK's TLS.
 */
public class BinaryTlsUpgradeIntegrationTest {

    private static final byte[] SSL_REQUEST = {0, 0, 0, 8, 4, (byte) 0xd2, 0x16, 0x2f};
    private static final byte[] STARTUP = {0, 0, 0, 23, 0, 3, 0, 0, 'u', 's', 'e', 'r', 0, 'p', 'o', 's', 't', 'g', 'r', 'e', 's', 0, 0};
    private static final byte[] AUTHENTICATION_OK_AND_READY = {'R', 0, 0, 0, 8, 0, 0, 0, 0, 'Z', 0, 0, 0, 5, 'I'};
    private static final byte[] QUERY = {'Q', 0, 0, 0, 13, 'S', 'E', 'L', 'E', 'C', 'T', ' ', '1', 0};
    private static final byte[] QUERY_RESULT = {'C', 0, 0, 0, 13, 'S', 'E', 'L', 'E', 'C', 'T', ' ', '1', 0, 'Z', 0, 0, 0, 5, 'I'};
    // five bytes: shorter than any first message MockServer once waited for
    private static final byte[] SYNC = {'S', 0, 0, 0, 4};
    private static final byte[] READY = {'Z', 0, 0, 0, 5, 'I'};
    // not PostgreSQL: a message that is the start of an HTTP request line, which must not be waited on
    private static final byte[] LIKE_HTTP = "GET".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] LIKE_HTTP_REPLY = "not http".getBytes(StandardCharsets.US_ASCII);

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

    private void mockServerWith(Configuration configuration) {
        mockServer = startClientAndServer(configuration, PortFactory.findFreePort());
        mock(STARTUP, AUTHENTICATION_OK_AND_READY);
        mock(QUERY, QUERY_RESULT);
        mock(SYNC, READY);
        mock(LIKE_HTTP, LIKE_HTTP_REPLY);
    }

    private void mock(byte[] request, byte[] response) {
        mockServer.upsert(new Expectation(binaryRequest(request)).thenRespondWithBinary(binaryResponse(response)));
    }

    private Socket connect() throws IOException {
        return opened(new Socket("127.0.0.1", mockServer.getLocalPort()));
    }

    private <T extends Socket> T opened(T socket) throws IOException {
        sockets.add(socket);
        socket.setSoTimeout(10_000);
        socket.setTcpNoDelay(true);
        return socket;
    }

    private static void send(Socket socket, byte[] message) throws IOException {
        socket.getOutputStream().write(message);
        socket.getOutputStream().flush();
    }

    /** Sends one message and reads exactly the reply expected, which fails on anything else or on nothing. */
    private static void exchange(Socket socket, byte[] message, byte[] expectedReply) throws IOException {
        send(socket, message);
        assertThat("reply to " + ByteBufUtil.hexDump(message), ByteBufUtil.hexDump(socket.getInputStream().readNBytes(expectedReply.length)), is(ByteBufUtil.hexDump(expectedReply)));
    }

    private static KeyStore mockServerKeyStore() {
        return new KeyStoreFactory(configuration(), new MockServerLogger()).loadOrCreateKeyStore();
    }

    /**
     * Starts TLS over a connection already in use, as the JDK's own TLS client: it trusts only MockServer's
     * certificate authority, so a handshake that completes was with a certificate MockServer issued.
     */
    private SSLSocket startTls(Socket socket, String protocol, boolean withClientCertificate) throws Exception {
        KeyStore keyStore = mockServerKeyStore();
        KeyStore trusted = KeyStore.getInstance(KeyStore.getDefaultType());
        trusted.load(null, null);
        trusted.setCertificateEntry("mockserver-ca", keyStore.getCertificate(KeyStoreFactory.KEY_STORE_CA_ALIAS));
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(trusted);
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(keyStore, KeyStoreFactory.KEY_STORE_PASSWORD.toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(withClientCertificate ? keys.getKeyManagers() : null, trust.getTrustManagers(), null);
        SSLSocket tls = (SSLSocket) context.getSocketFactory().createSocket(socket, "127.0.0.1", socket.getPort(), true);
        sockets.add(tls);
        tls.setUseClientMode(true);
        tls.setEnabledProtocols(new String[]{protocol});
        tls.startHandshake();
        return tls;
    }

    private void mockAnUpgradeToTlsAndTheSessionThatFollows(String protocol) throws Exception {
        mockServerWith(configuration());
        mock(SSL_REQUEST, new byte[]{'S'});
        Socket socket = connect();

        exchange(socket, SSL_REQUEST, new byte[]{'S'});
        SSLSocket tls = startTls(socket, protocol, false);

        assertThat(tls.getSession().getProtocol(), is(protocol));
        X509Certificate presented = (X509Certificate) tls.getSession().getPeerCertificates()[0];
        assertThat("the certificate is one MockServer issued", presented.getIssuerX500Principal().getName(), containsString("MockServer"));
        exchange(tls, STARTUP, AUTHENTICATION_OK_AND_READY);
        exchange(tls, QUERY, QUERY_RESULT);
        exchange(tls, SYNC, READY);
        exchange(tls, LIKE_HTTP, LIKE_HTTP_REPLY);
        exchange(tls, QUERY, QUERY_RESULT);

        tls.shutdownOutput();
        assertThat("MockServer answers the client's close with its own, and no error", tls.getInputStream().read(), is(-1));
    }

    @Test
    public void shouldMockASessionThatUpgradesToTls12() throws Exception {
        mockAnUpgradeToTlsAndTheSessionThatFollows("TLSv1.2");
    }

    @Test
    public void shouldMockASessionThatUpgradesToTls13() throws Exception {
        mockAnUpgradeToTlsAndTheSessionThatFollows("TLSv1.3");
    }

    @Test
    public void shouldStayInTheClearWhenTheUpgradeIsRefused() throws Exception {
        mockServerWith(configuration());
        mock(SSL_REQUEST, new byte[]{'N'});
        Socket socket = connect();

        exchange(socket, SSL_REQUEST, new byte[]{'N'});

        exchange(socket, STARTUP, AUTHENTICATION_OK_AND_READY);
        exchange(socket, SYNC, READY);
        exchange(socket, LIKE_HTTP, LIKE_HTTP_REPLY);
    }

    @Test
    public void shouldUpgradeWhenTheHandshakeStartsOneByteAtATime() throws Exception {
        mockServerWith(configuration());
        mock(SSL_REQUEST, new byte[]{'S'});
        int attempts = 5;
        for (int attempt = 0; attempt < attempts; attempt++) {
            OneByteAtATimeSocket socket = opened(new OneByteAtATimeSocket("127.0.0.1", mockServer.getLocalPort(), false));
            Exception failure = null;
            try {
                exchange(socket, SSL_REQUEST, new byte[]{'S'});
                socket.oneAtATimeFromNow();
                exchange(startTls(socket, "TLSv1.3", false), STARTUP, AUTHENTICATION_OK_AND_READY);
            } catch (Exception failed) {
                failure = failed;
            }
            // a stalled attempt shows nothing either way, see OneByteAtATimeSocket
            if (socket.longestGapMillis() < 500) {
                if (failure != null) {
                    throw failure;
                }
                return;
            }
        }
        throw new AssertionError("the handshake's first bytes could not be written less than 500 ms apart in " + attempts + " attempts");
    }

    @Test
    public void shouldKeepAMessageThatOnlyResemblesAHandshakeBinary() throws Exception {
        // a handshake record, but of a ServerHello: nothing a client opens with
        byte[] notAClientHello = {22, 3, 3, 0, 5, 2, 0, 0, 1, 0};
        mockServerWith(configuration());
        mock(notAClientHello, READY);
        Socket socket = connect();

        exchange(socket, STARTUP, AUTHENTICATION_OK_AND_READY);
        exchange(socket, notAClientHello, READY);

        exchange(socket, SYNC, READY);
    }

    @Test
    public void shouldTreatAHandshakeSentOverTlsAsAMessage() throws Exception {
        byte[] clientHello = tlsClientHello();
        mockServerWith(configuration());
        mock(SSL_REQUEST, new byte[]{'S'});
        mock(clientHello, READY);
        Socket socket = connect();
        exchange(socket, SSL_REQUEST, new byte[]{'S'});
        SSLSocket tls = startTls(socket, "TLSv1.3", false);
        // an exchange first: sent straight after the handshake, a message this long can reach MockServer in two reads
        exchange(tls, STARTUP, AUTHENTICATION_OK_AND_READY);

        // a second upgrade is not a thing: inside TLS these bytes are a message like any other
        exchange(tls, clientHello, READY);

        exchange(tls, SYNC, READY);
    }

    @Test
    public void shouldTreatAHandshakeSentOverAConnectionThatStartedWithTlsAsAMessage() throws Exception {
        byte[] clientHello = tlsClientHello();
        mockServerWith(configuration());
        mock(clientHello, READY);

        SSLSocket tls = startTls(connect(), "TLSv1.3", false);

        exchange(tls, STARTUP, AUTHENTICATION_OK_AND_READY);
        exchange(tls, clientHello, READY);
        exchange(tls, SYNC, READY);
        exchange(tls, LIKE_HTTP, LIKE_HTTP_REPLY);
    }

    /** The reply to a message sent after the upgrade, or how the upgrade or the exchange failed. */
    private String replyAfterUpgrading(String protocol, boolean withClientCertificate, boolean upgrade) {
        try {
            Socket socket = connect();
            if (upgrade) {
                exchange(socket, SSL_REQUEST, new byte[]{'S'});
            }
            SSLSocket tls = startTls(socket, protocol, withClientCertificate);
            send(tls, STARTUP);
            byte[] reply = tls.getInputStream().readNBytes(AUTHENTICATION_OK_AND_READY.length);
            return reply.length == 0 ? "closed without a reply" : ByteBufUtil.hexDump(reply);
        } catch (Exception failed) {
            return "failed: " + failed.getClass().getSimpleName();
        }
    }

    @Test
    public void shouldRequireAClientCertificateOfAnUpgradedConnectionAsOfOneThatStartedWithTls() {
        // the client certificate is signed by the bundled CA
        mockServerWith(configuration().tlsMutualAuthenticationAllowBundledCa(true).tlsMutualAuthenticationRequired(true));
        mock(SSL_REQUEST, new byte[]{'S'});
        String answered = ByteBufUtil.hexDump(AUTHENTICATION_OK_AND_READY);

        assertThat("upgraded, with a certificate", replyAfterUpgrading("TLSv1.3", true, true), is(answered));
        assertThat("started with TLS, with a certificate", replyAfterUpgrading("TLSv1.3", true, false), is(answered));

        String upgradedWithout = replyAfterUpgrading("TLSv1.3", false, true);
        String startedWithout = replyAfterUpgrading("TLSv1.3", false, false);
        // how the refusal reaches the client depends on which of them gets there first, so only that it is one is checked
        assertThat("upgraded, without a certificate: " + upgradedWithout, upgradedWithout.equals(answered), is(false));
        assertThat("started with TLS, without a certificate: " + startedWithout, startedWithout.equals(answered), is(false));
    }

    @Test
    public void shouldOfferAnUpgradedConnectionOnlyTheTlsProtocolsConfigured() {
        mockServerWith(configuration().tlsProtocols("TLSv1.3"));
        mock(SSL_REQUEST, new byte[]{'S'});
        String answered = ByteBufUtil.hexDump(AUTHENTICATION_OK_AND_READY);

        assertThat("upgraded, with the protocol configured", replyAfterUpgrading("TLSv1.3", false, true), is(answered));

        String upgradedWithAnother = replyAfterUpgrading("TLSv1.2", false, true);
        String startedWithAnother = replyAfterUpgrading("TLSv1.2", false, false);
        assertThat("upgraded, with a protocol not configured: " + upgradedWithAnother, upgradedWithAnother.equals(answered), is(false));
        assertThat("started with TLS, with a protocol not configured: " + startedWithAnother, startedWithAnother.equals(answered), is(false));
    }

    /** The first record a TLS client sends, from a client that offers little so that it is a small message. */
    private static byte[] tlsClientHello() throws Exception {
        SSLEngine client = SSLContext.getDefault().createSSLEngine();
        client.setUseClientMode(true);
        client.setEnabledProtocols(new String[]{"TLSv1.2"});
        client.setEnabledCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"});
        client.beginHandshake();
        ByteBuffer record = ByteBuffer.allocate(client.getSession().getPacketBufferSize());
        client.wrap(ByteBuffer.allocate(0), record);
        record.flip();
        byte[] clientHello = new byte[record.remaining()];
        record.get(clientHello);
        assertThat("a handshake record", clientHello[0], is((byte) 22));
        assertThat("that opens with a ClientHello", clientHello[5], is((byte) 1));
        assertThat((int) clientHello[2], lessThan(5));
        return clientHello;
    }
}
