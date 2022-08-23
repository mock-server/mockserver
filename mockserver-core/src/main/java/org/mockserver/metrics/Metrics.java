package org.mockserver.metrics;

import io.prometheus.client.Gauge;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Action;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockserver.log.model.LogEntry.LogMessageType.EXCEPTION;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "FieldMayBeFinal"})
public class Metrics {

    private static final AtomicReference<Boolean> additionalMetricsRegistered = new AtomicReference<>(false);
    private static final Map<Name, Gauge> metrics = new ConcurrentHashMap<>();

    private final Boolean metricsEnabled;

    public Metrics(Configuration configuration) {
        metricsEnabled = configuration.metricsEnabled();
        if (metricsEnabled && additionalMetricsRegistered.compareAndSet(false, true)) {
            new BuildInfoCollector().register();
            Arrays.stream(Name.values()).forEach(Metrics::getOrCreate);
        }
    }

    private static Gauge getOrCreate(Name name) {
        synchronized (name) {
            Gauge gauge = metrics.get(name);
            if (gauge == null) {
                try {
                    gauge = Gauge.build()
                        .name(name.name().toLowerCase())
                        .help(name.description)
                        .register();
                    metrics.put(name, gauge);
                } catch (Throwable throwable) {
                    new MockServerLogger().logEvent(
                        new LogEntry()
                            .setType(EXCEPTION)
                            .setMessageFormat("exception:{} creating metric:{}")
                            .setArguments(throwable.getMessage(), name.name())
                            .setThrowable(throwable)
                    );
                }
            }
            return gauge;
        }
    }

    public static void clear() {
        metrics.forEach((name, gauge) -> gauge.set(0));
    }

    public static void clear(Name name) {
        getOrCreate(name).set(0);
    }

    public void set(Name name, Integer value) {
        if (metricsEnabled) {
            getOrCreate(name).set(value);
        }
    }

    public static Integer get(Name name) {
        return (int) getOrCreate(name).get();
    }

    public void increment(Name name) {
        if (metricsEnabled) {
            getOrCreate(name).inc();
        }
    }

    public void increment(Action.Type type) {
        if (metricsEnabled) {
            increment(Name.valueOf(type.name() + "_ACTIONS_COUNT"));
        }
    }

    public void decrement(Name name) {
        if (metricsEnabled) {
            getOrCreate(name).dec();
        }
    }

    public void decrement(Action.Type type) {
        if (metricsEnabled) {
            decrement(Name.valueOf(type.name() + "_ACTIONS_COUNT"));
        }
    }

    public static void clearRequestAndExpectationMetrics() {
        clear(Name.REQUESTS_RECEIVED_COUNT);
        clear(Name.EXPECTATIONS_NOT_MATCHED_COUNT);
        clear(Name.RESPONSE_EXPECTATIONS_MATCHED_COUNT);
    }

    public static void clearActionMetrics() {
        clear(Name.FORWARD_ACTIONS_COUNT);
        clear(Name.FORWARD_TEMPLATE_ACTIONS_COUNT);
        clear(Name.FORWARD_CLASS_CALLBACK_ACTIONS_COUNT);
        clear(Name.FORWARD_OBJECT_CALLBACK_ACTIONS_COUNT);
        clear(Name.FORWARD_REPLACE_ACTIONS_COUNT);
        clear(Name.RESPONSE_ACTIONS_COUNT);
        clear(Name.RESPONSE_TEMPLATE_ACTIONS_COUNT);
        clear(Name.RESPONSE_CLASS_CALLBACK_ACTIONS_COUNT);
        clear(Name.RESPONSE_OBJECT_CALLBACK_ACTIONS_COUNT);
        clear(Name.ERROR_ACTIONS_COUNT);
    }

    public static void clearWebSocketMetrics() {
        clear(Name.WEBSOCKET_CALLBACK_CLIENTS_COUNT);
        clear(Name.WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT);
        clear(Name.WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT);
    }

    public enum Name {
        REQUESTS_RECEIVED_COUNT("Expectation not matched count"),
        EXPECTATIONS_NOT_MATCHED_COUNT("Expectation not matched count"),
        RESPONSE_EXPECTATIONS_MATCHED_COUNT("Response expectation matched count"),
        FORWARD_EXPECTATIONS_MATCHED_COUNT("Forward expectation matched count"),
        FORWARD_ACTIONS_COUNT("Action forward count"),
        FORWARD_TEMPLATE_ACTIONS_COUNT("Action forward template count"),
        FORWARD_CLASS_CALLBACK_ACTIONS_COUNT("Action forward class callback count"),
        FORWARD_OBJECT_CALLBACK_ACTIONS_COUNT("Action forward object callback count"),
        FORWARD_REPLACE_ACTIONS_COUNT("Action forward replace count"),
        RESPONSE_ACTIONS_COUNT("Action response count"),
        RESPONSE_TEMPLATE_ACTIONS_COUNT("Action response template count"),
        RESPONSE_CLASS_CALLBACK_ACTIONS_COUNT("Action response class callback count"),
        RESPONSE_OBJECT_CALLBACK_ACTIONS_COUNT("Action response object callback count"),
        ERROR_ACTIONS_COUNT("Action error count"),
        WEBSOCKET_CALLBACK_CLIENTS_COUNT("Websocket callback client count"),
        WEBSOCKET_CALLBACK_RESPONSE_HANDLERS_COUNT("Websocket callback response handler count"),
        WEBSOCKET_CALLBACK_FORWARD_HANDLERS_COUNT("Websocket callback forward handler count");

        public final String description;

        Name(String description) {
            this.description = description;
        }
    }
}
