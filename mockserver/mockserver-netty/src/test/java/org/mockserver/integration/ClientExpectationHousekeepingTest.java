package org.mockserver.integration;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Format;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.mock.ExpectationFilter.expectationFilter;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Java client round trip of expectation metadata, retrieving and clearing by metadata and by never-matched, and
 * paged retrieval, against a running server.
 */
public class ClientExpectationHousekeepingTest {

    private static ClientAndServer clientAndServer;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeClass
    public static void startServer() {
        clientAndServer = startClientAndServer();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(clientAndServer);
    }

    @Before
    public void registerExpectations() {
        clientAndServer.reset();
        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("team", "checkout");
        contact.put("oncall", Arrays.asList("ann", "bob"));
        clientAndServer.when(request().withPath("/one"))
            .withId("one").withPriority(30)
            .withMetadata("owner", "team-a").withMetadata("ticket", 101).withMetadata("contact", contact)
            .withMetadata("tags", Arrays.asList("smoke", "checkout"))
            .respond(response().withBody("one"));
        clientAndServer.when(request().withPath("/two"))
            .withId("two").withPriority(20)
            .withMetadata("owner", "team-b")
            .respond(response().withBody("two"));
        clientAndServer.when(request().withPath("/three"))
            .withId("three").withPriority(10)
            .respond(response().withBody("three"));
    }

    @Test
    public void metadataRoundTripsThroughCreateAndRetrieve() {
        Expectation[] retrieved = clientAndServer.retrieveActiveExpectations(null);

        assertThat(retrieved[0].getId(), is("one"));
        assertThat(retrieved[0].getMetadata().get("owner"), is("team-a"));
        assertThat(retrieved[0].getMetadata().get("ticket"), is(101));
        assertThat(retrieved[0].getMetadata().get("tags"), is(Arrays.asList("smoke", "checkout")));
        assertThat(((Map<?, ?>) retrieved[0].getMetadata().get("contact")).get("oncall"), is(Arrays.asList("ann", "bob")));
        assertThat(retrieved[2].getMetadata() == null, is(true));
    }

    @Test
    public void retrievedExpectationsCanBeUpsertedBackUnchanged() {
        Expectation[] retrieved = clientAndServer.retrieveActiveExpectations(null);

        clientAndServer.upsert(retrieved);

        assertThat(Arrays.asList(clientAndServer.retrieveActiveExpectations(null)), is(Arrays.asList(retrieved)));
    }

    @Test
    public void retrievesAndClearsByMetadata() {
        assertThat(ids(clientAndServer.retrieveActiveExpectationsByMetadata("owner", "team-a")), contains("one"));
        assertThat(ids(clientAndServer.retrieveActiveExpectationsByMetadata("ticket", 101)), contains("one"));
        assertThat(ids(clientAndServer.retrieveActiveExpectations(null, expectationFilter().withMetadataJsonPath("$.contact[?(@.team == 'checkout')]"))), contains("one"));

        clientAndServer.clearByMetadata("owner", "team-b");

        assertThat(ids(clientAndServer.retrieveActiveExpectations(null)), contains("one", "three"));
    }

    @Test
    public void retrievesAndClearsExpectationsThatHaveNeverMatched() throws Exception {
        assertThat(get("/two"), is("two"));

        assertThat(ids(clientAndServer.retrieveUnmatchedExpectations()), contains("one", "three"));

        clientAndServer.clearUnmatchedExpectations();

        assertThat(ids(clientAndServer.retrieveActiveExpectations(null)), contains("two"));
    }

    @Test
    public void pagesActiveAndRecordedExpectations() {
        assertThat(ids(clientAndServer.retrieveActiveExpectations(null, expectationFilter().withOffset(1).withLimit(1))), contains("two"));
        assertThat(clientAndServer.retrieveActiveExpectations(null, expectationFilter().withLimit(1), Format.JAVA), containsString(".withMetadata(\"owner\", \"team-a\")"));
        assertThat(clientAndServer.retrieveRecordedExpectations(null, 0, 5).length, is(0));
    }

    @Test
    public void rejectsAnInvalidFilterWithABadRequest() {
        // the client turns the server's 400 into an IllegalArgumentException carrying the server's reason
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> clientAndServer.retrieveActiveExpectations(null, expectationFilter().withLimit(-1)));

        assertThat(exception.getMessage(), containsString("\"limit\""));
    }

    @Test
    public void aMixedCaseFilterNameOverHttpSelectsAndAnUnrecognisedOneIsRefused() throws Exception {
        assertThat(put("/mockserver/clear?type=expectations&MetadataKey=owner&METADATAVALUE=team-b"), is(200));
        assertThat(ids(clientAndServer.retrieveActiveExpectations(null)), contains("one", "three"));

        assertThat(put("/mockserver/clear?type=expectations&metadata_key=owner"), is(400));
        assertThat(put("/mockserver/clear?Unmatched="), is(400));
        assertThat(ids(clientAndServer.retrieveActiveExpectations(null)), contains("one", "three"));
    }

    @Test
    public void malformedPercentEncodingOverARawSocketIsRefusedAndClearsNothing() throws Exception {
        for (String query : Arrays.asList(
            "type=expectations&metadataKey=owner&metadataValue=100%",
            "metadataKey=owner&metadataValue=%ZZ",
            "metadataKey=owner&metadataValue=team-a&note=50%off"
        )) {
            assertThat(query, rawPutStatus("/mockserver/clear?" + query), is(400));
        }
        assertThat(ids(clientAndServer.retrieveActiveExpectations(null)), contains("one", "two", "three"));
        assertThat(clientAndServer.clearByMetadataKey("owner").retrieveActiveExpectations(null).length, is(1));
    }

    // java.net.http refuses a URI with a bare %, so the request line is written by hand, as curl would send it
    private static int rawPutStatus(String pathAndQuery) throws Exception {
        try (java.net.Socket socket = new java.net.Socket("localhost", clientAndServer.getPort())) {
            socket.setSoTimeout(10_000);
            java.io.OutputStream out = socket.getOutputStream();
            out.write(("PUT " + pathAndQuery + " HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.flush();
            String statusLine = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII)).readLine();
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }

    private static int put(String pathAndQuery) throws Exception {
        return HTTP.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + clientAndServer.getPort() + pathAndQuery)).PUT(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString()
        ).statusCode();
    }

    @Test
    public void notAndOptionalPrefixedKeysAndValuesSelectLiterally() {
        clientAndServer.when(request().withPath("/negated")).withId("negated").withMetadata("owner", "!team-a").respond(response());
        clientAndServer.when(request().withPath("/optional")).withId("optional").withMetadata("?owner", "?team-a").respond(response());

        assertThat(ids(clientAndServer.retrieveActiveExpectationsByMetadata("owner", "!team-a")), contains("negated"));

        clientAndServer.clear(null, expectationFilter().withMetadataKey("!owner"));
        clientAndServer.clearByMetadata("owner", "!team-a");
        assertThat(ids(clientAndServer.retrieveActiveExpectations(null)), contains("one", "two", "three", "optional"));

        clientAndServer.clearByMetadata("?owner", "?team-a");
        assertThat(ids(clientAndServer.retrieveActiveExpectations(null)), contains("one", "two", "three"));
    }

    private static List<String> ids(Expectation[] expectations) {
        return Arrays.stream(expectations).map(Expectation::getId).collect(Collectors.toList());
    }

    private static String get(String path) throws Exception {
        return HTTP.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + clientAndServer.getPort() + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        ).body();
    }
}
