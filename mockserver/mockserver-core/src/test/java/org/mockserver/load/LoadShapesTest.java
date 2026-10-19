package org.mockserver.load;

import org.junit.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for {@link LoadShapes#expand} for each shape type and metric. Pure (no global state),
 * so they run in the parallel Surefire phase.
 */
public class LoadShapesTest {

    @Test
    public void expandsNullAndTypelessShapeToEmpty() {
        assertThat(LoadShapes.expand(null), empty());
        assertThat(LoadShapes.expand(new LoadShape()), empty());
    }

    @Test
    public void expandsVuSpikeWithRecoveryHold() {
        LoadShape shape = LoadShape.spike(LoadShape.Metric.VU, 2, 40, 10_000L, 30_000L, 5_000L)
            .withRecoveryHoldMillis(8_000L)
            .withCurve(RampCurve.QUADRATIC);

        List<LoadStage> stages = LoadShapes.expand(shape);

        assertThat(stages, hasSize(4));

        LoadStage up = stages.get(0);
        assertThat(up.getType(), is(LoadStageType.VU));
        assertThat(up.isVuRamp(), is(true));
        assertThat(up.getStartVus(), is(2));
        assertThat(up.getEndVus(), is(40));
        assertThat(up.getDurationMillis(), is(10_000L));
        assertThat(up.getCurve(), is(RampCurve.QUADRATIC));

        LoadStage hold = stages.get(1);
        assertThat(hold.getType(), is(LoadStageType.VU));
        assertThat(hold.isVuRamp(), is(false));
        assertThat(hold.getVus(), is(40));
        assertThat(hold.getDurationMillis(), is(30_000L));

        LoadStage down = stages.get(2);
        assertThat(down.isVuRamp(), is(true));
        assertThat(down.getStartVus(), is(40));
        assertThat(down.getEndVus(), is(2));
        assertThat(down.getDurationMillis(), is(5_000L));
        assertThat(down.getCurve(), is(RampCurve.QUADRATIC));

        LoadStage recovery = stages.get(3);
        assertThat(recovery.isVuRamp(), is(false));
        assertThat(recovery.getVus(), is(2));
        assertThat(recovery.getDurationMillis(), is(8_000L));
    }

    @Test
    public void expandsSpikeWithoutOptionalStages() {
        // no recovery hold and zero ramp-down → only ramp-up + hold.
        LoadShape shape = LoadShape.spike(LoadShape.Metric.VU, 0, 10, 5_000L, 20_000L, 0L);

        List<LoadStage> stages = LoadShapes.expand(shape);

        assertThat(stages, hasSize(2));
        assertThat(stages.get(0).isVuRamp(), is(true));
        assertThat(stages.get(1).getVus(), is(10));
    }

    @Test
    public void expandsRateSpike() {
        LoadShape shape = LoadShape.spike(LoadShape.Metric.RATE, 5.0, 250.0, 10_000L, 30_000L, 10_000L);

        List<LoadStage> stages = LoadShapes.expand(shape);

        assertThat(stages, hasSize(3));
        LoadStage up = stages.get(0);
        assertThat(up.getType(), is(LoadStageType.RATE));
        assertThat(up.isRateRamp(), is(true));
        assertThat(up.getStartRate(), is(5.0));
        assertThat(up.getEndRate(), is(250.0));
        assertThat(up.getMaxVus(), is(nullValue()));

        LoadStage hold = stages.get(1);
        assertThat(hold.getType(), is(LoadStageType.RATE));
        assertThat(hold.getRate(), is(250.0));
        assertThat(hold.getMaxVus(), is(nullValue()));

        assertThat(stages.get(2).getStartRate(), is(250.0));
        assertThat(stages.get(2).getEndRate(), is(5.0));
    }

    @Test
    public void expandsVuStairsAsPureSteps() {
        LoadShape shape = LoadShape.stairs(LoadShape.Metric.VU, 5, 5, 4, 15_000L);

        List<LoadStage> stages = LoadShapes.expand(shape);

        assertThat(stages, hasSize(4));
        for (int i = 0; i < 4; i++) {
            LoadStage stage = stages.get(i);
            assertThat(stage.getType(), is(LoadStageType.VU));
            assertThat(stage.isVuRamp(), is(false));
            assertThat(stage.getVus(), is(5 + i * 5));
            assertThat(stage.getDurationMillis(), is(15_000L));
        }
    }

    @Test
    public void expandsRateStairs() {
        LoadShape shape = LoadShape.stairs(LoadShape.Metric.RATE, 10.0, 20.0, 3, 12_000L);

        List<LoadStage> stages = LoadShapes.expand(shape);

        assertThat(stages, hasSize(3));
        assertThat(stages.get(0).getRate(), is(10.0));
        assertThat(stages.get(1).getRate(), is(30.0));
        assertThat(stages.get(2).getRate(), is(50.0));
        stages.forEach(s -> assertThat(s.getType(), is(LoadStageType.RATE)));
    }

    @Test
    public void expandsZeroStepStairsToEmpty() {
        assertThat(LoadShapes.expand(LoadShape.stairs(LoadShape.Metric.VU, 5, 5, 0, 1_000L)), empty());
        assertThat(LoadShapes.expand(LoadShape.stairs(LoadShape.Metric.VU, 5, 5, 3, 0L)), empty());
    }

    @Test
    public void expandsVuRampHoldDefaultLinear() {
        LoadShape shape = LoadShape.rampHold(LoadShape.Metric.VU, 30, 20_000L, 60_000L);

        List<LoadStage> stages = LoadShapes.expand(shape);

        assertThat(stages, hasSize(2));
        LoadStage ramp = stages.get(0);
        assertThat(ramp.getType(), is(LoadStageType.VU));
        assertThat(ramp.isVuRamp(), is(true));
        assertThat(ramp.getStartVus(), is(0));
        assertThat(ramp.getEndVus(), is(30));
        assertThat(ramp.getDurationMillis(), is(20_000L));
        assertThat(ramp.getCurve(), is(RampCurve.LINEAR));

        LoadStage hold = stages.get(1);
        assertThat(hold.getVus(), is(30));
        assertThat(hold.getDurationMillis(), is(60_000L));
    }

    @Test
    public void expandsRateRampHoldWithCurve() {
        LoadShape shape = LoadShape.rampHold(LoadShape.Metric.RATE, 500.0, 30_000L, 120_000L)
            .withCurve(RampCurve.EXPONENTIAL);

        List<LoadStage> stages = LoadShapes.expand(shape);

        assertThat(stages, hasSize(2));
        LoadStage ramp = stages.get(0);
        assertThat(ramp.getType(), is(LoadStageType.RATE));
        assertThat(ramp.getStartRate(), is(0.0));
        assertThat(ramp.getEndRate(), is(500.0));
        assertThat(ramp.getCurve(), is(RampCurve.EXPONENTIAL));
        assertThat(stages.get(1).getRate(), is(500.0));
    }

    @Test
    public void stageCountMatchesExpansionForEachShapeType() {
        LoadShape[] shapes = {
            LoadShape.spike(LoadShape.Metric.VU, 2, 40, 10_000L, 30_000L, 5_000L).withRecoveryHoldMillis(8_000L),
            LoadShape.spike(LoadShape.Metric.RATE, 1, 10, 0L, 5_000L, 0L),
            LoadShape.stairs(LoadShape.Metric.RATE, 10, 5, 4, 2_000L),
            LoadShape.stairs(LoadShape.Metric.VU, 1, 1, 7, 0L),
            LoadShape.rampHold(LoadShape.Metric.VU, 20, 10_000L, 50_000L),
            new LoadShape()
        };
        for (LoadShape shape : shapes) {
            assertThat(LoadShapes.stageCount(shape), is((long) LoadShapes.expand(shape).size()));
        }
        assertThat(LoadShapes.stageCount(null), is(0L));
    }

    @Test
    public void stageCountOfHugeStairsIsComputedWithoutExpanding() {
        LoadShape shape = LoadShape.stairs(LoadShape.Metric.RATE, 1, 1, Integer.MAX_VALUE, 1L);

        assertThat(LoadShapes.stageCount(shape), is((long) Integer.MAX_VALUE));
    }

    @Test(timeout = 10_000)
    public void expandRejectsStairsBeyondCeilingWithoutAllocatingStages() {
        LoadShape shape = LoadShape.stairs(LoadShape.Metric.VU, 1, 1, Integer.MAX_VALUE, 1L);

        try {
            LoadShapes.expand(shape);
            org.junit.Assert.fail("expected the oversized STAIRS shape to be rejected");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("exceeds the maximum of " + LoadShapes.MAX_EXPANDED_STAGES));
        }
    }

    @Test
    public void expandAllowsStairsAtTheCeiling() {
        LoadShape shape = LoadShape.stairs(LoadShape.Metric.VU, 1, 0, LoadShapes.MAX_EXPANDED_STAGES, 1L);

        assertThat(LoadShapes.expand(shape), hasSize(LoadShapes.MAX_EXPANDED_STAGES));
    }

    @Test
    public void vuSetpointsSaturateAtTheIntRangeInsteadOfWrapping() {
        List<LoadStage> stages = LoadShapes.expand(LoadShape.rampHold(LoadShape.Metric.VU, 4_294_967_297.0, 1_000L, 1_000L));

        assertThat(stages.get(0).getEndVus(), is(Integer.MAX_VALUE));
        assertThat(stages.get(1).getVus(), is(Integer.MAX_VALUE));
        assertThat(LoadShapes.expand(LoadShape.rampHold(LoadShape.Metric.VU, -1e300, 1_000L, 1_000L)).get(1).getVus(), is(Integer.MIN_VALUE));
    }
}
