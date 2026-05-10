package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.DelayDistribution;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class DelayDistributionDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        DelayDistributionDTO dto = new DelayDistributionDTO(DelayDistribution.uniform(100, 500));

        // then
        assertThat(dto.getType(), is(DelayDistribution.Type.UNIFORM));
        assertThat(dto.getMin(), is(100L));
        assertThat(dto.getMax(), is(500L));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // when
        DelayDistributionDTO dto = new DelayDistributionDTO();
        dto.setType(DelayDistribution.Type.GAUSSIAN);
        dto.setMean(200L);
        dto.setStdDev(50L);

        // then
        assertThat(dto.getType(), is(DelayDistribution.Type.GAUSSIAN));
        assertThat(dto.getMean(), is(200L));
        assertThat(dto.getStdDev(), is(50L));
    }

    @Test
    public void shouldHandleNullInput() {
        // when
        DelayDistributionDTO dto = new DelayDistributionDTO(null);

        // then
        assertThat(dto.getType(), is(nullValue()));
        assertThat(dto.getMin(), is(nullValue()));
        assertThat(dto.getMax(), is(nullValue()));
    }

    @Test
    public void shouldBuildObject() {
        // when
        DelayDistributionDTO dto = new DelayDistributionDTO(DelayDistribution.logNormal(200, 800));
        DelayDistribution built = dto.buildObject();

        // then
        assertThat(built.getType(), is(DelayDistribution.Type.LOG_NORMAL));
        assertThat(built.getMedian(), is(200L));
        assertThat(built.getP99(), is(800L));
    }

    @Test
    public void shouldRoundTripAllFields() {
        // given
        DelayDistribution original = new DelayDistribution();
        original.setType(DelayDistribution.Type.UNIFORM);
        original.setMin(10L);
        original.setMax(20L);
        original.setMedian(30L);
        original.setP99(40L);
        original.setMean(50L);
        original.setStdDev(60L);

        // when
        DelayDistributionDTO dto = new DelayDistributionDTO(original);
        DelayDistribution built = dto.buildObject();

        // then
        assertThat(built.getType(), is(DelayDistribution.Type.UNIFORM));
        assertThat(built.getMin(), is(10L));
        assertThat(built.getMax(), is(20L));
        assertThat(built.getMedian(), is(30L));
        assertThat(built.getP99(), is(40L));
        assertThat(built.getMean(), is(50L));
        assertThat(built.getStdDev(), is(60L));
    }
}
