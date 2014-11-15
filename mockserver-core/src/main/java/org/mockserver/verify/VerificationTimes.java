package org.mockserver.verify;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class VerificationTimes extends ObjectWithReflectiveEqualsHashCodeToString {

    private final int count;
    private final boolean exact;

    private VerificationTimes(int count, boolean exact) {
        this.count = count;
        this.exact = exact;
    }

    public static VerificationTimes once() {
        return new VerificationTimes(1, true);
    }

    public static VerificationTimes exactly(int count) {
        return new VerificationTimes(count, true);
    }

    public static VerificationTimes atLeast(int count) {
        return new VerificationTimes(count, false);
    }

    public int getCount() {
        return count;
    }

    public boolean isExact() {
        return exact;
    }

    public String toString() {
        String string = "";
        if (exact) {
            string += "exactly ";
        } else {
            string += "at least ";
        }
        if (count == 1) {
            string += "once";
        } else {
            string += count + " times";
        }
        return string;
    }
}
