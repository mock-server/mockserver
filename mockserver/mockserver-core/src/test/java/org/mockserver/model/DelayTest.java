package org.mockserver.model;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
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
        assertThat(delay.getDistribution(), is(nullValue()));
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

    @Test
    public void shouldCreateUniformDelay() {
        // when
        Delay delay = uniform(TimeUnit.MILLISECONDS, 100, 500);

        // then
        assertThat(delay.getTimeUnit(), is(TimeUnit.MILLISECONDS));
        assertThat(delay.getDistribution(), is(notNullValue()));
        assertThat(delay.getDistribution().getType(), is(DelayDistribution.Type.UNIFORM));
        assertThat(delay.getDistribution().getMin(), is(100L));
        assertThat(delay.getDistribution().getMax(), is(500L));
    }

    @Test
    public void shouldCreateLogNormalDelay() {
        // when
        Delay delay = logNormal(TimeUnit.MILLISECONDS, 200, 800);

        // then
        assertThat(delay.getTimeUnit(), is(TimeUnit.MILLISECONDS));
        assertThat(delay.getDistribution(), is(notNullValue()));
        assertThat(delay.getDistribution().getType(), is(DelayDistribution.Type.LOG_NORMAL));
        assertThat(delay.getDistribution().getMedian(), is(200L));
        assertThat(delay.getDistribution().getP99(), is(800L));
    }

    @Test
    public void shouldCreateGaussianDelay() {
        // when
        Delay delay = gaussian(TimeUnit.MILLISECONDS, 200, 50);

        // then
        assertThat(delay.getTimeUnit(), is(TimeUnit.MILLISECONDS));
        assertThat(delay.getDistribution(), is(notNullValue()));
        assertThat(delay.getDistribution().getType(), is(DelayDistribution.Type.GAUSSIAN));
        assertThat(delay.getDistribution().getMean(), is(200L));
        assertThat(delay.getDistribution().getStdDev(), is(50L));
    }

    @Test
    public void shouldSampleFixedDelayValueMillis() {
        // when
        Delay delay = new Delay(TimeUnit.SECONDS, 3);

        // then
        assertThat(delay.sampleValueMillis(), is(3000L));
    }

    @Test
    public void shouldSampleDistributionDelayValueMillis() {
        // when
        Delay delay = uniform(TimeUnit.MILLISECONDS, 100, 200);

        // then
        for (int i = 0; i < 100; i++) {
            long millis = delay.sampleValueMillis();
            assertThat(millis, allOf(greaterThanOrEqualTo(100L), lessThanOrEqualTo(200L)));
        }
    }

    @Test
    public void shouldSampleDistributionDelayValueMillisWithTimeUnitConversion() {
        // when
        Delay delay = uniform(TimeUnit.SECONDS, 1, 2);

        // then
        for (int i = 0; i < 100; i++) {
            long millis = delay.sampleValueMillis();
            assertThat(millis, allOf(greaterThanOrEqualTo(1000L), lessThanOrEqualTo(2000L)));
        }
    }

    @Test
    public void shouldReturnZeroMillisForNullTimeUnit() {
        // when
        Delay delay = new Delay(null, 5);

        // then
        assertThat(delay.sampleValueMillis(), is(0L));
    }
}
