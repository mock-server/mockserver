package org.mockserver.client.server;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.client.netty.websocket.WebSocketClient;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.model.*;

/**
 * @author jamesdbloom
 */
public class ForwardChainExpectation {

    private final MockServerClient mockServerClient;
    private final Expectation expectation;
    private WebSocketClient webSocketClient;

    public ForwardChainExpectation(MockServerClient mockServerClient, Expectation expectation) {
        this.mockServerClient = mockServerClient;
        this.expectation = expectation;
    }

    public void respond(HttpResponse httpResponse) {
        expectation.thenRespond(httpResponse);
        mockServerClient.sendExpectation(expectation);
    }

    public void respond(HttpTemplate httpTemplate) {
        expectation.thenRespond(httpTemplate);
        mockServerClient.sendExpectation(expectation);
    }

    public void forward(HttpForward httpForward) {
        expectation.thenForward(httpForward);
        mockServerClient.sendExpectation(expectation);
    }

    public void error(HttpError httpError) {
        expectation.thenError(httpError);
        mockServerClient.sendExpectation(expectation);
    }

    public void callback(HttpClassCallback httpClassCallback) {
        expectation.thenCallback(httpClassCallback);
        mockServerClient.sendExpectation(expectation);
    }

    public void callback(ExpectationCallback httpObjectCallback) {
        if (webSocketClient == null) {
            webSocketClient = new WebSocketClient(mockServerClient.remoteAddress(), mockServerClient.contextPath());
        }
        expectation.thenCallback(new HttpObjectCallback()
                .withClientId(
                        webSocketClient
                                .registerExpectationCallback(httpObjectCallback)
                                .clientId()
                ));
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
