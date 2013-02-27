package org.jamesdbloom.mockserver.client.serialization.model;

import org.jamesdbloom.mockserver.model.Delay;
import org.jamesdbloom.mockserver.model.ModelObject;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class DelayDTO extends ModelObject {

    private TimeUnit timeUnit;
    private long value;

    public DelayDTO(Delay delay) {
        timeUnit = delay.getTimeUnit();
        value = delay.getValue();
    }

    public DelayDTO() {
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
