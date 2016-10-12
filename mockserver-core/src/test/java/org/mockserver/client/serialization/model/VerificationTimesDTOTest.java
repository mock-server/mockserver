package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.verify.VerificationTimes;

import static org.junit.Assert.assertEquals;
import static org.mockserver.verify.VerificationTimes.*;

public class VerificationTimesDTOTest {

    @Test
    public void shouldBuildObject_once() {
        assertValidVerificationTimesBuilt(once());
    }

    @Test
    public void shouldBuildObject_never() {
        assertValidVerificationTimesBuilt(never());
    }

    @Test
    public void shouldBuildObject_exactly() {
        assertValidVerificationTimesBuilt(exactly(42));
    }

    @Test
    public void shouldBuildObject_atLeast() {
        assertValidVerificationTimesBuilt(atLeast(42));
    }

    @Test
    public void shouldBuildObject_atMost() {
        assertValidVerificationTimesBuilt(atMost(42));
    }

    @Test
    public void shouldBuildObject_between() {
        assertValidVerificationTimesBuilt(between(41, 42));
    }

    private void assertValidVerificationTimesBuilt(VerificationTimes originalTimes) {
        assertEquals(new VerificationTimesDTO(originalTimes).buildObject(), originalTimes);
    }
}