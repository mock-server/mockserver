package org.mockserver.mock;

import org.mockserver.model.AnyOfRequestDefinition;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.model.ConditionalRequestDefinition;
import org.mockserver.model.RequestDefinition;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which binary messages the registered expectations could match, so that a proxied binary message no expectation
 * can match skips the matcher pass. Of the request definitions, a {@link BinaryRequestDefinition} matches a binary
 * message only when their bytes are equal, unless it has no bytes or is negated, and a
 * {@link ConditionalRequestDefinition} or {@link AnyOfRequestDefinition} may match any message; no other kind matches one.
 * <p>
 * Kept by expectation id off the expectation store's mutation listener, and read lock-free. It errs towards "may
 * match": a wrong yes costs one matcher pass, while a wrong no would lose a match.
 */
final class BinaryExpectationIndex {

    private final Set<String> binaryIds = ConcurrentHashMap.newKeySet();
    private final Set<String> matchAnyIds = ConcurrentHashMap.newKeySet();
    private final Map<String, byte[]> bytesById = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, byte[]>> bytesByLength = new ConcurrentHashMap<>();

    /**
     * Records what the expectation with this id now matches, replacing what it matched before, so it serves a
     * fresh add and an in-place update alike.
     */
    void track(String id, RequestDefinition requestDefinition) {
        remove(id);
        if (requestDefinition instanceof BinaryRequestDefinition) {
            BinaryRequestDefinition binaryRequest = (BinaryRequestDefinition) requestDefinition;
            // the matcher's own array, so a lookup compares the bytes the matcher compares
            byte[] bytes = binaryRequest.getBinaryData();
            if (bytes == null || bytes.length == 0 || binaryRequest.isNot()) {
                matchAnyIds.add(id);
            } else {
                bytesById.put(id, bytes);
                bytesByLength.compute(bytes.length, (length, ids) -> {
                    Map<String, byte[]> sameLength = ids != null ? ids : new ConcurrentHashMap<>();
                    sameLength.put(id, bytes);
                    return sameLength;
                });
            }
            binaryIds.add(id);
        } else if (requestDefinition instanceof ConditionalRequestDefinition || requestDefinition instanceof AnyOfRequestDefinition) {
            matchAnyIds.add(id);
        }
    }

    void remove(String id) {
        binaryIds.remove(id);
        matchAnyIds.remove(id);
        byte[] bytes = bytesById.remove(id);
        if (bytes != null) {
            bytesByLength.computeIfPresent(bytes.length, (length, ids) -> {
                ids.remove(id);
                return ids.isEmpty() ? null : ids;
            });
        }
    }

    /**
     * False only when no expectation can match a binary message of these bytes: none has a binary request, or none
     * has these bytes and none could match any message.
     */
    boolean mayMatch(byte[] bytes) {
        if (binaryIds.isEmpty()) {
            return false;
        }
        if (bytes == null || !matchAnyIds.isEmpty()) {
            return true;
        }
        Map<String, byte[]> sameLength = bytesByLength.get(bytes.length);
        if (sameLength != null) {
            for (byte[] candidate : sameLength.values()) {
                if (Arrays.equals(candidate, bytes)) {
                    return true;
                }
            }
        }
        return false;
    }
}
