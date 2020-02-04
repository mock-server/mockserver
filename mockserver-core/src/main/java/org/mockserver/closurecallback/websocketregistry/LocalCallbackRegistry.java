package org.mockserver.closurecallback.websocketregistry;

import org.mockserver.collections.CircularHashMap;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.mock.action.ExpectationResponseCallback;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.mockserver.configuration.ConfigurationProperties.maxWebSocketExpectations;

public class LocalCallbackRegistry {

    public static boolean enabled = true;
    private static final Map<String, ExpectationResponseCallback> RESPONSE_CALLBACK_REGISTRY = Collections.synchronizedMap(new CircularHashMap<>(maxWebSocketExpectations()));
    private static final Map<String, ExpectationForwardCallback> FORWARD_CALLBACK_REGISTRY = Collections.synchronizedMap(new CircularHashMap<>(maxWebSocketExpectations()));

    public static void registerCallback(String clientId, ExpectationCallback<?> expectationCallback) {
        if (enabled) {
            if (expectationCallback instanceof ExpectationResponseCallback) {
                RESPONSE_CALLBACK_REGISTRY.put(clientId, (ExpectationResponseCallback) expectationCallback);
            } else if (expectationCallback instanceof ExpectationForwardCallback) {
                FORWARD_CALLBACK_REGISTRY.put(clientId, (ExpectationForwardCallback) expectationCallback);
            }
        }
    }

    public static void unregisterCallback(String clientId) {
        RESPONSE_CALLBACK_REGISTRY.remove(clientId);
        FORWARD_CALLBACK_REGISTRY.remove(clientId);
    }

    public static boolean responseClientExists(String clientId) {
        return RESPONSE_CALLBACK_REGISTRY.containsKey(clientId);
    }

    public static boolean forwardClientExists(String clientId) {
        return FORWARD_CALLBACK_REGISTRY.containsKey(clientId);
    }

    public static ExpectationResponseCallback retrieveResponseCallback(String clientId) {
        return RESPONSE_CALLBACK_REGISTRY.get(clientId);
    }

    public static ExpectationForwardCallback retrieveForwardCallback(String clientId) {
        return FORWARD_CALLBACK_REGISTRY.get(clientId);
    }

}
