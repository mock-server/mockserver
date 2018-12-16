package org.mockserver.client;

import com.google.common.base.Strings;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.Version;
import org.mockserver.client.MockServerEventBus.EventType;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.netty.SocketConnectionException;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.*;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.mock.HttpStateHandler.LOG_SEPARATOR;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.PortBinding.portBinding;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class MockServerClient implements java.io.Closeable {

    protected final MockServerLogger mockServerLogger = new MockServerLogger(this.getClass());
    private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    private final String host;
    private final String contextPath;
    private final Class<MockServerClient> clientClass;
    protected Future<Integer> portFuture;
    private Boolean secure;
    private Integer port;
    private NettyHttpClient nettyHttpClient = new NettyHttpClient(eventLoopGroup, null);
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer(mockServerLogger);
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer(mockServerLogger);
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer(mockServerLogger);
    private VerificationSerializer verificationSerializer = new VerificationSerializer(mockServerLogger);
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer(mockServerLogger);

    /**
     * Start the client communicating to a MockServer on localhost at the port
     * specified with the Future
     *
     * @param portFuture the port for the MockServer to communicate with
     */
    public MockServerClient(Future<Integer> portFuture) {
        this.clientClass = MockServerClient.class;
        this.host = "127.0.0.1";
        this.portFuture = portFuture;
        this.contextPath = "";
    }

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * for example:
     *
     * MockServerClient mockServerClient = new MockServerClient("localhost", 1080);
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
     * MockServerClient mockServerClient = new MockServerClient("localhost", 1080, "/mockserver");
     *
     * @param host        the host for the MockServer to communicate with
     * @param port        the port for the MockServer to communicate with
     * @param contextPath the context path that the MockServer war is deployed to
     */
    public MockServerClient(String host, int port, String contextPath) {
        this.clientClass = MockServerClient.class;
        if (StringUtils.isEmpty(host)) {
            throw new IllegalArgumentException("Host can not be null or empty");
        }
        if (contextPath == null) {
            throw new IllegalArgumentException("ContextPath can not be null");
        }
        this.host = host;
        this.port = port;
        this.contextPath = contextPath;
    }

    EventLoopGroup getEventLoopGroup() {
        return eventLoopGroup;
    }

    public boolean isSecure() {
        return secure != null ? secure : false;
    }

    public MockServerClient withSecure(boolean secure) {
        this.secure = secure;
        return this;
    }

    private int port() {
        if (this.port == null) {
            try {
                port = portFuture.get();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return this.port;
    }

    public InetSocketAddress remoteAddress() {
        return new InetSocketAddress(this.host, port());
    }

    public String contextPath() {
        return contextPath;
    }

    private String calculatePath(String path) {
        String cleanedPath = "/mockserver/" + path;
        if (!Strings.isNullOrEmpty(contextPath)) {
            cleanedPath =
                (!contextPath.startsWith("/") ? "/" : "") +
                    contextPath +
                    (!contextPath.endsWith("/") ? "/" : "") +
                    (cleanedPath.startsWith("/") ? cleanedPath.substring(1) : cleanedPath);
        }
        return (!cleanedPath.startsWith("/") ? "/" : "") + cleanedPath;
    }

    private HttpResponse sendRequest(HttpRequest request) {
        try {
            if (secure != null) {
                request.withSecure(secure);
            }

            HttpResponse response = nettyHttpClient.sendRequest(
                request.withHeader(HOST.toString(), this.host + ":" + port()),
                ConfigurationProperties.maxSocketTimeout(),
                TimeUnit.MILLISECONDS
            );

            if (response != null) {
                if (response.getStatusCode() != null &&
                    response.getStatusCode() == BAD_REQUEST.code()) {
                    throw new IllegalArgumentException(response.getBodyAsString());
                }
                String serverVersion = response.getFirstHeader("version");
                String clientVersion = Version.getVersion();
                if (!Strings.isNullOrEmpty(serverVersion) &&
                    !Strings.isNullOrEmpty(clientVersion) &&
                    !clientVersion.equals(serverVersion)) {
                    throw new ClientException("Client version \"" + clientVersion + "\" does not match server version \"" + serverVersion + "\"");
                }
            }

            return response;
        } catch (RuntimeException rex) {
            if (!Strings.isNullOrEmpty(rex.getMessage()) && (rex.getMessage().contains("executor not accepting a task") || rex.getMessage().contains("loop shut down"))) {
                throw new IllegalStateException(this.getClass().getSimpleName() + " has already been closed, please create new " + this.getClass().getSimpleName() + " instance");
            } else {
                throw rex;
            }
        }
    }

    /**
     * Returns whether MockServer is running
     */
    public boolean isRunning() {
        return isRunning(10, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns whether server MockServer is running, by polling the MockServer a configurable amount of times
     */
    public boolean isRunning(int attempts, long timeout, TimeUnit timeUnit) {
        try {
            HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("status")));
            if (httpResponse.getStatusCode() == HttpStatusCode.OK_200.code()) {
                return true;
            } else if (attempts == 0) {
                return false;
            } else {
                try {
                    timeUnit.sleep(timeout);
                } catch (InterruptedException e) {
                    // ignore interrupted exception
                }
                return isRunning(attempts - 1, timeout, timeUnit);
            }
        } catch (SocketConnectionException sce) {
            return false;
        }
    }

    /**
     * Bind new ports to listen on
     */
    public List<Integer> bind(Integer... ports) {
        String boundPorts = sendRequest(request().withMethod("PUT").withPath(calculatePath("bind")).withBody(portBindingSerializer.serialize(portBinding(ports)), StandardCharsets.UTF_8)).getBodyAsString();
        return portBindingSerializer.deserialize(boundPorts).getPorts();
    }

    /**
     * Stop MockServer gracefully (only support for Netty version, not supported for WAR version)
     */
    public MockServerClient stop() {
        return stop(true);
    }

    /**
     * Stop MockServer gracefully (only support for Netty version, not supported for WAR version)
     */
    public MockServerClient stop(boolean ignoreFailure) {
        MockServerEventBus.getInstance().publish(EventType.STOP);
        try {
            sendRequest(request().withMethod("PUT").withPath(calculatePath("stop")));
            if (isRunning()) {
                for (int i = 0; isRunning() && i < 50; i++) {
                    TimeUnit.MILLISECONDS.sleep(5);
                }
            }
        } catch (RejectedExecutionException ree) {
            mockServerLogger.trace("Request rejected because closing down but logging at trace level for information just in case due to some other actual error " + ree);
        } catch (Exception e) {
            if (!ignoreFailure) {
                mockServerLogger.warn("Failed to send stop request to MockServer " + e.getMessage());
            }
        }
        if (!eventLoopGroup.isShuttingDown()) {
            eventLoopGroup.shutdownGracefully();
        }
        return clientClass.cast(this);
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Reset MockServer by clearing all expectations
     */
    public MockServerClient reset() {
        MockServerEventBus.getInstance().publish(EventType.RESET);
        sendRequest(request().withMethod("PUT").withPath(calculatePath("reset")));
        return clientClass.cast(this);
    }

    /**
     * Clear all expectations and logs that match the http
     *
     * @param httpRequest the http request that is matched against when deciding whether to clear each expectation if null all expectations are cleared
     */
    public MockServerClient clear(HttpRequest httpRequest) {
        sendRequest(request().withMethod("PUT").withPath(calculatePath("clear")).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", StandardCharsets.UTF_8));
        return clientClass.cast(this);
    }

    /**
     * Clear expectations, logs or both that match the http
     *
     * @param httpRequest the http request that is matched against when deciding whether to clear each expectation if null all expectations are cleared
     * @param type        the type to clear, EXPECTATION, LOG or BOTH
     */
    public MockServerClient clear(HttpRequest httpRequest, ClearType type) {
        sendRequest(request().withMethod("PUT").withPath(calculatePath("clear")).withQueryStringParameter("type", type.name().toLowerCase()).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", StandardCharsets.UTF_8));
        return clientClass.cast(this);
    }

    /**
     * Verify a list of requests have been sent in the order specified for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      request()
     *          .withPath("/first_request")
     *          .withBody("some_request_body"),
     *      request()
     *          .withPath("/second_request")
     *          .withBody("some_request_body")
     *  );
     * </pre>
     *
     * @param httpRequests the http requests that must be matched for this verification to pass
     * @throws AssertionError if the request has not been found
     */
    public MockServerClient verify(HttpRequest... httpRequests) throws AssertionError {
        if (httpRequests == null || httpRequests.length == 0 || httpRequests[0] == null) {
            throw new IllegalArgumentException("verify(HttpRequest...) requires a non null non empty array of HttpRequest objects");
        }

        VerificationSequence verificationSequence = new VerificationSequence().withRequests(httpRequests);
        String result = sendRequest(request().withMethod("PUT").withPath(calculatePath("verifySequence")).withBody(verificationSequenceSerializer.serialize(verificationSequence), StandardCharsets.UTF_8)).getBodyAsString();

        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
        return clientClass.cast(this);
    }

    /**
     * Verify a request has been sent for example:
     * <pre>
     * mockServerClient
     *  .verify(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      VerificationTimes.exactly(3)
     *  );
     * </pre>
     * VerificationTimes supports multiple static factory methods:
     *
     * once()      - verify the request was only received once
     * exactly(n)  - verify the request was only received exactly n times
     * atLeast(n)  - verify the request was only received at least n times
     *
     * @param httpRequest the http request that must be matched for this verification to pass
     * @param times       the number of times this request must be matched
     * @throws AssertionError if the request has not been found
     */
    public MockServerClient verify(HttpRequest httpRequest, VerificationTimes times) throws AssertionError {
        if (httpRequest == null) {
            throw new IllegalArgumentException("verify(HttpRequest, VerificationTimes) requires a non null HttpRequest object");
        }
        if (times == null) {
            throw new IllegalArgumentException("verify(HttpRequest, VerificationTimes) requires a non null VerificationTimes object");
        }

        Verification verification = verification().withRequest(httpRequest).withTimes(times);
        String result = sendRequest(request().withMethod("PUT").withPath(calculatePath("verify")).withBody(verificationSerializer.serialize(verification), StandardCharsets.UTF_8)).getBodyAsString();

        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
        return clientClass.cast(this);
    }

    /**
     * Verify no requests have been have been sent.
     *
     * @throws AssertionError if any request has been found
     */
    public MockServerClient verifyZeroInteractions() throws AssertionError {
        Verification verification = verification().withRequest(request()).withTimes(exactly(0));
        String result = sendRequest(request().withMethod("PUT").withPath(calculatePath("verify")).withBody(verificationSerializer.serialize(verification), StandardCharsets.UTF_8)).getBodyAsString();

        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
        return clientClass.cast(this);
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter, use null for the parameter to retrieve all requests
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @return an array of all requests that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public HttpRequest[] retrieveRecordedRequests(HttpRequest httpRequest) {
        String recordedRequests = retrieveRecordedRequests(httpRequest, Format.JSON);
        if (StringUtils.isNotEmpty(recordedRequests) && !recordedRequests.equals("[]")) {
            return httpRequestSerializer.deserializeArray(recordedRequests);
        } else {
            return new HttpRequest[0];
        }
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter, use null for the parameter to retrieve all requests
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @param format      the format to retrieve the expectations, either JAVA or JSON
     * @return an array of all requests that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public String retrieveRecordedRequests(HttpRequest httpRequest, Format format) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", format.name())
                .withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", StandardCharsets.UTF_8)
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the request-response combinations that have been recorded as a list of expectations, only those that match the httpRequest parameter are returned, use null to retrieve all requests
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @return an array of all expectations that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public Expectation[] retrieveRecordedExpectations(HttpRequest httpRequest) {
        String recordedExpectations = retrieveRecordedExpectations(httpRequest, Format.JSON);
        if (!Strings.isNullOrEmpty(recordedExpectations) && !recordedExpectations.equals("[]")) {
            return expectationSerializer.deserializeArray(recordedExpectations);
        } else {
            return new Expectation[0];
        }
    }

    /**
     * Retrieve the request-response combinations that have been recorded as a list of expectations, only those that match the httpRequest parameter are returned, use null to retrieve all requests
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @param format      the format to retrieve the expectations, either JAVA or JSON
     * @return an array of all expectations that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public String retrieveRecordedExpectations(HttpRequest httpRequest, Format format) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", format.name())
                .withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", StandardCharsets.UTF_8)
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the logs associated to a specific requests, this shows all logs for expectation matching, verification, clearing, etc
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @return all log messages recorded by the MockServer when creating expectations, matching expectations, performing verification, clearing logs, etc
     */
    public String retrieveLogMessages(HttpRequest httpRequest) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.LOGS.name())
                .withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", StandardCharsets.UTF_8)
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the logs associated to a specific requests, this shows all logs for expectation matching, verification, clearing, etc
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @return an array of all log messages recorded by the MockServer when creating expectations, matching expectations, performing verification, clearing logs, etc
     */
    public String[] retrieveLogMessagesArray(HttpRequest httpRequest) {
        return retrieveLogMessages(httpRequest).split(LOG_SEPARATOR);
    }

    /**
     * Specify an unlimited expectation that will respond regardless of the number of matching http
     * for example:
     * <pre>
     * mockServerClient
     *  .when(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body")
     *  )
     *  .respond(
     *      response()
     *          .withBody("some_response_body")
     *          .withHeader("responseName", "responseValue")
     *  )
     * </pre>
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
     * <pre>
     * mockServerClient
     *  .when(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      Times.exactly(5)
     *  )
     *  .respond(
     *      response()
     *          .withBody("some_response_body")
     *          .withHeader("responseName", "responseValue")
     *  )
     * </pre>
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
     * <pre>
     * mockServerClient
     *  .when(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      Times.exactly(5),
     *      TimeToLive.exactly(TimeUnit.SECONDS, 120)
     *  )
     *  .respond(
     *      response()
     *          .withBody("some_response_body")
     *          .withHeader("responseName", "responseValue")
     *  )
     * </pre>
     *
     * @param httpRequest the http request that must be matched for this expectation to respond
     * @param times       the number of times to respond when this http is matched
     * @param timeToLive  the length of time from when the server receives the expectation that the expectation should be active
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(HttpRequest httpRequest, Times times, TimeToLive timeToLive) {
        return new ForwardChainExpectation(this, new Expectation(httpRequest, times, timeToLive));
    }

    void sendExpectation(Expectation expectation) {
        HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("expectation")).withBody(expectation != null ? expectationSerializer.serialize(expectation) : "", StandardCharsets.UTF_8));
        if (httpResponse != null && httpResponse.getStatusCode() != 201) {
            throw new ClientException(formatLogMessage("error:{}while submitted expectation:{}", httpResponse.getBody(), expectation));
        }
    }

    /**
     * Retrieve the active expectations match the httpRequest parameter, use null for the parameter to retrieve all expectations
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @return an array of all expectations that have been setup and have not expired
     */
    public Expectation[] retrieveActiveExpectations(HttpRequest httpRequest) {
        String activeExpectations = retrieveActiveExpectations(httpRequest, Format.JSON);
        if (!Strings.isNullOrEmpty(activeExpectations) && !activeExpectations.equals("[]")) {
            return expectationSerializer.deserializeArray(activeExpectations);
        } else {
            return new Expectation[0];
        }
    }

    /**
     * Retrieve the active expectations match the httpRequest parameter, use null for the parameter to retrieve all expectations
     *
     * @param httpRequest the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @param format      the format to retrieve the expectations, either JAVA or JSON
     * @return an array of all expectations that have been setup and have not expired
     */
    public String retrieveActiveExpectations(HttpRequest httpRequest, Format format) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", format.name())
                .withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", StandardCharsets.UTF_8)
        );
        return httpResponse.getBodyAsString();
    }
}
