package org.mockserver.testing.integration.mock;

import com.google.common.collect.ImmutableList;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.model.*;
import org.mockserver.scheduler.Scheduler;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.Header.header;
import static org.slf4j.event.Level.WARN;

/**
 * @author jamesdbloom
 */
public abstract class AbstractMockingIntegrationTestBase {

    protected static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(AbstractMockingIntegrationTestBase.class);
    protected static MockServerClient mockServerClient;
    protected static String servletContext = "";
    @SuppressWarnings("deprecation")
    public static final List<String> HEADERS_TO_IGNORE = ImmutableList.of(
        HttpHeaderNames.SERVER.toString(),
        HttpHeaderNames.EXPIRES.toString(),
        HttpHeaderNames.DATE.toString(),
        HttpHeaderNames.HOST.toString(),
        HttpHeaderNames.CONNECTION.toString(),
        HttpHeaderNames.USER_AGENT.toString(),
        HttpHeaderNames.CONTENT_LENGTH.toString(),
        HttpHeaderNames.ACCEPT_ENCODING.toString(),
        HttpHeaderNames.TRANSFER_ENCODING.toString(),
        HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(),
        HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(),
        HttpHeaderNames.VARY.toString(),
        HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(),
        HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(),
        HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(),
        HttpHeaderNames.ACCESS_CONTROL_MAX_AGE.toString(),
        HttpHeaderNames.KEEP_ALIVE.toString(),
        "version",
        "x-cors"
    );

    protected static EchoServer insecureEchoServer;
    protected static EchoServer secureEchoServer;

    @BeforeClass
    public static void startEchoServer() {
        if (insecureEchoServer == null) {
            insecureEchoServer = new EchoServer(false);
        }
        if (secureEchoServer == null) {
            secureEchoServer = new EchoServer(true);
        }
    }

    @BeforeClass
    public static void resetServletContext() {
        servletContext = "";
    }

    public abstract int getServerPort();

    public int getServerSecurePort() {
        return getServerPort();
    }

    protected boolean isSecureControlPlane() {
        return false;
    }

    protected Header authorisationHeader() {
        return null;
    }

    @Before
    public void resetServer() {
        try {
            if (mockServerClient != null) {
                mockServerClient.reset();
            }
            if (insecureEchoServer != null) {
                insecureEchoServer.clear();
            }
            if (secureEchoServer != null) {
                secureEchoServer.clear();
            }
        } catch (Throwable throwable) {
            if (MockServerLogger.isEnabled(WARN)) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setLogLevel(WARN)
                        .setMessageFormat("exception while resetting - " + throwable.getMessage())
                        .setThrowable(throwable)
                );
            }
        }
    }

    protected String calculatePath(String path) {
        return (!path.startsWith("/") ? "/" : "") + path;
    }

    protected static EventLoopGroup clientEventLoopGroup;
    protected static NettyHttpClient httpClient;

    @BeforeClass
    public static void createClientAndEventLoopGroup() {
        clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(AbstractMockingIntegrationTestBase.class.getSimpleName() + "-eventLoop"));
        httpClient = new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false);
    }

    @AfterClass
    public static void stopEventLoopGroup() {
        if (clientEventLoopGroup != null) {
            clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
        }
    }

    @SuppressWarnings("DuplicatedCode")
    protected String addContextToPath(String path) {
        String cleanedPath = path;
        if (isNotBlank(servletContext)) {
            cleanedPath =
                (!servletContext.startsWith("/") ? "/" : "") +
                    servletContext +
                    (!servletContext.endsWith("/") ? "/" : "") +
                    (cleanedPath.startsWith("/") ? cleanedPath.substring(1) : cleanedPath);
        }
        return (!cleanedPath.startsWith("/") ? "/" : "") + cleanedPath;
    }

    protected void verifyRequestsMatches(RequestDefinition[] requestDefinitions, HttpRequest... httpRequestMatchers) {
        if (requestDefinitions.length != httpRequestMatchers.length) {
            throw new AssertionError("Number of request matchers does not match number of requests, expected:<" + httpRequestMatchers.length + "> but was:<" + requestDefinitions.length + ">");
        } else {
            for (int i = 0; i < httpRequestMatchers.length; i++) {
                if (!new MatcherBuilder(configuration(), MOCK_SERVER_LOGGER).transformsToMatcher(httpRequestMatchers[i]).matches(null, requestDefinitions[i])) {
                    throw new AssertionError("Request does not match request matcher, expected <" + httpRequestMatchers[i] + "> but was:<" + requestDefinitions[i] + ">, full list requests is: " + Arrays.toString(httpRequestMatchers));
                }
            }
        }
    }

    protected void verifyRequestsMatches(LogEventRequestAndResponse[] logEventRequestAndResponses, HttpRequest... httpRequestMatchers) {
        if (logEventRequestAndResponses.length != httpRequestMatchers.length) {
            throw new AssertionError("Number of request matchers does not match number of requests, expected:<" + httpRequestMatchers.length + "> but was:<" + logEventRequestAndResponses.length + ">");
        } else {
            for (int i = 0; i < httpRequestMatchers.length; i++) {
                if (!new MatcherBuilder(configuration(), MOCK_SERVER_LOGGER).transformsToMatcher(httpRequestMatchers[i]).matches(null, logEventRequestAndResponses[i].getHttpRequest())) {
                    throw new AssertionError("Request does not match request matcher, expected <" + httpRequestMatchers[i] + "> but was:<" + logEventRequestAndResponses[i] + ">, full list requests is: " + Arrays.toString(httpRequestMatchers));
                }
            }
        }
    }

    protected HttpResponse makeRequest(HttpRequest httpRequest, Collection<String> headersToIgnore) {
        try {
            boolean isSsl = httpRequest.isSecure() != null && httpRequest.isSecure();
            int port = (isSsl ? getServerSecurePort() : getServerPort());
            httpRequest.withPath(addContextToPath(httpRequest.getPath().getValue()));
            if (!httpRequest.containsHeader(HOST.toString())) {
                httpRequest.withHeader(HOST.toString(), "localhost:" + port);
            }
            boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0;
            HttpResponse httpResponse = httpClient.sendRequest(httpRequest, new InetSocketAddress("localhost", port))
                .get(30, (isDebug ? TimeUnit.MINUTES : TimeUnit.SECONDS));
            httpResponse.withHeaders(filterHeaders(headersToIgnore, httpResponse.getHeaderList()));
            httpResponse.withReasonPhrase(
                isBlank(httpResponse.getReasonPhrase()) ?
                    HttpResponseStatus.valueOf(httpResponse.getStatusCode()).reasonPhrase() :
                    httpResponse.getReasonPhrase()
            );
            return httpResponse;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Headers filterHeaders(Collection<String> headersToIgnore, List<Header> headerList) {
        Headers headers = new Headers();
        for (Header header : headerList) {
            if (!headersToIgnore.contains(header.getName().getValue().toLowerCase())) {
                if (header.getName().getValue().equalsIgnoreCase(CONTENT_TYPE.toString())) {
                    // this fixes Tomcat which removes the space between
                    // media type and charset in the Content-Type header
                    for (NottableString value : new ArrayList<>(header.getValues())) {
                        header.getValues().clear();
                        header.addValues(value.getValue().replace(";charset", "; charset"));
                    }
                    header = header(header.getName().lowercase(), header.getValues());
                }
                headers.withEntry(header);
            }
        }
        return headers;
    }
}
