package org.mockserver.client.server;

import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class MockServerClient {
    private static final Logger logger = LoggerFactory.getLogger(MockServerClient.class);

    private final String uriBase;
    private ApacheHttpClient apacheHttpClient;
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
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
        this(host, port, "");
    }

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * and contextPath for example:
     *
     *   MockServerClient mockServerClient = new MockServerClient("localhost", 8080, "/mockserver");
     *
     * @param host the host for the MockServer to communicate with
     * @param port the port for the MockServer to communicate with
     * @param contextPath the context path that the MockServer war is deployed to
     */
    public MockServerClient(String host, int port, String contextPath) {
        if (StringUtils.isEmpty(host)) throw new IllegalArgumentException("Host can not be null or empty");
        if (contextPath == null) throw new IllegalArgumentException("ContextPath can not be null");
        uriBase = "http://" + host + ":" + port + (contextPath.length() > 0 && !contextPath.startsWith("/") ? "/" : "") + contextPath;
        apacheHttpClient = new ApacheHttpClient(false);
    }

    /**
     * Specify an unlimited expectation that will respond regardless of the number of matching http
     * for example:
     *
     *   mockServerClient
     *           .when(
     *                   request()
     *                           .withPath("/some_path")
     *                           .withBody("some_request_body")
     *           )
     *           .respond(
     *                   response()
     *                           .withBody("some_response_body")
     *                           .withHeaders(
     *                                   new Header("responseName", "responseValue")
     *                           )
     *           );
     *
     * @param httpRequest the http request that must be matched for this expectation to respond
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(HttpRequest httpRequest) {
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
     * @param httpRequest the http request that must be matched for this expectation to respond
     * @param times       the number of times to respond when this http is matched
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(HttpRequest httpRequest, Times times) {
        return new ForwardChainExpectation(this, new Expectation(httpRequest, times));
    }

    /**
     * Pretty-print the json for all expectations to the log.  They are printed into a dedicated log called mockserver_request.log
     */
    public MockServerClient dumpToLog() {
        dumpToLog(null);
        return this;
    }

    /**
     * Pretty-print the json for all expectations that match the request to the log.  They are printed into a dedicated log called mockserver_request.log
     *
     * @param httpRequest the http request that is matched against when deciding what to log if null all requests are logged
     */
    public MockServerClient dumpToLog(HttpRequest httpRequest) {
        apacheHttpClient.sendPUTRequest(uriBase, "/dumpToLog", httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "");
        return this;
    }

    /**
     * Reset MockServer by clearing all expectations
     */
    public MockServerClient reset() {
        apacheHttpClient.sendPUTRequest(uriBase, "/reset", "");
        return this;
    }

    /**
     * Stop MockServer gracefully (only support for Netty and Vert.X versions, not supported for WAR version)
     */
    public MockServerClient stop() {
        try {
            apacheHttpClient.sendPUTRequest(uriBase, "/stop", "");
        } catch (Exception e) {
            logger.debug("Failed to send stop request to proxy " + e.getMessage());
        }
        return this;
    }

    /**
     * Clear all expectations that match the http
     *
     * @param httpRequest the http request that is matched against when deciding whether to clear each expectation if null all expectations are cleared
     */
    public MockServerClient clear(HttpRequest httpRequest) {
        apacheHttpClient.sendPUTRequest(uriBase, "/clear", httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "");
        return this;
    }

    protected void sendExpectation(Expectation expectation) {
        apacheHttpClient.sendPUTRequest(uriBase, "/expectation", expectation != null ? expectationSerializer.serialize(expectation) : "");
    }

    /**
     * Verify a request has been sent for example:
     *
     *   mockServerClient
     *           .verify(
     *                   request()
     *                           .withPath("/some_path")
     *                           .withBody("some_request_body")
     *           );
     *
     * @param httpRequests the http requests that must be matched for this verification to pass
     * @throws AssertionError if the request has not been found
     */
    public MockServerClient verify(HttpRequest... httpRequests) throws AssertionError {
        for (HttpRequest httpRequest : httpRequests) {
            verify(httpRequest, org.mockserver.client.proxy.Times.atLeast(1));
        }
        return this;
    }

    /**
     * Verify a request has been sent for example:
     *
     *   mockServerClient
     *           .verify(
     *                   request()
     *                           .withPath("/some_path")
     *                           .withBody("some_request_body"),
     *                   Times.exactly(3)
     *           );
     *
     * Times supports multiple static factory methods:
     *
     *   once()      - verify the request was only received once
     *   exactly(n)  - verify the request was only received exactly n times
     *   atLeast(n)  - verify the request was only received at least n times
     *
     * @param httpRequest the http request that must be matched for this verification to pass
     * @param times the number of times this request must be matched
     * @throws AssertionError if the request has not been found
     */
    public MockServerClient verify(HttpRequest httpRequest, org.mockserver.client.proxy.Times times) throws AssertionError {
        if (httpRequest == null) throw new IllegalArgumentException("verify(HttpRequest) requires a non null HttpRequest object");

        Expectation[] expectations = retrieveAsExpectations(httpRequest);
        if (expectations == null) {
            throw new AssertionError("Expected " + httpRequestSerializer.serialize(httpRequest) + butFoundAssertionErrorMessage());
        }
        if (times.isExact()) {
            if (expectations.length != times.getCount()) {
                throw new AssertionError("Expected " + httpRequestSerializer.serialize(httpRequest) + butFoundAssertionErrorMessage());
            }
        } else {
            if (expectations.length < times.getCount()) {
                throw new AssertionError("Expected " + httpRequestSerializer.serialize(httpRequest) + butFoundAssertionErrorMessage());
            }
        }
        return this;
    }

    private String butFoundAssertionErrorMessage() {
        String allRequests = apacheHttpClient.sendPUTRequest(uriBase, "/retrieve", "");
        return " but " + (StringUtils.isNotEmpty(allRequests) ? "only found " + allRequests : "found no requests");
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter as expectations, use null for the parameter to retrieve all requests
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @return an array of all expectations that have been recorded by the proxy
     */
    public Expectation[] retrieveAsExpectations(HttpRequest httpRequest) {
        return expectationSerializer.deserializeArray(apacheHttpClient.sendPUTRequest(uriBase, "/retrieve", (httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "")));
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter as a JSON array, use null for the parameter to retrieve all requests
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @return a JSON array of all expectations that have been recorded by the proxy
     */
    public String retrieveAsJSON(HttpRequest httpRequest) {
        return apacheHttpClient.sendPUTRequest(uriBase, "/retrieve", (httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : ""));
    }
}
