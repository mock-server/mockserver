package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Retrieve and clear by metadata, by never-matched, and paged retrieval, through {@code PUT /mockserver/retrieve}
 * and {@code PUT /mockserver/clear}.
 */
public class HttpStateExpectationHousekeepingTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private HttpState httpState;

    @Before
    public void setUp() {
        Configuration configuration = configuration();
        MockServerLogger logger = new MockServerLogger(configuration, HttpStateExpectationHousekeepingTest.class);
        httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true));
        httpState.add(
            new Expectation(request().withPath("/one")).withId("one").withPriority(30)
                .withMetadata("owner", "team-a").withMetadata("tags", Arrays.asList("smoke", "checkout")).withMetadata("ticket", 101)
                .thenRespond(response()),
            new Expectation(request().withPath("/two")).withId("two").withPriority(20)
                .withMetadata("owner", "team-b").withMetadata("tags", Arrays.asList("nightly"))
                .thenRespond(response()),
            new Expectation(request().withPath("/three")).withId("three").withPriority(10)
                .withNamespace("tenant").withMetadata("owner", "team-a")
                .thenRespond(response()),
            new Expectation(request().withPath("/four")).withId("four").withPriority(0)
                .thenRespond(response())
        );
    }

    @After
    public void stopHttpState() {
        httpState.stop();
    }

    @Test
    public void retrievedExpectationsCarryTheirMetadata() throws Exception {
        JsonNode retrieved = retrieve(active());

        assertThat(retrieved.get(0).get("id").asText(), is("one"));
        assertThat(retrieved.get(0).get("metadata").toString(), is("{\"owner\":\"team-a\",\"tags\":[\"smoke\",\"checkout\"],\"ticket\":101}"));
        assertThat(retrieved.get(3).has("metadata"), is(false));
    }

    @Test
    public void retrievesByMetadataKeyAndValue() throws Exception {
        assertThat(ids(active().withQueryStringParameter("metadataKey", "owner").withQueryStringParameter("metadataValue", "team-a")), contains("one", "three"));
        assertThat(ids(active().withQueryStringParameter("metadataKey", "ticket").withQueryStringParameter("metadataValue", "101")), contains("one"));
        assertThat(ids(active().withQueryStringParameter("metadataKey", "owner")), contains("one", "two", "three"));
        assertThat(ids(active().withQueryStringParameter("metadataKey", "owner").withQueryStringParameter("metadataValue", "nobody")), empty());
    }

    @Test
    public void retrievesByMetadataJsonPath() throws Exception {
        assertThat(ids(active().withQueryStringParameter("metadataJsonPath", "$.tags[?(@ == 'smoke')]")), contains("one"));
        assertThat(ids(active().withQueryStringParameter("metadataJsonPath", "$[?(@.owner == 'team-b')]")), contains("two"));
    }

    @Test
    public void metadataFilterCombinesWithTheNamespaceAndRequestMatcherFilters() throws Exception {
        HttpRequest byOwner = active().withQueryStringParameter("metadataKey", "owner").withQueryStringParameter("metadataValue", "team-a");

        // the namespace view on retrieve is "this namespace plus global"
        assertThat(ids(byOwner.clone().withQueryStringParameter("namespace", "tenant")), contains("one", "three"));
        assertThat(ids(byOwner.clone().withQueryStringParameter("namespace", "elsewhere")), contains("one"));
        assertThat(ids(byOwner.clone().withBody("{\"path\":\"/three\"}")), contains("three"));
    }

    @Test
    public void retrievesAndClearsOnlyExpectationsThatHaveNeverMatched() throws Exception {
        assertThat(httpState.getRequestMatchers().firstMatchingExpectation(request("/one")).getId(), is("one"));
        assertThat(httpState.getRequestMatchers().firstMatchingExpectation(request("/four")).getId(), is("four"));

        assertThat(ids(active().withQueryStringParameter("unmatched", "true")), contains("two", "three"));

        httpState.clear(clear().withQueryStringParameter("type", "expectations").withQueryStringParameter("unmatched", "true"));

        assertThat(ids(active()), contains("one", "four"));
    }

    @Test
    public void anExpectationChangedByAnUpsertCountsAsUnmatchedAgain() throws Exception {
        httpState.getRequestMatchers().firstMatchingExpectation(request("/four"));
        assertThat(ids(active().withQueryStringParameter("unmatched", "true")), contains("one", "two", "three"));

        httpState.add(new Expectation(request().withPath("/four")).withId("four").withMetadata("owner", "team-c").thenRespond(response()));

        assertThat(ids(active().withQueryStringParameter("unmatched", "true")), contains("one", "two", "three", "four"));
    }

    @Test
    public void clearsByMetadataLeavingEverythingElse() throws Exception {
        httpState.clear(clear().withQueryStringParameter("type", "expectations").withQueryStringParameter("metadataKey", "owner").withQueryStringParameter("metadataValue", "team-a"));

        assertThat(ids(active()), contains("two", "four"));
    }

    @Test
    public void clearByMetadataJsonPathWithTypeAllLeavesTheLog() throws Exception {
        httpState.log(new LogEntry().setType(FORWARDED_REQUEST).setHttpRequest(request("/two")).setHttpResponse(response("logged"))
            .setExpectation(new Expectation(request("/two"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response("logged"))));

        httpState.clear(clear().withQueryStringParameter("metadataJsonPath", "$.tags[?(@ == 'nightly')]"));

        assertThat(ids(active()), contains("one", "three", "four"));
        assertThat(retrieve(request("/mockserver/retrieve").withMethod("PUT").withQueryStringParameter("type", "RECORDED_EXPECTATIONS")).size(), is(1));
    }

    @Test
    public void clearByMetadataCombinesWithTheNamespaceAndTheRequestMatcher() throws Exception {
        // the namespace on clear is "only this namespace", as for clearByNamespace
        httpState.clear(clear().withQueryStringParameter("metadataKey", "owner").withQueryStringParameter("metadataValue", "team-a").withQueryStringParameter("namespace", "tenant"));
        assertThat(ids(active()), contains("one", "two", "four"));

        httpState.clear(clear().withQueryStringParameter("metadataKey", "owner").withBody("{\"path\":\"/two\"}"));
        assertThat(ids(active()), contains("one", "four"));

        httpState.clear(clear().withQueryStringParameter("metadataKey", "owner").withBody("{\"id\":\"four\"}"));
        assertThat(ids(active()), contains("one", "four"));
        httpState.clear(clear().withQueryStringParameter("metadataKey", "owner").withBody("{\"id\":\"one\"}"));
        assertThat(ids(active()), contains("four"));
    }

    @Test
    public void pagesActiveExpectationsInMatchOrder() throws Exception {
        assertThat(ids(active().withQueryStringParameter("limit", "2")), contains("one", "two"));
        assertThat(ids(active().withQueryStringParameter("offset", "2").withQueryStringParameter("limit", "1")), contains("three"));
        assertThat(ids(active().withQueryStringParameter("offset", "3")), contains("four"));
        assertThat(ids(active().withQueryStringParameter("offset", "4")), empty());
        // paging is applied after the other filters
        assertThat(ids(active().withQueryStringParameter("metadataKey", "owner").withQueryStringParameter("offset", "1").withQueryStringParameter("limit", "1")), contains("two"));
    }

    @Test
    public void pagesTheJavaFormatToo() {
        String java = httpState.retrieve(active().withQueryStringParameter("format", "JAVA").withQueryStringParameter("limit", "1")).getBodyAsString();

        assertThat(java, containsString("/one"));
        assertThat(java, containsString(".withMetadata(\"owner\", \"team-a\")"));
        assertThat(java, containsString(".withMetadataJson(\"tags\", \"[\\\"smoke\\\",\\\"checkout\\\"]\")"));
        assertThat(java.contains("/two"), is(false));
    }

    @Test
    public void pagesRecordedExpectationsInTheOrderReceived() throws Exception {
        for (String path : Arrays.asList("/r0", "/r1", "/r2", "/r3")) {
            httpState.log(new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request(path))
                .setHttpResponse(response(path))
                .setExpectation(new Expectation(request(path), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response(path))));
        }
        HttpRequest recorded = request("/mockserver/retrieve").withMethod("PUT").withQueryStringParameter("type", "RECORDED_EXPECTATIONS");

        assertThat(paths(recorded), contains("/r0", "/r1", "/r2", "/r3"));
        assertThat(paths(recorded.clone().withQueryStringParameter("offset", "1").withQueryStringParameter("limit", "2")), contains("/r1", "/r2"));
        assertThat(retrieve(recorded.clone().withQueryStringParameter("format", "LOG_ENTRIES").withQueryStringParameter("limit", "3")).size(), is(3));
    }

    @Test
    public void rejectsFiltersWhereTheyDoNotApply() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> httpState.retrieve(request("/mockserver/retrieve").withMethod("PUT").withQueryStringParameter("type", "REQUESTS").withQueryStringParameter("unmatched", "true"))).getMessage(),
            containsString("only apply to active_expectations"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> httpState.retrieve(request("/mockserver/retrieve").withMethod("PUT").withQueryStringParameter("type", "LOGS").withQueryStringParameter("limit", "1"))).getMessage(),
            containsString("limit and offset only apply"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> httpState.retrieve(active().withQueryStringParameter("limit", "-1"))).getMessage(),
            containsString("\"limit\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> httpState.clear(clear().withQueryStringParameter("limit", "1"))).getMessage(),
            containsString("not supported for clear"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> httpState.clear(clear().withQueryStringParameter("type", "log").withQueryStringParameter("unmatched", "true"))).getMessage(),
            containsString("not type=log"));
    }

    @Test
    public void aBlankOrNonSelectingFilterOnClearIsRejectedAndClearsNothing() throws Exception {
        httpState.log(new LogEntry().setType(FORWARDED_REQUEST).setHttpRequest(request("/two")).setHttpResponse(response("logged"))
            .setExpectation(new Expectation(request("/two"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response("logged"))));
        List<HttpRequest> blankClears = Arrays.asList(
            clear().withQueryStringParameter("metadataKey", ""),
            clear().withQueryStringParameter("metadataKey", " ").withQueryStringParameter("type", "expectations"),
            clear().withQueryStringParameter("metadataJsonPath", ""),
            clear().withQueryStringParameter("unmatched", ""),
            clear().withQueryStringParameter("unmatched", "false"),
            clear().withQueryStringParameter("metadataValue", "team-a"),
            clear().withQueryStringParameter("metadataKey", "").withBody("{\"path\":\"/two\"}")
        );

        for (HttpRequest blankClear : blankClears) {
            assertThrows(blankClear.getQueryStringParameterList().toString(), IllegalArgumentException.class, () -> httpState.clear(blankClear));
        }

        assertThat(ids(active()), contains("one", "two", "three", "four"));
        assertThat(retrieve(request("/mockserver/retrieve").withMethod("PUT").withQueryStringParameter("type", "RECORDED_EXPECTATIONS")).size(), is(1));
    }

    @Test
    public void mixedCaseFilterNamesSelectOnClearAndNeverClearEverything() throws Exception {
        httpState.getRequestMatchers().firstMatchingExpectation(request("/one"));

        httpState.clear(clear().withQueryStringParameter("type", "expectations").withQueryStringParameter("MetadataKey", "owner").withQueryStringParameter("METADATAVALUE", "team-b"));
        assertThat(ids(active()), contains("one", "three", "four"));

        httpState.clear(clear().withQueryStringParameter("metadatajsonpath", "$.tags[?(@ == 'smoke')]").withBody("{\"path\":\"/one\"}"));
        assertThat(ids(active()), contains("three", "four"));

        httpState.clear(clear().withQueryStringParameter("Unmatched", "TRUE").withQueryStringParameter("metadataKey", "owner").withBody("{\"path\":\"/three\"}"));
        assertThat(ids(active()), contains("four"));

        for (HttpRequest refused : Arrays.asList(
            clear().withQueryStringParameter("MetadataKey", ""),
            clear().withQueryStringParameter("UNMATCHED", "false"),
            clear().withQueryStringParameter("metadata_key", "owner"),
            clear().withQueryStringParameter("Metadata-Key", "owner").withBody("{\"path\":\"/four\"}"),
            clear().withQueryStringParameter("unmatchedOnly", "true"),
            clear().withQueryStringParameter("metadataKey", "owner").withQueryStringParameter("METADATAKEY", "other")
        )) {
            assertThrows(refused.getQueryStringParameterList().toString(), IllegalArgumentException.class, () -> httpState.clear(refused));
        }
        assertThat(ids(active()), contains("four"));
    }

    @Test
    public void mixedCaseFilterNamesSelectOnRetrieve() throws Exception {
        httpState.getRequestMatchers().firstMatchingExpectation(request("/one"));

        assertThat(ids(active().withQueryStringParameter("METADATAKEY", "owner").withQueryStringParameter("metadataVALUE", "team-a")), contains("one", "three"));
        assertThat(ids(active().withQueryStringParameter("MetadataJsonPath", "$[?(@.owner == 'team-b')]")), contains("two"));
        assertThat(ids(active().withQueryStringParameter("UNMATCHED", "true")), contains("two", "three", "four"));
        assertThat(ids(active().withQueryStringParameter("Offset", "1").withQueryStringParameter("LIMIT", "2")), contains("two", "three"));
        assertThat(ids(active().withQueryStringParameter("metadataKey", "owner").withBody("{\"path\":\"/two\"}")), contains("two"));
        assertThrows(IllegalArgumentException.class, () -> httpState.retrieve(active().withQueryStringParameter("metadata_key", "owner")));
    }

    private static HttpRequest wireClear(String rawQuery) {
        org.mockserver.codec.ExpandedParameterDecoder decoder = new org.mockserver.codec.ExpandedParameterDecoder(configuration(), new MockServerLogger());
        HttpRequest received = clear();
        decoder.applyQueryParameters(received, "/mockserver/clear?" + rawQuery, true);
        return received;
    }

    @Test
    public void anUndecodableOrIncompleteFilteredClearIsRefusedAndClearsNothing() throws Exception {
        httpState.log(new LogEntry().setType(FORWARDED_REQUEST).setHttpRequest(request("/two")).setHttpResponse(response("logged"))
            .setExpectation(new Expectation(request("/two"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response("logged"))));
        List<HttpRequest> refused = Arrays.asList(
            wireClear("type=expectations&metadataKey=owner&metadataValue=100%"),
            wireClear("type=expectations&metadataKey=owner&metadataValue=%ZZ"),
            wireClear("metadataKey=owner&metadataValue=%Z"),
            wireClear("metadataKey=owner&metadataValue=team-a&note=50%off"),
            wireClear("metadataKey=owner&metadataValue=%C3%28"),
            wireClear("type=expectations&namespace="),
            wireClear("type=expectations&unmatched=false"),
            wireClear("type=expectations&metadata_key=owner"),
            // a filter with an invalid JSON request-matcher body
            wireClear("type=expectations&metadataKey=owner").withBody("{\"path\": \"/one\""),
            wireClear("type=expectations&metadataKey=owner").withBody("not json")
        );

        for (HttpRequest clear : refused) {
            assertThrows(String.valueOf(clear.getQueryStringParameters() != null ? clear.getQueryStringParameters().getRawParameterString() : clear.getUndecodableQueryString()) + " " + clear.getBodyAsString(), IllegalArgumentException.class, () -> httpState.clear(clear));
        }

        assertThat(ids(active()), contains("one", "two", "three", "four"));
        assertThat(retrieve(request("/mockserver/retrieve").withMethod("PUT").withQueryStringParameter("type", "RECORDED_EXPECTATIONS")).size(), is(1));
    }

    @Test
    public void wireFilteredClearsSelectExactlyAndAnEmptyBodyMeansEveryExpectationTheFilterSelects() throws Exception {
        httpState.clear(wireClear("type=expectations&metadataKey=owner&metadataValue=team%2Db").withBody(""));
        assertThat(ids(active()), contains("one", "three", "four"));
        httpState.clear(wireClear("type=expectations&METADATAKEY=owner&metadataValue=team-a+x"));
        assertThat(ids(active()), contains("one", "three", "four"));
        // with no filter name at all, the raw query takes the unfiltered path exactly as before
        httpState.clear(wireClear("type=expectations"));
        assertThat(ids(active()), hasSize(0));
    }

    private HttpRequest servletClear(String queryString) {
        org.springframework.mock.web.MockHttpServletRequest servletRequest = new org.springframework.mock.web.MockHttpServletRequest("PUT", "/mockserver/clear");
        servletRequest.setQueryString(queryString);
        return new org.mockserver.mappers.HttpServletRequestToMockServerHttpRequestDecoder(configuration(), new MockServerLogger()).mapHttpServletRequestToMockServerRequest(servletRequest);
    }

    @Test
    public void aLiteralQuestionMarkOnTheWarPathSelectsExactlyAndNeverClearsEverything() throws Exception {
        httpState.add(new Expectation(request().withPath("/question")).withId("question").withMetadata("owner", "what?x").thenRespond(response()));
        httpState.log(new LogEntry().setType(FORWARDED_REQUEST).setHttpRequest(request("/two")).setHttpResponse(response("logged"))
            .setExpectation(new Expectation(request("/two"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response("logged"))));

        httpState.clear(servletClear("type=expectations&metadataKey=owner&metadataValue=what?x"));
        assertThat(ids(active()), contains("one", "two", "three", "four"));

        httpState.clear(servletClear("metadataKey=owner&metadataValue=team-a?"));
        assertThat(ids(active()), contains("one", "two", "three", "four"));
        assertThat(retrieve(request("/mockserver/retrieve").withMethod("PUT").withQueryStringParameter("type", "RECORDED_EXPECTATIONS")).size(), is(1));

        assertThrows(IllegalArgumentException.class, () -> httpState.clear(servletClear("type=expectations&metadataKey=owner&metadataValue=50%off?")));
        assertThat(ids(active()), contains("one", "two", "three", "four"));
    }

    @Test
    public void aHashEndsTheQueryOnTheWireAsTheServerDecoderDoes() throws Exception {
        // a raw "#" (which no client sends: they cut the URL there) ends the query for the gate too, so
        // "team-a#x" selects team-a, exactly as the server's own decoder reads it
        httpState.clear(wireClear("type=expectations&metadataKey=owner&metadataValue=team-a#x"));
        assertThat(ids(active()), contains("two", "four"));
        assertThat(ExpectationFilter.fromControlPlaneRequest(wireClear("metadataKey=owner&metadataValue=team%23x")).getMetadataValue(), is("team#x"));
    }

    @Test
    public void aBlankFilterOnRetrieveIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> httpState.retrieve(active().withQueryStringParameter("metadataKey", "")));
        assertThrows(IllegalArgumentException.class, () -> httpState.retrieve(active().withQueryStringParameter("metadataJsonPath", " ")));
    }

    @Test
    public void filterlessCallsBehaveExactlyAsBefore() throws Exception {
        assertThat(ids(active()), contains("one", "two", "three", "four"));
        httpState.clear(clear().withQueryStringParameter("type", "expectations").withQueryStringParameter("namespace", "tenant"));
        assertThat(ids(active()), contains("one", "two", "four"));
        httpState.clear(clear().withQueryStringParameter("type", "expectations"));
        assertThat(ids(active()), hasSize(0));
    }

    private static HttpRequest active() {
        return request("/mockserver/retrieve")
            .withMethod("PUT")
            .withQueryStringParameter("type", "ACTIVE_EXPECTATIONS");
    }

    private static HttpRequest clear() {
        return request("/mockserver/clear").withMethod("PUT");
    }

    private JsonNode retrieve(HttpRequest retrieveRequest) throws Exception {
        HttpResponse response = httpState.retrieve(retrieveRequest);
        assertThat(response.getStatusCode(), is(200));
        return objectMapper.readTree(response.getBodyAsString());
    }

    private List<String> ids(HttpRequest retrieveRequest) throws Exception {
        List<String> ids = new ArrayList<>();
        for (JsonNode expectation : retrieve(retrieveRequest)) {
            ids.add(expectation.path("id").asText());
        }
        return ids;
    }

    private List<String> paths(HttpRequest retrieveRequest) throws Exception {
        List<String> paths = new ArrayList<>();
        for (JsonNode expectation : retrieve(retrieveRequest)) {
            paths.add(expectation.path("httpRequest").path("path").asText());
        }
        return paths;
    }
}
