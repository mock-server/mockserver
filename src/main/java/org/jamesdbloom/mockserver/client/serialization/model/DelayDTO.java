package org.jamesdbloom.mockserver.client.serialization.model;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.jamesdbloom.mockserver.model.Delay;
import org.jamesdbloom.mockserver.model.ModelObject;

import java.util.concurrent.TimeUnit;

import static org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;

/**
 * @author jamesdbloom
 */
public class DelayDTO extends ModelObject {

    private TimeUnit timeUnit;
    private long value;

    public DelayDTO(Delay delay) {
        this.timeUnit = delay.getTimeUnit();
        this.value = delay.getValue();
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }
}
