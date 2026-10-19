package org.mockserver.mock.action.http;

import org.mockserver.collections.MostRecentRegistration;
import org.mockserver.configuration.Configuration;
import org.mockserver.load.IterationContext;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.load.LoadCapture;
import org.mockserver.load.LoadCheck;
import org.mockserver.load.LoadResponseExtractor;
import org.mockserver.load.LoadFeeder;
import org.mockserver.load.LoadPacing;
import org.mockserver.load.LoadProfile;
import org.mockserver.load.LoadScenario;
import org.mockserver.load.LoadStage;
import org.mockserver.load.LoadScenarioState;
import org.mockserver.load.LoadShape;
import org.mockserver.load.LoadShapes;
import org.mockserver.load.LoadStageType;
import org.mockserver.load.LoadStep;
import org.mockserver.load.LoadThreshold;
import org.mockserver.metrics.MetricLabels;
import org.mockserver.metrics.Metrics;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.proxyconfiguration.ForwardTargetBlockedException;
import org.mockserver.slo.Scope;
import org.mockserver.slo.SloSampleStore;
import org.mockserver.telemetry.W3CTraceContext;
import org.mockserver.templates.engine.TemplateEngine;
import org.mockserver.templates.engine.mustache.MustacheTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;
import org.mockserver.time.TimeService;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Orchestrates an in-process API-driven load scenario. A scenario drives an ordered list of templated
 * request {@link LoadStep}s through a sequence of {@link LoadStage}s described by a {@link LoadProfile},
 * producing latency/error samples for the SLO verdict feature.
 *
 * <p>Copies the {@link ChaosExperimentOrchestrator} shape: a process-wide singleton with a
 * single-thread daemon {@link ScheduledExecutorService} ("load-scenario-scheduler"). The
 * scheduler thread does <b>no I/O</b> — it advances stages and computes setpoints on a fixed control
 * tick and hands each request to an injected {@code sender} that returns a {@link CompletableFuture}
 * immediately. Step pacing is scheduled (never {@link Delay#applyDelay() Thread.sleep}-ed), so a slow
 * target never blocks a worker thread.
 *
 * <p><b>Two load models, run in sequence:</b>
 * <ul>
 *   <li>{@link LoadStageType#VU} — closed model. The tick maintains a pool of looping virtual users
 *       sized to {@code targetVusAt(elapsedInStage)} (hold or ramp); each VU loops the steps back-to-back.</li>
 *   <li>{@link LoadStageType#RATE} — open model (arrival rate). The tick computes the target rate
 *       {@code r(t)} in iterations/second and starts new <em>one-shot</em> iterations so the cumulative
 *       number started tracks the integral of {@code r(t)} (deficit accounting), auto-scaling a VU pool
 *       up to the stage {@code maxVus} (or the global cap). When the cap blocks the rate, the shortfall
 *       is counted as a {@code rate_limit} throttle.</li>
 *   <li>{@link LoadStageType#PAUSE} — drives no load; VUs drain for the duration.</li>
 * </ul>
 *
 * <p><b>Decoupling:</b> core must not depend on the Netty HTTP client, so the actual request sender is
 * injected via {@link #registerSender(Function)} (mirrors {@code HttpState.setReplayHandler}). The Netty
 * runtime wires it from {@code HttpActionHandler.getHttpClient()}; unit tests pass a deterministic
 * synchronous fake sender to {@link #start(LoadScenario, Function)}.
 *
 * <p><b>Self-load guard:</b> off by default ({@code loadGenerationEnabled=false} → the PUT endpoint
 * returns 403). Even when enabled, {@link #validate} enforces hard caps on VUs, rate, stages, duration
 * and step count, and dispatch is bounded live by an in-flight {@link Semaphore} and an RPS token
 * bucket, so a forgotten scenario cannot self-DoS the server.
 *
 * <p>Time is read via a pluggable {@link LongSupplier} clock (defaults to
 * {@link TimeService#currentTimeMillis()}) so tests can drive progression deterministically via
 * {@link #tickNow()} / {@link #advanceNow(long)} without wall-clock sleeps.
 */
public class LoadScenarioOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(LoadScenarioOrchestrator.class);

    /** Error kind of a request forwardProxyBlockPrivateNetworks refused, which was not sent. */
    static final String BLOCKED = "blocked";

    /** Control tick interval in milliseconds (stage advance + setpoint recomputation). */
    static final long CONTROL_TICK_MILLIS = 100;

    private static final LoadScenarioOrchestrator INSTANCE = new LoadScenarioOrchestrator(
        TimeService::currentTimeMillis,
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "load-scenario-scheduler");
            t.setDaemon(true);
            return t;
        })
    );

    private final LongSupplier clock;
    private final ScheduledExecutorService scheduler;
    /**
     * Active runs keyed by scenario name. A run is "active" while it is PENDING (waiting out its
     * start delay) or RUNNING. The single scheduler thread ticks every active run each control tick.
     * Re-triggering a name replaces that name's run (and evicts the prior run's metric series).
     */
    private final Map<String, RunningScenario> runs = new ConcurrentHashMap<>();
    /**
     * The terminal (stopped/completed) status of the most recent run for each scenario name, retained
     * for status queries after the run leaves {@link #runs}. Cleared on {@link #reset()} and when the
     * scenario is removed from the registry.
     */
    private final Map<String, LoadScenarioStatus> terminalStatuses = new ConcurrentHashMap<>();
    /** Name of the most-recently-triggered run, backing the single-run convenience accessors. */
    private volatile String lastRunName;
    /**
     * Most recently started run, retained for test assertions on slot accounting after termination. Unset when the
     * server whose sender the ended run used stops, so that server is not kept.
     */
    private final AtomicReference<RunningScenario> lastRun = new AtomicReference<>();
    /**
     * The single shared scheduled control tick that drives ALL active runs. Created lazily on the
     * first trigger and cancelled when the last run terminates so an idle orchestrator schedules no
     * work (the single-run model previously scheduled one tick per run).
     */
    private volatile ScheduledFuture<?> sharedTick;
    /** Sender installed by the runtime; null in unit tests until start() supplies one. */
    private final MostRecentRegistration<Function<HttpRequest, CompletableFuture<HttpResponse>>> installedSender = new MostRecentRegistration<>();
    /** Configuration used to read caps and to build the template engines for rendering. */
    private volatile Configuration configuration = Configuration.configuration();
    /** Logs a request forwardProxyBlockPrivateNetworks refused, at the configured log level. */
    private volatile MockServerLogger mockServerLogger = new MockServerLogger(LoadScenarioOrchestrator.class);

    LoadScenarioOrchestrator(LongSupplier clock, ScheduledExecutorService scheduler) {
        this.clock = clock;
        this.scheduler = scheduler;
    }

    public static LoadScenarioOrchestrator getInstance() {
        return INSTANCE;
    }

    /**
     * Install the request sender that re-issues a {@link HttpRequest} to its target and returns
     * the upstream response, without registering it for {@link #unregisterSender} to fall back to.
     * The runtime uses {@link #registerSender}, wiring the existing HTTP client so the core never
     * depends on it directly (mirrors {@code HttpState.setReplayHandler}).
     */
    public void setSender(Function<HttpRequest, CompletableFuture<HttpResponse>> sender) {
        installedSender.set(sender);
    }

    /**
     * Use {@code sender}, a running server's, from now on, and fall back to it when the server whose sender is
     * in use stops, while this server is still running. Called by the runtime.
     */
    public void registerSender(Function<HttpRequest, CompletableFuture<HttpResponse>> sender) {
        installedSender.register(sender);
    }

    /**
     * Forget {@code sender}, a stopping server's. If it is the one in use, the sender of the server still running
     * that registered most recently is used instead; a sender in use that is not {@code sender} is kept. The most
     * recent run is no longer retained if it has ended and used {@code sender}.
     */
    public void unregisterSender(Function<HttpRequest, CompletableFuture<HttpResponse>> sender) {
        installedSender.unregister(sender);
        RunningScenario run = lastRun.get();
        if (run != null && run.sender == sender && runs.get(run.scenario.getName()) != run) {
            lastRun.compareAndSet(run, null);
        }
    }

    /** Install the configuration used for caps and template engines. Called by the runtime. */
    public void setConfiguration(Configuration configuration) {
        if (configuration != null) {
            this.configuration = configuration;
            this.mockServerLogger = new MockServerLogger(configuration, LoadScenarioOrchestrator.class);
        }
    }

    /** Test hook: capture what is logged. */
    void setMockServerLogger(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    private void logBlocked(RunningScenario run, ForwardTargetBlockedException blocked) {
        MockServerLogger logger = this.mockServerLogger;
        if (logger.isEnabledForInstance(Level.WARN)) {
            logger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.WARN)
                    .setMessageFormat("load scenario:{}request blocked by SSRF policy:{}")
                    .setArguments(run.scenario.getName(), blocked.getMessage())
            );
        }
    }

    /**
     * Trigger a scenario to start (run it). Returns a validation error message if the definition is
     * invalid, the caps are exceeded, or the concurrent-scenario cap would be exceeded; or
     * {@code null} on success. Each trigger gets a fresh {@code run_id}. Re-triggering a name that is
     * already active replaces that run (and evicts its prior metric series). A scenario with
     * {@code startDelayMillis > 0} enters {@code PENDING} and begins after the delay; otherwise it
     * begins {@code RUNNING} immediately. When {@code sender} is null the installed runtime sender is
     * used; unit tests pass a deterministic synchronous sender here.
     */
    public String start(LoadScenario scenario, Function<HttpRequest, CompletableFuture<HttpResponse>> sender) {
        String error = validate(scenario);
        if (error != null) {
            return error;
        }
        Function<HttpRequest, CompletableFuture<HttpResponse>> effectiveSender = sender != null ? sender : installedSender.get();
        if (effectiveSender == null) {
            return "no load sender installed (server runtime not wired)";
        }
        // Concurrency cap: count active (PENDING+RUNNING) runs. Re-triggering an already-active name
        // replaces it (no net increase), so it is exempt from the cap check.
        int maxConcurrent = Math.max(1, configuration.loadGenerationMaxConcurrentScenarios());
        if (!runs.containsKey(scenario.getName()) && runs.size() >= maxConcurrent) {
            return "starting '" + scenario.getName() + "' would exceed the maximum of " + maxConcurrent
                + " concurrently active load scenarios";
        }

        long now = clock.getAsLong();
        RunningScenario running = new RunningScenario(scenario, effectiveSender, now);
        lastRun.set(running);
        lastRunName = scenario.getName();

        // Register the run and clear the name's retained terminal status in one atomic step, so the
        // status a terminate publishes for THIS run (it can only follow the registration) is never the
        // one cleared here.
        String name = scenario.getName();
        AtomicReference<RunningScenario> replaced = new AtomicReference<>();
        AtomicReference<LoadScenarioStatus> retained = new AtomicReference<>();
        runs.compute(name, (key, registered) -> {
            replaced.set(registered);
            retained.set(terminalStatuses.remove(name));
            return running;
        });
        // Stop the run this one replaced, and evict the series of whichever run last held the name, so
        // series accumulation stays bounded to one run per (scenario,run_id).
        RunningScenario previous = replaced.get();
        if (previous != null) {
            previous.stopped.set(true);
            Metrics.evictLoadRun(previous.runId);
        }
        LoadScenarioStatus prior = retained.get();
        if (prior != null && prior.runId != null && !prior.runId.equals(running.runId)) {
            Metrics.evictLoadRun(prior.runId);
        }

        installGaugeReaders();
        ensureSharedTick();

        if (scenario.getStartDelayMillis() > 0) {
            running.state = LoadScenarioState.PENDING;
            LOG.info("load scenario '{}' pending start delay of {}ms ({} step(s), {} stage(s))",
                scenario.getName(), scenario.getStartDelayMillis(), scenario.getSteps().size(),
                scenario.getProfile().getStages().size());
        } else {
            running.beginRunning(now);
            LOG.info("load scenario '{}' started with {} step(s), {} stage(s)",
                scenario.getName(), scenario.getSteps().size(), scenario.getProfile().getStages().size());
        }

        // Run an immediate first tick so the first stage starts (or the pending-delay is evaluated)
        // without waiting a full control interval.
        tick(running);
        return null;
    }

    /**
     * Stop the most-recently-triggered run (single-run convenience). Idempotent. For multi-run use
     * {@link #stop(String)} or {@link #stopAll()}.
     */
    public void stop() {
        if (lastRunName != null) {
            stop(lastRunName);
        }
    }

    /** Stop a specific scenario's active run by name. Idempotent; no-op if not active. */
    public void stop(String name) {
        // Peek (don't remove) — terminate() performs the gap-free de-registration itself, so a
        // status poll never sees the run absent from both the active and terminal maps.
        RunningScenario run = runs.get(name);
        if (run != null) {
            terminate(run, LoadScenarioState.STOPPED);
        }
    }

    /** Stop every active run. */
    public void stopAll() {
        for (String name : new ArrayList<>(runs.keySet())) {
            stop(name);
        }
    }

    /** Reset: stop all active runs and clear all terminal status. Called on server reset. */
    public void reset() {
        stopAll();
        terminalStatuses.clear();
        lastRunName = null;
        lastRun.set(null);
    }

    /**
     * Status of the most-recently-triggered run (active or its retained terminal status), or null if
     * none has ever run. Single-run convenience; see {@link #getStatuses()} for all runs.
     */
    public LoadScenarioStatus getStatus() {
        if (lastRunName == null) {
            return null;
        }
        return statusFor(lastRunName);
    }

    /** Status for a specific scenario name (active run snapshot or retained terminal status), or null. */
    public LoadScenarioStatus statusFor(String name) {
        RunningScenario run = runs.get(name);
        if (run != null) {
            return run.snapshot(run.state, clock.getAsLong());
        }
        return terminalStatuses.get(name);
    }

    /** True if the named scenario currently has an active (PENDING or RUNNING) run. */
    public boolean isActive(String name) {
        return runs.containsKey(name);
    }

    /**
     * Snapshots of every currently-active run (PENDING/RUNNING), keyed by scenario name. The registry
     * (not the orchestrator) is the source of truth for the full scenario list; this only reports the
     * live ones. Includes the retained terminal status for names with no active run is the caller's
     * job via {@link #statusFor(String)}.
     */
    public Map<String, LoadScenarioStatus> getStatuses() {
        Map<String, LoadScenarioStatus> result = new LinkedHashMap<>();
        long now = clock.getAsLong();
        runs.forEach((name, run) -> result.put(name, run.snapshot(run.state, now)));
        return result;
    }

    // -- Internal --

    /**
     * Install the active-VU and in-flight gauge readers. Each reader returns one entry per active run
     * (PENDING runs report zero), so the gauges emit one series per (scenario, run_id). Installed once
     * the first run starts; cleared when the last run terminates.
     */
    private void installGaugeReaders() {
        Metrics.setLoadGaugeReaders(
            () -> {
                if (runs.isEmpty()) {
                    return Collections.emptyMap();
                }
                Map<Metrics.LoadGaugeKey, Integer> out = new LinkedHashMap<>();
                runs.forEach((name, run) -> out.put(run.gaugeKey, Math.max(0, run.activeVUs.get())));
                return out;
            },
            () -> {
                if (runs.isEmpty()) {
                    return Collections.emptyMap();
                }
                Map<Metrics.LoadGaugeKey, Integer> out = new LinkedHashMap<>();
                runs.forEach((name, run) -> out.put(run.gaugeKey, Math.max(0, run.inFlightCount.get())));
                return out;
            });
    }

    /** Lazily start the single shared control tick that drives all active runs. */
    private synchronized void ensureSharedTick() {
        if (sharedTick == null) {
            sharedTick = scheduler.scheduleAtFixedRate(
                this::tickAll, CONTROL_TICK_MILLIS, CONTROL_TICK_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    /** Cancel the shared tick once no runs remain active. */
    private synchronized void stopSharedTickIfIdle() {
        if (runs.isEmpty() && sharedTick != null) {
            sharedTick.cancel(false);
            sharedTick = null;
        }
    }

    /** The shared control tick: advance every active run. Runs on the single scheduler thread. */
    private void tickAll() {
        for (RunningScenario run : new ArrayList<>(runs.values())) {
            tick(run);
        }
    }

    /**
     * True while {@code run} is still the registry's active run for its scenario name. A run that was
     * stopped, completed, or replaced by a re-trigger is no longer current, so late-draining callbacks
     * and ticks must not act on it (this is the multi-run replacement for the old single-run guard).
     */
    private boolean isCurrent(RunningScenario run) {
        return runs.get(run.scenario.getName()) == run;
    }

    /**
     * Transition a run to a terminal state with no observable status gap. The terminal status is
     * published and the run de-registered from {@link #runs} in one atomic step per scenario name, and
     * only by the call that finds the run still registered: a concurrent {@link #statusFor(String)}
     * (which reads {@code runs} first, then {@code terminalStatuses}) never sees the name absent from
     * both maps, and a call that lost (to another terminate of the same run, or to a re-trigger that
     * replaced it) writes nothing. Returns {@code true} iff this call de-registered the run. The run's
     * durable metric series are RETAINED (scrapeable) until the scenario is next triggered or removed
     * from the registry.
     */
    private boolean terminate(RunningScenario run, LoadScenarioState terminalState) {
        run.stopped.set(true);
        String name = run.scenario.getName();
        LoadScenarioStatus status = run.snapshot(terminalState, clock.getAsLong());
        AtomicBoolean deregistered = new AtomicBoolean();
        // The status is published inside the remapping function, so it is visible before the run
        // leaves `runs`; a loser must never publish, or retracting it could delete the winner's status.
        runs.computeIfPresent(name, (key, registered) -> {
            if (registered != run) {
                return registered;
            }
            terminalStatuses.put(name, status);
            deregistered.set(true);
            return null;
        });
        if (!deregistered.get()) {
            return false;
        }
        if (runs.isEmpty()) {
            Metrics.setLoadGaugeReaders(null, null);
        } else {
            installGaugeReaders();
        }
        stopSharedTickIfIdle();
        LOG.info("load scenario '{}' {} ({} requests sent)",
            name, terminalState.name().toLowerCase(), run.requestsSent.get());
        return true;
    }

    /**
     * Evict the retained metric series for a scenario removed from the registry (its run is no longer
     * referenceable). Called by HttpState when a DELETE removes a registered scenario.
     */
    public void evictTerminalSeries(String name) {
        LoadScenarioStatus status = terminalStatuses.remove(name);
        if (status != null && status.runId != null) {
            Metrics.evictLoadRun(status.runId);
        }
    }

    /**
     * Control tick: advance through the stages by elapsed time, apply the current stage's setpoint
     * (VU pool size or arrival rate), and detect completion (all stages elapsed or maxRequests
     * reached). Runs on the scheduler thread.
     */
    private void tick(RunningScenario run) {
        if (run.stopped.get() || !isCurrent(run)) {
            return;
        }
        long now = clock.getAsLong();
        // PENDING: wait out the start delay (measured against the orchestrator clock so the injected
        // test clock works). Fire NO load until the delay elapses, then transition to RUNNING and start
        // the stage clock from that moment.
        if (run.state == LoadScenarioState.PENDING) {
            if (now - run.triggeredAt < run.scenario.getStartDelayMillis()) {
                return;
            }
            run.beginRunning(now);
            LOG.info("load scenario '{}' start delay elapsed, now running ({} step(s), {} stage(s))",
                run.scenario.getName(), run.scenario.getSteps().size(),
                run.scenario.getProfile().getStages().size());
        }
        if (run.maxRequestsReached()) {
            completeInternal(run);
            return;
        }
        long elapsed = now - run.startedAt;

        // Locate the active stage for the elapsed time. The run ends once elapsed passes the sum of all
        // stage durations.
        List<LoadStage> stages = run.scenario.getProfile().getStages();
        long boundary = 0;
        int stageIndex = -1;
        long elapsedInStage = 0;
        for (int i = 0; i < stages.size(); i++) {
            long stageDuration = Math.max(0, stages.get(i).getDurationMillis());
            if (elapsed < boundary + stageDuration) {
                stageIndex = i;
                elapsedInStage = elapsed - boundary;
                break;
            }
            boundary += stageDuration;
        }
        if (stageIndex < 0) {
            // Past the last stage boundary: the whole sequence is done.
            completeInternal(run);
            return;
        }

        run.stageIndex.set(stageIndex);
        run.elapsedInStageMillis.set(elapsedInStage);
        LoadStage stage = stages.get(stageIndex);

        // On a stage transition (entering any new stage index, including the first), reset the RATE
        // arrival-rate accumulator so a freshly-entered RATE stage integrates ONLY its own elapsed time.
        // This re-arms the driveRateStage "last==0 → first tick initialises, does not integrate" guard
        // and discards iterations still owed from a prior RATE stage, so a RATE stage that follows a
        // VU/PAUSE stage (or another RATE stage after a gap) cannot add targetRate·gap to the deficit on
        // its first tick and burst. Runs exactly once per boundary (guarded by activeStageIndex) on the
        // single scheduler thread, so no extra synchronisation is needed for these run-local fields.
        if (run.activeStageIndex != stageIndex) {
            run.activeStageIndex = stageIndex;
            run.lastRateTickMillis = 0;
            run.rateDeficit = 0;
        }

        switch (stage.getType()) {
            case VU:
                driveVuStage(run, stage, elapsedInStage);
                break;
            case RATE:
                driveRateStage(run, stage, elapsedInStage, now);
                break;
            case PAUSE:
            default:
                // No load: target zero VUs so any looping VUs from a prior VU stage retire at their
                // iteration boundary and the pool drains.
                run.targetVUs.set(0);
                break;
        }

        // Evaluate in-run thresholds from this run's per-run data and, when abortOnFail is set and the
        // grace window has elapsed, abort the run early on a FAIL verdict (terminal STOPPED state via
        // the existing stop path, marked aborted-by-threshold).
        Boolean verdict = run.evaluateThresholds(now);
        if (Boolean.FALSE.equals(verdict)
            && run.scenario.isAbortOnFail()
            && elapsed >= run.scenario.getAbortGraceMillis()) {
            abortOnThresholdFail(run);
        }
    }

    /**
     * Abort a run because an {@code abortOnFail} threshold was breached after the grace window: mark it
     * aborted-by-threshold and terminate it via the existing stop path (terminal STOPPED state). The
     * FAIL verdict already recorded by {@link RunningScenario#evaluateThresholds(long)} is carried into
     * the retained terminal status.
     */
    private void abortOnThresholdFail(RunningScenario run) {
        run.abortedByThreshold = true;
        if (terminate(run, LoadScenarioState.STOPPED)) {
            LOG.info("load scenario '{}' aborted by threshold breach ({} requests sent)",
                run.scenario.getName(), run.requestsSent.get());
        }
    }

    /**
     * Closed-model VU stage: maintain a pool of <em>looping</em> virtual users sized to the stage's
     * VU setpoint. Growth launches new looping VUs; surplus VUs retire at their iteration boundary.
     */
    private void driveVuStage(RunningScenario run, LoadStage stage, long elapsedInStage) {
        int target = Math.max(0, stage.targetVusAt(elapsedInStage));
        run.targetVUs.set(target);
        int active = run.activeVUs.get();
        if (active < target) {
            int toLaunch = target - active;
            for (int i = 0; i < toLaunch; i++) {
                int vuId = run.vuIdSequence.getAndIncrement();
                run.activeVUs.incrementAndGet();
                launchIteration(run, vuId, 0, true);
            }
        }
    }

    /**
     * Open-model arrival-rate stage: start as many new one-shot iterations as the integral of the
     * target rate since the last tick demands (deficit accounting), bounded by the stage's VU cap.
     *
     * <p>The deficit is a fractional accumulator: each tick adds {@code rate * dtSeconds} iterations
     * owed and starts {@code floor(deficit)} of them, carrying the fractional remainder forward so the
     * achieved long-run rate equals the target rate exactly regardless of tick granularity. Each
     * started iteration occupies one VU slot for its lifetime; the {@code activeVUs} pool auto-scales
     * up to the cap. When the cap is hit the unstarted owed iterations are dropped (deficit clamped)
     * and counted as a {@code rate_limit} throttle so the operator can see the shortfall.
     */
    private void driveRateStage(RunningScenario run, LoadStage stage, long elapsedInStage, long now) {
        double targetRate = stage.targetRateAt(elapsedInStage);
        run.targetRate.set(Double.doubleToRawLongBits(Math.max(0.0, targetRate)));
        // targetVUs for a RATE stage is the live in-use VU pool (informational for the status DTO).
        int stageVuCap = stage.getMaxVus() != null && stage.getMaxVus() > 0
            ? stage.getMaxVus()
            : configuration.loadGenerationMaxVirtualUsers();

        long last = run.lastRateTickMillis;
        if (last == 0) {
            run.lastRateTickMillis = now;
            run.targetVUs.set(run.activeVUs.get());
            return;
        }
        long dtMillis = Math.max(0, now - last);
        run.lastRateTickMillis = now;
        run.rateDeficit += targetRate * (dtMillis / 1000.0);

        int toStart = (int) Math.floor(run.rateDeficit);
        if (toStart > 0) {
            int started = 0;
            for (int i = 0; i < toStart; i++) {
                if (run.activeVUs.get() >= stageVuCap) {
                    break;
                }
                int vuId = run.vuIdSequence.getAndIncrement();
                run.activeVUs.incrementAndGet();
                launchIteration(run, vuId, 0, false);
                started++;
            }
            run.rateDeficit -= started;
            int shortfall = toStart - started;
            if (shortfall > 0) {
                // The VU cap blocked the rate: drop the owed-but-unstartable iterations (so the deficit
                // does not snowball) and record the shortfall as a rate_limit throttle. This is the
                // dominant drop path for an overloaded open-model run, so the shortfall also feeds the
                // per-run droppedIterations counter — keeping the invariant droppedIterations ==
                // count(rate_limit) + count(inflight_cap) across all three throttle sites.
                run.rateDeficit -= shortfall;
                if (run.rateDeficit < 0) {
                    run.rateDeficit = 0;
                }
                run.droppedIterations.addAndGet(shortfall);
                for (int i = 0; i < shortfall; i++) {
                    Metrics.incrementLoadThrottled(run.scenario.getName(), run.runId, "rate_limit");
                }
            }
        }
        run.targetVUs.set(run.activeVUs.get());
    }

    private void completeInternal(RunningScenario run) {
        // Final threshold evaluation so a normally-completed run carries PASS (all satisfied) or FAIL
        // (any breached); a run with no thresholds keeps its null verdict. terminate() then performs
        // the atomic, gap-free de-registration: a run that a concurrent re-trigger has already
        // replaced is no longer registered, so it publishes no terminal record.
        run.evaluateThresholds(clock.getAsLong());
        terminate(run, LoadScenarioState.COMPLETED);
    }

    /**
     * Run one iteration for a virtual user: render and fire each step in order, chaining the
     * CompletableFutures. After the last step, a {@code looping} VU schedules its next iteration
     * (closed model); a one-shot VU (open/arrival-rate model) ends and releases its slot. No
     * dedicated thread per VU; no blocking.
     */
    private void launchIteration(RunningScenario run, int vuId, long vuIteration, boolean looping) {
        if (run.stopped.get() || !isCurrent(run)) {
            endVu(run);
            return;
        }
        // Closed-model surplus retirement: if the live population currently exceeds the target
        // concurrency, this looping VU's loop ends here. Applied as a single atomic conditional
        // decrement so concurrent surplus VUs never collapse the population below the target. One-shot
        // (rate) VUs never retire this way — they always run exactly one iteration.
        if (looping && vuIteration > 0 && tryRetireSurplus(run)) {
            return;
        }
        // Data-feeder row selection: allocate a global per-iteration index (incremented once per
        // iteration, distinct from the per-step iterationIndex used for $iteration.index) and pick the
        // row this iteration will expose as $iteration.data. For SEQUENTIAL the index also gates the run:
        // once it reaches the dataset size the dataset is exhausted, so no further iteration runs and the
        // run completes (each row used exactly once). Done BEFORE any work so a SEQUENTIAL run never
        // dispatches past its dataset; composes with the VU/RATE models via the existing complete path.
        Map<String, String> data = Collections.emptyMap();
        List<Map<String, String>> feederRows = run.feederRows;
        if (feederRows != null && !feederRows.isEmpty()) {
            LoadFeeder.Strategy strategy = run.feederStrategy;
            if (strategy == LoadFeeder.Strategy.SEQUENTIAL) {
                long seqIndex = run.feederIterationIndex.getAndIncrement();
                if (seqIndex >= feederRows.size()) {
                    // Dataset exhausted: this VU's loop ends here (release its slot once) and the run
                    // completes. completeInternal is idempotent across concurrent VUs (atomic de-register).
                    endVu(run);
                    completeInternal(run);
                    return;
                }
                data = feederRows.get((int) seqIndex);
            } else if (strategy == LoadFeeder.Strategy.RANDOM) {
                data = feederRows.get(ThreadLocalRandom.current().nextInt(feederRows.size()));
            } else {
                // CIRCULAR (default): cycle the dataset by the global per-iteration index, never exhausts.
                long circularIndex = run.feederIterationIndex.getAndIncrement();
                data = feederRows.get((int) Math.floorMod(circularIndex, feederRows.size()));
            }
        }
        // Adaptive-pacing anchor: record the wall-clock nanos at which this iteration's work begins, so
        // the closed-model reschedule point can wait out the remainder of the target iteration cycle.
        // Threaded (like {@code captured}) through the chained fireStep calls; ignored when pacing is off
        // and never used for the one-shot (RATE) path. Read via TimeService.nanoTime() (the same clock
        // recordResult uses) so it is independent of the mutable test clock used for stage progression.
        final long iterationStartNanos = TimeService.nanoTime();
        // Per-iteration cross-step captured-variable map: created fresh here so its scope is exactly one
        // virtual user's single pass through the steps (one user "session"). It is threaded through the
        // chained fireStep calls below and never shared across VUs or across a VU's successive iterations,
        // so cross-step correlation is race-free and captures never leak between users or iterations.
        Map<String, String> captured = new ConcurrentHashMap<>();
        // Per-iteration ordered step sequence: SEQUENTIAL runs all steps in declared order (the original
        // behaviour); WEIGHTED runs a single step chosen at random proportional to the steps' weights, so
        // one run can model a mixed workload. Computed once here and threaded through the chained fireStep
        // calls (like {@code captured}) so the dispatch logic — pacing, capture, feeder, cap handling and
        // the closed/one-shot iteration boundary — stays a single code path that simply indexes this list.
        List<LoadStep> iterationSteps = selectIterationSteps(run.scenario);
        fireStep(run, vuId, vuIteration, 0, looping, captured, data, iterationStartNanos, iterationSteps);
    }

    /**
     * Build the ordered list of steps a single iteration will run. SEQUENTIAL (default) returns the
     * scenario's full ordered step list unchanged. WEIGHTED returns a single-element list containing one
     * step chosen by weighted random over the steps' weights (cumulative-weight + a uniform draw in
     * {@code [0, totalWeight)}); an absent weight counts as {@code 1.0}. validate() has already proved a
     * WEIGHTED scenario has at least one step and a positive total weight, so the selection always
     * resolves to exactly one step.
     */
    private static List<LoadStep> selectIterationSteps(LoadScenario scenario) {
        List<LoadStep> steps = scenario.getSteps();
        if (scenario.getStepSelection() != LoadScenario.StepSelection.WEIGHTED || steps == null || steps.isEmpty()) {
            return steps;
        }
        double totalWeight = 0;
        for (LoadStep step : steps) {
            totalWeight += weightOf(step);
        }
        // Defensive: validate() rejects a non-positive total, but never index past the end on a rounding
        // edge — fall back to the last step.
        double target = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0;
        for (LoadStep step : steps) {
            cumulative += weightOf(step);
            if (target < cumulative) {
                return Collections.singletonList(step);
            }
        }
        return Collections.singletonList(steps.get(steps.size() - 1));
    }

    /** A step's effective selection weight: its explicit weight, or {@code 1.0} when absent. */
    private static double weightOf(LoadStep step) {
        Double weight = step != null ? step.getWeight() : null;
        return weight != null ? weight : 1.0;
    }

    /**
     * Atomically retire this VU iff the live population currently exceeds the target concurrency,
     * decrementing the active-population counter exactly once on success. Returns {@code true} when
     * the VU was retired (its loop must end), {@code false} when it should continue.
     */
    private boolean tryRetireSurplus(RunningScenario run) {
        while (true) {
            int active = run.activeVUs.get();
            if (active <= run.targetVUs.get()) {
                return false;
            }
            if (run.activeVUs.compareAndSet(active, active - 1)) {
                return true;
            }
        }
    }

    /**
     * End a virtual user's loop, releasing the single active-population slot it owned. Called from
     * exactly one of the loop's terminal exit points so the live counter decrements exactly once per
     * launched VU (never zero, never twice).
     */
    private void endVu(RunningScenario run) {
        run.activeVUs.decrementAndGet();
    }

    private void fireStep(RunningScenario run, int vuId, long vuIteration, int stepIndex, boolean looping, Map<String, String> captured, Map<String, String> data, long iterationStartNanos, List<LoadStep> iterationSteps) {
        // Coordinated-omission correction: capture the scheduled-due timestamp at the very start of the
        // dispatch path — BEFORE the in-flight permit and RPS-token acquire below — so the latency we
        // record measures from the moment dispatch was requested, INCLUDING any queueing wait the
        // self-load guard imposes when the system-under-test is overloaded. Measuring from after the
        // acquire (the old behaviour) excluded that wait and made tail percentiles look far better than
        // reality (the classic coordinated-omission error).
        final long scheduledNanos = TimeService.nanoTime();
        if (run.stopped.get() || !isCurrent(run)) {
            endVu(run);
            return;
        }
        // Stop dispatching once the request cap is reached; the control tick then transitions to
        // "completed". Enforcing this here (not only on the tick) bounds the overshoot. This VU's loop
        // ends here, so release its slot exactly once.
        if (run.maxRequestsReached()) {
            endVu(run);
            completeInternal(run);
            return;
        }
        List<LoadStep> steps = iterationSteps;
        if (steps == null || stepIndex >= steps.size()) {
            // Iteration finished: account it.
            Metrics.incrementLoadIteration(run.scenario.getName(), run.runId);
            if (!looping) {
                // One-shot (arrival-rate) iteration: end the VU and release its slot.
                endVu(run);
                return;
            }
            // Closed-model VU: schedule the next iteration (re-scheduled, not recursed, to avoid
            // starving the single scheduler thread). Adaptive pacing: if a target iteration cycle is
            // configured and this iteration's work finished inside it, wait out the remainder before the
            // next iteration's start; on overrun (or when pacing is off) the delay is 0 (immediate).
            // Pacing only delays the NEXT launch — in-flight latency measurement is untouched. The
            // one-shot (RATE) path above returns before here, so pacing never affects the open model.
            long nextIteration = vuIteration + 1;
            long pacingDelayMillis = pacingDelayMillis(run, iterationStartNanos);
            run.lastPacingDelayMillis = pacingDelayMillis;
            scheduler.schedule(() -> launchIteration(run, vuId, nextIteration, true), pacingDelayMillis, TimeUnit.MILLISECONDS);
            return;
        }

        LoadStep step = steps.get(stepIndex);
        long globalIndex = run.iterationIndex.getAndIncrement();
        long count = run.requestsSent.get();
        long elapsed = clock.getAsLong() - run.startedAt;
        IterationContext iteration = new IterationContext(globalIndex, vuId, vuIteration, elapsed, count, captured, data);
        final String stepLabel = run.stepLabel(step, stepIndex);
        final Map<String, String> stepCustomLabels = run.customLabelsFor(step);

        HttpRequest rendered;
        try {
            rendered = run.render(step.getRequest(), iteration);
        } catch (Exception e) {
            LOG.warn("load scenario '{}' failed to render step {} (vu {} iteration {}): {}",
                run.scenario.getName(), stepIndex, vuId, vuIteration, e.getMessage());
            run.failed.incrementAndGet();
            run.requestsSent.incrementAndGet();
            Metrics.incrementLoadError(run.scenario.getName(), run.runId, "render");
            scheduleNextStep(run, vuId, vuIteration, stepIndex, step, looping, captured, data, iterationStartNanos, iterationSteps);
            return;
        }

        // Mark every generated request so the server keeps its own load traffic out of the
        // user-facing request event log (a bounded ring buffer). Without this, a running scenario
        // floods the log and evicts real / LLM traffic that the Traffic, Trace and Optimise views
        // depend on. The marker is an in-process flag on the request (HttpRequest#setLoadGenerated)
        // that is never serialized to the wire, so it stays driver-only and cannot reach an upstream
        // target to disable that target's logging. Gated by loadGenerationSuppressEventLog (default
        // true). Load throughput/latency/SLO are recorded client-side in recordResult(), so
        // suppressing the log entry does not lose metrics.
        if (rendered != null && configuration != null && Boolean.TRUE.equals(configuration.loadGenerationSuppressEventLog())) {
            rendered.setLoadGenerated(true);
        }

        // Self-load guard at dispatch: acquire an in-flight permit and an RPS token. Each skip is a
        // distinct throttle reason so an operator can see why a scenario could not reach its setpoint,
        // and is counted as a dropped iteration (a due iteration the cap prevented from dispatching).
        if (!run.inFlight.tryAcquire()) {
            run.droppedIterations.incrementAndGet();
            Metrics.incrementLoadThrottled(run.scenario.getName(), run.runId, "inflight_cap");
            scheduleNextStep(run, vuId, vuIteration, stepIndex, step, looping, captured, data, iterationStartNanos, iterationSteps);
            return;
        }
        if (!run.tryAcquireRpsToken(clock.getAsLong())) {
            run.inFlight.release();
            run.droppedIterations.incrementAndGet();
            Metrics.incrementLoadThrottled(run.scenario.getName(), run.runId, "rate_limit");
            scheduleNextStep(run, vuId, vuIteration, stepIndex, step, looping, captured, data, iterationStartNanos, iterationSteps);
            return;
        }

        run.requestsSent.incrementAndGet();
        run.inFlightCount.incrementAndGet();
        final String host = hostOf(rendered);
        final String route = run.routeLabel(step, rendered);
        final String method = methodOf(rendered);
        final long requestBytes = bodyBytes(rendered != null ? rendered.getBodyAsRawBytes() : null);
        final String traceId = traceIdOf(rendered);
        CompletableFuture<HttpResponse> future;
        try {
            future = run.sender.apply(rendered);
        } catch (Exception e) {
            run.inFlight.release();
            run.inFlightCount.decrementAndGet();
            recordResult(run, host, stepLabel, route, method, requestBytes, traceId, stepCustomLabels, null, scheduledNanos, true, "connection");
            scheduleNextStep(run, vuId, vuIteration, stepIndex, step, looping, captured, data, iterationStartNanos, iterationSteps);
            return;
        }
        if (future == null) {
            run.inFlight.release();
            run.inFlightCount.decrementAndGet();
            recordResult(run, host, stepLabel, route, method, requestBytes, traceId, stepCustomLabels, null, scheduledNanos, true, "null_response");
            scheduleNextStep(run, vuId, vuIteration, stepIndex, step, looping, captured, data, iterationStartNanos, iterationSteps);
            return;
        }
        future.whenComplete((response, throwable) -> {
            run.inFlight.release();
            run.inFlightCount.decrementAndGet();
            boolean error = throwable != null || response == null
                || (response.getStatusCode() != null && response.getStatusCode() >= 500);
            String errorKind = classifyError(throwable, response);
            if (BLOCKED.equals(errorKind) && run.blockedLogged.compareAndSet(false, true)) {
                // once per run: every refused request is counted, as an error of kind "blocked"
                logBlocked(run, blockedCause(throwable));
            }
            recordResult(run, host, stepLabel, route, method, requestBytes, traceId, stepCustomLabels, response, scheduledNanos, error, errorKind);
            // Evaluate this step's per-step response checks (k6 'check' parity) against the completed
            // response, recording pass/fail outcomes and feeding the CHECK_FAILURE_RATE threshold.
            // Best-effort and never throws out of the dispatch path.
            applyChecks(run, step, stepLabel, response);
            // Apply this step's cross-step captures to the per-iteration map BEFORE the next step is
            // scheduled, so a subsequent step's template can read what this response yielded. Best-effort
            // and never throws out of the dispatch path: a missing value or extraction error falls back
            // to the capture's defaultValue (when set) or leaves the variable unset.
            applyCaptures(run, step, response, captured);
            scheduleNextStep(run, vuId, vuIteration, stepIndex, step, looping, captured, data, iterationStartNanos, iterationSteps);
        });
    }

    /**
     * Apply a step's {@link LoadCapture} rules against the completed response, writing extracted values
     * into the per-iteration {@code captured} map. Best-effort and side-effect-only: a malformed
     * expression, a missing value, a null response, or any extraction error is logged at debug and
     * skipped, falling back to the capture's {@code defaultValue} when set, otherwise leaving the
     * variable unset. Never throws out of the dispatch path.
     */
    private void applyCaptures(RunningScenario run, LoadStep step, HttpResponse response, Map<String, String> captured) {
        List<LoadCapture> captures = step != null ? step.getCaptures() : null;
        if (captures == null || captures.isEmpty()) {
            return;
        }
        for (LoadCapture capture : captures) {
            if (capture == null || isBlank(capture.getName()) || capture.getSource() == null) {
                continue;
            }
            String value = null;
            try {
                value = extractCapture(capture, response);
            } catch (Throwable throwable) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("load scenario '{}' capture '{}' failed - skipping: {}",
                        run.scenario.getName(), capture.getName(), throwable.getMessage());
                }
            }
            if (value == null) {
                value = capture.getDefaultValue();
            }
            if (value != null) {
                captured.put(capture.getName(), value);
            }
        }
    }

    /**
     * Extract a single {@link LoadCapture}'s value from the response, or null when nothing matches.
     * Delegates to the shared {@link LoadResponseExtractor} primitives (the same extraction code the
     * per-step {@link LoadCheck}s reuse) so JSONPath / header / body-regex extraction lives in one place.
     */
    private static String extractCapture(LoadCapture capture, HttpResponse response) {
        if (response == null) {
            return null;
        }
        String expression = capture.getExpression();
        switch (capture.getSource()) {
            case BODY_JSONPATH:
                return LoadResponseExtractor.jsonPath(response, expression);
            case HEADER:
                return LoadResponseExtractor.header(response, expression);
            case BODY_REGEX:
                return LoadResponseExtractor.regexGroup1(response, expression);
            default:
                return null;
        }
    }

    /**
     * Evaluate a step's {@link LoadCheck}s against the completed response, recording each pass/fail on
     * the run (per-check aggregates + run totals for {@code CHECK_FAILURE_RATE}) and emitting the
     * {@code mock_server_load_checks} metric. Best-effort and side-effect-only: a malformed
     * expression or any evaluation error is logged at debug and counts as a FAIL (a check that cannot be
     * evaluated has not been satisfied). Never throws out of the dispatch path. Skipped entirely when
     * the response is null (a connection error is already surfaced by the error metrics), so checks only
     * ever assert against a real response — the case latency/error metrics miss.
     */
    private void applyChecks(RunningScenario run, LoadStep step, String stepLabel, HttpResponse response) {
        List<LoadCheck> checks = step != null ? step.getChecks() : null;
        if (checks == null || checks.isEmpty() || response == null) {
            return;
        }
        for (LoadCheck check : checks) {
            if (check == null || !check.isValid()) {
                continue;
            }
            boolean passed;
            try {
                passed = check.evaluate(response);
            } catch (Throwable throwable) {
                passed = false;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("load scenario '{}' check on step '{}' failed to evaluate - counting as fail: {}",
                        run.scenario.getName(), stepLabel, throwable.getMessage());
                }
            }
            run.recordCheck(stepLabel, check, passed);
            if (isCurrent(run)) {
                Metrics.incrementLoadCheck(run.scenario.getName(), run.runId, stepLabel, passed ? "pass" : "fail");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Classify the error branch of a completed dispatch into one of the kind labels, or null when
     * the request succeeded.
     */
    private static String classifyError(Throwable throwable, HttpResponse response) {
        if (blockedCause(throwable) != null) {
            return BLOCKED;
        }
        if (throwable != null) {
            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
            String name = cause.getClass().getSimpleName().toLowerCase();
            String message = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
            if (name.contains("timeout") || message.contains("timeout") || message.contains("timed out")) {
                return "timeout";
            }
            return "connection";
        }
        if (response == null) {
            return "null_response";
        }
        if (response.getStatusCode() != null && response.getStatusCode() >= 500) {
            return "http_5xx";
        }
        return null;
    }

    /**
     * @return the refusal, by forwardProxyBlockPrivateNetworks, that failed a request, or null
     */
    private static ForwardTargetBlockedException blockedCause(Throwable throwable) {
        if (throwable instanceof ForwardTargetBlockedException) {
            return (ForwardTargetBlockedException) throwable;
        }
        return throwable != null && throwable.getCause() instanceof ForwardTargetBlockedException ? (ForwardTargetBlockedException) throwable.getCause() : null;
    }

    private void scheduleNextStep(RunningScenario run, int vuId, long vuIteration, int stepIndex, LoadStep step, boolean looping, Map<String, String> captured, Map<String, String> data, long iterationStartNanos, List<LoadStep> iterationSteps) {
        if (run.stopped.get() || !isCurrent(run)) {
            // The VU loop ends here (the scenario stopped or was replaced mid-iteration). This is a
            // genuine loop-exit point, so release the slot exactly once.
            endVu(run);
            return;
        }
        long thinkMillis = step.getThinkTime() != null ? Math.max(0, step.getThinkTime().sampleValueMillis()) : 0;
        scheduler.schedule(() -> fireStep(run, vuId, vuIteration, stepIndex + 1, looping, captured, data, iterationStartNanos, iterationSteps), thinkMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Compute the adaptive-pacing delay (milliseconds) to wait before launching this VU's next
     * closed-model iteration: {@code max(0, round(cycleMillis - elapsedMillis))}, where the cycle is
     * the scenario's {@link LoadPacing#cycleMillis() target cycle} and {@code elapsedMillis} is how
     * long this iteration's work took (wall-clock, via {@link TimeService#nanoTime()}). Returns
     * {@code 0} when no pacing is configured or when the iteration overran the cycle.
     */
    private long pacingDelayMillis(RunningScenario run, long iterationStartNanos) {
        LoadPacing pacing = run.scenario.getPacing();
        double cycleMillis = pacing != null ? pacing.cycleMillis() : 0;
        if (cycleMillis <= 0) {
            return 0;
        }
        long elapsedMillis = Math.max(0, (TimeService.nanoTime() - iterationStartNanos) / 1_000_000L);
        long delay = Math.round(cycleMillis - elapsedMillis);
        return Math.max(0, delay);
    }

    private void recordResult(RunningScenario run, String host, String stepLabel, String route, String method,
                              long requestBytes, String traceId, Map<String, String> customLabels,
                              HttpResponse response, long scheduledNanos, boolean error, String errorKind) {
        // Coordinated-omission-corrected latency: measured from the scheduled-due time captured at the
        // start of the dispatch path (before the in-flight/RPS acquire), so any queueing wait the
        // self-load guard imposed is INCLUDED. This single corrected value is the one recorded to the
        // Prometheus histogram, the per-run HDR histogram and the SLO sample store — there is no
        // competing post-acquire "service time" recorded to the same metric.
        long latencyMillis = Math.max(0, (TimeService.nanoTime() - scheduledNanos) / 1_000_000L);
        if (error) {
            run.failed.incrementAndGet();
        } else {
            run.succeeded.incrementAndGet();
        }
        // Record every completed iteration — successes AND failures (a failed request still has a
        // latency) — into the authoritative per-run HDR histogram backing the status DTO's percentiles.
        run.latencyHistogram.recordValue(latencyMillis);
        Integer statusCode = response != null ? response.getStatusCode() : null;
        double latencySeconds = latencyMillis / 1000.0;
        long responseBytes = bodyBytes(response != null ? response.getBodyAsRawBytes() : null);
        // Mirror the forward-path observability so load traffic still shows up on the same metrics.
        Metrics.observeForwardRequest(host, statusCode, latencySeconds);
        // New first-class load family: scenario/run/step/route/method/status-class dimensioned. Only
        // emit while this run is still the current run: a run replaced by a new PUT has already had its
        // load series evicted, so a late-draining in-flight request must NOT resurrect them.
        if (isCurrent(run)) {
            Metrics.observeLoadRequest(run.scenario.getName(), run.runId, stepLabel, route, method, statusCode,
                latencySeconds, requestBytes, responseBytes, traceId, customLabels);
            if (error && errorKind != null) {
                Metrics.incrementLoadError(run.scenario.getName(), run.runId, errorKind);
            }
        }
        // Feed the SLO sample store so the SLO verdict feature can read load-driven SLIs.
        SloSampleStore.getInstance().record(configuration, TimeService.currentTimeMillis(), latencyMillis, error, Scope.FORWARD, host);
    }

    private static String hostOf(HttpRequest request) {
        if (request == null) {
            return null;
        }
        String hostHeader = request.getFirstHeader("Host");
        if (hostHeader != null && !hostHeader.isEmpty()) {
            int colon = hostHeader.indexOf(':');
            return colon > 0 ? hostHeader.substring(0, colon) : hostHeader;
        }
        return request.getRemoteAddress();
    }

    private static String methodOf(HttpRequest request) {
        if (request == null || request.getMethod() == null) {
            return "unknown";
        }
        String method = request.getMethod().getValue();
        return method != null && !method.isEmpty() ? method : "unknown";
    }

    private static long bodyBytes(byte[] body) {
        return body != null ? body.length : 0L;
    }

    /**
     * Extract the request's trace id (for the histogram exemplar) from a W3C {@code traceparent}
     * header on the rendered request, or null when absent/invalid.
     */
    private static String traceIdOf(HttpRequest request) {
        if (request == null) {
            return null;
        }
        String traceparent = request.getFirstHeader("traceparent");
        if (traceparent == null || traceparent.isEmpty()) {
            return null;
        }
        try {
            W3CTraceContext context = W3CTraceContext.parse(traceparent, null);
            return context != null && context.isValid() ? context.getTraceId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Force one control tick immediately on every active run (test hook), bypassing the scheduler
     * interval, so progression can be asserted deterministically. No-op when no scenario is active.
     */
    void tickNow() {
        tickAll();
    }

    /**
     * Test hook for the deterministic clock: callers using a mutable {@link LongSupplier} advance
     * the clock externally; this triggers a tick so the orchestrator observes the new elapsed time.
     */
    void advanceNow(long ignoredMillis) {
        tickNow();
    }

    /**
     * Test hook: the live virtual-user population of the current run, or {@code 0} when no scenario is
     * running.
     */
    int activeVuCount() {
        RunningScenario run = lastRunName != null ? runs.get(lastRunName) : null;
        if (run != null) {
            return run.activeVUs.get();
        }
        return 0;
    }

    /**
     * Test hook: the live virtual-user population of the most recently started run, even after it has
     * stopped or completed. Returns {@code 0} when no scenario has ever run.
     */
    int lastRunActiveVuCount() {
        RunningScenario run = lastRun.get();
        return run != null ? run.activeVUs.get() : 0;
    }

    /**
     * Test hook: the most recent adaptive-pacing delay (milliseconds) the most-recently-started run
     * applied at a closed-model iteration reschedule. {@code 0} when pacing is off or the last
     * iteration overran the cycle. Returns {@code 0} when no scenario has ever run.
     */
    long lastRunPacingDelayMillis() {
        RunningScenario run = lastRun.get();
        return run != null ? run.lastPacingDelayMillis : 0;
    }

    public String validate(LoadScenario scenario) {
        if (scenario == null) {
            return "'loadScenario' is required";
        }
        if (scenario.getName() == null || scenario.getName().isBlank()) {
            return "'name' is required";
        }
        List<LoadStep> steps = scenario.getSteps();
        if (steps == null || steps.isEmpty()) {
            return "'steps' must contain at least one step";
        }
        int maxSteps = configuration.loadGenerationMaxSteps();
        if (steps.size() > maxSteps) {
            return "'steps' exceeds the maximum of " + maxSteps;
        }
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i) == null || steps.get(i).getRequest() == null) {
                return "step[" + i + "] must have a request";
            }
            if (!isFiniteOrNull(steps.get(i).getWeight())) {
                return "step[" + i + "].weight must be a finite number";
            }
        }
        // WEIGHTED step selection: each iteration runs one step chosen proportional to its weight, so
        // every step's effective weight (absent = 1.0) must be > 0 and the total positive. SEQUENTIAL
        // (the default) runs all steps in order and ignores any weights — a weight may be present but is
        // unused, and is not rejected (lenient, so a scenario can be flipped between modes freely).
        if (scenario.getStepSelection() == LoadScenario.StepSelection.WEIGHTED) {
            double totalWeight = 0;
            for (int i = 0; i < steps.size(); i++) {
                Double weight = steps.get(i).getWeight();
                double effective = weight != null ? weight : 1.0;
                if (effective <= 0) {
                    return "step[" + i + "].weight must be > 0 when stepSelection is WEIGHTED";
                }
                totalWeight += effective;
            }
            if (!(totalWeight > 0) || !Double.isFinite(totalWeight)) {
                return "'steps' total weight must be a finite number > 0 when stepSelection is WEIGHTED";
            }
        }
        LoadProfile profile = scenario.getProfile();
        if (profile == null) {
            return "'profile' is required";
        }
        // The stage cap is checked on the count BEFORE getStages() expands a shape, so an oversized
        // shape (e.g. STAIRS with a huge 'steps') is rejected without allocating its stages.
        int maxStages = configuration.loadGenerationMaxStages();
        long stageCount = profile.stageCount();
        boolean shapeOnly = profile.getShape() != null && (profile.getRawStages() == null || profile.getRawStages().isEmpty());
        if (shapeOnly) {
            String shapeError = nonFiniteShapeParameter(profile.getShape());
            if (shapeError != null) {
                return shapeError;
            }
            long limit = Math.min(maxStages, LoadShapes.MAX_EXPANDED_STAGES);
            if (stageCount > limit) {
                return "'profile.shape' expands to " + stageCount + " stages, which exceeds the maximum of " + limit;
            }
        } else if (stageCount > maxStages) {
            return "'profile.stages' exceeds the maximum of " + maxStages;
        }
        // getStages() returns the explicit stages, or — when only a 'shape' is set — the stages that
        // shape expands into; so all the per-stage / cap checks below cover shaped profiles for free.
        List<LoadStage> stages = profile.getStages();
        if (stages == null || stages.isEmpty()) {
            if (profile.getShape() != null) {
                return "'profile.shape' expands to no stages; check its parameters (durations and step count must be > 0)";
            }
            return "'profile' must contain either 'stages' or a 'shape'";
        }
        int maxVus = configuration.loadGenerationMaxVirtualUsers();
        double maxRate = configuration.loadGenerationMaxRate();
        boolean anyLoad = false;
        for (int i = 0; i < stages.size(); i++) {
            LoadStage stage = stages.get(i);
            if (stage == null) {
                return "stage[" + i + "] is required";
            }
            if (stage.getType() == null) {
                return "stage[" + i + "] must have a type";
            }
            if (!isFiniteOrNull(stage.getRate()) || !isFiniteOrNull(stage.getStartRate()) || !isFiniteOrNull(stage.getEndRate())) {
                return "stage[" + i + "] rate, startRate and endRate must be finite numbers";
            }
            if (stage.getDurationMillis() <= 0) {
                return "stage[" + i + "].durationMillis must be > 0";
            }
            switch (stage.getType()) {
                case VU:
                    if (stage.isVuRamp()) {
                        if (stage.getStartVus() == null || stage.getEndVus() == null) {
                            return "stage[" + i + "] VU ramp requires both startVus and endVus";
                        }
                    } else if (stage.getVus() == null) {
                        return "stage[" + i + "] VU stage requires vus (hold) or startVus+endVus (ramp)";
                    }
                    if (stage.peakVus() <= 0) {
                        return "stage[" + i + "] must request at least one virtual user";
                    }
                    if (stage.peakVus() > maxVus) {
                        return "stage[" + i + "] requests " + stage.peakVus() + " virtual users, exceeding the maximum of " + maxVus;
                    }
                    anyLoad = true;
                    break;
                case RATE:
                    if (stage.isRateRamp()) {
                        if (stage.getStartRate() == null || stage.getEndRate() == null) {
                            return "stage[" + i + "] RATE ramp requires both startRate and endRate";
                        }
                    } else if (stage.getRate() == null) {
                        return "stage[" + i + "] RATE stage requires rate (hold) or startRate+endRate (ramp)";
                    }
                    if (stage.peakRate() <= 0) {
                        return "stage[" + i + "] must request a positive arrival rate";
                    }
                    if (stage.peakRate() > maxRate) {
                        return "stage[" + i + "] requests " + stage.peakRate() + " iterations/sec, exceeding the maximum of " + maxRate;
                    }
                    if (stage.getMaxVus() != null && stage.getMaxVus() > maxVus) {
                        return "stage[" + i + "].maxVus " + stage.getMaxVus() + " exceeds the maximum of " + maxVus;
                    }
                    anyLoad = true;
                    break;
                case PAUSE:
                default:
                    break;
            }
        }
        if (!anyLoad) {
            return "'profile' must contain at least one VU or RATE stage";
        }
        long totalDuration = profile.totalDurationMillis();
        if (totalDuration <= 0) {
            return "'profile' total duration must be > 0";
        }
        long maxDuration = configuration.loadGenerationMaxDurationMillis();
        if (totalDuration > maxDuration) {
            return "'profile' total duration " + totalDuration + " ms exceeds the maximum of " + maxDuration + " ms";
        }
        if (scenario.getTemplateType() == HttpTemplate.TemplateType.JAVASCRIPT) {
            return "templateType JAVASCRIPT is not supported for load steps; use VELOCITY or MUSTACHE";
        }
        LoadPacing pacing = scenario.getPacing();
        if (pacing != null && !Double.isFinite(pacing.getValue())) {
            return "'pacing.value' must be a finite number";
        }
        if (pacing != null && pacing.getMode() != null && pacing.getMode() != LoadPacing.Mode.NONE
            && pacing.getValue() <= 0) {
            return "'pacing.value' must be > 0 when 'pacing.mode' is " + pacing.getMode();
        }
        List<LoadThreshold> thresholds = scenario.getThresholds();
        if (thresholds != null) {
            for (int i = 0; i < thresholds.size(); i++) {
                if (thresholds.get(i) != null && !Double.isFinite(thresholds.get(i).getThreshold())) {
                    return "thresholds[" + i + "].threshold must be a finite number";
                }
            }
        }
        LoadFeeder feeder = scenario.getFeeder();
        if (feeder != null) {
            List<Map<String, String>> feederRows;
            try {
                // Resolves inline rows, or parses data/format — a malformed CSV/JSON dataset (or data
                // without a format) surfaces as a clear IllegalArgumentException message here.
                feederRows = feeder.resolvedRows();
            } catch (IllegalArgumentException e) {
                return e.getMessage();
            }
            if (feederRows == null || feederRows.isEmpty()) {
                return "'feeder' must resolve to at least one row (set 'feeder.rows' or 'feeder.data'+'feeder.format')";
            }
        }
        return null;
    }

    private static boolean isFiniteOrNull(Double value) {
        return value == null || Double.isFinite(value);
    }

    private static String nonFiniteShapeParameter(LoadShape shape) {
        String[] names = {"baseline", "peak", "start", "step", "target"};
        Double[] values = {shape.getBaseline(), shape.getPeak(), shape.getStart(), shape.getStep(), shape.getTarget()};
        for (int i = 0; i < names.length; i++) {
            if (!isFiniteOrNull(values[i])) {
                return "'profile.shape." + names[i] + "' must be a finite number";
            }
        }
        return null;
    }

    /** Mutable state for a running scenario. */
    private final class RunningScenario {
        final LoadScenario scenario;
        final Function<HttpRequest, CompletableFuture<HttpResponse>> sender;
        /** Orchestrator-clock time this run was triggered (used to time out a PENDING start delay). */
        final long triggeredAt;
        /**
         * Orchestrator-clock time the stage clock started (the moment the run entered RUNNING). Equal to
         * {@code triggeredAt} when there is no start delay; later for a delayed scenario. Mutable because
         * a PENDING run's stage clock starts only once the delay elapses.
         */
        volatile long startedAt;
        /** Lifecycle state of this run: PENDING (waiting out the start delay) or RUNNING. */
        volatile LoadScenarioState state = LoadScenarioState.LOADED;
        long startedAtEpoch = TimeService.currentTimeMillis();
        /** Stable per-run id (UUID) used as the {@code run_id} metric label and exposed in the status DTO. */
        final String runId = UUID.randomUUID().toString();
        final Metrics.LoadGaugeKey gaugeKey;
        final AtomicBoolean stopped = new AtomicBoolean(false);
        /** Whether a request refused by forwardProxyBlockPrivateNetworks has been logged for this run. */
        final AtomicBoolean blockedLogged = new AtomicBoolean(false);
        /** Live VU population: incremented once per launch, decremented exactly once when a loop ends. */
        final AtomicInteger activeVUs = new AtomicInteger(0);
        /** Live in-flight (dispatched, not-yet-completed) request count, backing the inflight gauge. */
        final AtomicInteger inFlightCount = new AtomicInteger(0);
        /** Monotonic VU-id allocator, kept separate from the live population counter. */
        final AtomicInteger vuIdSequence = new AtomicInteger(0);
        final AtomicInteger targetVUs = new AtomicInteger(0);
        /** Current stage index, exposed in the status DTO. */
        final AtomicInteger stageIndex = new AtomicInteger(0);
        /** Elapsed millis within the current stage, exposed in the status DTO. */
        final AtomicLong elapsedInStageMillis = new AtomicLong(0);
        /** Current target arrival rate (iterations/sec), stored as raw long bits; 0 for non-RATE stages. */
        final AtomicLong targetRate = new AtomicLong(0);
        final AtomicLong iterationIndex = new AtomicLong(0);
        /**
         * Global per-ITERATION index (incremented once per launched iteration, distinct from the
         * per-step {@link #iterationIndex}) used to index the data feeder across all VUs/iterations:
         * CIRCULAR uses {@code index % size}, SEQUENTIAL uses {@code index} and stops the run once it
         * reaches the dataset size. Untouched when there is no feeder or the strategy is RANDOM.
         */
        final AtomicLong feederIterationIndex = new AtomicLong(0);
        /**
         * The resolved feeder dataset (inline rows, or rows parsed from data/format), resolved once at
         * run start so parsing never happens per-iteration. Null/empty when the scenario has no feeder.
         */
        final List<Map<String, String>> feederRows;
        /** The feeder selection strategy (null when there is no feeder). */
        final LoadFeeder.Strategy feederStrategy;
        final AtomicLong requestsSent = new AtomicLong(0);
        final AtomicLong succeeded = new AtomicLong(0);
        final AtomicLong failed = new AtomicLong(0);
        /**
         * Iterations that were due but never dispatched because a safety cap was hit (the in-flight
         * permit or RPS token could not be acquired, or the RATE-stage VU cap blocked the arrival
         * rate). Surfaced as a first-class run statistic so the truncated-distribution signal is
         * visible rather than hidden — the percentiles below describe only the iterations that ran.
         */
        final AtomicLong droppedIterations = new AtomicLong(0);
        /**
         * Total per-step {@link LoadCheck}s that passed / failed across the whole run, backing the
         * {@code CHECK_FAILURE_RATE} threshold's observed value (failed / (passed + failed)). Both are
         * incremented once per evaluated check on the completion callback (possibly from several worker
         * threads concurrently), so they are atomic.
         */
        final AtomicLong checksPassed = new AtomicLong(0);
        final AtomicLong checksFailed = new AtomicLong(0);
        /**
         * Per-distinct-check pass/fail aggregates keyed by (step + source + comparator + value), so the
         * end-of-run report can surface each check's individual pass/fail counts. Populated lazily on the
         * completion callback; iterated only when a status snapshot is built.
         */
        final Map<String, CheckCounters> checkAggregates = new ConcurrentHashMap<>();
        /**
         * Authoritative per-run latency histogram (milliseconds, ~3 significant digits, auto-resizing).
         * Every completed iteration's coordinated-omission-corrected latency is recorded here (successes
         * and failures alike), and the status DTO's percentiles are read from a consistent {@code copy()}
         * of it — so percentiles are correct AND available even when Prometheus metrics are disabled.
         */
        final ConcurrentHistogram latencyHistogram = new ConcurrentHistogram(3);
        final Semaphore inFlight;
        final int maxRps;

        /**
         * Latest threshold verdict for this run: {@code Boolean.TRUE} = PASS (all thresholds hold),
         * {@code Boolean.FALSE} = FAIL (any breached), {@code null} = not yet evaluated (no thresholds,
         * or no request has been dispatched). Updated on the control tick (single scheduler thread) and read
         * by {@link #snapshot}; volatile for cross-thread visibility.
         */
        volatile Boolean verdict;
        /** Per-threshold results from the latest evaluation (empty until first evaluated). */
        volatile List<ThresholdResult> thresholdResults = Collections.emptyList();
        /** Set once when an {@code abortOnFail} FAIL terminates the run, surfaced in the status DTO. */
        volatile boolean abortedByThreshold;
        /**
         * The most recent adaptive-pacing delay (milliseconds) applied at a closed-model iteration
         * reschedule, for test assertions. {@code 0} when pacing is off or the iteration overran the
         * cycle. Written on the scheduler thread, read by a test hook; volatile for visibility.
         */
        volatile long lastPacingDelayMillis;

        // Arrival-rate (RATE stage) deficit accounting; only touched on the single scheduler thread.
        double rateDeficit;
        long lastRateTickMillis;
        /**
         * The stage index the controller last drove, used to detect a stage transition on the single
         * scheduler thread so the RATE accumulator can be reset exactly once per boundary. Starts at
         * {@code -1} (no stage entered yet) so the very first stage is treated as a transition.
         */
        int activeStageIndex = -1;

        // RPS token bucket (1-second window). Synchronized on this monitor.
        private long rpsWindowStart;
        private int rpsTokensUsed;

        // Engines built lazily for rendering (Velocity/Mustache only).
        private volatile TemplateEngine velocity;
        private volatile TemplateEngine mustache;

        RunningScenario(LoadScenario scenario, Function<HttpRequest, CompletableFuture<HttpResponse>> sender, long triggeredAt) {
            this.scenario = scenario;
            this.sender = sender;
            this.triggeredAt = triggeredAt;
            this.startedAt = triggeredAt;
            this.gaugeKey = new Metrics.LoadGaugeKey(scenario.getName(), runId);
            this.inFlight = new Semaphore(Math.max(1, configuration.loadGenerationMaxInFlightRequests()));
            this.maxRps = Math.max(1, configuration.loadGenerationMaxRequestsPerSecond());
            this.rpsWindowStart = triggeredAt;
            this.latencyHistogram.setAutoResize(true);
            // Resolve the feeder dataset once at run start (parse data/format if used) so row selection
            // per iteration is a pure index/lookup with no parsing. validate() already proved the rows
            // are non-empty and the data parses, so resolvedRows() cannot throw here.
            LoadFeeder feeder = scenario.getFeeder();
            if (feeder != null) {
                List<Map<String, String>> resolved = feeder.resolvedRows();
                this.feederRows = resolved != null && !resolved.isEmpty()
                    ? Collections.unmodifiableList(new ArrayList<>(resolved))
                    : null;
                this.feederStrategy = feeder.getStrategy() != null ? feeder.getStrategy() : LoadFeeder.Strategy.CIRCULAR;
            } else {
                this.feederRows = null;
                this.feederStrategy = null;
            }
        }

        /**
         * Transition this run to RUNNING and (re)anchor its stage clock and RPS window to {@code now}.
         * Called immediately on trigger when there is no start delay, or by the control tick once a
         * PENDING run's start delay elapses, so the stage progression always measures from t=0.
         */
        void beginRunning(long now) {
            this.startedAt = now;
            this.rpsWindowStart = now;
            this.startedAtEpoch = TimeService.currentTimeMillis();
            this.state = LoadScenarioState.RUNNING;
        }

        boolean maxRequestsReached() {
            Integer max = scenario.getMaxRequests();
            return max != null && max > 0 && requestsSent.get() >= max;
        }

        /**
         * Record one evaluated per-step check outcome: bump the run totals (for CHECK_FAILURE_RATE) and
         * the per-distinct-check aggregate (for the report). Called on the completion callback, possibly
         * concurrently across worker threads.
         */
        void recordCheck(String stepLabel, LoadCheck check, boolean passed) {
            if (passed) {
                checksPassed.incrementAndGet();
            } else {
                checksFailed.incrementAndGet();
            }
            String source = check.getSource() != null ? check.getSource().name() : "";
            String comparator = check.getComparator() != null ? check.getComparator().name() : "";
            String detail = check.getSource() == LoadCheck.Source.HEADER ? check.getHeaderName()
                : check.getSource() == LoadCheck.Source.BODY_JSONPATH ? check.getJsonPath()
                : "";
            String key = stepLabel + '|' + source + '|' + (detail != null ? detail : "")
                + '|' + comparator + '|' + (check.getValue() != null ? check.getValue() : "");
            CheckCounters counters = checkAggregates.computeIfAbsent(key,
                k -> new CheckCounters(stepLabel, source, detail, comparator, check.getValue()));
            if (passed) {
                counters.passed.incrementAndGet();
            } else {
                counters.failed.incrementAndGet();
            }
        }

        /** Build the immutable per-check result list for a status snapshot (empty when no checks ran). */
        List<CheckResult> checkResults() {
            if (checkAggregates.isEmpty()) {
                return Collections.emptyList();
            }
            List<CheckResult> results = new ArrayList<>(checkAggregates.size());
            for (CheckCounters counters : checkAggregates.values()) {
                results.add(new CheckResult(counters.step, counters.source, counters.detail,
                    counters.comparator, counters.value, counters.passed.get(), counters.failed.get()));
            }
            return Collections.unmodifiableList(results);
        }

        /**
         * Evaluate this run's in-run thresholds from PER-RUN data (this run's HDR histogram and
         * counters — never the global SLO sample store), recording the verdict and per-threshold
         * results on the run. A null/empty threshold list leaves the verdict null (no change to
         * existing behaviour). The verdict is also left null until at least one request has been dispatched,
         * so a run is never failed on zero samples. Otherwise the verdict is PASS iff every threshold
         * is satisfied, FAIL if any is breached.
         *
         * @param now the orchestrator-clock time to measure elapsed throughput against
         * @return the freshly-computed verdict ({@code true}=PASS, {@code false}=FAIL, {@code null}=not
         * evaluated)
         */
        Boolean evaluateThresholds(long now) {
            List<LoadThreshold> thresholds = scenario.getThresholds();
            if (thresholds == null || thresholds.isEmpty()) {
                verdict = null;
                thresholdResults = Collections.emptyList();
                return null;
            }
            long sent = requestsSent.get();
            if (sent <= 0) {
                // No completed request yet: do not evaluate so a run is never failed on zero samples.
                return verdict;
            }
            // The HDR histogram copy is only needed for LATENCY_* thresholds; for runs whose
            // thresholds are only ERROR_RATE/THROUGHPUT_RPS, skip the per-tick copy entirely.
            boolean needLatencySnapshot = false;
            for (LoadThreshold threshold : thresholds) {
                LoadThreshold.Metric metric = threshold.getMetric();
                if (metric == LoadThreshold.Metric.LATENCY_P50
                    || metric == LoadThreshold.Metric.LATENCY_P95
                    || metric == LoadThreshold.Metric.LATENCY_P99
                    || metric == LoadThreshold.Metric.LATENCY_P999) {
                    needLatencySnapshot = true;
                    break;
                }
            }
            Histogram latencySnapshot = needLatencySnapshot ? latencyHistogram.copy() : null;
            boolean haveLatency = latencySnapshot != null && latencySnapshot.getTotalCount() > 0;
            // Error rate is a fraction of COMPLETED requests in [0,1]. requestsSent is incremented at
            // dispatch but failed/succeeded only at completion, so dividing failures by requestsSent
            // counts still-in-flight requests as if they were successes and understates the rate while
            // any request is outstanding (e.g. 2 failed / 3 dispatched = 0.666 when the 3rd is in flight,
            // even though every response is a 500). The denominator must therefore be the number of
            // requests that have actually COMPLETED — succeeded + failed — so an in-flight request never
            // dilutes the observed rate; the verdict only ever observes settled outcomes. succeeded is
            // read before failed so the denominator can never lag the numerator; clamp to [0,1] as a
            // final guard against any residual read skew across the two counters.
            long succeededCount = succeeded.get();
            long failedCount = failed.get();
            long completed = succeededCount + failedCount;
            double errorRate = completed <= 0 ? 0.0 : Math.min(1.0, failedCount / (double) completed);
            long elapsedMillis = Math.max(0L, now - startedAt);
            double throughputRps = sent / Math.max(0.001, elapsedMillis / 1000.0);
            // Check-failure rate: failed per-step checks as a fraction of all evaluated checks, in
            // [0,1]; 0 when no checks ran (so a CHECK_FAILURE_RATE threshold on a run with no checks
            // simply reports 0 and cannot spuriously FAIL).
            long checksPassedCount = checksPassed.get();
            long checksFailedCount = checksFailed.get();
            long checksTotal = checksPassedCount + checksFailedCount;
            double checkFailureRate = checksTotal <= 0 ? 0.0 : Math.min(1.0, checksFailedCount / (double) checksTotal);

            List<ThresholdResult> results = new ArrayList<>(thresholds.size());
            boolean allSatisfied = true;
            for (LoadThreshold threshold : thresholds) {
                LoadThreshold.Metric metric = threshold.getMetric();
                double observed;
                switch (metric) {
                    case LATENCY_P50:
                        observed = haveLatency ? latencySnapshot.getValueAtPercentile(50.0) : 0.0;
                        break;
                    case LATENCY_P95:
                        observed = haveLatency ? latencySnapshot.getValueAtPercentile(95.0) : 0.0;
                        break;
                    case LATENCY_P99:
                        observed = haveLatency ? latencySnapshot.getValueAtPercentile(99.0) : 0.0;
                        break;
                    case LATENCY_P999:
                        observed = haveLatency ? latencySnapshot.getValueAtPercentile(99.9) : 0.0;
                        break;
                    case ERROR_RATE:
                        observed = errorRate;
                        break;
                    case THROUGHPUT_RPS:
                        observed = throughputRps;
                        break;
                    case CHECK_FAILURE_RATE:
                        observed = checkFailureRate;
                        break;
                    default:
                        observed = 0.0;
                        break;
                }
                boolean satisfied = threshold.satisfiedBy(observed);
                allSatisfied = allSatisfied && satisfied;
                results.add(new ThresholdResult(
                    metric != null ? metric.name() : null,
                    threshold.getComparator() != null ? threshold.getComparator().name() : null,
                    threshold.getThreshold(),
                    observed,
                    satisfied));
            }
            thresholdResults = Collections.unmodifiableList(results);
            verdict = allSatisfied;
            return verdict;
        }

        /**
         * Acquire one RPS token in a fixed 1-second window, or refuse when the window is full.
         * <p>
         * The window timestamp is re-read from the orchestrator clock <em>inside</em> the lock
         * (the caller-supplied {@code now} is ignored) so the window reset is ordered by lock
         * acquisition rather than by the caller's pre-lock clock snapshot. Reading {@code now}
         * before the lock let threads enter out of timestamp order, which could reset the window
         * to a slightly earlier instant and over-issue tokens beyond {@code maxRps} across the
         * window edge; reading under the lock makes the window strictly monotonic per run.
         */
        synchronized boolean tryAcquireRpsToken(long ignoredNow) {
            long now = clock.getAsLong();
            if (now - rpsWindowStart >= 1000) {
                rpsWindowStart = now;
                rpsTokensUsed = 0;
            }
            if (rpsTokensUsed < maxRps) {
                rpsTokensUsed++;
                return true;
            }
            return false;
        }

        /** The {@code step} metric label: the step's explicit name if set, else its 0-based index. */
        String stepLabel(LoadStep step, int stepIndex) {
            if (step != null && step.getName() != null && !step.getName().isBlank()) {
                return step.getName();
            }
            return Integer.toString(stepIndex);
        }

        /**
         * The low-cardinality {@code route} label. When the step has an explicit name, the name is
         * used (operator override); otherwise the rendered request path is templatised via
         * {@link MetricLabels#routeOf(String)}.
         */
        String routeLabel(LoadStep step, HttpRequest rendered) {
            if (step != null && step.getName() != null && !step.getName().isBlank()) {
                return step.getName();
            }
            String path = rendered != null && rendered.getPath() != null ? rendered.getPath().getValue() : null;
            return MetricLabels.routeOf(path);
        }

        /**
         * Merge scenario-level and step-level custom labels (step keys win on conflict), or null when
         * neither is present.
         */
        Map<String, String> customLabelsFor(LoadStep step) {
            Map<String, String> scenarioLabels = scenario.getLabels();
            Map<String, String> stepLabels = step != null ? step.getLabels() : null;
            boolean hasScenario = scenarioLabels != null && !scenarioLabels.isEmpty();
            boolean hasStep = stepLabels != null && !stepLabels.isEmpty();
            if (!hasScenario && !hasStep) {
                return null;
            }
            Map<String, String> merged = new LinkedHashMap<>();
            if (hasScenario) {
                merged.putAll(scenarioLabels);
            }
            if (hasStep) {
                merged.putAll(stepLabels);
            }
            return merged;
        }

        HttpRequest render(HttpRequest request, IterationContext iteration) {
            if (request == null) {
                return null;
            }
            TemplateEngine engine = engineFor(scenario.getTemplateType());
            HttpRequest clone = request.clone();
            String path = request.getPath() != null ? request.getPath().getValue() : null;
            if (path != null && containsTemplate(path)) {
                clone.withPath(engine.renderTemplate(path, request, iteration));
            }
            String body = request.getBodyAsString();
            if (body != null && containsTemplate(body)) {
                clone.withBody(engine.renderTemplate(body, request, iteration));
            }
            renderHeaders(request, clone, engine, iteration);
            return clone;
        }

        /**
         * Render any header value that contains a template placeholder, in place on the clone. This is
         * what makes a templated auth header such as {@code Authorization: Bearer
         * {{iteration.captured.token}}} resolve against the per-iteration captured map. Headers whose
         * values contain no placeholder are left untouched. The template context is the ORIGINAL request
         * (as for path/body). Best-effort: a header is rebuilt only when at least one of its values is a
         * template, preserving the not/optional flags of names and untemplated values.
         */
        private void renderHeaders(HttpRequest request, HttpRequest clone, TemplateEngine engine, IterationContext iteration) {
            if (request.getHeaders() == null || request.getHeaderList().isEmpty()) {
                return;
            }
            for (org.mockserver.model.Header header : request.getHeaderList()) {
                boolean anyTemplated = false;
                List<org.mockserver.model.NottableString> originalValues = header.getValues();
                if (originalValues != null) {
                    for (org.mockserver.model.NottableString value : originalValues) {
                        if (value != null && containsTemplate(value.getValue())) {
                            anyTemplated = true;
                            break;
                        }
                    }
                }
                if (!anyTemplated) {
                    continue;
                }
                List<org.mockserver.model.NottableString> renderedValues = new ArrayList<>();
                for (org.mockserver.model.NottableString value : originalValues) {
                    if (value != null && containsTemplate(value.getValue())) {
                        String rendered = engine.renderTemplate(value.getValue(), request, iteration);
                        renderedValues.add(org.mockserver.model.NottableString.string(rendered, value.isNot()));
                    } else {
                        renderedValues.add(value);
                    }
                }
                // Replace the cloned header (same name) with the rendered values. replace-by-name keeps
                // header ordering stable and overwrites the cloned copy carried over from request.clone().
                clone.replaceHeader(new org.mockserver.model.Header(
                    header.getName(), renderedValues.toArray(new org.mockserver.model.NottableString[0])));
            }
        }

        private boolean containsTemplate(String value) {
            return value != null && (value.contains("$") || value.contains("{{") || value.contains("#"));
        }

        private TemplateEngine engineFor(HttpTemplate.TemplateType type) {
            if (type == HttpTemplate.TemplateType.MUSTACHE) {
                if (mustache == null) {
                    mustache = new MustacheTemplateEngine(new org.mockserver.logging.MockServerLogger(), configuration);
                }
                return mustache;
            }
            if (velocity == null) {
                velocity = new VelocityTemplateEngine(new org.mockserver.logging.MockServerLogger(), configuration);
            }
            return velocity;
        }

        LoadScenarioStatus snapshot(LoadScenarioState state, long now) {
            boolean running = state == LoadScenarioState.RUNNING;
            boolean terminal = state == LoadScenarioState.COMPLETED || state == LoadScenarioState.STOPPED;
            // PENDING reports the trigger time as the reference; RUNNING/terminal report the stage clock.
            long elapsed = running || terminal ? now - startedAt : 0;
            int currentVus = running ? activeVUs.get() : 0;
            int idx = stageIndex.get();
            List<LoadStage> stages = scenario.getProfile().getStages();
            LoadStageType stageType = idx >= 0 && idx < stages.size() ? stages.get(idx).getType() : null;
            double targetRateValue = Double.longBitsToDouble(targetRate.get());
            double currentTarget = running
                ? (stageType == LoadStageType.RATE ? targetRateValue : targetVUs.get())
                : 0.0;
            // Percentiles are read from a consistent copy() of the authoritative per-run HDR histogram
            // (not the coarse Prometheus buckets), so they are correct AND available even when metrics
            // are disabled. An empty histogram reports 0 for every percentile.
            Histogram latencySnapshot = latencyHistogram.copy();
            long p50 = latencySnapshot.getTotalCount() == 0 ? 0L : latencySnapshot.getValueAtPercentile(50.0);
            long p95 = latencySnapshot.getTotalCount() == 0 ? 0L : latencySnapshot.getValueAtPercentile(95.0);
            long p99 = latencySnapshot.getTotalCount() == 0 ? 0L : latencySnapshot.getValueAtPercentile(99.0);
            long p999 = latencySnapshot.getTotalCount() == 0 ? 0L : latencySnapshot.getValueAtPercentile(99.9);
            return new LoadScenarioStatus(
                scenario.getName(),
                state,
                elapsed,
                currentVus,
                requestsSent.get(),
                succeeded.get(),
                failed.get(),
                p50,
                p95,
                p99,
                p999,
                droppedIterations.get(),
                runId,
                startedAtEpoch,
                terminal ? now : null,
                scenario.getLabels() != null && !scenario.getLabels().isEmpty()
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(scenario.getLabels()))
                    : null,
                scenario,
                running ? idx : -1,
                running && stageType != null ? stageType.name() : null,
                currentTarget,
                scenario.getStartDelayMillis(),
                verdict == null ? null : (verdict ? "PASS" : "FAIL"),
                abortedByThreshold,
                thresholdResults,
                checkResults()
            );
        }
    }

    /**
     * One per-threshold evaluation result, surfaced in the status DTO so a client/dashboard can show
     * which thresholds passed or breached and the observed value behind the verdict.
     */
    public static final class ThresholdResult {
        /** The {@link LoadThreshold.Metric} name (e.g. {@code LATENCY_P95}). */
        public final String metric;
        /** The {@link org.mockserver.slo.SloObjective.Comparator} name (e.g. {@code LESS_THAN}). */
        public final String comparator;
        /** The configured threshold value. */
        public final double threshold;
        /** The observed per-run value at evaluation time (latency ms, error-rate fraction, or rps). */
        public final double observed;
        /** True when {@code observed} satisfied the comparator against {@code threshold}. */
        public final boolean satisfied;

        public ThresholdResult(String metric, String comparator, double threshold, double observed, boolean satisfied) {
            this.metric = metric;
            this.comparator = comparator;
            this.threshold = threshold;
            this.observed = observed;
            this.satisfied = satisfied;
        }
    }

    /** Mutable per-distinct-check pass/fail aggregate, keyed in {@link RunningScenario#checkAggregates}. */
    private static final class CheckCounters {
        final String step;
        final String source;
        final String detail;
        final String comparator;
        final String value;
        final AtomicLong passed = new AtomicLong(0);
        final AtomicLong failed = new AtomicLong(0);

        CheckCounters(String step, String source, String detail, String comparator, String value) {
            this.step = step;
            this.source = source;
            this.detail = detail;
            this.comparator = comparator;
            this.value = value;
        }
    }

    /**
     * One per-distinct-check aggregate result, surfaced in the status DTO and end-of-run report so a
     * client/dashboard/CI can see how many times each per-step {@link LoadCheck} passed and failed.
     */
    public static final class CheckResult {
        /** The step label the check ran on (step name or index). */
        public final String step;
        /** The {@link LoadCheck.Source} name (e.g. {@code STATUS}). */
        public final String source;
        /** The header name (HEADER) or JSONPath (BODY_JSONPATH), or empty for STATUS. */
        public final String detail;
        /** The {@link LoadCheck.Comparator} name (e.g. {@code EQUALS}). */
        public final String comparator;
        /** The expected comparand value. */
        public final String value;
        /** Number of times this check passed. */
        public final long passed;
        /** Number of times this check failed. */
        public final long failed;

        public CheckResult(String step, String source, String detail, String comparator, String value,
                           long passed, long failed) {
            this.step = step;
            this.source = source;
            this.detail = detail;
            this.comparator = comparator;
            this.value = value;
            this.passed = passed;
            this.failed = failed;
        }
    }

    /** Immutable snapshot of a load scenario's progress, serialized by the GET endpoint. */
    public static final class LoadScenarioStatus {
        public final String name;
        /** Lifecycle state of this run (PENDING/RUNNING/COMPLETED/STOPPED). */
        public final LoadScenarioState state;
        public final long elapsedMillis;
        public final int currentVus;
        public final long requestsSent;
        public final long succeeded;
        public final long failed;
        public final long p50Millis;
        public final long p95Millis;
        public final long p99Millis;
        /**
         * The 99.9th-percentile coordinated-omission-corrected latency in milliseconds, read from the
         * per-run HDR histogram (0 when no iteration has completed). Surfaces deep-tail behaviour that
         * p99 alone hides.
         */
        public final long p999Millis;
        /**
         * Iterations that were due but never dispatched because a safety cap was hit (the sum of the
         * {@code rate_limit} and {@code inflight_cap} throttles for this run). The percentiles describe
         * only the iterations that ran, so a non-zero value here is the truncated-distribution signal —
         * the system-under-test could not keep up with the requested load.
         */
        public final long droppedIterations;
        public final String runId;
        public final long startedAtEpochMillis;
        public final Long endedAtEpochMillis;
        /** Scenario-level custom annotation labels (null when none), echoed for dashboards/clients. */
        public final Map<String, String> labels;
        /**
         * The full scenario definition this run was started with (never null for a real run). The GET
         * endpoint serializes it under {@code definition} so any client/dashboard can load the exact
         * {@link LoadScenario} back into an author form and round-trip it as a PUT body.
         */
        public final LoadScenario scenario;
        /** The 0-based index of the currently-running stage, or {@code -1} when not running. */
        public final int stageIndex;
        /** The type of the currently-running stage ({@code VU}/{@code RATE}/{@code PAUSE}), or null. */
        public final String stageType;
        /**
         * The current setpoint for the running stage: target VUs for a VU stage, target arrival rate
         * (iterations/second) for a RATE stage, {@code 0} for PAUSE or when not running.
         */
        public final double currentTarget;
        /** The configured start delay in milliseconds (0 when none). */
        public final long startDelayMillis;
        /**
         * In-run threshold verdict: {@code "PASS"} (all thresholds satisfied), {@code "FAIL"} (any
         * breached), or {@code null} when the scenario has no thresholds or none has been evaluated yet
         * (no request has been dispatched). A terminal {@code "FAIL"} should be mapped by clients to a
         * non-zero CI exit code.
         */
        public final String verdict;
        /** True when this run was terminated early by an {@code abortOnFail} threshold breach. */
        public final boolean abortedByThreshold;
        /** Per-threshold results behind the {@link #verdict} (empty when no thresholds / not evaluated). */
        public final List<ThresholdResult> thresholdResults;
        /**
         * Per-distinct-check pass/fail aggregates for the run's per-step {@link LoadCheck}s (empty when
         * no checks ran). Surfaced in the end-of-run report so a CI/dashboard sees which response
         * assertions held.
         */
        public final List<CheckResult> checkResults;

        public LoadScenarioStatus(String name, LoadScenarioState state, long elapsedMillis, int currentVus,
                                  long requestsSent, long succeeded, long failed,
                                  long p50Millis, long p95Millis, long p99Millis, long p999Millis,
                                  long droppedIterations,
                                  String runId, long startedAtEpochMillis, Long endedAtEpochMillis,
                                  Map<String, String> labels, LoadScenario scenario,
                                  int stageIndex, String stageType, double currentTarget,
                                  long startDelayMillis,
                                  String verdict, boolean abortedByThreshold,
                                  List<ThresholdResult> thresholdResults,
                                  List<CheckResult> checkResults) {
            this.name = name;
            this.state = state;
            this.elapsedMillis = elapsedMillis;
            this.currentVus = currentVus;
            this.requestsSent = requestsSent;
            this.succeeded = succeeded;
            this.failed = failed;
            this.p50Millis = p50Millis;
            this.p95Millis = p95Millis;
            this.p99Millis = p99Millis;
            this.p999Millis = p999Millis;
            this.droppedIterations = droppedIterations;
            this.runId = runId;
            this.startedAtEpochMillis = startedAtEpochMillis;
            this.endedAtEpochMillis = endedAtEpochMillis;
            this.labels = labels;
            this.scenario = scenario;
            this.stageIndex = stageIndex;
            this.stageType = stageType;
            this.currentTarget = currentTarget;
            this.startDelayMillis = startDelayMillis;
            this.verdict = verdict;
            this.abortedByThreshold = abortedByThreshold;
            this.thresholdResults = thresholdResults != null ? thresholdResults : Collections.emptyList();
            this.checkResults = checkResults != null ? checkResults : Collections.emptyList();
        }
    }
}
