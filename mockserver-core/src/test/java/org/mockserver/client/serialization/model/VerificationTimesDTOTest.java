package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.VerificationTimes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class VerificationTimesDTOTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // when
        VerificationTimesDTO times = new VerificationTimesDTO(VerificationTimes.exactly(5));

        // then
        assertThat(times.getCount(), is(5));
        assertThat(times.isExact(), is(true));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        VerificationTimes times = new VerificationTimesDTO(VerificationTimes.once()).buildObject();

        // then
        assertThat(times.getCount(), is(1));
        assertThat(times.isExact(), is(true));

        // when
        times = new VerificationTimesDTO(VerificationTimes.exactly(3)).buildObject();

        // then
        assertThat(times.getCount(), is(3));
        assertThat(times.isExact(), is(true));

        // when
        times = new VerificationTimesDTO(VerificationTimes.atLeast(3)).buildObject();

        // then
        assertThat(times.getCount(), is(3));
        assertThat(times.isExact(), is(false));
    }

}