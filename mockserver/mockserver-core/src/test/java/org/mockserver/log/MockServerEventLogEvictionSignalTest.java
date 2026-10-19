package org.mockserver.log;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.metrics.Metrics;
import org.mockserver.metrics.MetricsLock;
import org.mockserver.model.RequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.RUNNABLE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Verifies the two eviction SIGNALS that keep silent coverage-loss visible: the once-per-server WARN
 * emitted when the log first evicts (via {@link MockServerEventLog#buildEvictionWarning}) and that the
 * byte budget actually bounds retained memory. Also pins the remedies in the once-only ring-full WARN. Complements
 * {@code CircularConcurrentLinkedDequeTest} (eviction mechanics) and {@code ConfigurationTest} (the
 * heap-derived default).
 */
public class MockServerEventLogEvictionSignalTest {

    @ClassRule
    public static final MetricsLock metricsLock = new MetricsLock();

    private final List<MockServerEventLog> eventLogs = new ArrayList<>();

    @After
    public void stopEventLogs() {
        eventLogs.forEach(MockServerEventLog::stop);
    }

    private MockServerEventLog synchronousEventLog(Configuration configuration) {
        // synchronous (false) so add() runs processLogEntry inline on the calling thread — warnings and
        // eviction accounting are then deterministic without draining the disruptor.
        MockServerEventLog eventLog = new MockServerEventLog(configuration, new MockServerLogger(configuration, MockServerLogger.class), mock(Scheduler.class), false);
        eventLogs.add(eventLog);
        return eventLog;
    }

    private List<LogEntry> retrieveMessageLogEntries(MockServerEventLog log, RequestDefinition requestDefinition) {
        CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
        log.retrieveMessageLogEntries(requestDefinition, future::complete);
        try {
            return future.get(60, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    // ---- buildEvictionWarning: message names the bound that was hit and gives safe, ordered remedies ----

    @Test
    public void countBoundWarningNamesMaxLogEntriesAndTheSafeVerbosityRemedy() {
        String message = MockServerEventLog.buildEvictionWarning(false, 5000, 12345L, Level.INFO);

        // names the count bound and its value, not the byte budget
        assertThat(message, containsString("entry-count limit reached (maxLogEntries=5000)"));
        // verification impact (fail-closed, upper bounds only)
        assertThat(message, containsString("will now FAIL"));
        assertThat(message, containsString("atLeast(n)"));
        // cheapest-first remedy: reduce verbosity, and it is explicitly safe for verify
        assertThat(message, containsString("log level INFO"));
        assertThat(message, containsString("lowering the log level"));
        assertThat(message, containsString("does NOT affect what verify can find"));
        // and warns that lowering the level shrinks the default byte budget, which may then bind
        assertThat(message, containsString("the default byte budget is smaller at INFO and WARN than at DEBUG and TRACE"));
        // or keeps the log level and caps the did-not-match entries per request instead
        assertThat(message, containsString("to keep this log level, set maxLoggedMatchFailures"));
    }

    @Test
    public void byteBoundWarningNamesMaxEventLogSizeInBytesAndDoesNotOfferVerbosityAsTheFix() {
        String message = MockServerEventLog.buildEvictionWarning(true, 5000, 12345L, Level.INFO);

        // names the byte budget and its value
        assertThat(message, containsString("byte budget reached (maxEventLogSizeInBytes=12345 bytes)"));
        // the byte lever is body retention, not verbosity — and the message says so plainly
        assertThat(message, containsString("maxLoggedBodyBytes"));
        assertThat(message, containsString("bodies are retained at every log level"));
        // it must NOT tell a user under a byte-budget eviction that lowering the level will free memory
        assertThat(message, not(containsString("lowering the log level (e.g. to WARN)")));
        // nor offer the did-not-match cap, which does not shrink the always-retained bodies
        assertThat(message, not(containsString("maxLoggedMatchFailures")));
    }

    // ---- the WARN fires exactly once when eviction begins, and never when the log does not evict ----

    private int countEvictionWarnings(Runnable work) {
        return warningsContaining("MockServer event log full", work).size();
    }

    private List<String> warningsContaining(String marker, Runnable work) {
        List<String> warnings = new CopyOnWriteArrayList<>();
        Logger julLogger = Logger.getLogger(MockServerEventLog.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getMessage() != null && record.getMessage().contains(marker)) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(java.util.logging.Level.ALL);
        // Force the logger to accept WARNING regardless of MockServer's ambient JUL level (the
        // slf4j-jdk14 binding drops a warn() before it reaches any handler if isLoggable is false).
        java.util.logging.Level previous = julLogger.getLevel();
        julLogger.setLevel(java.util.logging.Level.ALL);
        julLogger.addHandler(handler);
        try {
            work.run();
        } finally {
            julLogger.removeHandler(handler);
            julLogger.setLevel(previous);
        }
        return warnings;
    }

    @Test
    public void shouldWarnExactlyOnceWhenEvictionBeginsUnderSustainedEviction() {
        Configuration configuration = configuration()
            .maxLogEntries(1000)
            .maxEventLogSizeInBytes(100L)
            .maxLoggedBodyBytes(0);
        MockServerEventLog log = synchronousEventLog(configuration);

        int warnings = countEvictionWarnings(() -> {
            byte[] body = new byte[40]; // 40 bytes each, well over the 100-byte budget => continuous eviction
            for (int i = 0; i < 200; i++) {
                log.add(new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setHttpRequest(request("/p" + i))
                    .setHttpResponse(response().withStatusCode(200).withBody(body)));
            }
        });

        // 200 adds, ~198 evictions — but the operator is told exactly once
        assertThat(log.getEvictedLogEntryCount(), is(greaterThan(1L)));
        assertThat(warnings, is(1));
    }

    @Test
    public void shouldNotWarnWhenTheLogNeverEvicts() {
        Configuration configuration = configuration()
            .maxLogEntries(1000)
            .maxEventLogSizeInBytes(0L)   // byte budget disabled
            .maxLoggedBodyBytes(0);
        MockServerEventLog log = synchronousEventLog(configuration);

        int warnings = countEvictionWarnings(() -> {
            byte[] body = new byte[40];
            for (int i = 0; i < 50; i++) { // 50 entries, well under maxLogEntries=1000
                log.add(new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setHttpRequest(request("/p" + i))
                    .setHttpResponse(response().withStatusCode(200).withBody(body)));
            }
        });

        assertThat(log.getEvictedLogEntryCount(), is(0L));
        assertThat(warnings, is(0));
    }

    @Test
    public void ringFullWarningNamesBothTheLogLevelAndTheMatchFailureCapRemedies() throws Exception {
        // ringBufferSize = nextPowerOfTwo(min(maxLogEntries, 16384)) = 1, so a blocked consumer fills it at once
        Configuration configuration = configuration().maxLogEntries(1);
        MockServerEventLog log = new MockServerEventLog(configuration, new MockServerLogger(configuration, MockServerLogger.class), mock(Scheduler.class), true);
        eventLogs.add(log);
        CountDownLatch hold = new CountDownLatch(1);
        CountDownLatch consumerStarted = new CountDownLatch(1);

        List<String> warnings;
        try {
            warnings = warningsContaining("Log event ring buffer full", () -> {
                log.add(new LogEntry().setType(RUNNABLE).setConsumer(() -> {
                    consumerStarted.countDown();
                    try {
                        hold.await(30, SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
                try {
                    assertThat("consumer should start", consumerStarted.await(10, SECONDS), is(true));
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                for (int i = 0; i < 100; i++) {
                    log.add(new LogEntry().setType(RECEIVED_REQUEST).setLogLevel(Level.INFO).setHttpRequest(request("/r" + i)));
                }
            });
        } finally {
            hold.countDown();
        }

        assertThat(log.getDroppedLogEventCount(), is(greaterThan(0L)));
        assertThat(warnings.size(), is(1));
        String warning = warnings.get(0);
        assertThat(warning, containsString("lower the log level (e.g. to WARN or ERROR)"));
        assertThat(warning, containsString("keep INFO and set maxLoggedMatchFailures"));
        assertThat(warning, containsString("raising ringBufferSize only absorbs short bursts"));
    }

    // ---- the byte budget actually bounds retained memory (measured, not inferred from config) ----

    private long retainedBodyBytes(MockServerEventLog log) {
        long total = 0;
        for (LogEntry entry : retrieveMessageLogEntries(log, null)) {
            total += entry.estimatedHeapSize();
        }
        return total;
    }

    @Test
    public void countOnlyBoundLetsRetainedBytesGrowWithBodySizeWhileAByteBoundHoldsItFlat() {
        byte[] bigBody = new byte[100_000]; // 100 KB response bodies
        int exchanges = 200;                // 200 * 100 KB ~= 20 MB of bodies offered

        // count-only: byte budget disabled, count bound generous => retained bytes track the bodies added
        Configuration countOnly = configuration()
            .maxLogEntries(100000)
            .maxEventLogSizeInBytes(0L)
            .maxLoggedBodyBytes(0);
        MockServerEventLog countOnlyLog = synchronousEventLog(countOnly);

        // byte-bounded: a 1 MB budget, same generous count bound
        long budget = 1_000_000L;
        Configuration byteBounded = configuration()
            .maxLogEntries(100000)
            .maxEventLogSizeInBytes(budget)
            .maxLoggedBodyBytes(0);
        MockServerEventLog byteBoundedLog = synchronousEventLog(byteBounded);

        for (int i = 0; i < exchanges; i++) {
            countOnlyLog.add(new LogEntry().setType(FORWARDED_REQUEST)
                .setHttpRequest(request("/p" + i))
                .setHttpResponse(response().withStatusCode(200).withBody(bigBody)));
            byteBoundedLog.add(new LogEntry().setType(FORWARDED_REQUEST)
                .setHttpRequest(request("/p" + i))
                .setHttpResponse(response().withStatusCode(200).withBody(bigBody)));
        }

        long countOnlyRetained = retainedBodyBytes(countOnlyLog);
        long byteBoundedRetained = retainedBodyBytes(byteBoundedLog);

        // count-only retained ~20 MB (unbounded by bytes — this is the OOM path)
        assertThat(countOnlyRetained, is(greaterThan(15_000_000L)));
        assertThat(countOnlyLog.getEvictedLogEntryCount(), is(0L));

        // byte-bounded stays within the budget (plus at most one over-budget straggler admitted before
        // eviction runs on the NEXT add); 100 KB bodies against a 1 MB budget => ~10 entries retained
        assertThat(byteBoundedRetained, is(lessThanOrEqualTo(budget + bigBody.length)));
        assertThat(byteBoundedLog.getEvictedLogEntryCount(), is(greaterThan(1L)));
        // and it is dramatically smaller than the count-only log holding the same traffic
        assertThat(byteBoundedRetained * 5 < countOnlyRetained, is(true));
    }

    // ---- mock_server_evicted_log_entries counts evicted ENTRIES, not eviction episodes ----

    @Test
    public void evictedLogEntriesMetricCountsEveryEvictedEntryAndStaysMonotonicAcrossReset() {
        Metrics.resetAdditionalMetricsForTesting();
        new Metrics(configuration().metricsEnabled(true));
        try {
            Configuration configuration = configuration()
                .maxLogEntries(10)
                .maxEventLogSizeInBytes(0L)
                .maxLoggedBodyBytes(0);
            MockServerEventLog log = synchronousEventLog(configuration);

            for (int i = 0; i < 100; i++) {
                log.add(new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setHttpRequest(request("/p" + i))
                    .setHttpResponse(response().withStatusCode(200)));
            }
            assertThat(log.getEvictedLogEntryCount(), is(90L));
            assertThat(Metrics.getEvictedLogEntryCount(), is(90L));

            // reset zeroes the log's own count, but the Prometheus counter must never go backwards
            log.reset();
            for (int i = 0; i < 15; i++) {
                log.add(new LogEntry()
                    .setType(FORWARDED_REQUEST)
                    .setHttpRequest(request("/q" + i))
                    .setHttpResponse(response().withStatusCode(200)));
            }
            assertThat(log.getEvictedLogEntryCount(), is(5L));
            assertThat(Metrics.getEvictedLogEntryCount(), is(95L));
        } finally {
            Metrics.resetAdditionalMetricsForTesting();
        }
    }
}
