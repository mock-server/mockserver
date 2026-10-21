package org.mockserver.mock;

import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.RetrieveType;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.RequestDefinitionSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.FORWARDED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Behavioural tests for the "record&rarr;mock" surface added to {@link HttpState}:
 * <ul>
 *   <li>per-request {@code ?consolidate=true}/{@code ?parameterize=true} query
 *       parameters on the RECORDED_EXPECTATIONS retrieve path;</li>
 *   <li>the {@code PUT /mockserver/recordings/promote} endpoint that consolidates
 *       recorded traffic and ACTIVATES it as unlimited-times mocks;</li>
 *   <li>HAR import via {@code PUT /mockserver/import?format=har}.</li>
 * </ul>
 * Uses a per-instance {@link Configuration} so it never mutates global
 * {@code ConfigurationProperties} state (no two-phase Surefire registration needed).
 */
public class RecordToMockHttpStateTest {

    private final List<HttpState> httpStates = new ArrayList<>();

    private final RequestDefinitionSerializer requestDefinitionSerializer = new RequestDefinitionSerializer(new MockServerLogger());
    private final ExpectationSerializer expectationSerializer = new ExpectationSerializer(new MockServerLogger());

    private static class FakeResponseWriter extends ResponseWriter {
        public HttpResponse response;

        protected FakeResponseWriter() {
            super(configuration(), new MockServerLogger());
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
            this.response = response;
        }
    }

    @After
    public void stopHttpStates() {
        httpStates.forEach(HttpState::stop);
    }

    private HttpState newHttpState() {
        Configuration configuration = configuration();
        Scheduler scheduler = mock(Scheduler.class);
        HttpState httpState = new HttpState(configuration, new MockServerLogger(configuration, MockServerLogger.class), scheduler);
        httpStates.add(httpState);
        return httpState;
    }

    private void recordForwardedCall(HttpState httpState, String method, String path, HttpResponse httpResponse) {
        httpState.log(
            new LogEntry()
                .setType(FORWARDED_REQUEST)
                .setHttpRequest(request(path).withMethod(method))
                .setHttpResponse(httpResponse)
                .setExpectation(new Expectation(request(path).withMethod(method), Times.once(), TimeToLive.unlimited(), 0).thenRespond(httpResponse))
        );
    }

    private HttpResponse retrieveRecorded(HttpState httpState, HttpRequest retrieveRequest) {
        FakeResponseWriter responseWriter = new FakeResponseWriter();
        boolean handle = httpState.handle(retrieveRequest, responseWriter, false);
        assertThat(handle, is(true));
        return responseWriter.response;
    }

    private HttpRequest recordedRetrieveRequest() {
        return request("/mockserver/retrieve")
            .withMethod("PUT")
            .withQueryStringParameter("type", RetrieveType.RECORDED_EXPECTATIONS.name())
            .withBody(requestDefinitionSerializer.serialize(request()));
    }

    // ----- retrieve with ?consolidate / ?parameterize -----------------------

    @Test
    public void shouldReturnRecordedExpectationsVerbatimByDefault() {
        HttpState httpState = newHttpState();
        for (int i = 0; i < 50; i++) {
            recordForwardedCall(httpState, "GET", "/users/123", response("ok"));
        }

        HttpResponse response = retrieveRecorded(httpState, recordedRetrieveRequest());

        // default (no options) = current verbatim behaviour: 50 brittle expectations
        assertThat(expectationSerializer.deserializeArray(response.getBodyAsString(), true).length, is(50));
    }

    @Test
    public void shouldConsolidateFiftyIdenticalHitsToOneUnlimitedExpectationOnRetrieve() {
        HttpState httpState = newHttpState();
        for (int i = 0; i < 50; i++) {
            recordForwardedCall(httpState, "GET", "/users/123", response("ok"));
        }

        HttpRequest retrieveRequest = recordedRetrieveRequest().withQueryStringParameter("consolidate", "true");
        HttpResponse response = retrieveRecorded(httpState, retrieveRequest);

        Expectation[] result = expectationSerializer.deserializeArray(response.getBodyAsString(), true);
        assertThat(result.length, is(1));
        assertThat(result[0].getTimes().isUnlimited(), is(true));
    }

    @Test
    public void shouldParameterizeVaryingPathSegmentOnRetrieve() {
        HttpState httpState = newHttpState();
        recordForwardedCall(httpState, "GET", "/users/123", response("ok"));
        recordForwardedCall(httpState, "GET", "/users/456", response("ok"));
        recordForwardedCall(httpState, "GET", "/users/789", response("ok"));

        HttpRequest retrieveRequest = recordedRetrieveRequest().withQueryStringParameter("consolidate", "true");
        HttpResponse response = retrieveRecorded(httpState, retrieveRequest);

        String body = response.getBodyAsString();
        assertThat(body, containsString("/users/{id}"));
        assertThat(body, not(containsString("/users/123")));
        assertThat(expectationSerializer.deserializeArray(body, true).length, is(1));
    }

    // ----- PUT /mockserver/recordings/promote -------------------------------

    @Test
    public void shouldPromoteConsolidatedRecordedExpectationsAndActivateThem() {
        HttpState httpState = newHttpState();
        // 50 identical hits to the same id, plus two varying ids of a second endpoint
        for (int i = 0; i < 50; i++) {
            recordForwardedCall(httpState, "GET", "/users/123", response("user-body"));
        }
        recordForwardedCall(httpState, "GET", "/orders/1", response("order-body"));
        recordForwardedCall(httpState, "GET", "/orders/2", response("order-body"));

        // when - promote (empty body = promote all recorded traffic)
        FakeResponseWriter responseWriter = new FakeResponseWriter();
        HttpRequest promote = request("/mockserver/recordings/promote").withMethod("PUT");
        boolean handled = httpState.handle(promote, responseWriter, false);

        // then - 201 with two consolidated expectations returned
        assertThat(handled, is(true));
        assertThat(responseWriter.response.getStatusCode(), is(201));
        Expectation[] created = expectationSerializer.deserializeArray(responseWriter.response.getBodyAsString(), true);
        assertThat(created.length, is(2));

        // and - they are ACTIVE: a matching request now resolves to a promoted expectation
        assertThat(httpState.firstMatchingExpectation(request("/users/123").withMethod("GET")), is(org.hamcrest.CoreMatchers.notNullValue()));
        assertThat(httpState.firstMatchingExpectation(request("/orders/99").withMethod("GET")), is(org.hamcrest.CoreMatchers.notNullValue()));
        // and - promoted mocks are unlimited-times (do not expire after one match)
        for (Expectation expectation : httpState.getRequestMatchers().retrieveActiveExpectations(null)) {
            assertThat(expectation.getTimes().isUnlimited(), is(true));
        }
    }

    @Test
    public void shouldPromoteOnlyRecordedTrafficMatchingBodyFilter() {
        HttpState httpState = newHttpState();
        recordForwardedCall(httpState, "GET", "/users/1", response("user"));
        recordForwardedCall(httpState, "GET", "/orders/1", response("order"));

        FakeResponseWriter responseWriter = new FakeResponseWriter();
        HttpRequest promote = request("/mockserver/recordings/promote")
            .withMethod("PUT")
            .withBody(requestDefinitionSerializer.serialize(request("/users/.*")));
        httpState.handle(promote, responseWriter, false);

        assertThat(responseWriter.response.getStatusCode(), is(201));
        Expectation[] created = expectationSerializer.deserializeArray(responseWriter.response.getBodyAsString(), true);
        assertThat(created.length, is(1));
        assertThat(created[0].getHttpRequest().toString(), containsString("/users/"));
        // the /orders/1 recording was not promoted
        assertThat(httpState.firstMatchingExpectation(request("/orders/1").withMethod("GET")), is(org.hamcrest.CoreMatchers.nullValue()));
    }

    // ----- HAR import -------------------------------------------------------

    @Test
    public void shouldImportHarAsExpectations() {
        HttpState httpState = newHttpState();
        String har = "{\"log\":{\"entries\":["
            + "{\"request\":{\"method\":\"GET\",\"url\":\"http://example.com/widgets/1\"},"
            + "\"response\":{\"status\":200,\"content\":{\"text\":\"widget\"}}}"
            + "]}}";

        FakeResponseWriter responseWriter = new FakeResponseWriter();
        HttpRequest importRequest = request("/mockserver/import")
            .withMethod("PUT")
            .withQueryStringParameter("format", "har")
            .withBody(har);
        httpState.handle(importRequest, responseWriter, false);

        assertThat(responseWriter.response.getStatusCode(), is(201));
        Expectation[] created = expectationSerializer.deserializeArray(responseWriter.response.getBodyAsString(), true);
        assertThat(created.length, is(1));
        // imported expectation is active
        assertThat(httpState.firstMatchingExpectation(request("/widgets/1").withMethod("GET")), is(org.hamcrest.CoreMatchers.notNullValue()));
    }

    @Test
    public void shouldConsolidateHarImportWhenRequested() {
        HttpState httpState = newHttpState();
        // a HAR that captured the same endpoint under three ids with identical responses
        String har = "{\"log\":{\"entries\":["
            + "{\"request\":{\"method\":\"GET\",\"url\":\"http://example.com/widgets/1\"},\"response\":{\"status\":200,\"content\":{\"text\":\"w\"}}},"
            + "{\"request\":{\"method\":\"GET\",\"url\":\"http://example.com/widgets/2\"},\"response\":{\"status\":200,\"content\":{\"text\":\"w\"}}},"
            + "{\"request\":{\"method\":\"GET\",\"url\":\"http://example.com/widgets/3\"},\"response\":{\"status\":200,\"content\":{\"text\":\"w\"}}}"
            + "]}}";

        FakeResponseWriter responseWriter = new FakeResponseWriter();
        HttpRequest importRequest = request("/mockserver/import")
            .withMethod("PUT")
            .withQueryStringParameter("format", "har")
            .withQueryStringParameter("consolidate", "true")
            .withBody(har);
        httpState.handle(importRequest, responseWriter, false);

        assertThat(responseWriter.response.getStatusCode(), is(201));
        List<Expectation> created = java.util.Arrays.asList(
            expectationSerializer.deserializeArray(responseWriter.response.getBodyAsString(), true));
        assertThat(created.size(), is(1));
        assertThat(created.get(0).getHttpRequest().toString(), containsString("/widgets/{id}"));
    }

    // ----- migration importers (WireMock / Mountebank / Mockoon) -------------

    private static com.fasterxml.jackson.databind.JsonNode parseImportBody(String body) throws Exception {
        return org.mockserver.serialization.ObjectMapperFactory.createObjectMapper().readTree(body);
    }

    @Test
    public void shouldImportWireMockStubWithStructuredWarnings() throws Exception {
        HttpState httpState = newHttpState();
        String wiremock = "{\"mappings\":[{"
            + "\"request\":{\"method\":\"GET\",\"urlPath\":\"/api/thing\","
            + "\"multipartPatterns\":[{\"matchingType\":\"ANY\"}]},"
            + "\"response\":{\"status\":200,\"body\":\"ok\"}}]}";

        FakeResponseWriter responseWriter = new FakeResponseWriter();
        httpState.handle(request("/mockserver/import").withMethod("PUT")
            .withQueryStringParameter("format", "wiremock").withBody(wiremock), responseWriter, false);

        assertThat(responseWriter.response.getStatusCode(), is(201));
        com.fasterxml.jackson.databind.JsonNode body = parseImportBody(responseWriter.response.getBodyAsString());
        assertThat(body.path("expectations").isArray(), is(true));
        assertThat(body.path("expectations").size(), is(1));
        // the unmapped multipartPatterns must surface as a structured warning, not a silent drop
        assertThat(body.path("warnings").isArray(), is(true));
        assertThat(body.path("warnings").size(), is(not(0)));
        assertThat(body.path("warnings").toString(), containsString("multipartPatterns"));
        // imported expectation is active
        assertThat(httpState.firstMatchingExpectation(request("/api/thing").withMethod("GET")),
            is(org.hamcrest.CoreMatchers.notNullValue()));
    }

    @Test
    public void shouldResolveWireMockBodyFileNameFromFilesDirectoryParameter() throws Exception {
        java.nio.file.Path files = java.nio.file.Files.createTempDirectory("wiremock-files");
        try {
            java.nio.file.Files.writeString(files.resolve("thing.json"), "{\"from\":\"file\"}");
            HttpState httpState = newHttpState();
            String wiremock = "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/api/file\"},"
                + "\"response\":{\"status\":200,\"bodyFileName\":\"thing.json\"}}";

            FakeResponseWriter responseWriter = new FakeResponseWriter();
            httpState.handle(request("/mockserver/import").withMethod("PUT")
                .withQueryStringParameter("format", "wiremock")
                .withQueryStringParameter("filesDirectory", files.toString())
                .withBody(wiremock), responseWriter, false);

            assertThat(responseWriter.response.getStatusCode(), is(201));
            assertThat(parseImportBody(responseWriter.response.getBodyAsString()).path("warnings").size(), is(0));
            Expectation imported = httpState.firstMatchingExpectation(request("/api/file").withMethod("GET"));
            assertThat(((org.mockserver.model.FileBody) imported.getHttpResponse().getBody()).getFilePath(),
                is(files.resolve("thing.json").toRealPath().toString()));
        } finally {
            java.nio.file.Files.deleteIfExists(files.resolve("thing.json"));
            java.nio.file.Files.deleteIfExists(files);
        }
    }

    @Test
    public void shouldAutoDetectMountebankImposter() throws Exception {
        HttpState httpState = newHttpState();
        String imposter = "{\"protocol\":\"http\",\"port\":4545,\"stubs\":[{"
            + "\"predicates\":[{\"equals\":{\"method\":\"GET\",\"path\":\"/mb\"}}],"
            + "\"responses\":[{\"is\":{\"statusCode\":200,\"body\":\"mb\"}}]}]}";

        FakeResponseWriter responseWriter = new FakeResponseWriter();
        // no ?format — must auto-detect from protocol+stubs
        httpState.handle(request("/mockserver/import").withMethod("PUT").withBody(imposter), responseWriter, false);

        assertThat(responseWriter.response.getStatusCode(), is(201));
        com.fasterxml.jackson.databind.JsonNode body = parseImportBody(responseWriter.response.getBodyAsString());
        assertThat(body.path("expectations").size(), is(1));
        assertThat(httpState.firstMatchingExpectation(request("/mb").withMethod("GET")),
            is(org.hamcrest.CoreMatchers.notNullValue()));
    }

    @Test
    public void shouldAutoDetectMockoonEnvironment() throws Exception {
        HttpState httpState = newHttpState();
        String environment = "{\"routes\":[{\"type\":\"http\",\"method\":\"get\",\"endpoint\":\"health\",\"uuid\":\"r1\","
            + "\"responses\":[{\"statusCode\":200,\"body\":\"up\"}]}]}";

        FakeResponseWriter responseWriter = new FakeResponseWriter();
        httpState.handle(request("/mockserver/import").withMethod("PUT").withBody(environment), responseWriter, false);

        assertThat(responseWriter.response.getStatusCode(), is(201));
        com.fasterxml.jackson.databind.JsonNode body = parseImportBody(responseWriter.response.getBodyAsString());
        assertThat(body.path("expectations").size(), is(1));
        assertThat(httpState.firstMatchingExpectation(request("/health").withMethod("GET")),
            is(org.hamcrest.CoreMatchers.notNullValue()));
    }
}
