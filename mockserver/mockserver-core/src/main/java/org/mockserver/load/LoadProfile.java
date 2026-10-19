package org.mockserver.load;

import org.mockserver.model.ObjectWithJsonToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The load profile of a {@link LoadScenario}: an ordered list of {@link LoadStage}s the orchestrator
 * runs <em>in sequence</em>. Each stage holds or ramps a setpoint — concurrent virtual users
 * (closed model, {@link LoadStageType#VU}), an arrival rate in iterations/second (open model,
 * {@link LoadStageType#RATE}), or no load at all ({@link LoadStageType#PAUSE}) — for its
 * {@code durationMillis}, optionally shaped by a {@link RampCurve}.
 *
 * <p>The total run duration is the sum of the stage durations; the orchestrator advances stage by
 * stage and ends after the last one (or when {@code maxRequests} is hit, or on stop). Per-stage
 * setpoint functions ({@link LoadStage#targetVusAt(long)} / {@link LoadStage#targetRateAt(long)}) are
 * pure and deterministic so ramp progression is unit-testable without driving traffic.
 *
 * <p>A profile may instead carry a declarative {@link LoadShape} (a named SPIKE / STAIRS / RAMP_HOLD
 * pattern). When a {@code shape} is set and no explicit {@code stages} are present,
 * {@link #getStages()} returns the stages {@link LoadShapes#expand expanded} from the shape — so the
 * orchestrator, which only ever calls {@link #getStages()}, needs no change. Explicit stages always
 * win: if both are set the shape is ignored.
 */
public class LoadProfile extends ObjectWithJsonToString {

    private List<LoadStage> stages = new ArrayList<>();
    private LoadShape shape;
    /** Lazily-computed expansion of {@link #shape}; never serialized (derived, not stored). */
    private transient volatile List<LoadStage> expandedShapeStages;

    public LoadProfile() {
    }

    public static LoadProfile loadProfile() {
        return new LoadProfile();
    }

    public static LoadProfile of(LoadStage... stages) {
        LoadProfile profile = new LoadProfile();
        Collections.addAll(profile.stages, stages);
        return profile;
    }

    /** Convenience: a single constant-VU stage. */
    public static LoadProfile constant(int vus, long durationMillis) {
        return of(LoadStage.constantVus(vus, durationMillis));
    }

    /** Convenience: a single linear VU ramp stage. */
    public static LoadProfile linear(int startVus, int endVus, long durationMillis) {
        return of(LoadStage.rampVus(startVus, endVus, durationMillis, RampCurve.LINEAR));
    }

    /** Convenience: a single constant arrival-rate (iterations/second) stage. */
    public static LoadProfile constantRate(double rate, long durationMillis) {
        return of(LoadStage.constantRate(rate, durationMillis));
    }

    /** Convenience: a named load shape. */
    public static LoadProfile shaped(LoadShape shape) {
        return new LoadProfile().withShape(shape);
    }

    /**
     * The ordered stages the orchestrator runs. Explicit {@link #stages} always win; otherwise, when a
     * {@link #shape} is set, this returns its {@link LoadShapes#expand expansion} (computed once and
     * cached). With neither, this is the (empty) explicit stages list.
     */
    public List<LoadStage> getStages() {
        if (stages != null && !stages.isEmpty()) {
            return stages;
        }
        if (shape != null) {
            if (expandedShapeStages == null) {
                // Unsynchronised check-then-set is intentionally benign here: expandedShapeStages is
                // volatile and LoadShapes.expand is a pure, deterministic, idempotent function of the
                // (immutable-for-the-lifetime-of-this-shape) shape, so a rare concurrent double-compute
                // only ever yields an equivalent value, and the volatile write safely publishes it.
                // This is off the hot path, so a lock would add cost without buying correctness.
                expandedShapeStages = LoadShapes.expand(shape);
            }
            return expandedShapeStages;
        }
        return stages;
    }

    /**
     * The number of stages {@link #getStages()} returns, computed without expanding a shape, so a
     * caller can enforce a stage cap before any expansion allocates.
     */
    public long stageCount() {
        if (stages != null && !stages.isEmpty()) {
            return stages.size();
        }
        return LoadShapes.stageCount(shape);
    }

    /** The explicit stages exactly as set (never the shape expansion) — used by serialization. */
    public List<LoadStage> getRawStages() {
        return stages;
    }

    public LoadShape getShape() {
        return shape;
    }

    public LoadProfile withShape(LoadShape shape) {
        this.shape = shape;
        this.expandedShapeStages = null;
        return this;
    }

    public LoadProfile withStages(List<LoadStage> stages) {
        this.stages = stages != null ? stages : new ArrayList<>();
        return this;
    }

    public LoadProfile withStages(LoadStage... stages) {
        this.stages = new ArrayList<>();
        Collections.addAll(this.stages, stages);
        return this;
    }

    public LoadProfile addStage(LoadStage stage) {
        if (this.stages == null) {
            this.stages = new ArrayList<>();
        }
        this.stages.add(stage);
        return this;
    }

    /**
     * Sum of all stage durations — the total run length (reflects the shape expansion). Saturates at
     * {@link Long#MAX_VALUE} rather than wrapping, so oversized durations cannot slip under a cap.
     */
    public long totalDurationMillis() {
        long total = 0;
        for (LoadStage stage : getStages()) {
            long duration = Math.max(0, stage.getDurationMillis());
            total = total > Long.MAX_VALUE - duration ? Long.MAX_VALUE : total + duration;
        }
        return total;
    }

    /** The maximum VU count any stage requests, used to enforce the VU hard cap up-front. */
    public int peakVus() {
        int peak = 0;
        for (LoadStage stage : getStages()) {
            peak = Math.max(peak, stage.peakVus());
        }
        return peak;
    }

    /** The maximum arrival rate any stage requests, used to enforce the rate hard cap up-front. */
    public double peakRate() {
        double peak = 0.0;
        for (LoadStage stage : getStages()) {
            peak = Math.max(peak, stage.peakRate());
        }
        return peak;
    }
}
