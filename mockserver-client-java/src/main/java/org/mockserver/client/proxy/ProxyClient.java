package org.mockserver.client.proxy;

import org.mockserver.client.AbstractClient;
import org.mockserver.client.netty.SocketConnectionException;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.Verification.verification;

/**
 * @author jamesdbloom
 */
public class ProxyClient extends AbstractClient<ProxyClient> {

    /**
     * Start the client communicating to a the proxy at the specified host and port
     * for example:
     *
     *   ProxyClient mockServerClient = new ProxyClient("localhost", 1080);
     *
     * @param host the host for the MockServer to communicate with
     * @param port the port for the MockServer to communicate with
     */
    public ProxyClient(String host, int port) {
        this(host, port, "");
    }

    /**
     * Start the client communicating to the proxy at the specified host and port
     * and contextPath for example:
     *
     *   ProxyClient mockServerClient = new ProxyClient("localhost", 1080, "/proxy");
     *
     * @param host the host for the proxy to communicate with
     * @param port the port for the proxy to communicate with
     * @param contextPath the context path that the proxy war is deployed to
     */
    public ProxyClient(String host, int port, String contextPath) {
        super(host, port, contextPath, ProxyClient.class);
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
        sendRequest(request().withMethod("PUT").withPath(calculatePath("dumpToLog")).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : ""));
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
        sendRequest(request().withMethod("PUT").withPath(calculatePath("dumpToLog?format=java")).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : ""));
        return this;
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter as expectations, use null for the parameter to retrieve all requests
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @return an array of all expectations that have been recorded by the proxy
     */
    public Expectation[] retrieveAsExpectations(HttpRequest httpRequest) {
        HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("retrieve")).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : ""));
        return expectationSerializer.deserializeArray(httpResponse.getBodyAsString());
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter as a JSON array, use null for the parameter to retrieve all requests
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @return a JSON array of all expectations that have been recorded by the proxy
     */
    public String retrieveAsJSON(HttpRequest httpRequest) {
        HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("retrieve")).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : ""));
        return httpResponse.getBodyAsString();
    }
}
