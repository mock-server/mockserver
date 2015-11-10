package org.mockserver.matchers;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class TimeToLive extends ObjectWithReflectiveEqualsHashCodeToString {

    private final TimeUnit timeUnit;
    private final Long timeToLive;
    private final boolean unlimited;
    private final Date createdDate;
    private Date endDate;

    private TimeToLive(TimeUnit timeUnit, Long timeToLive, boolean unlimited) {
        addFieldsExcludedFromEqualsAndHashCode("createdDate", "endDate");
        this.timeUnit = timeUnit;
        this.timeToLive = timeToLive;
        this.unlimited = unlimited;
        createdDate = new Date();
        if (!unlimited) {
            endDate = new Date(System.currentTimeMillis() + timeUnit.toMillis(timeToLive));
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
        if (unlimited || isAfterNow(endDate)) {
            return true;
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("Remaining time is " + (endDate.getTime() - createdDate.getTime()) + "ms");
            }
            return false;
        }
    }

    private boolean isAfterNow(Date date) {
        return date.getTime() > System.currentTimeMillis();
    }
}
