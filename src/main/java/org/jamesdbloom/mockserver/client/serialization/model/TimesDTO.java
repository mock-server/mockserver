package org.jamesdbloom.mockserver.client.serialization.model;

import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.model.ModelObject;

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

    public int getRemainingTimes() {
        return remainingTimes;
    }

    public TimesDTO setRemainingTimes(int remainingTimes) {
        this.remainingTimes = remainingTimes;
        return this;
    }

    public boolean isUnlimited() {
        return unlimited;
    }

    public TimesDTO setUnlimited(boolean unlimited) {
        this.unlimited = unlimited;
        return this;
    }
}
