package org.mockserver.client.proxy;

import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class ProxyClient {
    private final String uri;
    private ApacheHttpClient apacheHttpClient;
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();

    /**
     * Start the client communicating to the proxy at the specified host and port
     * for example:
     *   ProxyClient mockServerClient = new ProxyClient("localhost", 1080);
     *
     * @param host the host for the proxy to communicate with
     * @param port the port for the proxy to communicate with
     */
    public ProxyClient(String host, int port) {
        uri = "http://" + host + ":" + port;
        apacheHttpClient = new ApacheHttpClient();
    }

    /**
     * Pretty-print the json for all requests / responses as Expectations to the log.
     * They are printed into a dedicated log called mockserver_request.log
     */
    public ProxyClient dumpToLogAsJSON() {
        return dumpToLogAsJSON(null);
    }

    /**
     * Pretty-print the json for matching requests and their responses as Expectations to the log.
     * They are printed into a dedicated log called mockserver_request.log
     *
     * @param httpRequest the http request that is matched against when deciding what to log if null all requests are logged
     */
    public ProxyClient dumpToLogAsJSON(HttpRequest httpRequest) {
        apacheHttpClient.sendPUTRequest(uri, "/dumpToLog", httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "");
        return this;
    }

    /**
     * Output Java code for creating all requests / responses as Expectations to the log.
     * They are printed into a dedicated log called mockserver_request.log
     */
    public ProxyClient dumpToLogAsJava() {
        return dumpToLogAsJava(null);
    }

    /**
     * Output Java code for creating matching requests and their responses as Expectations to the log.
     * They are printed into a dedicated log called mockserver_request.log
     *
     * @param httpRequest the http request that is matched against when deciding what to log if null all requests are logged
     */
    public ProxyClient dumpToLogAsJava(HttpRequest httpRequest) {
        apacheHttpClient.sendPUTRequest(uri, "/dumpToLog?type=java", httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "");
        return this;
    }

    /**
     * Reset the proxy by clearing recorded requests
     */
    public ProxyClient reset() {
        apacheHttpClient.sendPUTRequest(uri, "/reset", "");
        return this;
    }

    /**
     * Clear all recorded requests that match the httpRequest parameter
     *
     * @param httpRequest the http request that is matched against when deciding whether to clear recorded requests
     */
    public ProxyClient clear(HttpRequest httpRequest) {
        apacheHttpClient.sendPUTRequest(uri, "/clear", httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "");
        return this;
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
     * @param httpRequest the http request that must be matched for this verification to pass
     * @throws AssertionError if the request has not been found
     */
    public ProxyClient verify(HttpRequest httpRequest) throws AssertionError {
        return verify(httpRequest, Times.atLeast(1));
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
     * @param httpRequest the http request that must be matched for this verification to pass
     * @param times the number of times this request must be matched
     * @throws AssertionError if the request has not been found
     */
    public ProxyClient verify(HttpRequest httpRequest, Times times) throws AssertionError {
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
        String allRequests = apacheHttpClient.sendPUTRequest(uri, "/retrieve", "");
        return " but " + (StringUtils.isNotEmpty(allRequests) ? "only found " + allRequests : "found no requests");
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter as expectations, use null for the parameter to retrieve all requests
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @return an array of all expectations that have been recorded by the proxy
     */
    public Expectation[] retrieveAsExpectations(HttpRequest httpRequest) {
        return expectationSerializer.deserializeArray(apacheHttpClient.sendPUTRequest(uri, "/retrieve", (httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "")));
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter as a JSON array, use null for the parameter to retrieve all requests
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @return a JSON array of all expectations that have been recorded by the proxy
     */
    public String retrieveAsJSON(HttpRequest httpRequest) {
        return apacheHttpClient.sendPUTRequest(uri, "/retrieve", (httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : ""));
    }
}
