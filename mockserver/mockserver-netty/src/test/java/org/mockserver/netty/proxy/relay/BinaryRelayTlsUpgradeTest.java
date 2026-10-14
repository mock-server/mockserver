package org.mockserver.netty.proxy.relay;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.unification.PortUnificationHandler;
import org.mockserver.socket.ChannelReadPause;
import org.mockserver.socket.LingeringClose;
import org.mockserver.socket.tls.KeyStoreFactory;
import org.mockserver.socket.tls.SniHandler;
import org.slf4j.event.Level;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * A relayed binary connection whose client turns TLS on part way through, or starts with it: MockServer's own
 * handshake with the upstream runs on the one upstream connection, the client is not read while it runs, and a
 * handshake that fails closes both connections. The upstream is an embedded TLS server the test moves bytes to and
 * from.
 */
public class BinaryRelayTlsUpgradeTest {

    private static final long HANDSHAKE_TIMEOUT_MILLIS = 5_000;
    private static SslContext upstreamServer;
    private static SslContext forwardClient;

    private BinaryRelayHarness relay;
    private EmbeddedChannel tlsServer;
    private final StringBuilder decryptedByUpstream = new StringBuilder();
    private final List<Object> closeEventsAtUpstream = new ArrayList<>();

    @BeforeClass
    public static void createTlsContexts() throws Exception {
        KeyStore keyStore = new KeyStoreFactory(configuration(), new MockServerLogger()).loadOrCreateKeyStore();
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(keyStore, KeyStoreFactory.KEY_STORE_PASSWORD.toCharArray());
        upstreamServer = SslContextBuilder.forServer(keys).sslProvider(SslProvider.JDK).build();
        forwardClient = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).sslProvider(SslProvider.JDK).build();
    }

    @After
    public void releaseBuffers() {
        if (relay != null) {
            relay.finish();
        }
        if (tlsServer != null) {
            tlsServer.finishAndReleaseAll();
        }
    }

    private EmbeddedChannel relay(boolean connectAtOnce) {
        relay = new BinaryRelayHarness(connectAtOnce);
        when(relay.httpClient.newBinaryRelaySslHandler(any(ByteBufAllocator.class), any(InetSocketAddress.class), any())).thenAnswer(invocation -> {
            SslHandler sslHandler = forwardClient.newHandler(invocation.getArgument(0), "127.0.0.1", 1234);
            sslHandler.setHandshakeTimeoutMillis(HANDSHAKE_TIMEOUT_MILLIS);
            return sslHandler;
        });
        tlsServer = new EmbeddedChannel(upstreamServer.newHandler(ByteBufAllocator.DEFAULT), new SimpleChannelInboundHandler<ByteBuf>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf decrypted) {
                decryptedByUpstream.append(decrypted.toString(StandardCharsets.UTF_8));
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
                if (event instanceof SslCloseCompletionEvent) {
                    closeEventsAtUpstream.add(event);
                }
            }
        });
        return relay.clientConnection();
    }

    /** As MockServer's pipeline does when the client's handshake begins; the event is what the SNI handler fires. */
    private static void clientTurnsTlsOn(EmbeddedChannel client, boolean withEvent) {
        PortUnificationHandler.enableSslUpstreamAndDownstream(client);
        if (withEvent) {
            client.pipeline().fireUserEventTriggered(new SniCompletionEvent("localhost"));
        }
    }

    /** Carries bytes between the relay's upstream connection and the TLS server until neither has more to send. */
    private void exchangeWithUpstream() {
        boolean moved = true;
        for (int round = 0; moved && round < 100; round++) {
            moved = false;
            relay.upstream.runPendingTasks();
            tlsServer.runPendingTasks();
            for (Object written; (written = relay.upstream.readOutbound()) != null; moved = true) {
                tlsServer.writeInbound(written);
            }
            for (Object written; (written = tlsServer.readOutbound()) != null; moved = true) {
                if (relay.upstream.isOpen()) {
                    relay.upstream.writeInbound(written);
                } else {
                    ReferenceCountUtil.release(written);
                }
            }
        }
        relay.client.runPendingTasks();
    }

    private void upstreamSendsOverTls(String message) {
        tlsServer.writeAndFlush(Unpooled.copiedBuffer(message, StandardCharsets.UTF_8));
        exchangeWithUpstream();
    }

    private static int holds(EmbeddedChannel channel) {
        return ChannelReadPause.holds(channel);
    }

    @Test
    public void shouldUpgradeTheUpstreamConnectionWhenTheClientTurnsTlsOn() {
        EmbeddedChannel client = relay(true);
        relay.clientSends("SSLRequest");
        assertThat("sent in the clear", relay.receivedByUpstream(), is("SSLRequest"));
        relay.upstreamSends("S");
        assertThat("the go-ahead is relayed", relay.receivedByClient(), is("S"));

        clientTurnsTlsOn(client, true);

        assertThat("the upstream handshake starts at once", relay.upstream.pipeline().first(), instanceOf(SslHandler.class));
        assertThat("the client is read until it sends over TLS", holds(client), is(0));
        relay.clientSends("startup");
        assertThat("and then held while the upstream handshake runs", holds(client), is(1));
        assertThat(BinaryRelayHarness.isReading(client), is(false));

        exchangeWithUpstream();

        assertThat("read again once it has succeeded", holds(client), is(0));
        assertThat("what was held went over TLS", decryptedByUpstream.toString(), is("startup"));
        upstreamSendsOverTls("ready");
        assertThat("and the upstream's answer is decrypted for the client", relay.receivedByClient(), is("ready"));
        relay.clientSends("query");
        assertThat("not held once the handshake is done", holds(client), is(0));
        exchangeWithUpstream();
        assertThat(decryptedByUpstream.toString(), is("startupquery"));

        assertThat("all of it on one upstream connection", relay.upstreamConnections, is(1));
        assertThat(relay.logged(Level.WARN), is(empty()));
    }

    @Test
    public void shouldUpgradeAtTheFirstMessageOverTlsWhenNoEventArrives() {
        EmbeddedChannel client = relay(true);
        relay.clientSends("SSLRequest");
        relay.receivedByUpstream();

        clientTurnsTlsOn(client, false);
        assertThat(relay.upstream.pipeline().get(SslHandler.class), is(nullValue()));
        relay.clientSends("startup");

        assertThat(relay.upstream.pipeline().first(), instanceOf(SslHandler.class));
        assertThat(holds(client), is(1));
        exchangeWithUpstream();
        assertThat(holds(client), is(0));
        assertThat(decryptedByUpstream.toString(), is("startup"));
        assertThat(relay.upstreamConnections, is(1));
    }

    @Test
    public void shouldSendInTheClearWhatWasWaitingForTheConnectAndOverTlsWhatCameAfterTheUpgrade() {
        EmbeddedChannel client = relay(false);
        relay.clientSends("SSLRequest");
        clientTurnsTlsOn(client, true);
        relay.clientSends("startup");
        assertThat("no handshake before the connection is made", relay.upstream.pipeline().get(SslHandler.class), is(nullValue()));

        relay.connect.setSuccess();
        relay.upstream.runPendingTasks();

        ByteBuf first = relay.upstream.readOutbound();
        assertThat("first, in the clear, what came before the upgrade", first.toString(StandardCharsets.UTF_8), is("SSLRequest"));
        first.release();
        assertThat(relay.upstream.pipeline().first(), instanceOf(SslHandler.class));
        exchangeWithUpstream();
        assertThat("then over TLS what came after it", decryptedByUpstream.toString(), is("startup"));
        assertThat(holds(client), is(0));
    }

    @Test
    public void shouldCloseBothConnectionsAndSaySoOnceWhenTheUpstreamDoesNotAnswerTheHandshakeWithTls() {
        EmbeddedChannel client = relay(true);
        relay.clientSends("SSLRequest");
        relay.receivedByUpstream();
        clientTurnsTlsOn(client, true);
        relay.clientSends("startup");
        ReferenceCountUtil.release(relay.upstream.readOutbound());

        // an upstream that refused the upgrade answers in the clear
        relay.upstreamSends("N and then some bytes that are not a TLS record");
        relay.client.runPendingTasks();

        assertThat(relay.upstream.isOpen(), is(false));
        assertThat(client.isOpen(), is(false));
        assertThat(holds(client), is(0));
        assertThat("nothing it sent reaches the client", relay.receivedByClient(), is(""));
        List<LogEntry> warnings = relay.logged(Level.WARN);
        assertThat("logged once, by the relay", warnings, hasSize(1));
        assertThat(warnings.get(0).getMessageFormat(), containsString("unable to start TLS with upstream"));
        assertThat(warnings.get(0).getThrowable(), is(notNullValue()));
        assertThat("what the upstream sent is counted, not dumped", String.valueOf(warnings.get(0).getArguments()[2]), not(containsString("4e20616e64")));
    }

    @Test
    public void shouldCloseBothConnectionsWhenTheUpstreamHandshakeTimesOut() {
        EmbeddedChannel client = relay(true);
        relay.clientSends("SSLRequest");
        relay.receivedByUpstream();
        clientTurnsTlsOn(client, true);
        relay.clientSends("startup");
        assertThat(holds(client), is(1));

        relay.upstream.advanceTimeBy(HANDSHAKE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        relay.upstream.runScheduledPendingTasks();
        relay.client.runPendingTasks();

        assertThat(relay.upstream.isOpen(), is(false));
        assertThat(client.isOpen(), is(false));
        assertThat(holds(client), is(0));
        assertThat(relay.logged(Level.WARN), hasSize(1));
        assertThat(relay.logged(Level.WARN).get(0).getMessageFormat(), containsString("unable to start TLS with upstream"));
    }

    @Test
    public void shouldCloseBothConnectionsWhenTheUpstreamTlsHandlerCannotBeMade() {
        EmbeddedChannel client = relay(true);
        when(relay.httpClient.newBinaryRelaySslHandler(any(ByteBufAllocator.class), any(InetSocketAddress.class), any())).thenThrow(new IllegalStateException("no TLS context"));
        relay.clientSends("SSLRequest");
        relay.receivedByUpstream();

        clientTurnsTlsOn(client, true);
        relay.client.runPendingTasks();

        assertThat(relay.upstream.isOpen(), is(false));
        assertThat(client.isOpen(), is(false));
        assertThat(relay.logged(Level.WARN), hasSize(1));
        assertThat(relay.logged(Level.WARN).get(0).getArguments()[2], is("no TLS context"));
    }

    @Test
    public void shouldDeliverWhatTheClientSentAndThenEndTheUpstreamWhenTheClientClosesDuringTheHandshake() {
        EmbeddedChannel client = relay(true);
        relay.clientSends("SSLRequest");
        relay.receivedByUpstream();
        clientTurnsTlsOn(client, true);
        relay.clientSends("terminate");
        assertThat(holds(client), is(1));

        client.close();

        assertThat("the hold is given up", holds(client), is(0));
        assertThat("the upstream connection waits for its handshake", relay.upstream.isOpen(), is(true));
        exchangeWithUpstream();
        assertThat("then has what the client sent", decryptedByUpstream.toString(), is("terminate"));
        assertThat("and is ended", relay.upstream.isOpen(), is(false));
        assertThat(relay.logged(Level.WARN), is(empty()));
    }

    @Test
    public void shouldNotCallItAFaultWhenTheHandshakeFailsAfterTheClientHasGone() {
        EmbeddedChannel client = relay(true);
        relay.clientSends("SSLRequest");
        relay.receivedByUpstream();
        clientTurnsTlsOn(client, true);
        relay.clientSends("terminate");
        client.close();
        ReferenceCountUtil.release(relay.upstream.readOutbound());

        relay.upstreamSends("not a TLS record");

        assertThat(relay.upstream.isOpen(), is(false));
        assertThat(holds(client), is(0));
        assertThat(relay.logged(Level.WARN), is(empty()));
    }

    /** A client whose connection is TLS from its first byte: its first decrypted message is what opens the relay. */
    private EmbeddedChannel clientStartedWithTls(boolean connectAtOnce) {
        EmbeddedChannel client = relay(connectAtOnce);
        PortUnificationHandler.enableSslUpstreamAndDownstream(client);
        return client;
    }

    @Test
    public void shouldRelayAConnectionThatStartedWithTlsOnOneUpstreamConnectionThatIsTlsFromItsFirstByte() {
        EmbeddedChannel client = clientStartedWithTls(false);

        relay.clientSends("startup");

        assertThat("the handshake is set up before the connection is made", relay.upstream.pipeline().first(), instanceOf(SslHandler.class));
        assertThat("not read while connecting and handshaking", holds(client), is(2));
        relay.connect.setSuccess();
        relay.upstream.runPendingTasks();
        ByteBuf first = relay.upstream.readOutbound();
        assertThat("the first byte upstream is a TLS handshake record", first.getUnsignedByte(first.readerIndex()), is((short) 0x16));
        tlsServer.writeInbound(first);
        exchangeWithUpstream();

        assertThat("read again once the handshake has succeeded", holds(client), is(0));
        assertThat("nothing went in the clear: the server decrypted it all", decryptedByUpstream.toString(), is("startup"));
        upstreamSendsOverTls("ready");
        assertThat(relay.receivedByClient(), is("ready"));
        relay.clientSends("query");
        assertThat(holds(client), is(0));
        exchangeWithUpstream();
        assertThat(decryptedByUpstream.toString(), is("startupquery"));

        assertThat(relay.upstreamConnections, is(1));
        assertThat(relay.logged(Level.WARN), is(empty()));
        verify(relay.httpClient).connectBinaryRelay(any(EventLoop.class), eq(BinaryRelayHarness.TARGET), eq(true), any(ChannelHandler.class));
        client.checkException();
    }

    @Test
    public void shouldNameTheUpstreamAsTheClientNamedMockServer() {
        EmbeddedChannel client = clientStartedWithTls(true);
        client.attr(SniHandler.SNI_HOSTNAME).set("db.example.com");

        relay.clientSends("startup");

        verify(relay.httpClient).newBinaryRelaySslHandler(any(ByteBufAllocator.class), eq(BinaryRelayHarness.TARGET), eq("db.example.com"));
    }

    @Test
    public void shouldOpenNoConnectionWhenTheTlsHandlerOfAConnectionThatStartedWithTlsCannotBeMade() {
        EmbeddedChannel client = clientStartedWithTls(true);
        when(relay.httpClient.newBinaryRelaySslHandler(any(ByteBufAllocator.class), any(InetSocketAddress.class), any())).thenThrow(new IllegalStateException("no TLS context"));

        relay.clientSends("startup");

        assertThat("nothing is connected to send in the clear", relay.upstreamConnections, is(0));
        assertThat(client.isOpen(), is(false));
        assertThat(holds(client), is(0));
        assertThat(relay.logged(Level.WARN), hasSize(1));
        assertThat(relay.logged(Level.WARN).get(0).getArguments()[2], is("no TLS context"));
    }

    @Test
    public void shouldSayOnlyOnceThatAConnectionThatStartedWithTlsCouldNotBeConnected() {
        EmbeddedChannel client = clientStartedWithTls(false);
        relay.clientSends("startup");
        // the client is still open when the closed channel's handshake fails, as it can be over a real socket
        relay.clientFlushGate.blocked = true;

        relay.connect.setFailure(new ConnectException("refused"));
        relay.upstream.runPendingTasks();
        relay.client.runPendingTasks();
        relay.clientFlushGate.blocked = false;
        client.flush();
        client.runPendingTasks();

        assertThat(client.isOpen(), is(false));
        assertThat(holds(client), is(0));
        List<LogEntry> warnings = relay.logged(Level.WARN);
        assertThat("the failed connect, and not its handshake as well", warnings, hasSize(1));
        assertThat(warnings.get(0).getMessageFormat(), containsString("unable to connect"));
    }

    @Test
    public void shouldCloseBothConnectionsWhenAnUpstreamOfAConnectionThatStartedWithTlsAnswersInTheClear() {
        EmbeddedChannel client = clientStartedWithTls(true);
        relay.clientSends("startup");
        ReferenceCountUtil.release(relay.upstream.readOutbound());

        relay.upstreamSends("not a TLS record");
        relay.client.runPendingTasks();

        assertThat(relay.upstream.isOpen(), is(false));
        assertThat(client.isOpen(), is(false));
        assertThat(holds(client), is(0));
        assertThat("nothing it sent reaches the client", relay.receivedByClient(), is(""));
        assertThat(relay.logged(Level.WARN), hasSize(1));
        assertThat(relay.logged(Level.WARN).get(0).getMessageFormat(), containsString("unable to start TLS with upstream"));
    }

    @Test
    public void shouldHoldEachConnectionWhileTheOtherCannotTakeMoreWhenItStartedWithTls() {
        EmbeddedChannel client = clientStartedWithTls(true);
        relay.clientSends("startup");
        exchangeWithUpstream();
        assertThat(holds(client), is(0));

        relay.upstreamFlushGate.blocked = true;
        relay.clientSends("a message the upstream cannot take yet");
        assertThat("the client is not read while the upstream cannot take more", holds(client), is(1));
        relay.upstreamFlushGate.blocked = false;
        relay.upstream.flush();
        exchangeWithUpstream();
        assertThat(holds(client), is(0));
        assertThat(decryptedByUpstream.toString(), is("startupa message the upstream cannot take yet"));

        BinaryRelayHarness.setWritable(client, false);
        upstreamSendsOverTls("rows");
        assertThat("the upstream is not read while the client cannot take more", holds(relay.upstream), is(1));
        BinaryRelayHarness.setWritable(client, true);
        assertThat(holds(relay.upstream), is(0));
        assertThat(relay.receivedByClient(), is("rows"));
    }

    @Test
    public void shouldDeliverTheLastMessageThenEndTheUpstreamWhenAClientThatStartedWithTlsCloses() {
        EmbeddedChannel client = clientStartedWithTls(true);
        relay.clientSends("startup");
        exchangeWithUpstream();

        relay.clientSends("terminate");
        client.close();
        exchangeWithUpstream();

        assertThat(decryptedByUpstream.toString(), is("startupterminate"));
        assertThat("the TLS session is ended with a close_notify", closeEventsAtUpstream, contains(SslCloseCompletionEvent.SUCCESS));
        assertThat(relay.upstream.isOpen(), is(false));
        assertThat(relay.logged(Level.WARN), is(empty()));
    }

    @Test
    public void shouldEndAnUpgradedUpstreamSessionWithCloseNotifyWhenTheClientCloses() {
        EmbeddedChannel client = relay(true);
        relay.clientSends("SSLRequest");
        relay.receivedByUpstream();
        clientTurnsTlsOn(client, true);
        relay.clientSends("startup");
        exchangeWithUpstream();
        relay.clientSends("terminate");

        client.close();
        exchangeWithUpstream();

        assertThat(decryptedByUpstream.toString(), is("startupterminate"));
        assertThat("after what the client sent, the TLS session is ended with a close_notify", closeEventsAtUpstream, contains(SslCloseCompletionEvent.SUCCESS));
        assertThat(relay.upstream.isOpen(), is(false));
        assertThat(relay.logged(Level.WARN), is(empty()));
    }

    @Test
    public void shouldCloseTheUpstreamAtTheLingerLimitWhenItsCloseNotifyIsNeverTaken() {
        EmbeddedChannel client = clientStartedWithTls(true);
        relay.clientSends("startup");
        exchangeWithUpstream();
        // nearest the socket, so that it holds back the close_notify as well
        BinaryRelayHarness.FlushGate socketTakesNothing = new BinaryRelayHarness.FlushGate();
        socketTakesNothing.blocked = true;
        relay.upstream.pipeline().addFirst(socketTakesNothing);
        // only advanceTimeBy moves the clock, so the limit cannot pass in real time between the steps
        relay.upstream.freezeTime();

        client.close();
        relay.upstream.runPendingTasks();
        relay.upstream.advanceTimeBy(LingeringClose.LINGER_MILLIS - 1, TimeUnit.MILLISECONDS);
        relay.upstream.runScheduledPendingTasks();
        assertThat("the upstream connection is kept for the linger limit", relay.upstream.isOpen(), is(true));
        relay.upstream.advanceTimeBy(1, TimeUnit.MILLISECONDS);
        relay.upstream.runScheduledPendingTasks();

        assertThat("and closed then, though the close_notify was never flushed", relay.upstream.isOpen(), is(false));
    }

    @Test
    public void shouldAddTheUpstreamTlsHandlerAfterTheTunnelsWhenTheConnectionGoesThroughOne() {
        EmbeddedChannel client = relay(false);
        relay.clientSends("SSLRequest");
        relay.upstream.pipeline().addFirst(NettyHttpClient.BINARY_RELAY_TUNNEL, new ChannelDuplexHandler());
        relay.connect.setSuccess();
        relay.receivedByUpstream();

        clientTurnsTlsOn(client, true);

        assertThat("nearest the socket after the tunnel's handler", relay.upstream.pipeline().names().get(0), is(NettyHttpClient.BINARY_RELAY_TUNNEL));
        assertThat(relay.upstream.pipeline().names().get(1), is("binary-relay-tls"));
        assertThat(relay.upstream.pipeline().get("binary-relay-tls"), instanceOf(SslHandler.class));
        client.checkException();
    }

    @Test
    public void shouldCloseAClientThatStartedWithTlsWhenItsUpstreamCloses() {
        EmbeddedChannel client = clientStartedWithTls(true);
        relay.clientSends("startup");
        exchangeWithUpstream();
        upstreamSendsOverTls("goodbye");

        relay.upstream.close();
        relay.client.runPendingTasks();

        assertThat("what it sent first is delivered", relay.receivedByClient(), is("goodbye"));
        assertThat(client.isOpen(), is(false));
    }

    @Test
    public void shouldReleaseTheTlsHandlerOfAConnectionThatStartedWithTlsWhenItsConnectFailsBeforeAChannelIsRegistered() {
        EmbeddedChannel client = clientStartedWithTls(true);
        SSLEngine engine = mock(SSLEngine.class, withSettings().extraInterfaces(ReferenceCounted.class));
        when(relay.httpClient.newBinaryRelaySslHandler(any(ByteBufAllocator.class), any(InetSocketAddress.class), any())).thenReturn(new SslHandler(engine));
        relay.connectFailsBeforeRegistration(new ChannelException("too many open files"), true);

        relay.clientSends("startup");
        client.runPendingTasks();

        verify((ReferenceCounted) engine).release();
        assertThat("never added to a pipeline that will not run", relay.upstream.pipeline().get(SslHandler.class), is(nullValue()));
        assertThat(client.isOpen(), is(false));
        assertThat(holds(client), is(0));
        assertThat(relay.logged(Level.WARN), hasSize(1));
    }
}
