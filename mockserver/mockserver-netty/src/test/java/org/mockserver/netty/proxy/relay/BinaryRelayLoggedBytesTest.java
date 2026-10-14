package org.mockserver.netty.proxy.relay;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Test;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.netty.proxy.BinaryRequestProxyingHandler;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.proxy.relay.BinaryRelayHarness.TARGET;

/**
 * On a connection relayed on one upstream connection, each log entry that shows a binary message shows at most its
 * first maxLoggedBodyBytes bytes and its whole length.
 */
public class BinaryRelayLoggedBytesTest {

    private static final int LENGTH = 1_000;
    private static final int LOGGED = 16;
    private static final byte[] REQUEST = filled('A');
    private static final byte[] RESPONSE = filled('B');
    private static final byte[] MOCKED = "mocked".getBytes();

    private final HttpState httpState = mock(HttpState.class);
    private final BinaryRelayHarness relay = new BinaryRelayHarness(true);

    private static byte[] filled(char character) {
        byte[] bytes = new byte[LENGTH];
        Arrays.fill(bytes, (byte) character);
        return bytes;
    }

    @After
    public void releaseBuffers() {
        relay.finish();
    }

    /** As {@link BinaryRelayHarness#clientConnection()}, with this test's expectations. */
    private void clientConnection() {
        relay.configuration.maxLoggedBodyBytes(LOGGED);
        relay.client = new EmbeddedChannel();
        relay.client.attr(REMOTE_SOCKET).set(TARGET);
        relay.client.pipeline().addLast(relay.clientFlushGate);
        relay.client.pipeline().addLast(new BinaryRequestProxyingHandler(relay.configuration, relay.mockServerLogger, relay.scheduler, relay.httpClient, httpState));
    }

    private void expectations(Expectation... expectations) {
        relay.configuration.matchBinaryExpectationsWhenProxying(true);
        when(httpState.mayMatchBinaryMessage(any(byte[].class))).thenReturn(true);
        when(httpState.firstMatchingExpectation(any(BinaryRequestDefinition.class))).thenAnswer(invocation -> {
            byte[] received = invocation.<BinaryRequestDefinition>getArgument(0).getBinaryData();
            for (Expectation expectation : expectations) {
                if (Arrays.equals(received, ((BinaryRequestDefinition) expectation.getHttpRequest()).getBinaryData())) {
                    return expectation;
                }
            }
            return null;
        });
    }

    private LogEntry loggedEntry(String messageFormatStart) {
        List<LogEntry> found = relay.logged().stream().filter(entry -> entry.getMessageFormat().startsWith(messageFormatStart)).collect(Collectors.toList());
        assertThat(messageFormatStart, found, hasSize(1));
        return found.get(0);
    }

    private static void assertBounded(LogEntry entry, int messagesShown) {
        String text = entry.getMessage(configuration());
        String leftOut = "...(" + LENGTH + " bytes, only the first " + LOGGED + " logged, maxLoggedBodyBytes)";
        assertThat(text, text.split(Pattern.quote(leftOut), -1).length - 1, is(messagesShown));
        assertThat(text, not(containsString("41".repeat(LOGGED + 1))));
        assertThat(text, not(containsString("42".repeat(LOGGED + 1))));
    }

    @Test
    public void shouldBoundARelayedMessageAndItsResponse() {
        clientConnection();
        relay.client.writeInbound(Unpooled.copiedBuffer(REQUEST));

        relay.upstream.writeInbound(Unpooled.copiedBuffer(RESPONSE));

        assertBounded(loggedEntry("received binary request"), 1);
        assertBounded(loggedEntry("returning binary response"), 2);
    }

    @Test
    public void shouldBoundWhatTheUpstreamSendsUnprompted() {
        clientConnection();
        relay.client.writeInbound(Unpooled.copiedBuffer(REQUEST));
        relay.upstream.writeInbound(Unpooled.copiedBuffer("answer".getBytes()));

        relay.upstream.writeInbound(Unpooled.copiedBuffer(RESPONSE));

        List<LogEntry> returned = relay.logged().stream().filter(entry -> entry.getMessageFormat().equals("returning binary response:{}from:{}")).collect(Collectors.toList());
        assertThat(returned, hasSize(1));
        assertBounded(returned.get(0), 1);
    }

    @Test
    public void shouldBoundTheRelayedMessageAMockResponseMayOvertake() {
        expectations(new Expectation(binaryRequest(MOCKED)).thenRespondWithBinary(binaryResponse("canned".getBytes())));
        clientConnection();
        relay.client.writeInbound(Unpooled.copiedBuffer(REQUEST));

        relay.client.writeInbound(Unpooled.copiedBuffer(MOCKED));

        assertBounded(loggedEntry("binary mock response written"), 1);
    }

    @Test
    public void shouldBoundAMessageForwardedAsItsExpectationHasNoBinaryResponse() {
        expectations(new Expectation(binaryRequest(REQUEST)).thenRespond(response()));
        clientConnection();

        relay.client.writeInbound(Unpooled.copiedBuffer(REQUEST));

        assertBounded(loggedEntry("forwarding binary request"), 1);
    }
}
