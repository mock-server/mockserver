package org.mockserver.netty.proxy;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.model.HttpResponse.response;

/**
 * A binary message's received-request entry names the expectation that answered it, so it can be verified by that
 * expectation's id, and is logged even when matching it fails.
 */
public class BinaryRequestProxyingHandlerReceivedRequestTest {

    private static final byte[] MESSAGE = "message".getBytes(StandardCharsets.UTF_8);

    private final MockServerLogger logger = mock(MockServerLogger.class);
    private final HttpState httpState = mock(HttpState.class);
    private final EmbeddedChannel channel = new EmbeddedChannel();

    @Before
    public void handler() {
        when(logger.isEnabledForInstance(any(Level.class))).thenReturn(true);
        channel.pipeline().addLast(new BinaryRequestProxyingHandler(configuration(), logger, mock(Scheduler.class), mock(NettyHttpClient.class), httpState));
    }

    @After
    public void releaseChannel() {
        channel.finishAndReleaseAll();
    }

    private List<LogEntry> receivedRequestEntries() {
        ArgumentCaptor<LogEntry> entries = ArgumentCaptor.forClass(LogEntry.class);
        verify(logger, atLeastOnce()).logEvent(entries.capture());
        return entries.getAllValues().stream().filter(entry -> entry.getType() == RECEIVED_REQUEST).collect(Collectors.toList());
    }

    @Test
    public void namesTheExpectationThatAnsweredTheMessage() {
        when(httpState.firstMatchingExpectation(any(BinaryRequestDefinition.class)))
            .thenReturn(new Expectation(binaryRequest(MESSAGE)).withId("answers").thenRespondWithBinary(binaryResponse("reply".getBytes(StandardCharsets.UTF_8))));

        channel.writeInbound(Unpooled.copiedBuffer(MESSAGE));

        assertThat(receivedRequestEntries().stream().map(LogEntry::getExpectationId).collect(Collectors.toList()), contains("answers"));
    }

    @Test
    public void namesNoExpectationForAMatchWithoutABinaryResponse() {
        when(httpState.firstMatchingExpectation(any(BinaryRequestDefinition.class)))
            .thenReturn(new Expectation(binaryRequest(MESSAGE)).withId("no-binary-response").thenRespond(response()));

        channel.writeInbound(Unpooled.copiedBuffer(MESSAGE));

        List<LogEntry> received = receivedRequestEntries();
        assertThat(received, hasSize(1));
        assertThat(received.get(0).getExpectationId(), is(nullValue()));
    }

    @Test
    public void logsTheMessageWhenMatchingItFails() {
        when(httpState.firstMatchingExpectation(any(BinaryRequestDefinition.class))).thenThrow(new IllegalStateException("matcher failed"));

        channel.writeInbound(Unpooled.copiedBuffer(MESSAGE));

        assertThat("matching failed, which closes the connection", channel.isOpen(), is(false));
        List<LogEntry> received = receivedRequestEntries();
        assertThat(received, hasSize(1));
        assertThat(((BinaryRequestDefinition) received.get(0).getHttpRequest()).getBinaryData(), is(MESSAGE));
    }
}
