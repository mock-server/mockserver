package org.mockserver.netty.proxy.relay;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.model.BinaryMessage;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.netty.proxy.BinaryRequestProxyingHandler;
import org.mockserver.netty.unification.PortUnificationHandler;
import org.mockserver.socket.ChannelReadPause;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.proxy.relay.BinaryRelayHarness.TARGET;
import static org.mockserver.netty.proxy.relay.BinaryRelayHarness.isReading;
import static org.mockserver.netty.proxy.relay.BinaryRelayHarness.setWritable;

/**
 * With {@code forwardBinaryRequestsMatchExpectations} a message on a relayed binary connection that matches a binary
 * expectation is answered by MockServer and never reaches the upstream; every other message is relayed.
 */
public class BinaryRelayAnsweredLocallyTest {

    private static final String CANNED = "canned";

    private final HttpState httpState = mock(HttpState.class);
    private final Expectation mocked = new Expectation(binaryRequest(bytes("mocked"))).thenRespondWithBinary(binaryResponse(bytes(CANNED)));
    private final Expectation noReply = new Expectation(binaryRequest(bytes("no reply"))).thenRespondWithBinary(binaryResponse(new byte[0]));
    private final Expectation notBinary = new Expectation(binaryRequest(bytes("http"))).thenRespond(response());
    private final Expectation delayed = new Expectation(binaryRequest(bytes("slow"))).thenRespondWithBinary(binaryResponse(bytes("SLOW")).withDelay(MILLISECONDS, 500));
    private final List<String> reported = new ArrayList<>();
    private final List<CompletableFuture<BinaryMessage>> responses = new ArrayList<>();
    private BinaryRelayHarness relay;

    @Before
    public void expectations() {
        when(httpState.mayMatchBinaryMessage(any(byte[].class))).thenReturn(true);
        when(httpState.firstMatchingExpectation(any(BinaryRequestDefinition.class))).thenAnswer(invocation -> {
            String received = new String(invocation.<BinaryRequestDefinition>getArgument(0).getBinaryData(), StandardCharsets.UTF_8);
            for (Expectation expectation : new Expectation[]{mocked, noReply, notBinary, delayed}) {
                if (received.equals(new String(((BinaryRequestDefinition) expectation.getHttpRequest()).getBinaryData(), StandardCharsets.UTF_8))) {
                    return expectation;
                }
            }
            return null;
        });
        relay = new BinaryRelayHarness(true);
        relay.configuration.forwardBinaryRequestsMatchExpectations(true);
    }

    @After
    public void releaseBuffers() {
        relay.finish();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** As {@link BinaryRelayHarness#clientConnection()}, with this test's expectations. */
    private EmbeddedChannel clientConnection() {
        relay.client = new EmbeddedChannel();
        relay.client.attr(REMOTE_SOCKET).set(TARGET);
        relay.client.pipeline().addLast(relay.clientFlushGate);
        relay.client.pipeline().addLast(new BinaryRequestProxyingHandler(relay.configuration, relay.mockServerLogger, relay.scheduler, relay.httpClient, httpState));
        return relay.client;
    }

    private List<String> warnings() {
        return relay.logged(Level.WARN).stream().map(LogEntry::getMessageFormat).collect(Collectors.toList());
    }

    @Test
    public void shouldAnswerAMatchedMessageAndRelayTheRest() {
        clientConnection();

        relay.clientSends("one");
        relay.clientSends("mocked");
        relay.clientSends("two");

        assertThat(relay.receivedByClient(), is(CANNED));
        assertThat(relay.receivedByUpstream(), is("onetwo"));
        assertThat(relay.upstreamConnections, is(1));
        verify(httpState).postProcess(mocked);
    }

    @Test
    public void shouldOpenNoUpstreamConnectionWhileEveryMessageIsAnswered() {
        clientConnection();

        relay.clientSends("mocked");
        relay.clientSends("mocked");

        assertThat(relay.receivedByClient(), is(CANNED + CANNED));
        assertThat(relay.upstreamConnections, is(0));
        assertThat(relay.forwardedPerMessage, is(empty()));
    }

    @Test
    public void shouldSwallowAMessageWhoseResponseIsEmpty() {
        clientConnection();

        relay.clientSends("one");
        relay.clientSends("no reply");

        assertThat(relay.receivedByUpstream(), is("one"));
        assertThat(relay.receivedByClient(), is(""));
        assertThat("nothing written can overtake the upstream's answer", warnings(), is(empty()));
        verify(httpState).postProcess(noReply);
    }

    @Test
    public void shouldMakeNoMatcherPassWhenNoExpectationCanMatchTheMessage() {
        when(httpState.mayMatchBinaryMessage(any(byte[].class))).thenReturn(false);
        clientConnection();

        relay.clientSends("mocked");

        assertThat(relay.receivedByUpstream(), is("mocked"));
        verify(httpState, never()).firstMatchingExpectation(any());
    }

    @Test
    public void shouldNotConsultExpectationsWhenTheSettingIsOff() {
        relay.configuration.forwardBinaryRequestsMatchExpectations(false);
        clientConnection();

        relay.clientSends("mocked");

        assertThat(relay.receivedByUpstream(), is("mocked"));
        assertThat(relay.receivedByClient(), is(""));
        verify(httpState, never()).mayMatchBinaryMessage(any());
        verify(httpState, never()).firstMatchingExpectation(any());
    }

    @Test
    public void shouldWarnOnlyWhenALocalReplyMayOvertakeTheAnswerToAForwardedMessage() {
        clientConnection();

        relay.clientSends("mocked");
        assertThat("nothing was forwarded", warnings(), is(empty()));

        relay.clientSends("one");
        relay.clientSends("mocked");
        assertThat(warnings(), hasSize(1));
        assertThat(warnings().get(0), containsString("may reach the client out of order"));
        assertThat(String.valueOf(relay.logged(Level.WARN).get(0).getArguments()[1]), containsString("6f6e65"));

        relay.upstreamSends("answer");
        relay.clientSends("mocked");
        assertThat("the forwarded message has been answered", warnings(), hasSize(1));
        assertThat(relay.receivedByClient(), is(CANNED + CANNED + "answer" + CANNED));
    }

    @Test
    public void shouldWarnOncePerForwardedMessageThatGetsNoAnswer() {
        clientConnection();

        relay.clientSends("fire and forget");
        relay.clientSends("mocked");
        relay.clientSends("mocked");
        relay.clientSends("mocked");
        assertThat("one forwarded message, however many replies follow it", warnings(), hasSize(1));

        relay.clientSends("another");
        relay.clientSends("mocked");
        assertThat("a later forwarded message is warned about too", warnings(), hasSize(2));
    }

    @Test
    public void shouldWriteADelayedLocalReplyOnceItsDelayHasPassedAndCheckItsOrderThen() {
        EmbeddedChannel client = clientConnection();
        client.freezeTime();

        relay.clientSends("slow");
        relay.clientSends("one");
        assertThat("not before its delay", relay.receivedByClient(), is(""));
        assertThat(relay.receivedByUpstream(), is("one"));

        client.advanceTimeBy(500, MILLISECONDS);
        client.runScheduledPendingTasks();

        assertThat(relay.receivedByClient(), is("SLOW"));
        assertThat("written while the message forwarded after it has no answer", warnings(), hasSize(1));
        assertThat(relay.upstreamConnections, is(1));
    }

    @Test
    public void shouldNotReadTheClientWhileItCannotTakeALocalReply() {
        EmbeddedChannel client = clientConnection();
        setWritable(client, false);

        relay.clientSends("mocked");
        assertThat(isReading(client), is(false));

        setWritable(client, true);
        assertThat(isReading(client), is(true));
    }

    @Test
    public void shouldGiveUpTheHoldWhenTheClientCloses() {
        EmbeddedChannel client = clientConnection();
        setWritable(client, false);
        relay.clientSends("mocked");
        assertThat(ChannelReadPause.holds(client), is(1));

        client.close();
        client.runPendingTasks();

        assertThat(ChannelReadPause.holds(client), is(0));
    }

    @Test
    public void shouldForwardAMessageWhoseExpectationHasNoBinaryResponseAndSaySo() {
        clientConnection();

        relay.clientSends("http");

        assertThat(relay.receivedByUpstream(), is("http"));
        assertThat(relay.receivedByClient(), is(""));
        assertThat(warnings(), hasSize(1));
        assertThat(warnings().get(0), containsString("has no binary response"));
        verify(httpState).postProcess(notBinary);
    }

    @Test
    public void shouldNotTellTheListenerOfALocallyAnsweredMessage() throws Exception {
        relay.configuration.binaryProxyListener((binaryRequest, binaryResponse, serverAddress, clientAddress) -> {
            reported.add(new String(binaryRequest.getBytes(), StandardCharsets.UTF_8));
            responses.add(binaryResponse);
        });
        clientConnection();

        relay.clientSends("one");
        relay.clientSends("mocked");
        relay.upstreamSends("answer");
        while (!relay.listenerCalls.isEmpty()) {
            relay.runNextListenerCall();
        }

        assertThat(reported, contains("one"));
        assertThat("the forwarded message is still answered by the upstream", new String(responses.get(0).get().getBytes(), StandardCharsets.UTF_8), is("answer"));
    }

    @Test
    public void shouldWarnOncePerConnectionAndForwardWhenEachMessageHasAConnectionOfItsOwn() {
        relay.configuration.forwardBinaryRequestsUseSingleConnection(false);
        clientConnection();

        relay.clientSends("mocked");
        relay.clientSends("mocked");

        assertThat(relay.forwardedPerMessage, contains("mocked waiting", "mocked waiting"));
        assertThat(warnings(), hasSize(1));
        assertThat(warnings().get(0), containsString("binary expectations are not matched on binary connection"));
        verify(httpState, never()).firstMatchingExpectation(any());
    }

    @Test
    public void shouldNotMatchOnAConnectionHandedBackToPerMessageForwarding() {
        when(relay.httpClient.binaryRelayUnavailableBecause(any(InetSocketAddress.class), anyBoolean())).thenReturn("its upstream proxy is forwardHttpProxy, which does not tunnel a connection");
        clientConnection();

        relay.clientSends("mocked");

        assertThat(relay.forwardedPerMessage, contains("mocked waiting"));
        assertThat(warnings(), hasSize(1));
        verify(httpState, never()).firstMatchingExpectation(any());
    }

    @Test
    public void shouldAnswerAMatchedMessageOfAConnectionThatStartedWithTlsAndRelayTheRest() {
        when(relay.httpClient.newBinaryRelaySslHandler(any(ByteBufAllocator.class), any(InetSocketAddress.class), any())).thenAnswer(invocation ->
            SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).sslProvider(SslProvider.JDK).build().newHandler(invocation.getArgument(0)));
        EmbeddedChannel client = clientConnection();
        PortUnificationHandler.enableSslUpstreamAndDownstream(client);

        relay.clientSends("mocked");
        assertThat(relay.receivedByClient(), is(CANNED));
        assertThat("no upstream connection for a message answered here", relay.upstreamConnections, is(0));
        relay.clientSends("one");

        assertThat(relay.upstreamConnections, is(1));
        assertThat("relayed, with TLS from the start", relay.upstream.pipeline().first(), instanceOf(SslHandler.class));
        assertThat(relay.forwardedPerMessage, is(empty()));
        assertThat(warnings(), is(empty()));
        verify(httpState).postProcess(mocked);
    }
}
