package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.Delay;
import org.mockserver.model.DelayDistribution;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 * @author jamesdbloom
 */
public class DelayDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        DelayDTO delay = new DelayDTO(new Delay(TimeUnit.DAYS, 5));

        // then
        assertThat(delay.getTimeUnit(), is(TimeUnit.DAYS));
        assertThat(delay.getValue(), is(5L));
        assertThat(delay.getDistribution(), is(nullValue()));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // when
        DelayDTO delay = new DelayDTO();
        delay.setTimeUnit(TimeUnit.DAYS);
        delay.setValue(5);

        // then
        assertThat(delay.getTimeUnit(), is(TimeUnit.DAYS));
        assertThat(delay.getValue(), is(5L));
    }

    @Test
    public void shouldHandleNullInput() {
        // when
        DelayDTO delay = new DelayDTO(null);

        // then
        assertThat(delay.getTimeUnit(), is(nullValue()));
        assertThat(delay.getValue(), is(0L));
        assertThat(delay.getDistribution(), is(nullValue()));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithDistribution() {
        // when
        DelayDTO delay = new DelayDTO(Delay.uniform(TimeUnit.MILLISECONDS, 100, 500));

        // then
        assertThat(delay.getTimeUnit(), is(TimeUnit.MILLISECONDS));
        assertThat(delay.getDistribution(), is(notNullValue()));
        assertThat(delay.getDistribution().getType(), is(DelayDistribution.Type.UNIFORM));
        assertThat(delay.getDistribution().getMin(), is(100L));
        assertThat(delay.getDistribution().getMax(), is(500L));
    }

    @Test
    public void shouldBuildObjectWithDistribution() {
        // when
        DelayDTO delay = new DelayDTO(Delay.logNormal(TimeUnit.MILLISECONDS, 200, 800));
        Delay built = delay.buildObject();

        // then
        assertThat(built.getTimeUnit(), is(TimeUnit.MILLISECONDS));
        assertThat(built.getDistribution(), is(notNullValue()));
        assertThat(built.getDistribution().getType(), is(DelayDistribution.Type.LOG_NORMAL));
        assertThat(built.getDistribution().getMedian(), is(200L));
        assertThat(built.getDistribution().getP99(), is(800L));
    }

    @Test
    public void shouldBuildObjectWithoutDistribution() {
        // when
        DelayDTO delay = new DelayDTO(new Delay(TimeUnit.SECONDS, 5));
        Delay built = delay.buildObject();

        // then
        assertThat(built.getTimeUnit(), is(TimeUnit.SECONDS));
        assertThat(built.getValue(), is(5L));
        assertThat(built.getDistribution(), is(nullValue()));
    }

    @Test
    public void shouldSetDistributionViaSetter() {
        // when
        DelayDTO delay = new DelayDTO();
        delay.setTimeUnit(TimeUnit.MILLISECONDS);
        DelayDistributionDTO distributionDTO = new DelayDistributionDTO();
        distributionDTO.setType(DelayDistribution.Type.GAUSSIAN);
        distributionDTO.setMean(200L);
        distributionDTO.setStdDev(50L);
        delay.setDistribution(distributionDTO);

        // then
        Delay built = delay.buildObject();
        assertThat(built.getDistribution(), is(notNullValue()));
        assertThat(built.getDistribution().getType(), is(DelayDistribution.Type.GAUSSIAN));
        assertThat(built.getDistribution().getMean(), is(200L));
        assertThat(built.getDistribution().getStdDev(), is(50L));
    }
}
