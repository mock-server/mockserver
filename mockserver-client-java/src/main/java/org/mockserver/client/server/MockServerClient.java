package org.mockserver.client.server;

import com.google.common.base.Charsets;
import org.mockserver.client.AbstractClient;
import org.mockserver.client.netty.SocketConnectionException;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;

import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class MockServerClient extends AbstractClient {

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * for example:
     *
     *   MockServerClient mockServerClient = new MockServerClient("localhost", 1080);
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
     *   MockServerClient mockServerClient = new MockServerClient("localhost", 1080, "/mockserver");
     *
     * @param host the host for the MockServer to communicate with
     * @param port the port for the MockServer to communicate with
     * @param contextPath the context path that the MockServer war is deployed to
     */
    public MockServerClient(String host, int port, String contextPath) {
        super(host, port, contextPath);
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
        return new ForwardChainExpectation(this, new Expectation(httpRequest, times, TimeToLive.unlimited()));
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
     *                   Times.exactly(5),
     *                   TimeToLive.exactly(TimeUnit.SECONDS, 120),
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
     * @param timeToLive  the length of time from when the server receives the expectation that the expectation should be active
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(HttpRequest httpRequest, Times times, TimeToLive timeToLive) {
        return new ForwardChainExpectation(this, new Expectation(httpRequest, times, timeToLive));
    }

    /**
     * Pretty-print the json for all expectations to the log.  They are printed into a dedicated log called mockserver_request.log
     */
    public MockServerClient dumpToLog() {
        return dumpToLog(null);
    }

    /**
     * Pretty-print the json for all expectations that match the request to the log.  They are printed into a dedicated log called mockserver_request.log
     *
     * @param httpRequest the http request that is matched against when deciding what to log if null all requests are logged
     */
    public MockServerClient dumpToLog(HttpRequest httpRequest) {
        sendRequest(request().withMethod("PUT").withPath(calculatePath("dumpToLog")).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", Charsets.UTF_8));
        return this;
    }

    /**
     * Returns whether MockServer is running
     */
    public boolean isRunning() {
        return isRunning(10, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns whether MockServer is running, by polling the MockServer a configurable amount of times
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
     * Stop MockServer gracefully (only support for Netty and Vert.X versions, not supported for WAR version)
     */
    public MockServerClient stop() {
        return stop(false);
    }

    public MockServerClient stop(boolean ignoreFailure) {
        try {
            sendRequest(request().withMethod("PUT").withPath(calculatePath("stop")));
            if (isRunning()) {
                for (int i = 0; isRunning() && i < 50; i++) {
                    TimeUnit.MILLISECONDS.sleep(5);
                }
            }
        } catch (Exception e) {
            if (!ignoreFailure) {
                logger.warn("Failed to send stop request to MockServer " + e.getMessage());
            }
        }
        return this;
    }

    /**
     * Reset MockServer by clearing all expectations
     */
    public MockServerClient reset() {
        sendRequest(request().withMethod("PUT").withPath(calculatePath("reset")));
        return this;
    }

    /**
     * Clear all expectations and logs that match the http
     *
     * @param httpRequest the http request that is matched against when deciding whether to clear each expectation if null all expectations are cleared
     */
    public MockServerClient clear(HttpRequest httpRequest) {
        sendRequest(request().withMethod("PUT").withPath(calculatePath("clear")).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", Charsets.UTF_8));
        return this;
    }


    /**
     * Clear expectations, logs or both that match the http
     *
     * @param httpRequest the http request that is matched against when deciding whether to clear each expectation if null all expectations are cleared
     * @param type the type to clear, EXPECTATION, LOG or BOTH
     */
    public MockServerClient clear(HttpRequest httpRequest, TYPE type) {
        sendRequest(request().withMethod("PUT").withPath(calculatePath("clear")).withQueryStringParameter("type", type.name().toLowerCase()).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", Charsets.UTF_8));
        return this;
    }

    void sendExpectation(Expectation expectation) {
        HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("expectation")).withBody(expectation != null ? expectationSerializer.serialize(expectation) : "", Charsets.UTF_8));
        if (httpResponse != null && httpResponse.getStatusCode() != 201) {
            throw new ClientException(formatErrorMessage(System.getProperty("line.separator") + "error: %swhile submitted expectation: %s", httpResponse.getBody() + System.getProperty("line.separator"), expectation));
        }
    }

    public enum TYPE {
        LOG,
        EXPECTATION,
        BOTH;
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
    public MockServerClient verify(HttpRequest... httpRequests) throws AssertionError {
        if (httpRequests == null || httpRequests.length == 0 || httpRequests[0] == null) {
            throw new IllegalArgumentException("verify(HttpRequest...) requires a non null non empty array of HttpRequest objects");
        }

        VerificationSequence verificationSequence = new VerificationSequence().withRequests(httpRequests);
        String result = sendRequest(request().withMethod("PUT").withPath(calculatePath("verifySequence")).withBody(verificationSequenceSerializer.serialize(verificationSequence), Charsets.UTF_8)).getBodyAsString();

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
    public MockServerClient verify(HttpRequest httpRequest, VerificationTimes times) throws AssertionError {
        if (httpRequest == null) {
            throw new IllegalArgumentException("verify(HttpRequest, VerificationTimes) requires a non null HttpRequest object");
        }
        if (times == null) {
            throw new IllegalArgumentException("verify(HttpRequest, VerificationTimes) requires a non null VerificationTimes object");
        }

        Verification verification = new Verification().withRequest(httpRequest).withTimes(times);
        String result = sendRequest(request().withMethod("PUT").withPath(calculatePath("verify")).withBody(verificationSerializer.serialize(verification), Charsets.UTF_8)).getBodyAsString();

        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
        return this;
    }

    /**
     * Verify no requests have been have been sent.
     *
     * @throws AssertionError if any request has been found
     */
    public MockServerClient verifyZeroInteractions() throws AssertionError {
        Verification verification = new Verification().withRequest(request()).withTimes(exactly(0));
        String result = sendRequest(request().withMethod("PUT").withPath(calculatePath("verify")).withBody(verificationSerializer.serialize(verification), Charsets.UTF_8)).getBodyAsString();

        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
        return this;
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter, use null for the parameter to retrieve all requests
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @return an array of all expectations that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public HttpRequest[] retrieveRecordedRequests(HttpRequest httpRequest) {
        HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("retrieve")).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", Charsets.UTF_8));
        return httpRequestSerializer.deserializeArray(httpResponse.getBodyAsString());
    }

    /**
     * Retrieve the already setup expectations match the httpRequest parameter, use null for the parameter to retrieve all expectations
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @return an array of all expectations that have been setup
     */
    public Expectation[] retrieveExistingExpectations(HttpRequest httpRequest) {
        HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("retrieve")).withQueryStringParameter("type", "expectation").withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", Charsets.UTF_8));
        return expectationSerializer.deserializeArray(httpResponse.getBodyAsString());
    }
}
