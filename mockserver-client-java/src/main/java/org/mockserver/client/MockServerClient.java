package org.mockserver.client;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.mockserver.Version;
import org.mockserver.client.MockServerEventBus.EventType;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.OpenAPIExpectation;
import org.mockserver.model.*;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.*;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.mockserver.stop.Stoppable;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;

import java.awt.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.configuration.ConfigurationProperties.maxFutureTimeout;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.mock.HttpState.LOG_SEPARATOR;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.MediaType.APPLICATION_JSON_UTF_8;
import static org.mockserver.model.PortBinding.portBinding;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class MockServerClient implements Stoppable {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(MockServerClient.class);
    private static final Map<Integer, MockServerEventBus> EVENT_BUS_MAP = new ConcurrentHashMap<>();
    private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(5, new Scheduler.SchedulerThreadFactory(this.getClass().getSimpleName() + "-eventLoop"));
    private final String host;
    private final String contextPath;
    private final Class<MockServerClient> clientClass;
    protected CompletableFuture<Integer> portFuture;
    private Boolean secure;
    private Integer port;
    private HttpRequest requestOverride;
    @SuppressWarnings("FieldMayBeFinal")
    private NettyHttpClient nettyHttpClient = new NettyHttpClient(MOCK_SERVER_LOGGER, eventLoopGroup, null, false, new NettySslContextFactory(MOCK_SERVER_LOGGER));
    @SuppressWarnings("FieldMayBeFinal")
    private RequestDefinitionSerializer requestDefinitionSerializer = new RequestDefinitionSerializer(MOCK_SERVER_LOGGER);
    @SuppressWarnings("FieldMayBeFinal")
    private ExpectationIdSerializer expectationIdSerializer = new ExpectationIdSerializer(MOCK_SERVER_LOGGER);
    @SuppressWarnings("FieldMayBeFinal")
    private LogEventRequestAndResponseSerializer httpRequestResponseSerializer = new LogEventRequestAndResponseSerializer(MOCK_SERVER_LOGGER);
    @SuppressWarnings("FieldMayBeFinal")
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer(MOCK_SERVER_LOGGER);
    @SuppressWarnings("FieldMayBeFinal")
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer(MOCK_SERVER_LOGGER);
    @SuppressWarnings("FieldMayBeFinal")
    private OpenAPIExpectationSerializer openAPIExpectationSerializer = new OpenAPIExpectationSerializer(MOCK_SERVER_LOGGER);
    @SuppressWarnings("FieldMayBeFinal")
    private VerificationSerializer verificationSerializer = new VerificationSerializer(MOCK_SERVER_LOGGER);
    @SuppressWarnings("FieldMayBeFinal")
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer(MOCK_SERVER_LOGGER);
    private final CompletableFuture<MockServerClient> stopFuture = new CompletableFuture<>();

    /**
     * Start the client communicating to a MockServer on localhost at the port
     * specified with the Future
     *
     * @param portFuture the port for the MockServer to communicate with
     */
    public MockServerClient(CompletableFuture<Integer> portFuture) {
        this.clientClass = MockServerClient.class;
        this.host = "127.0.0.1";
        this.portFuture = portFuture;
        this.contextPath = "";
    }

    /**
     * Start the client communicating to a MockServer at the specified host and port
     * for example:
     * <p>
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
     * <p>
     * MockServerClient mockServerClient = new MockServerClient("localhost", 1080, "/mockserver");
     *
     * @param host        the host for the MockServer to communicate with
     * @param port        the port for the MockServer to communicate with
     * @param contextPath the context path that the MockServer war is deployed to
     */
    public MockServerClient(String host, int port, String contextPath) {
        this.clientClass = MockServerClient.class;
        if (isEmpty(host)) {
            throw new IllegalArgumentException("Host can not be null or empty");
        }
        if (contextPath == null) {
            throw new IllegalArgumentException("ContextPath can not be null");
        }
        this.host = host;
        this.port = port;
        this.contextPath = contextPath;
    }

    public MockServerClient setRequestOverride(HttpRequest requestOverride) {
        if (requestOverride == null) {
            throw new IllegalArgumentException("Request with default properties can not be null");
        } else {
            this.requestOverride = requestOverride;
        }
        return this;
    }

    private MockServerEventBus getMockServerEventBus() {
        if (EVENT_BUS_MAP.get(this.port()) == null) {
            EVENT_BUS_MAP.put(this.port(), new MockServerEventBus());
        }
        return EVENT_BUS_MAP.get(this.port());
    }

    private void removeMockServerEventBus() {
        EVENT_BUS_MAP.remove(this.port());
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
                port = portFuture.get(maxFutureTimeout(), MILLISECONDS);
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

    public Integer getPort() {
        return port();
    }

    @SuppressWarnings("DuplicatedCode")
    private String calculatePath(String path) {
        String cleanedPath = "/mockserver/" + path;
        if (isNotBlank(contextPath)) {
            cleanedPath =
                (!contextPath.startsWith("/") ? "/" : "") +
                    contextPath +
                    (!contextPath.endsWith("/") ? "/" : "") +
                    (cleanedPath.startsWith("/") ? cleanedPath.substring(1) : cleanedPath);
        }
        return (!cleanedPath.startsWith("/") ? "/" : "") + cleanedPath;
    }

    private HttpResponse sendRequest(HttpRequest request, boolean ignoreErrors) {
        if (!stopFuture.isDone()) {
            try {
                if (!request.containsHeader(CONTENT_TYPE.toString())
                    && request.getBody() != null
                    && isNotBlank(request.getBody().getContentType())) {
                    request.withHeader(CONTENT_TYPE.toString(), request.getBody().getContentType());
                }
                if (secure != null) {
                    request.withSecure(secure);
                }
                if (requestOverride != null) {
                    request = request.update(requestOverride);
                }
                HttpResponse response = nettyHttpClient.sendRequest(
                    request.withHeader(HOST.toString(), this.host + ":" + port()),
                    ConfigurationProperties.maxSocketTimeout(),
                    TimeUnit.MILLISECONDS,
                    ignoreErrors
                );

                if (response != null) {
                    if (response.getStatusCode() != null &&
                        response.getStatusCode() == BAD_REQUEST.code()) {
                        throw new IllegalArgumentException(response.getBodyAsString());
                    }
                    String serverVersion = response.getFirstHeader("version");
                    String clientVersion = Version.getVersion();
                    if (!Version.matchesMajorMinorVersion(serverVersion)) {
                        throw new ClientException("Client version \"" + clientVersion + "\" major and minor versions do not match server version \"" + serverVersion + "\"");
                    }
                }

                return response;
            } catch (RuntimeException rex) {
                if (isNotBlank(rex.getMessage()) && (rex.getMessage().contains("executor not accepting a task") || rex.getMessage().contains("loop shut down"))) {
                    throw new IllegalStateException(this.getClass().getSimpleName() + " has already been closed, please create new " + this.getClass().getSimpleName() + " instance");
                } else {
                    throw rex;
                }
            }
        } else {
            throw new IllegalStateException(this.getClass().getSimpleName() + " has already been stopped, please create new " + this.getClass().getSimpleName() + " instance");
        }
    }

    private HttpResponse sendRequest(HttpRequest request) {
        return sendRequest(request, false);
    }

    /**
     * Launch UI and wait the default period to allow the UI to launch and start collecting logs,
     * this ensures that the log are visible in the UI even if MockServer is shutdown by a test
     * shutdown function, such as After, AfterClass, AfterAll, etc
     */
    public MockServerClient openUI() {
        return openUI(SECONDS, 1);
    }

    /**
     * Launch UI and wait a specified period to allow the UI to launch and start collecting logs,
     * this ensures that the log are visible in the UI even if MockServer is shutdown by a test
     * shutdown function, such as After, AfterClass, AfterAll, etc
     *
     * @param timeUnit TimeUnit the time unit, for example TimeUnit.SECONDS
     * @param pause    the number of time units to delay before the function returns to ensure the UI is receiving logs
     */
    public MockServerClient openUI(TimeUnit timeUnit, long pause) {
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop != null) {
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI("http" + (Boolean.TRUE.equals(secure) ? "s" : "") + "://" + host + ":" + port() + "/mockserver/dashboard"));
                    timeUnit.sleep(pause);
                } else {
                    if (MockServerLogger.isEnabled(WARN)) {
                        MOCK_SERVER_LOGGER.logEvent(
                            new LogEntry()
                                .setLogLevel(WARN)
                                .setMessageFormat("browse to URL not supported by the desktop instance from JVM")
                        );
                    }
                }
            } else {
                if (MockServerLogger.isEnabled(WARN)) {
                    MOCK_SERVER_LOGGER.logEvent(
                        new LogEntry()
                            .setLogLevel(WARN)
                            .setMessageFormat("unable to obtain the desktop instance from JVM")
                    );
                }
            }
        } catch (Throwable throwable) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setLogLevel(ERROR)
                    .setMessageFormat("exception while attempting to launch UI" + (isNotBlank(throwable.getMessage()) ? " " + throwable.getMessage() : ""))
                    .setThrowable(throwable)
            );
            throw new ClientException("exception while attempting to launch UI" + (isNotBlank(throwable.getMessage()) ? " " + throwable.getMessage() : ""));
        }
        return this;
    }

    /**
     * Returns whether MockServer is running, if called too quickly after starting MockServer
     * this may return false because MockServer has not yet started, to ensure MockServer has
     * started use hasStarted()
     *
     * @deprecated use hasStopped() or hasStarted() instead
     */
    @Deprecated
    @SuppressWarnings({"DeprecatedIsStillUsed", "RedundantSuppression"})
    public boolean isRunning() {
        return isRunning(10, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns whether server MockServer is running, by polling the MockServer a configurable
     * amount of times.  If called too quickly after starting MockServer this may return false
     * because MockServer has not yet started, to ensure MockServer has started use hasStarted()
     *
     * @deprecated use hasStopped() or hasStarted() instead
     */
    @Deprecated
    public boolean isRunning(int attempts, long timeout, TimeUnit timeUnit) {
        try {
            HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("status")), true);
            if (httpResponse != null && httpResponse.getStatusCode() == HttpStatusCode.OK_200.code()) {
                return true;
            } else if (attempts <= 0) {
                return false;
            } else {
                try {
                    timeUnit.sleep(timeout);
                } catch (InterruptedException e) {
                    // ignore interrupted exception
                }
                return isRunning(attempts - 1, timeout, timeUnit);
            }
        } catch (SocketConnectionException | IllegalStateException sce) {
            if (MockServerLogger.isEnabled(TRACE)) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setMessageFormat("exception while checking if MockServer is running - " + sce.getMessage() + " if MockServer was stopped this exception is expected")
                        .setThrowable(sce)
                );
            }
            return false;
        }
    }

    /**
     * Returns whether MockServer has stopped, if called too quickly after starting MockServer
     * this may return false because MockServer has not yet started, to ensure MockServer has
     * started use hasStarted()
     */
    public boolean hasStopped() {
        return hasStopped(10, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns whether server MockServer has stopped, by polling the MockServer a configurable
     * amount of times.  If called too quickly after starting MockServer this may return false
     * because MockServer has not yet started, to ensure MockServer has started use hasStarted()
     */
    public boolean hasStopped(int attempts, long timeout, TimeUnit timeUnit) {
        try {
            HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("status")), true);
            if (httpResponse != null && httpResponse.getStatusCode() == HttpStatusCode.OK_200.code()) {
                if (attempts <= 0) {
                    return false;
                } else {
                    try {
                        timeUnit.sleep(timeout);
                    } catch (InterruptedException e) {
                        // ignore interrupted exception
                    }
                    return hasStopped(attempts - 1, timeout, timeUnit);
                }
            } else {
                return true;
            }
        } catch (SocketConnectionException | IllegalStateException sce) {
            return true;
        }
    }

    /**
     * Returns whether MockServer has started, if called after MockServer has been stopped
     * this method will block for 5 seconds while confirming MockServer is not starting
     */
    public boolean hasStarted() {
        return hasStarted(10, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns whether server MockServer has started, by polling the MockServer a configurable amount of times
     */
    public boolean hasStarted(int attempts, long timeout, TimeUnit timeUnit) {
        try {
            HttpResponse httpResponse = sendRequest(request().withMethod("PUT").withPath(calculatePath("status")));
            if (httpResponse.getStatusCode() == HttpStatusCode.OK_200.code()) {
                return true;
            } else if (attempts <= 0) {
                return false;
            } else {
                try {
                    timeUnit.sleep(timeout);
                } catch (InterruptedException e) {
                    // ignore interrupted exception
                }
                return hasStarted(attempts - 1, timeout, timeUnit);
            }
        } catch (SocketConnectionException | IllegalStateException sce) {
            if (attempts <= 0) {
                if (MockServerLogger.isEnabled(DEBUG)) {
                    MOCK_SERVER_LOGGER.logEvent(
                        new LogEntry()
                            .setLogLevel(DEBUG)
                            .setMessageFormat("exception while checking if MockServer has started - " + sce.getMessage())
                            .setThrowable(sce)
                    );
                }
                return false;
            } else {
                try {
                    timeUnit.sleep(timeout);
                } catch (InterruptedException e) {
                    // ignore interrupted exception
                }
                return hasStarted(attempts - 1, timeout, timeUnit);
            }
        }
    }

    /**
     * Bind new ports to listen on
     */
    public List<Integer> bind(Integer... ports) {
        String boundPorts = sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("bind"))
                .withBody(portBindingSerializer.serialize(portBinding(ports)), StandardCharsets.UTF_8)
        ).getBodyAsString();
        return portBindingSerializer.deserialize(boundPorts).getPorts();
    }

    /**
     * Stop MockServer gracefully (only support for Netty version, not supported for WAR version)
     */
    public Future<MockServerClient> stopAsync() {
        return stop(true);
    }

    /**
     * Stop MockServer gracefully (only support for Netty version, not supported for WAR version)
     */
    public void stop() {
        try {
            stopAsync().get(10, SECONDS);
        } catch (Throwable throwable) {
            if (MockServerLogger.isEnabled(DEBUG)) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setLogLevel(DEBUG)
                        .setMessageFormat("exception while stopping - " + throwable.getMessage())
                        .setThrowable(throwable)
                );
            }
        }
    }

    /**
     * Stop MockServer gracefully (only support for Netty version, not supported for WAR version)
     */
    public Future<MockServerClient> stop(boolean ignoreFailure) {
        if (!stopFuture.isDone()) {
            getMockServerEventBus().publish(EventType.STOP);
            removeMockServerEventBus();
            new Scheduler.SchedulerThreadFactory("ClientStop").newThread(() -> {
                try {
                    sendRequest(request().withMethod("PUT").withPath(calculatePath("stop")));
                    if (!hasStopped()) {
                        for (int i = 0; !hasStopped() && i < 50; i++) {
                            TimeUnit.MILLISECONDS.sleep(5);
                        }
                    }
                } catch (RejectedExecutionException ree) {
                    if (!ignoreFailure && MockServerLogger.isEnabled(TRACE)) {
                        MOCK_SERVER_LOGGER.logEvent(
                            new LogEntry()
                                .setLogLevel(TRACE)
                                .setMessageFormat("request rejected while closing down, logging in case due other error " + ree)
                                .setThrowable(ree)
                        );
                    }
                } catch (Exception e) {
                    if (!ignoreFailure && MockServerLogger.isEnabled(WARN)) {
                        MOCK_SERVER_LOGGER.logEvent(
                            new LogEntry()
                                .setLogLevel(WARN)
                                .setMessageFormat("failed to send stop request to MockServer " + e.getMessage())
                        );
                    }
                }
                if (!eventLoopGroup.isShuttingDown()) {
                    eventLoopGroup.shutdownGracefully();
                }
                stopFuture.complete(clientClass.cast(this));
            }).start();
        }
        return stopFuture;
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Reset MockServer by clearing all expectations
     */
    public MockServerClient reset() {
        getMockServerEventBus().publish(EventType.RESET);
        sendRequest(
            request()
                .withMethod("PUT")
                .withPath(calculatePath("reset"))
        );
        return clientClass.cast(this);
    }

    /**
     * Clear all expectations and logs that match the http
     *
     * @param requestDefinition the http request that is matched against when deciding whether to clear each expectation if null all expectations are cleared
     */
    public MockServerClient clear(RequestDefinition requestDefinition) {
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("clear"))
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8)
        );
        return clientClass.cast(this);
    }

    /**
     * Clear all expectations and logs that match the http
     *
     * @param expectationId the http request that is matched against when deciding whether to clear each expectation if null all expectations are cleared
     */
    public MockServerClient clear(ExpectationId expectationId) {
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("clear"))
                .withBody(expectationId != null ? expectationIdSerializer.serialize(expectationId) : "", StandardCharsets.UTF_8)
        );
        return clientClass.cast(this);
    }

    /**
     * Clear expectations, logs or both that match the http
     *
     * @param requestDefinition the http request that is matched against when deciding whether to clear each expectation if null all expectations are cleared
     * @param type              the type to clear, EXPECTATION, LOG or BOTH
     */
    public MockServerClient clear(RequestDefinition requestDefinition, ClearType type) {
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("clear"))
                .withQueryStringParameter("type", type.name().toLowerCase())
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8)
        );
        return clientClass.cast(this);
    }

    /**
     * Clear expectations, logs or both that match the http
     *
     * @param expectationId the http request that is matched against when deciding whether to clear each expectation if null all expectations are cleared
     * @param type              the type to clear, EXPECTATION, LOG or BOTH
     */
    public MockServerClient clear(ExpectationId expectationId, ClearType type) {
        sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("clear"))
                .withQueryStringParameter("type", type.name().toLowerCase())
                .withBody(expectationId != null ? expectationIdSerializer.serialize(expectationId) : "", StandardCharsets.UTF_8)
        );
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
     * @param requestDefinitions the http requests that must be matched for this verification to pass
     * @throws AssertionError if the request has not been found
     */
    public MockServerClient verify(RequestDefinition... requestDefinitions) throws AssertionError {
        if (requestDefinitions == null || requestDefinitions.length == 0 || requestDefinitions[0] == null) {
            throw new IllegalArgumentException("verify(RequestDefinition...) requires a non-null non-empty array of RequestDefinition objects");
        }

        VerificationSequence verificationSequence = new VerificationSequence().withRequests(requestDefinitions);
        String result = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("verifySequence"))
                .withBody(verificationSequenceSerializer.serialize(verificationSequence), StandardCharsets.UTF_8)
        ).getBodyAsString();

        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
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
     * @param expectationIds the http requests that must be matched for this verification to pass
     * @throws AssertionError if the request has not been found
     */
    public MockServerClient verify(ExpectationId... expectationIds) throws AssertionError {
        if (expectationIds == null || expectationIds.length == 0 || expectationIds[0] == null) {
            throw new IllegalArgumentException("verify(RequestDefinition...) requires a non-null non-empty array of RequestDefinition objects");
        }

        VerificationSequence verificationSequence = new VerificationSequence().withExpectationIds(expectationIds);
        String result = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("verifySequence"))
                .withBody(verificationSequenceSerializer.serialize(verificationSequence), StandardCharsets.UTF_8)
        ).getBodyAsString();

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
     * <p>
     * once()      - verify the request was only received once
     * exactly(n)  - verify the request was only received exactly n times
     * atLeast(n)  - verify the request was only received at least n times
     *
     * @param requestDefinition the http request that must be matched for this verification to pass
     * @param times             the number of times this request must be matched
     * @throws AssertionError if the request has not been found
     */
    @SuppressWarnings("DuplicatedCode")
    public MockServerClient verify(RequestDefinition requestDefinition, VerificationTimes times) throws AssertionError {
        if (requestDefinition == null) {
            throw new IllegalArgumentException("verify(RequestDefinition, VerificationTimes) requires a non null RequestDefinition object");
        }
        if (times == null) {
            throw new IllegalArgumentException("verify(RequestDefinition, VerificationTimes) requires a non null VerificationTimes object");
        }

        Verification verification = verification().withRequest(requestDefinition).withTimes(times);
        String result = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("verify"))
                .withBody(verificationSerializer.serialize(verification), StandardCharsets.UTF_8)
        ).getBodyAsString();

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
     * <p>
     * once()      - verify the request was only received once
     * exactly(n)  - verify the request was only received exactly n times
     * atLeast(n)  - verify the request was only received at least n times
     *
     * @param expectationId the http request that must be matched for this verification to pass
     * @param times             the number of times this request must be matched
     * @throws AssertionError if the request has not been found
     */
    @SuppressWarnings("DuplicatedCode")
    public MockServerClient verify(ExpectationId expectationId, VerificationTimes times) throws AssertionError {
        if (expectationId == null) {
            throw new IllegalArgumentException("verify(RequestDefinition, VerificationTimes) requires a non null RequestDefinition object");
        }
        if (times == null) {
            throw new IllegalArgumentException("verify(RequestDefinition, VerificationTimes) requires a non null VerificationTimes object");
        }

        Verification verification = verification().withExpectationId(expectationId).withTimes(times);
        String result = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("verify"))
                .withBody(verificationSerializer.serialize(verification), StandardCharsets.UTF_8)
        ).getBodyAsString();

        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
        return clientClass.cast(this);
    }

    /**
     * Verify no requests have been sent.
     *
     * @throws AssertionError if any request has been found
     */
    @SuppressWarnings({"DuplicatedCode", "UnusedReturnValue"})
    public MockServerClient verifyZeroInteractions() throws AssertionError {
        Verification verification = verification().withRequest(request()).withTimes(exactly(0));
        String result = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("verify"))
                .withBody(verificationSerializer.serialize(verification), StandardCharsets.UTF_8)
        ).getBodyAsString();

        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
        return clientClass.cast(this);
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter, use null for the parameter to retrieve all requests
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @return an array of all requests that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public RequestDefinition[] retrieveRecordedRequests(RequestDefinition requestDefinition) {
        String recordedRequests = retrieveRecordedRequests(requestDefinition, Format.JSON);
        if (isNotBlank(recordedRequests) && !recordedRequests.equals("[]")) {
            return requestDefinitionSerializer.deserializeArray(recordedRequests);
        } else {
            return new RequestDefinition[0];
        }
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter, use null for the parameter to retrieve all requests
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @param format            the format to retrieve the expectations, either JAVA or JSON
     * @return an array of all requests that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public String retrieveRecordedRequests(RequestDefinition requestDefinition, Format format) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.REQUESTS.name())
                .withQueryStringParameter("format", format.name())
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8)
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the recorded requests and responses that match the httpRequest parameter, use null for the parameter to retrieve all requests and responses
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request (and its corresponding response), use null for the parameter to retrieve for all requests
     * @return an array of all requests and responses that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public LogEventRequestAndResponse[] retrieveRecordedRequestsAndResponses(RequestDefinition requestDefinition) {
        String recordedRequests = retrieveRecordedRequestsAndResponses(requestDefinition, Format.JSON);
        if (isNotBlank(recordedRequests) && !recordedRequests.equals("[]")) {
            return httpRequestResponseSerializer.deserializeArray(recordedRequests);
        } else {
            return new LogEventRequestAndResponse[0];
        }
    }

    /**
     * Retrieve the recorded requests that match the httpRequest parameter, use null for the parameter to retrieve all requests
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @param format            the format to retrieve the expectations, either JAVA or JSON
     * @return an array of all requests that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public String retrieveRecordedRequestsAndResponses(RequestDefinition requestDefinition, Format format) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.REQUEST_RESPONSES.name())
                .withQueryStringParameter("format", format.name())
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8)
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the request-response combinations that have been recorded as a list of expectations, only those that match the httpRequest parameter are returned, use null to retrieve all requests
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @return an array of all expectations that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public Expectation[] retrieveRecordedExpectations(RequestDefinition requestDefinition) {
        String recordedExpectations = retrieveRecordedExpectations(requestDefinition, Format.JSON);
        if (isNotBlank(recordedExpectations) && !recordedExpectations.equals("[]")) {
            return expectationSerializer.deserializeArray(recordedExpectations, true);
        } else {
            return new Expectation[0];
        }
    }

    /**
     * Retrieve the request-response combinations that have been recorded as a list of expectations, only those that match the httpRequest parameter are returned, use null to retrieve all requests
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @param format            the format to retrieve the expectations, either JAVA or JSON
     * @return an array of all expectations that have been recorded by the MockServer in the order they have been received and including duplicates where the same request has been received multiple times
     */
    public String retrieveRecordedExpectations(RequestDefinition requestDefinition, Format format) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
                .withQueryStringParameter("format", format.name())
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8)
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the logs associated to a specific requests, this shows all logs for expectation matching, verification, clearing, etc
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @return all log messages recorded by the MockServer when creating expectations, matching expectations, performing verification, clearing logs, etc
     */
    public String retrieveLogMessages(RequestDefinition requestDefinition) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.LOGS.name())
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8)
        );
        return httpResponse.getBodyAsString();
    }

    /**
     * Retrieve the logs associated to a specific requests, this shows all logs for expectation matching, verification, clearing, etc
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each request, use null for the parameter to retrieve for all requests
     * @return an array of all log messages recorded by the MockServer when creating expectations, matching expectations, performing verification, clearing logs, etc
     */
    public String[] retrieveLogMessagesArray(RequestDefinition requestDefinition) {
        return retrieveLogMessages(requestDefinition).split(LOG_SEPARATOR);
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
     * @param requestDefinition the http request that must be matched for this expectation to respond
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(RequestDefinition requestDefinition) {
        return when(requestDefinition, Times.unlimited());
    }

    /**
     * Specify a limited expectation that will respond a specified number of times when the http is matched
     * <p>
     * Example use:
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
     * @param requestDefinition the http request that must be matched for this expectation to respond
     * @param times             the number of times to respond when this http is matched
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(RequestDefinition requestDefinition, Times times) {
        return new ForwardChainExpectation(MOCK_SERVER_LOGGER, getMockServerEventBus(), this, new Expectation(requestDefinition, times, TimeToLive.unlimited(), 0));
    }

    /**
     * Specify a limited expectation that will respond a specified number of times when the http is matched
     * <p>
     * Example use:
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
     * @param requestDefinition the http request that must be matched for this expectation to respond
     * @param times             the number of times to respond when this http is matched
     * @param timeToLive        the length of time from when the server receives the expectation that the expectation should be active
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(RequestDefinition requestDefinition, Times times, TimeToLive timeToLive) {
        return new ForwardChainExpectation(MOCK_SERVER_LOGGER, getMockServerEventBus(), this, new Expectation(requestDefinition, times, timeToLive, 0));
    }

    /**
     * Specify a limited expectation that will respond a specified number of times when the http is matched and will be matched according to priority as follows:
     * <p>
     * - higher priority expectation will be matched first
     * - identical priority expectations will be match in the order they were submitted
     * - default priority is 0
     * <p>
     * Example use:
     * <pre>
     * mockServerClient
     *  .when(
     *      request()
     *          .withPath("/some_path")
     *          .withBody("some_request_body"),
     *      Times.exactly(5),
     *      TimeToLive.exactly(TimeUnit.SECONDS, 120),
     *      10
     *  )
     *  .respond(
     *      response()
     *          .withBody("some_response_body")
     *          .withHeader("responseName", "responseValue")
     *  )
     * </pre>
     *
     * @param requestDefinition the http request that must be matched for this expectation to respond
     * @param times             the number of times to respond when this http is matched
     * @param timeToLive        the length of time from when the server receives the expectation that the expectation should be active
     * @param priority          the priority for the expectation when matching, higher priority expectation will be matched first, identical priority expectations will be match in the order they were submitted
     * @return an Expectation object that can be used to specify the response
     */
    public ForwardChainExpectation when(RequestDefinition requestDefinition, Times times, TimeToLive timeToLive, Integer priority) {
        return new ForwardChainExpectation(MOCK_SERVER_LOGGER, getMockServerEventBus(), this, new Expectation(requestDefinition, times, timeToLive, priority));
    }

    /**
     * Specify OpenAPI and operations and responses to create matchers and example responses
     *
     * @param openAPIExpectations the OpenAPI and operations and responses to create matchers and example responses
     * @return upserted expectations
     */
    public Expectation[] upsert(OpenAPIExpectation... openAPIExpectations) {
        if (openAPIExpectations != null) {
            HttpResponse httpResponse = null;
            if (openAPIExpectations.length == 1) {
                httpResponse =
                    sendRequest(
                        request()
                            .withMethod("PUT")
                            .withContentType(APPLICATION_JSON_UTF_8)
                            .withPath(calculatePath("openapi"))
                            .withBody(openAPIExpectationSerializer.serialize(openAPIExpectations[0]), StandardCharsets.UTF_8)
                    );
                if (httpResponse != null && httpResponse.getStatusCode() != 201) {
                    throw new ClientException(formatLogMessage("error:{}while submitted OpenAPI expectation:{}", httpResponse.getBody(), openAPIExpectations[0]));
                }
            } else if (openAPIExpectations.length > 1) {
                httpResponse =
                    sendRequest(
                        request()
                            .withMethod("PUT")
                            .withContentType(APPLICATION_JSON_UTF_8)
                            .withPath(calculatePath("openapi"))
                            .withBody(openAPIExpectationSerializer.serialize(openAPIExpectations), StandardCharsets.UTF_8)
                    );
                if (httpResponse != null && httpResponse.getStatusCode() != 201) {
                    throw new ClientException(formatLogMessage("error:{}while submitted OpenAPI expectations:{}", httpResponse.getBody(), openAPIExpectations));
                }
            }
            if (httpResponse != null && isNotBlank(httpResponse.getBodyAsString())) {
                return expectationSerializer.deserializeArray(httpResponse.getBodyAsString(), true);
            }
        }
        return new Expectation[0];
    }

    /**
     * Specify one or more expectations to be create, or updated (if the id matches).
     * <p>
     * This method should be used to update existing expectation by id.  All fields will be updated for expectations with a matching id as the existing expectation is deleted and recreated.
     * <p>
     * To retrieve the id(s) for existing expectation(s) the retrieveActiveExpectations(HttpRequest httpRequest) method can be used.
     * <p>
     * Typically, to create expectations this method should not be used directly instead
     * the when(...) and response(...) or forward(...) or error(...) methods should be used
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
     * @param expectations one or more expectations to create or update (if the id field matches)
     * @return upserted expectations
     */
    public Expectation[] upsert(Expectation... expectations) {
        if (expectations != null) {
            HttpResponse httpResponse = null;
            if (expectations.length == 1) {
                httpResponse =
                    sendRequest(
                        request()
                            .withMethod("PUT")
                            .withContentType(APPLICATION_JSON_UTF_8)
                            .withPath(calculatePath("expectation"))
                            .withBody(expectationSerializer.serialize(expectations[0]), StandardCharsets.UTF_8)
                    );
                if (httpResponse != null && httpResponse.getStatusCode() != 201) {
                    throw new ClientException(formatLogMessage("error:{}while submitted expectation:{}", httpResponse.getBody(), expectations[0]));
                }
            } else if (expectations.length > 1) {
                httpResponse =
                    sendRequest(
                        request()
                            .withMethod("PUT")
                            .withContentType(APPLICATION_JSON_UTF_8)
                            .withPath(calculatePath("expectation"))
                            .withBody(expectationSerializer.serialize(expectations), StandardCharsets.UTF_8)
                    );
                if (httpResponse != null && httpResponse.getStatusCode() != 201) {
                    throw new ClientException(formatLogMessage("error:{}while submitted expectations:{}", httpResponse.getBody(), expectations));
                }
            }
            if (httpResponse != null && isNotBlank(httpResponse.getBodyAsString())) {
                return expectationSerializer.deserializeArray(httpResponse.getBodyAsString(), true);
            }
        }
        return new Expectation[0];
    }

    /**
     * Specify one or more expectations, normally this method should not be used directly instead the when(...) and response(...) or forward(...) or error(...) methods should be used
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
     * @param expectations one or more expectations
     * @return added or updated expectations
     * @deprecated this is deprecated due to unclear naming, use method upsert(Expectation... expectations) instead
     */
    @Deprecated
    public Expectation[] sendExpectation(Expectation... expectations) {
        return upsert(expectations);
    }

    /**
     * Retrieve the active expectations match the httpRequest parameter, use null for the parameter to retrieve all expectations
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @return an array of all expectations that have been setup and have not expired
     */
    public Expectation[] retrieveActiveExpectations(RequestDefinition requestDefinition) {
        String activeExpectations = retrieveActiveExpectations(requestDefinition, Format.JSON);
        if (isNotBlank(activeExpectations) && !activeExpectations.equals("[]")) {
            return expectationSerializer.deserializeArray(activeExpectations, true);
        } else {
            return new Expectation[0];
        }
    }

    /**
     * Retrieve the active expectations match the httpRequest parameter, use null for the parameter to retrieve all expectations
     *
     * @param requestDefinition the http request that is matched against when deciding whether to return each expectation, use null for the parameter to retrieve for all requests
     * @param format            the format to retrieve the expectations, either JAVA or JSON
     * @return an array of all expectations that have been setup and have not expired
     */
    public String retrieveActiveExpectations(RequestDefinition requestDefinition, Format format) {
        HttpResponse httpResponse = sendRequest(
            request()
                .withMethod("PUT")
                .withContentType(APPLICATION_JSON_UTF_8)
                .withPath(calculatePath("retrieve"))
                .withQueryStringParameter("type", RetrieveType.ACTIVE_EXPECTATIONS.name())
                .withQueryStringParameter("format", format.name())
                .withBody(requestDefinition != null ? requestDefinitionSerializer.serialize(requestDefinition) : "", StandardCharsets.UTF_8)
        );
        return httpResponse.getBodyAsString();
    }
}
