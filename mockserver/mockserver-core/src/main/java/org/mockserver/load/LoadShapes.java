package org.mockserver.load;

import java.util.ArrayList;
import java.util.List;

/**
 * Expands a declarative {@link LoadShape} into the ordinary list of {@link LoadStage}s the
 * orchestrator runs. This is the single source of truth for shape → stage expansion; it is pure and
 * side-effect-free (it only reads the shape and builds stages with the existing {@link LoadStage}
 * builders), so {@link LoadProfile#getStages()} can call it lazily and the result is unit-testable
 * without driving traffic.
 *
 * <p>Design choices (documented because they are observable in the expansion):
 * <ul>
 *   <li><b>STAIRS are pure steps</b> — each step is a single constant hold at {@code start + i*step};
 *       there is no inter-step ramp (a flight of stairs is discrete by definition, and pure holds are
 *       the simplest faithful expansion).</li>
 *   <li><b>RATE expansions leave {@code maxVus} unset</b> on every stage, so the global
 *       {@code loadGenerationMaxVirtualUsers} cap governs the auto-scaled VU pool. A shape does not
 *       impose its own (lower) per-stage VU cap — pick the safe option that never silently throttles
 *       below the configured global limit.</li>
 *   <li><b>VU setpoints are integers</b> — VU parameters are rounded to the nearest whole VU when the
 *       shape is built into VU stages; RATE parameters stay fractional iterations/second.</li>
 * </ul>
 */
public final class LoadShapes {

    /**
     * Absolute ceiling on the stages a shape may expand into, independent of configuration, so a
     * shape reached through a path that has not yet been validated cannot exhaust the heap.
     */
    public static final int MAX_EXPANDED_STAGES = 10_000;

    private LoadShapes() {
    }

    /**
     * The number of stages {@link #expand} would produce for {@code shape}, computed without
     * allocating them, so a caller can reject an oversized shape before expanding it.
     */
    public static long stageCount(LoadShape shape) {
        if (shape == null || shape.getType() == null) {
            return 0;
        }
        switch (shape.getType()) {
            case SPIKE:
                return positiveCount(shape.getRampUpMillis(), shape.getHoldMillis(), shape.getRampDownMillis(), shape.getRecoveryHoldMillis());
            case STAIRS:
                int steps = shape.getSteps() != null ? shape.getSteps() : 0;
                return steps > 0 && orZero(shape.getStepDurationMillis()) > 0 ? steps : 0;
            case RAMP_HOLD:
                return positiveCount(shape.getRampMillis(), shape.getHoldMillis());
            default:
                return 0;
        }
    }

    /**
     * Expand {@code shape} into its ordered list of {@link LoadStage}s, or an empty list when
     * {@code shape} is {@code null} or has no {@link LoadShape#getType() type}. The returned list is a
     * fresh, mutable {@link ArrayList} owned by the caller.
     *
     * @throws IllegalArgumentException when the shape would expand to more than
     *                                  {@link #MAX_EXPANDED_STAGES} stages
     */
    public static List<LoadStage> expand(LoadShape shape) {
        List<LoadStage> stages = new ArrayList<>();
        if (shape == null || shape.getType() == null) {
            return stages;
        }
        long count = stageCount(shape);
        if (count > MAX_EXPANDED_STAGES) {
            throw new IllegalArgumentException("'profile.shape' expands to " + count + " stages, which exceeds the maximum of " + MAX_EXPANDED_STAGES);
        }
        switch (shape.getType()) {
            case SPIKE:
                expandSpike(shape, stages);
                break;
            case STAIRS:
                expandStairs(shape, stages);
                break;
            case RAMP_HOLD:
                expandRampHold(shape, stages);
                break;
            default:
                break;
        }
        return stages;
    }

    private static void expandSpike(LoadShape shape, List<LoadStage> stages) {
        double baseline = orZero(shape.getBaseline());
        double peak = orZero(shape.getPeak());
        long rampUp = orZero(shape.getRampUpMillis());
        long hold = orZero(shape.getHoldMillis());
        long rampDown = orZero(shape.getRampDownMillis());
        long recovery = orZero(shape.getRecoveryHoldMillis());
        RampCurve curve = curveOf(shape);

        if (rampUp > 0) {
            stages.add(ramp(shape, baseline, peak, rampUp, curve));
        }
        if (hold > 0) {
            stages.add(hold(shape, peak, hold));
        }
        if (rampDown > 0) {
            stages.add(ramp(shape, peak, baseline, rampDown, curve));
        }
        if (recovery > 0) {
            stages.add(hold(shape, baseline, recovery));
        }
    }

    private static void expandStairs(LoadShape shape, List<LoadStage> stages) {
        double start = orZero(shape.getStart());
        double step = orZero(shape.getStep());
        int steps = shape.getSteps() != null ? shape.getSteps() : 0;
        long stepDuration = orZero(shape.getStepDurationMillis());

        for (int i = 0; i < steps; i++) {
            if (stepDuration > 0) {
                stages.add(hold(shape, start + i * step, stepDuration));
            }
        }
    }

    private static void expandRampHold(LoadShape shape, List<LoadStage> stages) {
        double target = orZero(shape.getTarget());
        long ramp = orZero(shape.getRampMillis());
        long hold = orZero(shape.getHoldMillis());
        RampCurve curve = curveOf(shape);

        if (ramp > 0) {
            stages.add(ramp(shape, 0.0, target, ramp, curve));
        }
        if (hold > 0) {
            stages.add(hold(shape, target, hold));
        }
    }

    /** A constant-setpoint stage at {@code value} for the shape's metric. */
    private static LoadStage hold(LoadShape shape, double value, long durationMillis) {
        if (shape.getMetric() == LoadShape.Metric.RATE) {
            return LoadStage.constantRate(value, durationMillis);
        }
        return LoadStage.constantVus(toVus(value), durationMillis);
    }

    /** A ramp stage from {@code start} to {@code end} along {@code curve} for the shape's metric. */
    private static LoadStage ramp(LoadShape shape, double start, double end, long durationMillis, RampCurve curve) {
        if (shape.getMetric() == LoadShape.Metric.RATE) {
            return LoadStage.rampRate(start, end, durationMillis, curve);
        }
        return LoadStage.rampVus(toVus(start), toVus(end), durationMillis, curve);
    }

    /** Rounds to a whole VU count, saturating at the int range so an oversized value is not wrapped. */
    private static int toVus(double value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(value)));
    }

    private static RampCurve curveOf(LoadShape shape) {
        return shape.getCurve() != null ? shape.getCurve() : RampCurve.LINEAR;
    }

    private static int positiveCount(Long... durations) {
        int count = 0;
        for (Long duration : durations) {
            if (orZero(duration) > 0) {
                count++;
            }
        }
        return count;
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    private static long orZero(Long value) {
        return value != null ? value : 0L;
    }
}
