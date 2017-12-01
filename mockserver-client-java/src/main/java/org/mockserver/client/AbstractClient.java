package org.mockserver.client;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.netty.SocketConnectionException;
import org.mockserver.client.serialization.*;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.PortBinding.portBinding;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClient<T extends AbstractClient> implements Closeable {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected final String host;
    protected final int port;
    protected final String contextPath;
    private final Class<T> clientClass;
    protected NettyHttpClient nettyHttpClient = new NettyHttpClient();
    protected HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    protected PortBindingSerializer portBindingSerializer = new PortBindingSerializer();
    protected ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    protected VerificationSerializer verificationSerializer = new VerificationSerializer();
    protected VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();

    /**
     * Start the client communicating to the proxy at the specified host and port
     * and contextPath for example:
     * <p>
     * ProxyClient mockServerClient = new ProxyClient("localhost", 1080, "/proxy");
     *
     * @param host        the host for the proxy to communicate with
     * @param port        the port for the proxy to communicate with
     * @param contextPath the context path that the proxy war is deployed to
     */
    protected AbstractClient(String host, int port, String contextPath, Class<T> clientClass) {
        this.clientClass = clientClass;
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

    protected String calculatePath(String path) {
        String cleanedPath = path;
        if (!Strings.isNullOrEmpty(contextPath)) {
            cleanedPath =
                    (!contextPath.startsWith("/") ? "/" : "") +
                            contextPath +
                            (!contextPath.endsWith("/") ? "/" : "") +
                            (cleanedPath.startsWith("/") ? cleanedPath.substring(1) : cleanedPath);
        }
        return (!cleanedPath.startsWith("/") ? "/" : "") + cleanedPath;
    }

    protected HttpResponse sendRequest(HttpRequest httpRequest) {
        HttpResponse httpResponse = nettyHttpClient.sendRequest(
                httpRequest.withHeader(HOST.toString(), host + ":" + port)
        );
        if (httpResponse != null &&
                httpResponse.getStatusCode() != null &&
                httpResponse.getStatusCode() == BAD_REQUEST.code()) {
            throw new IllegalArgumentException(httpResponse.getBodyAsString());
        }
        return httpResponse;
    }

    protected String formatErrorMessage(String message, Object... objects) {
        Object[] indentedObjects = new String[objects.length];
        for (int i = 0; i < objects.length; i++) {
            indentedObjects[i] = NEW_LINE + NEW_LINE + String.valueOf(objects[i]).replaceAll("(?m)^", "\t") + NEW_LINE;
        }
        return String.format(NEW_LINE + message + NEW_LINE, indentedObjects);
    }

    public InetSocketAddress remoteAddress() {
        return new InetSocketAddress(host, port);
    }

    public String contextPath() {
        return contextPath;
    }

    public enum TYPE {
        LOG,
        EXPECTATION,
        BOTH;
    }

    /**
     * Returns the server (MockServer or Proxy) is running
     */
    public boolean isRunning() {
        return isRunning(10, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the server (MockServer or Proxy) is running, by polling the MockServer a configurable amount of times
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
        String boundPorts = sendRequest(request().withMethod("PUT").withPath(calculatePath("bind")).withBody(portBindingSerializer.serialize(portBinding(ports)), Charsets.UTF_8)).getBodyAsString();
        return portBindingSerializer.deserialize(boundPorts).getPorts();
    }

    /**
     * Stop server (MockServer or Proxy) gracefully (only support for Netty version, not supported for WAR version)
     */
    public T stop() {
        return stop(false);
    }

    /**
     * Stop server (MockServer or Proxy) gracefully (only support for Netty version, not supported for WAR version)
     */
    public T stop(boolean ignoreFailure) {
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
        return clientClass.cast(this);
    }

    @Override
    public void close() throws IOException {
        stop();
    }

    /**
     * Reset server (MockServer or Proxy) by clearing all expectations
     */
    public T reset() {
        sendRequest(request().withMethod("PUT").withPath(calculatePath("reset")));
        return clientClass.cast(this);
    }

    /**
     * Clear all expectations and logs that match the http
     *
     * @param httpRequest the http request that is matched against when deciding whether to clear each expectation if null all expectations are cleared
     */
    public T clear(HttpRequest httpRequest) {
        sendRequest(request().withMethod("PUT").withPath(calculatePath("clear")).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", Charsets.UTF_8));
        return clientClass.cast(this);
    }

    /**
     * Clear expectations, logs or both that match the http
     *
     * @param httpRequest the http request that is matched against when deciding whether to clear each expectation if null all expectations are cleared
     * @param type the type to clear, EXPECTATION, LOG or BOTH
     */
    public T clear(HttpRequest httpRequest, MockServerClient.TYPE type) {
        sendRequest(request().withMethod("PUT").withPath(calculatePath("clear")).withQueryStringParameter("type", type.name().toLowerCase()).withBody(httpRequest != null ? httpRequestSerializer.serialize(httpRequest) : "", Charsets.UTF_8));
        return clientClass.cast(this);
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
    public T verify(HttpRequest... httpRequests) throws AssertionError {
        if (httpRequests == null || httpRequests.length == 0 || httpRequests[0] == null) {
            throw new IllegalArgumentException("verify(HttpRequest...) requires a non null non empty array of HttpRequest objects");
        }

        VerificationSequence verificationSequence = new VerificationSequence().withRequests(httpRequests);
        String result = sendRequest(request().withMethod("PUT").withPath(calculatePath("verifySequence")).withBody(verificationSequenceSerializer.serialize(verificationSequence), Charsets.UTF_8)).getBodyAsString();

        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
        return clientClass.cast(this);
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
    public T verify(HttpRequest httpRequest, VerificationTimes times) throws AssertionError {
        if (httpRequest == null) {
            throw new IllegalArgumentException("verify(HttpRequest, VerificationTimes) requires a non null HttpRequest object");
        }
        if (times == null) {
            throw new IllegalArgumentException("verify(HttpRequest, VerificationTimes) requires a non null VerificationTimes object");
        }

        Verification verification = verification().withRequest(httpRequest).withTimes(times);
        String result = sendRequest(request().withMethod("PUT").withPath(calculatePath("verify")).withBody(verificationSerializer.serialize(verification), Charsets.UTF_8)).getBodyAsString();

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
    public T verifyZeroInteractions() throws AssertionError {
        Verification verification = verification().withRequest(request()).withTimes(exactly(0));
        String result = sendRequest(request().withMethod("PUT").withPath(calculatePath("verify")).withBody(verificationSerializer.serialize(verification), Charsets.UTF_8)).getBodyAsString();

        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
        return clientClass.cast(this);
    }
}
