package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.proxyconfiguration.InetAddressValidator;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Handles forward-with-fallback actions: forwards the request to the upstream
 * host defined by the {@link HttpForward}; if the upstream returns a status code
 * matching the fallback criteria (default 500-599) or the request times out /
 * fails to connect, the configured fallback {@link HttpResponse} is returned
 * instead.
 */
public class HttpForwardWithFallbackActionHandler extends HttpForwardAction {

    public HttpForwardWithFallbackActionHandler(MockServerLogger mockServerLogger, Configuration configuration, NettyHttpClient httpClient) {
        super(mockServerLogger, configuration, httpClient);
    }

    public HttpForwardActionResult handle(HttpForwardWithFallback action, HttpRequest httpRequest) {
        if (action == null || action.getHttpForward() == null) {
            return badGatewayFuture(httpRequest);
        }

        HttpForward httpForward = action.getHttpForward();
        HttpResponse fallbackResponse = action.getFallbackResponse();
        List<Integer> fallbackOnStatusCodes = action.getFallbackOnStatusCodes();
        boolean fallbackOnTimeout = action.getFallbackOnTimeout() == null || action.getFallbackOnTimeout();

        // Prepare the request (same as HttpForwardActionHandler)
        httpRequest.withSecure(HttpForward.Scheme.HTTPS.equals(httpForward.getScheme()));
        int port = httpForward.getPort();
        boolean defaultPort = (HttpForward.Scheme.HTTPS.equals(httpForward.getScheme()) && port == 443)
            || (HttpForward.Scheme.HTTP.equals(httpForward.getScheme()) && port == 80);
        String hostHeader = defaultPort ? httpForward.getHost() : httpForward.getHost() + ":" + port;
        httpRequest.replaceHeader(new Header("Host", hostHeader));

        try {
            InetAddressValidator.validateForwardTarget(configuration, httpForward.getHost());
        } catch (IllegalArgumentException blocked) {
            if (mockServerLogger.isEnabledForInstance(Level.WARN)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setHttpRequest(httpRequest)
                        .setMessageFormat("forward-with-fallback action blocked by SSRF policy:{}")
                        .setArguments(blocked.getMessage())
                );
            }
            if (fallbackOnTimeout && fallbackResponse != null) {
                return completedFuture(httpRequest, fallbackResponse);
            }
            return badGatewayFuture(httpRequest);
        }

        // Send the upstream request. SSRF validation above has already resolved and vetted the host;
        // an unresolved address lets Netty's event-loop resolver do the (blocking) DNS lookup off the
        // calling thread rather than resolving synchronously in the InetSocketAddress constructor.
        HttpForwardActionResult forwardResult = sendRequest(
            httpRequest,
            httpRequest,
            InetSocketAddress.createUnresolved(httpForward.getHost(), httpForward.getPort()),
            null
        );

        // Wrap the response future to apply fallback logic
        CompletableFuture<HttpResponse> wrappedFuture = forwardResult.getHttpResponse()
            .handle((response, throwable) -> {
                if (throwable != null) {
                    // Connection error or timeout
                    if (fallbackOnTimeout && fallbackResponse != null) {
                        if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(Level.INFO)
                                    .setHttpRequest(httpRequest)
                                    .setMessageFormat("forward-with-fallback: upstream request failed ({}), returning fallback response")
                                    .setArguments(throwable.getMessage())
                            );
                        }
                        return fallbackResponse;
                    }
                    return HttpResponse.badGatewayResponse();
                }

                if (response == null) {
                    if (fallbackOnTimeout && fallbackResponse != null) {
                        if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(Level.INFO)
                                    .setHttpRequest(httpRequest)
                                    .setMessageFormat("forward-with-fallback: upstream returned null response, returning fallback response")
                            );
                        }
                        return fallbackResponse;
                    }
                    return HttpResponse.badGatewayResponse();
                }

                if (shouldFallback(response.getStatusCode(), fallbackOnStatusCodes) && fallbackResponse != null) {
                    if (mockServerLogger.isEnabledForInstance(Level.INFO)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(Level.INFO)
                                .setHttpRequest(httpRequest)
                                .setHttpResponse(response)
                                .setMessageFormat("forward-with-fallback: upstream returned status {}, returning fallback response")
                                .setArguments(response.getStatusCode())
                        );
                    }
                    return fallbackResponse;
                }

                return response;
            });

        return new HttpForwardActionResult(
            httpRequest,
            wrappedFuture,
            null,
            InetSocketAddress.createUnresolved(httpForward.getHost(), httpForward.getPort())
        );
    }

    /**
     * Returns true if the upstream status code matches the fallback criteria.
     * When no explicit status codes are configured, defaults to 500-599.
     */
    boolean shouldFallback(Integer statusCode, List<Integer> fallbackOnStatusCodes) {
        if (statusCode == null) {
            return true;
        }
        if (fallbackOnStatusCodes != null && !fallbackOnStatusCodes.isEmpty()) {
            return fallbackOnStatusCodes.contains(statusCode);
        }
        // Default: fall back on 5xx
        return statusCode >= 500 && statusCode <= 599;
    }

    private HttpForwardActionResult completedFuture(HttpRequest httpRequest, HttpResponse response) {
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        future.complete(response);
        return new HttpForwardActionResult(httpRequest, future, null);
    }

    @Override
    protected String actionName() {
        return "forward-with-fallback action";
    }
}
