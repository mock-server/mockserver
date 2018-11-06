package org.mockserver.serialization.model;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.verify.VerificationTimes;

/**
 * @author jamesdbloom
 */
public class VerificationTimesDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<VerificationTimes> {

    private int atLeast;
    private int atMost;

    public VerificationTimesDTO(VerificationTimes times) {
        atLeast = times.getAtLeast();
        atMost = times.getAtMost();
    }

    public VerificationTimesDTO() {
    }

    public VerificationTimes buildObject() {
        return VerificationTimes.between(atLeast, atMost);
    }

    public int getAtLeast() {
        return atLeast;
    }

    public int getAtMost() {
        return atMost;
    }
}
