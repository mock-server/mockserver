package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.same;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.VerificationTimes.atLeast;
import static org.mockserver.model.VerificationTimes.exactly;
import static org.mockserver.model.VerificationTimes.once;

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
