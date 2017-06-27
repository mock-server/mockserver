package org.mockserver.client.proxy;

import org.mockserver.client.AbstractClient;
import org.mockserver.client.netty.SocketConnectionException;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;

import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.Verification.verification;

/**
 * @author jamesdbloom
 */
public class ProxyClient extends AbstractClient {

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
        super(host, port, contextPath);
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
        sendRequest(request().withMethod("PUT").withPath(calculatePath("dumpToLog?type=java")).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : ""));
        return this;
    }

    /**
     * Returns whether the proxy is running
     */
    public boolean isRunning() {
        return isRunning(10, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns whether the proxy is running, by polling the MockServer a configurable amount of times
     */
    public boolean isRunning(int attempts, long  timeout, TimeUnit timeUnit) {
        try {
            HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("status")));
            if (httpResponse.getStatusCode() == HttpStatusCode.OK_200.code()) {
                return true;
            } else {
                try {
                    timeUnit.sleep(timeout);
                } catch (InterruptedException e) {
                    // ignore interrupted exception
                }
                return isRunning(attempts  - 1, timeout, timeUnit);
            }
        } catch (SocketConnectionException sce) {
            return false;
        }
    }

    /**
     * Stop the proxy gracefully (only support for Netty and Vert.X versions, not supported for WAR version)
     */
    public ProxyClient stop() {
        return stop(false);
    }

    public ProxyClient stop(boolean ignoreFailure) {
        try {
            sendRequest(request().withMethod("PUT").withPath(calculatePath("stop")));
            if (isRunning()) {
                for (int i = 0; isRunning() && i < 50; i++) {
                    TimeUnit.MILLISECONDS.sleep(5);
                }
            }
        } catch (Exception e) {
            if (!ignoreFailure) {
                logger.warn("Failed to send stop request to proxy " + e.getMessage());
            }
        }
        return this;
    }

    /**
     * Reset the proxy by clearing recorded requests
     */
    public ProxyClient reset() {
        sendRequest(request().withMethod("PUT").withPath(calculatePath("reset")));
        return this;
    }

    /**
     * Clear all recorded requests that match the httpRequest parameter
     *
     * @param httpRequest the http request that is matched against when deciding whether to clear recorded requests
     */
    public ProxyClient clear(HttpRequest httpRequest) {
        sendRequest(request().withMethod("PUT").withPath(calculatePath("clear")).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : ""));
        return this;
    }

    /**
     * Verify a list of requests have been sent in the order specified for example:
     *
     *   mockServerClient
     *           .verify(
     *                   request()
     *                           .withPath("/first_request")
     *                           .withBody("some_request_body"),
     *                   request()
     *                           .withPath("/second_request")
     *                           .withBody("some_request_body")
     *           );
     *
     * @param httpRequests the http requests that must be matched for this verification to pass
     * @throws AssertionError if the request has not been found
     */
    public ProxyClient verify(HttpRequest... httpRequests) throws AssertionError {
        if (httpRequests == null || httpRequests.length == 0 || httpRequests[0] == null) {
            throw new IllegalArgumentException("verify(HttpRequest...) requires a non null non empty array of HttpRequest objects");
        }

        VerificationSequence verificationSequence = new VerificationSequence().withRequests(httpRequests);
        String result = sendRequest(request().withMethod("PUT").withPath(calculatePath("verifySequence")).withBody(verificationSequenceSerializer.serialize(verificationSequence))).getBodyAsString();

        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
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
     *                   VerificationTimes.exactly(3)
     *           );
     *
     * VerificationTimes supports multiple static factory methods:
     *
     *   once()      - verify the request was only received once
     *   exactly(n)  - verify the request was only received exactly n times
     *   atLeast(n)  - verify the request was only received at least n times
     *
     * @param httpRequest the http request that must be matched for this verification to pass
     * @param times the number of times this request must be matched
     * @throws AssertionError if the request has not been found
     */
    public ProxyClient verify(HttpRequest httpRequest, VerificationTimes times) throws AssertionError {
        if (httpRequest == null) {
            throw new IllegalArgumentException("verify(HttpRequest, VerificationTimes) requires a non null HttpRequest object");
        }
        if (times == null) {
            throw new IllegalArgumentException("verify(HttpRequest, VerificationTimes) requires a non null VerificationTimes object");
        }

        Verification verification = verification().withRequest(httpRequest).withTimes(times);
        String result = sendRequest(request().withMethod("PUT").withPath(calculatePath("verify")).withBody(verificationSerializer.serialize(verification))).getBodyAsString();

        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
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
