package org.mockserver.matchers;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class TimeToLiveTest {

    @Test
    public void shouldCreateCorrectObjects() {
        // when
        assertThat(TimeToLive.unlimited().isUnlimited(), is(true));
        assertThat(TimeToLive.exactly(TimeUnit.MINUTES, 5l).isUnlimited(), is(false));
        assertThat(TimeToLive.exactly(TimeUnit.MINUTES, 5l).getTimeUnit(), is(TimeUnit.MINUTES));
        assertThat(TimeToLive.exactly(TimeUnit.MINUTES, 5l).getTimeToLive(), is(5l));
    }

    @Test
    public void shouldCalculateStillLive() throws InterruptedException {
        // when
        TimeToLive timeToLive = TimeToLive.exactly(TimeUnit.MILLISECONDS, 0l);

        TimeUnit.MILLISECONDS.sleep(5);

        // then
        assertThat(timeToLive.stillAlive(), is(false));
        assertThat(TimeToLive.exactly(TimeUnit.MINUTES, 10l).stillAlive(), is(true));
    }
}
