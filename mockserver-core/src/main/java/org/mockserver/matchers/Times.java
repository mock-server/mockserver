package org.mockserver.matchers;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class Times extends ObjectWithReflectiveEqualsHashCodeToString {

    private static final Times TIMES_UNLIMITED = new Times(-1, true) {
        public final int getRemainingTimes() {
            return -1;
        }

        public final boolean isUnlimited() {
            return true;
        }

        public final boolean greaterThenZero() {
            return true;
        }

        public final Times decrement() {
            return this;
        }
    };

    private int remainingTimes;
    private final boolean unlimited;

    private Times(int remainingTimes, boolean unlimited) {
        this.remainingTimes = remainingTimes;
        this.unlimited = unlimited;
    }

    public static Times unlimited() {
        return TIMES_UNLIMITED;
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
        return unlimited || remainingTimes > 0;
    }

    public Times decrement() {
        if (!unlimited) {
            remainingTimes--;
        }
        return this;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Times clone() {
        if (unlimited) {
            return Times.unlimited();
        } else {
            return Times.exactly(remainingTimes);
        }
    }
}
