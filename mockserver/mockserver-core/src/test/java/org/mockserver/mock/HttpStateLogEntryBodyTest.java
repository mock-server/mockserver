package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.fixture.FixtureRedactor;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.DnsRecordType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.repeat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.DnsRequestDefinition.dnsRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * GET /mockserver/logEntryBody returns one log entry's request or response with its body in full, which the
 * dashboard uses to load a body it shortened in its live updates. It is a control-plane read, so it is gated
 * like one, and it shows the entry as the log does, so secrets are redacted when redactSecretsInLog is on.
 */
public class HttpStateLogEntryBodyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REQUEST_BODY = repeat("request-body-", 10_000);
    private static final String RESPONSE_BODY = repeat("response-body-", 10_000);
    private static final byte[] BINARY_MESSAGE = repeat("binary-", 20_000).getBytes(StandardCharsets.UTF_8);

    private Scheduler scheduler;
    private HttpState httpState;

    private static class CapturingResponseWriter extends ResponseWriter {
        private final CompletableFuture<HttpResponse> response = new CompletableFuture<>();

        CapturingResponseWriter() {
            super(configuration(), new MockServerLogger());
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
            this.response.complete(response);
        }
    }

    @After
    public void tearDown() {
        if (httpState != null) {
            httpState.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private String seedExchange(Configuration configuration) throws Exception {
        return seed(configuration, new LogEntry()
            .setType(EXPECTATION_RESPONSE)
            .setLogLevel(Level.INFO)
            .setHttpRequest(request("/large").withHeader("Authorization", "Bearer SECRET-TOKEN-1").withBody(REQUEST_BODY))
            .setHttpResponse(response().withBody(RESPONSE_BODY))
            .setMessageFormat("returning response:{}"));
    }

    private String seed(Configuration configuration, LogEntry entry) throws Exception {
        MockServerLogger logger = new MockServerLogger(configuration, HttpStateLogEntryBodyTest.class);
        scheduler = new Scheduler(configuration, logger, true);
        httpState = new HttpState(configuration, logger, scheduler);
        String id = entry.id();
        httpState.getMockServerLog().add(entry);
        CompletableFuture<Integer> recorded = new CompletableFuture<>();
        httpState.getMockServerLog().retrieveMessageLogEntries(null, entries -> recorded.complete(entries.size()));
        assertThat(recorded.get(10, SECONDS) >= 1, is(true));
        return id;
    }

    private HttpResponse get(String query) throws Exception {
        CapturingResponseWriter writer = new CapturingResponseWriter();
        httpState.handle(request("/mockserver/logEntryBody").withMethod("GET").withQueryStringParameters(parse(query)), writer, false);
        return writer.response.get(10, SECONDS);
    }

    private static org.mockserver.model.Parameters parse(String query) {
        org.mockserver.model.Parameters parameters = new org.mockserver.model.Parameters();
        for (String pair : query.split("&")) {
            String[] nameValue = pair.split("=", 2);
            parameters.withEntry(nameValue[0], nameValue.length > 1 ? nameValue[1] : "");
        }
        return parameters;
    }

    @Test
    public void shouldReturnTheFullRequestAndResponseOfALogEntry() throws Exception {
        String id = seedExchange(configuration());

        HttpResponse requestPart = get("id=" + id + "&part=request");
        assertThat(requestPart.getStatusCode(), is(200));
        JsonNode request = OBJECT_MAPPER.readTree(requestPart.getBodyAsString()).get("httpRequest");
        assertThat(request.get("path").asText(), is("/large"));
        assertThat(request.get("body").asText(), is(REQUEST_BODY));

        HttpResponse responsePart = get("id=" + id + "&part=response");
        assertThat(responsePart.getStatusCode(), is(200));
        assertThat(OBJECT_MAPPER.readTree(responsePart.getBodyAsString()).get("httpResponse").get("body").asText(), is(RESPONSE_BODY));
    }

    @Test
    public void shouldRedactSecretsWhenRedactionIsOn() throws Exception {
        String id = seedExchange(configuration().redactSecretsInLog(true));

        HttpResponse requestPart = get("id=" + id + "&part=request");

        assertThat(requestPart.getStatusCode(), is(200));
        assertThat(requestPart.getBodyAsString(), not(containsString("SECRET-TOKEN-1")));
        assertThat(OBJECT_MAPPER.readTree(requestPart.getBodyAsString()).get("httpRequest").get("body").asText(), is(REQUEST_BODY));
    }

    private static LogEntry receivedBinaryMessage() {
        return new LogEntry()
            .setType(RECEIVED_REQUEST)
            .setLogLevel(Level.INFO)
            .setHttpRequest(binaryRequest(BINARY_MESSAGE))
            .setMessageFormat("received binary request:{}");
    }

    @Test
    public void shouldReturnTheFullBinaryMessageOfALogEntry() throws Exception {
        String id = seed(configuration(), receivedBinaryMessage());

        HttpResponse requestPart = get("id=" + id + "&part=request");

        assertThat(requestPart.getStatusCode(), is(200));
        JsonNode request = OBJECT_MAPPER.readTree(requestPart.getBodyAsString()).get("httpRequest");
        assertThat(request.get("binaryData").asText(), is(Base64.getEncoder().encodeToString(BINARY_MESSAGE)));
    }

    @Test
    public void shouldMaskTheBinaryMessageWhenRedactionIsOn() throws Exception {
        String id = seed(configuration().redactSecretsInLog(true), receivedBinaryMessage());

        HttpResponse requestPart = get("id=" + id + "&part=request");

        assertThat(requestPart.getStatusCode(), is(200));
        String binaryData = OBJECT_MAPPER.readTree(requestPart.getBodyAsString()).get("httpRequest").get("binaryData").asText();
        assertThat(new String(Base64.getDecoder().decode(binaryData), StandardCharsets.UTF_8), is(FixtureRedactor.REDACTED_PLACEHOLDER));
    }

    @Test
    public void shouldReturnTheDnsQueryOfALogEntry() throws Exception {
        String id = seed(configuration(), new LogEntry()
            .setType(RECEIVED_REQUEST)
            .setLogLevel(Level.INFO)
            .setHttpRequest(dnsRequest("api.example.com", DnsRecordType.AAAA))
            .setMessageFormat("received DNS query"));

        HttpResponse requestPart = get("id=" + id + "&part=request");

        assertThat(requestPart.getStatusCode(), is(200));
        JsonNode request = OBJECT_MAPPER.readTree(requestPart.getBodyAsString()).get("httpRequest");
        assertThat(request.get("dnsName").asText(), is("api.example.com"));
        assertThat(request.get("dnsType").asText(), is("AAAA"));
    }

    @Test
    public void shouldAnswerNotFoundForAnUnknownOrClearedEntry() throws Exception {
        String id = seedExchange(configuration());

        HttpResponse unknown = get("id=no\"such\\entry<b>&part=request");
        assertThat(unknown.getStatusCode(), is(404));
        JsonNode error = OBJECT_MAPPER.readTree(unknown.getBodyAsString());
        assertThat("the caller's id is not echoed into the error", error.size(), is(1));
        assertThat(error.get("error").asText(), containsString("cleared or evicted"));
        httpState.getMockServerLog().reset();
        assertThat(get("id=" + id + "&part=request").getStatusCode(), is(404));
    }

    @Test
    public void shouldRejectAMissingIdOrAnUnknownPart() throws Exception {
        String id = seedExchange(configuration());

        assertThat(get("part=request").getStatusCode(), is(400));
        assertThat(get("id=" + id + "&part=headers").getStatusCode(), is(400));
    }

    @Test
    public void shouldRequireControlPlaneAuthenticationWhenItIsConfigured() throws Exception {
        String id = seedExchange(configuration());
        httpState.setControlPlaneAuthenticationHandler(request -> false);

        HttpResponse response = get("id=" + id + "&part=request");

        assertThat(response.getStatusCode(), is(401));
        assertThat(response.getBodyAsString(), not(containsString("request-body-")));
    }
}
