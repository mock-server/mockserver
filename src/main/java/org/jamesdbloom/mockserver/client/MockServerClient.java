package org.jamesdbloom.mockserver.client;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.jamesdbloom.mockserver.client.serialization.ExpectationSerializer;
import org.jamesdbloom.mockserver.client.serialization.model.ExpectationDTO;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.mock.Expectation;
import org.jamesdbloom.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class MockServerClient {

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

    public void sendExpectation(Expectation expectation) {
        HttpClient httpClient = new HttpClient();
        try {
            httpClient.start();
            httpClient.newRequest(mockServerURI).method(HttpMethod.PUT).content(new StringContentProvider(expectationSerializer.serialize(expectation))).send();
        } catch (Exception e) {
            throw new RuntimeException(String.format("Exception sending expectation to MockServer as %s", expectation), e);
        }
    }
}
