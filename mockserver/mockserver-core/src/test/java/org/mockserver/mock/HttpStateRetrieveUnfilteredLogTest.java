package org.mockserver.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.DnsRecordType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.verify.VerificationTimes;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_MATCHED;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.DnsRequestDefinition.dnsRequest;
import static org.mockserver.model.DnsResponse.dnsResponse;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.Verification.verification;
import static org.slf4j.event.Level.INFO;

/**
 * A log read with no filter, or with the empty filter {@code {}} that the retrieve endpoint, MCP {@code retrieve_logs}
 * and the dashboard send for "all", must return every log entry: also those carrying a DNS or binary request, which an
 * empty HTTP request matcher rejects. A filter that sets a field must still narrow.
 */
public class HttpStateRetrieveUnfilteredLogTest {

    private static final String HTTP_USERS = "http users marker";
    private static final String HTTP_ORDERS = "http orders marker";
    private static final String DNS_MATCHED = "dns matched marker";
    private static final String BINARY_MATCHED = "binary matched marker";
    private static final String NO_REQUEST = "no request marker";

    private HttpState httpState;

    @Before
    public void setUp() {
        Configuration configuration = configuration().logLevel(INFO);
        MockServerLogger logger = new MockServerLogger(configuration, HttpStateRetrieveUnfilteredLogTest.class);
        httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true));
        log(RECEIVED_REQUEST, request("/api/users"), HTTP_USERS);
        log(RECEIVED_REQUEST, request("/api/orders"), HTTP_ORDERS);
        log(EXPECTATION_MATCHED, dnsRequest("api.example.com", DnsRecordType.A), DNS_MATCHED);
        log(EXPECTATION_MATCHED, binaryRequest("ping".getBytes()), BINARY_MATCHED);
        log(SERVER_CONFIGURATION, null, NO_REQUEST);
        // a retrieve runs after every entry logged before it, so the dashboard walk below sees them all
        entries(request().withPath("/sync"));
    }

    @After
    public void stopHttpState() {
        httpState.stop();
    }

    @Test
    public void noFilterReturnsDnsAndBinaryEntries() {
        assertThat(markers(null), containsInAnyOrder(HTTP_USERS, HTTP_ORDERS, DNS_MATCHED, BINARY_MATCHED, NO_REQUEST));
    }

    @Test
    public void emptyHttpFilterReturnsDnsAndBinaryEntries() {
        assertThat(markers(request()), containsInAnyOrder(HTTP_USERS, HTTP_ORDERS, DNS_MATCHED, BINARY_MATCHED, NO_REQUEST));
    }

    @Test
    public void noFilterIncludingDeletedReturnsDnsAndBinaryEntries() {
        List<String> markers = markers((filter, consumer) -> httpState.getMockServerLog().retrieveMessageLogEntriesIncludingDeleted(filter, consumer), null);

        assertThat(markers, containsInAnyOrder(HTTP_USERS, HTTP_ORDERS, DNS_MATCHED, BINARY_MATCHED, NO_REQUEST));
    }

    @Test
    public void dashboardWalkWithItsDefaultEmptyFilterReturnsDnsAndBinaryEntries() throws Exception {
        CompletableFuture<List<String>> future = new CompletableFuture<>();
        httpState.getMockServerLog().retrieveLogEntriesInReverseForUI(
            request(),
            logEntry -> !logEntry.isDeleted(),
            LogEntry::getMessageFormat,
            stream -> future.complete(stream.filter(HttpStateRetrieveUnfilteredLogTest::isMarker).collect(Collectors.toList()))
        );

        assertThat(future.get(10, SECONDS), containsInAnyOrder(HTTP_USERS, HTTP_ORDERS, DNS_MATCHED, BINARY_MATCHED, NO_REQUEST));
    }

    @Test
    public void retrieveLogsEndpointWithEmptyBodyReturnsDnsAndBinaryEntries() {
        // what MCP retrieve_logs sends
        String body = retrieveLogs(null);

        assertThat(body, containsString(DNS_MATCHED));
        assertThat(body, containsString(BINARY_MATCHED));
        assertThat(body, containsString(HTTP_USERS));
    }

    @Test
    public void retrieveLogsEndpointWithEmptyJsonObjectReturnsDnsAndBinaryEntries() {
        String body = retrieveLogs("{}");

        assertThat(body, containsString(DNS_MATCHED));
        assertThat(body, containsString(BINARY_MATCHED));
        assertThat(body, containsString(HTTP_USERS));
    }

    @Test
    public void unfilteredReadReturnsTheCreatedExpectationEntryOfADnsMock() {
        httpState.add(new Expectation(dnsRequest("mocked.example.com", DnsRecordType.A)).withId("dns-mock").thenRespondWithDns(dnsResponse()));

        List<Object> createdFor = entries(request()).stream()
            .filter(logEntry -> logEntry.getType() == LogEntry.LogMessageType.CREATED_EXPECTATION)
            .map(logEntry -> logEntry.getArguments()[1])
            .collect(Collectors.toList());

        assertThat(createdFor, hasItem((Object) "dns-mock"));
    }

    @Test
    public void httpFilterStillExcludesDnsAndBinaryEntries() {
        List<String> markers = markers(request().withPath("/api/users"));

        assertThat(markers, containsInAnyOrder(HTTP_USERS, NO_REQUEST));
    }

    @Test
    public void httpMethodFilterStillExcludesDnsAndBinaryEntries() {
        List<String> markers = markers(request().withMethod("GET"));

        assertThat(markers, containsInAnyOrder(HTTP_USERS, HTTP_ORDERS, NO_REQUEST));
    }

    @Test
    public void dnsFilterStillNarrowsToDnsEntries() {
        List<String> markers = markers(dnsRequest("api.example.com", DnsRecordType.A));

        assertThat(markers, containsInAnyOrder(DNS_MATCHED, NO_REQUEST));
    }

    @Test
    public void emptyHttpFilterOnTheEndpointStillReturnsOnlyHttpRecordedRequests() {
        HttpResponse response = httpState.retrieve(request("/mockserver/retrieve").withMethod("PUT").withQueryStringParameter("type", "REQUESTS").withBody("{}"));

        assertThat(response.getBodyAsString(), containsString("/api/users"));
        assertThat(response.getBodyAsString(), containsString("/api/orders"));
        assertThat(response.getBodyAsString(), not(containsString("api.example.com")));
    }

    @Test
    public void emptyHttpVerificationStillCountsOnlyHttpRequests() throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        httpState.getMockServerLog().verify(verification().withRequest(request()).withTimes(VerificationTimes.exactly(2)), result::complete);

        assertThat(result.get(10, SECONDS), is(""));
    }

    private void log(LogEntry.LogMessageType type, RequestDefinition request, String marker) {
        LogEntry logEntry = new LogEntry()
            .setType(type)
            .setLogLevel(INFO)
            .setMessageFormat(marker);
        if (request != null) {
            logEntry.setHttpRequest(request);
        }
        httpState.getMockServerLogger().logEvent(logEntry);
    }

    private String retrieveLogs(String body) {
        HttpRequest retrieve = request("/mockserver/retrieve")
            .withMethod("PUT")
            .withQueryStringParameter("type", "LOGS")
            .withQueryStringParameter("format", "LOG_ENTRIES");
        if (body != null) {
            retrieve.withBody(body);
        }
        return httpState.retrieve(retrieve).getBodyAsString();
    }

    private List<String> markers(RequestDefinition filter) {
        return markers((requestFilter, consumer) -> httpState.getMockServerLog().retrieveMessageLogEntries(requestFilter, consumer), filter);
    }

    private List<String> markers(BiConsumer<RequestDefinition, Consumer<List<LogEntry>>> retrieval, RequestDefinition filter) {
        return entries(retrieval, filter).stream()
            .map(LogEntry::getMessageFormat)
            .filter(HttpStateRetrieveUnfilteredLogTest::isMarker)
            .collect(Collectors.toList());
    }

    private List<LogEntry> entries(RequestDefinition filter) {
        return entries((requestFilter, consumer) -> httpState.getMockServerLog().retrieveMessageLogEntries(requestFilter, consumer), filter);
    }

    private List<LogEntry> entries(BiConsumer<RequestDefinition, Consumer<List<LogEntry>>> retrieval, RequestDefinition filter) {
        CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
        retrieval.accept(filter, future::complete);
        try {
            return future.get(10, SECONDS);
        } catch (Exception e) {
            throw new AssertionError("log retrieval did not complete", e);
        }
    }

    private static boolean isMarker(String messageFormat) {
        return messageFormat != null && messageFormat.endsWith(" marker");
    }
}
