package org.mockserver.log;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.verify.Verification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.fail;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.mockserver.verify.VerificationTimes.once;

/**
 * The "verification summary:" block appended to a failed request-count verification: it must only
 * ever follow the existing message (clients parse that by prefix) and must state the expected and
 * matched counts, the closest non-matching requests and the requests that arrived after the
 * verification started.
 */
public class MockServerEventLogVerificationSummaryTest {

    private static final String SUMMARY_SEPARATOR = NEW_LINE + NEW_LINE + "verification summary:" + NEW_LINE;
    private static final Scheduler scheduler = new Scheduler(configuration(), new MockServerLogger());
    private final Configuration configuration = configuration();
    private final List<Thread> delayedThreads = new ArrayList<>();
    private MockServerEventLog mockServerEventLog;

    @Before
    public void setupTestFixture() {
        configuration.detailedVerificationFailures(true);
        mockServerEventLog = new MockServerEventLog(configuration, new MockServerLogger(configuration, MockServerLogger.class), scheduler, true);
    }

    @After
    public void stopEventLog() throws InterruptedException {
        for (Thread thread : delayedThreads) {
            thread.join(SECONDS.toMillis(10));
        }
        mockServerEventLog.stop();
    }

    @AfterClass
    public static void stopScheduler() {
        scheduler.shutdown();
    }

    private String verify(Verification verification) {
        return verify(verification, 0);
    }

    private String verify(Verification verification, int additionalRemoteMatchCount) {
        CompletableFuture<String> result = new CompletableFuture<>();
        mockServerEventLog.verify(verification, additionalRemoteMatchCount, result::complete);
        try {
            return result.get(30, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return null;
        }
    }

    private void received(HttpRequest httpRequest) {
        mockServerEventLog.add(new LogEntry().setHttpRequest(httpRequest).setType(RECEIVED_REQUEST));
    }

    private static String summaryOf(String failureMessage) {
        int index = failureMessage.indexOf(SUMMARY_SEPARATOR);
        return index == -1 ? null : failureMessage.substring(index + SUMMARY_SEPARATOR.length());
    }

    private static String messageBeforeSummary(String failureMessage) {
        int index = failureMessage.indexOf(SUMMARY_SEPARATOR);
        return index == -1 ? failureMessage : failureMessage.substring(0, index);
    }

    @Test
    public void shouldAppendCountsAndClosestNonMatchingRequestsAfterTheExistingMessage() {
        // given
        received(request().withMethod("GET").withPath("/orders"));
        received(request().withMethod("GET").withPath("/orderz"));
        received(request().withMethod("POST").withPath("/orders"));
        received(request().withMethod("POST").withPath("/other"));
        received(request().withMethod("PUT").withPath("/other").withHeader("x-a", "b"));

        // when
        String result = verify(verification().withRequest(request().withMethod("GET").withPath("/orders")).withTimes(exactly(2)));

        // then - the existing message comes first, byte for byte; it has no closest match diff here
        // because one recorded request matches, so only the summary names the near misses
        assertThat(messageBeforeSummary(result), is("Request not found exactly 2 times, expected:<{" + NEW_LINE +
            "  \"method\" : \"GET\"," + NEW_LINE +
            "  \"path\" : \"/orders\"" + NEW_LINE +
            "}> but was:<[ {" + NEW_LINE +
            "  \"method\" : \"GET\"," + NEW_LINE +
            "  \"path\" : \"/orders\"" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"method\" : \"GET\"," + NEW_LINE +
            "  \"path\" : \"/orderz\"" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"method\" : \"POST\"," + NEW_LINE +
            "  \"path\" : \"/orders\"" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"method\" : \"POST\"," + NEW_LINE +
            "  \"path\" : \"/other\"" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"headers\" : {" + NEW_LINE +
            "    \"x-a\" : [ \"b\" ]" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"method\" : \"PUT\"," + NEW_LINE +
            "  \"path\" : \"/other\"" + NEW_LINE +
            "} ]>"));
        // and the summary is the last block: one-field misses first, most recent first on a tie,
        // every differing field named (not just the first one a fail-fast match stops at); an extra
        // recorded header is not a difference
        assertThat(summaryOf(result), is(
            "  expected exactly 2 times but matched 1 time among 5 recorded requests" + NEW_LINE +
                "  closest non-matching requests (fewest differing fields first):" + NEW_LINE +
                "    1. POST /orders differs in: method" + NEW_LINE +
                "    2. GET /orderz differs in: path" + NEW_LINE +
                "    3. PUT /other differs in: method, path" + NEW_LINE
        ));
    }

    @Test
    public void shouldAppendSummaryAfterTheClosestMatchDiff() {
        // given
        received(request().withMethod("GET").withPath("/orderz"));

        // when
        String result = verify(verification().withRequest(request().withMethod("GET").withPath("/orders")).withTimes(once()));

        // then
        assertThat(messageBeforeSummary(result), startsWith("Request not found exactly once, expected:<{"));
        assertThat(messageBeforeSummary(result), containsString(NEW_LINE + NEW_LINE + "closest match diff:" + NEW_LINE + "  path:" + NEW_LINE));
        assertThat(summaryOf(result), is(
            "  expected exactly once but matched 0 times among 1 recorded request" + NEW_LINE +
                "  closest non-matching requests (fewest differing fields first):" + NEW_LINE +
                "    1. GET /orderz differs in: path" + NEW_LINE
        ));
    }

    @Test
    public void shouldNotAppendSummaryWhenDetailedVerificationFailuresIsDisabled() {
        // given
        configuration.detailedVerificationFailures(false);
        received(request().withMethod("GET").withPath("/orders"));
        received(request().withMethod("POST").withPath("/orders"));

        // when
        String result = verify(verification().withRequest(request().withMethod("GET").withPath("/orders")).withTimes(exactly(2)));

        // then
        assertThat(result, startsWith("Request not found exactly 2 times"));
        assertThat(result, not(containsString("verification summary:")));
    }

    @Test
    public void shouldLeaveTheMessageUnchangedWhenTheSummaryWouldAddNothing() {
        // given - every recorded request matches, nothing arrived late, the verification did not wait
        received(request().withPath("/some_path"));
        received(request().withPath("/some_path"));
        received(request().withPath("/some_path"));

        // when
        String result = verify(verification()
            .withRequest(request().withPath("/some_path"))
            .withTimes(atLeast(4))
            .withMaximumNumberOfRequestToReturnInVerificationFailure(2));

        // then
        assertThat(result, is("Request not found at least 4 times, expected:<{" + NEW_LINE +
            "  \"path\" : \"/some_path\"" + NEW_LINE +
            "}> but was found 3 times among 3 total requests"));
    }

    @Test
    public void shouldNotListNearMissesWhenTheRequestWasFoundTooOften() {
        // given
        received(request().withPath("/some_path"));
        received(request().withPath("/some_path"));
        received(request().withPath("/some_other_path"));

        // when
        String result = verify(verification().withRequest(request().withPath("/some_path")).withTimes(once()));

        // then
        assertThat(result, startsWith("Request found 2 times but should have been found exactly once"));
        assertThat(result, not(containsString("verification summary:")));
    }

    @Test
    public void shouldCountRequestsThatArrivedAfterTheVerificationStarted() {
        // given - one request recorded before verify, one stamped after verify started
        received(request().withPath("/some_other_path"));
        mockServerEventLog.add(new LogEntry()
            .setHttpRequest(request().withPath("/late_path"))
            .setType(RECEIVED_REQUEST)
            .setEpochTime(System.currentTimeMillis() + SECONDS.toMillis(60)));

        // when
        String result = verify(verification().withRequest(request().withPath("/some_path")).withTimes(once()));

        // then
        assertThat(summaryOf(result), endsWith(
            "    2. /some_other_path differs in: path" + NEW_LINE +
                "  1 request arrived after verification started" + NEW_LINE
        ));
    }

    @Test
    public void shouldReportRequestsThatArrivedDuringAWaitingVerification() {
        // given - a non-matching request arrives while the verification is waiting
        Thread delayed = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            received(request().withPath("/arrived_while_waiting"));
        });
        delayedThreads.add(delayed);

        // when
        CompletableFuture<String> result = new CompletableFuture<>();
        mockServerEventLog.verify(verification().withRequest(request().withPath("/some_path")).withTimes(once()).withTimeout(2_000L), 0, result::complete);
        delayed.start();
        String message;
        try {
            message = result.get(30, SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
            return;
        }

        // then
        assertThat(summaryOf(message), is(
            "  expected exactly once but matched 0 times among 1 recorded request" + NEW_LINE +
                "  closest non-matching requests (fewest differing fields first):" + NEW_LINE +
                "    1. /arrived_while_waiting differs in: path" + NEW_LINE +
                "  1 request arrived after verification started" + NEW_LINE
        ));
    }

    @Test
    public void shouldSayNothingArrivedWhenAWaitingVerificationSawNoRequests() {
        // when
        String result = verify(verification().withRequest(request().withPath("/some_path")).withTimes(once()).withTimeout(100L));

        // then
        assertThat(summaryOf(result), is(
            "  expected exactly once but matched 0 times among 0 recorded requests" + NEW_LINE +
                "  0 requests arrived after verification started" + NEW_LINE
        ));
    }

    @Test
    public void shouldNameMatchesCountedOnOtherClusterNodes() {
        // given
        received(request().withPath("/some_path"));
        received(request().withPath("/some_other_path"));

        // when
        String result = verify(verification().withRequest(request().withPath("/some_path")).withTimes(exactly(3)), 1);

        // then
        assertThat(summaryOf(result), startsWith(
            "  expected exactly 3 times but matched 2 times (including 1 on other cluster nodes) among 2 recorded requests" + NEW_LINE
        ));
    }

    @Test
    public void shouldRankOnlyTheMostRecentRequestsAndSaySo() {
        // given - more recorded requests than the ranking budget; the oldest is the closest miss
        received(request().withMethod("GET").withPath("/some_path"));
        for (int i = 0; i < VerificationFailureSummary.MAX_REQUESTS_EVALUATED; i++) {
            received(request().withMethod("POST").withPath("/other_" + i));
        }

        // when
        String result = verify(verification()
            .withRequest(request().withMethod("GET").withPath("/some_path").withHeader("x-new", "1"))
            .withTimes(once()));

        // then - the out-of-budget oldest request is not ranked, and the budget is stated
        String summary = summaryOf(result);
        assertThat(summary, containsString("    1. POST /other_" + (VerificationFailureSummary.MAX_REQUESTS_EVALUATED - 1) + " differs in: method, path, headers" + NEW_LINE));
        assertThat(summary, containsString("  closest requests ranked from the 500 most recent of 501 recorded requests" + NEW_LINE));
        assertThat(summary, not(containsString("/some_path differs in")));
    }
}
