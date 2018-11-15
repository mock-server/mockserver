package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public abstract class Action<T extends Action> extends ObjectWithJsonToString {

    private Delay delay;

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param delay a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     */
    public T withDelay(Delay delay) {
        this.delay = delay;
        return (T) this;
    }

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param timeUnit a the time unit, for example TimeUnit.SECONDS
     * @param value    a the number of time units to delay the response
     */
    public T withDelay(TimeUnit timeUnit, long value) {
        this.delay = new Delay(timeUnit, value);
        return (T) this;
    }

    public Delay getDelay() {
        return delay;
    }

    @JsonIgnore
    public abstract Type getType();

    public enum Type {
        FORWARD,
        FORWARD_TEMPLATE,
        FORWARD_CLASS_CALLBACK,
        FORWARD_OBJECT_CALLBACK,
        FORWARD_REPLACE,
        RESPONSE,
        RESPONSE_TEMPLATE,
        RESPONSE_CLASS_CALLBACK,
        RESPONSE_OBJECT_CALLBACK,
        ERROR
    }
}
