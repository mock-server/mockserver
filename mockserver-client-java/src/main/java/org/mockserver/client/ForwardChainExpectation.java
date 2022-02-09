package org.mockserver.client;

import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.nio.NioEventLoopGroup;
import org.mockserver.client.MockServerEventBus.EventType;
import org.mockserver.closurecallback.websocketclient.WebSocketClient;
import org.mockserver.closurecallback.websocketclient.WebSocketException;
import org.mockserver.closurecallback.websocketregistry.LocalCallbackRegistry;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.*;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.uuid.UUIDService;

import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockserver.configuration.ConfigurationProperties.maxFutureTimeout;

/**
 * @author jamesdbloom
 */
public class ForwardChainExpectation {

    private final MockServerLogger mockServerLogger;
    private final MockServerClient mockServerClient;
    private final Expectation expectation;
    private final MockServerEventBus mockServerEventBus;

    ForwardChainExpectation(MockServerLogger mockServerLogger, MockServerEventBus mockServerEventBus, MockServerClient mockServerClient, Expectation expectation) {
        this.mockServerLogger = mockServerLogger;
        this.mockServerEventBus = mockServerEventBus;
        this.mockServerClient = mockServerClient;
        this.expectation = expectation;
    }

    /**
     * <p>
     * Set id of expectation which can be used to update this expectation later
     * or for clearing or verifying by expectation id.
     * </p>
     * <p>
     * Note: Each unique expectation must have a unique id otherwise this
     * expectation will update a existing expectation with the same id.
     * </p>
     * @param id unique string for expectation's id
     */
    public ForwardChainExpectation withId(String id) {
        expectation.withId(id);
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
     * Return error when expectation is matched
     *
     * @param httpError error to return
     * @return added or updated expectations
     */
    public Expectation[] error(final HttpError httpError) {
        expectation.thenError(httpError);
        return mockServerClient.upsert(expectation);
    }

    @SuppressWarnings("rawtypes")
    private <T extends HttpMessage> String registerWebSocketClient(ExpectationCallback<T> expectationCallback, ExpectationForwardAndResponseCallback expectationForwardResponseCallback) {
        try {
            String clientId = UUIDService.getUUID();
            LocalCallbackRegistry.registerCallback(clientId, expectationCallback);
            LocalCallbackRegistry.registerCallback(clientId, expectationForwardResponseCallback);
            final WebSocketClient<T> webSocketClient = new WebSocketClient<>(
                new NioEventLoopGroup(ConfigurationProperties.webSocketClientEventLoopThreadCount(), new Scheduler.SchedulerThreadFactory(WebSocketClient.class.getSimpleName() + "-eventLoop")),
                clientId,
                mockServerLogger
            );
            final Future<String> register = webSocketClient.registerExpectationCallback(
                expectationCallback,
                expectationForwardResponseCallback,
                mockServerClient.remoteAddress(),
                mockServerClient.contextPath(),
                mockServerClient.isSecure()
            );
            mockServerEventBus.subscribe(webSocketClient::stopClient, EventType.STOP, EventType.RESET);
            return register.get(maxFutureTimeout(), MILLISECONDS);
        } catch (Exception e) {
            if (e.getCause() instanceof WebSocketException) {
                throw new ClientException(e.getCause().getMessage(), e);
            } else {
                throw new ClientException("Unable to retrieve client registration id", e);
            }
        }
    }

    @VisibleForTesting
    Expectation getExpectation() {
        return expectation;
    }

}
