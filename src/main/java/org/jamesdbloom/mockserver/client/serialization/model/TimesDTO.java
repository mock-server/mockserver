package org.jamesdbloom.mockserver.client.serialization.model;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.jamesdbloom.mockserver.model.ModelObject;

import static org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;

/**
 * @author jamesdbloom
 */
public class TimesDTO extends ModelObject {

    private int remainingTimes;
    private boolean unlimited;

    public int getRemainingTimes() {
        return remainingTimes;
    }

    public void setRemainingTimes(int remainingTimes) {
        this.remainingTimes = remainingTimes;
    }

    public boolean isUnlimited() {
        return unlimited;
    }

    public void setUnlimited(boolean unlimited) {
        this.unlimited = unlimited;
    }
}
