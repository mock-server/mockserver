package org.mockserver.verify;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.verify.VerificationTimes.*;

/**
 * @author jamesdbloom
 */
public class VerificationTimesTest {

    @Test
    public void shouldCreateCorrectObjectForAtLeast() {
        // when
        VerificationTimes times = atLeast(2);

        // then
        assertThat(times.getAtLeast(), is(2));
        assertThat(times.getAtMost(), is(-1));
    }

    @Test
    public void shouldCreateCorrectObjectForAtMost() {
        // when
        VerificationTimes times = atMost(2);

        // then
        assertThat(times.getAtLeast(), is(-1));
        assertThat(times.getAtMost(), is(2));
    }

    @Test
    public void shouldCreateCorrectObjectForOnce() {
        // when
        VerificationTimes times = once();

        // then
        assertThat(times.getAtLeast(), is(1));
        assertThat(times.getAtMost(), is(1));
    }

    @Test
    public void shouldCreateCorrectObjectForExactly() {
        // when
        VerificationTimes times = exactly(2);

        // then
        assertThat(times.getAtLeast(), is(2));
        assertThat(times.getAtMost(), is(2));
    }

    @Test
    public void shouldCreateCorrectObjectForBetween() {
        // when
        VerificationTimes times = between(1,2);

        // then
        assertThat(times.getAtLeast(), is(1));
        assertThat(times.getAtMost(), is(2));
    }

    @Test
    public void shouldMatchBetweenCorrectly() {
        // when
        VerificationTimes times = between(1,2);

        // then
        assertThat(times.matches(0), is(false));
        assertThat(times.matches(1), is(true));
        assertThat(times.matches(2), is(true));
        assertThat(times.matches(3), is(false));
    }

    @Test
    public void shouldMatchExactCorrectly() {
        // when
        VerificationTimes times = exactly(2);

        // then
        assertThat(times.matches(0), is(false));
        assertThat(times.matches(1), is(false));
        assertThat(times.matches(2), is(true));
        assertThat(times.matches(3), is(false));
    }

    @Test
    public void shouldMatchAtLeastCorrectly() {
        // when
        VerificationTimes times = atLeast(2);

        // then
        assertThat(times.matches(0), is(false));
        assertThat(times.matches(1), is(false));
        assertThat(times.matches(2), is(true));
        assertThat(times.matches(3), is(true));
    }

    @Test
    public void shouldMatchAtMostCorrectly() {
        // when
        VerificationTimes times = atMost(2);

        // then
        assertThat(times.matches(0), is(true));
        assertThat(times.matches(1), is(true));
        assertThat(times.matches(2), is(true));
        assertThat(times.matches(3), is(false));
    }

    @Test
    public void shouldMatchAtMostZeroCorrectly() {
        // when
        VerificationTimes times = atMost(0);

        // then
        assertThat(times.matches(0), is(true));
        assertThat(times.matches(1), is(false));
        assertThat(times.matches(2), is(false));
        assertThat(times.matches(3), is(false));
    }

    @Test
    public void shouldGenerateCorrectToString() {
        // then
        assertThat(once().toString(), is("exactly once"));
        assertThat(exactly(0).toString(), is("exactly 0 times"));
        assertThat(exactly(1).toString(), is("exactly once"));
        assertThat(exactly(2).toString(), is("exactly 2 times"));
        assertThat(atLeast(0).toString(), is("at least 0 times"));
        assertThat(atLeast(1).toString(), is("at least once"));
        assertThat(atLeast(2).toString(), is("at least 2 times"));
        assertThat(atMost(0).toString(), is("at most 0 times"));
        assertThat(atMost(1).toString(), is("at most once"));
        assertThat(atMost(2).toString(), is("at most 2 times"));
        assertThat(between(1, 2).toString(), is("between 1 and 2 times"));
        assertThat(between(1, 1).toString(), is("exactly once"));
        assertThat(between(2, 2).toString(), is("exactly 2 times"));
    }
}
