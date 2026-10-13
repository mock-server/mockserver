package org.mockserver.log;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ExpectationId;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.verify.Disposition;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.OpenAPIDefinition.openAPI;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.mockserver.verify.VerificationTimes.once;

/**
 * The "verification summary:" block on response verification, request and response sequences,
 * expectation-id sequences and verification by disposition: always appended after the existing message.
 */
public class MockServerEventLogResponseAndSequenceSummaryTest {

    private static final String PETS_SPEC = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"pets\",\"version\":\"1\"},\"paths\":{" +
        "\"/pets\":{\"get\":{\"operationId\":\"listPets\",\"responses\":{\"200\":{\"description\":\"ok\"}}}}," +
        "\"/pets/{petId}\":{\"get\":{\"operationId\":\"showPetById\",\"parameters\":[{\"name\":\"petId\",\"in\":\"path\",\"required\":true,\"schema\":{\"type\":\"string\"}}],\"responses\":{\"200\":{\"description\":\"ok\"}}}}" +
        "}}";
    private static final String SUMMARY_SEPARATOR = NEW_LINE + NEW_LINE + "verification summary:" + NEW_LINE;
    private static final Scheduler scheduler = new Scheduler(configuration(), new MockServerLogger());
    private final Configuration configuration = configuration();
    private MockServerEventLog mockServerEventLog;

    @Before
    public void setupTestFixture() {
        configuration.detailedVerificationFailures(true);
        mockServerEventLog = new MockServerEventLog(configuration, new MockServerLogger(configuration, MockServerLogger.class), scheduler, true);
    }

    @After
    public void stopEventLog() {
        mockServerEventLog.stop();
    }

    @AfterClass
    public static void stopScheduler() {
        scheduler.shutdown();
    }

    private String verify(Verification verification) {
        return await(result -> mockServerEventLog.verify(verification, result));
    }

    private String verify(VerificationSequence verificationSequence) {
        return await(result -> mockServerEventLog.verify(verificationSequence, result));
    }

    private static String await(Consumer<Consumer<String>> verify) {
        CompletableFuture<String> result = new CompletableFuture<>();
        verify.accept(result::complete);
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

    private void mocked(HttpRequest httpRequest, HttpResponse httpResponse) {
        mockServerEventLog.add(new LogEntry().setHttpRequest(httpRequest).setHttpResponse(httpResponse).setType(EXPECTATION_RESPONSE));
    }

    private void forwarded(HttpRequest httpRequest, HttpResponse httpResponse) {
        mockServerEventLog.add(new LogEntry().setHttpRequest(httpRequest).setHttpResponse(httpResponse).setType(FORWARDED_REQUEST));
    }

    private void mockedBy(String expectationId, HttpRequest httpRequest) {
        mockServerEventLog.add(new LogEntry()
            .setHttpRequest(httpRequest)
            .setHttpResponse(response().withStatusCode(200))
            .setExpectationId(expectationId)
            .setType(EXPECTATION_RESPONSE));
    }

    private static String summaryOf(String failureMessage) {
        int index = failureMessage.indexOf(SUMMARY_SEPARATOR);
        return index == -1 ? null : failureMessage.substring(index + SUMMARY_SEPARATOR.length());
    }

    private static String messageBeforeSummary(String failureMessage) {
        int index = failureMessage.indexOf(SUMMARY_SEPARATOR);
        return index == -1 ? failureMessage : failureMessage.substring(0, index);
    }

    // response verification

    @Test
    public void shouldAppendCountsAndClosestNonMatchingResponsesAfterTheExistingMessage() {
        // given
        mocked(request().withMethod("GET").withPath("/a"), response().withStatusCode(200).withBody("ok"));
        mocked(request().withMethod("GET").withPath("/b"), response().withStatusCode(200).withBody("nope"));
        mocked(request().withMethod("GET").withPath("/c"), response().withStatusCode(500).withBody("ok"));
        mocked(request().withMethod("GET").withPath("/d"), response().withStatusCode(404).withBody("bad"));

        // when
        String result = verify(verification().withResponse(response().withStatusCode(200).withBody("ok")).withTimes(exactly(2)));

        // then - the existing message comes first, the summary is the last block
        assertThat(messageBeforeSummary(result), startsWith("Response not found exactly 2 times, expected:<{" + NEW_LINE +
            "  \"statusCode\" : 200," + NEW_LINE +
            "  \"body\" : \"ok\"" + NEW_LINE +
            "}> but was:<[ {"));
        assertThat(summaryOf(result), is(
            "  expected exactly 2 times but matched 1 time among 4 recorded responses" + NEW_LINE +
                "  closest non-matching responses (fewest differing fields first):" + NEW_LINE +
                "    1. GET /c returned 500 differs in: response status" + NEW_LINE +
                "    2. GET /b returned 200 differs in: response body" + NEW_LINE +
                "    3. GET /d returned 404 differs in: response status, response body" + NEW_LINE
        ));
    }

    @Test
    public void shouldLeaveTheResponseMessageUnchangedWhenTheSummaryWouldAddNothing() {
        // given - every recorded response matches, nothing arrived late, the verification did not wait
        mocked(request().withPath("/a"), response().withStatusCode(200));
        mocked(request().withPath("/a"), response().withStatusCode(200));

        // when
        String result = verify(verification()
            .withResponse(response().withStatusCode(200))
            .withTimes(atLeast(3))
            .withMaximumNumberOfRequestToReturnInVerificationFailure(1));

        // then
        assertThat(result, is("Response not found at least 3 times, expected:<{" + NEW_LINE +
            "  \"statusCode\" : 200" + NEW_LINE +
            "}> but was found 2 times among 2 recorded responses"));
    }

    @Test
    public void shouldNotAppendResponseSummaryWhenDetailedVerificationFailuresIsDisabled() {
        // given
        configuration.detailedVerificationFailures(false);
        mocked(request().withPath("/a"), response().withStatusCode(500));

        // when
        String result = verify(verification().withResponse(response().withStatusCode(200)).withTimes(once()));

        // then
        assertThat(result, startsWith("Response not found exactly once"));
        assertThat(result, not(containsString("verification summary:")));
    }

    @Test
    public void shouldNotListResponseNearMissesWhenTheResponseWasFoundTooOften() {
        // given
        mocked(request().withPath("/a"), response().withStatusCode(200));
        mocked(request().withPath("/a"), response().withStatusCode(200));
        mocked(request().withPath("/a"), response().withStatusCode(500));

        // when
        String result = verify(verification().withResponse(response().withStatusCode(200)).withTimes(once()));

        // then
        assertThat(result, startsWith("Response found 2 times but should have been found exactly once"));
        assertThat(result, not(containsString("verification summary:")));
    }

    @Test
    public void shouldSayNoResponseArrivedWhenAWaitingResponseVerificationSawNone() {
        // when
        String result = verify(verification().withResponse(response().withStatusCode(200)).withTimes(once()).withTimeout(100L));

        // then
        assertThat(summaryOf(result), is(
            "  expected exactly once but matched 0 times among 0 recorded responses" + NEW_LINE +
                "  0 responses arrived after verification started" + NEW_LINE
        ));
    }

    @Test
    public void shouldCountResponsesRecordedAfterTheVerificationStarted() {
        // given
        mockServerEventLog.add(new LogEntry()
            .setHttpRequest(request().withPath("/late"))
            .setHttpResponse(response().withStatusCode(500))
            .setType(EXPECTATION_RESPONSE)
            .setEpochTime(System.currentTimeMillis() + SECONDS.toMillis(60)));

        // when
        String result = verify(verification().withResponse(response().withStatusCode(200)).withTimes(once()));

        // then
        assertThat(summaryOf(result), endsWith(
            "    1. /late returned 500 differs in: response status" + NEW_LINE +
                "  1 response arrived after verification started" + NEW_LINE
        ));
    }

    @Test
    public void shouldNameOnlyMethodPathAndStatusOfARecordedResponse() {
        // given - secrets in the recorded request and response
        mocked(
            request().withMethod("POST").withPath("/login").withHeader("Authorization", "Bearer request-secret").withBody("password=body-secret"),
            response().withStatusCode(401).withHeader("Set-Cookie", "session=cookie-secret").withBody("{\"token\":\"response-secret\"}")
        );

        // when
        String result = verify(verification()
            .withResponse(response().withStatusCode(200).withHeader("Set-Cookie", "session=other").withBody("{\"token\":\"x\"}"))
            .withTimes(once()));

        // then
        String summary = summaryOf(result);
        assertThat(summary, containsString("    1. POST /login returned 401 differs in: response status, response headers, response body" + NEW_LINE));
        assertThat(summary, not(containsString("secret")));
    }

    @Test
    public void shouldRankOnlyTheMostRecentResponsesAndSaySo() {
        // given - the oldest recorded response is the closest miss but outside the ranking budget
        mocked(request().withPath("/oldest"), response().withStatusCode(201).withBody("ok"));
        for (int i = 0; i < VerificationFailureSummary.MAX_REQUESTS_EVALUATED; i++) {
            mocked(request().withPath("/other_" + i), response().withStatusCode(500).withBody("bad"));
        }

        // when
        String result = verify(verification().withResponse(response().withStatusCode(200).withBody("ok")).withTimes(once()));

        // then
        String summary = summaryOf(result);
        assertThat(summary, containsString("  closest responses ranked from the 500 most recent of 501 recorded responses" + NEW_LINE));
        assertThat(summary, not(containsString("/oldest")));
    }

    // verification by disposition

    @Test
    public void shouldCountMatchingRequestsThatWereForwardedWhenVerifyingMocked() {
        // given
        received(request().withPath("/a"));
        forwarded(request().withPath("/a"), response().withStatusCode(200));

        // when
        String result = verify(verification().withRequest(request().withPath("/a")).withDisposition(Disposition.MOCKED).withTimes(once()));

        // then
        assertThat(result, startsWith("Request not found exactly once, expected:<{"));
        assertThat(summaryOf(result), is(
            "  expected exactly once but matched 0 times among 1 recorded request" + NEW_LINE +
                "  1 recorded request matched but was forwarded, not mocked" + NEW_LINE
        ));
    }

    @Test
    public void shouldCountMatchingRequestsThatWereMockedWhenVerifyingForwarded() {
        // given
        received(request().withPath("/a"));
        received(request().withPath("/a"));
        mocked(request().withPath("/a"), response().withStatusCode(200));
        mocked(request().withPath("/a"), response().withStatusCode(200));

        // when
        String result = verify(verification().withRequest(request().withPath("/a")).withDisposition(Disposition.FORWARDED).withTimes(once()));

        // then
        assertThat(summaryOf(result), is(
            "  expected exactly once but matched 0 times among 2 recorded requests" + NEW_LINE +
                "  2 recorded requests matched but were mocked, not forwarded" + NEW_LINE
        ));
    }

    // request sequences

    @Test
    public void shouldNameTheFailedStepAndItsClosestRequests() {
        // given
        received(request().withMethod("GET").withPath("/a"));
        received(request().withMethod("POST").withPath("/b"));
        received(request().withMethod("GET").withPath("/x"));

        // when
        String result = verify(new VerificationSequence().withRequests(
            request().withMethod("GET").withPath("/a"),
            request().withMethod("PUT").withPath("/b"),
            request().withPath("/c")
        ));

        // then
        assertThat(messageBeforeSummary(result), startsWith("Request sequence not found, expected:<[ {"));
        assertThat(messageBeforeSummary(result), containsString(NEW_LINE + NEW_LINE + "closest match diff:" + NEW_LINE));
        assertThat(summaryOf(result), is(
            "  step 2 of 3 not found in the 2 recorded requests after step 1 matched recorded request 1 of 3" + NEW_LINE +
                "  closest non-matching requests for step 2 (fewest differing fields first):" + NEW_LINE +
                "    1. POST /b differs in: method" + NEW_LINE +
                "    2. GET /x differs in: method, path" + NEW_LINE
        ));
    }

    @Test
    public void shouldNameAFailedFirstStep() {
        // given
        received(request().withPath("/x"));

        // when
        String result = verify(new VerificationSequence().withRequests(request().withPath("/a"), request().withPath("/b")));

        // then
        assertThat(summaryOf(result), is(
            "  step 1 of 2 not found in 1 recorded request" + NEW_LINE +
                "  closest non-matching requests for step 1 (fewest differing fields first):" + NEW_LINE +
                "    1. /x differs in: path" + NEW_LINE
        ));
    }

    @Test
    public void shouldCountRequestsForTheFailedStepThatCameBeforeThePreviousStep() {
        // given - recorded out of order
        received(request().withPath("/b"));
        received(request().withPath("/a"));

        // when
        String result = verify(new VerificationSequence().withRequests(request().withPath("/a"), request().withPath("/b")));

        // then
        assertThat(summaryOf(result), is(
            "  step 2 of 2 not found in the 0 recorded requests after step 1 matched recorded request 2 of 2" + NEW_LINE +
                "  1 recorded request matched step 2 but came before step 1's match" + NEW_LINE
        ));
    }

    @Test
    public void shouldNotCountThePreviousStepsOwnMatchAsComingBefore() {
        // given - one request, which both steps match
        received(request().withPath("/a"));

        // when
        String result = verify(new VerificationSequence().withRequests(request().withPath("/a"), request().withPath("/a")));

        // then
        assertThat(summaryOf(result), is(
            "  step 2 of 2 not found in the 0 recorded requests after step 1 matched recorded request 1 of 1" + NEW_LINE
        ));
    }

    @Test
    public void shouldNotAppendSequenceSummaryWhenDetailedVerificationFailuresIsDisabled() {
        // given
        configuration.detailedVerificationFailures(false);
        received(request().withPath("/x"));

        // when
        String result = verify(new VerificationSequence().withRequests(request().withPath("/a")));

        // then
        assertThat(result, startsWith("Request sequence not found"));
        assertThat(result, not(containsString("verification summary:")));
    }

    @Test
    public void shouldSayNoRequestArrivedWhenAWaitingSequenceSawNone() {
        // when
        String result = verify(new VerificationSequence().withRequests(request().withPath("/a")).withTimeout(100L));

        // then
        assertThat(summaryOf(result), is(
            "  step 1 of 1 not found in 0 recorded requests" + NEW_LINE +
                "  0 requests arrived after verification started" + NEW_LINE
        ));
    }

    @Test
    public void shouldCheckOnlyTheMostRecentRequestsForASequenceAndSaySo() {
        // given
        received(request().withMethod("GET").withPath("/oldest"));
        for (int i = 0; i < VerificationFailureSummary.MAX_REQUESTS_EVALUATED; i++) {
            received(request().withMethod("POST").withPath("/other_" + i));
        }

        // when
        String result = verify(new VerificationSequence().withRequests(request().withMethod("GET").withPath("/a")));

        // then
        String summary = summaryOf(result);
        assertThat(summary, startsWith("  step 1 of 1 not found in 501 recorded requests" + NEW_LINE));
        assertThat(summary, containsString("  only the 500 most recent of 501 recorded requests were checked" + NEW_LINE));
        assertThat(summary, not(containsString("/oldest")));
    }

    // response sequences

    @Test
    public void shouldNameTheFailedStepOfAResponseSequenceAndItsClosestPairs() {
        // given
        mocked(request().withMethod("GET").withPath("/a"), response().withStatusCode(200));
        mocked(request().withMethod("GET").withPath("/b"), response().withStatusCode(500));
        mocked(request().withMethod("GET").withPath("/c"), response().withStatusCode(201));

        // when
        String result = verify(new VerificationSequence()
            .withRequests(request().withMethod("GET").withPath("/a"), request().withMethod("GET").withPath("/b"))
            .withResponses(response().withStatusCode(200), response().withStatusCode(201)));

        // then
        assertThat(messageBeforeSummary(result), startsWith("Response sequence not found, expected:<[ {"));
        assertThat(summaryOf(result), is(
            "  step 2 of 2 not found in the 2 recorded responses after step 1 matched recorded response 1 of 3" + NEW_LINE +
                "  closest non-matching responses for step 2 (fewest differing fields first):" + NEW_LINE +
                "    1. GET /c returned 201 differs in: path" + NEW_LINE +
                "    2. GET /b returned 500 differs in: response status" + NEW_LINE
        ));
    }

    @Test
    public void shouldCountResponsesForTheFailedStepThatCameBeforeThePreviousStep() {
        // given
        mocked(request().withPath("/b"), response().withStatusCode(201));
        mocked(request().withPath("/a"), response().withStatusCode(200));

        // when
        String result = verify(new VerificationSequence().withResponses(response().withStatusCode(200), response().withStatusCode(201)));

        // then
        assertThat(summaryOf(result), is(
            "  step 2 of 2 not found in the 0 recorded responses after step 1 matched recorded response 2 of 2" + NEW_LINE +
                "  1 recorded response matched step 2 but came before step 1's match" + NEW_LINE
        ));
    }

    @Test
    public void shouldGiveOnlyTheStepLineWhenTheFailedResponseStepHasAnOpenApiRequest() {
        // given - judged on its response alone, GET /x would wrongly count as an out-of-order match
        mocked(request().withMethod("GET").withPath("/x"), response().withStatusCode(200));
        mocked(request().withMethod("GET").withPath("/a"), response().withStatusCode(200));
        mocked(request().withMethod("GET").withPath("/pets"), response().withStatusCode(200));

        // when
        String result = verify(new VerificationSequence()
            .withRequests(request().withPath("/a"), openAPI(PETS_SPEC, "showPetById"))
            .withResponses(response().withStatusCode(200), response().withStatusCode(200)));

        // then
        assertThat(messageBeforeSummary(result), startsWith("Response sequence not found, expected:<"));
        assertThat(summaryOf(result), is(
            "  step 2 of 2 not found in the 1 recorded response after step 1 matched recorded response 2 of 3" + NEW_LINE
        ));
    }

    @Test
    public void shouldGiveOnlyTheStepLineWhenTheFailedRequestStepIsAnOpenApiRequest() {
        // given
        received(request().withMethod("GET").withPath("/pets"));
        received(request().withMethod("GET").withPath("/a"));

        // when
        String result = verify(new VerificationSequence().withRequests(request().withPath("/a"), openAPI(PETS_SPEC, "listPets")));

        // then
        assertThat(summaryOf(result), is(
            "  step 2 of 2 not found in the 0 recorded requests after step 1 matched recorded request 2 of 2" + NEW_LINE
        ));
    }

    // expectation-id sequences

    @Test
    public void shouldNameTheFailedStepOfAnExpectationIdSequence() {
        // given - the second expectation's request came first
        mockedBy("second", request().withPath("/b"));
        mockedBy("first", request().withPath("/a"));

        // when
        String result = verify(new VerificationSequence().withExpectationIds(new ExpectationId().withId("first"), new ExpectationId().withId("second")));

        // then
        assertThat(messageBeforeSummary(result), startsWith("Request sequence not found, expected:<"));
        assertThat(summaryOf(result), is(
            "  step 2 of 2 not found in the 0 recorded requests after step 1 matched recorded request 2 of 2" + NEW_LINE +
                "  1 recorded request matched step 2 but came before step 1's match" + NEW_LINE
        ));
    }
}
