package org.mockserver.client.serialization.model;

import org.mockserver.matchers.Times;
import org.mockserver.model.ModelObject;

/**
 * @author jamesdbloom
 */
public class TimesDTO extends ModelObject {

    private int remainingTimes;
    private boolean unlimited;

    public TimesDTO(Times times) {
        remainingTimes = times.getRemainingTimes();
        unlimited = times.isUnlimited();
    }

    public TimesDTO() {
    }

    public Times buildObject() {
        if (unlimited) {
            return Times.unlimited();
        } else {
            return Times.exactly(remainingTimes);
        }
    }

    public int getRemainingTimes() {
        return remainingTimes;
    }

    public boolean isUnlimited() {
        return unlimited;
    }
}
