package org.mockserver.log;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.verify.Verification;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.atMost;
import static org.mockserver.verify.VerificationTimes.never;

/**
 * Verifies the in-flight (ring backlog) byte bound added to {@link MockServerEventLog}: the pool of
 * bytes held by log entries that have been published to the disruptor ring but not yet processed by
 * the single consumer. The deque byte budget ({@code maxEventLogSizeInBytes}) bounds only what has
 * already been RETAINED after processing and cannot see the ring; under sustained large-body ingress
 * the ring backlog — one full body per pre-allocated slot — is what exhausts the heap, so the same
 * budget is applied to the in-flight pool and over-budget entries are dropped rather than allowed to
 * OOM the server.
 * <p>
 * Three properties are covered: the admission decision (pure {@link MockServerEventLog#wouldExceedInFlightBudget}
 * seam), the increment/decrement accounting balancing across ring reuse, and — critically — that a
 * DROPPED entry taints the fail-closed verify path exactly as an eviction does, so an upper-bound
 * verify cannot silently pass on evidence that was dropped before it was ever recorded.
 */
public class MockServerEventLogInFlightBytesTest {

    // ---- wouldExceedInFlightBudget: the admission boundary rules ----

    @Test
    public void shouldNotRejectWhenBudgetDisabled() {
        // budget <= 0 disables the in-flight bound (ring bounded by slot count only), like the deque's
        // "0 disables the byte budget" contract — so it never rejects however large the backlog.
        assertThat(MockServerEventLog.wouldExceedInFlightBudget(0L, 1_000_000L, 1_000_000L), is(false));
        assertThat(MockServerEventLog.wouldExceedInFlightBudget(-1L, 1_000_000L, 1_000_000L), is(false));
    }

    @Test
    public void shouldNotRejectABodylessEntry() {
        // a control / diagnostic entry with no body costs nothing to hold in flight
        assertThat(MockServerEventLog.wouldExceedInFlightBudget(100L, 100L, 0L), is(false));
    }

    @Test
    public void shouldAlwaysAdmitIntoAnEmptyBacklogEvenWhenOversized() {
        // never reject into an empty backlog: a single body larger than the whole budget is still
        // admitted (mirrors the deque's "one oversized element is still retained" rule)
        assertThat(MockServerEventLog.wouldExceedInFlightBudget(100L, 0L, 10_000L), is(false));
    }

    @Test
    public void shouldRejectOnceInFlightPlusIncomingExceedsBudget() {
        // 60 already in flight + 50 incoming = 110 > 100 -> reject
        assertThat(MockServerEventLog.wouldExceedInFlightBudget(100L, 60L, 50L), is(true));
    }

    @Test
    public void shouldAdmitExactlyUpToTheBudget() {
        // 60 + 40 = 100, not over 100 -> admit (strict greater-than boundary)
        assertThat(MockServerEventLog.wouldExceedInFlightBudget(100L, 60L, 40L), is(false));
        // one more byte tips it over
        assertThat(MockServerEventLog.wouldExceedInFlightBudget(100L, 60L, 41L), is(true));
    }

    // ---- end-to-end: the increment (publish) / decrement (process) accounting balances across reuse ----

    @Test
    public void shouldReturnInFlightBytesToZeroAfterTheRingDrains() {
        // large budget so nothing is dropped: every published body is counted in, then counted out as
        // the consumer processes it. Publish MORE entries than the ring has slots (maxLogEntries 1000
        // => a 1024-slot ring) so slots are reused many times over — proving the accounting does not
        // drift on slot reuse. After a drain the in-flight total must be exactly zero.
        Configuration configuration = configuration()
            .maxLogEntries(1000)
            .maxEventLogSizeInBytes(64L * 1024 * 1024)
            .maxLoggedBodyBytes(0);
        MockServerEventLog log = asynchronousEventLog(configuration);
        try {
            for (int i = 0; i < 1500; i++) {
                log.add(receivedRequestWithBody("/load", 10_000));
            }
            drain(log);

            assertThat(log.getInFlightBytes(), is(0L));
        } finally {
            log.stop();
        }
    }

    @Test
    public void shouldReturnInFlightBytesToZeroWhenEntriesShareAReleasedRequestBodyAtWarn() throws Exception {
        shouldReturnInFlightBytesToZeroWhenEntriesShareAReleasedRequestBody(Level.WARN);
    }

    @Test
    public void shouldReturnInFlightBytesToZeroWhenEntriesShareAReleasedRequestBodyAtInfo() throws Exception {
        shouldReturnInFlightBytesToZeroWhenEntriesShareAReleasedRequestBody(Level.INFO);
    }

    // A mocked exchange logs RECEIVED_REQUEST and EXPECTATION_RESPONSE for the SAME HttpRequest. Both are
    // weighed at publish while the matcher's decoded body String is still cached; processing the first
    // releases that String, so the second must still be subtracted at its publish-time weight.
    private void shouldReturnInFlightBytesToZeroWhenEntriesShareAReleasedRequestBody(Level logLevel) throws Exception {
        Configuration configuration = configuration()
            .logLevel(logLevel)
            .maxLogEntries(1000)
            .maxEventLogSizeInBytes(64L * 1024 * 1024)
            .maxLoggedBodyBytes(0);
        MockServerEventLog log = asynchronousEventLog(configuration);
        try {
            CountDownLatch release = blockConsumer(log);
            for (int i = 0; i < 20; i++) {
                addMockedExchange(log, "/shared", 10_000);
            }
            release.countDown();
            drain(log);

            assertThat(log.getDroppedLogEventCount(), is(0L));
            assertThat(log.getInFlightBytes(), is(0L));
            log.reset();
            assertThat(log.getInFlightBytes(), is(0L));
        } finally {
            log.stop();
        }
    }

    @Test
    public void shouldKeepRecordingUnderSustainedTenKilobyteJsonExchangesAtInfo() throws Exception {
        // Each batch of 20 exchanges holds ~1.3 MB in flight, well inside the 4 MB budget, so nothing is
        // legitimately over budget. If each exchange left even its released request String (~20 KB) as
        // phantom in-flight bytes, the budget would fill within ~200 exchanges and every later entry
        // would be dropped. Compact format keeps stdout small; the full message is still rendered.
        Configuration configuration = configuration()
            .logLevel(Level.INFO)
            .compactLogFormat(true)
            .maxLogEntries(1000)
            .maxEventLogSizeInBytes(4L * 1024 * 1024)
            .maxLoggedBodyBytes(0);
        MockServerEventLog log = asynchronousEventLog(configuration);
        try {
            for (int batch = 0; batch < 150; batch++) {
                CountDownLatch release = blockConsumer(log);
                for (int i = 0; i < 20; i++) {
                    addMockedExchange(log, "/load", 10_240);
                }
                release.countDown();
                drain(log);
            }
            addMockedExchange(log, "/final", 10_240);
            CompletableFuture<List<LogEntry>> recorded = new CompletableFuture<>();
            log.retrieveMessageLogEntries(request("/final"), recorded::complete);

            assertThat(log.getDroppedLogEventCount(), is(0L));
            assertThat(recorded.get(60, SECONDS).isEmpty(), is(false));
            assertThat(log.getInFlightBytes(), is(0L));
        } finally {
            log.stop();
        }
    }

    @Test
    public void shouldNeverDropWhenBudgetDisabled() throws Exception {
        // With the budget disabled (0) the accounting still runs (add() computes estimatedHeapSize and
        // tracks in/out unconditionally, so the counter cannot drift on a runtime budget change) — but
        // wouldExceedInFlightBudget always returns false, so NOTHING is ever dropped, even when a large
        // backlog is deliberately built up behind a held consumer. That "no drops despite a big
        // backlog" is the real disabled property, and it distinguishes disabled from an enabled bound;
        // the after-drain zero alone cannot, because it is true whether or not tracking ran.
        Configuration configuration = configuration()
            .maxLogEntries(1000)
            .maxEventLogSizeInBytes(0L)
            .maxLoggedBodyBytes(0);
        MockServerEventLog log = asynchronousEventLog(configuration);
        try {
            CountDownLatch release = blockConsumer(log);
            for (int i = 0; i < 20; i++) {
                log.add(receivedRequestWithBody("/x", 100_000)); // ~2 MB backlog held in the ring
            }
            assertThat(log.getDroppedLogEventCount(), is(0L));
            release.countDown();
            drain(log);

            assertThat(log.getInFlightBytes(), is(0L));
        } finally {
            log.stop();
        }
    }

    // ---- CRITICAL: a dropped body-bearing entry must fail the upper-bound verify closed ----

    @Test
    public void shouldFailClosedUpperBoundVerifyAfterDroppingABodyBearingEntry() throws Exception {
        // A tiny in-flight budget so that, with the consumer held, admitting one oversized entry tips
        // the backlog over budget and every subsequent body-bearing entry is DROPPED before publish.
        Configuration configuration = withInFlightCap(1000L)
            .maxLogEntries(1000)
            .maxEventLogSizeInBytes(500L)
            .maxLoggedBodyBytes(0);
        MockServerEventLog log = asynchronousEventLog(configuration);
        try {
            CountDownLatch release = blockConsumer(log);

            // first entry admitted into the empty backlog (even though > budget); it pushes in-flight > budget
            log.add(receivedRequestWithBody("/admitted", 2000));
            // subsequent body-bearing entries now exceed the in-flight budget and are dropped (never recorded)
            for (int i = 0; i < 5; i++) {
                log.add(receivedRequestWithBody("/dropped", 2000));
            }
            // drops are accounted on the producer thread inside add(), so they are already observable
            assertThat(log.getDroppedLogEventCount(), greaterThan(0L));
            // and attributed to the in-flight byte cap, not to a full ring
            assertThat(log.getDroppedLogEventCount(MockServerEventLog.DropReason.IN_FLIGHT_BYTES), is(log.getDroppedLogEventCount()));
            assertThat(log.getDroppedLogEventCount(MockServerEventLog.DropReason.RING_FULL), is(0L));

            release.countDown();
            drain(log);

            // /dropped never reached the log. A naive never() sees matchedCount==0 and would PASS — a
            // silent false green. It MUST fail closed: the request may have arrived and been dropped.
            String never = verify(log, verification().withRequest(request("/dropped")).withTimes(never()));
            assertThat(never, is(not("")));
            assertThat(never, containsString("could not be verified"));
            assertThat(never, containsString("DROPPED"));
            // the message quotes the in-flight cap in force, not the (smaller) retention budget
            assertThat(never, containsString("in-flight byte budget of 1000 bytes"));
            // atMost(0) is the same upper bound and must also fail closed
            assertThat(verify(log, verification().withRequest(request("/dropped")).withTimes(atMost(0))), containsString("DROPPED"));

            // after a reset the drop taint clears, so a later upper-bound verify can pass again
            log.reset();
            assertThat(verify(log, verification().withRequest(request("/dropped")).withTimes(never())), is(""));
        } finally {
            log.stop();
        }
    }

    // ---- the in-flight cap is separate from, and never smaller than, the retention budget ----

    @Test
    public void shouldNotDropABurstThatExceedsASmallRetentionBudget() throws Exception {
        // A retention budget that holds three entries, and a burst of twenty arriving while the
        // consumer is busy. Retention still evicts down to its own budget once the burst is processed,
        // but the burst itself must be recorded, not dropped, because the in-flight cap does not
        // shrink with the retention budget.
        int bodyBytes = 10_000;
        int burst = 20;
        long weight = receivedRequestWithBody("/burst", bodyBytes).estimatedHeapSize();
        long retention = weight * 3;
        Configuration configuration = configuration()
            .logLevel(Level.WARN)
            .maxLogEntries(1000)
            .maxEventLogSizeInBytes(retention)
            .maxLoggedBodyBytes(0);
        MockServerEventLog log = asynchronousEventLog(configuration);
        try {
            assertThat(log.getMaxRetainedBytes(), is(retention));
            assertThat(log.getMaxInFlightBytes(), is(greaterThan(burst * weight)));

            CountDownLatch release = blockConsumer(log);
            for (int i = 0; i < burst; i++) {
                log.add(receivedRequestWithBody("/burst", bodyBytes));
            }
            assertThat(log.getDroppedLogEventCount(), is(0L));
            release.countDown();
            drain(log);

            assertThat(log.getDroppedLogEventCount(), is(0L));
            assertThat(log.getEvictedLogEntryCount(), is(greaterThan(0L)));
            assertThat(log.getRetainedEntryCount() + log.getEvictedLogEntryCount(), is((long) burst));
            assertThat(log.getRetainedBytes(), is(lessThanOrEqualTo(retention)));
            assertThat(log.getInFlightBytes(), is(0L));
        } finally {
            log.stop();
        }
    }

    @Test
    public void shouldTakeTheInFlightCapFromConfigurationAndReapplyItOnACapacityChange() {
        Configuration configuration = configuration()
            .logLevel(Level.WARN)
            .maxLogEntries(1000)
            .maxLoggedBodyBytes(0);
        MockServerEventLog log = asynchronousEventLog(configuration);
        try {
            // by default the in-flight cap is the larger heap-derived cap, not the retention budget
            assertThat(log.getMaxInFlightBytes(), is(configuration.maxEventLogInFlightBytes()));
            assertThat(log.getMaxInFlightBytes(), is(greaterThan(log.getMaxRetainedBytes())));

            long large = configuration.maxEventLogInFlightBytes() + 4096L;
            configuration.maxEventLogSizeInBytes(large);
            log.applyConfigurationCapacity();
            assertThat(log.getMaxRetainedBytes(), is(large));
            assertThat(log.getMaxInFlightBytes(), is(large));

            configuration.maxEventLogSizeInBytes(0L);
            log.applyConfigurationCapacity();
            assertThat(log.getMaxInFlightBytes(), is(0L));
        } finally {
            log.stop();
        }
    }

    @Test
    public void shouldKeepTheHeapDerivedInFlightFloorWhenARuntimeChangeShrinksTheRetentionBudget() {
        Configuration configuration = configuration()
            .logLevel(Level.WARN)
            .maxLogEntries(1000)
            .maxLoggedBodyBytes(0);
        MockServerEventLog log = asynchronousEventLog(configuration);
        try {
            long warnFloor = configuration.maxEventLogInFlightBytes();
            assertThat(warnFloor, is((ConfigurationProperties.heapAvailableInKB() / 7) * 1024L));
            long small = 64L * 1024;
            assertThat(small, is(lessThan(warnFloor)));

            // a live change below the floor shrinks retention only; the in-flight cap is re-derived
            configuration.maxEventLogSizeInBytes(small);
            log.applyConfigurationCapacity();
            assertThat(log.getMaxRetainedBytes(), is(small));
            assertThat(log.getMaxInFlightBytes(), is(configuration.maxEventLogInFlightBytes()));
            assertThat(log.getMaxInFlightBytes(), is(warnFloor));

            // a WARN -> INFO change keeps the same heap/7 floor
            configuration.logLevel(Level.INFO);
            log.applyConfigurationCapacity();
            assertThat(configuration.maxEventLogInFlightBytes(), is(warnFloor));
            assertThat(log.getMaxRetainedBytes(), is(small));
            assertThat(log.getMaxInFlightBytes(), is(warnFloor));

            // an INFO -> DEBUG change re-derives the floor at the heap/12 default
            configuration.logLevel(Level.DEBUG);
            log.applyConfigurationCapacity();
            long debugFloor = configuration.maxEventLogInFlightBytes();
            assertThat(debugFloor, is((ConfigurationProperties.heapAvailableInKB() / 12) * 1024L));
            assertThat(debugFloor, is(lessThan(warnFloor)));
            assertThat(debugFloor, is(greaterThan(small)));
            assertThat(log.getMaxRetainedBytes(), is(small));
            assertThat(log.getMaxInFlightBytes(), is(debugFloor));
        } finally {
            log.stop();
        }
    }

    // ---- RETAINED (post-processing) site: the deque count/bytes behind the retained_* gauges ----

    @Test
    public void shouldTrackRetainedEntriesAndBytesAfterTheRingDrains() {
        // The SECOND retention site: once the consumer processes a body-bearing entry it moves from
        // the in-flight ring into the retained deque. The retained gauges (retained_entries /
        // retained_bytes) read those deque figures. Prove they are non-zero and track the added weight
        // after a drain — a gauge that stayed 0 here would be the false-green this change exists to stop.
        int entries = 50;
        int bodyBytes = 10_000;
        Configuration configuration = configuration()
            .maxLogEntries(1000)
            .maxEventLogSizeInBytes(64L * 1024 * 1024)
            .maxLoggedBodyBytes(0);
        MockServerEventLog log = asynchronousEventLog(configuration);
        try {
            // the ceilings behind max_retained_bytes / max_retained_entries mirror the configured bounds
            assertThat(log.getMaxRetainedEntries(), is(1000L));
            assertThat(log.getMaxRetainedBytes(), is(64L * 1024 * 1024));
            // empty log: both retained figures start at zero
            assertThat(log.getRetainedEntryCount(), is(0L));
            assertThat(log.getRetainedBytes(), is(0L));

            for (int i = 0; i < entries; i++) {
                log.add(receivedRequestWithBody("/retained", bodyBytes));
            }
            drain(log);

            // every entry was retained (none evicted — well under maxLogEntries) and the bodies now
            // live in the deque, not the ring
            assertThat(log.getRetainedEntryCount(), is((long) entries));
            assertThat(log.getInFlightBytes(), is(0L));
            // retained bytes tracks the added weight: at least the raw body bytes summed across entries
            assertThat(log.getRetainedBytes(), is(greaterThanOrEqualTo((long) entries * bodyBytes)));
        } finally {
            log.stop();
        }
    }

    @Test
    public void shouldFallToZeroRetainedBytesAndEntriesAfterReset() {
        // The retained figures must FALL when the log is cleared — a gauge that only ever climbs is as
        // useless for attribution as one stuck at 0. reset() clears the deque, zeroing both.
        Configuration configuration = configuration()
            .maxLogEntries(1000)
            .maxEventLogSizeInBytes(64L * 1024 * 1024)
            .maxLoggedBodyBytes(0);
        MockServerEventLog log = asynchronousEventLog(configuration);
        try {
            for (int i = 0; i < 20; i++) {
                log.add(receivedRequestWithBody("/retained", 10_000));
            }
            drain(log);
            assertThat(log.getRetainedEntryCount(), is(20L));
            assertThat(log.getRetainedBytes(), is(greaterThan(0L)));

            log.reset();

            assertThat(log.getRetainedEntryCount(), is(0L));
            assertThat(log.getRetainedBytes(), is(0L));
        } finally {
            log.stop();
        }
    }

    @Test
    public void shouldCapRetainedEntriesAndBytesWhenTheDequeEvicts() {
        // When more entries are added than maxLogEntries the oldest are evicted, so the retained
        // figures are BOUNDED by the cap rather than the total added — a fall relative to everything
        // that arrived. This is the deque's element bound doing the retaining, visible through the gauge.
        int cap = 10;
        int added = 25;
        int bodyBytes = 10_000;
        Configuration configuration = configuration()
            .maxLogEntries(cap)
            .maxEventLogSizeInBytes(64L * 1024 * 1024)
            .maxLoggedBodyBytes(0);
        MockServerEventLog log = asynchronousEventLog(configuration);
        try {
            for (int i = 0; i < added; i++) {
                log.add(receivedRequestWithBody("/retained", bodyBytes));
            }
            drain(log);

            // retained count is pinned at the cap (fell from the 25 added), not the total added
            assertThat(log.getRetainedEntryCount(), is((long) cap));
            // Eviction DID happen — but do not assert it evicted exactly `added - cap`. maxLogEntries
            // also sizes the disruptor ring (ringBufferSize = min(maxLogEntries, 16384) = 10 here), so
            // publishing 25 entries into 10 slots faster than the single consumer drains them drops
            // some at the RING, and a dropped entry never reaches the deque to be evicted from it. How
            // many are dropped is a function of how fast the consumer is scheduled, so an exact count
            // is a host-speed assertion wearing an accounting assertion's clothes (it passed locally
            // and failed on CI at 6 of an expected 15). What IS exact is the conservation law: every
            // entry added either was dropped before the deque, or is retained in it, or was evicted
            // from it.
            assertThat(log.getEvictedLogEntryCount(), is(greaterThan(0L)));
            assertThat(log.getDroppedLogEventCount()
                + log.getRetainedEntryCount()
                + log.getEvictedLogEntryCount(), is((long) added));
            // retained bytes reflects ONLY the surviving entries. Two bounds earn their keep here.
            //
            // Lower bound (STRICTLY greater than cap*body): the weigher (LogEntry.estimatedHeapSize) is
            // a materially honest estimate, not raw body bytes alone — it adds a per-entry structural
            // overhead (~2 KB: the LogEntry graph plus each HttpRequest/HttpResponse model object and its
            // headers) on top of the body. Retaining `cap` identical 10 KB-body entries therefore weighs
            // cap*(body + ~2 KB), which is strictly MORE than cap*body. A regression that reverted the
            // weigher to counting bodies alone would land exactly at cap*body and fail this bound.
            //
            // Upper bound: proves eviction actually debited the byte total. The ~2 KB/entry structural
            // overhead across cap=10 entries is ~20 KB, i.e. about two 10 KB bodies, so cap + 3 bodies of
            // slack comfortably covers it (retained is ~cap*(body + 2 KB) = ~120 KB, well under 130 KB)
            // while staying far below the 25 bodies (250 KB) a broken debit that never subtracted evicted
            // weight would report.
            assertThat(log.getRetainedBytes(), is(greaterThan((long) cap * bodyBytes)));
            assertThat(log.getRetainedBytes(), is(lessThan((long) (cap + 3) * bodyBytes)));
        } finally {
            log.stop();
        }
    }

    // ---- helpers ----

    // The in-flight cap is derived and never below the heap-derived default, so a test that needs a
    // tiny one to force drops pins it directly.
    private static Configuration withInFlightCap(long inFlightCap) {
        return new Configuration() {
            @Override
            public long maxEventLogInFlightBytes() {
                return inFlightCap;
            }
        };
    }

    private MockServerEventLog asynchronousEventLog(Configuration configuration) {
        // asynchronous (true) so add() publishes to the disruptor ring and the in-flight accounting is
        // exercised; the ring runs on its own consumer thread, so the injected Scheduler can be a mock.
        return new MockServerEventLog(configuration, new MockServerLogger(configuration, MockServerLogger.class), mock(Scheduler.class), true);
    }

    private LogEntry receivedRequestWithBody(String path, int bodyBytes) {
        HttpRequest request = request().withMethod("POST").withPath(path).withBody(new byte[bodyBytes]);
        return new LogEntry()
            .setType(RECEIVED_REQUEST)
            .setLogLevel(Level.INFO)
            .setHttpRequest(request)
            .setMessageFormat("received request:{}")
            .setArguments(request);
    }

    // Log a mocked exchange as the server does: RECEIVED_REQUEST then EXPECTATION_RESPONSE, both holding
    // the same HttpRequest, whose JSON body's decoded String is cached as request matching leaves it.
    private void addMockedExchange(MockServerEventLog log, String path, int bodyChars) {
        StringBuilder json = new StringBuilder("{\"data\":\"");
        while (json.length() < bodyChars - 2) {
            json.append('x');
        }
        String value = json.append("\"}").toString();
        byte[] rawBytes = value.getBytes(StandardCharsets.UTF_8);
        JsonBody body = new JsonBody(value, rawBytes, JsonBody.DEFAULT_JSON_CONTENT_TYPE, ONLY_MATCHING_FIELDS);
        assertThat(body.retainedDerivedFormBytes(), is(greaterThan(0L)));
        HttpRequest request = request().withMethod("POST").withPath(path).withBody(body);
        HttpResponse response = response().withStatusCode(200).withBody("{\"ok\":true}");
        log.add(new LogEntry()
            .setType(RECEIVED_REQUEST)
            .setLogLevel(Level.INFO)
            .setHttpRequest(request)
            .setMessageFormat("received request:{}")
            .setArguments(request));
        log.add(new LogEntry()
            .setType(EXPECTATION_RESPONSE)
            .setLogLevel(Level.INFO)
            .setHttpRequest(request)
            .setHttpResponse(response)
            .setExpectation(request, response)
            .setMessageFormat("returning response:{}for request:{}")
            .setArguments(response, request));
    }

    // Occupy the single consumer thread until the returned latch is counted down, so entries added in
    // the meantime pile up in the ring (published-but-unprocessed) and the in-flight bound is exercised
    // deterministically rather than racing the consumer.
    private CountDownLatch blockConsumer(MockServerEventLog log) throws InterruptedException {
        CountDownLatch consumerBlocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // Hold the single disruptor CONSUMER thread directly with a RUNNABLE marker so the ring backs up.
        // (A retrieve can no longer be used to block the consumer: queries now run OFF the consumer thread
        // on the query executor — that is exactly the ingestion-starvation fix — so a parked retrieve
        // callback would block a query thread and leave the consumer free to keep draining.)
        log.add(new LogEntry()
            .setType(LogEntry.LogMessageType.RUNNABLE)
            .setConsumer(() -> {
                consumerBlocked.countDown();
                try {
                    release.await(10, SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        assertThat(consumerBlocked.await(10, SECONDS), is(true));
        return release;
    }

    // Publish a retrieval (a RUNNABLE marker) and block on it: the disruptor processes FIFO, so when
    // this completes every entry added before it has been consumed and its in-flight bytes released.
    private void drain(MockServerEventLog log) {
        CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
        log.retrieveMessageLogEntries(request(), future::complete);
        try {
            future.get(60, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    private String verify(MockServerEventLog log, Verification verification) {
        CompletableFuture<String> result = new CompletableFuture<>();
        log.verify(verification, result::complete);
        try {
            return result.get(30, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }
}
