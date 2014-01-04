package org.mockserver.client.proxy;

import org.mockserver.client.http.HttpRequestClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author jamesdbloom
 */
public class ProxyClient {
    private final String uri;
    private HttpRequestClient httpClient;
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
        httpClient = new HttpRequestClient();
    }

    /**
     * Pretty-print the json for all expectations already setup to the log.  They are printed at
     * WARN level to ensure they appear even if the default logging level has not been altered
     */
    public void dumpToLog() {
        httpClient.sendPUTRequest(uri, "", "/dumpToLog");
    }

    /**
     * Reset the proxy by clearing recorded requests
     */
    public void reset() {
        httpClient.sendPUTRequest(uri, "", "/reset");
    }

    /**
     * Clear all recorded requests that match the httpRequest parameter
     *
     * @param httpRequest the http that is matched against when deciding whether to clear recorded requests
     */
    public void clear(HttpRequest httpRequest) {
        httpClient.sendPUTRequest(uri, (httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : ""), "/clear");
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter as expectations, use null for the parameter to retrieve all requests
     *
     * @param httpRequest the http that is matched against when deciding whether to return each expectation, , use null for the parameter to retrieve all requests
     * @return an array of all expectations that have been recorded by the proxy
     */
    public Expectation[] retrieveExpectationsAsObjects(HttpRequest httpRequest) {
        return expectationSerializer.deserializeArray(httpClient.sendPUTRequest(uri, (httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : ""), "/retrieve").getContent());
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter as a JSON array, use null for the parameter to retrieve all requests
     *
     * @param httpRequest the http that is matched against when deciding whether to return each expectation, , use null for the parameter to retrieve all requests
     * @return a JSON array of all expectations that have been recorded by the proxy
     */
    public String retrieveExpectationsAsJSON(HttpRequest httpRequest) {
        return new String(httpClient.sendPUTRequest(uri, (httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : ""), "/retrieve").getContent(), StandardCharsets.UTF_8);
    }
}
