package org.mockserver.mock.action.http;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.load.LoadProfile;
import org.mockserver.load.LoadScenario;
import org.mockserver.load.LoadStage;
import org.mockserver.load.LoadStep;
import org.mockserver.load.RampCurve;
import org.mockserver.metrics.Metrics;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.slo.SloSampleStore;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Tests for {@link LoadScenarioOrchestrator}. Uses a deterministic mutable clock and a
 * synchronous fake sender (every request returns an already-completed future), so assertions
 * about request volume and templated variation are deterministic without wall-clock sleeps.
 * Where a count must be reached, a bounded poll on the sender's recorded queue is used.
 */
public class LoadScenarioOrchestratorTest {

    private AtomicLong clock;
    private ScheduledExecutorService scheduler;
    private LoadScenarioOrchestrator orchestrator;

    private boolean originalEnabled;
    private int originalMaxVus;
    private long originalMaxDuration;
    private int originalMaxSteps;
    private int originalMaxConcurrent;
    private boolean originalSloTracking;
    private int originalMaxInFlight;

    @Before
    public void setUp() {
        clock = new AtomicLong(10_000L);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "test-load-scenario-scheduler");
            t.setDaemon(true);
            return t;
        });
        orchestrator = new LoadScenarioOrchestrator(clock::get, scheduler);
        Metrics.resetAdditionalMetricsForTesting();
        SloSampleStore.getInstance().reset();

        originalEnabled = ConfigurationProperties.loadGenerationEnabled();
        originalMaxVus = ConfigurationProperties.loadGenerationMaxVirtualUsers();
        originalMaxDuration = ConfigurationProperties.loadGenerationMaxDurationMillis();
        originalMaxSteps = ConfigurationProperties.loadGenerationMaxSteps();
        originalMaxConcurrent = ConfigurationProperties.loadGenerationMaxConcurrentScenarios();
        originalSloTracking = ConfigurationProperties.sloTrackingEnabled();
        originalMaxInFlight = ConfigurationProperties.loadGenerationMaxInFlightRequests();
    }

    @After
    public void tearDown() {
        orchestrator.reset();
        scheduler.shutdownNow();
        Metrics.resetAdditionalMetricsForTesting();
        SloSampleStore.getInstance().reset();
        ConfigurationProperties.loadGenerationEnabled(originalEnabled);
        ConfigurationProperties.loadGenerationMaxVirtualUsers(originalMaxVus);
        ConfigurationProperties.loadGenerationMaxDurationMillis(originalMaxDuration);
        ConfigurationProperties.loadGenerationMaxSteps(originalMaxSteps);
        ConfigurationProperties.loadGenerationMaxConcurrentScenarios(originalMaxConcurrent);
        ConfigurationProperties.sloTrackingEnabled(originalSloTracking);
        ConfigurationProperties.loadGenerationMaxInFlightRequests(originalMaxInFlight);
    }

    /** A synchronous fake sender that records every request and returns a 200 immediately. */
    private static final class RecordingSender implements Function<HttpRequest, CompletableFuture<HttpResponse>> {
        final ConcurrentLinkedQueue<HttpRequest> sent = new ConcurrentLinkedQueue<>();

        @Override
        public CompletableFuture<HttpResponse> apply(HttpRequest httpRequest) {
            sent.add(httpRequest);
            return CompletableFuture.completedFuture(response().withStatusCode(200));
        }
    }

    /**
     * A fake sender that completes each request's future after a fixed real delay, on a shared
     * scheduled executor, so the orchestrator observes a genuine non-zero request lifetime. Used to
     * prove the coordinated-omission-corrected latency captures the full in-flight time.
     */
    private static final class DelayingSender implements Function<HttpRequest, CompletableFuture<HttpResponse>> {
        final ConcurrentLinkedQueue<HttpRequest> sent = new ConcurrentLinkedQueue<>();
        private final long delayMillis;
        private final ScheduledExecutorService completer = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "test-delaying-sender");
            t.setDaemon(true);
            return t;
        });

        DelayingSender(long delayMillis) {
            this.delayMillis = delayMillis;
        }

        @Override
        public CompletableFuture<HttpResponse> apply(HttpRequest httpRequest) {
            sent.add(httpRequest);
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            completer.schedule(() -> future.complete(response().withStatusCode(200)), delayMillis, TimeUnit.MILLISECONDS);
            return future;
        }

        void shutdown() {
            completer.shutdownNow();
        }
    }

    private boolean awaitAtLeast(RecordingSender sender, int n, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (sender.sent.size() >= n) {
                return true;
            }
            Thread.sleep(5);
        }
        return sender.sent.size() >= n;
    }

    /** Poll until the most recent run's live active-VU count reaches the expected value (or timeout). */
    private boolean awaitLastRunActiveVus(int expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (orchestrator.lastRunActiveVuCount() == expected) {
                return true;
            }
            Thread.sleep(5);
        }
        return orchestrator.lastRunActiveVuCount() == expected;
    }

    @Test
    public void constantProfileDrivesRequestsUntilMaxRequests() throws Exception {
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("constant")
            .withMaxRequests(25)
            .withProfile(LoadProfile.constant(3, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        String error = orchestrator.start(scenario, sender);
        assertThat(error, is(nullValue()));

        assertThat("scenario should reach maxRequests", awaitAtLeast(sender, 25, 5_000L), is(true));
        // The cap is enforced at dispatch time, so the count overshoots only by roughly the number
        // of in-flight VUs (3), not by a full control interval of firing — it must not run forever.
        long deadline = System.currentTimeMillis() + 500L;
        while (orchestrator.getStatus() != null && org.mockserver.load.LoadScenarioState.RUNNING == orchestrator.getStatus().state
            && System.currentTimeMillis() < deadline) {
            orchestrator.tickNow();
            Thread.sleep(5);
        }
        assertThat(sender.sent.size(), greaterThanOrEqualTo(25));
        assertThat(sender.sent.size(), lessThan(60));
    }

    @Test
    public void linearProfileRampsTargetVusOverTime() throws Exception {
        // Pure ramp-shape assertion via the stage (deterministic, no traffic needed).
        LoadStage linear = LoadStage.rampVus(0, 10, 1000L, RampCurve.LINEAR);
        assertThat(linear.targetVusAt(0), is(0));
        assertThat(linear.targetVusAt(500), is(5));
        assertThat(linear.targetVusAt(1000), is(10));
        assertThat(linear.targetVusAt(2000), is(10)); // clamped at end

        // And via the orchestrator's live active-VU count driven by tickNow + the mutable clock.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("linear")
            .withProfile(LoadProfile.linear(0, 8, 1000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));
        assertThat(orchestrator.start(scenario, sender), is(nullValue()));

        clock.set(10_000L + 500L);
        orchestrator.tickNow();
        assertThat("active VUs ramp up to the t=500 target of 4",
            awaitLastRunActiveVus(4, 5_000L), is(true));

        // Just before the duration boundary the ramp is essentially at endVus.
        clock.set(10_000L + 999L);
        orchestrator.tickNow();
        assertThat("active VUs ramp up to the near-end target of 8",
            awaitLastRunActiveVus(8, 5_000L), is(true));
    }

    @Test
    public void stagesRunInSequenceVuThenPauseThenRate() throws Exception {
        // VU(2 VUs, 400ms) -> PAUSE(200ms) -> RATE(20/s, 500ms). Assert the stage index advances in
        // order, the pause drains VUs, and the run terminates after the last stage.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("sequenced")
            .withProfile(LoadProfile.of(
                LoadStage.constantVus(2, 400L),
                LoadStage.pause(200L),
                LoadStage.constantRate(20.0, 500L)))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));

        // Stage 0 (VU): two VUs spin up.
        assertThat("VU stage spins up 2 VUs", awaitLastRunActiveVus(2, 5_000L), is(true));
        assertThat(orchestrator.getStatus().stageIndex, is(0));
        assertThat(orchestrator.getStatus().stageType, is("VU"));

        // Advance into the PAUSE stage (after 400ms): VUs drain to zero.
        clock.set(10_000L + 500L);
        orchestrator.tickNow();
        assertThat("pause stage drains VUs", awaitLastRunActiveVus(0, 5_000L), is(true));
        assertThat(orchestrator.getStatus().stageIndex, is(1));
        assertThat(orchestrator.getStatus().stageType, is("PAUSE"));

        // Advance into the RATE stage (after 600ms): the open-model scheduler reports a RATE stage.
        clock.set(10_000L + 700L);
        orchestrator.tickNow();
        assertThat(orchestrator.getStatus().stageIndex, is(2));
        assertThat(orchestrator.getStatus().stageType, is("RATE"));

        // Advance past the last stage (total 1100ms): the run terminates.
        clock.set(10_000L + 1_200L);
        orchestrator.tickNow();
        long deadline = System.currentTimeMillis() + 2_000L;
        while (orchestrator.getStatus() != null && org.mockserver.load.LoadScenarioState.RUNNING == orchestrator.getStatus().state
            && System.currentTimeMillis() < deadline) {
            orchestrator.tickNow();
            Thread.sleep(5);
        }
        assertThat(orchestrator.getStatus().state, is(org.mockserver.load.LoadScenarioState.COMPLETED));
    }

    @Test
    public void rateStageAchievesTargetArrivalRate() throws Exception {
        // A constant 50 iterations/sec rate over ~1s should start ~50 iterations (one request per
        // iteration here), within tolerance. Drive the clock forward in fixed steps and tick so the
        // deficit accumulator integrates the rate deterministically.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("rate")
            .withProfile(LoadProfile.constantRate(50.0, 2_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        // Step the clock in 100ms increments over 1 second, ticking each time (50/s * 1s = ~50).
        for (int i = 1; i <= 10; i++) {
            clock.set(10_000L + i * 100L);
            orchestrator.tickNow();
            Thread.sleep(20); // let one-shot iterations dispatch their single step
        }
        long sent = sender.sent.size();
        assertThat("≈50 iterations started over 1s at 50/s", sent, greaterThanOrEqualTo(40L));
        assertThat(sent, lessThanOrEqualTo(60L));
    }

    @Test
    public void rateStageAfterPauseDoesNotBurstOnFirstTick() throws Exception {
        // RATE 10/s -> PAUSE 5s -> RATE 10/s. The accumulator state is per-run; before the fix the
        // second RATE stage's first integrating tick computed dt across the ENTIRE pause and added
        // rate*gap iterations at once (~50+), bursting. After the fix each RATE stage integrates only
        // its own elapsed time, so the second stage's FIRST tick starts ~rate*tick/1000 (~1 at 10/s,
        // 100ms tick), and the whole second stage totals ~rate*duration with no surplus.
        RecordingSender sender = new RecordingSender();
        long base = 10_000L;
        LoadScenario scenario = new LoadScenario()
            .withName("rate-pause-rate")
            .withProfile(LoadProfile.of(
                LoadStage.constantRate(10.0, 1_000L),   // stage 0: 0..1000ms
                LoadStage.pause(5_000L),                // stage 1: 1000..6000ms
                LoadStage.constantRate(10.0, 1_000L)))  // stage 2: 6000..7000ms
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));

        // Drive stage 0 (first RATE): 0..1000ms in 100ms ticks. ~10 iterations.
        for (int i = 1; i <= 10; i++) {
            clock.set(base + i * 100L);
            orchestrator.tickNow();
            Thread.sleep(20);
        }
        long afterFirstRate = sender.sent.size();
        assertThat("first RATE stage achieves ~10 iterations over 1s",
            afterFirstRate, allOf(greaterThanOrEqualTo(7L), lessThanOrEqualTo(13L)));

        // Drive the PAUSE stage: 1000..6000ms. No load.
        for (int i = 11; i <= 60; i++) {
            clock.set(base + i * 100L);
            orchestrator.tickNow();
        }
        Thread.sleep(20);
        long afterPause = sender.sent.size();
        assertThat("pause stage drives no load", afterPause - afterFirstRate, is(0L));

        // First tick of stage 2 (second RATE), at elapsed 6100ms. This is the "first integrating tick"
        // of the second RATE stage: after the reset it re-initialises (no integration) on the boundary
        // tick at 6000ms and integrates only one 100ms step here, so it must start ~1 iteration, NOT
        // rate*gap (~50). The boundary tick (6000ms) is the initialising tick.
        clock.set(base + 6_000L);
        orchestrator.tickNow();
        Thread.sleep(20);
        long atSecondRateBoundary = sender.sent.size();
        assertThat("the boundary tick of the second RATE stage initialises and does not integrate",
            atSecondRateBoundary - afterPause, is(0L));

        clock.set(base + 6_100L);
        orchestrator.tickNow();
        Thread.sleep(20);
        long afterFirstIntegratingTick = sender.sent.size();
        long firstTickIterations = afterFirstIntegratingTick - atSecondRateBoundary;
        assertThat("second RATE stage's first integrating tick starts ~1 iteration (rate*tick), not a burst",
            firstTickIterations, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(3L)));

        // Drive the remainder of stage 2: 6200..7000ms. Total over the second RATE stage ~= 10.
        for (int i = 62; i <= 70; i++) {
            clock.set(base + i * 100L);
            orchestrator.tickNow();
            Thread.sleep(20);
        }
        long secondRateTotal = sender.sent.size() - afterPause;
        assertThat("second RATE stage totals ~rate*duration with no burst surplus",
            secondRateTotal, allOf(greaterThanOrEqualTo(7L), lessThanOrEqualTo(13L)));
    }

    @Test
    public void rateStageAfterVuStageDoesNotBurstOnFirstTick() throws Exception {
        // RATE 10/s -> VU(2, 3s) -> RATE 10/s. Same no-burst property across a VU stage in between:
        // before the fix the second RATE stage's first integrating tick spanned the whole VU stage and
        // bursted; after the fix it integrates only its own elapsed time.
        //
        // The step carries a very large think time so the closed-loop VUs fire roughly one request then
        // park for far longer than the whole test (they never re-loop and flood the recorder). Think time
        // is inter-step pacing applied AFTER the request is sent, so it does not delay or reduce the RATE
        // one-shots' single request — only the VU loops are silenced, keeping the stage-2 RATE count a
        // clean signal instead of being swamped by unbounded zero-think VU looping.
        RecordingSender sender = new RecordingSender();
        long base = 10_000L;
        LoadScenario scenario = new LoadScenario()
            .withName("rate-vu-rate")
            .withProfile(LoadProfile.of(
                LoadStage.constantRate(10.0, 1_000L),   // stage 0: 0..1000ms
                LoadStage.constantVus(2, 3_000L),       // stage 1: 1000..4000ms
                LoadStage.constantRate(10.0, 1_000L)))  // stage 2: 4000..5000ms
            .withSteps(new LoadStep()
                .withRequest(request().withPath("/api").withHeader("Host", "target"))
                .withThinkTime(org.mockserver.model.Delay.seconds(60)));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));

        // Stage 0 (first RATE): 0..1000ms.
        for (int i = 1; i <= 10; i++) {
            clock.set(base + i * 100L);
            orchestrator.tickNow();
            Thread.sleep(20);
        }
        // Move through the VU stage (1100..3900ms). Each VU fires ~one request then parks (60s think).
        for (int i = 11; i <= 39; i++) {
            clock.set(base + i * 100L);
            orchestrator.tickNow();
            Thread.sleep(5);
        }

        // Boundary tick of stage 2 (4000ms): the RATE accumulator was reset on entry, so this tick
        // initialises and does not integrate.
        clock.set(base + 4_000L);
        orchestrator.tickNow();
        Thread.sleep(20);
        long atBoundary = sender.sent.size();

        // First integrating tick of stage 2 (4100ms): integrates only one 100ms step (~1 iteration),
        // NOT rate * the 3s VU stage (~30). This is the regression: pre-fix dtMillis spanned the whole
        // intervening VU stage and added rate*3s to the deficit on this one tick.
        clock.set(base + 4_100L);
        orchestrator.tickNow();
        Thread.sleep(20);
        long firstTickIterations = sender.sent.size() - atBoundary;
        assertThat("second RATE stage's first integrating tick after a VU stage starts ~1 iteration, not a burst",
            firstTickIterations, allOf(greaterThanOrEqualTo(0L), lessThanOrEqualTo(3L)));

        // Remainder of stage 2: 4200..5000ms. The RATE stage contribution totals ~rate*duration (~10).
        for (int i = 42; i <= 50; i++) {
            clock.set(base + i * 100L);
            orchestrator.tickNow();
            Thread.sleep(20);
        }
        long secondRateContribution = sender.sent.size() - atBoundary;
        assertThat("the second RATE stage contributes ~rate*duration with no burst surplus",
            secondRateContribution, allOf(greaterThanOrEqualTo(7L), lessThanOrEqualTo(14L)));
    }

    @Test
    public void rampRateStageIncreasesOverTime() throws Exception {
        // A 0->100/s linear ramp over 1s: more iterations are started in the second half than the
        // first half (the rate genuinely rises).
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("ramp-rate")
            .withProfile(LoadProfile.of(LoadStage.rampRate(0.0, 100.0, 1_000L, RampCurve.LINEAR)))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        // First half (0..500ms).
        for (int i = 1; i <= 5; i++) {
            clock.set(10_000L + i * 100L);
            orchestrator.tickNow();
            Thread.sleep(20);
        }
        long firstHalf = sender.sent.size();
        // Second half (500..1000ms).
        for (int i = 6; i <= 10; i++) {
            clock.set(10_000L + i * 100L);
            orchestrator.tickNow();
            Thread.sleep(20);
        }
        long secondHalf = sender.sent.size() - firstHalf;
        assertThat("the ramping rate starts more iterations in the second half than the first",
            secondHalf, greaterThan(firstHalf));
    }

    @Test
    public void pauseStageProducesNoRequests() throws Exception {
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("pause-only")
            .withProfile(LoadProfile.of(LoadStage.pause(500L), LoadStage.constantVus(1, 500L)))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        // Tick within the first (PAUSE) stage only: no requests should be produced.
        clock.set(10_000L + 200L);
        orchestrator.tickNow();
        Thread.sleep(100);
        clock.set(10_000L + 400L);
        orchestrator.tickNow();
        Thread.sleep(100);
        assertThat("pause stage drives no load", sender.sent.size(), is(0));
    }

    @Test
    public void rejectsRateStageExceedingMaxRate() {
        LoadScenario scenario = new LoadScenario()
            .withName("too-fast")
            .withProfile(LoadProfile.constantRate(1_000_000.0, 1_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        String error = orchestrator.start(scenario, new RecordingSender());
        assertThat(error, containsString("exceeding the maximum of"));
    }

    @Test
    public void rejectsRampStageMissingEndpoints() {
        LoadScenario scenario = new LoadScenario()
            .withName("bad-ramp")
            .withProfile(LoadProfile.of(new LoadStage().withType(org.mockserver.load.LoadStageType.VU)
                .withDurationMillis(1_000L).withStartVus(1))) // endVus missing -> treated as a hold without vus
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        String error = orchestrator.start(scenario, new RecordingSender());
        assertThat(error, containsString("VU stage requires"));
    }

    @Test
    public void rejectsStageWithZeroDuration() {
        LoadScenario scenario = new LoadScenario()
            .withName("zero-duration")
            .withProfile(LoadProfile.of(LoadStage.constantVus(1, 0L)))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        String error = orchestrator.start(scenario, new RecordingSender());
        assertThat(error, containsString("durationMillis must be > 0"));
    }

    @Test
    public void perIterationTemplatingProducesDistinctPaths() throws Exception {
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("templated")
            .withTemplateType(HttpTemplate.TemplateType.VELOCITY)
            .withMaxRequests(5)
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withSteps(new LoadStep().withRequest(
                request().withPath("/item/$iteration.index").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 5, 5_000L), is(true));

        Set<String> distinctPaths = sender.sent.stream()
            .map(r -> r.getPath().getValue())
            .collect(java.util.stream.Collectors.toSet());
        // $iteration.index varies per global iteration, so paths must differ.
        assertThat(distinctPaths.size(), greaterThan(1));
        assertThat(distinctPaths, hasItem("/item/0"));
    }

    @Test
    public void recordsSamplesIntoSloStoreWhenTrackingEnabled() throws Exception {
        ConfigurationProperties.sloTrackingEnabled(true);
        SloSampleStore.getInstance().reset();
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("slo-feed")
            .withMaxRequests(10)
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 10, 5_000L), is(true));
        // give the whenComplete callbacks a moment to flush into the store
        long deadline = System.currentTimeMillis() + 2_000L;
        while (SloSampleStore.getInstance().size() < 10 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertThat(SloSampleStore.getInstance().size(), greaterThanOrEqualTo(10));
    }

    @Test
    public void rejectsScenarioExceedingVuCap() {
        ConfigurationProperties.loadGenerationMaxVirtualUsers(5);
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("too-many-vus")
            .withProfile(LoadProfile.constant(50, 1000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        String error = orchestrator.start(scenario, new RecordingSender());
        assertThat(error, containsString("exceeding the maximum of 5"));
    }

    @Test
    public void rejectsScenarioExceedingStepCap() {
        ConfigurationProperties.loadGenerationMaxSteps(2);
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("too-many-steps")
            .withProfile(LoadProfile.constant(1, 1000L))
            .withSteps(
                new LoadStep().withRequest(request().withPath("/a")),
                new LoadStep().withRequest(request().withPath("/b")),
                new LoadStep().withRequest(request().withPath("/c")));

        String error = orchestrator.start(scenario, new RecordingSender());
        assertThat(error, containsString("exceeds the maximum of 2"));
    }

    @Test
    public void rejectsMissingProfileAndSteps() {
        assertThat(orchestrator.start(new LoadScenario().withName("x").withProfile(LoadProfile.constant(1, 1000L)),
            new RecordingSender()), containsString("steps"));
        assertThat(orchestrator.start(new LoadScenario().withName("x")
                .withSteps(new LoadStep().withRequest(request().withPath("/a"))),
            new RecordingSender()), containsString("profile"));
    }

    @Test
    public void activeVuCountReturnsToZeroAfterStop() throws Exception {
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("drain-on-stop")
            .withProfile(LoadProfile.constant(4, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        // Let the VUs spin up and issue some traffic so loops are genuinely in flight.
        assertThat("scenario should produce traffic", awaitAtLeast(sender, 20, 5_000L), is(true));
        assertThat("active VUs should have ramped up", orchestrator.activeVuCount(), is(greaterThan(0)));

        orchestrator.stop();

        // Every launched VU loop must release its slot exactly once as it observes the stopped state,
        // so the live active-VU count drains back to zero (no leak, no double-count to a negative).
        assertThat("active VU count should drain to zero after stop",
            awaitLastRunActiveVus(0, 5_000L), is(true));
        assertThat(orchestrator.lastRunActiveVuCount(), is(0));
    }

    @Test
    public void activeVuCountReturnsToZeroAfterCompletion() throws Exception {
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("drain-on-complete")
            .withMaxRequests(30)
            .withProfile(LoadProfile.constant(4, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat("scenario should reach maxRequests", awaitAtLeast(sender, 30, 5_000L), is(true));

        // Drive ticks so completion is observed, then the in-flight VU loops drain their slots.
        long deadline = System.currentTimeMillis() + 2_000L;
        while (orchestrator.getStatus() != null && org.mockserver.load.LoadScenarioState.RUNNING == orchestrator.getStatus().state
            && System.currentTimeMillis() < deadline) {
            orchestrator.tickNow();
            Thread.sleep(5);
        }
        assertThat(orchestrator.getStatus().state, is(org.mockserver.load.LoadScenarioState.COMPLETED));
        assertThat("active VU count should drain to zero after completion",
            awaitLastRunActiveVus(0, 5_000L), is(true));
        assertThat(orchestrator.lastRunActiveVuCount(), is(0));
    }

    @Test
    public void activeVuCountRampsDownToTargetWithoutUnderflow() throws Exception {
        // Ramp up to a high concurrency, then drop the target and assert the live population settles
        // exactly at the new target (surplus VUs retire exactly once each; concurrent retirement
        // never collapses below the target).
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("ramp-down")
            .withProfile(LoadProfile.linear(6, 2, 1_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        // At t=0 the linear profile starts at 6 VUs.
        assertThat("active VUs should ramp up to the start target",
            awaitLastRunActiveVus(6, 5_000L), is(true));

        // Advance the clock so the linear ramp's target falls to 2, then tick to apply the setpoint
        // and let surplus VUs retire at their iteration boundaries.
        clock.set(10_000L + 1_000L - 1L);
        orchestrator.tickNow();
        assertThat("surplus VUs should retire down to exactly the target",
            awaitLastRunActiveVus(2, 5_000L), is(true));
        assertThat("retirement must not underflow below the target",
            orchestrator.lastRunActiveVuCount(), is(2));
    }

    @Test
    public void getStatusReportsTerminalStateAfterStop() throws Exception {
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("stoppable")
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));
        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        orchestrator.stop();
        assertThat(orchestrator.getStatus(), is(notNullValue()));
        assertThat(orchestrator.getStatus().state, is(org.mockserver.load.LoadScenarioState.STOPPED));
    }

    @Test
    public void startDelayKeepsScenarioPendingUntilDelayElapses() throws Exception {
        // A scenario with startDelayMillis>0 must sit PENDING and fire NO requests until the delay
        // elapses (advance the test clock), then RUN.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("delayed")
            .withStartDelayMillis(500L)
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        // Immediately after trigger: PENDING, no traffic.
        assertThat(orchestrator.getStatus().state, is(org.mockserver.load.LoadScenarioState.PENDING));

        // Tick while still inside the delay window (t=300ms < 500ms delay): still PENDING, no requests.
        clock.set(10_000L + 300L);
        orchestrator.tickNow();
        Thread.sleep(50);
        assertThat("no requests fire during the start delay", sender.sent.size(), is(0));
        assertThat(orchestrator.getStatus().state, is(org.mockserver.load.LoadScenarioState.PENDING));

        // Advance past the delay: transitions to RUNNING and begins firing.
        clock.set(10_000L + 600L);
        orchestrator.tickNow();
        assertThat("scenario runs after the delay elapses", awaitAtLeast(sender, 2, 5_000L), is(true));
        assertThat(orchestrator.getStatus().state, is(org.mockserver.load.LoadScenarioState.RUNNING));
    }

    @Test
    public void triggeringMultipleScenariosRunsThemConcurrently() throws Exception {
        // Two scenarios triggered at once both run; each records its own metrics under its own run_id.
        org.mockserver.configuration.Configuration config =
            org.mockserver.configuration.Configuration.configuration().metricsEnabled(true);
        new Metrics(config);
        orchestrator.setConfiguration(config);

        RecordingSender senderA = new RecordingSender();
        RecordingSender senderB = new RecordingSender();
        // No maxRequests so each runs the full 60s stage and stays active during the assertions; a
        // 5ms per-step think time paces the loops so the instant sender does not flood unbounded.
        LoadScenario a = new LoadScenario().withName("concurrent-a")
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/a").withHeader("Host", "target"))
                .withThinkTime(org.mockserver.model.Delay.milliseconds(5)));
        LoadScenario b = new LoadScenario().withName("concurrent-b")
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/b").withHeader("Host", "target"))
                .withThinkTime(org.mockserver.model.Delay.milliseconds(5)));

        try {
            assertThat(orchestrator.start(a, senderA), is(nullValue()));
            String runIdA = orchestrator.statusFor("concurrent-a").runId;
            assertThat(orchestrator.start(b, senderB), is(nullValue()));
            String runIdB = orchestrator.statusFor("concurrent-b").runId;

            assertThat("both runs are active concurrently", orchestrator.getStatuses().size(), is(2));
            assertThat(runIdA, is(not(runIdB)));

            assertThat(awaitAtLeast(senderA, 10, 5_000L), is(true));
            assertThat(awaitAtLeast(senderB, 10, 5_000L), is(true));
            Thread.sleep(100);

            // Each run records under its own scenario/run_id — no cross-contamination.
            long countA = Metrics.getLoadRequestCount("concurrent-a", runIdA, "0", "/a", "unknown", "2xx");
            long countB = Metrics.getLoadRequestCount("concurrent-b", runIdB, "0", "/b", "unknown", "2xx");
            assertThat(countA, greaterThanOrEqualTo(10L));
            assertThat(countB, greaterThanOrEqualTo(10L));
            // Run A's series carries only A's path; B's run_id never appears under A's name.
            assertThat(Metrics.getLoadRequestCount("concurrent-a", runIdB, "0", "/a", "unknown", "2xx"), is(0L));
        } finally {
            orchestrator.stop("concurrent-a");
            orchestrator.stop("concurrent-b");
        }
    }

    @Test
    public void rejectsTriggerExceedingMaxConcurrentScenarios() {
        ConfigurationProperties.loadGenerationMaxConcurrentScenarios(1);
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        RecordingSender sender = new RecordingSender();
        LoadScenario a = new LoadScenario().withName("cap-a")
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/a").withHeader("Host", "target")));
        LoadScenario b = new LoadScenario().withName("cap-b")
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/b").withHeader("Host", "target")));

        assertThat(orchestrator.start(a, sender), is(nullValue()));
        String error = orchestrator.start(b, sender);
        assertThat(error, containsString("maximum of 1"));
        // Re-triggering an already-active name is exempt from the cap (it replaces, no net increase).
        assertThat(orchestrator.start(a, sender), is(nullValue()));
    }

    @Test
    public void correctedLatencyIncludesFullInFlightTime() throws Exception {
        // The recorded/HDR latency is measured from the scheduled-due time (start of the dispatch path),
        // so it captures the full request lifetime. With a sender that completes each request only after
        // a real ~60ms delay, the per-run HDR p99 must reflect that delay — a post-acquire "service
        // only" measurement of an instant-completing future would resolve near 0. This proves the
        // coordinated-omission-corrected latency is the value recorded.
        long delayMillis = 60L;
        DelayingSender sender = new DelayingSender(delayMillis);
        try {
            LoadScenario scenario = new LoadScenario()
                .withName("co-latency")
                .withMaxRequests(12)
                .withProfile(LoadProfile.constant(3, 60_000L))
                .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

            assertThat(orchestrator.start(scenario, sender), is(nullValue()));
            // Wait until enough requests have completed (succeeded count rises only on completion).
            // Retain the last non-null snapshot: the run can COMPLETE (maxRequests reached) and be moved
            // to terminal state between a poll and the read, at which point getStatus() may momentarily
            // return null — the retained terminal snapshot still carries the final succeeded count.
            long deadline = System.currentTimeMillis() + 15_000L;
            LoadScenarioOrchestrator.LoadScenarioStatus status = null;
            while (System.currentTimeMillis() < deadline) {
                LoadScenarioOrchestrator.LoadScenarioStatus s = orchestrator.getStatus();
                if (s != null) {
                    status = s;
                    if (s.succeeded >= 8) {
                        break;
                    }
                }
                Thread.sleep(10);
            }
            assertThat("a status snapshot was captured", status, is(notNullValue()));
            assertThat("requests completed", status.succeeded, greaterThanOrEqualTo(8L));
            // The corrected p99 reflects the genuine in-flight time, well above the ~0 a service-only
            // (or metrics-off) measurement of an instant future would yield. Tolerant lower bound to
            // absorb scheduler/timing jitter while still being far above zero.
            assertThat("corrected p99 captures the in-flight delay",
                status.p99Millis, greaterThanOrEqualTo(delayMillis - 30L));
            assertThat("p999 is at least p99", status.p999Millis, greaterThanOrEqualTo(status.p99Millis));
        } finally {
            sender.shutdown();
        }
    }

    @Test
    public void statusPercentilesPopulateWithMetricsDisabled() throws Exception {
        // Metrics are NOT enabled in this test class (no new Metrics(metricsEnabled=true)), so the
        // Prometheus load histogram is null and Metrics.loadLatencyPercentileMillis would return 0.
        // The status percentiles must still be non-zero/correct because they are sourced from the
        // per-run HDR histogram, proving HDR — not Prometheus — backs the DTO.
        assertThat("metrics must be off for this test", Metrics.isLoadMetricsActive(), is(false));
        long delayMillis = 40L;
        DelayingSender sender = new DelayingSender(delayMillis);
        try {
            LoadScenario scenario = new LoadScenario()
                .withName("hdr-no-metrics")
                .withMaxRequests(12)
                .withProfile(LoadProfile.constant(3, 60_000L))
                .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

            assertThat(orchestrator.start(scenario, sender), is(nullValue()));
            long deadline = System.currentTimeMillis() + 15_000L;
            while (orchestrator.getStatus().succeeded < 8 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            LoadScenarioOrchestrator.LoadScenarioStatus status = orchestrator.getStatus();
            assertThat(status.succeeded, greaterThanOrEqualTo(8L));
            // Cross-check: Prometheus would report 0 (histogram not registered) ...
            assertThat(Metrics.loadLatencyPercentileMillis(scenario.getName(), status.runId, 99), is(0L));
            // ... but the HDR-backed DTO reports a real value and a sane percentile ordering.
            assertThat("HDR-sourced p50 is non-zero with metrics off", status.p50Millis, greaterThan(0L));
            assertThat(status.p95Millis, greaterThanOrEqualTo(status.p50Millis));
            assertThat(status.p99Millis, greaterThanOrEqualTo(status.p95Millis));
            assertThat(status.p999Millis, greaterThanOrEqualTo(status.p99Millis));
        } finally {
            sender.shutdown();
        }
    }

    @Test
    public void droppedIterationsCountsCapBlockedDispatchesAndIsZeroWhenUnconstrained() throws Exception {
        // A tiny in-flight cap plus a slow sender forces the cap to block dispatches: those due-but-
        // undispatched iterations are surfaced as droppedIterations (rather than fabricating latency).
        ConfigurationProperties.loadGenerationMaxInFlightRequests(1);
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        DelayingSender constrainedSender = new DelayingSender(50L);
        try {
            LoadScenario scenario = new LoadScenario()
                .withName("dropped-cap")
                .withProfile(LoadProfile.constant(8, 60_000L))
                .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

            assertThat(orchestrator.start(scenario, constrainedSender), is(nullValue()));
            // With only one permit but eight looping VUs hammering, the cap rejects many dispatches.
            long deadline = System.currentTimeMillis() + 5_000L;
            while (orchestrator.getStatus().droppedIterations == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertThat("cap-blocked dispatches are counted as dropped iterations",
                orchestrator.getStatus().droppedIterations, greaterThan(0L));
        } finally {
            orchestrator.stop("dropped-cap");
            constrainedSender.shutdown();
        }

        // Unconstrained run: a generous cap and an instant sender never drops.
        ConfigurationProperties.loadGenerationMaxInFlightRequests(1000);
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        RecordingSender freeSender = new RecordingSender();
        LoadScenario unconstrained = new LoadScenario()
            .withName("dropped-none")
            .withMaxRequests(20)
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(unconstrained, freeSender), is(nullValue()));
        assertThat(awaitAtLeast(freeSender, 20, 5_000L), is(true));
        Thread.sleep(100);
        assertThat("an unconstrained run drops nothing",
            orchestrator.statusFor("dropped-none").droppedIterations, is(0L));
    }

    // -- In-run thresholds (pass/fail verdicts + abort-on-fail) --

    /** A synchronous fake sender that records every request and returns a fixed status code. */
    private static final class StatusSender implements Function<HttpRequest, CompletableFuture<HttpResponse>> {
        final ConcurrentLinkedQueue<HttpRequest> sent = new ConcurrentLinkedQueue<>();
        private final int statusCode;

        StatusSender(int statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public CompletableFuture<HttpResponse> apply(HttpRequest httpRequest) {
            sent.add(httpRequest);
            return CompletableFuture.completedFuture(response().withStatusCode(statusCode));
        }
    }

    /** Poll until the named run reports a non-null verdict (or timeout). */
    private boolean awaitVerdict(String name, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            LoadScenarioOrchestrator.LoadScenarioStatus status = orchestrator.statusFor(name);
            if (status != null && status.verdict != null) {
                return true;
            }
            orchestrator.tickNow();
            Thread.sleep(5);
        }
        LoadScenarioOrchestrator.LoadScenarioStatus status = orchestrator.statusFor(name);
        return status != null && status.verdict != null;
    }

    @Test
    public void thresholdSatisfiedYieldsPassVerdict() throws Exception {
        // A fast instant sender keeps p95 latency far below a generous bound, so the verdict is PASS.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("threshold-pass")
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withThresholds(new org.mockserver.load.LoadThreshold()
                .withMetric(org.mockserver.load.LoadThreshold.Metric.LATENCY_P95)
                .withComparator(org.mockserver.slo.SloObjective.Comparator.LESS_THAN)
                .withThreshold(10_000.0))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat("traffic flows", awaitQueue(sender.sent, 10, 5_000L), is(true));
        assertThat("a verdict is computed once requests complete",
            awaitVerdict("threshold-pass", 5_000L), is(true));

        LoadScenarioOrchestrator.LoadScenarioStatus status = orchestrator.statusFor("threshold-pass");
        assertThat(status.verdict, is("PASS"));
        assertThat(status.abortedByThreshold, is(false));
        assertThat(status.thresholdResults, hasSize(1));
        assertThat(status.thresholdResults.get(0).metric, is("LATENCY_P95"));
        assertThat(status.thresholdResults.get(0).comparator, is("LESS_THAN"));
        assertThat(status.thresholdResults.get(0).threshold, is(10_000.0));
        assertThat(status.thresholdResults.get(0).satisfied, is(true));
        orchestrator.stop("threshold-pass");
    }

    @Test
    public void thresholdBreachedYieldsFailVerdict() throws Exception {
        // Every response is a 500, so the error rate is 1.0, breaching an error-rate < 0.1 threshold.
        StatusSender sender = new StatusSender(500);
        LoadScenario scenario = new LoadScenario()
            .withName("threshold-fail")
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withThresholds(new org.mockserver.load.LoadThreshold()
                .withMetric(org.mockserver.load.LoadThreshold.Metric.ERROR_RATE)
                .withComparator(org.mockserver.slo.SloObjective.Comparator.LESS_THAN)
                .withThreshold(0.1))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        // Wait for failures to register (failed count rises on completion).
        long deadline = System.currentTimeMillis() + 5_000L;
        while (orchestrator.statusFor("threshold-fail") != null
            && orchestrator.statusFor("threshold-fail").failed < 5
            && System.currentTimeMillis() < deadline) {
            orchestrator.tickNow();
            Thread.sleep(5);
        }
        assertThat(awaitVerdict("threshold-fail", 5_000L), is(true));

        LoadScenarioOrchestrator.LoadScenarioStatus status = orchestrator.statusFor("threshold-fail");
        assertThat(status.verdict, is("FAIL"));
        assertThat(status.thresholdResults.get(0).metric, is("ERROR_RATE"));
        assertThat(status.thresholdResults.get(0).observed, closeTo(1.0, 0.0001));
        assertThat(status.thresholdResults.get(0).satisfied, is(false));
        // abortOnFail was not set: the run keeps running despite the FAIL verdict.
        assertThat(status.state, is(org.mockserver.load.LoadScenarioState.RUNNING));
        orchestrator.stop("threshold-fail");
    }

    @Test
    public void abortOnFailTerminatesRunAfterGraceWindow() throws Exception {
        // A 500-only sender breaches an error-rate threshold. With abortOnFail and a grace window, the
        // run must NOT abort before the grace elapses, then abort (STOPPED, abortedByThreshold) after.
        StatusSender sender = new StatusSender(500);
        LoadScenario scenario = new LoadScenario()
            .withName("abort-on-fail")
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withAbortOnFail(true)
            .withAbortGraceMillis(1_000L)
            .withThresholds(new org.mockserver.load.LoadThreshold()
                .withMetric(org.mockserver.load.LoadThreshold.Metric.ERROR_RATE)
                .withComparator(org.mockserver.slo.SloObjective.Comparator.LESS_THAN)
                .withThreshold(0.1))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        // Drive ticks at t < grace (clock starts at 10_000): a FAIL verdict is computed but no abort yet.
        long deadline = System.currentTimeMillis() + 5_000L;
        while (orchestrator.statusFor("abort-on-fail") != null
            && (orchestrator.statusFor("abort-on-fail").verdict == null)
            && System.currentTimeMillis() < deadline) {
            orchestrator.tickNow();
            Thread.sleep(5);
        }
        LoadScenarioOrchestrator.LoadScenarioStatus midRun = orchestrator.statusFor("abort-on-fail");
        assertThat("verdict is FAIL before the grace window", midRun.verdict, is("FAIL"));
        assertThat("run is NOT aborted before the grace window elapses",
            midRun.state, is(org.mockserver.load.LoadScenarioState.RUNNING));
        assertThat(midRun.abortedByThreshold, is(false));

        // Advance the clock past the grace window and tick: now the FAIL verdict aborts the run.
        clock.set(10_000L + 1_500L);
        orchestrator.tickNow();
        long abortDeadline = System.currentTimeMillis() + 5_000L;
        while (orchestrator.statusFor("abort-on-fail") != null
            && orchestrator.statusFor("abort-on-fail").state == org.mockserver.load.LoadScenarioState.RUNNING
            && System.currentTimeMillis() < abortDeadline) {
            orchestrator.tickNow();
            Thread.sleep(5);
        }
        LoadScenarioOrchestrator.LoadScenarioStatus terminal = orchestrator.statusFor("abort-on-fail");
        assertThat(terminal.state, is(org.mockserver.load.LoadScenarioState.STOPPED));
        assertThat(terminal.abortedByThreshold, is(true));
        assertThat("the terminal status carries the FAIL verdict", terminal.verdict, is("FAIL"));
    }

    @Test
    public void throughputThresholdEvaluatedFromPerRunRate() throws Exception {
        // THROUGHPUT_RPS > 1000 is unreachable with a tiny RPS cap, so the verdict is FAIL on rate.
        ConfigurationProperties.loadGenerationMaxInFlightRequests(1000);
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("throughput-threshold")
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withThresholds(new org.mockserver.load.LoadThreshold()
                .withMetric(org.mockserver.load.LoadThreshold.Metric.THROUGHPUT_RPS)
                .withComparator(org.mockserver.slo.SloObjective.Comparator.GREATER_THAN_OR_EQUAL)
                .withThreshold(1.0))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 10, 5_000L), is(true));
        // Advance the clock so a meaningful elapsed window exists, then evaluate.
        clock.set(10_000L + 1_000L);
        orchestrator.tickNow();
        assertThat(awaitVerdict("throughput-threshold", 5_000L), is(true));

        LoadScenarioOrchestrator.LoadScenarioStatus status = orchestrator.statusFor("throughput-threshold");
        assertThat(status.thresholdResults.get(0).metric, is("THROUGHPUT_RPS"));
        // observed rps = requestsSent / elapsedSeconds; with >=10 requests over ~1s it clears >= 1.
        assertThat(status.thresholdResults.get(0).observed, greaterThanOrEqualTo(1.0));
        assertThat(status.verdict, is("PASS"));
        orchestrator.stop("throughput-threshold");
    }

    // -- Per-step response checks (k6 'check' parity) --

    /** A synchronous fake sender returning a fixed status code and body. */
    private static final class BodySender implements Function<HttpRequest, CompletableFuture<HttpResponse>> {
        final ConcurrentLinkedQueue<HttpRequest> sent = new ConcurrentLinkedQueue<>();
        private final int statusCode;
        private final String body;

        BodySender(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public CompletableFuture<HttpResponse> apply(HttpRequest httpRequest) {
            sent.add(httpRequest);
            return CompletableFuture.completedFuture(response().withStatusCode(statusCode).withBody(body));
        }
    }

    /** Poll until the queue has recorded at least {@code n} requests (or timeout). */
    private boolean awaitQueue(ConcurrentLinkedQueue<HttpRequest> queue, int n, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (queue.size() >= n) {
                return true;
            }
            Thread.sleep(5);
        }
        return queue.size() >= n;
    }

    @Test
    public void perStepChecksRecordPassAndFail() throws Exception {
        // Every response is 200 with body {"status":"bad"}: a STATUS==200 check always PASSES while a
        // BODY_JSONPATH $.status EQUALS "ok" check always FAILS. Both outcomes must be recorded.
        BodySender sender = new BodySender(200, "{\"status\":\"bad\"}");
        LoadScenario scenario = new LoadScenario()
            .withName("checks-record")
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withSteps(new LoadStep()
                .withRequest(request().withPath("/api").withHeader("Host", "target"))
                .withCheck(new org.mockserver.load.LoadCheck()
                    .withSource(org.mockserver.load.LoadCheck.Source.STATUS)
                    .withComparator(org.mockserver.load.LoadCheck.Comparator.EQUALS).withValue("200"))
                .withCheck(new org.mockserver.load.LoadCheck()
                    .withSource(org.mockserver.load.LoadCheck.Source.BODY_JSONPATH).withJsonPath("$.status")
                    .withComparator(org.mockserver.load.LoadCheck.Comparator.EQUALS).withValue("ok")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat("traffic flows", awaitQueue(sender.sent, 10, 5_000L), is(true));
        // Poll until both a pass and a fail have been aggregated into the status snapshot.
        long deadline = System.currentTimeMillis() + 5_000L;
        LoadScenarioOrchestrator.LoadScenarioStatus status = orchestrator.statusFor("checks-record");
        while (System.currentTimeMillis() < deadline
            && (status == null || status.checkResults.size() < 2)) {
            Thread.sleep(5);
            status = orchestrator.statusFor("checks-record");
        }
        assertThat(status.checkResults, hasSize(2));
        long totalPassed = status.checkResults.stream().mapToLong(r -> r.passed).sum();
        long totalFailed = status.checkResults.stream().mapToLong(r -> r.failed).sum();
        assertThat("the STATUS check passed at least once", totalPassed, greaterThan(0L));
        assertThat("the JSONPath check failed at least once", totalFailed, greaterThan(0L));
        // The STATUS check aggregate has only passes; the JSONPath aggregate has only fails.
        LoadScenarioOrchestrator.CheckResult statusCheck = status.checkResults.stream()
            .filter(r -> "STATUS".equals(r.source)).findFirst().orElseThrow(AssertionError::new);
        LoadScenarioOrchestrator.CheckResult jsonCheck = status.checkResults.stream()
            .filter(r -> "BODY_JSONPATH".equals(r.source)).findFirst().orElseThrow(AssertionError::new);
        assertThat(statusCheck.passed, greaterThan(0L));
        assertThat(statusCheck.failed, is(0L));
        assertThat(jsonCheck.failed, greaterThan(0L));
        assertThat(jsonCheck.passed, is(0L));
        orchestrator.stop("checks-record");
    }

    @Test
    public void checkFailureRateThresholdTripsAndAbortsAfterGrace() throws Exception {
        // A check that always fails drives CHECK_FAILURE_RATE to 1.0, breaching a < 0.5 threshold. With
        // abortOnFail + a grace window, the run FAILs but only aborts once the grace elapses.
        BodySender sender = new BodySender(200, "{\"status\":\"bad\"}");
        LoadScenario scenario = new LoadScenario()
            .withName("check-rate-abort")
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withAbortOnFail(true)
            .withAbortGraceMillis(1_000L)
            .withThresholds(new org.mockserver.load.LoadThreshold()
                .withMetric(org.mockserver.load.LoadThreshold.Metric.CHECK_FAILURE_RATE)
                .withComparator(org.mockserver.slo.SloObjective.Comparator.LESS_THAN)
                .withThreshold(0.5))
            .withSteps(new LoadStep()
                .withRequest(request().withPath("/api").withHeader("Host", "target"))
                .withCheck(new org.mockserver.load.LoadCheck()
                    .withSource(org.mockserver.load.LoadCheck.Source.BODY_JSONPATH).withJsonPath("$.status")
                    .withComparator(org.mockserver.load.LoadCheck.Comparator.EQUALS).withValue("ok")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat("traffic flows", awaitQueue(sender.sent, 5, 5_000L), is(true));
        assertThat("a verdict is computed once checks are evaluated",
            awaitVerdict("check-rate-abort", 5_000L), is(true));

        LoadScenarioOrchestrator.LoadScenarioStatus midRun = orchestrator.statusFor("check-rate-abort");
        assertThat("CHECK_FAILURE_RATE breach yields a FAIL verdict", midRun.verdict, is("FAIL"));
        assertThat("not aborted before the grace window",
            midRun.state, is(org.mockserver.load.LoadScenarioState.RUNNING));
        assertThat(midRun.thresholdResults.get(0).metric, is("CHECK_FAILURE_RATE"));
        assertThat(midRun.thresholdResults.get(0).observed, closeTo(1.0, 0.0001));
        assertThat(midRun.thresholdResults.get(0).satisfied, is(false));

        clock.set(10_000L + 1_500L);
        orchestrator.tickNow();
        long abortDeadline = System.currentTimeMillis() + 5_000L;
        while (orchestrator.statusFor("check-rate-abort") != null
            && orchestrator.statusFor("check-rate-abort").state == org.mockserver.load.LoadScenarioState.RUNNING
            && System.currentTimeMillis() < abortDeadline) {
            orchestrator.tickNow();
            Thread.sleep(5);
        }
        LoadScenarioOrchestrator.LoadScenarioStatus terminal = orchestrator.statusFor("check-rate-abort");
        assertThat(terminal.state, is(org.mockserver.load.LoadScenarioState.STOPPED));
        assertThat(terminal.abortedByThreshold, is(true));
        assertThat(terminal.verdict, is("FAIL"));
    }

    @Test
    public void noChecksLeavesCheckResultsEmpty() throws Exception {
        // A scenario without checks records no check results (non-breaking default).
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("no-checks")
            .withMaxRequests(10)
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 10, 5_000L), is(true));
        long deadline = System.currentTimeMillis() + 2_000L;
        while (orchestrator.getStatus() != null && org.mockserver.load.LoadScenarioState.RUNNING == orchestrator.getStatus().state
            && System.currentTimeMillis() < deadline) {
            orchestrator.tickNow();
            Thread.sleep(5);
        }
        assertThat(orchestrator.statusFor("no-checks").checkResults, is(empty()));
    }

    @Test
    public void noThresholdsLeavesVerdictNull() throws Exception {
        // A scenario without thresholds carries a null verdict and empty thresholdResults (unchanged
        // behaviour); the run completes normally.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("no-thresholds")
            .withMaxRequests(15)
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 15, 5_000L), is(true));
        long deadline = System.currentTimeMillis() + 2_000L;
        while (orchestrator.getStatus() != null && org.mockserver.load.LoadScenarioState.RUNNING == orchestrator.getStatus().state
            && System.currentTimeMillis() < deadline) {
            orchestrator.tickNow();
            Thread.sleep(5);
        }
        LoadScenarioOrchestrator.LoadScenarioStatus status = orchestrator.statusFor("no-thresholds");
        assertThat(status.state, is(org.mockserver.load.LoadScenarioState.COMPLETED));
        assertThat("no thresholds -> null verdict", status.verdict, is(nullValue()));
        assertThat(status.abortedByThreshold, is(false));
        assertThat(status.thresholdResults, is(empty()));
    }

    @Test
    public void completedRunCarriesFinalVerdict() throws Exception {
        // A bounded run with a satisfiable threshold completes and carries a final PASS verdict.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("final-verdict")
            .withMaxRequests(15)
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withThresholds(new org.mockserver.load.LoadThreshold()
                .withMetric(org.mockserver.load.LoadThreshold.Metric.LATENCY_P99)
                .withComparator(org.mockserver.slo.SloObjective.Comparator.LESS_THAN_OR_EQUAL)
                .withThreshold(60_000.0))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 15, 5_000L), is(true));
        long deadline = System.currentTimeMillis() + 2_000L;
        while (orchestrator.getStatus() != null && org.mockserver.load.LoadScenarioState.RUNNING == orchestrator.getStatus().state
            && System.currentTimeMillis() < deadline) {
            orchestrator.tickNow();
            Thread.sleep(5);
        }
        LoadScenarioOrchestrator.LoadScenarioStatus status = orchestrator.statusFor("final-verdict");
        assertThat(status.state, is(org.mockserver.load.LoadScenarioState.COMPLETED));
        assertThat("a completed run carries a final verdict", status.verdict, is("PASS"));
        assertThat(status.thresholdResults, hasSize(1));
    }

    /**
     * Parks one chosen thread the first time it reaches this point, until released, so a test can hold
     * that thread at a known place inside the orchestrator while another thread acts.
     */
    private static final class ParkPoint {
        private final CountDownLatch parked = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile Thread threadToPark;

        boolean isForCurrentThread() {
            return Thread.currentThread() == threadToPark && parked.getCount() > 0;
        }

        void park() {
            parked.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the parked thread was never released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        /** Starts {@code action} on a new thread and returns once that thread is parked here. */
        Thread startAndPark(Runnable action) throws InterruptedException {
            Thread thread = new Thread(action, "test-parked-thread");
            thread.setDaemon(true);
            threadToPark = thread;
            thread.start();
            assertThat("the thread reached its park point", parked.await(10, TimeUnit.SECONDS), is(true));
            return thread;
        }

        void releaseAndJoin(Thread thread) throws InterruptedException {
            release.countDown();
            thread.join(10_000L);
            assertThat("the parked thread finished", thread.isAlive(), is(false));
        }
    }

    /** A clock that parks the chosen thread at its first read. */
    private static final class ParkingClock implements LongSupplier {
        final AtomicLong millis = new AtomicLong(10_000L);
        final ParkPoint firstRead = new ParkPoint();

        @Override
        public long getAsLong() {
            if (firstRead.isForCurrentThread()) {
                firstRead.park();
            }
            return millis.get();
        }

        Thread startAndPark(Runnable action) throws InterruptedException {
            return firstRead.startAndPark(action);
        }

        void releaseAndJoin(Thread thread) throws InterruptedException {
            firstRead.releaseAndJoin(thread);
        }
    }

    /**
     * A scenario that parks the chosen thread the first time it reads the name once the scenario's run
     * is registered: inside {@code start}, after the run became visible to other threads.
     */
    private static final class ParksOnceRegisteredScenario extends LoadScenario {
        final ParkPoint onceRegistered = new ParkPoint();
        private final LoadScenarioOrchestrator orchestrator;

        ParksOnceRegisteredScenario(LoadScenarioOrchestrator orchestrator, String name) {
            this.orchestrator = orchestrator;
            withName(name);
            withProfile(LoadProfile.constant(1, 1_000L));
            withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));
        }

        @Override
        public String getName() {
            String name = super.getName();
            if (onceRegistered.isForCurrentThread() && orchestrator.isActive(name)) {
                onceRegistered.park();
            }
            return name;
        }
    }

    /** An orchestrator whose scheduler never ticks, so only the test's own threads tick or stop a run. */
    private static LoadScenarioOrchestrator orchestratorTickedOnlyByTheTest(LongSupplier clock) {
        return new LoadScenarioOrchestrator(clock, mock(ScheduledExecutorService.class));
    }

    private static LoadScenario oneSecondScenario(String name) {
        return new LoadScenario()
            .withName(name)
            .withProfile(LoadProfile.constant(1, 1_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));
    }

    /** A sender whose responses never arrive, so a run's only way to end is a tick or a stop. */
    private static final Function<HttpRequest, CompletableFuture<HttpResponse>> NEVER_RESPONDS = httpRequest -> new CompletableFuture<>();

    @Test
    public void aStopThatLosesToAnotherStopKeepsTheTerminalStatus() throws Exception {
        ParkingClock parkingClock = new ParkingClock();
        LoadScenarioOrchestrator raced = orchestratorTickedOnlyByTheTest(parkingClock);
        try {
            assertThat(raced.start(oneSecondScenario("stop-vs-stop"), NEVER_RESPONDS), is(nullValue()));

            // the losing stop has begun terminating the run and is held before it records anything
            Thread losingStop = parkingClock.startAndPark(() -> raced.stop("stop-vs-stop"));
            parkingClock.millis.set(10_400L);
            raced.stop("stop-vs-stop");
            parkingClock.millis.set(10_900L);
            parkingClock.releaseAndJoin(losingStop);

            LoadScenarioOrchestrator.LoadScenarioStatus status = raced.statusFor("stop-vs-stop");
            assertThat("the stopped run still has a status", status, is(notNullValue()));
            assertThat(status.state, is(org.mockserver.load.LoadScenarioState.STOPPED));
            assertThat("the status is the one recorded by the stop that ended the run", status.endedAtEpochMillis, is(10_400L));
            assertThat(raced.isActive("stop-vs-stop"), is(false));
        } finally {
            raced.reset();
        }
    }

    @Test
    public void aCompletingTickThatLosesToAStopKeepsTheStoppedStatus() throws Exception {
        ParkingClock parkingClock = new ParkingClock();
        LoadScenarioOrchestrator raced = orchestratorTickedOnlyByTheTest(parkingClock);
        try {
            assertThat(raced.start(oneSecondScenario("tick-vs-stop"), NEVER_RESPONDS), is(nullValue()));
            assertThat(raced.statusFor("tick-vs-stop").state, is(org.mockserver.load.LoadScenarioState.RUNNING));

            // the tick has passed its "is this run still live" check and is held before it sees that the
            // only stage has elapsed, which is what makes it complete the run
            parkingClock.millis.set(12_000L);
            Thread completingTick = parkingClock.startAndPark(raced::tickNow);
            raced.stop("tick-vs-stop");
            parkingClock.releaseAndJoin(completingTick);

            LoadScenarioOrchestrator.LoadScenarioStatus status = raced.statusFor("tick-vs-stop");
            assertThat("the stopped run still has a status", status, is(notNullValue()));
            assertThat("the stop ended the run, so the late completion must not relabel it", status.state, is(org.mockserver.load.LoadScenarioState.STOPPED));
        } finally {
            raced.reset();
        }
    }

    @Test
    public void aCompletingTickThatLosesToAnotherTickKeepsTheCompletedStatus() throws Exception {
        ParkingClock parkingClock = new ParkingClock();
        LoadScenarioOrchestrator raced = orchestratorTickedOnlyByTheTest(parkingClock);
        try {
            assertThat(raced.start(oneSecondScenario("tick-vs-tick"), NEVER_RESPONDS), is(nullValue()));

            // both ticks see the run as live; the held one completes it second
            parkingClock.millis.set(12_000L);
            Thread losingTick = parkingClock.startAndPark(raced::tickNow);
            raced.tickNow();
            parkingClock.releaseAndJoin(losingTick);

            LoadScenarioOrchestrator.LoadScenarioStatus status = raced.statusFor("tick-vs-tick");
            assertThat("the completed run still has a status", status, is(notNullValue()));
            assertThat(status.state, is(org.mockserver.load.LoadScenarioState.COMPLETED));
        } finally {
            raced.reset();
        }
    }

    @Test
    public void unregisteringASenderKeepsANewerOneAndFallsBackToTheOlderOneStillRegistered() throws Exception {
        LoadScenarioOrchestrator installed = orchestratorTickedOnlyByTheTest(clock::get);
        CompletableFuture<HttpRequest> sentByOlder = new CompletableFuture<>();
        CompletableFuture<HttpRequest> sentByNewer = new CompletableFuture<>();
        Function<HttpRequest, CompletableFuture<HttpResponse>> olderServers = httpRequest -> {
            sentByOlder.complete(httpRequest);
            return new CompletableFuture<>();
        };
        Function<HttpRequest, CompletableFuture<HttpResponse>> stoppedServers = httpRequest -> new CompletableFuture<>();
        Function<HttpRequest, CompletableFuture<HttpResponse>> newerServers = httpRequest -> {
            sentByNewer.complete(httpRequest);
            return new CompletableFuture<>();
        };
        try {
            installed.registerSender(olderServers);
            installed.registerSender(stoppedServers);
            installed.registerSender(newerServers);
            installed.unregisterSender(stoppedServers);
            assertThat("a sender registered since by another server is kept", installed.start(oneSecondScenario("kept"), null), is(nullValue()));
            installed.tickNow();
            assertThat(sentByNewer.get(10, TimeUnit.SECONDS).getPath().getValue(), is("/api"));
            installed.stop("kept");

            installed.unregisterSender(newerServers);
            assertThat(installed.start(oneSecondScenario("fallen-back"), null), is(nullValue()));
            installed.tickNow();
            assertThat("the sender of the server still running is used", sentByOlder.get(10, TimeUnit.SECONDS).getPath().getValue(), is("/api"));
            installed.stop("fallen-back");

            installed.unregisterSender(olderServers);
            assertThat(installed.start(oneSecondScenario("none"), null), is("no load sender installed (server runtime not wired)"));
        } finally {
            installed.reset();
        }
    }


    @Test
    public void unregisteringTheSenderOfAnEndedRunLetsThatSenderBeCollected() throws Exception {
        // a real scheduler: a mock keeps the tasks it was handed, and through them the run and its sender
        WeakReference<?> stoppedServers = startStopAndUnregister(orchestrator, "ended");

        assertCollected(stoppedServers);
        assertThat("the ended run's status is kept", orchestrator.statusFor("ended").state, is(org.mockserver.load.LoadScenarioState.STOPPED));
    }

    @Test
    public void unregisteringAnotherServersSenderKeepsTheEndedRun() throws Exception {
        LoadScenarioOrchestrator installed = orchestratorTickedOnlyByTheTest(clock::get);
        try {
            assertThat(installed.start(oneSecondScenario("ended"), NEVER_RESPONDS), is(nullValue()));
            installed.stop("ended");
            assertThat("the ended run's virtual user still waits for its response", installed.lastRunActiveVuCount(), is(1));

            installed.unregisterSender(httpRequest -> new CompletableFuture<>());

            assertThat(installed.lastRunActiveVuCount(), is(1));
        } finally {
            installed.reset();
        }
    }

    @Test
    public void unregisteringTheSenderOfARunStillActiveKeepsThatRun() throws Exception {
        LoadScenarioOrchestrator installed = orchestratorTickedOnlyByTheTest(clock::get);
        try {
            installed.registerSender(NEVER_RESPONDS);
            assertThat(installed.start(oneSecondScenario("active"), null), is(nullValue()));

            installed.unregisterSender(NEVER_RESPONDS);

            assertThat(installed.isActive("active"), is(true));
            assertThat(installed.lastRunActiveVuCount(), is(1));
        } finally {
            installed.reset();
        }
    }

    private static WeakReference<?> startStopAndUnregister(LoadScenarioOrchestrator installed, String name) {
        Function<HttpRequest, CompletableFuture<HttpResponse>> stoppedServers = new NeverRespondingSender();
        installed.registerSender(stoppedServers);
        assertThat(installed.start(oneSecondScenario(name), null), is(nullValue()));
        installed.stop(name);
        installed.unregisterSender(stoppedServers);
        return new WeakReference<>(stoppedServers);
    }

    private static final class NeverRespondingSender implements Function<HttpRequest, CompletableFuture<HttpResponse>> {
        @Override
        public CompletableFuture<HttpResponse> apply(HttpRequest httpRequest) {
            return new CompletableFuture<>();
        }
    }

    private static void assertCollected(WeakReference<?> reference) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (reference.get() != null && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(100);
        }
        assertThat("the sender of a stopped server is still reachable after 30s of garbage collection", reference.get(), is(nullValue()));
    }

    @Test
    public void aStopLandingWhileItsRunIsStillStartingKeepsTheStoppedStatus() throws Exception {
        LoadScenarioOrchestrator raced = orchestratorTickedOnlyByTheTest(clock::get);
        try {
            ParksOnceRegisteredScenario scenario = new ParksOnceRegisteredScenario(raced, "start-vs-stop");

            // the start has registered its run and is held before it finishes
            Thread starting = scenario.onceRegistered.startAndPark(() -> raced.start(scenario, NEVER_RESPONDS));
            raced.stop("start-vs-stop");
            scenario.onceRegistered.releaseAndJoin(starting);

            LoadScenarioOrchestrator.LoadScenarioStatus status = raced.statusFor("start-vs-stop");
            assertThat("the stopped run still has a status", status, is(notNullValue()));
            assertThat(status.state, is(org.mockserver.load.LoadScenarioState.STOPPED));
            assertThat(raced.isActive("start-vs-stop"), is(false));
        } finally {
            raced.reset();
        }
    }

    @Test
    public void aCompletingTickOfAReplacedRunLeavesTheNewRunActive() throws Exception {
        ParkingClock parkingClock = new ParkingClock();
        LoadScenarioOrchestrator raced = orchestratorTickedOnlyByTheTest(parkingClock);
        try {
            assertThat(raced.start(oneSecondScenario("tick-vs-retrigger"), NEVER_RESPONDS), is(nullValue()));
            String replacedRunId = raced.statusFor("tick-vs-retrigger").runId;

            parkingClock.millis.set(12_000L);
            Thread completingTick = parkingClock.startAndPark(raced::tickNow);
            assertThat(raced.start(oneSecondScenario("tick-vs-retrigger"), NEVER_RESPONDS), is(nullValue()));
            parkingClock.releaseAndJoin(completingTick);

            LoadScenarioOrchestrator.LoadScenarioStatus status = raced.statusFor("tick-vs-retrigger");
            assertThat("the re-triggered run is still registered", raced.isActive("tick-vs-retrigger"), is(true));
            assertThat(status.state, is(org.mockserver.load.LoadScenarioState.RUNNING));
            assertThat("the status is the new run's, not the replaced run's", status.runId, is(not(replacedRunId)));
        } finally {
            raced.reset();
        }
    }

    /**
     * A path-aware fake sender: returns a canned response (body + headers) for requests whose path
     * starts with a given prefix, and 200/empty for everything else. Records every request so a test
     * can assert what a later step rendered.
     */
    private static final class CannedSender implements Function<HttpRequest, CompletableFuture<HttpResponse>> {
        final ConcurrentLinkedQueue<HttpRequest> sent = new ConcurrentLinkedQueue<>();
        private final String firstStepPathPrefix;
        private final HttpResponse firstStepResponse;

        CannedSender(String firstStepPathPrefix, HttpResponse firstStepResponse) {
            this.firstStepPathPrefix = firstStepPathPrefix;
            this.firstStepResponse = firstStepResponse;
        }

        @Override
        public CompletableFuture<HttpResponse> apply(HttpRequest httpRequest) {
            sent.add(httpRequest);
            String path = httpRequest.getPath() != null ? httpRequest.getPath().getValue() : "";
            if (path != null && path.startsWith(firstStepPathPrefix)) {
                return CompletableFuture.completedFuture(firstStepResponse);
            }
            return CompletableFuture.completedFuture(response().withStatusCode(200));
        }
    }

    private HttpRequest awaitSecondStep(CannedSender sender, String secondStepPathPrefix, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            for (HttpRequest sentRequest : sender.sent) {
                String path = sentRequest.getPath() != null ? sentRequest.getPath().getValue() : "";
                if (path != null && path.startsWith(secondStepPathPrefix)) {
                    return sentRequest;
                }
            }
            Thread.sleep(5);
        }
        return null;
    }

    @Test
    public void captureBodyJsonPathFlowsIntoSubsequentStepPathBodyAndHeader() throws Exception {
        // Step 1 (POST /login) returns {"token":"abc"}; a BODY_JSONPATH $.token capture binds it to
        // 'token'. Step 2 references it in its path, body and an Authorization header. Mustache engine.
        CannedSender sender = new CannedSender("/login",
            response().withStatusCode(200).withBody("{\"token\":\"abc\"}"));
        LoadScenario scenario = new LoadScenario()
            .withName("capture-jsonpath")
            .withTemplateType(HttpTemplate.TemplateType.MUSTACHE)
            .withMaxRequests(2)
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withSteps(
                new LoadStep()
                    .withRequest(request().withMethod("POST").withPath("/login").withHeader("Host", "target"))
                    .withCapture(new org.mockserver.load.LoadCapture()
                        .withName("token")
                        .withSource(org.mockserver.load.LoadCapture.Source.BODY_JSONPATH)
                        .withExpression("$.token")),
                new LoadStep()
                    .withRequest(request().withMethod("GET")
                        .withPath("/account/{{iteration.captured.token}}")
                        .withHeader("Host", "target")
                        .withBody("{\"t\":\"{{iteration.captured.token}}\"}")
                        .withHeader("Authorization", "Bearer {{iteration.captured.token}}")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        HttpRequest secondStep = awaitSecondStep(sender, "/account/", 5_000L);
        assertThat("second step request should have been dispatched", secondStep, is(notNullValue()));
        assertThat(secondStep.getPath().getValue(), is("/account/abc"));
        assertThat(secondStep.getBodyAsString(), containsString("abc"));
        assertThat(secondStep.getFirstHeader("Authorization"), is("Bearer abc"));
    }

    @Test
    public void captureHeaderFlowsIntoSubsequentStep() throws Exception {
        // Step 1 returns a Location header; a HEADER capture of 'Location' feeds step 2's path. Velocity.
        CannedSender sender = new CannedSender("/login",
            response().withStatusCode(200).withHeader("Location", "/sess/42"));
        LoadScenario scenario = new LoadScenario()
            .withName("capture-header")
            .withTemplateType(HttpTemplate.TemplateType.VELOCITY)
            .withMaxRequests(2)
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withSteps(
                new LoadStep()
                    .withRequest(request().withMethod("POST").withPath("/login").withHeader("Host", "target"))
                    .withCapture(new org.mockserver.load.LoadCapture()
                        .withName("loc")
                        .withSource(org.mockserver.load.LoadCapture.Source.HEADER)
                        .withExpression("Location")),
                new LoadStep()
                    .withRequest(request().withMethod("GET")
                        .withPath("/follow$iteration.captured.loc")
                        .withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        HttpRequest secondStep = awaitSecondStep(sender, "/follow", 5_000L);
        assertThat(secondStep, is(notNullValue()));
        assertThat(secondStep.getPath().getValue(), is("/follow/sess/42"));
    }

    @Test
    public void captureBodyRegexGroupOneFlowsIntoSubsequentStep() throws Exception {
        // Step 1 returns a CSRF token embedded in HTML; BODY_REGEX captures group 1. Mustache.
        CannedSender sender = new CannedSender("/form",
            response().withStatusCode(200).withBody("<input name=csrf value=\"XYZ-9\"/>"));
        LoadScenario scenario = new LoadScenario()
            .withName("capture-regex")
            .withTemplateType(HttpTemplate.TemplateType.MUSTACHE)
            .withMaxRequests(2)
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withSteps(
                new LoadStep()
                    .withRequest(request().withMethod("GET").withPath("/form").withHeader("Host", "target"))
                    .withCapture(new org.mockserver.load.LoadCapture()
                        .withName("csrf")
                        .withSource(org.mockserver.load.LoadCapture.Source.BODY_REGEX)
                        .withExpression("value=\"([^\"]+)\"")),
                new LoadStep()
                    .withRequest(request().withMethod("POST")
                        .withPath("/submit")
                        .withHeader("Host", "target")
                        .withHeader("X-CSRF", "{{iteration.captured.csrf}}")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        HttpRequest secondStep = awaitSecondStep(sender, "/submit", 5_000L);
        assertThat(secondStep, is(notNullValue()));
        assertThat(secondStep.getFirstHeader("X-CSRF"), is("XYZ-9"));
    }

    @Test
    public void captureNoMatchUsesDefaultValueAndMissingLeavesVarUnset() throws Exception {
        // Step 1's body has no 'token'; one capture has a defaultValue (used), one does not (var stays
        // unset, so the literal placeholder renders empty under Mustache).
        CannedSender sender = new CannedSender("/login",
            response().withStatusCode(200).withBody("{\"other\":\"v\"}"));
        LoadScenario scenario = new LoadScenario()
            .withName("capture-default")
            .withTemplateType(HttpTemplate.TemplateType.MUSTACHE)
            .withMaxRequests(2)
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withSteps(
                new LoadStep()
                    .withRequest(request().withMethod("POST").withPath("/login").withHeader("Host", "target"))
                    .withCapture(new org.mockserver.load.LoadCapture()
                        .withName("token")
                        .withSource(org.mockserver.load.LoadCapture.Source.BODY_JSONPATH)
                        .withExpression("$.token")
                        .withDefaultValue("fallback"))
                    .withCapture(new org.mockserver.load.LoadCapture()
                        .withName("missing")
                        .withSource(org.mockserver.load.LoadCapture.Source.BODY_JSONPATH)
                        .withExpression("$.alsoMissing")),
                new LoadStep()
                    .withRequest(request().withMethod("GET")
                        .withPath("/a/{{iteration.captured.token}}/{{iteration.captured.missing}}")
                        .withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        HttpRequest secondStep = awaitSecondStep(sender, "/a/", 5_000L);
        assertThat(secondStep, is(notNullValue()));
        // 'token' -> defaultValue 'fallback'; 'missing' -> unset -> empty under Mustache.
        assertThat(secondStep.getPath().getValue(), is("/a/fallback/"));
    }

    @Test
    public void capturesDoNotLeakAcrossIterations() throws Exception {
        // The per-iteration map is fresh each iteration: a value captured in one iteration must not be
        // visible in another. Step 1 returns a per-global-iteration unique token; step 2 echoes it. If
        // the map leaked, distinct iterations would render the same (stale) token. One VU, 3 iterations.
        UniqueTokenSender sender = new UniqueTokenSender();
        LoadScenario scenario = new LoadScenario()
            .withName("capture-isolation")
            .withTemplateType(HttpTemplate.TemplateType.MUSTACHE)
            .withMaxRequests(6) // 3 iterations of 2 steps each
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withSteps(
                new LoadStep()
                    .withRequest(request().withMethod("POST").withPath("/login").withHeader("Host", "target"))
                    .withCapture(new org.mockserver.load.LoadCapture()
                        .withName("token")
                        .withSource(org.mockserver.load.LoadCapture.Source.BODY_JSONPATH)
                        .withExpression("$.token")),
                new LoadStep()
                    .withRequest(request().withMethod("GET")
                        .withPath("/echo/{{iteration.captured.token}}")
                        .withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        long deadline = System.currentTimeMillis() + 5_000L;
        while (sender.sent.size() < 6 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertThat(sender.sent.size(), greaterThanOrEqualTo(6));
        Set<String> echoPaths = sender.sent.stream()
            .map(r -> r.getPath() != null ? r.getPath().getValue() : "")
            .filter(p -> p.startsWith("/echo/"))
            .collect(java.util.stream.Collectors.toSet());
        // Each iteration captured its own unique token, so the echoed paths must differ across iterations
        // (no leak would produce >1 distinct path); a leak/shared map would collapse them.
        assertThat(echoPaths.size(), greaterThan(1));
    }

    /**
     * A sender that returns a UNIQUE token per dispatched {@code /login} (one VU runs iterations in
     * order, so each iteration's login gets a distinct token), and 200 otherwise. Used to prove the
     * per-iteration captured map does not leak across iterations.
     */
    private static final class UniqueTokenSender implements Function<HttpRequest, CompletableFuture<HttpResponse>> {
        final ConcurrentLinkedQueue<HttpRequest> sent = new ConcurrentLinkedQueue<>();
        private final AtomicLong loginCount = new AtomicLong();

        @Override
        public CompletableFuture<HttpResponse> apply(HttpRequest httpRequest) {
            sent.add(httpRequest);
            String path = httpRequest.getPath() != null ? httpRequest.getPath().getValue() : "";
            if (path != null && path.startsWith("/login")) {
                long n = loginCount.incrementAndGet();
                return CompletableFuture.completedFuture(
                    response().withStatusCode(200).withBody("{\"token\":\"t" + n + "\"}"));
            }
            return CompletableFuture.completedFuture(response().withStatusCode(200));
        }
    }

    // --- named load shape validation ---

    @Test
    public void validateAcceptsShapedProfile() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("shaped")
            .withProfile(LoadProfile.shaped(
                org.mockserver.load.LoadShape.rampHold(
                    org.mockserver.load.LoadShape.Metric.VU, 10, 5_000L, 20_000L)))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        assertThat(orchestrator.validate(scenario), is(nullValue()));
    }

    @Test
    public void validateRejectsProfileWithNeitherStagesNorShape() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("empty")
            .withProfile(new LoadProfile())
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        assertThat(orchestrator.validate(scenario),
            is("'profile' must contain either 'stages' or a 'shape'"));
    }

    @Test
    public void validateRejectsShapeThatExpandsToNoStages() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("empty-shape")
            .withProfile(LoadProfile.shaped(
                org.mockserver.load.LoadShape.stairs(
                    org.mockserver.load.LoadShape.Metric.VU, 5, 5, 0, 1_000L)))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        assertThat(orchestrator.validate(scenario), containsString("expands to no stages"));
    }

    @Test
    public void validateRejectsShapeWhoseExpansionExceedsVuCap() {
        org.mockserver.configuration.Configuration config =
            org.mockserver.configuration.Configuration.configuration().loadGenerationMaxVirtualUsers(50);
        orchestrator.setConfiguration(config);
        LoadScenario scenario = new LoadScenario()
            .withName("too-big")
            .withProfile(LoadProfile.shaped(
                org.mockserver.load.LoadShape.rampHold(
                    org.mockserver.load.LoadShape.Metric.VU, 500, 5_000L, 20_000L)))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        String error = orchestrator.validate(scenario);
        assertThat(error, containsString("500 virtual users"));
        assertThat(error, containsString("exceeding the maximum of 50"));
    }

    @Test
    public void validateRejectsShapeWhoseExpansionExceedsStageCap() {
        org.mockserver.configuration.Configuration config =
            org.mockserver.configuration.Configuration.configuration().loadGenerationMaxStages(5);
        orchestrator.setConfiguration(config);
        LoadScenario scenario = new LoadScenario()
            .withName("too-many-steps")
            .withProfile(LoadProfile.shaped(
                org.mockserver.load.LoadShape.stairs(
                    org.mockserver.load.LoadShape.Metric.VU, 1, 1, 10, 1_000L)))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        assertThat(orchestrator.validate(scenario),
            containsString("exceeds the maximum of 5"));
    }

    @Test(timeout = 10_000)
    public void validateRejectsStairsWithHugeStepCountBeforeExpanding() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration().loadGenerationMaxStages(20));
        LoadScenario scenario = new LoadScenario()
            .withName("huge-stairs")
            .withProfile(LoadProfile.shaped(
                org.mockserver.load.LoadShape.stairs(
                    org.mockserver.load.LoadShape.Metric.RATE, 1, 1, Integer.MAX_VALUE, 1L)))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        assertThat(orchestrator.validate(scenario),
            is("'profile.shape' expands to 2147483647 stages, which exceeds the maximum of 20"));
    }

    @Test
    public void validateRejectsShapeBeyondExpansionCeilingEvenWhenStageCapIsHigher() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration().loadGenerationMaxStages(Integer.MAX_VALUE));
        LoadScenario scenario = new LoadScenario()
            .withName("huge-stairs-high-cap")
            .withProfile(LoadProfile.shaped(
                org.mockserver.load.LoadShape.stairs(
                    org.mockserver.load.LoadShape.Metric.VU, 1, 0, Integer.MAX_VALUE, 1L)))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        assertThat(orchestrator.validate(scenario),
            containsString("which exceeds the maximum of " + org.mockserver.load.LoadShapes.MAX_EXPANDED_STAGES));
    }

    @Test
    public void validateAcceptsStairsWithinStageCap() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration().loadGenerationMaxStages(20));
        LoadScenario scenario = new LoadScenario()
            .withName("stairs-ok")
            .withProfile(LoadProfile.shaped(
                org.mockserver.load.LoadShape.stairs(
                    org.mockserver.load.LoadShape.Metric.VU, 1, 1, 20, 1_000L)))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        assertThat(orchestrator.validate(scenario), is(nullValue()));
    }

    @Test
    public void validateRejectsStageDurationsWhoseSumWouldOverflow() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("overflowing-durations")
            .withProfile(LoadProfile.of(
                LoadStage.constantVus(1, Long.MAX_VALUE),
                LoadStage.constantVus(1, Long.MAX_VALUE),
                LoadStage.constantVus(1, 3L)))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        assertThat(orchestrator.validate(scenario), containsString("total duration " + Long.MAX_VALUE + " ms exceeds the maximum"));
    }

    @Test
    public void validateRejectsNaNArrivalRate() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("nan-rate")
            .withProfile(LoadProfile.constantRate(Double.NaN, 1_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        assertThat(orchestrator.validate(scenario), is("stage[0] rate, startRate and endRate must be finite numbers"));
    }

    private static LoadScenario validScenario(String name) {
        return new LoadScenario()
            .withName(name)
            .withProfile(LoadProfile.constant(1, 1_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));
    }

    @Test
    public void validateRejectsNonFiniteStepWeights() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        for (double weight : new double[]{Double.NaN, Double.POSITIVE_INFINITY}) {
            for (LoadScenario.StepSelection selection : LoadScenario.StepSelection.values()) {
                LoadScenario scenario = validScenario("weight")
                    .withStepSelection(selection)
                    .withSteps(java.util.Arrays.asList(new LoadStep().withRequest(request().withPath("/api")).withWeight(weight)));

                assertThat(selection + " " + weight, orchestrator.validate(scenario), is("step[0].weight must be a finite number"));
            }
        }
    }

    @Test
    public void validateRejectsWeightedTotalThatOverflowsToInfinity() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = validScenario("weight-total")
            .withStepSelection(LoadScenario.StepSelection.WEIGHTED)
            .withSteps(java.util.Arrays.asList(
                new LoadStep().withRequest(request().withPath("/a")).withWeight(Double.MAX_VALUE),
                new LoadStep().withRequest(request().withPath("/b")).withWeight(Double.MAX_VALUE)));

        assertThat(orchestrator.validate(scenario), containsString("'steps' total weight must be a finite number > 0"));
    }

    @Test
    public void validateRejectsNonFinitePacingValueInEveryMode() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            LoadScenario paced = validScenario("pacing").withPacing(org.mockserver.load.LoadPacing.constantPacing(value));
            LoadScenario unpaced = validScenario("pacing-none").withPacing(org.mockserver.load.LoadPacing.loadPacing().withValue(value));

            assertThat(orchestrator.validate(paced), is("'pacing.value' must be a finite number"));
            assertThat(orchestrator.validate(unpaced), is("'pacing.value' must be a finite number"));
        }
    }

    @Test
    public void validateRejectsNonFiniteStageRates() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadStage[] stages = {
            LoadStage.constantRate(Double.POSITIVE_INFINITY, 1_000L),
            LoadStage.rampRate(Double.NEGATIVE_INFINITY, 10, 1_000L),
            LoadStage.rampRate(1, Double.NaN, 1_000L)
        };
        for (LoadStage stage : stages) {
            LoadScenario scenario = validScenario("rate").withProfile(LoadProfile.of(stage));

            assertThat(orchestrator.validate(scenario), is("stage[0] rate, startRate and endRate must be finite numbers"));
        }
    }

    @Test
    public void validateRejectsNonFiniteShapeParameters() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = validScenario("shape").withProfile(LoadProfile.shaped(
            org.mockserver.load.LoadShape.stairs(org.mockserver.load.LoadShape.Metric.RATE, 1, Double.NaN, 3, 1_000L)));
        LoadScenario negativeInfinity = validScenario("shape-vu").withProfile(LoadProfile.shaped(
            org.mockserver.load.LoadShape.spike(org.mockserver.load.LoadShape.Metric.VU, Double.NEGATIVE_INFINITY, 10, 1_000L, 1_000L, 1_000L)));

        assertThat(orchestrator.validate(scenario), is("'profile.shape.step' must be a finite number"));
        assertThat(orchestrator.validate(negativeInfinity), is("'profile.shape.baseline' must be a finite number"));
    }

    @Test
    public void validateRejectsVuShapeAboveIntRangeInsteadOfWrapping() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration().loadGenerationMaxVirtualUsers(50));
        LoadScenario scenario = validScenario("vu-wrap").withProfile(LoadProfile.shaped(
            org.mockserver.load.LoadShape.rampHold(org.mockserver.load.LoadShape.Metric.VU, 4_294_967_297.0, 1_000L, 1_000L)));

        assertThat(orchestrator.validate(scenario), containsString("exceeding the maximum of 50"));
    }

    @Test
    public void validateRejectsNonFiniteThreshold() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = validScenario("threshold").withThresholds(
            org.mockserver.load.LoadThreshold.loadThreshold()
                .withMetric(org.mockserver.load.LoadThreshold.Metric.ERROR_RATE)
                .withComparator(org.mockserver.slo.SloObjective.Comparator.LESS_THAN)
                .withThreshold(Double.NaN));

        assertThat(orchestrator.validate(scenario), is("thresholds[0].threshold must be a finite number"));
    }

    // -- Adaptive iteration pacing --

    /** Poll until the most-recent run's last applied pacing delay is within [min, max] (or timeout). */
    private boolean awaitPacingDelayBetween(long min, long max, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            long delay = orchestrator.lastRunPacingDelayMillis();
            if (delay >= min && delay <= max) {
                return true;
            }
            Thread.sleep(5);
        }
        long delay = orchestrator.lastRunPacingDelayMillis();
        return delay >= min && delay <= max;
    }

    @Test
    public void constantPacingDelaysNextIterationToTargetCycle() throws Exception {
        // One VU, instant sender, 200ms target cycle: each iteration's work finishes ~immediately, so the
        // reschedule of the next iteration waits ~the full 200ms cycle (allow jitter for clock granularity).
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("paced")
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withPacing(org.mockserver.load.LoadPacing.constantPacing(200.0))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));

        // First iteration fires immediately; the closed-model reschedule then applies a ~200ms pacing delay.
        assertThat("first request is sent without waiting on pacing", awaitAtLeast(sender, 1, 5_000L), is(true));
        assertThat("next iteration is paced to ~200ms (instant iteration => delay ~ full cycle)",
            awaitPacingDelayBetween(150L, 200L, 5_000L), is(true));
    }

    @Test
    public void constantThroughputPacingComputesCycleFromRate() throws Exception {
        // 5 iterations/sec per VU => a 200ms cycle, identical paced delay to CONSTANT_PACING(200ms).
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("throughput-paced")
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withPacing(org.mockserver.load.LoadPacing.constantThroughput(5.0))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));

        assertThat(awaitAtLeast(sender, 1, 5_000L), is(true));
        assertThat("5/s per VU => 200ms cycle => ~200ms paced delay on an instant iteration",
            awaitPacingDelayBetween(150L, 200L, 5_000L), is(true));
    }

    @Test
    public void pacingDelayIsZeroWhenIterationOverrunsCycle() throws Exception {
        // A 1ms target cycle with a sender that completes after 50ms: the iteration's work always overruns
        // the cycle, so the next iteration must start immediately (paced delay 0).
        DelayingSender sender = new DelayingSender(50L);
        try {
            LoadScenario scenario = new LoadScenario()
                .withName("overrun")
                .withProfile(LoadProfile.constant(1, 60_000L))
                .withPacing(org.mockserver.load.LoadPacing.constantPacing(1.0))
                .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

            assertThat(orchestrator.start(scenario, sender), is(nullValue()));

            // Wait until at least 2 iterations have been dispatched (so a reschedule has computed its delay),
            // then assert the last applied pacing delay was 0 (overrun => no wait).
            assertThat(awaitAtLeastDelaying(sender, 2, 5_000L), is(true));
            assertThat("overrun iteration reschedules immediately", orchestrator.lastRunPacingDelayMillis(), is(0L));
        } finally {
            sender.shutdown();
        }
    }

    @Test
    public void nonePacingReschedulesImmediately() throws Exception {
        // No pacing configured: the closed-model loop reschedules with zero delay (unchanged behaviour).
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("unpaced")
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/api").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));

        // Many iterations accrue quickly with no pacing; the recorded pacing delay stays 0 throughout.
        assertThat(awaitAtLeast(sender, 5, 5_000L), is(true));
        assertThat(orchestrator.lastRunPacingDelayMillis(), is(0L));
    }

    @Test
    public void validateRejectsNonPositivePacingValue() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("bad-pacing")
            .withProfile(LoadProfile.constant(1, 1_000L))
            .withPacing(new org.mockserver.load.LoadPacing()
                .withMode(org.mockserver.load.LoadPacing.Mode.CONSTANT_PACING)
                .withValue(0.0))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        assertThat(orchestrator.validate(scenario),
            is("'pacing.value' must be > 0 when 'pacing.mode' is CONSTANT_PACING"));
    }

    @Test
    public void validateAcceptsNonePacing() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("none-pacing")
            .withProfile(LoadProfile.constant(1, 1_000L))
            .withPacing(new org.mockserver.load.LoadPacing()
                .withMode(org.mockserver.load.LoadPacing.Mode.NONE)
                .withValue(0.0))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        assertThat(orchestrator.validate(scenario), is(nullValue()));
    }

    private boolean awaitAtLeastDelaying(DelayingSender sender, int n, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (sender.sent.size() >= n) {
                return true;
            }
            Thread.sleep(5);
        }
        return sender.sent.size() >= n;
    }

    // --- data feeders ---

    private static java.util.Map<String, String> feederRow(String key, String value) {
        java.util.Map<String, String> row = new java.util.LinkedHashMap<>();
        row.put(key, value);
        return row;
    }

    @Test
    public void feederCircularCyclesRowsAcrossIterations() throws Exception {
        // 2 rows, CIRCULAR: iteration k uses rows[k % 2], so the rendered paths alternate user=a / user=b
        // and the selected value appears in the rendered request. One VU, several iterations.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("feeder-circular")
            .withTemplateType(HttpTemplate.TemplateType.MUSTACHE)
            .withMaxRequests(6)
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withFeeder(new org.mockserver.load.LoadFeeder()
                .withRows(java.util.Arrays.asList(feederRow("user", "a"), feederRow("user", "b")))
                .withStrategy(org.mockserver.load.LoadFeeder.Strategy.CIRCULAR))
            .withSteps(new LoadStep().withRequest(
                request().withPath("/u/{{iteration.data.user}}").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 6, 5_000L), is(true));

        java.util.List<String> paths = sender.sent.stream()
            .map(r -> r.getPath().getValue())
            .filter(p -> p.startsWith("/u/"))
            .limit(6)
            .collect(java.util.stream.Collectors.toList());
        // Single VU runs iterations strictly in order, so the cycle is a, b, a, b, ...
        assertThat(paths.get(0), is("/u/a"));
        assertThat(paths.get(1), is("/u/b"));
        assertThat(paths.get(2), is("/u/a"));
        assertThat(paths.get(3), is("/u/b"));
    }

    @Test
    public void feederRandomSelectsWithinTheDataset() throws Exception {
        // RANDOM: every rendered value must be one of the dataset rows (no out-of-range selection).
        // Drive enough iterations that the assertion is meaningful without being order-dependent.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("feeder-random")
            .withTemplateType(HttpTemplate.TemplateType.MUSTACHE)
            .withMaxRequests(30)
            .withProfile(LoadProfile.constant(2, 60_000L))
            .withFeeder(new org.mockserver.load.LoadFeeder()
                .withRows(java.util.Arrays.asList(
                    feederRow("user", "a"), feederRow("user", "b"), feederRow("user", "c")))
                .withStrategy(org.mockserver.load.LoadFeeder.Strategy.RANDOM))
            .withSteps(new LoadStep().withRequest(
                request().withPath("/u/{{iteration.data.user}}").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 30, 5_000L), is(true));

        Set<String> allowed = new java.util.HashSet<>(java.util.Arrays.asList("/u/a", "/u/b", "/u/c"));
        for (HttpRequest sent : sender.sent) {
            String path = sent.getPath().getValue();
            if (path.startsWith("/u/")) {
                assertThat("random selection must stay within the dataset", allowed.contains(path), is(true));
            }
        }
    }

    @Test
    public void feederSequentialUsesEachRowOnceThenCompletes() throws Exception {
        // SEQUENTIAL with 4 rows: exactly 4 iterations run (4 requests), each row once in order, then
        // the run COMPLETES (dataset exhausted) even though the profile duration is far from elapsed.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("feeder-sequential")
            .withTemplateType(HttpTemplate.TemplateType.MUSTACHE)
            .withProfile(LoadProfile.constant(1, 600_000L))
            .withFeeder(new org.mockserver.load.LoadFeeder()
                .withRows(java.util.Arrays.asList(
                    feederRow("user", "a"), feederRow("user", "b"),
                    feederRow("user", "c"), feederRow("user", "d")))
                .withStrategy(org.mockserver.load.LoadFeeder.Strategy.SEQUENTIAL))
            .withSteps(new LoadStep().withRequest(
                request().withPath("/u/{{iteration.data.user}}").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));

        long deadline = System.currentTimeMillis() + 5_000L;
        while (orchestrator.getStatus() != null
            && org.mockserver.load.LoadScenarioState.RUNNING == orchestrator.getStatus().state
            && System.currentTimeMillis() < deadline) {
            orchestrator.tickNow();
            Thread.sleep(5);
        }

        assertThat("run completes once the dataset is exhausted",
            orchestrator.getStatus().state, is(org.mockserver.load.LoadScenarioState.COMPLETED));
        java.util.List<String> paths = sender.sent.stream()
            .map(r -> r.getPath().getValue())
            .filter(p -> p.startsWith("/u/"))
            .collect(java.util.stream.Collectors.toList());
        // Each of the 4 rows used exactly once, in order — no row replayed, none skipped.
        assertThat(paths, is(java.util.Arrays.asList("/u/a", "/u/b", "/u/c", "/u/d")));
    }

    @Test
    public void feederDataAsJsonResolvesIntoRows() throws Exception {
        // data + format=JSON parses into rows server-side; CIRCULAR cycles them as for inline rows.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("feeder-json")
            .withTemplateType(HttpTemplate.TemplateType.MUSTACHE)
            .withMaxRequests(4)
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withFeeder(new org.mockserver.load.LoadFeeder()
                .withFormat(org.mockserver.load.LoadFeeder.Format.JSON)
                .withData("[{\"user\":\"alice\"},{\"user\":\"bob\"}]"))
            .withSteps(new LoadStep().withRequest(
                request().withPath("/u/{{iteration.data.user}}").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 4, 5_000L), is(true));
        Set<String> paths = sender.sent.stream()
            .map(r -> r.getPath().getValue())
            .filter(p -> p.startsWith("/u/"))
            .collect(java.util.stream.Collectors.toSet());
        assertThat(paths, hasItems("/u/alice", "/u/bob"));
    }

    @Test
    public void feederDataAsCsvResolvesIntoRows() throws Exception {
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("feeder-csv-run")
            .withTemplateType(HttpTemplate.TemplateType.MUSTACHE)
            .withMaxRequests(4)
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withFeeder(new org.mockserver.load.LoadFeeder()
                .withFormat(org.mockserver.load.LoadFeeder.Format.CSV)
                .withData("user\nalice\nbob"))
            .withSteps(new LoadStep().withRequest(
                request().withPath("/u/{{iteration.data.user}}").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 4, 5_000L), is(true));
        Set<String> paths = sender.sent.stream()
            .map(r -> r.getPath().getValue())
            .filter(p -> p.startsWith("/u/"))
            .collect(java.util.stream.Collectors.toSet());
        assertThat(paths, hasItems("/u/alice", "/u/bob"));
    }

    @Test
    public void feederDataResolvesInPathBodyAndHeader() throws Exception {
        // The selected row is available to path, body AND header templates within the iteration.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("feeder-pbh")
            .withTemplateType(HttpTemplate.TemplateType.MUSTACHE)
            .withMaxRequests(1)
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withFeeder(org.mockserver.load.LoadFeeder.loadFeeder(
                java.util.Collections.singletonList(feederRow("user", "zoe"))))
            .withSteps(new LoadStep().withRequest(
                request().withMethod("POST")
                    .withPath("/u/{{iteration.data.user}}")
                    .withHeader("Host", "target")
                    .withBody("{\"u\":\"{{iteration.data.user}}\"}")
                    .withHeader("X-User", "{{iteration.data.user}}")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 1, 5_000L), is(true));
        HttpRequest sent = sender.sent.stream()
            .filter(r -> r.getPath().getValue().startsWith("/u/"))
            .findFirst().orElse(null);
        assertThat(sent, is(notNullValue()));
        assertThat(sent.getPath().getValue(), is("/u/zoe"));
        assertThat(sent.getBodyAsString(), containsString("zoe"));
        assertThat(sent.getFirstHeader("X-User"), is("zoe"));
    }

    @Test
    public void validateRejectsFeederWithNoRows() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("empty-feeder")
            .withProfile(LoadProfile.constant(1, 1_000L))
            .withFeeder(new org.mockserver.load.LoadFeeder()
                .withRows(java.util.Collections.emptyList()))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        assertThat(orchestrator.validate(scenario), containsString("'feeder' must resolve to at least one row"));
    }

    @Test
    public void validateRejectsMalformedFeederData() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("bad-feeder")
            .withProfile(LoadProfile.constant(1, 1_000L))
            .withFeeder(new org.mockserver.load.LoadFeeder()
                .withFormat(org.mockserver.load.LoadFeeder.Format.JSON)
                .withData("{not an array"))
            .withSteps(new LoadStep().withRequest(request().withPath("/api")));

        assertThat(orchestrator.validate(scenario), containsString("not a valid JSON array"));
    }

    // ---- Weighted step selection ----------------------------------------------------------------

    @Test
    public void sequentialSelectionRunsAllStepsInOrderEachIteration() throws Exception {
        // SEQUENTIAL (the default) is unchanged: a multi-step iteration fires all steps in declared
        // order. One iteration (maxRequests = 3 == the three steps) so ordering is unambiguous.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("sequential-order")
            .withMaxRequests(3)
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withSteps(
                new LoadStep().withRequest(request().withPath("/a").withHeader("Host", "target")),
                new LoadStep().withRequest(request().withPath("/b").withHeader("Host", "target")),
                new LoadStep().withRequest(request().withPath("/c").withHeader("Host", "target")));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 3, 5_000L), is(true));

        java.util.List<String> paths = sender.sent.stream()
            .map(r -> r.getPath().getValue())
            .limit(3)
            .collect(java.util.stream.Collectors.toList());
        assertThat("default SEQUENTIAL runs all steps in declared order",
            paths, contains("/a", "/b", "/c"));
    }

    @Test
    public void weightedSelectionRunsExactlyOneStepPerIteration() throws Exception {
        // Two steps, WEIGHTED. With maxRequests = 1, exactly one step (one request) is dispatched for
        // the single iteration — proving a WEIGHTED iteration runs ONE step, not all of them.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("weighted-one-step")
            .withStepSelection(LoadScenario.StepSelection.WEIGHTED)
            .withMaxRequests(1)
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withSteps(
                new LoadStep().withRequest(request().withPath("/a").withHeader("Host", "target")).withWeight(1.0),
                new LoadStep().withRequest(request().withPath("/b").withHeader("Host", "target")).withWeight(1.0));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 1, 5_000L), is(true));
        // Let the scheduler settle: a WEIGHTED iteration must not fire a second step after the first.
        Thread.sleep(200);
        for (int i = 0; i < 5; i++) {
            orchestrator.tickNow();
        }
        assertThat("a WEIGHTED iteration dispatches exactly one step", sender.sent.size(), is(1));
        String path = sender.sent.peek().getPath().getValue();
        assertThat(path, anyOf(is("/a"), is("/b")));
    }

    @Test
    public void weightedSelectionDistributesProportionallyToWeights() throws Exception {
        // Heavy 8:1:1 weighting over many iterations: every step is hit at least once and the dominant
        // step is hit far more often. Tolerant bounds (and a deterministic-clock-driven volume) keep it
        // non-flaky — we assert ordering of frequencies and presence, not exact ratios. The volume stays
        // under the default 500 req/s RPS-token cap, which (with the frozen test clock) bounds the whole
        // run to one window — so 450 requests dispatch deterministically without advancing the clock.
        RecordingSender sender = new RecordingSender();
        LoadScenario scenario = new LoadScenario()
            .withName("weighted-distribution")
            .withStepSelection(LoadScenario.StepSelection.WEIGHTED)
            .withMaxRequests(450)
            .withProfile(LoadProfile.constant(8, 60_000L))
            .withSteps(
                new LoadStep().withRequest(request().withPath("/heavy").withHeader("Host", "target")).withWeight(8.0),
                new LoadStep().withRequest(request().withPath("/light1").withHeader("Host", "target")).withWeight(1.0),
                new LoadStep().withRequest(request().withPath("/light2").withHeader("Host", "target")).withWeight(1.0));

        assertThat(orchestrator.start(scenario, sender), is(nullValue()));
        assertThat(awaitAtLeast(sender, 450, 10_000L), is(true));

        java.util.Map<String, Long> counts = sender.sent.stream()
            .map(r -> r.getPath().getValue())
            .collect(java.util.stream.Collectors.groupingBy(p -> p, java.util.stream.Collectors.counting()));
        long heavy = counts.getOrDefault("/heavy", 0L);
        long light1 = counts.getOrDefault("/light1", 0L);
        long light2 = counts.getOrDefault("/light2", 0L);

        // Every step is selected at least once.
        assertThat("heavy step hit", heavy, greaterThan(0L));
        assertThat("light1 step hit", light1, greaterThan(0L));
        assertThat("light2 step hit", light2, greaterThan(0L));
        // The dominant (weight 8 of 10) step dominates: well over half of all picks, and clearly more
        // than either light step. Expected ~80%; assert a wide-margin lower bound to avoid flakiness.
        long total = heavy + light1 + light2;
        assertThat("heavily-weighted step dominates", heavy, greaterThan(total / 2));
        assertThat(heavy, greaterThan(light1));
        assertThat(heavy, greaterThan(light2));
    }

    @Test
    public void validateRejectsWeightedWithNonPositiveWeight() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("weighted-zero")
            .withStepSelection(LoadScenario.StepSelection.WEIGHTED)
            .withProfile(LoadProfile.constant(1, 1_000L))
            .withSteps(
                new LoadStep().withRequest(request().withPath("/a")).withWeight(1.0),
                new LoadStep().withRequest(request().withPath("/b")).withWeight(0.0));

        assertThat(orchestrator.validate(scenario),
            containsString("step[1].weight must be > 0 when stepSelection is WEIGHTED"));
    }

    @Test
    public void validateRejectsWeightedWithNegativeWeight() {
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("weighted-negative")
            .withStepSelection(LoadScenario.StepSelection.WEIGHTED)
            .withProfile(LoadProfile.constant(1, 1_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/a")).withWeight(-2.0));

        assertThat(orchestrator.validate(scenario),
            containsString("step[0].weight must be > 0 when stepSelection is WEIGHTED"));
    }

    @Test
    public void validateAcceptsWeightedWithDefaultedAbsentWeights() {
        // Absent weights default to 1.0 in WEIGHTED mode, so a scenario with no explicit weights is valid.
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("weighted-defaulted")
            .withStepSelection(LoadScenario.StepSelection.WEIGHTED)
            .withProfile(LoadProfile.constant(1, 1_000L))
            .withSteps(
                new LoadStep().withRequest(request().withPath("/a")),
                new LoadStep().withRequest(request().withPath("/b")));

        assertThat(orchestrator.validate(scenario), is(nullValue()));
    }

    @Test
    public void validateAcceptsSequentialWithWeightsPresentButIgnored() {
        // SEQUENTIAL ignores weights — even a zero/negative weight is accepted (and unused).
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        LoadScenario scenario = new LoadScenario()
            .withName("sequential-with-weights")
            .withProfile(LoadProfile.constant(1, 1_000L))
            .withSteps(
                new LoadStep().withRequest(request().withPath("/a")).withWeight(5.0),
                new LoadStep().withRequest(request().withPath("/b")).withWeight(0.0));

        assertThat(orchestrator.validate(scenario), is(nullValue()));
    }

    @Test
    public void validateAcceptsTemplatizedRecordingGeneratedWeightedScenario() {
        // A TEMPLATIZED record-to-load generation now emits WEIGHTED selection with per-step weights ==
        // hit counts (always >= 1), so the generated scenario must pass validation.
        orchestrator.setConfiguration(org.mockserver.configuration.Configuration.configuration());
        java.util.List<org.mockserver.model.RequestDefinition> recorded = new java.util.ArrayList<>(java.util.Arrays.asList(
            request().withMethod("GET").withPath("/orders/1"),
            request().withMethod("GET").withPath("/orders/2"),
            request().withMethod("GET").withPath("/users/9")));
        LoadScenario scenario = org.mockserver.load.LoadScenarioFromRecording.generate(
            "rec", recorded, org.mockserver.load.LoadScenarioFromRecording.Mode.TEMPLATIZED, null, null,
            LoadProfile.constant(1, 1_000L));

        assertThat(scenario.getStepSelection(), is(LoadScenario.StepSelection.WEIGHTED));
        assertThat(orchestrator.validate(scenario), is(nullValue()));
    }

    @Test
    public void countsARequestForwardProxyBlockPrivateNetworksRefusedAsBlockedAndLogsItOncePerRun() throws Exception {
        org.mockserver.configuration.Configuration config =
            org.mockserver.configuration.Configuration.configuration().metricsEnabled(true);
        new Metrics(config);
        orchestrator.setConfiguration(config);
        java.util.List<org.mockserver.log.model.LogEntry> logged = new java.util.concurrent.CopyOnWriteArrayList<>();
        orchestrator.setMockServerLogger(new org.mockserver.logging.MockServerLogger(LoadScenarioOrchestrator.class) {
            @Override
            public boolean isEnabledForInstance(org.slf4j.event.Level level) {
                return true;
            }

            @Override
            public void logEvent(org.mockserver.log.model.LogEntry logEntry) {
                logged.add(logEntry);
            }
        });
        java.util.concurrent.atomic.AtomicInteger refused = new java.util.concurrent.atomic.AtomicInteger();
        Function<HttpRequest, CompletableFuture<HttpResponse>> refusing = request -> {
            refused.incrementAndGet();
            return CompletableFuture.failedFuture(new org.mockserver.proxyconfiguration.ForwardTargetBlockedException("Forward to loopback address blocked: 127.0.0.1"));
        };
        LoadScenario scenario = new LoadScenario().withName("refused")
            .withProfile(LoadProfile.constant(1, 60_000L))
            .withSteps(new LoadStep().withRequest(request().withPath("/a").withHeader("Host", "127.0.0.1"))
                .withThinkTime(org.mockserver.model.Delay.milliseconds(5)));

        try {
            assertThat(orchestrator.start(scenario, refusing), is(nullValue()));
            String runId = orchestrator.statusFor("refused").runId;
            long deadline = System.currentTimeMillis() + 5_000L;
            while (refused.get() < 5 && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
            assertThat(refused.get(), greaterThanOrEqualTo(5));

            assertThat(Metrics.getLoadErrorCount("refused", runId, LoadScenarioOrchestrator.BLOCKED), greaterThanOrEqualTo(4L));
            assertThat(Metrics.getLoadErrorCount("refused", runId, "connection"), is(0L));
            java.util.List<org.mockserver.log.model.LogEntry> warnings = logged.stream()
                .filter(entry -> entry.getLogLevel() == org.slf4j.event.Level.WARN)
                .collect(java.util.stream.Collectors.toList());
            assertThat(warnings, hasSize(1));
            assertThat(warnings.get(0).getMessageFormat(), is("load scenario:{}request blocked by SSRF policy:{}"));
            assertThat(String.valueOf(warnings.get(0).getArguments()[1]), containsString("Forward to loopback address blocked: 127.0.0.1"));
        } finally {
            orchestrator.stop("refused");
        }
    }
}
