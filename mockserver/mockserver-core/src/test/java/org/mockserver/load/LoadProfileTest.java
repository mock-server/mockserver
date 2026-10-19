package org.mockserver.load;

import org.junit.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for {@link LoadProfile} shape expansion via {@link LoadProfile#getStages()}. Pure (no
 * global state), so they run in the parallel Surefire phase.
 */
public class LoadProfileTest {

    @Test
    public void getStagesReturnsExpandedStagesWhenShapeSet() {
        LoadProfile profile = LoadProfile.shaped(LoadShape.rampHold(LoadShape.Metric.VU, 20, 10_000L, 50_000L));

        List<LoadStage> stages = profile.getStages();

        assertThat(stages, hasSize(2));
        assertThat(stages.get(0).isVuRamp(), is(true));
        assertThat(stages.get(1).getVus(), is(20));
        // raw stages remain empty — only the shape is stored.
        assertThat(profile.getRawStages(), empty());
    }

    @Test
    public void explicitStagesWinOverShape() {
        LoadProfile profile = LoadProfile.of(LoadStage.constantVus(3, 5_000L))
            .withShape(LoadShape.spike(LoadShape.Metric.VU, 1, 99, 1_000L, 1_000L, 1_000L));

        List<LoadStage> stages = profile.getStages();

        assertThat(stages, hasSize(1));
        assertThat(stages.get(0).getVus(), is(3));
    }

    @Test
    public void getStagesIsEmptyWhenNeitherStagesNorShape() {
        assertThat(new LoadProfile().getStages(), empty());
    }

    @Test
    public void totalDurationAndPeaksReflectShapeExpansion() {
        LoadProfile profile = LoadProfile.shaped(
            LoadShape.spike(LoadShape.Metric.VU, 2, 40, 10_000L, 30_000L, 5_000L));

        assertThat(profile.totalDurationMillis(), is(45_000L));
        assertThat(profile.peakVus(), is(40));
        assertThat(profile.peakRate(), is(0.0));
    }

    @Test
    public void peakRateReflectsRateShapeExpansion() {
        LoadProfile profile = LoadProfile.shaped(
            LoadShape.rampHold(LoadShape.Metric.RATE, 250.0, 10_000L, 20_000L));

        assertThat(profile.peakRate(), is(250.0));
        assertThat(profile.peakVus(), is(0));
        assertThat(profile.totalDurationMillis(), is(30_000L));
    }

    @Test
    public void expansionIsCachedAndStableAcrossCalls() {
        LoadProfile profile = LoadProfile.shaped(LoadShape.rampHold(LoadShape.Metric.VU, 10, 1_000L, 1_000L));
        assertThat(profile.getStages(), sameInstance(profile.getStages()));
    }

    @Test
    public void stageCountReportsShapeSizeWithoutExpanding() {
        LoadProfile profile = LoadProfile.shaped(LoadShape.stairs(LoadShape.Metric.VU, 1, 1, Integer.MAX_VALUE, 1L));

        assertThat(profile.stageCount(), is((long) Integer.MAX_VALUE));
    }

    @Test
    public void stageCountPrefersExplicitStages() {
        LoadProfile profile = LoadProfile.of(LoadStage.constantVus(3, 5_000L), LoadStage.constantVus(4, 5_000L))
            .withShape(LoadShape.stairs(LoadShape.Metric.VU, 1, 1, Integer.MAX_VALUE, 1L));

        assertThat(profile.stageCount(), is(2L));
    }

    @Test
    public void totalDurationSaturatesInsteadOfWrapping() {
        LoadProfile profile = LoadProfile.of(
            LoadStage.constantVus(1, Long.MAX_VALUE),
            LoadStage.constantVus(1, Long.MAX_VALUE),
            LoadStage.constantVus(1, 3L));

        assertThat(profile.totalDurationMillis(), is(Long.MAX_VALUE));
    }
}
