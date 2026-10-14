package org.mockserver.netty.proxy.relay;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.model.Not;
import org.mockserver.netty.proxy.BinaryRequestProxyingHandler;
import org.mockserver.scheduler.Scheduler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_MATCHED;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_NOT_MATCHED;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.proxy.relay.BinaryRelayHarness.TARGET;

/**
 * With {@code matchBinaryExpectationsWhenProxying} and a real expectation store, a relayed message that no
 * expectation can match is forwarded without a matcher pass, so it adds no match entries to the log, while a message
 * an expectation may match is matched as before.
 */
public class BinaryRelayMatcherPassTest {

    private final BinaryRelayHarness relay = new BinaryRelayHarness(true);
    private final List<LogEntry.LogMessageType> loggedTypes = new CopyOnWriteArrayList<>();
    private MockServerLogger mockServerLogger;
    private HttpState httpState;

    @Before
    public void expectations() {
        relay.configuration.logLevel("INFO").matchBinaryExpectationsWhenProxying(true);
        mockServerLogger = spy(new MockServerLogger(relay.configuration, BinaryRelayMatcherPassTest.class));
        // read as it is logged: the event log reuses an entry once it has taken it
        doAnswer(invocation -> {
            loggedTypes.add(invocation.<LogEntry>getArgument(0).getType());
            return invocation.callRealMethod();
        }).when(mockServerLogger).logEvent(any(LogEntry.class));
        httpState = new HttpState(relay.configuration, mockServerLogger, mock(Scheduler.class));
        for (int i = 0; i < 3; i++) {
            httpState.add(new Expectation(request().withPath("/http-" + i)).thenRespond(response()));
        }
        httpState.add(new Expectation(binaryRequest(bytes("mocked"))).thenRespondWithBinary(binaryResponse(bytes("canned"))));
        httpState.add(new Expectation(binaryRequest(bytes("other!"))).thenRespondWithBinary(binaryResponse(bytes("OTHER"))));
    }

    @After
    public void releaseBuffers() {
        relay.finish();
        httpState.stop();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private void clientConnection() {
        relay.client = new EmbeddedChannel();
        relay.client.attr(REMOTE_SOCKET).set(TARGET);
        relay.client.pipeline().addLast(relay.clientFlushGate);
        relay.client.pipeline().addLast(new BinaryRequestProxyingHandler(relay.configuration, mockServerLogger, relay.scheduler, relay.httpClient, httpState));
    }

    private List<LogEntry.LogMessageType> logged(LogEntry.LogMessageType type) {
        return loggedTypes.stream().filter(logged -> logged == type).collect(Collectors.toList());
    }

    @Test
    public void shouldForwardAMessageNoExpectationCanMatchWithoutAMatcherPass() {
        clientConnection();

        relay.clientSends("one");
        relay.clientSends("mocked");
        relay.clientSends("mockeD");
        relay.clientSends("two");

        assertThat(relay.receivedByClient(), is("canned"));
        assertThat(relay.receivedByUpstream(), is("onemockeDtwo"));
        assertThat("only the message that has an expectation's bytes is matched", logged(EXPECTATION_MATCHED), hasSize(1));
        assertThat(logged(EXPECTATION_NOT_MATCHED), is(empty()));
    }

    @Test
    public void shouldMatchEveryMessageWhileAnExpectationMayMatchAnyMessage() {
        httpState.add(new Expectation(Not.not(binaryRequest(bytes("forward me")))).thenRespondWithBinary(binaryResponse(bytes("NOT"))));
        clientConnection();

        relay.clientSends("mocked");
        relay.clientSends("one");
        relay.clientSends("forward me");

        assertThat(relay.receivedByClient(), is("canned" + "NOT"));
        assertThat(relay.receivedByUpstream(), is("forward me"));
        assertThat("the message that matched nothing still had its matcher pass", logged(EXPECTATION_NOT_MATCHED), is(not(empty())));
    }
}
