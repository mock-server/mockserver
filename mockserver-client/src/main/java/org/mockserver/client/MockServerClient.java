package org.mockserver.client;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class MockServerClient {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String mockServerURI;
    private final int port;

    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();

    public MockServerClient(String host, int port) {
        this.port = port;
        mockServerURI = "http://" + host + ":" + port + "/";
    }

    public ForwardChainExpectation when(final HttpRequest httpRequest) {
        return when(httpRequest, Times.unlimited());
    }

    public ForwardChainExpectation when(HttpRequest httpRequest, Times times) {
        return new ForwardChainExpectation(this, new Expectation(httpRequest, times));
    }

    public void clear(HttpRequest httpRequest) {
        sendExpectation(new Expectation(httpRequest, Times.unlimited()), "clear");
    }

    protected void sendExpectation(Expectation expectation) {
        sendExpectation(expectation, "");
    }

    private void sendExpectation(Expectation expectation, String path) {
        HttpClient httpClient = new HttpClient();
        try {
            httpClient.start();
            httpClient.newRequest(mockServerURI + path)
                    .method(HttpMethod.PUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .content(new StringContentProvider((expectation != null ? expectationSerializer.serialize(expectation) : "")))
                    .send();
        } catch (Exception e) {
            logger.error(String.format("Exception sending expectation to MockServer as %s", expectation), e);
            throw new RuntimeException(String.format("Exception sending expectation to MockServer as %s", expectation), e);
        }
    }

    public void stopServer() {
        int stopPort = Integer.parseInt(System.getProperty("mockserver.stopPort", "" + (port + 1)));
        String stopKey = System.getProperty("mockserver.stopKey", "STOP_KEY");

    }
}
