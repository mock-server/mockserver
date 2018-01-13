package org.mockserver.client;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.client.netty.websocket.WebSocketClient;
import org.mockserver.client.netty.websocket.WebSocketException;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.*;

/**
 * @author jamesdbloom
 */
public class ForwardChainExpectation {

    private final AbstractClient mockServerClient;
    private final Expectation expectation;
    private WebSocketClient webSocketClient;

    public ForwardChainExpectation(AbstractClient mockServerClient, Expectation expectation) {
        this.mockServerClient = mockServerClient;
        this.expectation = expectation;
    }

    /**
     * Return response when expectation is matched
     *
     * @param httpResponse response to return
     */
    public void respond(HttpResponse httpResponse) {
        expectation.thenRespond(httpResponse);
        mockServerClient.sendExpectation(expectation);
    }

    /**
     * Evaluate Velocity or JavaScript template to generate response
     * to return when expectation is matched
     *
     * @param httpTemplate Velocity or JavaScript template used to generate response
     */
    public void respond(HttpTemplate httpTemplate) {
        expectation.thenRespond(httpTemplate);
        mockServerClient.sendExpectation(expectation);
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
     */
    public void respond(HttpClassCallback httpClassCallback) {
        expectation.thenRespond(httpClassCallback);
        mockServerClient.sendExpectation(expectation);
    }

    /**
     * Call method on object locally or remotely (over web socket)
     * to generate response to return when expectation is matched
     *
     * @param expectationResponseCallback object to call locally or remotely to generate response
     */
    public void respond(ExpectationResponseCallback expectationResponseCallback) {
        if (webSocketClient == null) {
            webSocketClient = new WebSocketClient(mockServerClient.remoteAddress(), mockServerClient.contextPath());
        }
        try {
            expectation.thenRespond(new HttpObjectCallback()
                .withClientId(
                    webSocketClient
                        .registerExpectationCallback(expectationResponseCallback)
                        .clientId()

                ));
        } catch (WebSocketException wse) {
            throw new ClientException(wse.getMessage());
        }
        mockServerClient.sendExpectation(expectation);
    }

    /**
     * Forward request to the specified host and port when expectation is matched
     *
     * @param httpForward host and port to forward to
     */
    public void forward(HttpForward httpForward) {
        expectation.thenForward(httpForward);
        mockServerClient.sendExpectation(expectation);
    }

    /**
     * Evaluate Velocity or JavaScript template to generate
     * request to forward when expectation is matched
     *
     * @param httpTemplate Velocity or JavaScript template used to generate response
     */
    public void forward(HttpTemplate httpTemplate) {
        expectation.thenForward(httpTemplate);
        mockServerClient.sendExpectation(expectation);
    }

    /**
     * Call method on local class in same JVM implementing ExpectationResponseCallback
     * to generate request to forward when expectation is matched
     * <p>
     * The callback class must:
     * - implement org.mockserver.mock.action.ExpectationForwardCallback
     * - have a zero argument constructor
     * - be available in the classpath of the MockServer
     *
     * @param httpClassCallback class to callback as a fully qualified class name, i.e. "com.foo.MyExpectationResponseCallback"
     */
    public void forward(HttpClassCallback httpClassCallback) {
        expectation.thenForward(httpClassCallback);
        mockServerClient.sendExpectation(expectation);
    }

    /**
     * Call method on object locally or remotely (over web socket)
     * to generate request to forward when expectation is matched
     *
     * @param expectationForwardCallback object to call locally or remotely to generate request
     */
    public void forward(ExpectationForwardCallback expectationForwardCallback) {
        if (webSocketClient == null) {
            webSocketClient = new WebSocketClient(mockServerClient.remoteAddress(), mockServerClient.contextPath());
        }
        try {
            expectation.thenForward(new HttpObjectCallback()
                .withClientId(
                    webSocketClient
                        .registerExpectationCallback(expectationForwardCallback)
                        .clientId()
                ));
        } catch (WebSocketException wse) {
            throw new ClientException(wse.getMessage());
        }
        mockServerClient.sendExpectation(expectation);
    }

    /**
     * Override fields, headers, and cookies etc in request being forwarded with
     * specified fields, headers and cookies, etc in the specified request
     * when expectation is matched
     *
     * @param httpOverrideForwardedRequest contains request to override request being forwarded
     */
    public void forward(HttpOverrideForwardedRequest httpOverrideForwardedRequest) {
        expectation.thenForward(httpOverrideForwardedRequest);
        mockServerClient.sendExpectation(expectation);
    }

    /**
     * Return error when expectation is matched
     *
     * @param httpError error to return
     */
    public void error(HttpError httpError) {
        expectation.thenError(httpError);
        mockServerClient.sendExpectation(expectation);
    }

    @VisibleForTesting
    Expectation getExpectation() {
        return expectation;
    }

    @VisibleForTesting
    void setWebSocketClient(WebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
    }
}
