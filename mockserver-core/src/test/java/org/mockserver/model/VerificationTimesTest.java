package org.mockserver.model;

import org.junit.Test;
import org.mockserver.verify.VerificationTimes;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.mockserver.verify.VerificationTimes.once;

/**
 * @author jamesdbloom
 */
public class VerificationTimesTest {

    @Test
    public void shouldCreateCorrectObjectForAtLeast() {
        // when
        VerificationTimes times = atLeast(2);

        // then
        assertThat(times.isExact(), is(false));
        assertThat(times.getCount(), is(2));
    }

    @Test
    public void shouldCreateCorrectObjectForExactly() {
        // when
        VerificationTimes times = exactly(2);

        // then
        assertThat(times.isExact(), is(true));
        assertThat(times.getCount(), is(2));
    }
    @Test
    public void shouldCreateCorrectObjectForOnce() {
        // when
        VerificationTimes times = once();

        // then
        assertThat(times.isExact(), is(true));
        assertThat(times.getCount(), is(1));
    }

}
