package org.mockserver.matchers;

import org.mockserver.model.ModelObject;

/**
 * @author jamesdbloom
 */
public class Times extends ModelObject {

    private int remainingTimes;
    private boolean unlimited;

    private Times(int remainingTimes, boolean unlimited) {
        this.remainingTimes = remainingTimes;
        this.unlimited = unlimited;
    }

    public int getRemainingTimes() {
        return remainingTimes;
    }

    public boolean isUnlimited() {
        return unlimited;
    }

    public static Times unlimited() {
        return new Times(1, true);
    }

    public static Times once() {
        return new Times(1, false);
    }

    public static Times exactly(int count) {
        return new Times(count, false);
    }

    public boolean greaterThenZero() {
        if (unlimited || remainingTimes > 0) {
            return true;
        } else {
            logger.trace("Remaining count is 0");
            return false;
        }
    }

    public void decrement() {
        if (!unlimited) {
            remainingTimes--;
        }
    }

    public void setNotUnlimitedResponses() {
        unlimited = false;
    }
}
