package org.mockserver.netty.proxy.relay;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.netty.proxy.BinaryRequestProxyingHandler;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationTimes;

import java.nio.charset.StandardCharsets;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.model.ExpectationId.expectationId;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.proxy.relay.BinaryRelayHarness.TARGET;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationSequence.verificationSequence;
import static org.mockserver.verify.VerificationTimes.never;
import static org.mockserver.verify.VerificationTimes.once;

/**
 * With {@code forwardBinaryRequestsMatchExpectations}, a relayed message that a binary expectation answers is counted
 * by that expectation's id, at every log level; one an expectation matched but did not answer, as it has no binary
 * response, is forwarded and not counted by the id.
 */
public class BinaryRelayVerifyByIdTest {

    private final BinaryRelayHarness relay = new BinaryRelayHarness(true);
    private HttpState httpState;

    @Before
    public void expectations() {
        relay.configuration.logLevel("WARN").redactSecretsInLog(false).forwardBinaryRequestsMatchExpectations(true);
        MockServerLogger mockServerLogger = new MockServerLogger(relay.configuration, BinaryRelayVerifyByIdTest.class);
        httpState = new HttpState(relay.configuration, mockServerLogger, mock(Scheduler.class));
        httpState.add(new Expectation(binaryRequest(bytes("mocked"))).withId("answers").thenRespondWithBinary(binaryResponse(bytes("canned"))));
        httpState.add(new Expectation(binaryRequest(bytes("second"))).withId("answers-too").thenRespondWithBinary(binaryResponse(bytes("SECOND"))));
        httpState.add(new Expectation(binaryRequest(bytes("no-reply"))).withId("no-binary-response").thenRespond(response()));
        relay.client = new EmbeddedChannel();
        relay.client.attr(REMOTE_SOCKET).set(TARGET);
        relay.client.pipeline().addLast(relay.clientFlushGate);
        relay.client.pipeline().addLast(new BinaryRequestProxyingHandler(relay.configuration, mockServerLogger, relay.scheduler, relay.httpClient, httpState));
    }

    @After
    public void releaseBuffers() {
        relay.finish();
        httpState.stop();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private String verify(String expectationId, VerificationTimes times) throws Exception {
        return verify(verification().withExpectationId(expectationId(expectationId)).withTimes(times));
    }

    private String verify(Verification verification) throws Exception {
        return httpState.verify(verification).get(10, SECONDS);
    }

    @Test
    public void countsARelayedMessageByTheIdOfTheExpectationThatAnsweredIt() throws Exception {
        relay.clientSends("mocked");
        relay.clientSends("no-reply");
        relay.clientSends("other");
        relay.clientSends("second");

        assertThat(relay.receivedByClient(), is("canned" + "SECOND"));
        assertThat(relay.receivedByUpstream(), is("no-reply" + "other"));
        assertThat(verify("answers", once()), is(""));
        assertThat(verify("answers-too", once()), is(""));
        assertThat("matched, but forwarded as it has no binary response", verify("no-binary-response", never()), is(""));
        assertThat("each message is still recorded", verify(verification().withRequest(binaryRequest(bytes("no-reply"))).withTimes(once())), is(""));
        assertThat(verify("answers", never()), containsString("Request found 1 time but should have been found exactly 0 times"));
        assertThat(httpState.verify(verificationSequence().withExpectationIds(expectationId("answers"), expectationId("answers-too"))).get(10, SECONDS), is(""));
        assertThat(httpState.verify(verificationSequence().withExpectationIds(expectationId("answers-too"), expectationId("answers"))).get(10, SECONDS), containsString("Request sequence not found"));
    }
}
