package org.mockserver.client.serialization.model;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.verify.VerificationTimes;

/**
 * @author jamesdbloom
 */
public class VerificationTimesDTO extends ObjectWithReflectiveEqualsHashCodeToString {

    private int lowerBound;
    private int upperBound;

    public VerificationTimesDTO(VerificationTimes times) {
        lowerBound = times.getLowerBound().or(-1);
        upperBound = times.getUpperBound().or(-1);
    }

    @SuppressWarnings("unused")
    public VerificationTimesDTO() {
    }

    public VerificationTimes buildObject() {
        if (lowerBound >= 0) {
            if (upperBound >= 0) {
                return VerificationTimes.between(lowerBound, upperBound);
            } else {
                return VerificationTimes.atLeast(lowerBound);
            }
        } else {
            if (upperBound >= 0) {
                return VerificationTimes.atMost(upperBound);
            } else {
                // Should never happen, VerificationTimes doesn't allow this invariant.
                throw new IllegalStateException("Neither lower nor upper bound is defined");
            }
        }
    }

    public int getLowerBound() {
        return lowerBound;
    }

    public int getUpperBound() {
        return upperBound;
    }
}
