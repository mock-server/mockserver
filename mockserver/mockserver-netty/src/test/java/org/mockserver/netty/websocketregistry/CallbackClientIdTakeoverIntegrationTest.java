package org.mockserver.netty.websocketregistry;

import com.google.common.collect.ImmutableMap;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.authentication.jwt.JWKGenerator;
import org.mockserver.authentication.jwt.JWTGenerator;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.keys.AsymmetricKeyGenerator;
import org.mockserver.keys.AsymmetricKeyPair;
import org.mockserver.keys.AsymmetricKeyPairAlgorithm;
import org.mockserver.socket.tls.PEMToFile;
import org.mockserver.test.TempFileWriter;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedKeyManager;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A callback WebSocket that registers with a client id already in use takes it over only from a
 * connection that is gone, or, with control-plane authentication, from the same caller.
 */
public class CallbackClientIdTakeoverIntegrationTest {

    private static final String CONTROL_PLANE_CA = "org/mockserver/netty/integration/tls/ca.pem";
    private static final String CONTROL_PLANE_CA_KEY = "org/mockserver/netty/integration/tls/ca-key-pkcs8.pem";
    private static final String LEAF_CERTIFICATE = "org/mockserver/netty/integration/tls/leaf-cert.pem";
    private static final String LEAF_KEY = "org/mockserver/netty/integration/tls/leaf-key-pkcs8.pem";

    private static ClientAndServer openServer;
    private static ClientAndServer jwtServer;
    private static ClientAndServer mtlsServer;
    private static JWTGenerator jwtGenerator;

    @BeforeClass
    public static void startServers() {
        openServer = ClientAndServer.startClientAndServer(configuration());
        AsymmetricKeyPair keyPair = AsymmetricKeyGenerator.createAsymmetricKeyPair(AsymmetricKeyPairAlgorithm.RSA2048_SHA256);
        String jwkFile = TempFileWriter.write(new JWKGenerator().generateJWK(keyPair));
        jwtGenerator = new JWTGenerator(keyPair);
        jwtServer = ClientAndServer.startClientAndServer(
            configuration()
                .controlPlaneJWTAuthenticationRequired(true)
                .controlPlaneJWTAuthenticationJWKSource(jwkFile)
        );
    }

    @BeforeClass
    public static void startMutualTlsServer() {
        mtlsServer = ClientAndServer.startClientAndServer(
            configuration()
                .controlPlaneTLSMutualAuthenticationRequired(true)
                .controlPlaneTLSMutualAuthenticationCAChain(CONTROL_PLANE_CA)
        );
    }

    @AfterClass
    public static void stopServers() {
        stopQuietly(openServer);
        stopQuietly(jwtServer);
        stopQuietly(mtlsServer);
    }

    @Test
    public void shouldLetTheRegisteredConnectionCloseDuringTheLivenessCheck() throws Exception {
        String clientId = UUID.randomUUID().toString();
        RawCallbackSocket halfOpen = RawCallbackSocket.open(openServer.getPort(), clientId, null, false);
        assertThat(halfOpen.status, is(101));
        long start = System.currentTimeMillis();
        CompletableFuture<RawCallbackSocket> claim = CompletableFuture.supplyAsync(() -> openQuietly(openServer.getPort(), clientId, null));
        Thread.sleep(300);
        halfOpen.close();
        try (RawCallbackSocket reconnected = claim.get(10, TimeUnit.SECONDS)) {
            assertThat(reconnected.status, is(101));
            assertThat("allowed once the registered connection closed, before the ping timed out", System.currentTimeMillis() - start, lessThan(CallbackClientIdClaim.LIVENESS_TIMEOUT_MILLIS));
        }
    }

    @Test
    public void shouldLetOnlyOneOfTwoConcurrentClaimsTakeOverAHalfOpenConnection() throws Exception {
        String clientId = UUID.randomUUID().toString();
        try (RawCallbackSocket halfOpen = RawCallbackSocket.open(openServer.getPort(), clientId, null, false)) {
            assertThat(halfOpen.status, is(101));
            CompletableFuture<RawCallbackSocket> first = CompletableFuture.supplyAsync(() -> openQuietly(openServer.getPort(), clientId, null));
            CompletableFuture<RawCallbackSocket> second = CompletableFuture.supplyAsync(() -> openQuietly(openServer.getPort(), clientId, null));
            try (RawCallbackSocket one = first.get(15, TimeUnit.SECONDS); RawCallbackSocket other = second.get(15, TimeUnit.SECONDS)) {
                List<Integer> statuses = new ArrayList<>(List.of(one.status, other.status));
                Collections.sort(statuses);
                assertThat(statuses, is(List.of(101, 409)));
            }
        }
    }

    @Test
    public void shouldLetTheSameClientCertificateTakeOverWhileItsOldConnectionAnswersPings() throws Exception {
        String clientId = UUID.randomUUID().toString();
        try (RawCallbackSocket registered = RawCallbackSocket.open(mutualTlsSocket(leafKey(), leafCertificate()), mtlsServer.getPort(), clientId, null, true);
             RawCallbackSocket reconnected = RawCallbackSocket.open(mutualTlsSocket(leafKey(), leafCertificate()), mtlsServer.getPort(), clientId, null, true)) {
            assertThat(registered.status, is(101));
            assertThat("the same caller takes over without a liveness check", reconnected.status, is(101));
        }
    }

    @Test
    public void shouldRefuseATakeoverByAnotherClientCertificate() throws Exception {
        String clientId = UUID.randomUUID().toString();
        KeyPair otherKey = rsaKeyPair();
        X509Certificate other = certificate(new X500Name("CN=other-client, O=MockServer"), otherKey.getPublic(),
            X500Name.getInstance(PEMToFile.x509FromPEMFile(CONTROL_PLANE_CA).getSubjectX500Principal().getEncoded()), PEMToFile.privateKeyFromPEMFile(CONTROL_PLANE_CA_KEY));
        try (RawCallbackSocket registered = RawCallbackSocket.open(mutualTlsSocket(leafKey(), leafCertificate()), mtlsServer.getPort(), clientId, null, true);
             RawCallbackSocket hijacker = RawCallbackSocket.open(mutualTlsSocket(otherKey.getPrivate(), other), mtlsServer.getPort(), clientId, null, true)) {
            assertThat(registered.status, is(101));
            assertThat(hijacker.status, is(403));
        }
    }

    @Test
    public void shouldLetARenewedClientCertificateReplaceAConnectionThatNoLongerAnswers() throws Exception {
        String clientId = UUID.randomUUID().toString();
        KeyPair renewedKey = rsaKeyPair();
        X509Certificate renewed = certificate(X500Name.getInstance(leafCertificate().getSubjectX500Principal().getEncoded()), renewedKey.getPublic(),
            X500Name.getInstance(PEMToFile.x509FromPEMFile(CONTROL_PLANE_CA).getSubjectX500Principal().getEncoded()), PEMToFile.privateKeyFromPEMFile(CONTROL_PLANE_CA_KEY));
        try (RawCallbackSocket halfOpen = RawCallbackSocket.open(mutualTlsSocket(leafKey(), leafCertificate()), mtlsServer.getPort(), clientId, null, false)) {
            assertThat(halfOpen.status, is(101));
            long start = System.currentTimeMillis();
            try (RawCallbackSocket reconnected = RawCallbackSocket.open(mutualTlsSocket(renewedKey.getPrivate(), renewed), mtlsServer.getPort(), clientId, null, true)) {
                assertThat(reconnected.status, is(101));
                assertThat(System.currentTimeMillis() - start, greaterThanOrEqualTo(CallbackClientIdClaim.LIVENESS_TIMEOUT_MILLIS - 100));
                assertThat("the unresponsive connection is closed", halfOpen.closedByServerWithin(5000), is(true));
            }
        }
    }

    /**
     * A self-signed leaf copying the registered certificate's names, followed by that certificate, neither
     * authenticates nor takes the client id over.
     */
    @Test
    public void shouldRefuseASpoofedLeafThatCopiesTheRegisteredCertificatesNames() throws Exception {
        String clientId = UUID.randomUUID().toString();
        X509Certificate victim = leafCertificate();
        KeyPair spoofKey = rsaKeyPair();
        X509Certificate spoof = certificate(X500Name.getInstance(victim.getSubjectX500Principal().getEncoded()), spoofKey.getPublic(),
            X500Name.getInstance(victim.getIssuerX500Principal().getEncoded()), spoofKey.getPrivate());
        try (RawCallbackSocket registered = RawCallbackSocket.open(mutualTlsSocket(leafKey(), victim), mtlsServer.getPort(), clientId, null, true);
             RawCallbackSocket hijacker = RawCallbackSocket.open(mutualTlsSocket(spoofKey.getPrivate(), spoof, victim), mtlsServer.getPort(), clientId, null, true)) {
            assertThat(registered.status, is(101));
            assertThat(hijacker.status, is(401));
        }
    }

    private static RawCallbackSocket openQuietly(int port, String clientId, String authorization) {
        try {
            return RawCallbackSocket.open(port, clientId, authorization, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static PrivateKey leafKey() {
        return PEMToFile.privateKeyFromPEMFile(LEAF_KEY);
    }

    private static X509Certificate leafCertificate() {
        return PEMToFile.x509FromPEMFile(LEAF_CERTIFICATE);
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate certificate(X500Name subject, PublicKey publicKey, X500Name issuer, PrivateKey signingKey) throws Exception {
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            issuer, BigInteger.valueOf(now.toEpochMilli()), Date.from(now.minus(1, ChronoUnit.HOURS)), Date.from(now.plus(1, ChronoUnit.HOURS)), subject, publicKey
        );
        builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
        return new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(signingKey)));
    }

    private static Socket mutualTlsSocket(PrivateKey key, X509Certificate... chain) throws Exception {
        X509ExtendedKeyManager keyManager = new X509ExtendedKeyManager() {
            @Override
            public String[] getClientAliases(String keyType, Principal[] issuers) {
                return new String[]{"client"};
            }

            @Override
            public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                return "client";
            }

            @Override
            public String[] getServerAliases(String keyType, Principal[] issuers) {
                return null;
            }

            @Override
            public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                return null;
            }

            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                return chain;
            }

            @Override
            public PrivateKey getPrivateKey(String alias) {
                return key;
            }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(new KeyManager[]{keyManager}, InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
        SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket("127.0.0.1", mtlsServer.getPort());
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setApplicationProtocols(new String[]{"http/1.1"});
        socket.setSSLParameters(parameters);
        socket.startHandshake();
        return socket;
    }

    @Test
    public void shouldRefuseATakeoverWhileTheRegisteredConnectionAnswersPings() throws Exception {
        String clientId = UUID.randomUUID().toString();
        try (RawCallbackSocket registered = RawCallbackSocket.open(openServer.getPort(), clientId, null, true);
             RawCallbackSocket hijacker = RawCallbackSocket.open(openServer.getPort(), clientId, null, true)) {
            assertThat(registered.status, is(101));
            assertThat(hijacker.status, is(409));
            assertThat(registered.closedByServerWithin(500), is(false));
        }
    }

    @Test
    public void shouldAllowAReconnectOnceTheRegisteredConnectionHasClosed() throws Exception {
        String clientId = UUID.randomUUID().toString();
        RawCallbackSocket dropped = RawCallbackSocket.open(openServer.getPort(), clientId, null, true);
        assertThat(dropped.status, is(101));
        dropped.close();
        try (RawCallbackSocket reconnected = RawCallbackSocket.open(openServer.getPort(), clientId, null, true)) {
            assertThat(reconnected.status, is(101));
            assertThat(reconnected.registration, containsString(clientId));
        }
    }

    @Test
    public void shouldReplaceAHalfOpenConnectionOnceItFailsToAnswerAPing() throws Exception {
        String clientId = UUID.randomUUID().toString();
        try (RawCallbackSocket halfOpen = RawCallbackSocket.open(openServer.getPort(), clientId, null, false)) {
            assertThat(halfOpen.status, is(101));
            long start = System.currentTimeMillis();
            try (RawCallbackSocket reconnected = RawCallbackSocket.open(openServer.getPort(), clientId, null, true)) {
                assertThat(reconnected.status, is(101));
                assertThat(reconnected.registration, containsString(clientId));
                assertThat(System.currentTimeMillis() - start, greaterThanOrEqualTo(CallbackClientIdClaim.LIVENESS_TIMEOUT_MILLIS - 100));
                assertThat("the unresponsive connection is closed", halfOpen.closedByServerWithin(5000), is(true));
            }
        }
    }

    @Test
    public void shouldRefuseATakeoverByAnotherAuthenticatedCaller() throws Exception {
        String clientId = UUID.randomUUID().toString();
        try (RawCallbackSocket registered = RawCallbackSocket.open(jwtServer.getPort(), clientId, bearer("alice"), true);
             RawCallbackSocket hijacker = RawCallbackSocket.open(jwtServer.getPort(), clientId, bearer("mallory"), true)) {
            assertThat(registered.status, is(101));
            assertThat(hijacker.status, is(403));
            assertThat(registered.closedByServerWithin(500), is(false));
        }
    }

    @Test
    public void shouldLetAnotherAuthenticatedCallerReplaceAConnectionThatNoLongerAnswers() throws Exception {
        String clientId = UUID.randomUUID().toString();
        try (RawCallbackSocket halfOpen = RawCallbackSocket.open(jwtServer.getPort(), clientId, bearer("alice"), false)) {
            assertThat(halfOpen.status, is(101));
            long start = System.currentTimeMillis();
            try (RawCallbackSocket renewed = RawCallbackSocket.open(jwtServer.getPort(), clientId, bearer("alice-renewed"), true)) {
                assertThat(renewed.status, is(101));
                assertThat(System.currentTimeMillis() - start, greaterThanOrEqualTo(CallbackClientIdClaim.LIVENESS_TIMEOUT_MILLIS - 100));
                assertThat("the unresponsive connection is closed", halfOpen.closedByServerWithin(5000), is(true));
            }
        }
    }

    @Test
    public void shouldLetTheSameAuthenticatedCallerReconnectWhileItsOldConnectionLooksOpen() throws Exception {
        String clientId = UUID.randomUUID().toString();
        try (RawCallbackSocket registered = RawCallbackSocket.open(jwtServer.getPort(), clientId, bearer("alice"), true);
             RawCallbackSocket reconnected = RawCallbackSocket.open(jwtServer.getPort(), clientId, bearer("alice"), true)) {
            assertThat(registered.status, is(101));
            assertThat(reconnected.status, is(101));
            assertThat(reconnected.registration, containsString(clientId));
        }
    }

    @Test
    public void shouldStillRefuseACallbackUpgradeWithoutCredentials() throws Exception {
        try (RawCallbackSocket anonymous = RawCallbackSocket.open(jwtServer.getPort(), UUID.randomUUID().toString(), null, true)) {
            assertThat(anonymous.status, is(401));
        }
    }

    private static String bearer(String subject) {
        Instant now = Instant.now();
        return "Bearer " + jwtGenerator.signJWT(ImmutableMap.<String, Serializable>of(
            "sub", subject,
            "iss", "https://www.mock-server.com",
            "aud", "https://www.mock-server.com",
            "nbf", now.minus(1, ChronoUnit.HOURS).getEpochSecond(),
            "exp", now.plus(1, ChronoUnit.HOURS).getEpochSecond(),
            "iat", now.getEpochSecond()
        ));
    }

    /**
     * A callback WebSocket over a plain socket, so a test controls whether it answers pings.
     */
    private static final class RawCallbackSocket implements Closeable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final CountDownLatch readerSawClose = new CountDownLatch(1);
        private volatile boolean readerRunning;
        final int status;
        final String registration;

        private RawCallbackSocket(Socket socket, int status, String registration) throws IOException {
            this.socket = socket;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
            this.status = status;
            this.registration = registration;
        }

        static RawCallbackSocket open(int port, String clientId, String authorization, boolean answerPings) throws IOException {
            return open(new Socket("127.0.0.1", port), port, clientId, authorization, answerPings);
        }

        static RawCallbackSocket open(Socket socket, int port, String clientId, String authorization, boolean answerPings) throws IOException {
            socket.setSoTimeout(10_000);
            byte[] key = new byte[16];
            new SecureRandom().nextBytes(key);
            String upgrade = "GET /_mockserver_callback_websocket HTTP/1.1\r\n"
                + "Host: 127.0.0.1:" + port + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + Base64.getEncoder().encodeToString(key) + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "X-CLIENT-REGISTRATION-ID: " + clientId + "\r\n"
                + (authorization != null ? "Authorization: " + authorization + "\r\n" : "")
                + "\r\n";
            socket.getOutputStream().write(upgrade.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String head = readHead(socket.getInputStream());
            int status = Integer.parseInt(head.split(" ")[1]);
            String registration = status == 101 ? new String(readFrame(socket.getInputStream())[1], StandardCharsets.UTF_8) : null;
            RawCallbackSocket rawSocket = new RawCallbackSocket(socket, status, registration);
            if (status == 101 && answerPings) {
                rawSocket.readerRunning = true;
                Thread reader = new Thread(rawSocket::answerPings, "raw-callback-socket-" + clientId);
                reader.setDaemon(true);
                reader.start();
            }
            return rawSocket;
        }

        private void answerPings() {
            try {
                socket.setSoTimeout(0);
                while (true) {
                    byte[][] frame = readFrame(in);
                    if ((frame[0][0] & 0x0F) == 0x9) {
                        writeMaskedFrame(0xA, frame[1]);
                    }
                }
            } catch (IOException closed) {
                readerSawClose.countDown();
            }
        }

        boolean closedByServerWithin(int millis) throws IOException, InterruptedException {
            if (readerRunning) {
                return readerSawClose.await(millis, TimeUnit.MILLISECONDS);
            }
            socket.setSoTimeout(millis);
            try {
                while (in.read() != -1) {
                    // discard frames until the server closes the connection
                }
                return true;
            } catch (SocketTimeoutException timeout) {
                return false;
            } catch (IOException reset) {
                return true;
            }
        }

        private synchronized void writeMaskedFrame(int opcode, byte[] payload) throws IOException {
            byte[] mask = new byte[4];
            new SecureRandom().nextBytes(mask);
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x80 | opcode);
            frame.write(0x80 | payload.length);
            frame.write(mask);
            for (int i = 0; i < payload.length; i++) {
                frame.write(payload[i] ^ mask[i % 4]);
            }
            out.write(frame.toByteArray());
            out.flush();
        }

        private static String readHead(InputStream in) throws IOException {
            StringBuilder head = new StringBuilder();
            while (!head.toString().endsWith("\r\n\r\n")) {
                int next = in.read();
                if (next == -1) {
                    throw new IOException("connection closed during the upgrade response: " + head);
                }
                head.append((char) next);
            }
            return head.toString();
        }

        private static byte[][] readFrame(InputStream in) throws IOException {
            int first = readByte(in);
            int length = readByte(in) & 0x7F;
            if (length == 126) {
                length = (readByte(in) << 8) | readByte(in);
            } else if (length == 127) {
                long longLength = 0;
                for (int i = 0; i < 8; i++) {
                    longLength = (longLength << 8) | readByte(in);
                }
                length = (int) longLength;
            }
            byte[] payload = new byte[length];
            int read = 0;
            while (read < length) {
                int count = in.read(payload, read, length - read);
                if (count == -1) {
                    throw new IOException("connection closed mid-frame");
                }
                read += count;
            }
            return new byte[][]{new byte[]{(byte) first}, payload};
        }

        private static int readByte(InputStream in) throws IOException {
            int next = in.read();
            if (next == -1) {
                throw new IOException("connection closed");
            }
            return next;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
