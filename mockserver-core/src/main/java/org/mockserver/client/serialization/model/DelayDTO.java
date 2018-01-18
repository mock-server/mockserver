package org.mockserver.client.serialization.model;

import org.mockserver.model.Delay;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class DelayDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<Delay> {

    private TimeUnit timeUnit;
    private long value;

    public DelayDTO(Delay delay) {
        if (delay != null) {
            timeUnit = delay.getTimeUnit();
            value = delay.getValue();
        }
    }

    public DelayDTO() {
    }

    public Delay buildObject() {
        return new Delay(timeUnit, value);
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public DelayDTO setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
        return this;
    }

    public long getValue() {
        return value;
    }

    public DelayDTO setValue(long value) {
        this.value = value;
        return this;
    }
}
