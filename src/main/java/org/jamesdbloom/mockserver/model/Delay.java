package org.jamesdbloom.mockserver.model;

import static org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;

import org.codehaus.jackson.annotate.JsonAutoDetect;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public class Delay extends ModelObject {

    private final TimeUnit timeUnit;
    private final long value;

    public Delay(TimeUnit timeUnit, long value) {
        this.timeUnit = timeUnit;
        this.value = value;
    }

    public void applyDelay() {
        try {
            timeUnit.sleep(value);
        } catch (InterruptedException e) {
            throw new RuntimeException("InterruptedException while apply delay to response", e);
        }
    }
}
