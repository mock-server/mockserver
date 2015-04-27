package org.mockserver.matchers;

import org.joda.time.DateTime;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class TimeToLive extends ObjectWithReflectiveEqualsHashCodeToString {

    private final TimeUnit timeUnit;
    private final Long timeToLive;
    private final boolean unlimited;
    private final DateTime createdDate;
    private DateTime endDate;

    private TimeToLive(TimeUnit timeUnit, Long timeToLive, boolean unlimited) {
        addFieldsExcludedFromEqualsAndHashCode("createdDate", "endDate");
        this.timeUnit = timeUnit;
        this.timeToLive = timeToLive;
        this.unlimited = unlimited;
        createdDate = DateTime.now();
        if (!unlimited) {
            endDate = DateTime.now().plus(timeUnit.toMillis(timeToLive));
        }
    }

    public static TimeToLive unlimited() {
        return new TimeToLive(null, null, true);
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

    public boolean isUnlimited() {
        return unlimited;
    }

    public boolean stillAlive() {
        if (unlimited || endDate.isAfterNow()) {
            return true;
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("Remaining time is " + (endDate.getMillis() - createdDate.getMillis()) + "ms");
            }
            return false;
        }
    }
}
