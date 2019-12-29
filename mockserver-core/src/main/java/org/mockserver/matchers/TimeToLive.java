package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class TimeToLive extends ObjectWithReflectiveEqualsHashCodeToString {

    private static final String[] EXCLUDED_FIELDS = {"endDate"};
    private static final TimeToLive TIME_TO_LIVE_UNLIMITED = new TimeToLive(null, null, true) {
        public boolean stillAlive() {
            return true;
        }
    };
    private final TimeUnit timeUnit;
    private final Long timeToLive;
    private final boolean unlimited;
    private long endDate;

    private TimeToLive(TimeUnit timeUnit, Long timeToLive, boolean unlimited) {
        this.timeUnit = timeUnit;
        this.timeToLive = timeToLive;
        this.unlimited = unlimited;
        if (!unlimited) {
            endDate = System.currentTimeMillis() + timeUnit.toMillis(timeToLive);
        }
    }

    public static TimeToLive unlimited() {
        return TIME_TO_LIVE_UNLIMITED;
    }

    public static TimeToLive exactly(TimeUnit timeUnit, Long timeToLive) {
        return new TimeToLive(timeUnit, timeToLive, false);
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public Long getTimeToLive() {
        return timeToLive;
    }

    @JsonIgnore
    public long getEndDate() {
        return endDate;
    }

    public TimeToLive setEndDate(long endDate) {
        this.endDate = endDate;
        return this;
    }

    public boolean isUnlimited() {
        return unlimited;
    }

    public boolean stillAlive() {
        return unlimited || isAfterNow(endDate);
    }

    private boolean isAfterNow(long date) {
        return date > System.currentTimeMillis();
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
