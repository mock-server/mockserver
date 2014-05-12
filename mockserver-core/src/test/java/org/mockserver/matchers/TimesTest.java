package org.mockserver.matchers;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class TimesTest {

    @Test
    public void shouldCreateCorrectObjects() {
        // when
        assertThat(Times.unlimited().isUnlimited(), is(true));
        assertThat(Times.unlimited().setNotUnlimitedResponses().isUnlimited(), is(false));
        assertThat(Times.once().isUnlimited(), is(false));
        assertThat(Times.once().getRemainingTimes(), is(1));
        assertThat(Times.exactly(5).isUnlimited(), is(false));
        assertThat(Times.exactly(5).getRemainingTimes(), is(5));
    }

    @Test
    public void shouldUpdateCountCorrectly() {
        // given
        Times times = Times.exactly(2);

        // then
        assertThat(times.greaterThenZero(), is(true));
        times.decrement().decrement();
        assertThat(times.greaterThenZero(), is(false));
    }
}
