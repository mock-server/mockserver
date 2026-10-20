package org.mockserver.client;

import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.nio.NioEventLoopGroup;
import org.mockserver.client.MockServerEventBus.EventType;
import org.mockserver.closurecallback.websocketclient.WebSocketClient;
import org.mockserver.closurecallback.websocketclient.WebSocketException;
import org.mockserver.closurecallback.websocketregistry.LocalCallbackRegistry;
import org.mockserver.configuration.ClientConfiguration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.*;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.uuid.UUIDService;

import java.util.List;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author jamesdbloom
 */
public class ForwardChainExpectation {

    private final ClientConfiguration configuration;
    private final MockServerLogger mockServerLogger;
    private final MockServerClient mockServerClient;
    private final Expectation expectation;
    private final MockServerEventBus mockServerEventBus;

    ForwardChainExpectation(ClientConfiguration configuration, MockServerLogger mockServerLogger, MockServerEventBus mockServerEventBus, MockServerClient mockServerClient, Expectation expectation) {
        this.configuration = configuration;
        this.mockServerLogger = mockServerLogger;
        this.mockServerEventBus = mockServerEventBus;
        this.mockServerClient = mockServerClient;
        this.expectation = expectation;
    }

    /**
     * <p>
     * Set id of this expectation which can be used to update this expectation
     * later or for clearing or verifying by expectation id.
     * </p>
     * <p>
     * Note: Each unique expectation must have a unique id otherwise this
     * expectation will update a existing expectation with the same id.
     * </p>
     *
     * @param id unique string for expectation's id
     */
    public ForwardChainExpectation withId(String id) {
        expectation.withId(id);
        return this;
    }

    /**
     * <p>
     * Set priority of this expectation which is used to determine the matching
     * order of expectations when a request is received.
     * </p>
     * <p>
     * Matching is ordered by priority (highest first) then creation (earliest first).
     * </p>
     *
     * @param priority expectation's priority
     */
    public ForwardChainExpectation withPriority(int priority) {
        expectation.withPriority(priority);
        return this;
    }

    /**
     * <p>
     * Set percentage of requests this expectation should match (0-100).
     * </p>
     * <p>
     * When set, the expectation will only match the specified percentage of
     * requests that structurally match, enabling probabilistic matching.
     * </p>
     *
     * @param percentage percentage of requests to match (0-100), or null for all requests
     */
    public ForwardChainExpectation withPercentage(Integer percentage) {
        expectation.withPercentage(percentage);
        return this;
    }

    /**
     * <p>
     * Set a declarative HTTP chaos/fault injection profile for this expectation.
     * </p>
     * <p>
     * When set, probabilistic error status injection and/or latency injection
     * is applied to the response returned by the expectation's action.
     * </p>
     *
     * @param chaos the chaos profile to apply, or null to disable chaos injection
     */
    public ForwardChainExpectation withChaos(HttpChaosProfile chaos) {
        expectation.withChaos(chaos);
        return this;
    }

    /**
     * Set the scenario name for stateful expectation matching
     *
     * @param scenarioName the name of the scenario
     */
    public ForwardChainExpectation withScenarioName(String scenarioName) {
        expectation.withScenarioName(scenarioName);
        return this;
    }

    public ForwardChainExpectation withScenarioState(String scenarioState) {
        expectation.withScenarioState(scenarioState);
        return this;
    }

    public ForwardChainExpectation withNewScenarioState(String newScenarioState) {
        expectation.withNewScenarioState(newScenarioState);
        return this;
    }

    /**
     * Set the optional namespace (a.k.a. tenant) that this expectation belongs to,
     * enabling multiple teams or test-suites to share a single MockServer instance
     * without their expectations colliding.
     * <p>
     * A {@code null}, empty or whitespace-only namespace (the default) places the
     * expectation in the global namespace, which is matched regardless of any
     * request namespace. A non-blank namespace scopes the expectation so it only
     * matches requests that carry the configured namespace header with this exact
     * value.
     *
     * @param namespace the namespace (tenant) for this expectation, or null for global
     */
    public ForwardChainExpectation withNamespace(String namespace) {
        expectation.withNamespace(namespace);
        return this;
    }

    /**
     * Set the capture rules that extract values from the matched request into
     * scenario state, so later template responses can read them via the
     * {@code scenario} helper.
     *
     * @param capture the capture rules to apply on match
     */
    public ForwardChainExpectation withCapture(java.util.List<CaptureRule> capture) {
        expectation.withCapture(capture);
        return this;
    }

    /**
     * Set the capture rules that extract values from the matched request into
     * scenario state, so later template responses can read them via the
     * {@code scenario} helper.
     *
     * @param capture the capture rules to apply on match
     */
    public ForwardChainExpectation withCapture(CaptureRule... capture) {
        expectation.withCapture(capture);
        return this;
    }

    public ForwardChainExpectation withResponseMode(org.mockserver.mock.ResponseMode responseMode) {
        expectation.withResponseMode(responseMode);
        return this;
    }

    /**
     * Set a list of responses for stateful / multi-response selection. The list takes priority
     * over a singular response and is selected from according to the expectation's
     * {@link org.mockserver.mock.ResponseMode} (sequential by default).
     *
     * @param httpResponses the list of responses to select from
     */
    public ForwardChainExpectation withHttpResponses(List<HttpResponse> httpResponses) {
        expectation.thenRespond(httpResponses);
        return this;
    }

    /**
     * Set the relative weight of each response in {@code httpResponses} (index-aligned), used when
     * {@link org.mockserver.mock.ResponseMode#WEIGHTED} selection is active.
     *
     * @param responseWeights the index-aligned relative weights
     */
    public ForwardChainExpectation withResponseWeights(List<Integer> responseWeights) {
        expectation.withResponseWeights(responseWeights);
        return this;
    }

    /**
     * Set the number of matches served by each response before advancing to the next, used when
     * {@link org.mockserver.mock.ResponseMode#SWITCH} selection is active (default 1).
     *
     * @param switchAfter the number of matches per response block (must be &gt;= 1)
     */
    public ForwardChainExpectation withSwitchAfter(Integer switchAfter) {
        expectation.withSwitchAfter(switchAfter);
        return this;
    }

    /**
     * Add a single cross-protocol scenario correlation to this expectation.
     *
     * @param crossProtocolScenario the cross-protocol scenario to add
     */
    public ForwardChainExpectation withCrossProtocolScenario(CrossProtocolScenario crossProtocolScenario) {
        expectation.withCrossProtocolScenario(crossProtocolScenario);
        return this;
    }

    /**
     * Set the list of cross-protocol scenario correlations for this expectation.
     *
     * @param crossProtocolScenarios the cross-protocol scenarios
     */
    public ForwardChainExpectation withCrossProtocolScenarios(List<CrossProtocolScenario> crossProtocolScenarios) {
        expectation.withCrossProtocolScenarios(crossProtocolScenarios);
        return this;
    }

    /**
     * Replace this expectation's before-actions with the given list, which run in order before the primary
     * action; an empty or null argument leaves the current list unchanged. To add one action to those
     * already set use {@link #withBeforeAction(AfterAction)}.
     *
     * @param beforeActions the complete list of before-actions
     */
    public ForwardChainExpectation withBeforeActions(AfterAction... beforeActions) {
        expectation.withBeforeActions(beforeActions);
        return this;
    }

    /**
     * Append a before-action to execute before the primary action, after any before-actions already set,
     * so chained calls register every action in order; to replace the whole list use
     * {@link #withBeforeActions(AfterAction...)}. Blocking before-actions (the default) can gate the
     * response via their failure policy and timeout.
     *
     * @param beforeAction the before-action to append
     */
    public ForwardChainExpectation withBeforeAction(AfterAction beforeAction) {
        expectation.withBeforeAction(beforeAction);
        return this;
    }

    /**
     * Replace this expectation's after-actions with the given list, which run after the primary action
     * completes; an empty or null argument leaves the current list unchanged. To add one action to those
     * already set use {@link #withAfterAction(AfterAction)}.
     *
     * @param afterActions the complete list of after-actions
     */
    public ForwardChainExpectation withAfterActions(AfterAction... afterActions) {
        expectation.withAfterActions(afterActions);
        return this;
    }

    /**
     * Set an ordered list of steps for this expectation. Steps provide a unified way to
     * declare an ordered pipeline of side-effects and a single designated responder.
     * Exactly one step must have {@code responder = true}. Steps that precede the
     * responder run before the response; steps that follow run after.
     *
     * <p>When steps are set, they supersede {@code beforeActions} + the primary response
     * action for dispatch ordering. The primary action is still determined by the
     * responder step's action type.</p>
     *
     * @param steps the ordered steps
     */
    public ForwardChainExpectation withSteps(ExpectationStep... steps) {
        expectation.withSteps(steps);
        return this;
    }

    /**
     * Set an ordered list of steps for this expectation.
     *
     * @param steps the ordered steps
     * @see #withSteps(ExpectationStep...)
     */
    public ForwardChainExpectation withSteps(java.util.List<ExpectationStep> steps) {
        expectation.withSteps(steps);
        return this;
    }

    /**
     * Append an after-action to execute after the primary action completes, after any after-actions
     * already set, so chained calls register every action in order; to replace the whole list use
     * {@link #withAfterActions(AfterAction...)}.
     *
     * @param afterAction the after-action to append
     */
    public ForwardChainExpectation withAfterAction(AfterAction afterAction) {
        expectation.withAfterAction(afterAction);
        return this;
    }

    /**
     * Return response when expectation is matched
     *
     * @param httpResponse response to return
     * @return added or updated expectations
     */
    public Expectation[] respond(final HttpResponse httpResponse) {
        expectation.thenRespond(httpResponse);
        return mockServerClient.upsert(expectation);
    }

    /**
     * Set a list of responses to cycle through sequentially when expectation
     * is matched. Each match returns the next response in the list, cycling
     * back to the first after the last.
     *
     * @param httpResponses list of responses to cycle through
     * @return added or updated expectations
     */
    public Expectation[] respond(final List<HttpResponse> httpResponses) {
        expectation.thenRespond(httpResponses);
        return mockServerClient.upsert(expectation);
    }

    /**
     * Evaluate Velocity or JavaScript template to generate response
     * to return when expectation is matched
     *
     * @param httpTemplate Velocity or JavaScript template used to generate response
     * @return added or updated expectations
     */
    public Expectation[] respond(final HttpTemplate httpTemplate) {
        expectation.thenRespond(httpTemplate);
        return mockServerClient.upsert(expectation);
    }

    /**
     * Call method on local class in same JVM implementing ExpectationResponseCallback
     * to generate response to return when expectation is matched
     * <p>
     * The callback class must:
     * - implement org.mockserver.mock.action.ExpectationResponseCallback
     * - have a zero argument constructor
     * - be available in the classpath of the MockServer
     *
     * @param httpClassCallback class to callback as a fully qualified class name, i.e. "com.foo.MyExpectationResponseCallback"
     * @return added or updated expectations
     */
    public Expectation[] respond(final HttpClassCallback httpClassCallback) {
        expectation.thenRespond(httpClassCallback);
        return mockServerClient.upsert(expectation);
    }

    /**
     * Call method on object locally or remotely (over web socket)
     * to generate response to return when expectation is matched
     *
     * @param expectationResponseCallback object to call locally or remotely to generate response
     * @return added or updated expectations
     */
    public Expectation[] respond(final ExpectationResponseCallback expectationResponseCallback) {
        expectation.thenRespond(new HttpObjectCallback().withClientId(registerWebSocketClient(expectationResponseCallback, null)));
        return mockServerClient.upsert(expectation);
    }

    /**
     * Call method on object locally or remotely (over web socket)
     * to generate response to return when expectation is matched
     *
     * @param expectationResponseCallback object to call locally or remotely to generate response
     * @return added or updated expectations
     */
    public Expectation[] respond(final ExpectationResponseCallback expectationResponseCallback, Delay delay) {
        expectation
            .thenRespond(
                new HttpObjectCallback()
                    .withClientId(registerWebSocketClient(expectationResponseCallback, null))
                    .withDelay(delay)
            );
        return mockServerClient.upsert(expectation);
    }

    /**
     * Forward request to the specified host and port when expectation is matched
     *
     * @param httpForward host and port to forward to
     * @return added or updated expectations
     */
    public Expectation[] forward(final HttpForward httpForward) {
        expectation.thenForward(httpForward);
        return mockServerClient.upsert(expectation);
    }

    /**
     * Evaluate Velocity or JavaScript template to generate
     * request to forward when expectation is matched
     *
     * @param httpTemplate Velocity or JavaScript template used to generate response
     * @return added or updated expectations
     */
    public Expectation[] forward(final HttpTemplate httpTemplate) {
        expectation.thenForward(httpTemplate);
        return mockServerClient.upsert(expectation);
    }

    /**
     * Call method on local class in same JVM implementing ExpectationResponseCallback
     * to generate request to forward when expectation is matched
     * <p>
     * The callback class must:
     * - implement org.mockserver.mock.action.ExpectationForwardCallback or org.mockserver.mock.action.ExpectationForwardAndResponseCallback
     * - have a zero argument constructor
     * - be available in the classpath of the MockServer
     *
     * @param httpClassCallback class to callback as a fully qualified class name, i.e. "com.foo.MyExpectationResponseCallback"
     * @return added or updated expectations
     */
    public Expectation[] forward(final HttpClassCallback httpClassCallback) {
        expectation.thenForward(httpClassCallback);
        return mockServerClient.upsert(expectation);
    }

    /**
     * Call method on object locally or remotely (over web socket)
     * to generate request to forward when expectation is matched
     *
     * @param expectationForwardCallback object to call locally or remotely to generate request
     * @return added or updated expectations
     */
    public Expectation[] forward(final ExpectationForwardCallback expectationForwardCallback) {
        expectation
            .thenForward(
                new HttpObjectCallback()
                    .withClientId(registerWebSocketClient(expectationForwardCallback, null))
            );
        return mockServerClient.upsert(expectation);
    }

    /**
     * Call method on object locally or remotely (over web socket)
     * to generate request to forward when expectation is matched
     *
     * @param expectationForwardCallback object to call locally or remotely to generate request
     * @return added or updated expectations
     */
    public Expectation[] forward(final ExpectationForwardCallback expectationForwardCallback, final ExpectationForwardAndResponseCallback expectationForwardResponseCallback) {
        expectation
            .thenForward(
                new HttpObjectCallback()
                    .withResponseCallback(true)
                    .withClientId(registerWebSocketClient(expectationForwardCallback, expectationForwardResponseCallback))
            );
        return mockServerClient.upsert(expectation);
    }

    /**
     * Call method on object locally or remotely (over web socket)
     * to generate request to forward when expectation is matched
     *
     * @param expectationForwardCallback object to call locally or remotely to generate request
     * @return added or updated expectations
     */
    public Expectation[] forward(final ExpectationForwardCallback expectationForwardCallback, final Delay delay) {
        expectation
            .thenForward(
                new HttpObjectCallback()
                    .withClientId(registerWebSocketClient(expectationForwardCallback, null))
                    .withDelay(delay)
            );
        return mockServerClient.upsert(expectation);
    }

    /**
     * Call method on object locally or remotely (over web socket)
     * to generate request to forward when expectation is matched
     *
     * @param expectationForwardCallback object to call locally or remotely to generate request
     * @return added or updated expectations
     */
    public Expectation[] forward(final ExpectationForwardCallback expectationForwardCallback, final ExpectationForwardAndResponseCallback expectationForwardResponseCallback, final Delay delay) {
        expectation
            .thenForward(
                new HttpObjectCallback()
                    .withResponseCallback(true)
                    .withClientId(registerWebSocketClient(expectationForwardCallback, expectationForwardResponseCallback))
                    .withDelay(delay)
            );
        return mockServerClient.upsert(expectation);
    }

    /**
     * Override fields, headers, and cookies etc in request being forwarded with
     * specified fields, headers and cookies, etc in the specified request
     * when expectation is matched
     *
     * @param httpOverrideForwardedRequest contains request to override request being forwarded
     * @return added or updated expectations
     */
    public Expectation[] forward(final HttpOverrideForwardedRequest httpOverrideForwardedRequest) {
        expectation.thenForward(httpOverrideForwardedRequest);
        return mockServerClient.upsert(expectation);
    }

    /**
     * Forward request to the upstream host and return a fallback response when
     * the upstream returns a configured status code (default 5xx) or times out.
     *
     * @param httpForwardWithFallback the forward-with-fallback action
     * @return added or updated expectations
     */
    public Expectation[] forwardWithFallback(final HttpForwardWithFallback httpForwardWithFallback) {
        expectation.thenForwardWithFallback(httpForwardWithFallback);
        return mockServerClient.upsert(expectation);
    }

    /**
     * Return error when expectation is matched
     *
     * @param httpError error to return
     * @return added or updated expectations
     */
    public Expectation[] error(final HttpError httpError) {
        expectation.thenError(httpError);
        return mockServerClient.upsert(expectation);
    }

    public Expectation[] respondWithSse(final HttpSseResponse httpSseResponse) {
        expectation.thenRespondWithSse(httpSseResponse);
        return mockServerClient.upsert(expectation);
    }

    public Expectation[] respondWithLlm(final HttpLlmResponse httpLlmResponse) {
        expectation.thenRespondWithLlm(httpLlmResponse);
        return mockServerClient.upsert(expectation);
    }

    public Expectation[] respondWithWebSocket(final HttpWebSocketResponse httpWebSocketResponse) {
        expectation.thenRespondWithWebSocket(httpWebSocketResponse);
        return mockServerClient.upsert(expectation);
    }

    public Expectation[] respondWithGrpcStream(final GrpcStreamResponse grpcStreamResponse) {
        expectation.thenRespondWithGrpcStream(grpcStreamResponse);
        return mockServerClient.upsert(expectation);
    }

    public Expectation[] respondWithGrpcBidi(final GrpcBidiResponse grpcBidiResponse) {
        expectation.thenRespondWithGrpcBidi(grpcBidiResponse);
        return mockServerClient.upsert(expectation);
    }

    public Expectation[] respondWithBinary(final BinaryResponse binaryResponse) {
        expectation.thenRespondWithBinary(binaryResponse);
        return mockServerClient.upsert(expectation);
    }

    public Expectation[] respondWithDns(final DnsResponse dnsResponse) {
        expectation.thenRespondWithDns(dnsResponse);
        return mockServerClient.upsert(expectation);
    }

    @SuppressWarnings("rawtypes")
    private <T extends HttpMessage> String registerWebSocketClient(ExpectationCallback<T> expectationCallback, ExpectationForwardAndResponseCallback expectationForwardResponseCallback) {
        WebSocketClient<T> webSocketClient = null;
        String clientId = UUIDService.getUUID();
        try {
            LocalCallbackRegistry.registerCallback(clientId, expectationCallback);
            LocalCallbackRegistry.registerCallback(clientId, expectationForwardResponseCallback);
            webSocketClient = new WebSocketClient<>(
                new NioEventLoopGroup(configuration.webSocketClientEventLoopThreadCount(), new Scheduler.SchedulerThreadFactory(WebSocketClient.class.getSimpleName() + "-eventLoop")),
                clientId,
                mockServerLogger,
                () -> mockServerClient.webSocketSslContext()
            );
            webSocketClient.withAuthorization(mockServerClient::webSocketAuthorization);
            final Future<String> register = webSocketClient.registerExpectationCallback(
                expectationCallback,
                expectationForwardResponseCallback,
                mockServerClient.remoteAddress(),
                mockServerClient.contextPath(),
                mockServerClient.isSecure()
            );
            mockServerEventBus.subscribe(webSocketClient::stopClient, EventType.STOP, EventType.RESET);
            return register.get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
        } catch (Exception e) {
            ClientException notRegistered = e.getCause() instanceof WebSocketException
                ? new ClientException(e.getCause().getMessage(), e)
                : new ClientException("Unable to retrieve client registration id", e);
            // no expectation will refer to this id, and its entries take slots that live callbacks need
            LocalCallbackRegistry.unregisterCallback(clientId);
            if (webSocketClient != null) {
                // the caller gets no expectation to use it with, so its event loops must be released now
                try {
                    webSocketClient.stopClient();
                } catch (RuntimeException stopFailure) {
                    notRegistered.addSuppressed(stopFailure);
                }
            }
            throw notRegistered;
        }
    }

    /**
     * Submit the expectation to MockServer without setting any additional primary action.
     * This is the correct terminal call when the expectation's action is already fully
     * defined by its {@code steps} (the responder step defines the action). Using
     * {@code .respond()} or {@code .forward()} would set a redundant top-level action
     * that conflicts with the steps pipeline.
     *
     * @return added or updated expectations
     */
    public Expectation[] upsert() {
        return mockServerClient.upsert(expectation);
    }

    @VisibleForTesting
    Expectation getExpectation() {
        return expectation;
    }

}
