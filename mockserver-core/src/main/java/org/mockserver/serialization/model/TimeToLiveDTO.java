package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class TimeToLiveDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<TimeToLive> {

    private static final String[] EXCLUDED_FIELDS = {"key", "endDate"};
    private TimeUnit timeUnit;
    private Long timeToLive;
    private Long endDate;
    private boolean unlimited;

    public TimeToLiveDTO(TimeToLive timeToLive) {
        this.timeUnit = timeToLive.getTimeUnit();
        this.timeToLive = timeToLive.getTimeToLive();
        this.endDate = timeToLive.getEndDate();
        this.unlimited = timeToLive.isUnlimited();
    }

    public TimeToLiveDTO() {
    }


    public TimeToLive buildObject() {
        if (unlimited) {
            return TimeToLive.unlimited();
        } else {
            TimeToLive exactly = TimeToLive.exactly(timeUnit, timeToLive);
            if (this.endDate != null) {
                exactly.setEndDate(this.endDate);
            }
            return exactly;
        }
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public Long getTimeToLive() {
        return timeToLive;
    }

    public Long getEndDate() {
        return endDate;
    }

    public boolean isUnlimited() {
        return unlimited;
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
