package org.mockserver.client.proxy;

import org.mockserver.client.http.HttpRequestClient;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class ProxyClient {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private HttpRequestClient httpClient;
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();

    /**
     * Start the client communicating to the proxy at the specified host and port
     * for example:
     *   ProxyClient mockServerClient = new ProxyClient("localhost", 1080);
     *
     * @param host the host for the proxy to communicate with
     * @param port the port for the proxy to communicate with
     */
    public ProxyClient(String host, int port) {
        httpClient = new HttpRequestClient("http://" + host + ":" + port);
    }

    /**
     * Pretty-print the json for all expectations already setup to the log.  They are printed at
     * WARN level to ensure they appear even if the default logging level has not been altered
     */
    public void dumpToLog() {
        httpClient.sendRequest("", "/dumpToLog");
    }

    /**
     * Reset the proxy by clearing recorded requests
     */
    public void reset() {
        httpClient.sendRequest("", "/reset");
    }

    /**
     * Clear all recorded requests that match the httpRequest parameter
     *
     * @param httpRequest the http that is matched against when deciding whether to clear recorded requests
     */
    public void clear(HttpRequest httpRequest) {
        httpClient.sendRequest((httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : ""), "/clear");
    }
}
