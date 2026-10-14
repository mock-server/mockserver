package org.mockserver.netty.integration.proxy.direct;

import io.netty.buffer.ByteBufUtil;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.BinaryMessageFraming;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.BinaryResponse;
import org.mockserver.model.Delay;
import org.slf4j.event.Level;

import javax.net.ssl.SSLSocket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.netty.integration.proxy.direct.StartTlsUpstream.SSL_REQUEST;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A binary expectation's {@code upstream} on a connection MockServer relays to a PostgreSQL-like upstream, over real
 * sockets: ANSWER_ONLY answers and does not forward; ANSWER_AND_FORWARD answers, forwards, and drops the upstream's
 * reply; FORWARD_AND_REPLACE forwards and writes its bytes in place of the upstream's reply once that has ended. A
 * reply here ends at ReadyForQuery, however many reads it takes, which binaryMessageFraming POSTGRESQL makes known;
 * without it the expectation answers as ANSWER_ONLY.
 */
public class BinaryResponseUpstreamIntegrationTest {

    private static final byte[] STARTUP = {0, 0, 0, 23, 0, 3, 0, 0, 'u', 's', 'e', 'r', 0, 'p', 'o', 's', 't', 'g', 'r', 'e', 's', 0, 0};
    private static final byte[] AUTHENTICATION_OK_AND_READY = {'R', 0, 0, 0, 8, 0, 0, 0, 0, 'Z', 0, 0, 0, 5, 'I'};
    private static final byte[] QUERY = {'Q', 0, 0, 0, 13, 'S', 'E', 'L', 'E', 'C', 'T', ' ', '1', 0};
    private static final byte[] READY = {'Z', 0, 0, 0, 5, 'I'};
    private static final byte[] MOCKED_QUERY = {'Q', 0, 0, 0, 13, 'S', 'E', 'L', 'E', 'C', 'T', ' ', '2', 0};
    // the upstream's reply to MOCKED_QUERY, which it sends in two parts: a RowDescription, then a DataRow onwards
    private static final byte[] UPSTREAM_ROWS = {'T', 0, 0, 0, 6, 0, 0};
    private static final byte[] UPSTREAM_REST = {'D', 0, 0, 0, 6, 0, 0, 'C', 0, 0, 0, 13, 'S', 'E', 'L', 'E', 'C', 'T', ' ', '2', 0, 'Z', 0, 0, 0, 5, 'I'};
    private static final byte[] CANNED = {'C', 0, 0, 0, 13, 'S', 'E', 'L', 'E', 'C', 'T', ' ', '9', 0, 'Z', 0, 0, 0, 5, 'I'};

    private final List<LogEntry> logged = new CopyOnWriteArrayList<>();
    private ClientAndServer mockServer;

    @After
    public void stopMockServer() {
        MockServerLogger.setGlobalLogEventListener(null);
        stopQuietly(mockServer);
    }

    private static Configuration postgresqlFraming() {
        return configuration().logLevel("INFO").matchBinaryExpectationsWhenProxying(true).binaryMessageFraming(BinaryMessageFraming.POSTGRESQL);
    }

    private Socket clientThrough(Configuration configuration, StartTlsUpstream upstream) throws IOException {
        // a clone: the event log clears the entry it is handed once it has copied it
        MockServerLogger.setGlobalLogEventListener(logEntry -> logged.add(logEntry.clone()));
        mockServer = startClientAndServer(configuration, "127.0.0.1", upstream.port());
        Socket client = new Socket("127.0.0.1", mockServer.getPort());
        client.setSoTimeout(20_000);
        client.setTcpNoDelay(true);
        return client;
    }

    private void mock(byte[] request, BinaryResponse response) {
        mockServer.upsert(new Expectation(binaryRequest(request)).thenRespondWithBinary(response));
    }

    private static StartTlsUpstream postgresLikeUpstream() throws IOException {
        return new StartTlsUpstream()
            .answering(STARTUP, AUTHENTICATION_OK_AND_READY)
            .answering(QUERY, READY)
            .answering(MOCKED_QUERY, UPSTREAM_ROWS);
    }

    private static void send(OutputStream output, byte[]... messages) throws IOException {
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] message : messages) {
            joined.write(message);
        }
        output.write(joined.toByteArray());
        output.flush();
    }

    private static void expect(InputStream input, byte[] expected) throws IOException {
        assertThat(ByteBufUtil.hexDump(input.readNBytes(expected.length)), is(ByteBufUtil.hexDump(expected)));
    }

    private static void expectNothingFor(Socket client, int millis) throws IOException {
        int timeout = client.getSoTimeout();
        client.setSoTimeout(millis);
        try {
            int read = client.getInputStream().read();
            fail("expected nothing, but read " + read);
        } catch (SocketTimeoutException expected) {
            // nothing arrived
        } finally {
            client.setSoTimeout(timeout);
        }
    }

    private List<LogEntry> logged(String containing) {
        return logged.stream()
            .filter(entry -> String.valueOf(entry.getMessageFormat()).contains(containing))
            .collect(Collectors.toList());
    }

    private void awaitLogged(String containing, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (logged(containing).size() < count) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("expected " + count + " log entries containing \"" + containing + "\" but found " + logged(containing).size());
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }

    private static void awaitUpstreamMessage(StartTlsUpstream.Connection connection, String message) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!connection.messages().contains(message)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the upstream never received " + message + ", only " + connection.messages());
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }

    @Test
    public void shouldAnswerWithoutForwardingWhenAnswerOnly() throws Exception {
        try (StartTlsUpstream upstream = postgresLikeUpstream();
             Socket client = clientThrough(postgresqlFraming(), upstream)) {
            mock(MOCKED_QUERY, binaryResponse(CANNED).withUpstream(BinaryResponse.Upstream.ANSWER_ONLY));

            send(client.getOutputStream(), STARTUP);
            expect(client.getInputStream(), AUTHENTICATION_OK_AND_READY);
            send(client.getOutputStream(), MOCKED_QUERY);
            expect(client.getInputStream(), CANNED);
            send(client.getOutputStream(), QUERY);
            expect(client.getInputStream(), READY);

            assertThat(upstream.awaitConnection(1, 5_000).messages(), contains("clear " + ByteBufUtil.hexDump(STARTUP), "clear " + ByteBufUtil.hexDump(QUERY)));
        }
    }

    @Test
    public void shouldAnswerForwardAndDropTheUpstreamsReplyAcrossReadsWhenAnswerAndForward() throws Exception {
        try (StartTlsUpstream upstream = postgresLikeUpstream();
             Socket client = clientThrough(postgresqlFraming(), upstream)) {
            mock(MOCKED_QUERY, binaryResponse(CANNED).withUpstream(BinaryResponse.Upstream.ANSWER_AND_FORWARD));

            send(client.getOutputStream(), STARTUP);
            expect(client.getInputStream(), AUTHENTICATION_OK_AND_READY);
            send(client.getOutputStream(), MOCKED_QUERY);
            expect(client.getInputStream(), CANNED);

            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 5_000);
            awaitUpstreamMessage(connection, "clear " + ByteBufUtil.hexDump(MOCKED_QUERY));
            awaitLogged("dropping binary response", 1);
            connection.push(UPSTREAM_REST);
            awaitLogged("dropping binary response", 2);
            send(client.getOutputStream(), QUERY);

            // neither part of the upstream's reply reached the client: the next bytes are the next query's reply
            expect(client.getInputStream(), READY);
            assertThat(connection.messages(), contains("clear " + ByteBufUtil.hexDump(STARTUP), "clear " + ByteBufUtil.hexDump(MOCKED_QUERY), "clear " + ByteBufUtil.hexDump(QUERY)));
        }
    }

    @Test
    public void shouldNotWarnThatADelayedAnswerOvertakesTheMessageForwardedForIt() throws Exception {
        try (StartTlsUpstream upstream = new StartTlsUpstream()
            .answering(STARTUP, AUTHENTICATION_OK_AND_READY)
            .answering(QUERY, READY);
             Socket client = clientThrough(postgresqlFraming(), upstream)) {
            mock(MOCKED_QUERY, binaryResponse(CANNED).withDelay(Delay.milliseconds(200)).withUpstream(BinaryResponse.Upstream.ANSWER_AND_FORWARD));

            send(client.getOutputStream(), STARTUP);
            expect(client.getInputStream(), AUTHENTICATION_OK_AND_READY);
            send(client.getOutputStream(), MOCKED_QUERY);
            expect(client.getInputStream(), CANNED);

            // the forwarded message has had no reply yet, but it is the answered message itself, not one it overtook
            assertThat(logged("may reach the client out of order"), hasSize(0));
            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 5_000);
            connection.push(UPSTREAM_ROWS);
            connection.push(UPSTREAM_REST);
            send(client.getOutputStream(), QUERY);
            expect(client.getInputStream(), READY);
        }
    }

    @Test
    public void shouldForwardAndWriteTheReplacementOnceTheUpstreamsReplyHasEndedWhenForwardAndReplace() throws Exception {
        try (StartTlsUpstream upstream = postgresLikeUpstream();
             Socket client = clientThrough(postgresqlFraming(), upstream)) {
            mock(MOCKED_QUERY, binaryResponse(CANNED).withUpstream(BinaryResponse.Upstream.FORWARD_AND_REPLACE));

            send(client.getOutputStream(), STARTUP);
            expect(client.getInputStream(), AUTHENTICATION_OK_AND_READY);
            send(client.getOutputStream(), MOCKED_QUERY);

            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 5_000);
            awaitLogged("dropping binary response", 1);
            expectNothingFor(client, 300);
            connection.push(UPSTREAM_REST);
            expect(client.getInputStream(), CANNED);

            send(client.getOutputStream(), QUERY);
            expect(client.getInputStream(), READY);
            assertThat(connection.messages(), contains("clear " + ByteBufUtil.hexDump(STARTUP), "clear " + ByteBufUtil.hexDump(MOCKED_QUERY), "clear " + ByteBufUtil.hexDump(QUERY)));
            assertThat(logged("in place of the response from"), hasSize(1));
        }
    }

    @Test
    public void shouldHoldTheUpstreamsLaterRepliesBehindADelayedReplacement() throws Exception {
        try (StartTlsUpstream upstream = new StartTlsUpstream()
            .answering(STARTUP, AUTHENTICATION_OK_AND_READY)
            .answering(QUERY, READY)
            .answering(MOCKED_QUERY, CANNED.clone());
             Socket client = clientThrough(postgresqlFraming(), upstream)) {
            byte[] replacement = {'C', 0, 0, 0, 13, 'S', 'E', 'L', 'E', 'C', 'T', ' ', '7', 0, 'Z', 0, 0, 0, 5, 'T'};
            mock(MOCKED_QUERY, binaryResponse(replacement).withDelay(Delay.milliseconds(500)).withUpstream(BinaryResponse.Upstream.FORWARD_AND_REPLACE));

            send(client.getOutputStream(), STARTUP);
            expect(client.getInputStream(), AUTHENTICATION_OK_AND_READY);
            long sent = System.nanoTime();
            send(client.getOutputStream(), MOCKED_QUERY, QUERY);

            expect(client.getInputStream(), replacement);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sent), greaterThanOrEqualTo(450L));
            expect(client.getInputStream(), READY);
        }
    }

    @Test
    public void shouldReplaceAReplyOverTlsAfterRelayingTheSslRequestsOneByteAnswer() throws Exception {
        try (StartTlsUpstream upstream = postgresLikeUpstream();
             Socket client = clientThrough(postgresqlFraming(), upstream)) {
            mock(MOCKED_QUERY, binaryResponse(CANNED).withUpstream(BinaryResponse.Upstream.FORWARD_AND_REPLACE));

            send(client.getOutputStream(), SSL_REQUEST);
            assertThat(client.getInputStream().read(), is((int) 'S'));
            SSLSocket tls = StartTlsUpstream.startTlsAsClient(client, "127.0.0.1", "TLSv1.3", false);
            send(tls.getOutputStream(), STARTUP);
            expect(tls.getInputStream(), AUTHENTICATION_OK_AND_READY);
            send(tls.getOutputStream(), MOCKED_QUERY);
            StartTlsUpstream.Connection connection = upstream.awaitConnection(1, 5_000);
            awaitLogged("dropping binary response", 1);
            connection.push(UPSTREAM_REST);
            expect(tls.getInputStream(), CANNED);
            send(tls.getOutputStream(), QUERY);
            expect(tls.getInputStream(), READY);

            assertThat(connection.messages(), contains("clear " + ByteBufUtil.hexDump(SSL_REQUEST), "TLS " + ByteBufUtil.hexDump(STARTUP), "TLS " + ByteBufUtil.hexDump(MOCKED_QUERY), "TLS " + ByteBufUtil.hexDump(QUERY)));
        }
    }

    @Test
    public void shouldAnswerWithoutForwardingAndWarnOnceWithoutPostgresqlFraming() throws Exception {
        try (StartTlsUpstream upstream = postgresLikeUpstream();
             Socket client = clientThrough(configuration().logLevel("INFO").matchBinaryExpectationsWhenProxying(true), upstream)) {
            mock(MOCKED_QUERY, binaryResponse(CANNED).withUpstream(BinaryResponse.Upstream.FORWARD_AND_REPLACE));

            send(client.getOutputStream(), STARTUP);
            expect(client.getInputStream(), AUTHENTICATION_OK_AND_READY);
            send(client.getOutputStream(), MOCKED_QUERY);
            expect(client.getInputStream(), CANNED);
            send(client.getOutputStream(), MOCKED_QUERY);
            expect(client.getInputStream(), CANNED);
            send(client.getOutputStream(), QUERY);
            expect(client.getInputStream(), READY);

            assertThat(upstream.awaitConnection(1, 5_000).messages(), contains("clear " + ByteBufUtil.hexDump(STARTUP), "clear " + ByteBufUtil.hexDump(QUERY)));
            assertThat(logged("as ANSWER_ONLY where binary expectation").stream().filter(entry -> entry.getLogLevel() == Level.WARN).count(), is(1L));
        }
    }
}
