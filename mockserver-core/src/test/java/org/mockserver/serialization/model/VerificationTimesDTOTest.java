package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.verify.VerificationTimes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class VerificationTimesDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        VerificationTimesDTO times = new VerificationTimesDTO(VerificationTimes.between(1, 2));

        // then
        assertThat(times.getAtLeast(), is(1));
        assertThat(times.getAtMost(), is(2));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        VerificationTimes times = new VerificationTimesDTO(VerificationTimes.never()).buildObject();

        // then
        assertThat(times.getAtLeast(), is(0));
        assertThat(times.getAtMost(), is(0));

        // when
        times = new VerificationTimesDTO(VerificationTimes.once()).buildObject();

        // then
        assertThat(times.getAtLeast(), is(1));
        assertThat(times.getAtMost(), is(1));

        // when
        times = new VerificationTimesDTO(VerificationTimes.exactly(2)).buildObject();

        // then
        assertThat(times.getAtLeast(), is(2));
        assertThat(times.getAtMost(), is(2));

        // when
        times = new VerificationTimesDTO(VerificationTimes.atLeast(3)).buildObject();

        // then
        assertThat(times.getAtLeast(), is(3));
        assertThat(times.getAtMost(), is(-1));

        // when
        times = new VerificationTimesDTO(VerificationTimes.atMost(4)).buildObject();

        // then
        assertThat(times.getAtLeast(), is(-1));
        assertThat(times.getAtMost(), is(4));
    }

}