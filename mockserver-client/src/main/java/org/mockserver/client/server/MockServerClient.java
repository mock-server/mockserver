package org.mockserver.client.server;

import org.mockserver.client.http.HttpRequestClient;
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
    private HttpRequestClient httpClient;
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * for example:
     *
     *   MockServerClient mockServerClient = new MockServerClient("localhost", 8080);
     *
     * @param host the host for the MockServer to communicate with
     * @param port the port for the MockServer to communicate with
     */
    public MockServerClient(String host, int port) {
        httpClient = new HttpRequestClient("http://" + host + ":" + port);
    }

    /**
     * Specify an unlimited expectation that will respond regardless of the number of matching http
     * for example:
     *
     *   mockServerClient
     *           .when(
     *                   new HttpRequest()
     *                           .withPath("/some_path")
     *                           .withBody("some_request_body")
     *           )
     *           .respond(
     *                   new HttpResponse()
     *                           .withBody("some_response_body")
     *                           .withHeaders(
     *                                   new Header("responseName", "responseValue")
     *                           )
     *           );
     *
     * @param httpRequest the http that must be matched for this expectation to respond
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(final HttpRequest httpRequest) {
        return when(httpRequest, Times.unlimited());
    }

    /**
     * Specify an limited expectation that will respond a specified number of times when the http is matched
     * for example:
     *
     *   mockServerClient
     *           .when(
     *                   new HttpRequest()
     *                           .withPath("/some_path")
     *                           .withBody("some_request_body"),
     *                   Times.exactly(5)
     *           )
     *           .respond(
     *                   new HttpResponse()
     *                           .withBody("some_response_body")
     *                           .withHeaders(
     *                                   new Header("responseName", "responseValue")
     *                           )
     *           );
     *
     * @param httpRequest the http that must be matched for this expectation to respond
     * @param times       the number of times to respond when this http is matched
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(HttpRequest httpRequest, Times times) {
        return new ForwardChainExpectation(this, new Expectation(httpRequest, times));
    }

    /**
     * Pretty-print the json for all expectations already setup to the log.  They are printed at
     * WARN level to ensure they appear even if the default logging level has not been altered
     */
    public void dumpToLog() {
        httpClient.sendRequest("", "/dumpToLog");
    }

    /**
     * Reset MockServer by clearing all expectations
     */
    public void reset() {
        httpClient.sendRequest("", "/reset");
    }

    /**
     * Clear all expectations that match the http
     *
     * @param httpRequest the http that is matched against when deciding whether to clear each expectation
     */
    public void clear(HttpRequest httpRequest) {
        sendExpectation(new Expectation(httpRequest, Times.unlimited()).respond(new HttpResponse()), "/clear");
    }

    protected void sendExpectation(Expectation expectation) {
        sendExpectation(expectation, "");
    }

    private void sendExpectation(Expectation expectation, String path) {
        httpClient.sendRequest(expectation != null ? expectationSerializer.serialize(expectation) : "", path);
    }

}
