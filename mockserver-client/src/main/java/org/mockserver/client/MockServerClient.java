package org.mockserver.client;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
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
    private HttpClient httpClient = new HttpClient();

    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * for example:
     * <p/>
     * MockServerClient mockServerClient = new MockServerClient("localhost", 8080);
     *
     * @param host the host for the MockServer to communicate with
     * @param port the port for the MockServer to communicate with
     */
    public MockServerClient(String host, int port) {
        mockServerURI = "http://" + host + ":" + port + "/";
        try {
            httpClient.start();
        } catch (Exception e) {
            logger.error("Exception starting HttpClient in MockServerClient", e);
            throw new RuntimeException("Exception starting HttpClient in MockServerClient", e);
        }
    }

    @VisibleForTesting
    MockServerClient(String host, int port, HttpClient httpClient) {
        mockServerURI = "http://" + host + ":" + port + "/";
        this.httpClient = httpClient;
        try {
            this.httpClient.start();
        } catch (Exception e) {
            logger.error("Exception starting HttpClient in MockServerClient", e);
            throw new RuntimeException("Exception starting HttpClient in MockServerClient", e);
        }
    }


    /**
     * Specify an unlimited expectation that will respond regardless of the number of matching request
     * for example:
     * <p/>
     * mockServerClient
     * .when(
     * new HttpRequest()
     * .withPath("/some_path")
     * .withBody("some_request_body")
     * )
     * .respond(
     * new HttpResponse()
     * .withBody("some_response_body")
     * .withHeaders(
     * new Header("responseName", "responseValue")
     * )
     * );
     *
     * @param httpRequest the request that must be matched for this expectation to respond
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(final HttpRequest httpRequest) {
        return when(httpRequest, Times.unlimited());
    }

    /**
     * Specify an limited expectation that will respond a specified number of times when the request is matched
     * for example:
     * <p/>
     * mockServerClient
     * .when(
     * new HttpRequest()
     * .withPath("/some_path")
     * .withBody("some_request_body"),
     * Times.exactly(5)
     * )
     * .respond(
     * new HttpResponse()
     * .withBody("some_response_body")
     * .withHeaders(
     * new Header("responseName", "responseValue")
     * )
     * );
     *
     * @param httpRequest the request that must be matched for this expectation to respond
     * @param times       the number of times to respond when this request is matched
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
        try {
            sendRequest("", "dumpToLog");
        } catch (Exception e) {
            logger.error("Exception sending reset request to MockServer", e);
            throw new RuntimeException("Exception sending reset request to MockServer", e);
        }
    }

    /**
     * Reset MockServer by clearing all expectations
     */
    public void reset() {
        try {
            sendRequest("", "reset");
        } catch (Exception e) {
            logger.error("Exception sending reset request to MockServer", e);
            throw new RuntimeException("Exception sending reset request to MockServer", e);
        }
    }

    /**
     * Clear all expectations that match the request
     *
     * @param httpRequest the request that is matched against when deciding whether to clear each expectation
     */
    public void clear(HttpRequest httpRequest) {
        sendExpectation(new Expectation(httpRequest, Times.unlimited()).respond(new HttpResponse()), "clear");
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
        httpClient.newRequest(mockServerURI + path)
                .method(HttpMethod.PUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .content(new ComparableStringContentProvider(body, "UTF-8"))
                .send();
    }

    static class ComparableStringContentProvider extends StringContentProvider {

        public ComparableStringContentProvider(String content, String encoding) {
            super(content, encoding);
        }

        @Override
        public boolean equals(Object other) {
            return EqualsBuilder.reflectionEquals(this, other);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }
    }
}
