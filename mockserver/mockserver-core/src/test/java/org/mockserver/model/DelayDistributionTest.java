package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;

public class DelayDistributionTest {

    @Test
    public void shouldCreateUniformDistribution() {
        DelayDistribution distribution = DelayDistribution.uniform(100, 500);

        assertThat(distribution.getType(), is(DelayDistribution.Type.UNIFORM));
        assertThat(distribution.getMin(), is(100L));
        assertThat(distribution.getMax(), is(500L));
    }

    @Test
    public void shouldCreateLogNormalDistribution() {
        DelayDistribution distribution = DelayDistribution.logNormal(200, 800);

        assertThat(distribution.getType(), is(DelayDistribution.Type.LOG_NORMAL));
        assertThat(distribution.getMedian(), is(200L));
        assertThat(distribution.getP99(), is(800L));
    }

    @Test
    public void shouldCreateGaussianDistribution() {
        DelayDistribution distribution = DelayDistribution.gaussian(200, 50);

        assertThat(distribution.getType(), is(DelayDistribution.Type.GAUSSIAN));
        assertThat(distribution.getMean(), is(200L));
        assertThat(distribution.getStdDev(), is(50L));
    }

    @Test
    public void shouldSampleUniformWithinBounds() {
        DelayDistribution distribution = DelayDistribution.uniform(100, 200);
        for (int i = 0; i < 1000; i++) {
            long sample = distribution.sample();
            assertThat(sample, allOf(greaterThanOrEqualTo(100L), lessThanOrEqualTo(200L)));
        }
    }

    @Test
    public void shouldSampleUniformReturnsFixedValueWhenMinEqualsMax() {
        DelayDistribution distribution = DelayDistribution.uniform(150, 150);
        for (int i = 0; i < 100; i++) {
            assertThat(distribution.sample(), is(150L));
        }
    }

    @Test
    public void shouldSampleLogNormalWithReasonableMean() {
        DelayDistribution distribution = DelayDistribution.logNormal(200, 800);
        long sum = 0;
        int count = 10000;
        for (int i = 0; i < count; i++) {
            long sample = distribution.sample();
            assertThat(sample, greaterThanOrEqualTo(0L));
            sum += sample;
        }
        double mean = (double) sum / count;
        assertThat(mean, allOf(greaterThan(100.0), lessThan(500.0)));
    }

    @Test
    public void shouldSampleGaussianWithReasonableMean() {
        DelayDistribution distribution = DelayDistribution.gaussian(200, 50);
        long sum = 0;
        int count = 10000;
        for (int i = 0; i < count; i++) {
            long sample = distribution.sample();
            assertThat(sample, greaterThanOrEqualTo(0L));
            sum += sample;
        }
        double mean = (double) sum / count;
        assertThat(mean, allOf(greaterThan(150.0), lessThan(250.0)));
    }

    @Test
    public void shouldSampleGaussianClampsNegativeToZero() {
        DelayDistribution distribution = DelayDistribution.gaussian(1, 100);
        for (int i = 0; i < 1000; i++) {
            assertThat(distribution.sample(), greaterThanOrEqualTo(0L));
        }
    }

    @Test
    public void shouldReturnZeroForNullType() {
        DelayDistribution distribution = new DelayDistribution();
        assertThat(distribution.sample(), is(0L));
    }

    @Test
    public void shouldReturnMedianWhenP99EqualsMedian() {
        DelayDistribution distribution = DelayDistribution.logNormal(200, 200);
        for (int i = 0; i < 100; i++) {
            assertThat(distribution.sample(), is(200L));
        }
    }

    @Test
    public void shouldReturnMeanWhenStdDevIsZero() {
        DelayDistribution distribution = DelayDistribution.gaussian(200, 0);
        for (int i = 0; i < 100; i++) {
            assertThat(distribution.sample(), is(200L));
        }
    }

    @Test
    public void shouldSetAndGetFieldsViaSetter() {
        DelayDistribution distribution = new DelayDistribution();
        distribution.setType(DelayDistribution.Type.UNIFORM);
        distribution.setMin(10L);
        distribution.setMax(20L);
        distribution.setMedian(30L);
        distribution.setP99(40L);
        distribution.setMean(50L);
        distribution.setStdDev(60L);

        assertThat(distribution.getType(), is(DelayDistribution.Type.UNIFORM));
        assertThat(distribution.getMin(), is(10L));
        assertThat(distribution.getMax(), is(20L));
        assertThat(distribution.getMedian(), is(30L));
        assertThat(distribution.getP99(), is(40L));
        assertThat(distribution.getMean(), is(50L));
        assertThat(distribution.getStdDev(), is(60L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNegativeMinForUniform() {
        DelayDistribution.uniform(-1, 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectMaxLessThanMinForUniform() {
        DelayDistribution.uniform(200, 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNonPositiveMedianForLogNormal() {
        DelayDistribution.logNormal(0, 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectP99LessThanMedianForLogNormal() {
        DelayDistribution.logNormal(200, 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNegativeMeanForGaussian() {
        DelayDistribution.gaussian(-1, 50);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNegativeStdDevForGaussian() {
        DelayDistribution.gaussian(200, -1);
    }
}
