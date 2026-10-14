package org.mockserver.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.DnsRecordType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Not;
import org.mockserver.model.RequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.RequestDefinitionSerializer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_MATCHED;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.model.DnsRequestDefinition.dnsRequest;
import static org.mockserver.model.DnsResponse.dnsResponse;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.slf4j.event.Level.INFO;

/**
 * {@code PUT /mockserver/clear} with an empty filter ({@code {}}, which is what a client's {@code clear(request())}
 * sends) must clear every expectation and log entry, as an empty body does: {@code not(...)}, DNS and binary
 * expectations too, which reverse matching the empty filter would skip.
 */
public class HttpStateClearUnfilteredTest {

    private HttpState httpState;

    @Before
    public void setUp() {
        Configuration configuration = configuration().logLevel(INFO);
        MockServerLogger logger = new MockServerLogger(configuration, HttpStateClearUnfilteredTest.class);
        httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true));
        httpState.add(
            new Expectation(request().withMethod("GET").withPath("/api/users")).withId("http-1").thenRespond(response()),
            new Expectation(request().withMethod("GET").withPath("/api/orders")).withId("http-2").thenRespond(response()),
            new Expectation(Not.not(request().withPath("/secret"))).withId("not-1").thenRespond(response()),
            new Expectation(dnsRequest("api.example.com", DnsRecordType.A)).withId("dns-1").thenRespondWithDns(dnsResponse()),
            new Expectation(binaryRequest("ping".getBytes())).withId("bin-1").thenRespondWithBinary(binaryResponse())
        );
        httpState.getMockServerLogger().logEvent(
            new LogEntry()
                .setType(EXPECTATION_RESPONSE)
                .setLogLevel(INFO)
                .setHttpRequest(request("/api/users"))
                .setHttpResponse(response())
                .setMessageFormat("returning response:{}for request:{}")
                .setArguments(response(), request("/api/users"))
        );
        httpState.getMockServerLogger().logEvent(
            new LogEntry()
                .setType(EXPECTATION_MATCHED)
                .setLogLevel(INFO)
                .setHttpRequest(dnsRequest("api.example.com", DnsRecordType.A))
                .setMessageFormat("request:{}matched expectation:{}")
                .setArguments(dnsRequest("api.example.com", DnsRecordType.A), "dns-1")
        );
        assertThat(expectationIds(), containsInAnyOrder("http-1", "http-2", "not-1", "dns-1", "bin-1"));
        assertThat(seededLogTypes(), containsInAnyOrder(EXPECTATION_RESPONSE, EXPECTATION_MATCHED));
    }

    @After
    public void stopHttpState() {
        httpState.stop();
    }

    @Test
    public void emptyBodyClearsEverything() {
        httpState.clear(clear(null));

        assertThat(expectationIds(), empty());
        assertThat(seededLogTypes(), empty());
    }

    @Test
    public void emptyJsonObjectClearsEveryExpectationAndLogEntry() {
        httpState.clear(clear(null).withBody("{}"));

        assertThat(expectationIds(), empty());
        assertThat(seededLogTypes(), empty());
    }

    @Test
    public void clientSerialisedEmptyRequestClearsEverything() {
        // what MockServerClient.clear(request()) sends
        httpState.clear(clear(null).withBody(new RequestDefinitionSerializer(new MockServerLogger()).serialize(request())));

        assertThat(expectationIds(), empty());
        assertThat(seededLogTypes(), empty());
    }

    @Test
    public void emptyJsonObjectClearsEveryExpectationWithTheCandidateIndexEngaged() {
        httpState.getRequestMatchers().withCandidateIndexThreshold(2);

        httpState.clear(clear("expectations").withBody("{}"));

        assertThat(expectationIds(), empty());
    }

    @Test
    public void emptyJsonObjectWithTypeExpectationsClearsEveryExpectationOnly() {
        httpState.clear(clear("expectations").withBody("{}"));

        assertThat(expectationIds(), empty());
        assertThat(seededLogTypes(), containsInAnyOrder(EXPECTATION_RESPONSE, EXPECTATION_MATCHED));
    }

    @Test
    public void emptyJsonObjectWithTypeLogClearsEveryLogEntryOnly() {
        httpState.clear(clear("log").withBody("{}"));

        assertThat(seededLogTypes(), empty());
        assertThat(expectationIds(), containsInAnyOrder("http-1", "http-2", "not-1", "dns-1", "bin-1"));
    }

    @Test
    public void emptyJsonObjectWithTypeAllClearsEverything() {
        httpState.clear(clear("all").withBody("{}"));

        assertThat(expectationIds(), empty());
        assertThat(seededLogTypes(), empty());
    }

    @Test
    public void resetClearsEverything() {
        httpState.reset();

        assertThat(expectationIds(), empty());
        assertThat(seededLogTypes(), empty());
    }

    @Test
    public void pathFilterStillNarrows() {
        httpState.clear(clear(null).withBody("{\"path\":\"/api/users\"}"));

        List<String> ids = expectationIds();
        assertThat(ids, not(hasItem("http-1")));
        assertThat(ids, hasItem("http-2"));
        assertThat(ids, hasItem("dns-1"));
        assertThat(ids, hasItem("bin-1"));
        List<LogEntry.LogMessageType> logTypes = seededLogTypes();
        assertThat(logTypes, not(hasItem(EXPECTATION_RESPONSE)));
        assertThat(logTypes, hasItem(EXPECTATION_MATCHED));
    }

    @Test
    public void methodOnlyFilterStillNarrows() {
        httpState.clear(clear("expectations").withBody("{\"method\":\"GET\"}"));

        List<String> ids = expectationIds();
        assertThat(ids, not(hasItem("http-1")));
        assertThat(ids, not(hasItem("http-2")));
        assertThat(ids, hasItem("dns-1"));
        assertThat(ids, hasItem("bin-1"));
    }

    @Test
    public void dnsFilterStillNarrows() {
        httpState.clear(clear("expectations").withBody("{\"dnsName\":\"api.example.com\",\"dnsType\":\"A\"}"));

        assertThat(expectationIds(), containsInAnyOrder("http-1", "http-2", "not-1", "bin-1"));
    }

    private static HttpRequest clear(String type) {
        HttpRequest request = request("/mockserver/clear").withMethod("PUT");
        return type == null ? request : request.withQueryStringParameter("type", type);
    }

    private List<String> expectationIds() {
        return httpState.getRequestMatchers().retrieveActiveExpectations(null).stream()
            .map(Expectation::getId)
            .collect(Collectors.toList());
    }

    /**
     * The seeded HTTP entry, found with no filter, and the seeded DNS entry, found with a DNS filter (an unfiltered
     * log retrieval reverse matches an empty HTTP request, which a DNS request fails).
     */
    private List<LogEntry.LogMessageType> seededLogTypes() {
        List<LogEntry.LogMessageType> types = logTypes(null, EXPECTATION_RESPONSE);
        types.addAll(logTypes(dnsRequest("api.example.com", DnsRecordType.A), EXPECTATION_MATCHED));
        return types;
    }

    private List<LogEntry.LogMessageType> logTypes(RequestDefinition filter, LogEntry.LogMessageType type) {
        CompletableFuture<List<LogEntry>> future = new CompletableFuture<>();
        httpState.getMockServerLog().retrieveMessageLogEntries(filter, future::complete);
        try {
            return future.get(60, SECONDS).stream()
                .map(LogEntry::getType)
                .filter(entryType -> entryType == type)
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new AssertionError("log retrieval did not complete", e);
        }
    }
}
