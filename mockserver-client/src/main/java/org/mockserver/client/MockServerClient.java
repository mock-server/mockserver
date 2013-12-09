package org.mockserver.client;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class MockServerClient {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String mockServerURI;

    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();

    public MockServerClient(String host, int port) {
        mockServerURI = "http://" + host + ":" + port + "/";
    }

    public ForwardChainExpectation when(final HttpRequest httpRequest) {
        return when(httpRequest, Times.unlimited());
    }

    public ForwardChainExpectation when(HttpRequest httpRequest, Times times) {
        return new ForwardChainExpectation(this, new Expectation(httpRequest, times));
    }

    public void clear(HttpRequest httpRequest) {
        sendExpectation(new Expectation(httpRequest, Times.unlimited()).respond(new HttpResponse()), "clear");
    }

    public void reset() {
        try {
            sendRequest("", "reset");
        } catch (Exception e) {
            logger.error("Exception sending reset request to MockServer", e);
            throw new RuntimeException("Exception sending reset request to MockServer", e);
        }
    }

    protected void sendExpectation(Expectation expectation) {
        sendExpectation(expectation, "");
    }

    private void sendExpectation(Expectation expectation, String path) {
        try {
            sendRequest(expectation != null ? expectationSerializer.serialize(expectation) : "", path);
        } catch (Exception e) {
            logger.error(String.format("Exception sending expectation to MockServer as %s", expectation), e);
            throw new RuntimeException(String.format("Exception sending expectation to MockServer as %s", expectation), e);
        }
    }

    private void sendRequest(String body, String path) throws Exception {
        HttpClient httpClient = new HttpClient();
        httpClient.start();
        httpClient.newRequest(mockServerURI + path)
                .method(HttpMethod.PUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .content(new StringContentProvider(body))
                .send();
    }
}
