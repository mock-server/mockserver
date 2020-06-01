package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class Action<T extends Action> extends ObjectWithJsonToString {
    private int hashCode;
    private Delay delay;

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param delay a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     */
    public T withDelay(Delay delay) {
        this.delay = delay;
        this.hashCode = 0;
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
        FORWARD(Direction.FORWARD),
        FORWARD_TEMPLATE(Direction.FORWARD),
        FORWARD_CLASS_CALLBACK(Direction.FORWARD),
        FORWARD_OBJECT_CALLBACK(Direction.FORWARD),
        FORWARD_REPLACE(Direction.FORWARD),
        RESPONSE(Direction.RESPONSE),
        RESPONSE_TEMPLATE(Direction.RESPONSE),
        RESPONSE_CLASS_CALLBACK(Direction.RESPONSE),
        RESPONSE_OBJECT_CALLBACK(Direction.RESPONSE),
        ERROR(Direction.RESPONSE);

        public final Direction direction;

        Type(Direction direction) {
            this.direction = direction;
        }
    }

    public enum Direction {
        FORWARD,
        RESPONSE
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        Action<?> action = (Action<?>) o;
        return Objects.equals(delay, action.delay);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(delay);
        }
        return hashCode;
    }
}
