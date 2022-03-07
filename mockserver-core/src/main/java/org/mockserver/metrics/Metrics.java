package org.mockserver.metrics;

import org.mockserver.configuration.Configuration;
import org.mockserver.model.Action;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "FieldMayBeFinal"})
public class Metrics {

    private static Map<Name, Integer> metrics = new ConcurrentHashMap<>();
    private final Configuration configuration;

    public Metrics(Configuration configuration) {
        this.configuration = configuration;
    }

    public static void clear() {
        metrics.clear();
    }

    public void set(Name name, Integer value) {
        metrics.put(name, value);
    }

    public static Integer get(Name name) {
        Integer value = metrics.get(name);
        return value != null ? value : 0;
    }

    public void increment(Name name) {
        if (configuration.metricsEnabled()) {
            synchronized (name) {
                metrics.merge(name, 1, Integer::sum);
            }
        }
    }

    public void decrement(Name name) {
        if (configuration.metricsEnabled()) {
            synchronized (name) {
                final Integer currentValue = metrics.get(name);
                if (currentValue != null) {
                    metrics.put(name, currentValue - 1);
                } else {
                    throw new IllegalArgumentException("Can not decrement metric \"" + name + "\" because it not exist");
                }
            }
        }
    }

    public void increment(Action.Type type) {
        if (configuration.metricsEnabled()) {
            Name name = Name.valueOf("ACTION_" + type.name() + "_COUNT");
            synchronized (name) {
                metrics.merge(name, 1, Integer::sum);
            }
        }
    }

    public void decrement(Action.Type type) {
        if (configuration.metricsEnabled()) {
            Name name = Name.valueOf("ACTION_" + type.name() + "_COUNT");
            synchronized (name) {
                final Integer currentValue = metrics.get(name);
                if (currentValue != null) {
                    metrics.put(name, currentValue - 1);
                } else {
                    throw new IllegalArgumentException("Can not decrement metric \"" + name + "\" because it not exist");
                }
            }
        }
    }

    public static void clearActionMetrics() {
        metrics.remove(Name.ACTION_FORWARD_COUNT);
        metrics.remove(Name.ACTION_FORWARD_TEMPLATE_COUNT);
        metrics.remove(Name.ACTION_FORWARD_CLASS_CALLBACK_COUNT);
        metrics.remove(Name.ACTION_FORWARD_OBJECT_CALLBACK_COUNT);
        metrics.remove(Name.ACTION_FORWARD_REPLACE_COUNT);
        metrics.remove(Name.ACTION_RESPONSE_COUNT);
        metrics.remove(Name.ACTION_RESPONSE_TEMPLATE_COUNT);
        metrics.remove(Name.ACTION_RESPONSE_CLASS_CALLBACK_COUNT);
        metrics.remove(Name.ACTION_RESPONSE_OBJECT_CALLBACK_COUNT);
        metrics.remove(Name.ACTION_ERROR_COUNT);
    }

    public static void clearWebSocketMetrics() {
        metrics.remove(Name.WEBSOCKET_CALLBACK_CLIENT_COUNT);
        metrics.remove(Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLER_COUNT);
        metrics.remove(Name.WEBSOCKET_CALLBACK_FORWARD_HANDLER_COUNT);
    }

    public enum Name {
        EXPECTATION_NOT_MATCHED_COUNT,
        RESPONSE_EXPECTATION_MATCHED_COUNT,
        FORWARD_EXPECTATION_MATCHED_COUNT,
        ACTION_FORWARD_COUNT,
        ACTION_FORWARD_TEMPLATE_COUNT,
        ACTION_FORWARD_CLASS_CALLBACK_COUNT,
        ACTION_FORWARD_OBJECT_CALLBACK_COUNT,
        ACTION_FORWARD_REPLACE_COUNT,
        ACTION_RESPONSE_COUNT,
        ACTION_RESPONSE_TEMPLATE_COUNT,
        ACTION_RESPONSE_CLASS_CALLBACK_COUNT,
        ACTION_RESPONSE_OBJECT_CALLBACK_COUNT,
        ACTION_ERROR_COUNT,
        WEBSOCKET_CALLBACK_CLIENT_COUNT,
        WEBSOCKET_CALLBACK_RESPONSE_HANDLER_COUNT,
        WEBSOCKET_CALLBACK_FORWARD_HANDLER_COUNT
    }
}
