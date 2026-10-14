package org.mockserver.integration;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.mock.Expectation;
import org.mockserver.model.DnsRecord;
import org.mockserver.model.DnsResponse;
import org.mockserver.model.RequestDefinition;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.model.DnsRequestDefinition.dnsRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * The Java client sends a DNS or binary request definition to the server as that definition, so clearing and
 * retrieving by one acts on what it describes rather than on everything, and a verification sequence keeps it as a step.
 */
public class ClientNonHttpRequestFilterTest {

    private static final byte[] FIRST_BYTES = "first-binary".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECOND_BYTES = "second-binary".getBytes(StandardCharsets.UTF_8);

    private static ClientAndServer clientAndServer;

    @BeforeClass
    public static void startServer() {
        clientAndServer = startClientAndServer();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(clientAndServer);
    }

    @Before
    public void registerOneOfEachKind() {
        clientAndServer.reset();
        clientAndServer.when(dnsRequest("first.example.")).withId("dns-first")
            .respondWithDns(new DnsResponse().withAnswerRecords(DnsRecord.aRecord("first.example.", "10.0.0.1")));
        clientAndServer.when(dnsRequest("second.example.")).withId("dns-second")
            .respondWithDns(new DnsResponse().withAnswerRecords(DnsRecord.aRecord("second.example.", "10.0.0.2")));
        clientAndServer.when(binaryRequest(FIRST_BYTES)).withId("binary-first")
            .respondWithBinary(binaryResponse("first-reply".getBytes(StandardCharsets.UTF_8)));
        clientAndServer.when(binaryRequest(SECOND_BYTES)).withId("binary-second")
            .respondWithBinary(binaryResponse("second-reply".getBytes(StandardCharsets.UTF_8)));
        clientAndServer.when(request().withPath("/http")).withId("http")
            .respond(response().withStatusCode(200));
    }

    @Test
    public void clearingByDnsFilterRemovesOnlyThatDnsExpectation() {
        clientAndServer.clear(dnsRequest("first.example."));

        assertThat(activeIds(null), containsInAnyOrder("dns-second", "binary-first", "binary-second", "http"));
    }

    @Test
    public void clearingByBinaryFilterRemovesOnlyThatBinaryExpectation() {
        clientAndServer.clear(binaryRequest(FIRST_BYTES));

        assertThat(activeIds(null), containsInAnyOrder("dns-first", "dns-second", "binary-second", "http"));
    }

    @Test
    public void clearingByDnsFilterKeepsTheLogOfHttpRequests() throws Exception {
        assertThat(send("/http"), is(200));

        clientAndServer.clear(dnsRequest("first.example."));

        assertThat(clientAndServer.retrieveRecordedRequests(request("/http")).length, is(1));
    }

    @Test
    public void retrievingByDnsFilterReturnsOnlyThatDnsExpectation() {
        assertThat(activeIds(dnsRequest("second.example.")), containsInAnyOrder("dns-second"));
    }

    @Test
    public void retrievingByBinaryFilterReturnsOnlyThatBinaryExpectation() {
        assertThat(activeIds(binaryRequest(SECOND_BYTES)), containsInAnyOrder("binary-second"));
    }

    @Test
    public void verifyingASequenceWithADnsQueryInTheMiddleFailsRatherThanSkippingIt() throws Exception {
        assertThat(send("/first"), is(404));
        assertThat(send("/last"), is(404));

        AssertionError failure = assertThrows(AssertionError.class, () -> clientAndServer.verify(
            request("/first"), dnsRequest("never.example."), request("/last")
        ));

        assertThat(failure.getMessage(), containsString("Request sequence not found"));
        assertThat(failure.getMessage(), containsString("never.example."));
    }

    private static int send(String path) throws Exception {
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + clientAndServer.getPort() + path))
                .timeout(Duration.ofSeconds(10))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        ).statusCode();
    }

    private static List<String> activeIds(RequestDefinition filter) {
        return Arrays.stream(clientAndServer.retrieveActiveExpectations(filter))
            .map(Expectation::getId)
            .collect(Collectors.toList());
    }
}
