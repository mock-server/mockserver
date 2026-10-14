package org.mockserver.netty.unification;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.configuration.BinaryMessageFraming;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.action.http.HttpActionHandler;
import org.mockserver.model.BinaryMessage;
import org.mockserver.netty.MockServerUnificationInitializer;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.ChannelReadPause;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.slf4j.event.Level;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.netty.unification.BinaryAwareRecvByteBufAllocator.BINARY_READ_SIZE;
import static org.mockserver.netty.unification.BinaryAwareRecvByteBufAllocatorTest.readHandle;

/**
 * Where a binary connection's messages begin and end. MockServer's own pipeline is driven read by read: the test
 * decides which bytes are waiting on the socket when a read loop starts, and the loop is Netty's
 * ({@code AbstractNioByteChannel.read()}) with the connection's own receive buffer sizes, so every case is exact.
 * The TLS client is the JDK's engine.
 */
public class BinaryMessageBoundaryTest {

    private static final byte[] SSL_REQUEST = {0, 0, 0, 8, 4, (byte) 0xd2, 0x16, 0x2f};
    // BinaryMessageGatherer.MAX_GATHERED_BYTES, which is not visible from this package
    private static final int MAX_GATHERED_BYTES = 256 * 1024;

    private enum Transport {
        IN_THE_CLEAR, TLS_FROM_THE_START, TLS_TURNED_ON_PART_WAY
    }

    private static final Scheduler scheduler = mock(Scheduler.class);
    private static HttpState httpState;
    private static NettySslContextFactory serverTls;
    private static SSLContext clientTls;

    // each message on a connection of its own, which is what these cases record; the relay has its own cases below
    private final Configuration configuration = configuration().assumeAllRequestsAreHttp(false).forwardBinaryRequestsUseSingleConnection(false);
    private final NettyHttpClient httpClient = mock(NettyHttpClient.class);
    // what binary handling took as messages, in order
    private final List<String> messages = new ArrayList<>();
    private final List<Boolean> forwardedOverTls = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();
    private final List<EmbeddedChannel> relayUpstreams = new ArrayList<>();
    private final List<Consumer<Throwable>> notYetAccepted = new ArrayList<>();
    private boolean upstreamAccepts;
    // what a client sends, in the clear, before turning TLS on part way through
    private byte[] tlsUpgradeRequest = SSL_REQUEST;
    private MockServerUnificationInitializer initializer;

    @BeforeClass
    public static void startServerState() throws Exception {
        // WARN, whatever the JVM's level is: a fault caught on a connection is logged at WARN
        httpState = new HttpState(configuration(), new MockServerLogger(configuration().logLevel(Level.WARN), BinaryMessageBoundaryTest.class), scheduler);
        serverTls = new NettySslContextFactory(configuration(), new MockServerLogger(), true);
        // made once here, so that each connection finds it ready and takes it on its own thread
        serverTls.createServerSslContext();
        clientTls = SSLContext.getInstance("TLS");
        clientTls.init(null, InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
    }

    @AfterClass
    public static void stopServerState() {
        httpState.stop();
        httpState = null;
        serverTls = null;
    }

    @Before
    public void forwardWhatIsBinaryToAnUpstreamThatNeverAnswers() {
        when(httpClient.sendRequest(any(BinaryMessage.class), anyBoolean(), any(InetSocketAddress.class), any()))
            .thenAnswer(invocation -> {
                messages.add(describe(invocation.<BinaryMessage>getArgument(0).getBytes()));
                forwardedOverTls.add(invocation.getArgument(1));
                return new CompletableFuture<BinaryMessage>();
            });
        // as used when forwarding without waiting for a response: the upstream says when it has taken the message
        when(httpClient.sendRequest(any(BinaryMessage.class), anyBoolean(), any(InetSocketAddress.class), any(), any()))
            .thenAnswer(invocation -> {
                messages.add(describe(invocation.<BinaryMessage>getArgument(0).getBytes()));
                Consumer<Throwable> taken = invocation.getArgument(4);
                if (upstreamAccepts) {
                    taken.accept(null);
                } else {
                    notYetAccepted.add(taken);
                }
                return new CompletableFuture<BinaryMessage>();
            });
        HttpActionHandler actionHandler = mock(HttpActionHandler.class);
        when(actionHandler.getHttpClient()).thenReturn(httpClient);
        initializer = new MockServerUnificationInitializer(configuration, mock(LifeCycle.class), httpState, actionHandler, serverTls);
    }

    @After
    public void closeConnections() {
        connections.forEach(Connection::close);
        relayUpstreams.forEach(EmbeddedChannel::finishAndReleaseAll);
        initializer.getMcpSessionManager().shutdown();
        reset(scheduler);
    }

    private static byte[] message(char letter, int length) {
        byte[] message = new byte[length];
        Arrays.fill(message, (byte) letter);
        return message;
    }

    /** A message as runs of its letters, so that a piece of one message and two messages joined can both be seen. */
    private static String describe(byte[] bytes) {
        if (Arrays.equals(bytes, SSL_REQUEST)) {
            return "SSLRequest";
        }
        StringBuilder runs = new StringBuilder();
        for (int start = 0; start < bytes.length; ) {
            int end = start;
            while (end < bytes.length && bytes[end] == bytes[start]) {
                end++;
            }
            String value = Character.isLetter(bytes[start]) ? String.valueOf((char) bytes[start]) : "#" + bytes[start];
            runs.append(runs.length() > 0 ? " " : "").append(value).append('*').append(end - start);
            start = end;
        }
        return runs.toString();
    }

    /** The messages, with a run of equal ones written once. */
    private String messagesTaken() {
        List<String> summary = new ArrayList<>();
        for (int start = 0; start < messages.size(); ) {
            int end = start;
            while (end < messages.size() && messages.get(end).equals(messages.get(start))) {
                end++;
            }
            summary.add(end - start > 1 ? (end - start) + " x [" + messages.get(start) + "]" : "[" + messages.get(start) + "]");
            start = end;
        }
        return String.join(", ", summary);
    }

    private Connection connect(Transport transport) throws Exception {
        Connection connection = new Connection(transport, true);
        connections.add(connection);
        return connection;
    }

    private Connection connectToAMockServerThatIsNotProxying() throws Exception {
        Connection connection = new Connection(Transport.IN_THE_CLEAR, false);
        connections.add(connection);
        return connection;
    }

    /** A connection that has already carried one small message, so MockServer knows it is binary. */
    private Connection connectAndOpenTheSession(Transport transport) throws Exception {
        Connection connection = connect(transport);
        connection.clientWrites(message('a', 23));
        connection.serverReads();
        messages.clear();
        connection.readLoops = 0;
        return connection;
    }

    private final class Connection {

        private final EmbeddedChannel channel = new EmbeddedChannel();
        // what the client has sent and MockServer has not read yet
        private final ByteBuf waiting = Unpooled.buffer();
        private SSLEngine tls;
        private int readLoops;

        private Connection(Transport transport, boolean proxying) throws Exception {
            if (proxying) {
                channel.attr(REMOTE_SOCKET).set(new InetSocketAddress("127.0.0.1", 1234));
            }
            channel.pipeline().addLast(initializer);
            if (transport == Transport.TLS_TURNED_ON_PART_WAY) {
                clientWrites(tlsUpgradeRequest);
                serverReads();
            }
            if (transport != Transport.IN_THE_CLEAR) {
                handshake();
            }
            messages.clear();
            readLoops = 0;
        }

        /** Puts bytes on the wire as they are: nothing is read until {@link #serverReads()}. */
        private void onTheWire(byte[] bytes) {
            waiting.writeBytes(bytes);
        }

        /** One message, written in one go: in the clear as it is, over TLS as the record or records it takes. */
        private void clientWrites(byte[] message) throws Exception {
            onTheWire(tls == null ? message : encrypted(message));
        }

        /** MockServer reads until nothing is waiting, in as many read loops as that takes. */
        private void serverReads() {
            while (waiting.isReadable() && channel.config().isAutoRead()) {
                readLoop();
            }
        }

        /** {@code AbstractNioByteChannel.read()}, with the bytes waiting in place of the socket. */
        @SuppressWarnings("deprecation")
        private void readLoop() {
            readLoops++;
            RecvByteBufAllocator.Handle handle = readHandle(channel);
            handle.reset(channel.config());
            do {
                ByteBuf buffer = handle.allocate(channel.alloc());
                handle.attemptedBytesRead(buffer.writableBytes());
                int read = Math.min(waiting.readableBytes(), buffer.writableBytes());
                buffer.writeBytes(waiting, read);
                handle.lastBytesRead(read);
                if (read == 0) {
                    buffer.release();
                    break;
                }
                handle.incMessagesRead(1);
                channel.pipeline().fireChannelRead(buffer);
            } while (handle.continueReading());
            handle.readComplete();
            channel.pipeline().fireChannelReadComplete();
            channel.runPendingTasks();
        }

        private void timePasses(long millis) {
            channel.advanceTimeBy(millis, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks();
        }

        private byte[] encrypted(byte[] plaintext) throws Exception {
            ByteArrayOutputStream records = new ByteArrayOutputStream();
            ByteBuffer source = ByteBuffer.wrap(plaintext);
            ByteBuffer record = ByteBuffer.allocate(tls.getSession().getPacketBufferSize());
            do {
                record.clear();
                SSLEngineResult result = tls.wrap(source, record);
                assertThat(result.getStatus(), is(SSLEngineResult.Status.OK));
                records.write(record.array(), 0, record.position());
            } while (source.hasRemaining());
            return records.toByteArray();
        }

        private void handshake() throws Exception {
            handshake(new byte[0]);
        }

        /** A handshake whose ClientHello is put on the wire right behind the bytes given, to be read with them. */
        private void handshake(byte[] sentJustBeforeTheClientHello) throws Exception {
            onTheWire(sentJustBeforeTheClientHello);
            tls = clientTls.createSSLEngine();
            tls.setUseClientMode(true);
            tls.setEnabledProtocols(new String[]{"TLSv1.3"});
            tls.beginHandshake();
            ByteBuffer fromServer = ByteBuffer.allocate(1 << 18);
            fromServer.flip();
            ByteBuffer discarded = ByteBuffer.allocate(tls.getSession().getApplicationBufferSize());
            for (int step = 0; step < 100; step++) {
                switch (tls.getHandshakeStatus()) {
                    case NEED_WRAP:
                        onTheWire(encrypted(new byte[0]));
                        serverReads();
                        break;
                    case NEED_TASK:
                        for (Runnable task = tls.getDelegatedTask(); task != null; task = tls.getDelegatedTask()) {
                            task.run();
                        }
                        break;
                    case NEED_UNWRAP:
                    case NEED_UNWRAP_AGAIN:
                        fromServer.compact();
                        for (ByteBuf written = channel.readOutbound(); written != null; written = channel.readOutbound()) {
                            int length = written.readableBytes();
                            written.readBytes(fromServer.array(), fromServer.position(), length);
                            fromServer.position(fromServer.position() + length);
                            written.release();
                        }
                        fromServer.flip();
                        discarded.clear();
                        assertThat("the server's side of the handshake", tls.unwrap(fromServer, discarded).getStatus(), is(SSLEngineResult.Status.OK));
                        break;
                    default:
                        return;
                }
            }
            throw new AssertionError("the TLS handshake did not finish");
        }

        private void close() {
            waiting.release();
            channel.finishAndReleaseAll();
        }
    }

    private static final String ONE_MESSAGE_AT_THE_LIMIT = "[m*" + MAX_GATHERED_BYTES + "]";

    @Test
    public void shouldTakeOneMessageInOneReadAsOneMessage() throws Exception {
        for (Transport transport : Transport.values()) {
            Connection connection = connectAndOpenTheSession(transport);

            connection.clientWrites(message('m', 800));
            connection.serverReads();

            assertThat(transport.name(), messagesTaken(), is("[m*800]"));
        }
    }

    @Test
    public void shouldTakeAFirstMessageLargerThanTheBufferItIsReadWithAsOneMessage() throws Exception {
        for (Transport transport : Transport.values()) {
            Connection connection = connect(transport);

            // in the clear the first read is of 2,048 bytes; after a handshake that began the connection, of fewer
            connection.clientWrites(message('m', 3000));
            connection.serverReads();

            assertThat(transport.name(), messagesTaken(), is("[m*3000]"));
            assertThat(transport.name(), connection.readLoops, is(1));
        }
    }

    @Test
    public void shouldTakeAMessageLargerThanA64KiBReadAsOneMessage() throws Exception {
        for (Transport transport : Transport.values()) {
            Connection connection = connectAndOpenTheSession(transport);

            connection.clientWrites(message('m', 100_000));
            connection.serverReads();

            assertThat(transport.name(), messagesTaken(), is("[m*100000]"));
            assertThat(transport.name(), connection.readLoops, is(1));
        }
    }

    @Test
    public void shouldJoinTheReadsOfAMessageInTheOrderTheyWereRead() throws Exception {
        for (Transport transport : Transport.values()) {
            Connection connection = connectAndOpenTheSession(transport);
            byte[] message = Arrays.copyOf(message('p', 70_000), 70_100);
            Arrays.fill(message, 70_000, 70_100, (byte) 'q');

            connection.clientWrites(message);
            connection.serverReads();

            assertThat(transport.name(), messagesTaken(), is("[p*70000 q*100]"));
        }
    }

    @Test
    public void shouldTakeTwoMessagesThatAreWaitingTogetherAsOneMessage() throws Exception {
        for (Transport transport : Transport.values()) {
            Connection connection = connectAndOpenTheSession(transport);

            // over TLS these are two records; nothing but a read loop separates messages, and one loop reads both
            connection.clientWrites(message('x', 100));
            connection.clientWrites(message('y', 100));
            connection.serverReads();

            assertThat(transport.name(), messagesTaken(), is("[x*100 y*100]"));
            assertThat(transport.name(), connection.readLoops, is(1));
        }
    }

    @Test
    public void shouldTakeTwoMessagesAsTwoWhenTheClientWaitsForTheFirstToBeRead() throws Exception {
        for (Transport transport : Transport.values()) {
            Connection connection = connectAndOpenTheSession(transport);

            connection.clientWrites(message('x', 100));
            connection.serverReads();
            connection.clientWrites(message('y', 100));
            connection.serverReads();

            assertThat(transport.name(), messagesTaken(), is("[x*100], [y*100]"));
        }
    }

    @Test
    public void shouldTakeAMessageWhoseSecondHalfArrivesLaterAsTwoInTheClearAndAsOneOverTls() throws Exception {
        for (Transport transport : Transport.values()) {
            Connection connection = connectAndOpenTheSession(transport);
            byte[] onTheWire = connection.tls == null ? message('m', 800) : connection.encrypted(message('m', 800));

            connection.onTheWire(Arrays.copyOfRange(onTheWire, 0, onTheWire.length / 2));
            connection.serverReads();
            connection.timePasses(50);
            connection.onTheWire(Arrays.copyOfRange(onTheWire, onTheWire.length / 2, onTheWire.length));
            connection.serverReads();

            // TLS gives nothing up until the whole record has arrived; in the clear the first half is all there is
            assertThat(transport.name(), messagesTaken(), is(transport == Transport.IN_THE_CLEAR ? "2 x [m*400]" : "[m*800]"));
            assertThat(transport.name(), connection.readLoops, is(2));
        }
    }

    @Test
    public void shouldTakeWhatAReadLoopBringsBeyondTheLimitAsFurtherMessages() throws Exception {
        Connection connection = connectAndOpenTheSession(Transport.IN_THE_CLEAR);

        // 24 reads of 64 KiB: Netty ends a read loop after 16, with the rest still waiting
        connection.clientWrites(message('m', 24 * BINARY_READ_SIZE));
        connection.serverReads();

        assertThat("the first loop brings four messages' worth, the second two", messagesTaken(), is("6 x " + ONE_MESSAGE_AT_THE_LIMIT));
        assertThat(connection.readLoops, is(2));
    }

    @Test
    public void shouldTakeWhatAReadLoopBringsBeyondTheLimitAsFurtherMessagesOverTls() throws Exception {
        for (Transport transport : Arrays.asList(Transport.TLS_FROM_THE_START, Transport.TLS_TURNED_ON_PART_WAY)) {
            Connection connection = connectAndOpenTheSession(transport);

            connection.clientWrites(message('m', 24 * BINARY_READ_SIZE));
            connection.serverReads();

            // each loop decrypts the records that are complete in it: under 1 MiB in the first, the rest in the second
            assertThat(transport.name(), connection.readLoops, is(2));
            assertThat(transport.name(), messages.size(), is(7));
            assertThat(transport.name(), messages.stream().filter(("m*" + MAX_GATHERED_BYTES)::equals).count(), is(5L));
            assertThat(transport.name(), messages.stream().mapToInt(taken -> Integer.parseInt(taken.substring(2))).sum(), is(24 * BINARY_READ_SIZE));
            messages.clear();
        }
    }

    @Test
    public void shouldKeepBytesThatWereHeldAsAPossibleHandshakeApartFromWhatFollowsThem() throws Exception {
        Connection connection = connectAndOpenTheSession(Transport.IN_THE_CLEAR);

        connection.clientWrites(new byte[]{22});
        connection.serverReads();
        assertThat("held: it could be the start of a TLS handshake", messagesTaken(), is(""));
        connection.clientWrites(message('x', 3));
        connection.serverReads();

        assertThat("one read loop delivers both, but they arrived in two", messagesTaken(), is("[#22*1], [x*3]"));
    }

    @Test
    public void shouldTakeBytesGivenUpAfterTheWaitAsAMessageAtOnce() throws Exception {
        Connection connection = connectAndOpenTheSession(Transport.IN_THE_CLEAR);
        connection.clientWrites(new byte[]{22});
        connection.serverReads();

        connection.timePasses(PortUnificationHandler.UNDECIDED_PROTOCOL_WAIT_MILLIS);

        assertThat(messagesTaken(), is("[#22*1]"));
    }

    @Test
    public void shouldGiveTheListenerTheSameMessageAsIsForwarded() throws Exception {
        List<String> heardByTheListener = new ArrayList<>();
        configuration
            .forwardBinaryRequestsWithoutWaitingForResponse(true)
            .binaryProxyListener((binaryRequest, binaryResponse, serverAddress, clientAddress) -> heardByTheListener.add(describe(binaryRequest.getBytes())));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(scheduler).scheduleLocalCallback(any(Runnable.class), anyBoolean());
        upstreamAccepts = true;
        for (Transport transport : Transport.values()) {
            Connection connection = connectAndOpenTheSession(transport);
            heardByTheListener.clear();

            connection.clientWrites(message('m', 100_000));
            connection.serverReads();

            assertThat(transport.name(), messagesTaken(), is("[m*100000]"));
            assertThat(transport.name(), heardByTheListener, contains("m*100000"));
            messages.clear();
        }
    }

    /** Relays binary connections on one upstream connection each, an embedded channel that takes everything. */
    private void relayToAnUpstreamThatTakesEverything() {
        configuration.forwardBinaryRequestsUseSingleConnection(true);
        when(httpClient.connectBinaryRelay(any(EventLoop.class), any(InetSocketAddress.class), anyBoolean(), any(ChannelHandler.class))).thenAnswer(invocation -> {
            EmbeddedChannel upstream = new EmbeddedChannel(invocation.<ChannelHandler>getArgument(3));
            relayUpstreams.add(upstream);
            return upstream.newSucceededFuture();
        });
    }

    /** What the relay's upstream connections have been sent since this was last called, one entry for each write. */
    private List<String> relayedWrites() {
        List<String> written = new ArrayList<>();
        for (EmbeddedChannel upstream : relayUpstreams) {
            for (ByteBuf write; (write = upstream.readOutbound()) != null; ) {
                written.add(describe(ByteBufUtil.getBytes(write)));
                write.release();
            }
        }
        return written;
    }

    @Test
    public void shouldRelayWhatOneReadLoopBringsAsOneWriteAndOneListenerCall() throws Exception {
        List<String> heardByTheListener = new ArrayList<>();
        configuration.binaryProxyListener((binaryRequest, binaryResponse, serverAddress, clientAddress) -> heardByTheListener.add(describe(binaryRequest.getBytes())));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(scheduler).scheduleLocalCallback(any(Runnable.class), anyBoolean());
        relayToAnUpstreamThatTakesEverything();
        Connection connection = connectAndOpenTheSession(Transport.IN_THE_CLEAR);
        relayedWrites();
        heardByTheListener.clear();

        connection.clientWrites(message('m', 200_000));
        connection.serverReads();

        assertThat("read in several reads of one loop", connection.readLoops, is(1));
        assertThat("written to the upstream at once, whole", relayedWrites(), contains("m*200000"));
        assertThat(heardByTheListener, contains("m*200000"));
        assertThat("nothing went the per-message way", messages, is(new ArrayList<String>()));
    }

    @Test
    public void shouldRelayWhatAReadLoopBringsBeyondTheLimitAsFurtherWrites() throws Exception {
        relayToAnUpstreamThatTakesEverything();
        Connection connection = connectAndOpenTheSession(Transport.IN_THE_CLEAR);
        relayedWrites();

        connection.clientWrites(message('m', 24 * BINARY_READ_SIZE));
        connection.serverReads();

        List<String> written = relayedWrites();
        assertThat("no write is more than one gathered message", written.size(), is(6));
        assertThat(written.stream().allMatch(("m*" + MAX_GATHERED_BYTES)::equals), is(true));
    }

    /** The first record a TLS client sends. */
    private static byte[] clientHello() throws Exception {
        SSLEngine client = clientTls.createSSLEngine();
        client.setUseClientMode(true);
        client.setEnabledProtocols(new String[]{"TLSv1.3"});
        client.beginHandshake();
        ByteBuffer record = ByteBuffer.allocate(client.getSession().getPacketBufferSize());
        assertThat(client.wrap(ByteBuffer.allocate(0), record).getStatus(), is(SSLEngineResult.Status.OK));
        return Arrays.copyOf(record.array(), record.position());
    }

    @Test
    public void shouldAnswerInTheClearAMessageThatAHandshakeFollowsInTheSameReadLoop() throws Exception {
        // messages that exactly fill the buffer they are read with, so that what follows starts the loop's next read
        httpState.add(new Expectation(binaryRequest(message('o', 23))).thenRespondWithBinary(binaryResponse(message('k', 1))));
        httpState.add(new Expectation(binaryRequest(message('f', 2048))).thenRespondWithBinary(binaryResponse(message('r', 3))));
        httpState.add(new Expectation(binaryRequest(message('l', BINARY_READ_SIZE))).thenRespondWithBinary(binaryResponse(message('s', 3))));
        for (boolean firstMessage : new boolean[]{true, false}) {
            Connection connection = connectToAMockServerThatIsNotProxying();
            if (!firstMessage) {
                connection.clientWrites(message('o', 23));
                connection.serverReads();
                connection.channel.<ByteBuf>readOutbound().release();
                connection.readLoops = 0;
            }

            connection.clientWrites(firstMessage ? message('f', 2048) : message('l', BINARY_READ_SIZE));
            connection.onTheWire(clientHello());
            connection.serverReads();

            String which = firstMessage ? "a first message" : "a later message";
            assertThat(which, connection.readLoops, is(1));
            ByteBuf reply = connection.channel.readOutbound();
            assertThat(which + " is answered before TLS is turned on, so in the clear", describe(ByteBufUtil.getBytes(reply)), is(firstMessage ? "r*3" : "s*3"));
            reply.release();
            ByteBuf handshake = connection.channel.readOutbound();
            assertThat(which + ": then the server's side of the handshake", (int) handshake.getByte(handshake.readerIndex()), is(22));
            handshake.release();
        }
    }

    /** Faults MockServer has logged so far, in every connection: each logged entry that carries an exception. */
    private static long faultsLogged() throws Exception {
        CompletableFuture<List<LogEntry>> logged = new CompletableFuture<>();
        httpState.getMockServerLog().retrieveMessageLogEntries(null, logged::complete);
        return logged.get(10, TimeUnit.SECONDS).stream().filter(entry -> entry.getThrowable() != null).count();
    }

    @Test
    public void shouldCloseWithoutTurningTlsOnWhenNoExpectationMatchesTheMessageBeforeAHandshake() throws Exception {
        httpState.add(new Expectation(binaryRequest(message('g', 23))).thenRespondWithBinary(binaryResponse(message('h', 1))));
        for (boolean firstMessage : new boolean[]{true, false}) {
            Connection connection = connectToAMockServerThatIsNotProxying();
            if (!firstMessage) {
                connection.clientWrites(message('g', 23));
                connection.serverReads();
                connection.channel.<ByteBuf>readOutbound().release();
                connection.readLoops = 0;
            }
            long faultsBefore = faultsLogged();

            // no expectation matches it: MockServer says so and closes the connection
            connection.clientWrites(firstMessage ? message('u', 2048) : message('u', BINARY_READ_SIZE));
            connection.onTheWire(clientHello());
            connection.serverReads();

            String which = firstMessage ? "a first message" : "a later message";
            assertThat(which, connection.readLoops, is(1));
            ByteBuf reply = connection.channel.readOutbound();
            assertThat(which + " is answered in the clear", reply.toString(StandardCharsets.UTF_8), startsWith("unknown message format"));
            reply.release();
            assertThat(which + ": and the connection is closed", connection.channel.isOpen(), is(false));
            assertThat(which + ": with no handshake begun on it", connection.channel.<ByteBuf>readOutbound(), is(nullValue()));
            assertThat(which + ": or TLS turned on", PortUnificationHandler.isSslEnabledUpstream(connection.channel), is(false));
            assertThat(which + ": and nothing went wrong", faultsLogged(), is(faultsBefore));
            connection.channel.checkException();
        }
    }

    @Test
    public void shouldForwardInTheClearAMessageThatAHandshakeFollowsInTheSameReadLoop() throws Exception {
        Connection connection = connect(Transport.IN_THE_CLEAR);

        connection.clientWrites(message('f', 2048));
        connection.onTheWire(clientHello());
        connection.serverReads();

        assertThat(connection.readLoops, is(1));
        assertThat("the handshake is not a message", messagesTaken(), is("[f*2048]"));
        assertThat("and what was sent in the clear goes upstream in the clear", forwardedOverTls, contains(false));
    }

    @Test
    public void shouldMatchAnExpectationAgainstTheWholeMessage() throws Exception {
        byte[] request = message('e', 100_000);
        httpState.add(new Expectation(binaryRequest(request)).thenRespondWithBinary(binaryResponse(message('r', 3))));
        Connection connection = connectToAMockServerThatIsNotProxying();

        connection.clientWrites(request);
        connection.serverReads();

        ByteBuf reply = connection.channel.readOutbound();
        assertThat("the expectation's reply, not the text for a message with no expectation", describe(ByteBufUtil.getBytes(reply)), is("r*3"));
        reply.release();
        assertThat("and nothing else", connection.channel.<ByteBuf>readOutbound(), is(nullValue()));
        assertThat(connection.channel.isOpen(), is(true));
    }

    @Test
    public void shouldStopReadingAClientThatIsAheadOfTheUpstreamAndLoseNothing() throws Exception {
        configuration.forwardBinaryRequestsWithoutWaitingForResponse(true);
        Connection connection = connect(Transport.IN_THE_CLEAR);
        int sent = 0;
        for (char letter : new char[]{'a', 'b', 'c', 'd'}) {
            connection.clientWrites(message(letter, MAX_GATHERED_BYTES));
            sent += MAX_GATHERED_BYTES;
        }

        connection.serverReads();

        // one message is being forwarded, one may wait, and the one that takes the wait past its limit stops the reads
        assertThat("not read while too much waits", ChannelReadPause.holds(connection.channel), is(1));
        int read = sent - connection.waiting.readableBytes();
        assertThat("read: three messages and the rest of the read that completed the third", read, is(2048 + 12 * BINARY_READ_SIZE));
        assertThat("only the first has gone to the upstream", messagesTaken(), is("[a*" + MAX_GATHERED_BYTES + "]"));

        while (!notYetAccepted.isEmpty()) {
            notYetAccepted.remove(0).accept(null);
            connection.serverReads();
        }

        assertThat(ChannelReadPause.holds(connection.channel), is(0));
        assertThat("everything sent was forwarded, in the order sent", messagesTaken(), is(
            "[a*" + MAX_GATHERED_BYTES + "], [b*" + MAX_GATHERED_BYTES + "], [c*" + MAX_GATHERED_BYTES + "], [d*2048], [d*" + (MAX_GATHERED_BYTES - 2048) + "]"
        ));
    }

    /** A PostgreSQL startup message: its length (itself included), protocol 3.0, then parameters, as letters. */
    private static byte[] postgresqlStartup(int length) {
        ByteBuffer message = ByteBuffer.allocate(length).putInt(length).putInt(196608);
        while (message.hasRemaining()) {
            message.put((byte) 's');
        }
        return message.array();
    }

    /** A PostgreSQL message after the startup message: a type byte, its length (itself included), then a body. */
    private static byte[] postgresqlMessage(char type, int length) {
        ByteBuffer message = ByteBuffer.allocate(length).put((byte) type).putInt(length - 1);
        while (message.hasRemaining()) {
            message.put((byte) Character.toLowerCase(type));
        }
        return message.array();
    }

    /** A connection whose PostgreSQL startup message has been taken, so that what follows is typed. */
    private Connection connectWithPostgresqlFramingAndStartTheSession(Transport transport) throws Exception {
        configuration.binaryMessageFraming(BinaryMessageFraming.POSTGRESQL);
        Connection connection = connect(transport);
        byte[] startup = postgresqlStartup(23);
        connection.clientWrites(startup);
        connection.serverReads();
        assertThat(transport.name() + ": the startup message is one message", messagesTaken(), is("[" + describe(startup) + "]"));
        messages.clear();
        connection.readLoops = 0;
        return connection;
    }

    @Test
    public void shouldTakeAPostgresqlMessageThatArrivesOverSeveralReadLoopsAsOneMessage() throws Exception {
        for (Transport transport : Transport.values()) {
            Connection connection = connectWithPostgresqlFramingAndStartTheSession(transport);
            byte[] query = postgresqlMessage('Q', 800);
            byte[] onTheWire = connection.tls == null ? query : connection.encrypted(query);

            connection.onTheWire(Arrays.copyOfRange(onTheWire, 0, onTheWire.length / 2));
            connection.serverReads();
            connection.timePasses(50);
            connection.onTheWire(Arrays.copyOfRange(onTheWire, onTheWire.length / 2, onTheWire.length));
            connection.serverReads();

            assertThat(transport.name(), messagesTaken(), is("[" + describe(query) + "]"));
            assertThat(transport.name(), connection.readLoops, is(2));
            messages.clear();
        }
    }

    @Test
    public void shouldTakeAPostgresqlMessageLargerThanTheGatheringLimitAsOneMessage() throws Exception {
        for (Transport transport : Transport.values()) {
            Connection connection = connectWithPostgresqlFramingAndStartTheSession(transport);
            byte[] copyData = postgresqlMessage('D', 24 * BINARY_READ_SIZE);

            connection.clientWrites(copyData);
            connection.serverReads();

            assertThat(transport.name(), connection.readLoops, is(2));
            assertThat(transport.name(), messagesTaken(), is("[" + describe(copyData) + "]"));
            messages.clear();
        }
    }

    @Test
    public void shouldTakePostgresqlMessagesWaitingTogetherAsSeparateMessages() throws Exception {
        for (Transport transport : Transport.values()) {
            Connection connection = connectWithPostgresqlFramingAndStartTheSession(transport);
            byte[] parse = postgresqlMessage('P', 40);
            byte[] bind = postgresqlMessage('B', 30);
            byte[] sync = postgresqlMessage('S', 5);

            connection.clientWrites(parse);
            connection.clientWrites(bind);
            connection.clientWrites(sync);
            connection.serverReads();

            assertThat(transport.name(), connection.readLoops, is(1));
            assertThat(transport.name(), messagesTaken(), is("[" + describe(parse) + "], [" + describe(bind) + "], [" + describe(sync) + "]"));
            messages.clear();
        }
    }

    @Test
    public void shouldNotTakeBytesInTheMiddleOfAPostgresqlMessageForAHandshake() throws Exception {
        Connection connection = connectWithPostgresqlFramingAndStartTheSession(Transport.IN_THE_CLEAR);
        byte[] hello = clientHello();
        byte[] query = postgresqlMessage('Q', 20 + hello.length);
        System.arraycopy(hello, 0, query, 10, hello.length);

        connection.onTheWire(Arrays.copyOfRange(query, 0, 10));
        connection.serverReads();
        connection.onTheWire(Arrays.copyOfRange(query, 10, query.length));
        connection.serverReads();

        assertThat("a read that starts like a handshake, in the middle of a message, is part of it", messagesTaken(), is("[" + describe(query) + "]"));
        assertThat(PortUnificationHandler.isSslEnabledUpstream(connection.channel), is(false));
        assertThat(connection.channel.<ByteBuf>readOutbound(), is(nullValue()));
        assertThat(connection.channel.isOpen(), is(true));
    }

    @Test
    public void shouldCloseAConnectionThatDeclaresAPostgresqlMessageOverMaxRequestBodySize() throws Exception {
        configuration.maxRequestBodySize(1000);
        for (Transport transport : Transport.values()) {
            Connection connection = connectWithPostgresqlFramingAndStartTheSession(transport);
            byte[] header = Arrays.copyOf(postgresqlMessage('Q', 1001), 5);

            connection.clientWrites(header);
            connection.serverReads();

            assertThat(transport.name(), connection.channel.isOpen(), is(false));
            assertThat(transport.name(), messagesTaken(), is(""));
        }
    }

    @Test
    public void shouldFramePostgresqlMessagesOnARelayedConnectionAndMatchOrForwardEachOnItsOwn() throws Exception {
        configuration.binaryMessageFraming(BinaryMessageFraming.POSTGRESQL).matchBinaryExpectationsWhenProxying(true);
        relayToAnUpstreamThatTakesEverything();
        byte[] answered = postgresqlMessage('Q', 60);
        httpState.add(new Expectation(binaryRequest(answered)).thenRespondWithBinary(binaryResponse(message('c', 3))));
        Connection connection = connect(Transport.IN_THE_CLEAR);
        byte[] startup = postgresqlStartup(23);
        connection.clientWrites(startup);
        connection.serverReads();
        assertThat(relayedWrites(), contains(describe(startup)));
        byte[] sync = postgresqlMessage('S', 5);
        byte[] forwarded = postgresqlMessage('Q', 800);

        // one read loop brings the answered message, the next and half of a third; the rest comes later
        connection.clientWrites(answered);
        connection.clientWrites(sync);
        connection.onTheWire(Arrays.copyOfRange(forwarded, 0, 400));
        connection.serverReads();
        connection.timePasses(50);
        connection.onTheWire(Arrays.copyOfRange(forwarded, 400, forwarded.length));
        connection.serverReads();

        ByteBuf reply = connection.channel.readOutbound();
        assertThat("the matched message is answered by its expectation", describe(ByteBufUtil.getBytes(reply)), is("c*3"));
        reply.release();
        assertThat("the others go upstream, each whole and alone", relayedWrites(), contains(describe(sync), describe(forwarded)));
        assertThat("nothing went the per-message way", messages, is(new ArrayList<String>()));
    }

    private static final BinaryMessageFraming[] LENGTH_FRAMINGS = {BinaryMessageFraming.MYSQL, BinaryMessageFraming.REDIS, BinaryMessageFraming.LENGTH_PREFIX};

    /**
     * A message of the framing given, exactly {@code length} bytes long, whose body is the letter given: a MySQL
     * packet, a Redis bulk string, or a type byte and a 4-byte little-endian length that counts the whole message.
     */
    private static byte[] framedMessage(BinaryMessageFraming framing, char letter, int length) {
        byte[] message = message(letter, length);
        switch (framing) {
            case MYSQL:
                ByteBuffer.wrap(message).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(length - 4);
                message[3] = 0;
                return message;
            case REDIS:
                int bodyBytes = length - 5;
                while (1 + String.valueOf(bodyBytes).length() + 2 + bodyBytes + 2 > length) {
                    bodyBytes--;
                }
                byte[] header = ("$" + bodyBytes + "\r\n").getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(header, 0, message, 0, header.length);
                message[length - 2] = '\r';
                message[length - 1] = '\n';
                return message;
            default:
                message[0] = 'T';
                ByteBuffer.wrap(message, 1, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(length);
                return message;
        }
    }

    private static int headerBytes(BinaryMessageFraming framing, byte[] message) {
        switch (framing) {
            case MYSQL:
                return 4;
            case REDIS:
                return new String(message, StandardCharsets.US_ASCII).indexOf('\n') + 1;
            default:
                return 5;
        }
    }

    /** A MySQL SSLRequest: a 32-byte payload, sequence id 1, whose capability flags include CLIENT_SSL. */
    private static byte[] mysqlSslRequest() {
        byte[] request = new byte[36];
        request[0] = 32;
        request[3] = 1;
        request[4] = (byte) 0x0d;
        request[5] = (byte) 0xaa;
        return request;
    }

    private void frameWith(BinaryMessageFraming framing) {
        configuration.binaryMessageFraming(framing)
            .binaryMessageLengthPrefixOffset(1)
            .binaryMessageLengthPrefixBytes(4)
            .binaryMessageLengthPrefixByteOrder(java.nio.ByteOrder.LITTLE_ENDIAN)
            .binaryMessageLengthIncludesPrefix(true);
        tlsUpgradeRequest = framing == BinaryMessageFraming.MYSQL ? mysqlSslRequest() : framedMessage(framing, 'u', 20);
    }

    /** A connection framed as given that has carried one message, so MockServer knows it is binary. */
    private Connection connectWithFramingAndStartTheSession(BinaryMessageFraming framing, Transport transport) throws Exception {
        frameWith(framing);
        Connection connection = connect(transport);
        byte[] first = framedMessage(framing, 's', 23);
        connection.clientWrites(first);
        connection.serverReads();
        assertThat(framing + " " + transport + ": the first message is one message", messagesTaken(), is("[" + describe(first) + "]"));
        messages.clear();
        connection.readLoops = 0;
        return connection;
    }

    @Test
    public void shouldTakeAMessageThatArrivesOverSeveralReadLoopsAsOneMessageWithEachFraming() throws Exception {
        for (BinaryMessageFraming framing : LENGTH_FRAMINGS) {
            for (Transport transport : Transport.values()) {
                Connection connection = connectWithFramingAndStartTheSession(framing, transport);
                byte[] query = framedMessage(framing, 'q', 800);
                byte[] onTheWire = connection.tls == null ? query : connection.encrypted(query);

                connection.onTheWire(Arrays.copyOfRange(onTheWire, 0, onTheWire.length / 2));
                connection.serverReads();
                connection.timePasses(50);
                connection.onTheWire(Arrays.copyOfRange(onTheWire, onTheWire.length / 2, onTheWire.length));
                connection.serverReads();

                assertThat(framing + " " + transport, messagesTaken(), is("[" + describe(query) + "]"));
                assertThat(framing + " " + transport, connection.readLoops, is(2));
                messages.clear();
            }
        }
    }

    @Test
    public void shouldTakeAMessageLargerThanTheGatheringLimitAsOneMessageWithEachFraming() throws Exception {
        for (BinaryMessageFraming framing : LENGTH_FRAMINGS) {
            for (Transport transport : Transport.values()) {
                Connection connection = connectWithFramingAndStartTheSession(framing, transport);
                byte[] large = framedMessage(framing, 'd', 24 * BINARY_READ_SIZE);

                connection.clientWrites(large);
                connection.serverReads();

                assertThat(framing + " " + transport, connection.readLoops, is(2));
                assertThat(framing + " " + transport, messagesTaken(), is("[" + describe(large) + "]"));
                messages.clear();
            }
        }
    }

    @Test
    public void shouldTakeMessagesWaitingTogetherAsSeparateMessagesWithEachFraming() throws Exception {
        for (BinaryMessageFraming framing : LENGTH_FRAMINGS) {
            for (Transport transport : Transport.values()) {
                Connection connection = connectWithFramingAndStartTheSession(framing, transport);
                byte[] first = framedMessage(framing, 'p', 40);
                byte[] second = framedMessage(framing, 'b', 30);
                byte[] third = framedMessage(framing, 'e', 12);

                connection.clientWrites(first);
                connection.clientWrites(second);
                connection.clientWrites(third);
                connection.serverReads();

                assertThat(framing + " " + transport, connection.readLoops, is(1));
                assertThat(framing + " " + transport, messagesTaken(), is("[" + describe(first) + "], [" + describe(second) + "], [" + describe(third) + "]"));
                messages.clear();
            }
        }
    }

    @Test
    public void shouldCloseAConnectionThatDeclaresAMessageOverMaxRequestBodySizeWithEachFraming() throws Exception {
        configuration.maxRequestBodySize(1000);
        for (BinaryMessageFraming framing : LENGTH_FRAMINGS) {
            for (Transport transport : Transport.values()) {
                Connection connection = connectWithFramingAndStartTheSession(framing, transport);
                byte[] tooLong = framedMessage(framing, 'q', 1001);

                connection.clientWrites(Arrays.copyOf(tooLong, headerBytes(framing, tooLong)));
                connection.serverReads();

                assertThat(framing + " " + transport, connection.channel.isOpen(), is(false));
                assertThat(framing + " " + transport, messagesTaken(), is(""));
            }
        }
    }

    @Test
    public void shouldNotTakeAMysqlPacketThatStartsLikeAHandshakeForOneUnlessItFollowsAnSslRequest() throws Exception {
        Connection connection = connectWithFramingAndStartTheSession(BinaryMessageFraming.MYSQL, Transport.IN_THE_CLEAR);
        // a packet whose header and first two payload bytes are those of a ClientHello
        byte[] hello = clientHello();
        byte[] execute = message('x', 4 + ((hello[0] & 0xff) | (hello[1] & 0xff) << 8 | (hello[2] & 0xff) << 16));
        System.arraycopy(hello, 0, execute, 0, 6);

        connection.clientWrites(execute);
        connection.serverReads();

        assertThat("a packet at a message boundary is a packet unless an SSLRequest came just before it", messagesTaken(), is("[" + describe(execute) + "]"));
        assertThat(PortUnificationHandler.isSslEnabledUpstream(connection.channel), is(false));
        assertThat(connection.channel.<ByteBuf>readOutbound(), is(nullValue()));
        assertThat(connection.channel.isOpen(), is(true));
    }

    @Test
    public void shouldTurnTlsOnWhenAMysqlClientHelloIsReadTogetherWithTheSslRequestBeforeIt() throws Exception {
        for (boolean firstBytesOfTheConnection : new boolean[]{true, false}) {
            Connection connection;
            if (firstBytesOfTheConnection) {
                frameWith(BinaryMessageFraming.MYSQL);
                connection = connect(Transport.IN_THE_CLEAR);
            } else {
                connection = connectWithFramingAndStartTheSession(BinaryMessageFraming.MYSQL, Transport.IN_THE_CLEAR);
            }

            connection.handshake(mysqlSslRequest());
            byte[] query = framedMessage(BinaryMessageFraming.MYSQL, 'q', 800);
            connection.clientWrites(query);
            connection.serverReads();

            assertThat("first bytes " + firstBytesOfTheConnection, messagesTaken(), is("[" + describe(mysqlSslRequest()) + "], [" + describe(query) + "]"));
            assertThat("the SSLRequest in the clear, the query over TLS", forwardedOverTls.subList(forwardedOverTls.size() - 2, forwardedOverTls.size()), contains(false, true));
            messages.clear();
        }
    }
}
