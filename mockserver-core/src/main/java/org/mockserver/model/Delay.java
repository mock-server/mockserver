package org.mockserver.model;

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

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public long getValue() {
        return value;
    }

    public void applyDelay() {
        if (timeUnit != null) {
            try {
                timeUnit.sleep(value);
            } catch (InterruptedException ie) {
                logger.error("InterruptedException while apply delay to response", ie);
                throw new RuntimeException("InterruptedException while apply delay to response", ie);
            }
        }
    }
}
