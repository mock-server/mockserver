package org.mockserver.matchers;

import org.mockserver.model.EqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class Times extends EqualsHashCodeToString {

    private int remainingTimes;
    private boolean unlimited;

    private Times(int remainingTimes, boolean unlimited) {
        this.remainingTimes = remainingTimes;
        this.unlimited = unlimited;
    }

    public static Times unlimited() {
        return new Times(0, true);
    }

    public static Times once() {
        return new Times(1, false);
    }

    public static Times exactly(int count) {
        return new Times(count, false);
    }

    public int getRemainingTimes() {
        return remainingTimes;
    }

    public boolean isUnlimited() {
        return unlimited;
    }

    public boolean greaterThenZero() {
        if (unlimited || remainingTimes > 0) {
            return true;
        } else {
            logger.trace("Remaining count is 0");
            return false;
        }
    }

    public Times decrement() {
        if (!unlimited) {
            remainingTimes--;
        }
        return this;
    }

    public Times setNotUnlimitedResponses() {
        if (unlimited) {
            remainingTimes = 1;
            unlimited = false;
        }
        return this;
    }
}
