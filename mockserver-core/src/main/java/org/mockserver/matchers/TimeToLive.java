package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class TimeToLive extends ObjectWithReflectiveEqualsHashCodeToString {

    private static final String[] excludedFields = {"endDate"};
    private final TimeUnit timeUnit;
    private final Long timeToLive;
    private final boolean unlimited;
    private Date endDate;

    private TimeToLive(TimeUnit timeUnit, Long timeToLive, boolean unlimited) {
        this.timeUnit = timeUnit;
        this.timeToLive = timeToLive;
        this.unlimited = unlimited;
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
            return false;
        }
    }

    private boolean isAfterNow(Date date) {
        return date.getTime() > System.currentTimeMillis();
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
