package org.mockserver.netty.proxy;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;

/**
 * Each log entry that shows a mocked binary message shows at most its first maxLoggedBodyBytes bytes and its whole
 * length. A relayed message's entries are covered by {@code BinaryRelayLoggedBytesTest}.
 */
public class BinaryRequestProxyingHandlerLoggedBytesTest {

    private static final int LENGTH = 1_000;
    private static final int LOGGED = 16;
    private static final byte[] REQUEST = filled('A');
    private static final byte[] RESPONSE = filled('B');

    private final NettyHttpClient httpClient = mock(NettyHttpClient.class);
    private final MockServerLogger mockServerLogger = mock(MockServerLogger.class);
    private final HttpState httpState = mock(HttpState.class);
    private EmbeddedChannel channel;

    private static byte[] filled(char character) {
        byte[] bytes = new byte[LENGTH];
        Arrays.fill(bytes, (byte) character);
        return bytes;
    }

    @After
    public void releaseChannel() {
        if (channel != null) {
            channel.finishAndReleaseAll();
        }
    }

    private Configuration bounded() {
        return configuration().maxLoggedBodyBytes(LOGGED);
    }

    private void receive(Configuration configuration) {
        when(mockServerLogger.isEnabledForInstance(any(Level.class))).thenReturn(true);
        channel = new EmbeddedChannel();
        // synchronous: what is scheduled runs on the calling thread
        channel.pipeline().addLast(new BinaryRequestProxyingHandler(configuration, mockServerLogger, new Scheduler(configuration, mockServerLogger, true), httpClient, httpState));
        channel.writeInbound(Unpooled.copiedBuffer(REQUEST));
        channel.runPendingTasks();
    }

    private List<LogEntry> logged() {
        ArgumentCaptor<LogEntry> entries = ArgumentCaptor.forClass(LogEntry.class);
        verify(mockServerLogger, atLeast(0)).logEvent(entries.capture());
        return entries.getAllValues();
    }

    private LogEntry loggedEntry(String messageFormatStart) {
        List<LogEntry> found = logged().stream().filter(entry -> entry.getMessageFormat().startsWith(messageFormatStart)).collect(Collectors.toList());
        assertThat(messageFormatStart, found, hasSize(1));
        return found.get(0);
    }

    /** Every message in the entry is cut to its first bytes, each followed by its whole length. */
    private static void assertBounded(LogEntry entry, int messagesShown) {
        String text = entry.getMessage(configuration());
        String leftOut = "...(" + LENGTH + " bytes, only the first " + LOGGED + " logged, maxLoggedBodyBytes)";
        assertThat(text, text.split(java.util.regex.Pattern.quote(leftOut), -1).length - 1, is(messagesShown));
        for (String notLogged : new String[]{"41".repeat(LOGGED + 1), "42".repeat(LOGGED + 1), "A".repeat(LOGGED + 1), "B".repeat(LOGGED + 1)}) {
            assertThat(text, not(containsString(notLogged)));
        }
    }

    @Test
    public void shouldBoundAMessageThatMatchesNoExpectationInEachEntry() {
        receive(bounded());

        assertBounded(loggedEntry("received binary request"), 1);
        assertBounded(loggedEntry("no matching binary expectation"), 1);
        LogEntry unknownFormat = loggedEntry("unknown message format");
        assertBounded(unknownFormat, 2);
        assertThat("its text too", unknownFormat.getMessage(configuration()), containsString("A".repeat(LOGGED) + "...(" + LENGTH + " bytes"));
    }

    @Test
    public void shouldBoundAMockedMessageAndItsResponse() {
        when(httpState.firstMatchingExpectation(any(BinaryRequestDefinition.class))).thenReturn(new Expectation(binaryRequest(REQUEST)).thenRespondWithBinary(binaryResponse(RESPONSE)));

        receive(bounded());

        assertBounded(loggedEntry("returning binary mock response"), 2);
    }

    @Test
    public void shouldBoundAMockedMessageThatHasNoReply() {
        when(httpState.firstMatchingExpectation(any(BinaryRequestDefinition.class))).thenReturn(new Expectation(binaryRequest(REQUEST)).thenRespondWithBinary(binaryResponse(new byte[0])));

        receive(bounded());

        assertBounded(loggedEntry("returning nothing"), 1);
    }

    @Test
    public void shouldLogEveryByteWithoutALimit() {
        receive(bounded().maxLoggedBodyBytes(0));

        assertThat(loggedEntry("received binary request").getMessage(configuration()), containsString("41".repeat(LENGTH)));
    }

}
