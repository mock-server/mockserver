package org.mockserver.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.verify.Verification;
import org.slf4j.event.Level;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockserver.log.MockServerEventLog.DropReason.IN_FLIGHT_BYTES;
import static org.mockserver.log.MockServerEventLog.DropReason.RING_FULL;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.VERIFICATION_FAILED;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.never;

/**
 * An upper-bound verification that fails closed on an incomplete event log must name the cause that
 * actually occurred since the last reset (ring-full drops, in-flight byte drops, evictions) and give
 * that cause's remedy, not list every possible cause.
 *
 * <p>Each test builds its own {@link MockServerEventLog} from its own {@link Configuration}; no
 * JVM-global state is touched, so the class runs in the parallel Surefire phase.
 */
public class MockServerEventLogVerifyIncompleteLogCauseTest {

    private static final int IN_FLIGHT_CAP = 1000;
    private static final String RING_FULL_CAUSE = "because the ring buffer was full";
    private static final String MATCH_FAILURE_CAP = "or keep the log level and set maxLoggedMatchFailures (e.g. to 10) to cap the did-not-match entries logged per request";
    private static final String RING_FULL_REMEDY = "for the ring-full drops, lower the log level (e.g. to WARN) if they persist under steady load, " + MATCH_FAILURE_CAP;
    private static final String IN_FLIGHT_CAUSE = "exceeded the in-flight byte budget of " + IN_FLIGHT_CAP + " bytes";
    private static final String IN_FLIGHT_REMEDY = "for the in-flight byte drops, lower the log level or, if you have heap to spare, raise maxEventLogSizeInBytes above that budget";
    private static final String EVICTED_CAUSE = "EVICTED after the log reached";
    private static final String COUNT_EVICTED_CAUSE = EVICTED_CAUSE + " its maximum number of entries (maxLogEntries=";
    private static final String BYTE_EVICTED_CAUSE = EVICTED_CAUSE + " its maximum size in bytes (maxEventLogSizeInBytes=";
    private static final String COUNT_EVICTION_REMEDY = "for the evictions at maxLogEntries, raise it, or lower the log level so fewer entries are recorded per request, " + MATCH_FAILURE_CAP;
    private static final String BYTE_EVICTION_REMEDY = "for the evictions at maxEventLogSizeInBytes, raise it";

    private MockServerEventLog log;
    private Scheduler scheduler;
    private HttpState httpState;

    @After
    public void stop() {
        if (httpState != null) {
            // stops its event log too
            httpState.stop();
        } else if (log != null) {
            log.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    public void shouldNameOnlyTheFullRingWhenOnlyRingFullDropsHappened() throws Exception {
        log = asynchronousEventLog(smallRing().maxLogEntries(1000).maxEventLogSizeInBytes(0L));
        dropBecauseTheRingIsFull();

        String failure = verifyNever("/absent");

        assertThat(failure, containsString("Request could not be verified"));
        assertThat(failure, containsString(log.getDroppedLogEventCount(RING_FULL) + " log events were DROPPED before being recorded " + RING_FULL_CAUSE));
        assertThat(failure, containsString(RING_FULL_REMEDY));
        assertThat(failure, containsString("a larger ringBufferSize only absorbs short bursts"));
        assertThat(failure, not(containsString("in-flight")));
        assertThat(failure, not(containsString("EVICTED")));
        assertThat(failure, not(containsString("maxLogEntries")));
        assertThat(failure, not(containsString("maxEventLogSizeInBytes")));
    }

    @Test
    public void shouldNameOnlyTheInFlightByteBudgetWhenOnlyInFlightDropsHappened() throws Exception {
        log = asynchronousEventLog(smallRing().maxLogEntries(1000).maxEventLogSizeInBytes(0L));
        dropBecauseOfInFlightBytes();

        String failure = verifyNever("/absent");

        assertThat(log.getDroppedLogEventCount(RING_FULL), is(0L));
        assertThat(failure, containsString(log.getDroppedLogEventCount(IN_FLIGHT_BYTES) + " log events were DROPPED before being recorded"));
        assertThat(failure, containsString(IN_FLIGHT_CAUSE));
        assertThat(failure, containsString(IN_FLIGHT_REMEDY));
        assertThat(failure, containsString("maxLoggedBodyBytes does not help"));
        assertThat(failure, not(containsString("maxLoggedMatchFailures")));
        assertThat(failure, not(containsString("ring")));
        assertThat(failure, not(containsString("EVICTED")));
        assertThat(failure, not(containsString("maxLogEntries")));
    }

    @Test
    public void shouldNameOnlyTheCountBoundWhenOnlyCountEvictionsHappened() {
        log = asynchronousEventLog(smallRing().maxLogEntries(2).maxEventLogSizeInBytes(0L));
        addAndDrain("/first", "/second", "/third", "/fourth");

        String failure = verifyNever("/absent");

        assertThat(failure, containsString("2 recorded entries were " + EVICTED_CAUSE + " its maximum number of entries (maxLogEntries=2)"));
        assertThat(failure, containsString(COUNT_EVICTION_REMEDY));
        assertThat(failure, not(containsString("DROPPED")));
        assertThat(failure, not(containsString("ring")));
        assertThat(failure, not(containsString("in-flight")));
        assertThat(failure, not(containsString("maxEventLogSizeInBytes")));
    }

    @Test
    public void shouldNameOnlyTheByteBoundWhenOnlyByteEvictionsHappened() {
        log = asynchronousEventLog(smallRing().maxLogEntries(1000).maxEventLogSizeInBytes(1500L));
        addAndDrain("/first", "/second", "/third", "/fourth");

        String failure = verifyNever("/absent");

        assertThat(failure, containsString(log.getEvictedLogEntryCount() + " recorded entries were " + BYTE_EVICTED_CAUSE + "1500)"));
        assertThat(failure, containsString(BYTE_EVICTION_REMEDY));
        assertThat(failure, containsString("set maxLoggedBodyBytes to truncate large bodies"));
        assertThat(failure, not(containsString("maxLoggedMatchFailures")));
        assertThat(failure, not(containsString("DROPPED")));
        assertThat(failure, not(containsString("ring")));
        assertThat(failure, not(containsString("in-flight")));
        assertThat(failure, not(containsString("maxLogEntries")));
    }

    @Test
    public void shouldNameBothRetentionBoundsWhenEachEvicted() {
        // three small entries fit the byte budget, so the count bound evicts for each entry past the
        // third; the large body then takes the log over the byte budget, which evicts two more
        log = asynchronousEventLog(smallRing().maxLogEntries(3).maxEventLogSizeInBytes(4000L));
        addAndDrain("/first", "/second", "/third", "/fourth", "/fifth");
        log.add(receivedRequest(request("/large").withMethod("POST").withBody(new byte[3000])));
        drain();

        String failure = verifyNever("/absent");

        assertThat(log.getEvictedLogEntryCount(), is(5L));
        assertThat(failure, containsString("3 recorded entries were " + COUNT_EVICTED_CAUSE + "3)"));
        assertThat(failure, containsString("2 recorded entries were " + BYTE_EVICTED_CAUSE + "4000)"));
        assertThat(failure, containsString(COUNT_EVICTION_REMEDY));
        assertThat(failure, containsString(BYTE_EVICTION_REMEDY));
        assertThat(failure, not(containsString("DROPPED")));
    }

    @Test
    public void shouldNameEveryCauseAndRemedyWhenAllThreeHappened() throws Exception {
        log = asynchronousEventLog(smallRing().maxLogEntries(2).maxEventLogSizeInBytes(0L));
        dropBecauseTheRingIsFull();
        dropBecauseOfInFlightBytes();

        String failure = verifyNever("/absent");

        assertThat(log.getEvictedLogEntryCount(), greaterThan(0L));
        assertThat(failure, containsString(log.getDroppedLogEventCount(RING_FULL) + " log events were DROPPED before being recorded " + RING_FULL_CAUSE));
        assertThat(failure, containsString(log.getDroppedLogEventCount(IN_FLIGHT_BYTES) + " log events were DROPPED before being recorded"));
        assertThat(failure, containsString(IN_FLIGHT_CAUSE));
        assertThat(failure, containsString(EVICTED_CAUSE + " its maximum number of entries (maxLogEntries=2)"));
        assertThat(failure, containsString(RING_FULL_REMEDY));
        assertThat(failure, containsString(IN_FLIGHT_REMEDY));
        assertThat(failure, containsString(COUNT_EVICTION_REMEDY));
    }

    @Test
    public void shouldNameTheCauseOnTheResponseVerificationPath() throws Exception {
        log = asynchronousEventLog(smallRing().maxLogEntries(1000).maxEventLogSizeInBytes(0L));
        dropBecauseTheRingIsFull();

        String failure = verify(verification().withRequest(request("/absent")).withResponse(response().withStatusCode(200)).withTimes(never()));

        assertThat(failure, containsString("Response could not be verified"));
        assertThat(failure, containsString(RING_FULL_CAUSE));
        assertThat(failure, containsString(RING_FULL_REMEDY));
        assertThat(failure, not(containsString("in-flight")));
        assertThat(failure, not(containsString("EVICTED")));
    }

    @Test
    public void shouldNameOnlyTheCausesSinceTheLastResetAndKeepThemAcrossAFilteredClear() throws Exception {
        log = asynchronousEventLog(smallRing().maxLogEntries(1000).maxEventLogSizeInBytes(0L));
        dropBecauseTheRingIsFull();
        assertThat(verifyNever("/absent"), containsString(RING_FULL_CAUSE));

        // a filtered clear removes some entries; it says nothing about the events already lost
        log.clear(request("/some-other-path"));
        assertThat(verifyNever("/absent"), containsString(RING_FULL_CAUSE));

        // a reset declares the whole history irrelevant
        log.reset();
        assertThat(verifyNever("/absent"), is(""));

        // the lifetime ring-full total is still non-zero, but only in-flight drops happened since the reset
        dropBecauseOfInFlightBytes();
        assertThat(log.getDroppedLogEventCount(RING_FULL), greaterThan(0L));
        String afterReset = verifyNever("/absent");
        assertThat(afterReset, containsString(IN_FLIGHT_CAUSE));
        assertThat(afterReset, containsString(IN_FLIGHT_REMEDY));
        assertThat(afterReset, not(containsString("ring")));

        // an unfiltered clear clears every cause, as a reset does
        dropBecauseTheRingIsFull();
        log.clear(null);
        assertThat(verifyNever("/absent"), is(""));
    }

    @Test
    public void shouldStillPassALowerBoundVerificationWhateverTheCause() throws Exception {
        log = asynchronousEventLog(smallRing().maxLogEntries(1000).maxEventLogSizeInBytes(0L));
        addAndDrain("/present");
        dropBecauseTheRingIsFull();
        dropBecauseOfInFlightBytes();

        assertThat(verify(verification().withRequest(request("/present")).withTimes(atLeast(1))), is(""));
    }

    @Test
    public void shouldNameDropsInTheVerificationFailedLogEntry() throws Exception {
        log = eventLogRecordingItsOwnVerifications(smallRing().maxLogEntries(1000).maxEventLogSizeInBytes(0L));
        dropBecauseTheRingIsFull();

        verifyNever("/absent");

        assertThat(verificationFailedMessageFormats(), contains(containsString("could not be verified exactly 0 times because the event log has dropped log events")));
    }

    @Test
    public void shouldNameEvictionsInTheVerificationFailedLogEntry() {
        log = eventLogRecordingItsOwnVerifications(smallRing().maxLogEntries(8).maxEventLogSizeInBytes(0L));
        addAndDrain("/1", "/2", "/3", "/4", "/5", "/6", "/7", "/8", "/9", "/10");

        verifyNever("/absent");

        assertThat(verificationFailedMessageFormats(), contains(containsString("could not be verified exactly 0 times because the event log has evicted entries")));
    }

    @Test
    public void shouldNameDropsAndEvictionsInTheVerificationFailedLogEntry() throws Exception {
        log = eventLogRecordingItsOwnVerifications(smallRing().maxLogEntries(8).maxEventLogSizeInBytes(0L));
        addAndDrain("/1", "/2", "/3", "/4", "/5", "/6", "/7", "/8", "/9", "/10");
        dropBecauseOfInFlightBytes();

        verifyNever("/absent");

        assertThat(verificationFailedMessageFormats(), contains(containsString("could not be verified exactly 0 times because the event log has dropped log events and evicted entries")));
    }

    @Test
    public void shouldGiveTheRingFullDropCountInTheVerificationFailedLogEntry() throws Exception {
        log = eventLogRecordingItsOwnVerifications(smallRing().maxLogEntries(1000).maxEventLogSizeInBytes(0L));
        dropBecauseTheRingIsFull();

        String failure = verifyNever("/absent");

        Map<String, Long> loss = lossInTheVerificationFailedLogEntry();
        assertThat(new ArrayList<>(loss.keySet()), contains("droppedRingFull"));
        assertThat(loss.get("droppedRingFull"), greaterThan(1L));
        assertThat(failure, containsString(loss.get("droppedRingFull") + " log events were DROPPED before being recorded " + RING_FULL_CAUSE));
    }

    @Test
    public void shouldGiveTheInFlightDropCountAndBudgetInTheVerificationFailedLogEntry() throws Exception {
        log = eventLogRecordingItsOwnVerifications(smallRing().maxLogEntries(1000).maxEventLogSizeInBytes(0L));
        dropBecauseOfInFlightBytes();

        String failure = verifyNever("/absent");

        Map<String, Long> loss = lossInTheVerificationFailedLogEntry();
        assertThat(new ArrayList<>(loss.keySet()), contains("droppedInFlightBytes", "inFlightBytesBudget"));
        assertThat(loss.get("droppedInFlightBytes"), is(3L));
        assertThat(loss.get("inFlightBytesBudget"), is((long) IN_FLIGHT_CAP));
        assertThat(failure, containsString("3 log events were DROPPED before being recorded"));
        assertThat(failure, containsString(IN_FLIGHT_CAUSE));
    }

    @Test
    public void shouldGiveTheCountEvictionsAndBoundInTheVerificationFailedLogEntry() {
        log = eventLogRecordingItsOwnVerifications(smallRing().maxLogEntries(8).maxEventLogSizeInBytes(0L));
        addAndDrain("/1", "/2", "/3", "/4", "/5", "/6", "/7", "/8", "/9", "/10");

        String failure = verifyNever("/absent");

        Map<String, Long> loss = lossInTheVerificationFailedLogEntry();
        assertThat(new ArrayList<>(loss.keySet()), contains("evictedAtMaxLogEntries", "maxLogEntries"));
        assertThat(loss.get("evictedAtMaxLogEntries"), greaterThan(1L));
        assertThat(loss.get("maxLogEntries"), is(8L));
        assertThat(failure, containsString(loss.get("evictedAtMaxLogEntries") + " recorded entries were " + COUNT_EVICTED_CAUSE + "8)"));
    }

    @Test
    public void shouldGiveTheByteEvictionsAndBoundInTheVerificationFailedLogEntry() {
        log = eventLogRecordingItsOwnVerifications(smallRing().maxLogEntries(1000).maxEventLogSizeInBytes(1500L));
        addAndDrain("/first", "/second", "/third", "/fourth");

        String failure = verifyNever("/absent");

        Map<String, Long> loss = lossInTheVerificationFailedLogEntry();
        assertThat(new ArrayList<>(loss.keySet()), contains("evictedAtMaxEventLogSizeInBytes", "maxEventLogSizeInBytes"));
        assertThat(loss.get("evictedAtMaxEventLogSizeInBytes"), greaterThan(1L));
        assertThat(loss.get("maxEventLogSizeInBytes"), is(1500L));
        assertThat(failure, containsString(loss.get("evictedAtMaxEventLogSizeInBytes") + " recorded entries were " + BYTE_EVICTED_CAUSE + "1500)"));
    }

    @Test
    public void shouldGiveTheLossInTheResponseVerificationFailedLogEntry() {
        log = eventLogRecordingItsOwnVerifications(smallRing().maxLogEntries(8).maxEventLogSizeInBytes(0L));
        addAndDrain("/1", "/2", "/3", "/4", "/5", "/6", "/7", "/8", "/9", "/10");

        verify(verification().withRequest(request("/absent")).withResponse(response().withStatusCode(200)).withTimes(never()));

        assertThat(verificationFailedMessageFormats(), contains("response:{}could not be verified exactly 0 times because the event log has evicted entries:{}"));
        Map<String, Long> loss = lossInTheVerificationFailedLogEntry();
        assertThat(new ArrayList<>(loss.keySet()), contains("evictedAtMaxLogEntries", "maxLogEntries"));
        assertThat(loss.get("maxLogEntries"), is(8L));
    }

    @Test
    public void shouldGiveTheCountsAndBoundsInTheRenderedLogMessage() {
        log = eventLogRecordingItsOwnVerifications(smallRing().maxLogEntries(8).maxEventLogSizeInBytes(0L));
        addAndDrain("/1", "/2", "/3", "/4", "/5", "/6", "/7", "/8", "/9", "/10");

        verifyNever("/absent");

        Map<String, Long> loss = lossInTheVerificationFailedLogEntry();
        String rendered = verificationFailedEntries().get(0).getMessage();
        assertThat(rendered, containsString("because the event log has evicted entries:"));
        assertThat(rendered, containsString("evictedAtMaxLogEntries=" + loss.get("evictedAtMaxLogEntries")));
        assertThat(rendered, containsString("maxLogEntries=8"));
    }

    // The dashboard reads the entry's text and the loss fields by name; the fixture is the entry as the
    // dashboard receives it with every cause present, and the UI's tests render the same file.
    @Test
    public void shouldLogTheTextAndLossFieldsTheDashboardReads() throws Exception {
        JsonNode dashboardParts = dashboardFixture().get("logEntry").get("messageParts");
        // the count bound evicts for each small entry past the third, then the large body evicts by bytes
        log = eventLogRecordingItsOwnVerifications(smallRing().maxLogEntries(3).maxEventLogSizeInBytes(4000L));
        addAndDrain("/first", "/second", "/third", "/fourth", "/fifth");
        log.add(receivedRequest(request("/large").withMethod("POST").withBody(new byte[3000])));
        drain();
        dropBecauseTheRingIsFull();
        dropBecauseOfInFlightBytes();

        String failure = verifyNever("/absent");

        String[] text = verificationFailedMessageFormats().get(0).split("\\{}");
        assertThat(text.length, is(2));
        assertThat(text[0], is(dashboardParts.get(0).get("value").asText()));
        assertThat(text[1], is(dashboardParts.get(2).get("value").asText()));
        List<String> dashboardLossFields = new ArrayList<>();
        dashboardParts.get(3).get("value").fieldNames().forEachRemaining(dashboardLossFields::add);
        assertThat(dashboardLossFields, hasSize(7));
        Map<String, Long> loss = lossInTheVerificationFailedLogEntry();
        assertThat(new ArrayList<>(loss.keySet()), is(dashboardLossFields));
        assertThat(failure, containsString(loss.get("droppedRingFull") + " log events were DROPPED before being recorded " + RING_FULL_CAUSE));
        assertThat(failure, containsString(loss.get("droppedInFlightBytes") + " log events were DROPPED before being recorded because the request/response bodies"));
        assertThat(failure, containsString(loss.get("evictedAtMaxLogEntries") + " recorded entries were " + COUNT_EVICTED_CAUSE + loss.get("maxLogEntries") + ")"));
        assertThat(failure, containsString(loss.get("evictedAtMaxEventLogSizeInBytes") + " recorded entries were " + BYTE_EVICTED_CAUSE + loss.get("maxEventLogSizeInBytes") + ")"));
    }

    // A four-slot ring and a pinned in-flight cap, so each drop path can be reached with a handful of
    // entries. The cap is derived and never below the heap-derived default, so it is pinned by override.
    private static Configuration smallRing() {
        return new Configuration() {
            @Override
            public long maxEventLogInFlightBytes() {
                return IN_FLIGHT_CAP;
            }
        }.ringBufferSize(4);
    }

    private MockServerEventLog asynchronousEventLog(Configuration configuration) {
        return new MockServerEventLog(configuration, new MockServerLogger(configuration, MockServerLogger.class), mock(Scheduler.class), true);
    }

    // An event log whose logger writes back to it at INFO, as a running server's does, so the
    // VERIFICATION_FAILED entry a failed verification logs can be read back.
    private MockServerEventLog eventLogRecordingItsOwnVerifications(Configuration configuration) {
        configuration.logLevel(Level.INFO);
        scheduler = new Scheduler(configuration, new MockServerLogger());
        httpState = new HttpState(configuration, new MockServerLogger(configuration, MockServerLogger.class), scheduler);
        return httpState.getMockServerLog();
    }

    // Entries with no request or response weigh nothing in flight, so with the consumer held they are
    // admitted until the ring has no free slot and are then dropped for that reason alone.
    private void dropBecauseTheRingIsFull() throws InterruptedException {
        long before = log.getDroppedLogEventCount(RING_FULL);
        long inFlightBefore = log.getDroppedLogEventCount(IN_FLIGHT_BYTES);
        CountDownLatch release = blockConsumer();
        for (int i = 0; i < 20; i++) {
            log.add(new LogEntry().setType(LogEntry.LogMessageType.INFO).setLogLevel(Level.INFO).setMessageFormat("diagnostic " + i));
        }
        release.countDown();
        drain();
        assertThat(log.getDroppedLogEventCount(RING_FULL), greaterThan(before + 1));
        assertThat(log.getDroppedLogEventCount(IN_FLIGHT_BYTES), is(inFlightBefore));
    }

    // With the consumer held, the first body is admitted into the empty backlog and takes it over the
    // cap, so the bodies behind it are dropped before they reach the ring.
    private void dropBecauseOfInFlightBytes() throws InterruptedException {
        long before = log.getDroppedLogEventCount(IN_FLIGHT_BYTES);
        long ringFullBefore = log.getDroppedLogEventCount(RING_FULL);
        CountDownLatch release = blockConsumer();
        for (int i = 0; i < 4; i++) {
            log.add(receivedRequest(request("/large").withMethod("POST").withBody(new byte[2 * IN_FLIGHT_CAP])));
        }
        release.countDown();
        drain();
        assertThat(log.getDroppedLogEventCount(IN_FLIGHT_BYTES), is(before + 3));
        assertThat(log.getDroppedLogEventCount(RING_FULL), is(ringFullBefore));
    }

    private void addAndDrain(String... paths) {
        for (String path : paths) {
            log.add(receivedRequest(request(path)));
            drain();
        }
    }

    private LogEntry receivedRequest(HttpRequest request) {
        return new LogEntry()
            .setType(RECEIVED_REQUEST)
            .setLogLevel(Level.INFO)
            .setHttpRequest(request)
            .setMessageFormat("received request:{}")
            .setArguments(request);
    }

    private CountDownLatch blockConsumer() throws InterruptedException {
        CountDownLatch consumerBlocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        log.add(new LogEntry()
            .setType(LogEntry.LogMessageType.RUNNABLE)
            .setConsumer(() -> {
                consumerBlocked.countDown();
                try {
                    release.await(30, SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        assertThat(consumerBlocked.await(10, SECONDS), is(true));
        return release;
    }

    // The ring is FIFO, so when this retrieval completes every entry added before it has been processed.
    private List<LogEntry> drain() {
        CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
        log.retrieveMessageLogEntries(null, future::complete);
        try {
            return future.get(60, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    private List<String> verificationFailedMessageFormats() {
        return verificationFailedEntries().stream()
            .map(LogEntry::getMessageFormat)
            .collect(Collectors.toList());
    }

    private List<LogEntry> verificationFailedEntries() {
        return drain().stream()
            .filter(entry -> entry.getType() == VERIFICATION_FAILED)
            .collect(Collectors.toList());
    }

    // The second argument of the one VERIFICATION_FAILED entry: every value must be a count or a bound.
    @SuppressWarnings("unchecked")
    private Map<String, Long> lossInTheVerificationFailedLogEntry() {
        List<LogEntry> failed = verificationFailedEntries();
        assertThat(failed, hasSize(1));
        Object[] arguments = failed.get(0).getArguments();
        assertThat(arguments.length, is(2));
        assertThat(arguments[1], instanceOf(Map.class));
        for (Object value : ((Map<String, Object>) arguments[1]).values()) {
            assertThat(value, instanceOf(Long.class));
        }
        return (Map<String, Long>) arguments[1];
    }

    private static JsonNode dashboardFixture() throws IOException {
        String relativePath = "mockserver-ui/src/__fixtures__/incompleteLogVerificationFailure.json";
        for (File dir = new File(System.getProperty("user.dir")).getAbsoluteFile(); dir != null; dir = dir.getParentFile()) {
            File candidate = new File(dir, relativePath);
            if (candidate.isFile()) {
                return new ObjectMapper().readTree(candidate);
            }
        }
        throw new FileNotFoundException("could not find '" + relativePath + "' above " + System.getProperty("user.dir"));
    }

    private String verifyNever(String path) {
        return verify(verification().withRequest(request(path)).withTimes(never()));
    }

    private String verify(Verification verification) {
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
