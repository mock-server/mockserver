package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;

import static org.mockserver.model.HttpResponse.response;

/**
 * Stops a forward whose target leads back to the same MockServer. Like a received-by entry in an RFC 9110 Via
 * header, every forward appends this instance's identifier to {@link #HEADER_NAME}; a request that already carries
 * it {@code forwardLoopMaxHops} times is answered with {@code 508 Loop Detected} instead of being forwarded again.
 * Only this instance's own entries count, so forwarding to, or through, other MockServer instances is unaffected.
 * Forward actions, {@code proxyPassMappings} and a WebSocket upgrade a forward relays share one guard per instance.
 */
final class ForwardLoopGuard {

    static final String HEADER_NAME = "x-mockserver-forwarded-by";
    static final int LOOP_DETECTED = 508;

    /**
     * What sent the request onward, which the {@code 508} names with the setting to check.
     */
    enum Route {
        FORWARD("the forward's target leads back to this MockServer, where the request matched a forward again: check the forward's host and port"),
        PROXY_PASS("the proxyPassMappings entry's target leads back to this MockServer, where the request matched the mapping again: check the mapping's target URI");

        private final String cause;

        Route(String cause) {
            this.cause = cause;
        }
    }

    private final Configuration configuration;
    private final String instanceId;

    ForwardLoopGuard(Configuration configuration, String instanceId) {
        this.configuration = configuration;
        this.instanceId = instanceId;
    }

    private int maxHops() {
        if (instanceId == null) {
            return 0;
        }
        Integer maxHops = configuration != null ? configuration.forwardLoopMaxHops() : null;
        return maxHops != null ? Math.max(0, maxHops) : 0;
    }

    /**
     * @return how many times this instance has already forwarded the request; an intermediary may have folded
     * the header's values into one comma-separated line
     */
    int hopsThroughThisInstance(HttpRequest received) {
        if (received == null || instanceId == null) {
            return 0;
        }
        int hops = 0;
        for (String value : received.getHeader(HEADER_NAME)) {
            if (value != null && value.contains(instanceId)) {
                for (String entry : value.split(",")) {
                    if (entry.trim().equals(instanceId)) {
                        hops++;
                    }
                }
            }
        }
        return hops;
    }

    /**
     * @return the {@code 508} that answers {@code received} in place of forwarding it again, or null when the
     * forward may go ahead
     */
    HttpResponse loopDetectedResponse(HttpRequest received) {
        return loopDetectedResponse(received, Route.FORWARD);
    }

    HttpResponse loopDetectedResponse(HttpRequest received, Route route) {
        int maxHops = maxHops();
        if (maxHops == 0) {
            return null;
        }
        int hops = hopsThroughThisInstance(received);
        if (hops < maxHops) {
            return null;
        }
        return response()
            .withStatusCode(LOOP_DETECTED)
            .withReasonPhrase("Loop Detected")
            .withBody(loopDetectedReason(hops, route));
    }

    static String loopDetectedReason(int hops, Route route) {
        return "MockServer did not forward this request because it has already forwarded it " + hops + " times, so " + route.cause + ", or raise forwardLoopMaxHops if the repeated forwarding is intended";
    }

    /**
     * As {@link #loopDetectedResponse}, also logging the refusal as a WARN.
     */
    HttpResponse refuseLoop(HttpRequest received, Route route, MockServerLogger mockServerLogger) {
        HttpResponse loopDetected = loopDetectedResponse(received, route);
        if (loopDetected != null && mockServerLogger != null && mockServerLogger.isEnabledForInstance(Level.WARN)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.WARN)
                    .setCorrelationId(received.getLogCorrelationId())
                    .setHttpRequest(received)
                    .setMessageFormat("returning 508 Loop Detected in place of forwarding request:{}because:{}")
                    .setArguments(received, loopDetected.getBodyAsString())
            );
        }
        return loopDetected;
    }

    /**
     * @return the header values a request forwarded on from {@code received} carries: its entries plus this
     * instance's, or null when loop detection is off
     */
    List<String> forwardedEntries(HttpRequest received) {
        if (maxHops() == 0) {
            return null;
        }
        List<String> entries = new ArrayList<>(received != null ? received.getHeader(HEADER_NAME) : List.of());
        entries.add(instanceId);
        return entries;
    }

    /**
     * Sets {@code toSend}'s header to {@link #forwardedEntries}, so a template or callback that builds a new request
     * cannot reset the count. A no-op when loop detection is off.
     */
    HttpRequest markForwarded(HttpRequest received, HttpRequest toSend) {
        List<String> entries = toSend != null ? forwardedEntries(received) : null;
        return entries != null ? toSend.replaceHeader(new Header(HEADER_NAME, entries)) : toSend;
    }
}
