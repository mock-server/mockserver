package org.jamesdbloom.mockserver.model;

import org.jamesdbloom.mockserver.client.serialization.model.DelayDTO;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class Delay extends ModelObject {

    private final TimeUnit timeUnit;
    private final long value;

    public Delay(TimeUnit timeUnit, long value) {
        this.timeUnit = timeUnit;
        this.value = value;
    }

    public Delay(DelayDTO delay) {
        timeUnit = delay.getTimeUnit();
        value = delay.getValue();
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public long getValue() {
        return value;
    }

    public void applyDelay() {
        try {
            timeUnit.sleep(value);
        } catch (InterruptedException e) {
            throw new RuntimeException("InterruptedException while apply delay to response", e);
        }
    }
}
