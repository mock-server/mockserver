package org.mockserver.closurecallback.websocketregistry;

import org.mockserver.collections.CircularHashMap;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.mock.action.ExpectationResponseCallback;

import java.util.Collections;
import java.util.Map;

public class LocalCallbackRegistry {

    public static boolean enabled = true;
    private static Map<String, ExpectationResponseCallback> responseCallbackRegistry;
    private static Map<String, ExpectationForwardCallback> forwardCallbackRegistry;
    private static Map<String, ExpectationForwardAndResponseCallback> forwardAndResponseCallbackRegistry;
    private static int maxWebSocketExpectations = ConfigurationProperties.maxWebSocketExpectations();

    public static void setMaxWebSocketExpectations(int maxWebSocketExpectations) {
        LocalCallbackRegistry.maxWebSocketExpectations = maxWebSocketExpectations;
    }

    public static Map<String, ExpectationResponseCallback> responseCallbackRegistry() {
        if (responseCallbackRegistry == null) {
            responseCallbackRegistry = Collections.synchronizedMap(new CircularHashMap<>(maxWebSocketExpectations));
        }
        return responseCallbackRegistry;
    }

    public static Map<String, ExpectationForwardCallback> forwardCallbackRegistry() {
        if (forwardCallbackRegistry == null) {
            forwardCallbackRegistry = Collections.synchronizedMap(new CircularHashMap<>(maxWebSocketExpectations));
        }
        return forwardCallbackRegistry;
    }

    public static Map<String, ExpectationForwardAndResponseCallback> forwardAndResponseCallbackRegistry() {
        if (forwardAndResponseCallbackRegistry == null) {
            forwardAndResponseCallbackRegistry = Collections.synchronizedMap(new CircularHashMap<>(maxWebSocketExpectations));
        }
        return forwardAndResponseCallbackRegistry;
    }

    public static void registerCallback(String clientId, ExpectationCallback<?> expectationCallback) {
        // if not added to local registry then web socket will be used
        if (enabled && expectationCallback != null) {
            if (expectationCallback instanceof ExpectationResponseCallback) {
                responseCallbackRegistry().put(clientId, (ExpectationResponseCallback) expectationCallback);
            } else if (expectationCallback instanceof ExpectationForwardAndResponseCallback) {
                forwardAndResponseCallbackRegistry().put(clientId, (ExpectationForwardAndResponseCallback) expectationCallback);
            } else if (expectationCallback instanceof ExpectationForwardCallback) {
                forwardCallbackRegistry().put(clientId, (ExpectationForwardCallback) expectationCallback);
            }
        }
    }

    public static void unregisterCallback(String clientId) {
        responseCallbackRegistry().remove(clientId);
        forwardAndResponseCallbackRegistry().remove(clientId);
        forwardCallbackRegistry().remove(clientId);
    }

    public static boolean responseClientExists(String clientId) {
        return responseCallbackRegistry().containsKey(clientId);
    }

    public static boolean forwardClientExists(String clientId) {
        return forwardCallbackRegistry().containsKey(clientId)
            || forwardAndResponseCallbackRegistry().containsKey(clientId);
    }

    public static ExpectationResponseCallback retrieveResponseCallback(String clientId) {
        return responseCallbackRegistry().get(clientId);
    }

    public static ExpectationForwardCallback retrieveForwardCallback(String clientId) {
        ExpectationForwardCallback expectationForwardCallback = forwardCallbackRegistry().get(clientId);
        if (expectationForwardCallback == null) {
            return retrieveForwardAndResponseCallback(clientId);
        } else {
            return expectationForwardCallback;
        }
    }

    public static ExpectationForwardAndResponseCallback retrieveForwardAndResponseCallback(String clientId) {
        return forwardAndResponseCallbackRegistry().get(clientId);
    }

}
