package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.ArrayList;
import java.util.List;

import static org.mockserver.model.HttpResponse.response;

/**
 * Stops a forward whose target leads back to the same MockServer. Like a received-by entry in an RFC 9110 Via
 * header, every forward appends this instance's identifier to {@link #HEADER_NAME}; a request that already carries
 * it {@code forwardLoopMaxHops} times is answered with {@code 508 Loop Detected} instead of being forwarded again.
 * Only this instance's own entries count, so forwarding to, or through, other MockServer instances is unaffected.
 */
final class ForwardLoopGuard {

    static final String HEADER_NAME = "x-mockserver-forwarded-by";
    static final int LOOP_DETECTED = 508;

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
            .withBody(loopDetectedReason(hops));
    }

    static String loopDetectedReason(int hops) {
        return "MockServer did not forward this request because it has already forwarded it " + hops + " times, so the forward's target leads back to this MockServer, where the request matched a forward again: check the forward's host and port, or raise forwardLoopMaxHops if the repeated forwarding is intended";
    }

    /**
     * Sets {@code toSend}'s header to the entries {@code received} carries plus this instance's, so a template or
     * callback that builds a new request cannot reset the count. A no-op when loop detection is off.
     */
    HttpRequest markForwarded(HttpRequest received, HttpRequest toSend) {
        if (toSend == null || maxHops() == 0) {
            return toSend;
        }
        List<String> entries = new ArrayList<>(received != null ? received.getHeader(HEADER_NAME) : List.of());
        entries.add(instanceId);
        return toSend.replaceHeader(new Header(HEADER_NAME, entries));
    }
}
