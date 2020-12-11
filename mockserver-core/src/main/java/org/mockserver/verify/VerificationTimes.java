package org.mockserver.verify;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class VerificationTimes extends ObjectWithReflectiveEqualsHashCodeToString {

    private final int atLeast;
    private final int atMost;

    private VerificationTimes(int atLeast, int atMost) {
        this.atMost = atMost;
        this.atLeast = atLeast;
    }

    public static VerificationTimes never() {
        return new VerificationTimes(0, 0);
    }

    public static VerificationTimes once() {
        return new VerificationTimes(1, 1);
    }

    public static VerificationTimes exactly(int count) {
        return new VerificationTimes(count, count);
    }

    public static VerificationTimes atLeast(int count) {
        return new VerificationTimes(count, -1);
    }

    public static VerificationTimes atMost(int count) {
        return new VerificationTimes(-1, count);
    }

    public static VerificationTimes between(int atLeast, int atMost) {
        return new VerificationTimes(atLeast, atMost);
    }

    public int getAtLeast() {
        return atLeast;
    }

    public int getAtMost() {
        return atMost;
    }

    public boolean matches(int times) {
        if (atLeast != -1 && times < atLeast) {
            return false;
        } else {
            return atMost == -1 || times <= atMost;
        }
    }

    public String toString() {
        String string = "";
        if (atLeast == atMost) {
            string += "exactly ";
            if (atMost == 1) {
                string += "once";
            } else {
                string += atMost + " times";
            }
        } else if (atMost == -1) {
            string += "at least ";
            if (atLeast == 1) {
                string += "once";
            } else {
                string += atLeast + " times";
            }
        } else if (atLeast == -1) {
            string += "at most ";
            if (atMost == 1) {
                string += "once";
            } else {
                string += atMost + " times";
            }
        } else {
            string += "between " + atLeast + " and " + atMost + " times";
        }
        return string;
    }
}
