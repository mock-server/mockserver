package org.mockserver.mock;

import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.model.DnsRecordType;
import org.mockserver.verify.Disposition;
import org.mockserver.model.RequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationTimes;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_MATCHED;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.DnsRequestDefinition.dnsRequest;
import static org.mockserver.model.DnsResponse.dnsResponse;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationSequence.verificationSequence;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.mockserver.verify.VerificationTimes.never;
import static org.mockserver.verify.VerificationTimes.once;
import static org.slf4j.event.Level.INFO;
import static org.slf4j.event.Level.WARN;

/**
 * A DNS query or binary message is logged as a received request carrying its definition, so a verification by a DNS
 * or binary request counts it, a sequence finds it, and a DNS or binary filter retrieves it. Reads with no filter
 * still return and count only HTTP requests. The logged bytes follow maxLoggedBodyBytes and redactSecretsInLog.
 */
public class HttpStateNonHttpVerificationTest {

    private static final byte[] PING = "ping-message".getBytes(StandardCharsets.UTF_8);

    private HttpState httpState;

    @After
    public void stopHttpState() {
        if (httpState != null) {
            httpState.stop();
        }
    }

    /**
     * Redaction set on the instance, so a sequential-phase test that sets the static redactSecretsInLog cannot
     * change what these tests see.
     */
    private static Configuration redactionOff() {
        return configuration().redactSecretsInLog(false);
    }

    private void start(Configuration configuration) {
        MockServerLogger logger = new MockServerLogger(configuration, HttpStateNonHttpVerificationTest.class);
        httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true));
    }

    private void received(RequestDefinition request) {
        httpState.getMockServerLogger().logEvent(new LogEntry().setType(RECEIVED_REQUEST).setLogLevel(INFO).setHttpRequest(request).setMessageFormat("received"));
    }

    private void receivedOneOfEachKind() {
        received(request("/http"));
        received(dnsRequest("api.example.com", DnsRecordType.A));
        received(binaryRequest(PING));
    }

    private String verify(Verification verification) throws Exception {
        return httpState.verify(verification).get(10, SECONDS);
    }

    private String verify(RequestDefinition request, VerificationTimes times) throws Exception {
        return verify(verification().withRequest(request).withTimes(times));
    }

    private String retrieveRequests(String body) {
        return httpState.retrieve(request("/mockserver/retrieve").withMethod("PUT").withQueryStringParameter("type", "REQUESTS").withBody(body)).getBodyAsString();
    }

    private List<LogEntry> requestLogEntries(RequestDefinition filter) throws Exception {
        CompletableFuture<List<LogEntry>> entries = new CompletableFuture<>();
        httpState.getMockServerLog().retrieveRequestLogEntries(filter, entries::complete);
        return entries.get(10, SECONDS);
    }

    @Test
    public void verifiesAReceivedDnsQueryByItsDefinition() throws Exception {
        start(redactionOff().logLevel(WARN));
        receivedOneOfEachKind();

        assertThat(verify(dnsRequest("api.example.com"), once()), is(""));
        assertThat(verify(dnsRequest("api.example.com", DnsRecordType.AAAA), never()), is(""));
        assertThat(verify(dnsRequest("api.example.com"), never()), containsString("Request found 1 time but should have been found exactly 0 times"));
    }

    @Test
    public void verifiesAReceivedBinaryMessageByItsBytes() throws Exception {
        start(redactionOff().logLevel(WARN));
        receivedOneOfEachKind();

        assertThat(verify(binaryRequest(PING), once()), is(""));
        assertThat(verify(binaryRequest("other".getBytes(StandardCharsets.UTF_8)), never()), is(""));
        assertThat(verify(binaryRequest(PING), never()), containsString("Request found 1 time but should have been found exactly 0 times"));
    }

    @Test
    public void verifiesASequenceThroughDnsAndBinarySteps() throws Exception {
        start(redactionOff());
        receivedOneOfEachKind();
        received(request("/last"));

        assertThat(httpState.verify(verificationSequence().withRequests(request("/http"), dnsRequest("api.example.com"), binaryRequest(PING), request("/last"))).get(10, SECONDS), is(""));
        assertThat(httpState.verify(verificationSequence().withRequests(binaryRequest(PING), dnsRequest("api.example.com"))).get(10, SECONDS), containsString("Request sequence not found"));
    }

    @Test
    public void anUnfilteredVerificationCountsOnlyHttpRequests() throws Exception {
        start(redactionOff());
        receivedOneOfEachKind();

        assertThat(verify(request(), exactly(1)), is(""));
    }

    @Test
    public void retrievesDnsAndBinaryRequestsOnlyByADnsOrBinaryFilter() {
        start(redactionOff());
        receivedOneOfEachKind();

        String unfiltered = retrieveRequests("{}");
        assertThat(unfiltered, containsString("/http"));
        assertThat(unfiltered, not(containsString("api.example.com")));
        assertThat(unfiltered, not(containsString("binaryData")));
        assertThat(retrieveRequests("{\"dnsName\":\"api.example.com\"}"), containsString("api.example.com"));
        assertThat(retrieveRequests("{\"binaryData\":\"" + Base64.getEncoder().encodeToString(PING) + "\"}"), containsString(Base64.getEncoder().encodeToString(PING)));
    }

    @Test
    public void keepsOnlyTheFirstMaxLoggedBodyBytesOfABinaryMessage() throws Exception {
        start(redactionOff().maxLoggedBodyBytes(4));
        received(binaryRequest(PING));

        List<LogEntry> entries = requestLogEntries(binaryRequest());

        assertThat(entries, hasSize(1));
        assertThat(((BinaryRequestDefinition) entries.get(0).getHttpRequest()).getBinaryData(), is(Arrays.copyOf(PING, 4)));
    }

    @Test
    public void masksTheBytesOfABinaryMessageWhenRedactingButStillCountsIt() throws Exception {
        start(configuration().redactSecretsInLog(true));
        received(binaryRequest(PING));

        String retrieved = retrieveRequests("{\"binaryData\":\"" + Base64.getEncoder().encodeToString(PING) + "\"}");

        assertThat(retrieved, not(containsString(Base64.getEncoder().encodeToString(PING))));
        assertThat(retrieved, containsString(Base64.getEncoder().encodeToString("***REDACTED***".getBytes(StandardCharsets.UTF_8))));
        assertThat(verify(binaryRequest(PING), once()), is(""));
    }

    @Test
    public void matchesASequenceOnTheReceivedRequestsWhenRedactingAndShowsRedactedCopies() throws Exception {
        start(configuration().redactSecretsInLog(true));
        received(binaryRequest(PING));
        received(request("/a").withHeader("Authorization", "Bearer secret-token"));

        assertThat("an HTTP step matching a redacted header", sequence(request("/a").withHeader("Authorization", "Bearer secret-token")), is(""));
        assertThat("a binary step", sequence(binaryRequest(PING)), is(""));
        assertThat(sequence(binaryRequest(PING), request("/a").withHeader("Authorization", "Bearer secret-token")), is(""));
        String failure = sequence(request("/a"), request("/missing"));
        assertThat(failure, containsString("Request sequence not found"));
        assertThat(failure, not(containsString("secret-token")));
        assertThat(failure, not(containsString(Base64.getEncoder().encodeToString(PING))));
    }

    @Test
    public void masksABinaryRequestQuotedInALogMessageWhenRedacting() {
        LogEntry matched = new LogEntry().setType(EXPECTATION_MATCHED).setLogLevel(INFO).setHttpRequest(binaryRequest(PING))
            .setMessageFormat("request:{}matched").setArguments(binaryRequest(PING));
        Configuration redacting = configuration().redactSecretsInLog(true);

        assertThat(matched.getMessage(redacting), not(containsString(Base64.getEncoder().encodeToString(PING))));
        assertThat(String.valueOf(matched.getArguments(redacting)[0]), not(containsString(Base64.getEncoder().encodeToString(PING))));
        assertThat(matched.getMessage(redactionOff()), containsString(Base64.getEncoder().encodeToString(PING)));
    }

    private String sequence(RequestDefinition... requests) throws Exception {
        return httpState.verify(verificationSequence().withRequests(requests)).get(10, SECONDS);
    }

    @Test
    public void weighsTheBytesOfABinaryMessageAgainstTheLogByteBudget() {
        byte[] large = new byte[10_000];

        assertThat(new LogEntry().setType(RECEIVED_REQUEST).setHttpRequest(binaryRequest(large)).estimatedHeapSize(), greaterThanOrEqualTo((long) large.length));
    }

    @Test
    public void rejectsVerifyingADnsOrBinaryRequestWithAResponseByDispositionOrByExpectationId() {
        start(redactionOff());
        httpState.add(new Expectation(dnsRequest("api.example.com")).withId("dns-mock").thenRespondWithDns(dnsResponse()));

        assertRejected(() -> httpState.verify(verification().withRequest(dnsRequest("api.example.com")).withResponse(response())), "with a response");
        assertRejected(() -> httpState.verify(verification().withRequest(binaryRequest(PING)).withDisposition(Disposition.MOCKED)), "by disposition");
        assertRejected(() -> httpState.verify(verification().withExpectationId(new org.mockserver.model.ExpectationId().withId("dns-mock")).withTimes(never())), "by expectation id");
        assertRejected(() -> httpState.verify(verificationSequence().withRequests(dnsRequest("api.example.com")).withResponses(response())), "with a response");
        assertRejected(() -> httpState.verify(verificationSequence().withExpectationIds(new org.mockserver.model.ExpectationId().withId("dns-mock"))), "by expectation id");
    }

    private static void assertRejected(org.junit.function.ThrowingRunnable verification, String how) {
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, verification);
        assertThat(rejected.getMessage(), containsString("DNS and binary requests cannot be verified " + how));
    }
}
