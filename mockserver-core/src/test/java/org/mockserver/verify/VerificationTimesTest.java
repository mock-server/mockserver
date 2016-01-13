package org.mockserver.verify;

import com.google.common.base.Optional;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.verify.VerificationTimes.*;

/**
 * @author jamesdbloom
 */
public class VerificationTimesTest {

    @Test
    public void shouldCreateCorrectObjectForBetween() {
        VerificationTimes times = between(2, 3);

        assertThat(times.getExactCount().isPresent(), is(false));
        assertThat(times.getUpperBound(), is(Optional.of(3)));
        assertThat(times.getLowerBound(), is(Optional.of(2)));
    }

    @Test
    public void shouldCreateCorrectObjectForAtLeast() {
        VerificationTimes times = atLeast(2);

        assertThat(times.getExactCount().isPresent(), is(false));
        assertThat(times.getUpperBound().isPresent(), is(false));
        assertThat(times.getLowerBound(), is(Optional.of(2)));
    }

    @Test
    public void shouldCreateCorrectObjectForAtMost() {
        VerificationTimes times = atMost(2);

        assertThat(times.getExactCount().isPresent(), is(false));
        assertThat(times.getUpperBound(), is(Optional.of(2)));
        assertThat(times.getLowerBound().isPresent(), is(false));
    }

    @Test
    public void shouldCreateCorrectObjectForExactly() {
        VerificationTimes times = exactly(2);

        assertThat(times.getExactCount(), is(Optional.of(2)));
        assertThat(times.getUpperBound(), is(Optional.of(2)));
        assertThat(times.getLowerBound(), is(Optional.of(2)));
    }

    @Test
    public void shouldCreateCorrectObjectForOnce() {
        VerificationTimes times = once();

        assertThat(times.getExactCount(), is(Optional.of(1)));
        assertThat(times.getUpperBound(), is(Optional.of(1)));
        assertThat(times.getLowerBound(), is(Optional.of(1)));
    }

    @Test
    public void shouldCreateCorrectObjectForNever() {
        VerificationTimes times = never();

        assertThat(times.getExactCount(), is(Optional.of(0)));
        assertThat(times.getUpperBound(), is(Optional.of(0)));
        assertThat(times.getLowerBound(), is(Optional.of(0)));
    }

    @Test
    public void shouldMatchActualCount_between() {
        VerificationTimes times = between(3, 5);

        assertThat(times.matchesActualCount(2), is(false));
        assertThat(times.matchesActualCount(3), is(true));
        assertThat(times.matchesActualCount(4), is(true));
        assertThat(times.matchesActualCount(5), is(true));
        assertThat(times.matchesActualCount(6), is(false));
    }

    @Test
    public void shouldMatchActualCount_exactly() {
        VerificationTimes times = exactly(42);

        assertThat(times.matchesActualCount(41), is(false));
        assertThat(times.matchesActualCount(42), is(true));
        assertThat(times.matchesActualCount(43), is(false));
    }

    @Test
    public void shouldMatchActualCount_never() {
        VerificationTimes times = never();

        assertThat(times.matchesActualCount(1), is(false));
        assertThat(times.matchesActualCount(42), is(false));
        assertThat(times.matchesActualCount(0), is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowNegativeCountForExactly() {
        exactly(-42);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowNegativeCountForBetween_Lower() {
        between(-42, 42);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowNegativeCountForBetween_Upper() {
        between(42, -42);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowBetweenInvalidRange() {
        between(43, 41);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowNegativeCountForAtMost() {
        atLeast(-42);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowNegativeCountForAtLeast() {
        atLeast(-42);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowZeroCountForAtLeast() {
        atLeast(0);
    }

    @Test
    public void shouldGenerateCorrectToString() {
        assertThat(once().toString(), is("exactly once"));
        assertThat(never().toString(), is("never"));
        
        assertThat(atLeast(1).toString(), is("at least once"));
        assertThat(atLeast(2).toString(), is("at least 2 times"));
        
        assertThat(atMost(0).toString(), is("never"));
        assertThat(atMost(1).toString(), is("at most once"));
        assertThat(atMost(2).toString(), is("at most 2 times"));
        
        assertThat(exactly(0).toString(), is("never"));
        assertThat(exactly(1).toString(), is("exactly once"));
        assertThat(exactly(2).toString(), is("exactly 2 times"));

        assertThat(between(41, 43).toString(), is("between 41 and 43 times"));
        assertThat(between(0, 1).toString(), is("between 0 and 1 times"));
    }
}
