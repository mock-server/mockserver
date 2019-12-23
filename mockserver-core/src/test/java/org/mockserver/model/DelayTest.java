package org.mockserver.model;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Delay.*;

/**
 * @author jamesdbloom
 */
public class DelayTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        Delay delay = new Delay(TimeUnit.DAYS, 5);

        // then
        assertThat(delay.getTimeUnit(), is(TimeUnit.DAYS));
        assertThat(delay.getValue(), is(5L));
    }

    @Test
    public void shouldReturnValuesFromMillisecondsStaticBuilder() {
        // when
        Delay delay = milliseconds(2);

        // then
        assertThat(delay.getTimeUnit(), is(TimeUnit.MILLISECONDS));
        assertThat(delay.getValue(), is(2L));
    }

    @Test
    public void shouldReturnValuesFromSecondsStaticBuilder() {
        // when
        Delay delay = seconds(3);

        // then
        assertThat(delay.getTimeUnit(), is(TimeUnit.SECONDS));
        assertThat(delay.getValue(), is(3L));
    }

    @Test
    public void shouldReturnValuesFromMinutesStaticBuilder() {
        // when
        Delay delay = minutes(4);

        // then
        assertThat(delay.getTimeUnit(), is(TimeUnit.MINUTES));
        assertThat(delay.getValue(), is(4L));
    }

    @Test
    public void shouldReturnValuesFromDelayStaticBuilder() {
        // when
        Delay delay = delay(TimeUnit.DAYS, 5);

        // then
        assertThat(delay.getTimeUnit(), is(TimeUnit.DAYS));
        assertThat(delay.getValue(), is(5L));
    }
}
