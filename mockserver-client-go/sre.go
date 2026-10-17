package mockserver

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// This file adds the SRE control-plane operations: load scenarios (load
// generation / SLI production), service-scoped HTTP chaos, SLO verdicts,
// preemption (cordon/drain) simulation, and scheduled multi-stage chaos
// experiments. Each operation mirrors the MockServer OpenAPI contract
// (jekyll-www.mock-server.com/mockserver-openapi.yaml).
//
// Several of these features are off by default on the server and return an
// HTTP 403 (or 400 for SLO) until the corresponding configuration flag is
// enabled. Those statuses are surfaced as clear, dedicated errors so callers
// can distinguish "feature disabled" from a malformed request.

// FeatureDisabledError indicates the requested SRE feature is disabled on the
// server (e.g. loadGenerationEnabled=false or sloTrackingEnabled=false). It is
// returned when MockServer responds with the status code it uses to signal a
// disabled feature for that endpoint.
type FeatureDisabledError struct {
	Feature string
	Status  int
	Body    string
}

func (e *FeatureDisabledError) Error() string {
	return fmt.Sprintf("mockserver: %s is disabled on the server (status %d): %s", e.Feature, e.Status, e.Body)
}

// -----------------------------------------------------------------------------
// 1. Load scenario registry
//    Register/list/get/delete:
//      PUT    /mockserver/loadScenario          register (does not run)
//      GET    /mockserver/loadScenario          list all
//      GET    /mockserver/loadScenario/{name}   get one
//      DELETE /mockserver/loadScenario/{name}   remove one
//      DELETE /mockserver/loadScenario          clear all
//    Run control:
//      PUT    /mockserver/loadScenario/start     start one/many (requires loadGenerationEnabled)
//      PUT    /mockserver/loadScenario/stop      stop named / all
//    States: LOADED, PENDING, RUNNING, COMPLETED, STOPPED.
//    schemas LoadScenario, LoadProfile, LoadStep (OpenAPI).
// -----------------------------------------------------------------------------

// LoadStageType is the kind of a LoadStage. Wire values match schema
// LoadStageType (OpenAPI).
type LoadStageType string

const (
	// LoadStageVU is a closed-model stage: hold or ramp the number of concurrent
	// virtual users.
	LoadStageVU LoadStageType = "VU"
	// LoadStageRate is an open-model stage: hold or ramp a target arrival rate in
	// iterations per second.
	LoadStageRate LoadStageType = "RATE"
	// LoadStagePause drives no load for the stage duration.
	LoadStagePause LoadStageType = "PAUSE"
)

// RampCurve is the interpolation curve used to ramp a value across a stage.
// Wire values match schema RampCurve (OpenAPI). Only meaningful for ramp stages.
type RampCurve string

const (
	// RampLinear ramps with a constant slope.
	RampLinear RampCurve = "LINEAR"
	// RampExponential ramps with a steeper ease-in.
	RampExponential RampCurve = "EXPONENTIAL"
	// RampQuadratic ramps with an ease-in (slow then fast).
	RampQuadratic RampCurve = "QUADRATIC"
)

// LoadStage is one stage of a LoadProfile, run in sequence: it holds or ramps a
// setpoint for its DurationMillis. Wire keys match schema LoadStage (OpenAPI).
//
// A stage is one of three types:
//   - VU (closed model): hold Vus, or ramp from StartVus to EndVus along Curve.
//   - RATE (open model): hold Rate, or ramp from StartRate to EndRate
//     (iterations/second) along Curve, optionally capped at MaxVus virtual users.
//   - PAUSE: drive no load for DurationMillis.
//
// Pointer types are used for the optional numeric setpoints so that a meaningful
// zero (e.g. StartVus=0 at the bottom of a ramp, or Rate=0) is still emitted on
// the wire; omitempty drops them only when the pointer itself is nil.
type LoadStage struct {
	// Type is the stage kind: "VU", "RATE" or "PAUSE" (required).
	Type LoadStageType `json:"type,omitempty"`
	// DurationMillis is how long this stage runs in milliseconds (> 0, required).
	DurationMillis int64 `json:"durationMillis,omitempty"`
	// Curve is the ramp interpolation curve ("LINEAR", "EXPONENTIAL" or
	// "QUADRATIC"); only meaningful for ramp stages.
	Curve RampCurve `json:"curve,omitempty"`
	// Vus is the number of virtual users to hold for a VU hold stage.
	Vus *int `json:"vus,omitempty"`
	// StartVus is the virtual users at the start of a VU ramp.
	StartVus *int `json:"startVus,omitempty"`
	// EndVus is the virtual users at the end of a VU ramp.
	EndVus *int `json:"endVus,omitempty"`
	// Rate is the arrival rate (iterations/second) to hold for a RATE hold stage.
	Rate *float64 `json:"rate,omitempty"`
	// StartRate is the arrival rate at the start of a RATE ramp.
	StartRate *float64 `json:"startRate,omitempty"`
	// EndRate is the arrival rate at the end of a RATE ramp.
	EndRate *float64 `json:"endRate,omitempty"`
	// MaxVus optionally caps the auto-scaling VU pool that runs the started
	// iterations of a RATE stage (defaults to the global VU cap).
	MaxVus *int `json:"maxVus,omitempty"`
}

// LoadShapeType is a named declarative load shape that expands into ordinary
// stages. Wire values match schema LoadShapeType (OpenAPI).
type LoadShapeType string

const (
	// LoadShapeSpike ramps up, holds the peak, ramps back down, then optionally
	// holds a recovery baseline.
	LoadShapeSpike LoadShapeType = "SPIKE"
	// LoadShapeStairs is a flight of pure-hold steps, each one "step" higher.
	LoadShapeStairs LoadShapeType = "STAIRS"
	// LoadShapeRampHold ramps from 0 to a target then holds.
	LoadShapeRampHold LoadShapeType = "RAMP_HOLD"
)

// LoadShapeMetric is what a LoadShape drives. Wire values match schema
// LoadShapeMetric (OpenAPI).
type LoadShapeMetric string

const (
	// LoadShapeMetricVU drives concurrent virtual users (closed model).
	LoadShapeMetricVU LoadShapeMetric = "VU"
	// LoadShapeMetricRate drives an arrival rate in iterations/second (open model).
	LoadShapeMetricRate LoadShapeMetric = "RATE"
)

// LoadShape is a declarative named load shape that expands into ordinary stages;
// only the parameters its Type needs are read. Use a shape OR an explicit stages
// list, not both. Wire keys match schema LoadShape (OpenAPI line 4813).
//
// Numeric levels (Baseline, Peak, Start, Step, Target) use pointer types so a
// meaningful zero is still emitted; omitempty drops them only when nil.
type LoadShape struct {
	// Type is the shape kind: "SPIKE", "STAIRS" or "RAMP_HOLD" (required).
	Type LoadShapeType `json:"type,omitempty"`
	// Metric is what the shape drives: "VU" or "RATE".
	Metric LoadShapeMetric `json:"metric,omitempty"`
	// Curve is the ramp interpolation curve for ramp segments.
	Curve RampCurve `json:"curve,omitempty"`
	// Baseline is the SPIKE level held before and after the spike.
	Baseline *float64 `json:"baseline,omitempty"`
	// Peak is the SPIKE level held at the top of the spike.
	Peak *float64 `json:"peak,omitempty"`
	// RampUpMillis is the SPIKE baseline-to-peak ramp duration.
	RampUpMillis int64 `json:"rampUpMillis,omitempty"`
	// HoldMillis is the SPIKE peak-hold (and RAMP_HOLD target-hold) duration.
	HoldMillis int64 `json:"holdMillis,omitempty"`
	// RampDownMillis is the SPIKE peak-to-baseline ramp duration.
	RampDownMillis int64 `json:"rampDownMillis,omitempty"`
	// RecoveryHoldMillis is the optional SPIKE baseline hold after the down ramp.
	RecoveryHoldMillis int64 `json:"recoveryHoldMillis,omitempty"`
	// Start is the STAIRS level of the first step.
	Start *float64 `json:"start,omitempty"`
	// Step is the STAIRS rise of each step above the previous one.
	Step *float64 `json:"step,omitempty"`
	// Steps is the STAIRS number of steps.
	Steps int `json:"steps,omitempty"`
	// StepDurationMillis is the STAIRS hold duration per step.
	StepDurationMillis int64 `json:"stepDurationMillis,omitempty"`
	// Target is the RAMP_HOLD level ramped up to (from 0) and then held.
	Target *float64 `json:"target,omitempty"`
	// RampMillis is the RAMP_HOLD 0-to-target ramp duration.
	RampMillis int64 `json:"rampMillis,omitempty"`
}

// LoadProfile describes the load over time, as EITHER an ordered list of Stages
// OR a single named Shape (which expands into stages). Set one, not both; if
// both are set the explicit stages win. Wire keys match schema LoadProfile
// (OpenAPI).
type LoadProfile struct {
	// Stages are the ordered stages run one after another.
	Stages []LoadStage `json:"stages,omitempty"`
	// Shape is a declarative named shape that expands into stages.
	Shape *LoadShape `json:"shape,omitempty"`
}

// ConstantVusStage builds a VU hold stage holding vus virtual users for
// durationMillis.
func ConstantVusStage(vus int, durationMillis int64) LoadStage {
	return LoadStage{Type: LoadStageVU, Vus: &vus, DurationMillis: durationMillis}
}

// RampVusStage builds a VU ramp stage ramping from startVus to endVus over
// durationMillis along curve.
func RampVusStage(startVus, endVus int, durationMillis int64, curve RampCurve) LoadStage {
	return LoadStage{Type: LoadStageVU, StartVus: &startVus, EndVus: &endVus, DurationMillis: durationMillis, Curve: curve}
}

// ConstantRateStage builds a RATE hold stage holding rate iterations/second for
// durationMillis.
func ConstantRateStage(rate float64, durationMillis int64) LoadStage {
	return LoadStage{Type: LoadStageRate, Rate: &rate, DurationMillis: durationMillis}
}

// RampRateStage builds a RATE ramp stage ramping from startRate to endRate
// (iterations/second) over durationMillis along curve.
func RampRateStage(startRate, endRate float64, durationMillis int64, curve RampCurve) LoadStage {
	return LoadStage{Type: LoadStageRate, StartRate: &startRate, EndRate: &endRate, DurationMillis: durationMillis, Curve: curve}
}

// PauseStage builds a PAUSE stage driving no load for durationMillis.
func PauseStage(durationMillis int64) LoadStage {
	return LoadStage{Type: LoadStagePause, DurationMillis: durationMillis}
}

// LoadStep is a single templated request step in a load scenario.
// Wire keys match schema LoadStep (OpenAPI line 4402). The step Request reuses
// the client's existing HttpRequest type (the schema $refs HttpRequest).
type LoadStep struct {
	// Request is the templated request fired for this step (required).
	Request *HttpRequest `json:"request,omitempty"`
	// ThinkTime is an optional inter-step pause (a Delay).
	ThinkTime *Delay `json:"thinkTime,omitempty"`
	// Name is an optional human label for the step (used as the metric label).
	Name string `json:"name,omitempty"`
	// Labels are optional step-level annotation labels, merged over the
	// scenario labels (step keys win).
	Labels map[string]string `json:"labels,omitempty"`
	// Captures are optional cross-step capture rules applied to this step's
	// response, binding extracted values visible to SUBSEQUENT steps in the same
	// iteration. Meaningful only under SEQUENTIAL step selection.
	Captures []LoadCapture `json:"captures,omitempty"`
	// Checks are optional assertions on this step's response. A failing check
	// never fails the request; failures feed the CHECK_FAILURE_RATE threshold.
	Checks []LoadCheck `json:"checks,omitempty"`
	// Weight is the relative selection weight, used only when the scenario's
	// StepSelection is WEIGHTED (probability proportional to weight). Omitted
	// means 1.0 in WEIGHTED mode; must be > 0 when WEIGHTED. A pointer so a
	// supplied value is always emitted and omitted otherwise. Ignored under the
	// default SEQUENTIAL mode.
	Weight *float64 `json:"weight,omitempty"`
}

// LoadCaptureSource is where a LoadCapture extracts its value from. Wire values
// match the LoadCapture.source enum (OpenAPI).
type LoadCaptureSource = string

const (
	// LoadCaptureBodyJSONPath extracts via a JSONPath over the response body.
	LoadCaptureBodyJSONPath LoadCaptureSource = "BODY_JSONPATH"
	// LoadCaptureHeader extracts a response header value.
	LoadCaptureHeader LoadCaptureSource = "HEADER"
	// LoadCaptureBodyRegex extracts via a regex (capture group 1) over the body.
	LoadCaptureBodyRegex LoadCaptureSource = "BODY_REGEX"
)

// LoadCapture is a declarative cross-step capture / correlation rule: it
// extracts a value from a step's response and binds it to a variable name a
// later step in the same iteration can reference. Best-effort — on no match it
// falls back to DefaultValue (when set) or leaves the variable unset, never
// failing the run. Wire keys match schema LoadCapture (OpenAPI line 4913).
type LoadCapture struct {
	// Name is the variable name later steps reference (required).
	Name string `json:"name,omitempty"`
	// Source is where to extract from: BODY_JSONPATH, HEADER or BODY_REGEX
	// (required).
	Source LoadCaptureSource `json:"source,omitempty"`
	// Expression is the JSONPath, header name or regex driving the extraction
	// (required).
	Expression string `json:"expression,omitempty"`
	// DefaultValue is the optional fallback bound when extraction yields nothing.
	DefaultValue string `json:"defaultValue,omitempty"`
}

// LoadCheckSource is where a LoadCheck reads its observed value from. Wire
// values match the LoadCheck.source enum (OpenAPI).
type LoadCheckSource = string

const (
	// LoadCheckStatus reads the response status code.
	LoadCheckStatus LoadCheckSource = "STATUS"
	// LoadCheckHeader reads the first value of the response header HeaderName.
	LoadCheckHeader LoadCheckSource = "HEADER"
	// LoadCheckBodyJSONPath evaluates JSONPath over the response body.
	LoadCheckBodyJSONPath LoadCheckSource = "BODY_JSONPATH"
)

// LoadCheckComparator is how a LoadCheck compares the observed value with
// Value. Wire values match the LoadCheck.comparator enum (OpenAPI).
type LoadCheckComparator = string

const (
	// LoadCheckEquals passes when the observed value equals Value.
	LoadCheckEquals LoadCheckComparator = "EQUALS"
	// LoadCheckNotEquals passes when the observed value differs from Value.
	LoadCheckNotEquals LoadCheckComparator = "NOT_EQUALS"
	// LoadCheckContains passes when the observed value contains Value.
	LoadCheckContains LoadCheckComparator = "CONTAINS"
	// LoadCheckMatches passes when Value, a regex, matches the whole observed value.
	LoadCheckMatches LoadCheckComparator = "MATCHES"
	// LoadCheckGT passes when the observed number is greater than Value.
	LoadCheckGT LoadCheckComparator = "GT"
	// LoadCheckLT passes when the observed number is less than Value.
	LoadCheckLT LoadCheckComparator = "LT"
	// LoadCheckGTE passes when the observed number is at least Value.
	LoadCheckGTE LoadCheckComparator = "GTE"
	// LoadCheckLTE passes when the observed number is at most Value.
	LoadCheckLTE LoadCheckComparator = "LTE"
)

// LoadCheck is a per-step response assertion for a load scenario: it reads a
// value from the step's response and compares it with Value. A failing check
// is counted (metric, report, CHECK_FAILURE_RATE threshold) but never fails the
// request. Wire keys match schema LoadCheck (OpenAPI).
type LoadCheck struct {
	// Source is where to read the observed value from (required).
	Source LoadCheckSource `json:"source,omitempty"`
	// HeaderName is the response header to read (required when Source is HEADER).
	HeaderName string `json:"headerName,omitempty"`
	// JSONPath is evaluated over the response body (required when Source is
	// BODY_JSONPATH).
	JSONPath string `json:"jsonPath,omitempty"`
	// Comparator is how the observed value is compared with Value (required).
	Comparator LoadCheckComparator `json:"comparator,omitempty"`
	// Value is the expected value. An empty Value is omitted from the wire.
	Value string `json:"value,omitempty"`
}

// LoadThresholdMetric is the per-run metric a LoadThreshold evaluates. Wire
// values match the LoadThreshold.metric enum (OpenAPI).
type LoadThresholdMetric = string

const (
	// LoadThresholdLatencyP50 is the per-run 50th-percentile latency (ms).
	LoadThresholdLatencyP50 LoadThresholdMetric = "LATENCY_P50"
	// LoadThresholdLatencyP95 is the per-run 95th-percentile latency (ms).
	LoadThresholdLatencyP95 LoadThresholdMetric = "LATENCY_P95"
	// LoadThresholdLatencyP99 is the per-run 99th-percentile latency (ms).
	LoadThresholdLatencyP99 LoadThresholdMetric = "LATENCY_P99"
	// LoadThresholdLatencyP999 is the per-run 99.9th-percentile latency (ms).
	LoadThresholdLatencyP999 LoadThresholdMetric = "LATENCY_P999"
	// LoadThresholdErrorRate is the per-run error rate (0.0-1.0 fraction).
	LoadThresholdErrorRate LoadThresholdMetric = "ERROR_RATE"
	// LoadThresholdThroughputRPS is the per-run throughput (requests/second).
	LoadThresholdThroughputRPS LoadThresholdMetric = "THROUGHPUT_RPS"
	// LoadThresholdCheckFailureRate is the fraction (0.0-1.0) of evaluated
	// per-step LoadChecks that failed (0 when no checks ran).
	LoadThresholdCheckFailureRate LoadThresholdMetric = "CHECK_FAILURE_RATE"
)

// LoadThreshold is an in-run pass/fail threshold for a load scenario: a per-run
// metric compared against a value. All thresholds must hold for the run verdict
// to be PASS (logical AND); any breach makes the verdict FAIL. Wire keys match
// schema LoadThreshold (OpenAPI line 4935). All three fields are required.
type LoadThreshold struct {
	// Metric is the per-run metric to evaluate (required).
	Metric LoadThresholdMetric `json:"metric,omitempty"`
	// Comparator is how the observed value is compared to the threshold:
	// LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL
	// (required).
	Comparator string `json:"comparator,omitempty"`
	// Threshold is the threshold value (ms for latency, 0.0-1.0 for ERROR_RATE,
	// requests/second for THROUGHPUT_RPS) (required).
	Threshold float64 `json:"threshold,omitempty"`
}

// LoadPacingMode is how a LoadPacing target iteration cycle is derived. Wire
// values match the LoadPacing.mode enum (OpenAPI).
type LoadPacingMode = string

const (
	// LoadPacingNone disables pacing (immediate reschedule).
	LoadPacingNone LoadPacingMode = "NONE"
	// LoadPacingConstantPacing treats Value as the target cycle in milliseconds.
	LoadPacingConstantPacing LoadPacingMode = "CONSTANT_PACING"
	// LoadPacingConstantThroughput treats Value as target iterations/second per VU.
	LoadPacingConstantThroughput LoadPacingMode = "CONSTANT_THROUGHPUT"
)

// LoadPacing is adaptive iteration pacing (think-time) for a load scenario: a
// target per-virtual-user iteration cycle time. Applies only to the closed-model
// VU loop (open-model RATE iterations ignore it) and composes with per-step
// ThinkTime. Wire keys match schema LoadPacing (OpenAPI line 4955). Both fields
// are required.
type LoadPacing struct {
	// Mode is how the target cycle is derived from Value: NONE, CONSTANT_PACING
	// or CONSTANT_THROUGHPUT (required).
	Mode LoadPacingMode `json:"mode,omitempty"`
	// Value is the target cycle in milliseconds (CONSTANT_PACING) or target
	// iterations/second per VU (CONSTANT_THROUGHPUT); must be > 0 when Mode is
	// not NONE; ignored when NONE (required).
	Value float64 `json:"value,omitempty"`
}

// LoadFeederStrategy is how a LoadFeeder selects a row each iteration. Wire
// values match the LoadFeeder.strategy enum (OpenAPI).
type LoadFeederStrategy = string

const (
	// LoadFeederCircular cycles rows[globalIteration % size] and never exhausts.
	LoadFeederCircular LoadFeederStrategy = "CIRCULAR"
	// LoadFeederRandom picks a uniformly random row each iteration.
	LoadFeederRandom LoadFeederStrategy = "RANDOM"
	// LoadFeederSequential uses each row once in order and COMPLETES the run once
	// the dataset is exhausted.
	LoadFeederSequential LoadFeederStrategy = "SEQUENTIAL"
)

// LoadFeeder is parameterized test data (a data feeder) for a load scenario: an
// inline dataset from which one row is selected per iteration and exposed to the
// iteration's templated request fields. The dataset is always inline. Supply
// EITHER Rows (the primary form) OR Data + Format; when both are given Rows wins.
// Wire keys match schema LoadFeeder (OpenAPI line 4971).
type LoadFeeder struct {
	// Rows is the inline dataset: a list of column-name to value maps, one per
	// row (the primary mechanism). Must be non-empty when used.
	Rows []map[string]string `json:"rows,omitempty"`
	// Data is an optional raw inline dataset parsed server-side into rows per
	// Format. Ignored when Rows is set.
	Data string `json:"data,omitempty"`
	// Format is the format of Data: CSV or JSON (required when Data is set).
	Format string `json:"format,omitempty"`
	// Strategy is how a row is chosen each iteration: CIRCULAR (default), RANDOM
	// or SEQUENTIAL.
	Strategy LoadFeederStrategy `json:"strategy,omitempty"`
}

// LoadScenario is an API-driven load scenario: ordered templated steps driven
// at a target concurrency. Wire keys match schema LoadScenario (OpenAPI line
// 4427). Name, Profile and Steps are required by the server.
type LoadScenario struct {
	// Name is the human-readable scenario name (required).
	Name string `json:"name,omitempty"`
	// TemplateType is the template engine: "VELOCITY" (default) or "MUSTACHE".
	TemplateType string `json:"templateType,omitempty"`
	// MaxRequests is an optional hard cap on total requests dispatched.
	MaxRequests int `json:"maxRequests,omitempty"`
	// StartDelayMillis is an optional delay (milliseconds) applied before the
	// scenario starts driving load once started.
	StartDelayMillis int64 `json:"startDelayMillis,omitempty"`
	// Labels are optional scenario-level annotation labels.
	Labels map[string]string `json:"labels,omitempty"`
	// Thresholds are optional in-run pass/fail thresholds; the run carries a PASS
	// verdict iff all hold, FAIL otherwise. Empty/omitted means no verdict.
	Thresholds []LoadThreshold `json:"thresholds,omitempty"`
	// AbortOnFail, when true, aborts the run early on a FAIL verdict (terminal
	// STOPPED state, abortedByThreshold set); default false.
	AbortOnFail bool `json:"abortOnFail,omitempty"`
	// AbortGraceMillis suppresses AbortOnFail for the first N milliseconds of the
	// run so noisy startup samples cannot trigger a premature abort.
	AbortGraceMillis int64 `json:"abortGraceMillis,omitempty"`
	// Pacing is optional adaptive iteration pacing (closed-model VU loop only).
	Pacing *LoadPacing `json:"pacing,omitempty"`
	// Feeder is optional parameterized inline test data exposed per iteration.
	Feeder *LoadFeeder `json:"feeder,omitempty"`
	// Profile is the ramp profile (required).
	Profile *LoadProfile `json:"profile,omitempty"`
	// StepSelection is how each iteration selects which steps to run: SEQUENTIAL
	// (default, all steps in order) or WEIGHTED (one step per iteration by weight).
	StepSelection string `json:"stepSelection,omitempty"`
	// Steps is the ordered list of request steps fired each iteration (required).
	Steps []LoadStep `json:"steps,omitempty"`
}

// StepSelection wire values for LoadScenario.StepSelection (OpenAPI).
const (
	// StepSelectionSequential runs ALL steps in declared order each iteration.
	StepSelectionSequential = "SEQUENTIAL"
	// StepSelectionWeighted runs exactly ONE step per iteration chosen by weight.
	StepSelectionWeighted = "WEIGHTED"
)

// LoadScenarioState is the lifecycle state of a registered load scenario.
// One of LOADED, PENDING, RUNNING, COMPLETED, STOPPED.
type LoadScenarioState = string

const (
	// LoadScenarioStateLoaded is a registered scenario not yet started.
	LoadScenarioStateLoaded LoadScenarioState = "LOADED"
	// LoadScenarioStatePending is a started scenario waiting on its start delay.
	LoadScenarioStatePending LoadScenarioState = "PENDING"
	// LoadScenarioStateRunning is a scenario actively driving load.
	LoadScenarioStateRunning LoadScenarioState = "RUNNING"
	// LoadScenarioStateCompleted is a scenario that ran its profile to completion.
	LoadScenarioStateCompleted LoadScenarioState = "COMPLETED"
	// LoadScenarioStateStopped is a scenario stopped before completion.
	LoadScenarioStateStopped LoadScenarioState = "STOPPED"
)

// LoadScenarioRef is a {name,state} pair returned when registering or starting
// a scenario.
type LoadScenarioRef struct {
	Name  string `json:"name,omitempty"`
	State string `json:"state,omitempty"`
}

// LoadScenarioStatus is the live status of a running (or most recently run)
// scenario. The server emits these fields FLAT on the listing entry (siblings
// of name/state/definition), present only once the scenario has run, so it is
// embedded into LoadScenarioEntry and carries no name/state of its own.
type LoadScenarioStatus struct {
	ElapsedMillis int64   `json:"elapsedMillis,omitempty"`
	CurrentVus    int     `json:"currentVus,omitempty"`
	StageIndex    int     `json:"stageIndex,omitempty"`
	StageType     string  `json:"stageType,omitempty"`
	CurrentTarget float64 `json:"currentTarget,omitempty"`
	RequestsSent  int64   `json:"requestsSent,omitempty"`
	Succeeded     int64   `json:"succeeded,omitempty"`
	Failed        int64   `json:"failed,omitempty"`
	P50Millis     float64 `json:"p50Millis,omitempty"`
	P95Millis     float64 `json:"p95Millis,omitempty"`
	P99Millis     float64 `json:"p99Millis,omitempty"`
	P999Millis    float64 `json:"p999Millis,omitempty"`
	// DroppedIterations are iterations that were due but never dispatched because a
	// safety cap was hit (rate_limit + inflight_cap throttles for this run).
	DroppedIterations int64 `json:"droppedIterations,omitempty"`
	// Verdict is the in-run threshold verdict: "PASS" or "FAIL"; absent when the
	// scenario has no thresholds or none has been evaluated yet.
	Verdict string `json:"verdict,omitempty"`
	// AbortedByThreshold is true when this run was terminated early by an
	// abortOnFail threshold breach.
	AbortedByThreshold bool `json:"abortedByThreshold,omitempty"`
	// ThresholdResults are the per-threshold results behind the verdict (present
	// when thresholds were evaluated).
	ThresholdResults []LoadThresholdResult `json:"thresholdResults,omitempty"`
	RunID            string                `json:"runId,omitempty"`
	StartedAt        int64                 `json:"startedAt,omitempty"`
	EndedAt          int64                 `json:"endedAt,omitempty"`
	Labels           map[string]string     `json:"labels,omitempty"`
}

// LoadThresholdResult is a single per-threshold result behind a run verdict. Wire
// keys match the thresholdResults item of schema LoadScenarioListEntry (OpenAPI).
type LoadThresholdResult struct {
	Metric     LoadThresholdMetric `json:"metric,omitempty"`
	Comparator string              `json:"comparator,omitempty"`
	Threshold  float64             `json:"threshold,omitempty"`
	// Observed is the observed per-run value at evaluation time (latency ms,
	// error-rate fraction, or requests/second).
	Observed  float64 `json:"observed,omitempty"`
	Satisfied bool    `json:"satisfied,omitempty"`
}

// LoadScenarioEntry is one entry of the registry listing: the scenario name,
// its current lifecycle state, the registered definition, and — when the
// scenario has run — its live status fields, which the server emits FLAT on the
// entry. The embedded LoadScenarioStatus promotes those flat fields onto the
// entry; the entry's own Name/State (depth 0) win over the embedded type.
type LoadScenarioEntry struct {
	Name             string        `json:"name,omitempty"`
	State            string        `json:"state,omitempty"`
	StartDelayMillis int64         `json:"startDelayMillis,omitempty"`
	Definition       *LoadScenario `json:"definition,omitempty"`
	LoadScenarioStatus
}

// LoadScenarioList is the response of GET /mockserver/loadScenario.
type LoadScenarioList struct {
	Scenarios []LoadScenarioEntry `json:"scenarios,omitempty"`
}

// LoadScenarioStartResult is the response of PUT /mockserver/loadScenario/start.
type LoadScenarioStartResult struct {
	Started []LoadScenarioRef `json:"started,omitempty"`
	Status  string            `json:"status,omitempty"`
}

// LoadScenarioStopResult is the response of PUT /mockserver/loadScenario/stop.
type LoadScenarioStopResult struct {
	Stopped []LoadScenarioRef `json:"stopped,omitempty"`
	Status  string            `json:"status,omitempty"`
}

// loadScenarioNames is the wire body shared by start (names) and stop (names/all).
type loadScenarioNames struct {
	Names []string `json:"names,omitempty"`
	All   bool     `json:"all,omitempty"`
}

// LoadScenario registers (loads) a scenario in the registry without running it
// (PUT /mockserver/loadScenario). Each scenario is keyed by its unique Name.
// Registration is allowed even when load generation is disabled on the server.
// Returns the registered scenario reference ({name,state}).
func (c *Client) LoadScenario(scenario LoadScenario) (*LoadScenarioRef, error) {
	body, err := json.Marshal(scenario)
	if err != nil {
		return nil, fmt.Errorf("mockserver: marshal load scenario: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/loadScenario", body, nil)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: register load scenario failed (status %d): %s", statusCode, string(respBody))
	}
	var ref LoadScenarioRef
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &ref); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal load scenario ref: %w", err)
		}
	}
	return &ref, nil
}

// LoadScenarios lists all registered scenarios with their state, definition and
// optional live status (GET /mockserver/loadScenario).
func (c *Client) LoadScenarios() (*LoadScenarioList, error) {
	respBody, statusCode, err := c.doRequest("GET", "/mockserver/loadScenario", nil, nil)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: list load scenarios failed (status %d): %s", statusCode, string(respBody))
	}
	var list LoadScenarioList
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &list); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal load scenario list: %w", err)
		}
	}
	return &list, nil
}

// GetLoadScenario retrieves a single registered scenario by name
// (GET /mockserver/loadScenario/{name}). Returns an error if no scenario with
// that name is registered (HTTP 404).
func (c *Client) GetLoadScenario(name string) (*LoadScenarioEntry, error) {
	respBody, statusCode, err := c.doRequest("GET", "/mockserver/loadScenario/"+url.PathEscape(name), nil, nil)
	if err != nil {
		return nil, err
	}
	if statusCode == 404 {
		return nil, fmt.Errorf("mockserver: load scenario %q not found", name)
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: get load scenario failed (status %d): %s", statusCode, string(respBody))
	}
	var entry LoadScenarioEntry
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &entry); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal load scenario: %w", err)
		}
	}
	return &entry, nil
}

// DeleteLoadScenario removes a single registered scenario by name
// (DELETE /mockserver/loadScenario/{name}).
func (c *Client) DeleteLoadScenario(name string) error {
	respBody, statusCode, err := c.doRequest("DELETE", "/mockserver/loadScenario/"+url.PathEscape(name), nil, nil)
	if err != nil {
		return err
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: delete load scenario failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// ClearLoadScenarios removes all registered scenarios
// (DELETE /mockserver/loadScenario). Idempotent.
func (c *Client) ClearLoadScenarios() error {
	respBody, statusCode, err := c.doRequest("DELETE", "/mockserver/loadScenario", nil, nil)
	if err != nil {
		return err
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: clear load scenarios failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// StartLoadScenarios starts one or more registered scenarios by name
// (PUT /mockserver/loadScenario/start). Requires load generation to be enabled
// on the server — returns a FeatureDisabledError on HTTP 403
// (loadGenerationEnabled=false). Returns an error if a named scenario is not
// registered (HTTP 404). Honours each scenario's StartDelayMillis.
func (c *Client) StartLoadScenarios(names ...string) (*LoadScenarioStartResult, error) {
	body, err := json.Marshal(loadScenarioNames{Names: names})
	if err != nil {
		return nil, fmt.Errorf("mockserver: marshal start request: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/loadScenario/start", body, nil)
	if err != nil {
		return nil, err
	}
	if statusCode == 403 {
		return nil, &FeatureDisabledError{Feature: "load generation", Status: statusCode, Body: string(respBody)}
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: start load scenarios failed (status %d): %s", statusCode, string(respBody))
	}
	var result LoadScenarioStartResult
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &result); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal start result: %w", err)
		}
	}
	return &result, nil
}

// StopLoadScenarios stops the named running scenarios
// (PUT /mockserver/loadScenario/stop). With no names it stops all running
// scenarios (sends an empty body). Idempotent.
func (c *Client) StopLoadScenarios(names ...string) (*LoadScenarioStopResult, error) {
	var body []byte
	if len(names) > 0 {
		var err error
		body, err = json.Marshal(loadScenarioNames{Names: names})
		if err != nil {
			return nil, fmt.Errorf("mockserver: marshal stop request: %w", err)
		}
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/loadScenario/stop", body, nil)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: stop load scenarios failed (status %d): %s", statusCode, string(respBody))
	}
	var result LoadScenarioStopResult
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &result); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal stop result: %w", err)
		}
	}
	return &result, nil
}

// RunLoadScenario is a convenience that registers a scenario and immediately
// starts it. It is equivalent to LoadScenario followed by StartLoadScenarios
// for the scenario's name. Starting requires load generation to be enabled
// (FeatureDisabledError on HTTP 403).
func (c *Client) RunLoadScenario(scenario LoadScenario) (*LoadScenarioStartResult, error) {
	if _, err := c.LoadScenario(scenario); err != nil {
		return nil, err
	}
	return c.StartLoadScenarios(scenario.Name)
}

// GetLoadScenarioReport returns the end-of-run summary report for a scenario run
// (GET /mockserver/loadScenario/{name}/report). With format "" the JSON report
// is returned; with format "junit" a JUnit-XML <testsuite> document is returned
// (so a load run becomes a first-class CI test artifact). The raw response body
// is returned verbatim. Returns an error if the scenario never ran (HTTP 404).
func (c *Client) GetLoadScenarioReport(name string, format string) (string, error) {
	var params url.Values
	if format != "" {
		params = url.Values{"format": []string{format}}
	}

	respBody, statusCode, err := c.doRequest("GET", "/mockserver/loadScenario/"+url.PathEscape(name)+"/report", nil, params)
	if err != nil {
		return "", err
	}
	if statusCode == 404 {
		return "", fmt.Errorf("mockserver: load scenario %q has no run report", name)
	}
	if statusCode >= 400 {
		return "", fmt.Errorf("mockserver: get load scenario report failed (status %d): %s", statusCode, string(respBody))
	}
	return string(respBody), nil
}

// GenerateLoadScenarioFromOpenAPI seeds a load scenario from an OpenAPI spec and
// loads (registers) it in the LOADED state without running it
// (PUT /mockserver/loadScenario/generateFromOpenAPI). The body is the request
// document ({name, specUrlOrPayload, optional target, optional profile}); a map
// or a typed struct may be passed. The decoded response ({status, name, state,
// scenario}) is returned as raw JSON for the caller to inspect or edit.
func (c *Client) GenerateLoadScenarioFromOpenAPI(body interface{}) (interface{}, error) {
	return c.generateLoadScenario("/mockserver/loadScenario/generateFromOpenAPI", "generate load scenario from OpenAPI", body)
}

// GenerateLoadScenarioFromRecording seeds a load scenario from recorded proxy
// traffic and loads (registers) it in the LOADED state without running it
// (PUT /mockserver/loadScenario/generateFromRecording). The body is the request
// document ({name, optional mode, requestFilter, maxSteps, target, profile}); a
// map or a typed struct may be passed. The decoded response ({status, name,
// state, scenario}) is returned as raw JSON for the caller to inspect or edit.
func (c *Client) GenerateLoadScenarioFromRecording(body interface{}) (interface{}, error) {
	return c.generateLoadScenario("/mockserver/loadScenario/generateFromRecording", "generate load scenario from recording", body)
}

// generateLoadScenario is the shared transport for the two generate endpoints.
func (c *Client) generateLoadScenario(path, action string, body interface{}) (interface{}, error) {
	reqBody, err := json.Marshal(body)
	if err != nil {
		return nil, fmt.Errorf("mockserver: marshal %s request: %w", action, err)
	}

	respBody, statusCode, err := c.doRequest("PUT", path, reqBody, nil)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: %s failed (status %d): %s", action, statusCode, string(respBody))
	}
	var result interface{}
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &result); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal %s result: %w", action, err)
		}
	}
	return result, nil
}

// -----------------------------------------------------------------------------
// 2. Service chaos  (PUT /mockserver/serviceChaos)
//    OpenAPI: path ./mockserver/serviceChaos (lines 644-684),
//    schemas ServiceChaosRequest (4290), HttpChaosProfile (4312).
// -----------------------------------------------------------------------------

// ChaosLatency models the HttpChaosProfile.latency object (OpenAPI line 4323):
// an injected latency expressed as a value plus a time unit.
type ChaosLatency struct {
	Value    int    `json:"value,omitempty"`
	TimeUnit string `json:"timeUnit,omitempty"`
}

// ServiceChaosProfile is the HTTP chaos / fault-injection profile registered
// for a downstream host. Wire keys match schema HttpChaosProfile (OpenAPI line
// 4312).
type ServiceChaosProfile struct {
	// ErrorStatus is the HTTP status to return instead of the real response.
	ErrorStatus int `json:"errorStatus,omitempty"`
	// ErrorProbability is the probability (0.0-1.0) a request triggers the error.
	ErrorProbability float64 `json:"errorProbability,omitempty"`
	// Latency is the injected latency, if any.
	Latency *ChaosLatency `json:"latency,omitempty"`
	// DropConnectionProbability is the probability (0.0-1.0) that a request has its
	// TCP connection dropped without any response. The server rejects values outside
	// that range. Replaces the former ConnectionDrop *bool, which named a property
	// (connectionDrop) the server has never accepted and therefore always ignored.
	DropConnectionProbability *float64 `json:"dropConnectionProbability,omitempty"`
	// Seed is a fixed seed for deterministic probabilistic outcomes.
	Seed *int `json:"seed,omitempty"`
}

// serviceChaosRequest is the wire body for PUT /mockserver/serviceChaos. Wire
// keys match schema ServiceChaosRequest (OpenAPI line 4290).
type serviceChaosRequest struct {
	Host      string               `json:"host,omitempty"`
	Chaos     *ServiceChaosProfile `json:"chaos,omitempty"`
	Remove    bool                 `json:"remove,omitempty"`
	Clear     bool                 `json:"clear,omitempty"`
	TTLMillis int64                `json:"ttlMillis,omitempty"`
}

// SetServiceChaos registers an HTTP chaos profile for a downstream host
// (PUT /mockserver/serviceChaos). ttlMillis is an optional auto-revert window;
// pass 0 to omit it.
func (c *Client) SetServiceChaos(host string, profile ServiceChaosProfile, ttlMillis int64) error {
	reqBody := serviceChaosRequest{
		Host:      host,
		Chaos:     &profile,
		TTLMillis: ttlMillis,
	}
	body, err := json.Marshal(reqBody)
	if err != nil {
		return fmt.Errorf("mockserver: marshal service chaos: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/serviceChaos", body, nil)
	if err != nil {
		return err
	}
	if statusCode == 403 {
		return fmt.Errorf("mockserver: set service chaos forbidden by control-plane authorization (status 403): %s", string(respBody))
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: set service chaos failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// RemoveServiceChaos removes the chaos profile registered for a single host
// (PUT /mockserver/serviceChaos with remove:true).
func (c *Client) RemoveServiceChaos(host string) error {
	reqBody := serviceChaosRequest{Host: host, Remove: true}
	body, err := json.Marshal(reqBody)
	if err != nil {
		return fmt.Errorf("mockserver: marshal service chaos removal: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/serviceChaos", body, nil)
	if err != nil {
		return err
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: remove service chaos failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// ClearServiceChaos clears all service-scoped chaos profiles
// (PUT /mockserver/serviceChaos with clear:true).
func (c *Client) ClearServiceChaos() error {
	reqBody := serviceChaosRequest{Clear: true}
	body, err := json.Marshal(reqBody)
	if err != nil {
		return fmt.Errorf("mockserver: marshal service chaos clear: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/serviceChaos", body, nil)
	if err != nil {
		return err
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: clear service chaos failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// -----------------------------------------------------------------------------
// 3. SLO verdict  (PUT /mockserver/verifySLO)
//    OpenAPI: path ./mockserver/verifySLO (lines 2836-2891),
//    schemas SloCriteria (4484), SloObjective (4459), SloVerdict (4548),
//    SloObjectiveResult (4527).
// -----------------------------------------------------------------------------

// SloObjective is a single service-level objective over the recorded SLI
// samples. Wire keys match schema SloObjective (OpenAPI line 4459).
type SloObjective struct {
	// Sli is the indicator: LATENCY_P50, LATENCY_P95, LATENCY_P99 or ERROR_RATE.
	Sli string `json:"sli,omitempty"`
	// Comparator: LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL.
	Comparator string `json:"comparator,omitempty"`
	// Threshold is the objective threshold (ms for latency, 0.0-1.0 for ERROR_RATE).
	Threshold float64 `json:"threshold,omitempty"`
	// Scope: FORWARD (default) or INBOUND.
	Scope string `json:"scope,omitempty"`
}

// SloWindow is the time window an SloCriteria is evaluated over. Wire keys
// match the inline window object of schema SloCriteria (OpenAPI line 4494).
type SloWindow struct {
	// Type: LOOKBACK (default) or EXPLICIT.
	Type string `json:"type,omitempty"`
	// LookbackMillis is the LOOKBACK window length ending now.
	LookbackMillis int64 `json:"lookbackMillis,omitempty"`
	// FromEpochMillis is the EXPLICIT window start in epoch milliseconds.
	FromEpochMillis int64 `json:"fromEpochMillis,omitempty"`
	// ToEpochMillis is the EXPLICIT window end in epoch milliseconds.
	ToEpochMillis int64 `json:"toEpochMillis,omitempty"`
}

// SloCriteria is a named set of service-level objectives over a time window.
// Wire keys match schema SloCriteria (OpenAPI line 4484). Objectives is required.
type SloCriteria struct {
	Name               string         `json:"name,omitempty"`
	Window             *SloWindow     `json:"window,omitempty"`
	MinimumSampleCount int            `json:"minimumSampleCount,omitempty"`
	UpstreamHosts      []string       `json:"upstreamHosts,omitempty"`
	Objectives         []SloObjective `json:"objectives,omitempty"`
}

// SloObjectiveResult is the evaluated result of a single objective. Wire keys
// match schema SloObjectiveResult (OpenAPI line 4527).
type SloObjectiveResult struct {
	Sli           string   `json:"sli,omitempty"`
	Comparator    string   `json:"comparator,omitempty"`
	Threshold     float64  `json:"threshold,omitempty"`
	ObservedValue *float64 `json:"observedValue,omitempty"`
	Result        string   `json:"result,omitempty"`
	Detail        string   `json:"detail,omitempty"`
}

// SloVerdict is the overall verdict of an SLO evaluation (the AND of all
// objective results). Wire keys match schema SloVerdict (OpenAPI line 4548).
// Result is one of PASS, FAIL, INCONCLUSIVE.
type SloVerdict struct {
	Name                  string               `json:"name,omitempty"`
	Result                string               `json:"result,omitempty"`
	WindowFromEpochMillis int64                `json:"windowFromEpochMillis,omitempty"`
	WindowToEpochMillis   int64                `json:"windowToEpochMillis,omitempty"`
	SampleCount           int                  `json:"sampleCount,omitempty"`
	ObjectiveResults      []SloObjectiveResult `json:"objectiveResults,omitempty"`
}

// VerifySLO evaluates a set of service-level objectives over a window
// (PUT /mockserver/verifySLO). The server encodes the verdict in the HTTP
// status: 200 for PASS or INCONCLUSIVE, 406 for FAIL, 403 when SLO tracking is
// disabled (sloTrackingEnabled=false), and 400 for malformed criteria.
//
// The decoded SloVerdict is returned for both PASS/INCONCLUSIVE (200) and FAIL
// (406) so callers can inspect the per-objective results; the returned error is
// non-nil only on FAIL or a real error. A 403 is surfaced as a
// FeatureDisabledError, a 400 as a plain error naming the criteria — these used
// to be the same status, so a malformed criteria was misreported as a disabled
// feature.
func (c *Client) VerifySLO(criteria SloCriteria) (SloVerdict, error) {
	var verdict SloVerdict

	body, err := json.Marshal(criteria)
	if err != nil {
		return verdict, fmt.Errorf("mockserver: marshal slo criteria: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/verifySLO", body, nil)
	if err != nil {
		return verdict, err
	}

	if statusCode == 403 {
		return verdict, &FeatureDisabledError{Feature: "SLO tracking", Status: statusCode, Body: string(respBody)}
	}
	if statusCode == 400 {
		return verdict, fmt.Errorf("mockserver: invalid SLO criteria: %s", string(respBody))
	}
	if statusCode != 200 && statusCode != 406 {
		return verdict, fmt.Errorf("mockserver: verify SLO failed (status %d): %s", statusCode, string(respBody))
	}

	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &verdict); err != nil {
			return verdict, fmt.Errorf("mockserver: unmarshal slo verdict: %w", err)
		}
	}

	if statusCode == 406 {
		return verdict, &VerificationError{Message: fmt.Sprintf("SLO verdict FAIL: %s", string(respBody))}
	}
	return verdict, nil
}

// -----------------------------------------------------------------------------
// 4. Preemption  (PUT/GET/DELETE /mockserver/preemption)
//    OpenAPI: path ./mockserver/preemption (lines 2893-2964),
//    schemas PreemptionRequest (4570), PreemptionStatus (4592).
// -----------------------------------------------------------------------------

// PreemptionRequest holds preemption simulation parameters (all optional).
// Wire keys match schema PreemptionRequest (OpenAPI line 4570).
type PreemptionRequest struct {
	// Mode: reject503, goaway or both (default both).
	Mode string `json:"mode,omitempty"`
	// DrainMillis is how long in-flight requests are allowed to drain.
	DrainMillis int64 `json:"drainMillis,omitempty"`
	// TTLMillis auto-uncordons after this many milliseconds (0 = no auto-uncordon).
	TTLMillis int64 `json:"ttlMillis,omitempty"`
	// LastStreamID is the HTTP/2 GOAWAY last_stream_id to advertise (-1 = server chooses).
	LastStreamID *int64 `json:"lastStreamId,omitempty"`
}

// PreemptionStatus is the current cordon/drain status of the server. Wire keys
// match schema PreemptionStatus (OpenAPI line 4592).
type PreemptionStatus struct {
	// State: inactive, draining or drained.
	State string `json:"state,omitempty"`
	// InFlight is the number of requests currently in flight.
	InFlight int `json:"inFlight,omitempty"`
	// DrainRemainingMillis is the milliseconds left in the drain window.
	DrainRemainingMillis int64 `json:"drainRemainingMillis,omitempty"`
	// Mode is the active signalling mode (omitted when inactive).
	Mode string `json:"mode,omitempty"`
}

// SetPreemption cordons and drains the server, simulating a preemption
// (PUT /mockserver/preemption). The returned status reflects the started (or
// replaced) simulation.
func (c *Client) SetPreemption(req PreemptionRequest) error {
	body, err := json.Marshal(req)
	if err != nil {
		return fmt.Errorf("mockserver: marshal preemption request: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/preemption", body, nil)
	if err != nil {
		return err
	}
	if statusCode == 403 {
		return &FeatureDisabledError{Feature: "preemption simulation", Status: statusCode, Body: string(respBody)}
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: set preemption failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// PreemptionStatus retrieves the current cordon/drain status
// (GET /mockserver/preemption).
func (c *Client) PreemptionStatus() (*PreemptionStatus, error) {
	respBody, statusCode, err := c.doRequest("GET", "/mockserver/preemption", nil, nil)
	if err != nil {
		return nil, err
	}
	if statusCode == 403 {
		return nil, &FeatureDisabledError{Feature: "preemption simulation", Status: statusCode, Body: string(respBody)}
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: preemption status failed (status %d): %s", statusCode, string(respBody))
	}
	var status PreemptionStatus
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &status); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal preemption status: %w", err)
		}
	}
	return &status, nil
}

// ClearPreemption uncordons the server, clearing any active preemption
// simulation (DELETE /mockserver/preemption). Idempotent.
func (c *Client) ClearPreemption() error {
	respBody, statusCode, err := c.doRequest("DELETE", "/mockserver/preemption", nil, nil)
	if err != nil {
		return err
	}
	if statusCode == 403 {
		return &FeatureDisabledError{Feature: "preemption simulation", Status: statusCode, Body: string(respBody)}
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: clear preemption failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// -----------------------------------------------------------------------------
// 5. Chaos experiment  (PUT /mockserver/chaosExperiment)
//    OpenAPI: path ./mockserver/chaosExperiment (lines 2130-2208),
//    schema ChaosExperiment (4611), HttpChaosProfile (4312).
// -----------------------------------------------------------------------------

// ChaosExperimentStage is a single stage of a chaos experiment: a set of
// host -> chaos profile mappings applied for a fixed duration. Wire keys match
// the inline stage object of schema ChaosExperiment (OpenAPI line 4624).
type ChaosExperimentStage struct {
	// DurationMillis is how long the stage runs before advancing.
	DurationMillis int64 `json:"durationMillis,omitempty"`
	// Profiles maps a downstream host to the chaos profile applied during the stage.
	Profiles map[string]ServiceChaosProfile `json:"profiles,omitempty"`
}

// ChaosExperiment is a scheduled multi-stage chaos experiment definition. Wire
// keys match schema ChaosExperiment (OpenAPI line 4611). Stages is required.
type ChaosExperiment struct {
	// Name is the human-readable experiment name.
	Name string `json:"name,omitempty"`
	// Loop, when true, loops back to stage 0 after the last stage completes.
	Loop bool `json:"loop,omitempty"`
	// Stages is the ordered sequence of stages (required).
	Stages []ChaosExperimentStage `json:"stages,omitempty"`
}

// StartChaosExperiment starts a scheduled multi-stage chaos experiment
// (PUT /mockserver/chaosExperiment). Only one experiment may be active at a
// time; starting a new one stops the previous one. The server rejects an
// invalid experiment with 400 and answers 403 only when control-plane
// authorization denies the request; no server setting disables chaos.
func (c *Client) StartChaosExperiment(experiment ChaosExperiment) error {
	body, err := json.Marshal(experiment)
	if err != nil {
		return fmt.Errorf("mockserver: marshal chaos experiment: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/chaosExperiment", body, nil)
	if err != nil {
		return err
	}
	if statusCode == 403 {
		return fmt.Errorf("mockserver: start chaos experiment forbidden by control-plane authorization (status 403): %s", string(respBody))
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: start chaos experiment failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}
