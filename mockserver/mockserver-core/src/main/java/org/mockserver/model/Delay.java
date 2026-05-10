package org.mockserver.model;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class Delay extends ObjectWithReflectiveEqualsHashCodeToString {

    private final TimeUnit timeUnit;
    private final long value;
    private final DelayDistribution distribution;

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

    public static Delay uniform(TimeUnit timeUnit, long min, long max) {
        return new Delay(timeUnit, 0, DelayDistribution.uniform(min, max));
    }

    public static Delay logNormal(TimeUnit timeUnit, long median, long p99) {
        return new Delay(timeUnit, 0, DelayDistribution.logNormal(median, p99));
    }

    public static Delay gaussian(TimeUnit timeUnit, long mean, long stdDev) {
        return new Delay(timeUnit, 0, DelayDistribution.gaussian(mean, stdDev));
    }

    public Delay(TimeUnit timeUnit, long value) {
        this.timeUnit = timeUnit;
        this.value = value;
        this.distribution = null;
    }

    public Delay(TimeUnit timeUnit, long value, DelayDistribution distribution) {
        this.timeUnit = timeUnit;
        this.value = value;
        this.distribution = distribution;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public long getValue() {
        return value;
    }

    public DelayDistribution getDistribution() {
        return distribution;
    }

    public long sampleValueMillis() {
        if (distribution != null && timeUnit != null) {
            long sampled = distribution.sample();
            long millis = timeUnit.toMillis(sampled);
            return Math.max(0, millis);
        }
        if (timeUnit != null) {
            long millis = timeUnit.toMillis(value);
            return Math.max(0, millis);
        }
        return 0;
    }

    public void applyDelay() {
        if (timeUnit != null) {
            try {
                long millis = sampleValueMillis();
                if (millis > 0) {
                    TimeUnit.MILLISECONDS.sleep(millis);
                }
            } catch (InterruptedException ie) {
                throw new RuntimeException("InterruptedException while apply delay to response", ie);
            }
        }
    }
}
