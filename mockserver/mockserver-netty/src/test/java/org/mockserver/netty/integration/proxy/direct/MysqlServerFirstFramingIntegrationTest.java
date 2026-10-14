package org.mockserver.netty.integration.proxy.direct;

import io.netty.buffer.ByteBufUtil;
import org.junit.After;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.BinaryMessageFraming;
import org.mockserver.mock.Expectation;
import org.mockserver.netty.MockServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * MySQL through a port-forwarding MockServer with binaryMessageFraming MYSQL and forwardBinaryServerFirstWaitMillis:
 * the server's greeting reaches a client that has sent nothing, and the client's packets are framed, so one written
 * in two parts with a pause is matched whole by an expectation and the others are relayed, all on the one upstream
 * connection the greeting came on.
 */
public class MysqlServerFirstFramingIntegrationTest {

    private static final long WAIT_MILLIS = 200;
    private static final byte[] GREETING = packet(0, 20, 'g');
    private MockServer mockServer;

    @After
    public void stopMockServer() {
        stopQuietly(mockServer);
    }

    private static byte[] packet(int sequence, int payloadLength, char fill) {
        byte[] packet = new byte[4 + payloadLength];
        Arrays.fill(packet, (byte) fill);
        packet[0] = (byte) payloadLength;
        packet[1] = (byte) (payloadLength >> 8);
        packet[2] = (byte) (payloadLength >> 16);
        packet[3] = (byte) sequence;
        return packet;
    }

    private static void send(Socket socket, byte[] bytes) throws IOException {
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
    }

    private static String receive(InputStream input, int length) throws IOException {
        return ByteBufUtil.hexDump(input.readNBytes(length));
    }

    @Test
    public void shouldRelayTheGreetingAndFrameTheClientsPacketsMatchingOneWrittenInTwoParts() throws Exception {
        try (MysqlLikeUpstream upstream = new MysqlLikeUpstream()) {
            mockServer = new MockServer(configuration()
                .forwardBinaryServerFirstWaitMillis(WAIT_MILLIS)
                .binaryMessageFraming(BinaryMessageFraming.MYSQL)
                .matchBinaryExpectationsWhenProxying(true), upstream.port(), "127.0.0.1", 0);
            byte[] answered = packet(0, 3000, 'q');
            byte[] answer = packet(1, 1, 'M');
            new MockServerClient("127.0.0.1", mockServer.getLocalPort())
                .upsert(new Expectation(binaryRequest(answered)).thenRespondWithBinary(binaryResponse(answer)));
            try (Socket client = new Socket("127.0.0.1", mockServer.getLocalPort())) {
                client.setSoTimeout(20_000);
                client.setTcpNoDelay(true);

                assertThat("the greeting, before the client has sent anything", receive(client.getInputStream(), GREETING.length), is(ByteBufUtil.hexDump(GREETING)));
                byte[] handshakeResponse = packet(1, 40, 'h');
                send(client, handshakeResponse);
                assertThat(receive(client.getInputStream(), 5), is(ByteBufUtil.hexDump(packet(2, 1, 'U'))));

                send(client, Arrays.copyOfRange(answered, 0, 1500));
                Thread.sleep(WAIT_MILLIS);
                send(client, Arrays.copyOfRange(answered, 1500, answered.length));
                assertThat("matched whole and answered by the expectation", receive(client.getInputStream(), answer.length), is(ByteBufUtil.hexDump(answer)));

                byte[] forwarded = packet(0, 10, 'f');
                send(client, forwarded);
                assertThat(receive(client.getInputStream(), 5), is(ByteBufUtil.hexDump(packet(1, 1, 'U'))));
            }
            assertThat("the upstream saw the packets that were not answered, each whole", upstream.packetsReceived, contains("h*40", "f*10"));
            assertThat("one upstream connection, opened for the greeting", upstream.connections.get(), is(1));
        }
    }

    /** Greets each connection with a MySQL-shaped packet, then answers each packet it reads with a 1-byte one. */
    private static final class MysqlLikeUpstream implements AutoCloseable {
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private final AtomicInteger connections = new AtomicInteger();
        private final List<String> packetsReceived = new CopyOnWriteArrayList<>();

        MysqlLikeUpstream() throws IOException {
            Thread acceptor = new Thread(this::accept, "mysql-like-upstream");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        private void accept() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    connections.incrementAndGet();
                    Thread session = new Thread(() -> converse(socket), "mysql-like-upstream-session");
                    session.setDaemon(true);
                    session.start();
                } catch (IOException closed) {
                    return;
                }
            }
        }

        private void converse(Socket socket) {
            try (Socket closing = socket) {
                send(closing, GREETING);
                InputStream input = closing.getInputStream();
                for (byte[] header; (header = input.readNBytes(4)).length == 4; ) {
                    int length = (header[0] & 0xff) | (header[1] & 0xff) << 8 | (header[2] & 0xff) << 16;
                    byte[] payload = input.readNBytes(length);
                    packetsReceived.add((char) payload[0] + "*" + payload.length);
                    send(closing, packet((header[3] + 1) & 0xff, 1, 'U'));
                }
            } catch (IOException closed) {
                // the session ended
            }
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
