package org.mockserver.client.serialization.model;

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
    public void shouldReturnValueSetInConstructor() {
        // when
        TimeToLiveDTO timeToLive = new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.MINUTES, 5l));

        // then
        assertThat(timeToLive.getTimeUnit(), is(TimeUnit.MINUTES));
        assertThat(timeToLive.getTimeToLive(), is(5l));
        assertThat(timeToLive.isUnlimited(), is(false));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        TimeToLive timeToLive = new TimeToLiveDTO(TimeToLive.unlimited()).buildObject();

        // then
        assertThat(timeToLive.isUnlimited(), is(true));

        // when
        timeToLive = new TimeToLiveDTO(TimeToLive.exactly(TimeUnit.MINUTES, 5l)).buildObject();

        // then
        assertThat(timeToLive.getTimeUnit(), is(TimeUnit.MINUTES));
        assertThat(timeToLive.getTimeToLive(), is(5l));
        assertThat(timeToLive.isUnlimited(), is(false));
    }
}
