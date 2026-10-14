package org.mockserver.integration;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.model.DnsRecord;
import org.mockserver.model.DnsRequestDefinition;
import org.mockserver.model.DnsResponse;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;
import org.slf4j.event.Level;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.model.DnsRequestDefinition.dnsRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.testing.socket.DnsQuery.addressAnsweredFor;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;
import static org.mockserver.verify.VerificationTimes.never;
import static org.mockserver.verify.VerificationTimes.once;

/**
 * A DNS query or binary message MockServer received is recorded with its request definition, so the Java client can
 * verify, verify in a sequence and retrieve it by a DNS or binary request matcher. The server logs at WARN, as a DNS
 * query must be recorded at every log level to be counted.
 */
public class ClientNonHttpRequestVerificationTest {

    private static final byte[] FIRST_BYTES = "first-binary".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECOND_BYTES = "second-binary".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FIRST_REPLY = "first-reply".getBytes(StandardCharsets.UTF_8);

    private static ClientAndServer clientAndServer;

    @BeforeClass
    public static void startServer() {
        clientAndServer = startClientAndServer(configuration().dnsEnabled(true).dnsPort(0).logLevel(Level.WARN).redactSecretsInLog(false));
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
            .respondWithBinary(binaryResponse(FIRST_REPLY));
        clientAndServer.when(request().withPath("/http")).withId("http")
            .respond(response().withStatusCode(200));
    }

    @Test
    public void verifiesADnsQueryItReceived() throws Exception {
        queryFirstName();

        clientAndServer.verify(dnsRequest("first.example."), once());
        clientAndServer.verify(dnsRequest("second.example."), never());
    }

    @Test
    public void failsToVerifyADnsQueryItDidNotReceive() throws Exception {
        queryFirstName();

        AssertionError failure = assertThrows(AssertionError.class, () -> clientAndServer.verify(dnsRequest("second.example."), atLeast(1)));

        assertThat(failure.getMessage(), containsString("Request not found at least once"));
        assertThat(failure.getMessage(), containsString("second.example."));
    }

    @Test
    public void verifiesABinaryMessageItReceived() throws Exception {
        sendFirstBinaryMessage();

        clientAndServer.verify(binaryRequest(FIRST_BYTES), once());
        clientAndServer.verify(binaryRequest(SECOND_BYTES), never());
    }

    @Test
    public void verifyNeverFailsForADnsQueryItReceived() throws Exception {
        queryFirstName();

        AssertionError failure = assertThrows(AssertionError.class, () -> clientAndServer.verifyNever(dnsRequest("first.example."), Duration.ofMillis(200)));

        assertThat(failure.getMessage(), containsString("Found request matching verification"));
    }

    @Test
    public void verifyNeverFailsForABinaryMessageItReceived() throws Exception {
        sendFirstBinaryMessage();

        AssertionError failure = assertThrows(AssertionError.class, () -> clientAndServer.verifyNever(binaryRequest(FIRST_BYTES), Duration.ofMillis(200)));

        assertThat(failure.getMessage(), containsString("Found request matching verification"));
    }

    @Test
    public void verifiesASequenceOfHttpDnsAndBinaryRequests() throws Exception {
        assertThat(send("/first"), is(404));
        queryFirstName();
        sendFirstBinaryMessage();
        assertThat(send("/last"), is(404));

        clientAndServer.verify(request("/first"), dnsRequest("first.example."), binaryRequest(FIRST_BYTES), request("/last"));
        AssertionError failure = assertThrows(AssertionError.class, () -> clientAndServer.verify(binaryRequest(FIRST_BYTES), dnsRequest("first.example.")));
        assertThat(failure.getMessage(), containsString("Request sequence not found"));
    }

    @Test
    public void anEmptyHttpMatcherCountsOnlyHttpRequests() throws Exception {
        assertThat(send("/http"), is(200));
        queryFirstName();
        sendFirstBinaryMessage();

        clientAndServer.verify(request(), exactly(1));
    }

    @Test
    public void retrievesTheRecordedDnsQueryAndBinaryMessage() throws Exception {
        queryFirstName();
        sendFirstBinaryMessage();
        assertThat(send("/http"), is(200));

        RequestDefinition[] dnsQueries = clientAndServer.retrieveRecordedRequestDefinitions(dnsRequest("first.example."));
        assertThat(dnsQueries, arrayWithSize(1));
        assertThat(dnsQueries[0], instanceOf(DnsRequestDefinition.class));
        assertThat(((DnsRequestDefinition) dnsQueries[0]).getDnsName(), is("first.example."));

        RequestDefinition[] binaryMessages = clientAndServer.retrieveRecordedRequestDefinitions(binaryRequest(FIRST_BYTES));
        assertThat(binaryMessages, arrayWithSize(1));
        assertThat(new String(((BinaryRequestDefinition) binaryMessages[0]).getBinaryData(), StandardCharsets.UTF_8), is("first-binary"));

        assertThat("an unfiltered read returns only HTTP requests, as before", clientAndServer.retrieveRecordedRequestDefinitions(null), arrayWithSize(1));
        HttpRequest[] httpRequests = clientAndServer.retrieveRecordedRequests(null);
        assertThat(httpRequests, arrayWithSize(1));
        assertThat(httpRequests[0].getPath().getValue(), is("/http"));
        assertThat("a DNS filter returns no HTTP request", clientAndServer.retrieveRecordedRequests(dnsRequest("first.example.")), arrayWithSize(0));
    }

    @Test
    public void verifyingADnsExpectationByIdFailsWithTheReason() throws Exception {
        queryFirstName();

        AssertionError failure = assertThrows(AssertionError.class, () -> clientAndServer.verify("dns-first", once()));

        assertThat(failure.getMessage(), containsString("DNS and binary requests cannot be verified by expectation id"));
        assertThat(failure.getMessage(), containsString("first.example."));
    }

    private static void queryFirstName() throws Exception {
        assertThat(addressAnsweredFor("first.example.", clientAndServer.getDnsPort()), is("10.0.0.1"));
    }

    private static void sendFirstBinaryMessage() throws Exception {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(10_000);
            socket.connect(new InetSocketAddress("127.0.0.1", clientAndServer.getPort()), 10_000);
            socket.getOutputStream().write(FIRST_BYTES);
            socket.getOutputStream().flush();
            InputStream input = socket.getInputStream();
            byte[] reply = new byte[FIRST_REPLY.length];
            int read = 0;
            while (read < reply.length) {
                int count = input.read(reply, read, reply.length - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
            assertThat(new String(reply, 0, read, StandardCharsets.UTF_8), is("first-reply"));
        }
    }

    private static int send(String path) throws Exception {
        return HttpClient.newHttpClient().send(
            java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + clientAndServer.getPort() + path))
                .timeout(Duration.ofSeconds(10))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        ).statusCode();
    }
}
