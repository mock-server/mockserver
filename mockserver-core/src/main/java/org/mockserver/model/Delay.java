package org.mockserver.model;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class Delay extends ObjectWithReflectiveEqualsHashCodeToString {

    private final TimeUnit timeUnit;
    private final long value;

    public static Delay milliseconds(long value) {
        return new Delay(TimeUnit.MILLISECONDS, value);
    }

    public static Delay seconds(long value) {
        return new Delay(TimeUnit.SECONDS, value);
    }

    public static Delay minutes(long value) {
        return new Delay(TimeUnit.MINUTES, value);
    }

    public static Delay delay(TimeUnit timeUnit, long value) {
        return new Delay(timeUnit, value);
    }

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
