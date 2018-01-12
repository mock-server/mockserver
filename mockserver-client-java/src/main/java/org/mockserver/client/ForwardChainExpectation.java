package org.mockserver.client;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.client.netty.websocket.WebSocketClient;
import org.mockserver.mock.Expectation;
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

    public void respond(HttpResponse httpResponse) {
        expectation.thenRespond(httpResponse);
        mockServerClient.sendExpectation(expectation);
    }

    public void respond(HttpTemplate httpTemplate) {
        expectation.thenRespond(httpTemplate);
        mockServerClient.sendExpectation(expectation);
    }

    public void response(HttpClassCallback httpClassCallback) {
        expectation.thenRespond(httpClassCallback);
        mockServerClient.sendExpectation(expectation);
    }

    public void response(ExpectationResponseCallback httpObjectCallback) {
        if (webSocketClient == null) {
            webSocketClient = new WebSocketClient(mockServerClient.remoteAddress(), mockServerClient.contextPath());
        }
        expectation.thenRespond(new HttpObjectCallback()
            .withClientId(
                webSocketClient
                    .registerExpectationCallback(httpObjectCallback)
                    .clientId()
            ));
        mockServerClient.sendExpectation(expectation);
    }

    public void forward(HttpForward httpForward) {
        expectation.thenForward(httpForward);
        mockServerClient.sendExpectation(expectation);
    }

    public void forward(HttpTemplate httpTemplate) {
        expectation.thenForward(httpTemplate);
        mockServerClient.sendExpectation(expectation);
    }

    public void forward(HttpClassCallback httpClassCallback) {
        expectation.thenForward(httpClassCallback);
        mockServerClient.sendExpectation(expectation);
    }

    public void forward(ExpectationResponseCallback httpObjectCallback) {
        if (webSocketClient == null) {
            webSocketClient = new WebSocketClient(mockServerClient.remoteAddress(), mockServerClient.contextPath());
        }
        expectation.thenForward(new HttpObjectCallback()
            .withClientId(
                webSocketClient
                    .registerExpectationCallback(httpObjectCallback)
                    .clientId()
            ));
        mockServerClient.sendExpectation(expectation);
    }

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
