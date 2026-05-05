package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class TimeToLiveDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        TimeToLiveDTO timeToLive = new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.MINUTES, 5L));

        // then
        assertThat(timeToLive.getTimeUnit(), is(TimeUnit.MINUTES));
        assertThat(timeToLive.getTimeToLive(), is(5L));
        assertThat(timeToLive.isUnlimited(), is(false));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        TimeToLive timeToLive = new TimeToLiveDTO(TimeToLive.unlimited()).buildObject();

        // then
        assertThat(timeToLive.isUnlimited(), is(true));

        // when
        timeToLive = new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.MINUTES, 5L)).buildObject();

        // then
        assertThat(timeToLive.getTimeUnit(), is(TimeUnit.MINUTES));
        assertThat(timeToLive.getTimeToLive(), is(5L));
        assertThat(timeToLive.isUnlimited(), is(false));
    }
}
