package org.mockserver.imports;

import io.netty.handler.codec.http.DefaultHttpObject;
import io.netty.util.ReferenceCountUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.fixture.FixtureRedactor;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.MockServerHttpResponseToFullHttpResponse;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.SortableExpectationId;
import org.mockserver.mock.action.http.FileBodyMaterialiser;
import org.mockserver.model.AfterAction;
import org.mockserver.model.BodyWithContentType;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.Delay;
import org.mockserver.model.DelayDistribution;
import org.mockserver.model.FileBody;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.model.NottableDateTimeString;
import org.mockserver.model.XPathBody;
import org.mockserver.model.XmlBody;
import org.mockserver.openapi.OpenApiRuntimeExpressionResolver;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.templates.engine.mustache.MustacheTemplateEngine;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.NottableDateTimeString.after;
import static org.mockserver.model.NottableDateTimeString.before;
import static org.mockserver.model.NottableDateTimeString.equalToDateTime;
import static org.mockserver.model.NottableString.string;
import static org.hamcrest.Matchers.instanceOf;

public class WireMockImporterTest {

    private final WireMockImporter importer = new WireMockImporter();
    private final Configuration configuration = Configuration.configuration();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    // A representative WireMock mappings export exercising the mappable constructs plus a
    // couple that must produce warnings (matchesXPath body, response transformers).
    private static final String WIREMOCK_MAPPINGS = """
        {
          "mappings": [
            {
              "id": "get-user",
              "priority": 1,
              "scenarioName": "user-flow",
              "requiredScenarioState": "Started",
              "newScenarioState": "user-fetched",
              "request": {
                "method": "GET",
                "urlPath": "/api/users/123",
                "queryParameters": {
                  "expand": { "equalTo": "profile" },
                  "fields": { "matches": "^[a-z,]+$" }
                },
                "headers": {
                  "Accept": { "equalTo": "application/json" },
                  "X-Trace": { "contains": "abc" }
                }
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "application/json" },
                "jsonBody": { "id": 123, "name": "Alice" },
                "fixedDelayMilliseconds": 250
              }
            },
            {
              "request": {
                "method": "POST",
                "urlPattern": "/api/orders/[0-9]+\\\\?draft=true",
                "bodyPatterns": [
                  { "equalToJson": "{\\"item\\":\\"book\\"}", "ignoreExtraElements": true }
                ]
              },
              "response": {
                "status": 201,
                "body": "created"
              }
            },
            {
              "request": { "method": "GET", "urlPath": "/api/matchjsonpath" },
              "request2": {},
              "response": { "status": 200, "body": "ok" }
            },
            {
              "request": { "method": "GET", "urlPath": "/api/flaky" },
              "response": { "fault": "CONNECTION_RESET_BY_PEER" }
            },
            {
              "request": { "method": "GET", "urlPath": "/api/legacy" },
              "response": { "proxyBaseUrl": "https://origin.example.com:8443" }
            },
            {
              "request": {
                "method": "GET",
                "urlPath": "/api/xml",
                "bodyPatterns": [ { "matchesXPath": "//book/title" } ]
              },
              "response": {
                "status": 200,
                "body": "xml-ok",
                "transformers": [ "response-template" ]
              }
            }
          ]
        }
        """;

    @Test
    public void importsExactPathMethodHeadersQueryAndJsonBody() {
        ImportResult result = importer.importExpectations(WIREMOCK_MAPPINGS);

        Expectation getUser = result.getExpectations().get(0);
        HttpRequest request = (HttpRequest) getUser.getHttpRequest();
        assertThat(request.getMethod().getValue(), is("GET"));
        assertThat(request.getPath().getValue(), is("/api/users/123"));
        // query + header matchers serialised into the request JSON
        String requestJson = request.toString();
        assertThat(requestJson, containsString("expand"));
        assertThat(requestJson, containsString("profile"));
        assertThat(requestJson, containsString("^[a-z,]+$"));
        assertThat(requestJson, containsString("Accept"));

        HttpResponse response = getUser.getHttpResponse();
        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBodyAsString(), containsString("\"name\""));
        assertThat(response.getBodyAsString(), containsString("Alice"));
        assertThat(response.getDelay().getValue(), is(250L));
    }

    @Test
    public void mapsScenarioAndInvertedPriority() {
        Expectation getUser = importer.importExpectations(WIREMOCK_MAPPINGS).getExpectations().get(0);
        assertThat(getUser.getScenarioName(), is("user-flow"));
        assertThat(getUser.getScenarioState(), is("Started"));
        assertThat(getUser.getNewScenarioState(), is("user-fetched"));
        // WireMock priority 1 (highest, WireMock default 5) maps to MockServer 5-1=4, which beats
        // an unspecified-priority MockServer catch-all at the default of 0.
        assertThat(getUser.getPriority(), is(4));
    }

    @Test
    public void priorityOneStubOutranksUnspecifiedPriorityStub() {
        // A WireMock priority:1 stub must win over an unspecified-priority (catch-all) stub. In
        // MockServer higher priority ints win (EXPECTATION_SORTABLE_PRIORITY_COMPARATOR), and an
        // unspecified priority defaults to 0 — so the mapped priority:1 stub must be > 0.
        String stubs = """
            [ { "request": { "method": "GET", "urlPath": "/x" },
                "priority": 1,
                "response": { "status": 200, "body": "explicit" } },
              { "request": { "method": "GET", "urlPathPattern": "/.*" },
                "response": { "status": 200, "body": "catch-all" } } ]
            """;
        List<Expectation> expectations = importer.importExpectations(stubs).getExpectations();
        Expectation explicit = expectations.get(0);
        Expectation catchAll = expectations.get(1);
        assertThat(explicit.getPriority(), is(4));
        assertThat(catchAll.getPriority(), is(0));
        assertTrue("priority:1 stub must outrank an unspecified-priority catch-all",
            explicit.getPriority() > catchAll.getPriority());

        // Confirm against the real matcher ordering comparator: sorting the two SortableExpectationIds
        // by EXPECTATION_SORTABLE_PRIORITY_COMPARATOR must place the explicit stub first.
        SortableExpectationId explicitId = new SortableExpectationId("explicit", explicit.getPriority(), 1L);
        SortableExpectationId catchAllId = new SortableExpectationId("catch-all", catchAll.getPriority(), 2L);
        List<SortableExpectationId> ids = new ArrayList<>(List.of(catchAllId, explicitId));
        ids.sort(SortableExpectationId.EXPECTATION_SORTABLE_PRIORITY_COMPARATOR);
        assertThat(ids.get(0), is(explicitId));
    }

    @Test
    public void mapsUrlPatternToPathRegexDroppingQueryPortionWithWarning() {
        ImportResult result = importer.importExpectations(WIREMOCK_MAPPINGS);
        HttpRequest request = (HttpRequest) result.getExpectations().get(1).getHttpRequest();
        assertThat(request.getPath().getValue(), is("/api/orders/[0-9]+"));
        assertThat(request.getBody().toString(), containsString("item"));
        assertTrue("expected a urlPattern query-drop warning",
            result.getWarnings().stream().anyMatch(w -> w.getConstruct().contains("urlPattern")));
    }

    @Test
    public void mapsFaultToConnectionDrop() {
        Expectation flaky = importer.importExpectations(WIREMOCK_MAPPINGS).getExpectations().get(3);
        HttpError error = flaky.getHttpError();
        assertThat(error, is(not(nullValue())));
        assertThat(error.getDropConnection(), is(true));
    }

    @Test
    public void mapsProxyBaseUrlToForward() {
        Expectation legacy = importer.importExpectations(WIREMOCK_MAPPINGS).getExpectations().get(4);
        HttpForward forward = legacy.getHttpForward();
        assertThat(forward, is(not(nullValue())));
        assertThat(forward.getHost(), is("origin.example.com"));
        assertThat(forward.getPort(), is(8443));
        assertThat(forward.getScheme(), is(HttpForward.Scheme.HTTPS));
    }

    @Test
    public void mapsMatchesXPathAndTreatsATemplateWithoutReferencesAsAPlainResponse() {
        ImportResult result = importer.importExpectations(WIREMOCK_MAPPINGS);
        Expectation xml = result.getExpectations().get(5);
        assertThat(((XPathBody) ((HttpRequest) xml.getHttpRequest()).getBody()).getValue(), is("//book/title"));
        assertThat(xml.getHttpResponse().getBodyAsString(), is("xml-ok"));
        assertThat(result.getWarnings().stream().anyMatch(w -> w.getConstruct().contains("matchesXPath")
            || w.getConstruct().contains("transformers")), is(false));
    }

    @Test
    public void supportsSingleStubForm() {
        String single = """
            { "request": { "method": "DELETE", "urlPath": "/thing" },
              "response": { "status": 204 } }
            """;
        ImportResult result = importer.importExpectations(single);
        assertThat(result.getExpectations(), hasSize(1));
        HttpRequest request = (HttpRequest) result.getExpectations().get(0).getHttpRequest();
        assertThat(request.getMethod().getValue(), is("DELETE"));
        assertThat(result.getExpectations().get(0).getHttpResponse().getStatusCode(), is(204));
    }

    @Test
    public void supportsBareArrayForm() {
        String array = """
            [ { "request": { "urlPath": "/a" }, "response": { "status": 200 } },
              { "request": { "urlPath": "/b" }, "response": { "status": 201 } } ]
            """;
        assertThat(importer.importExpectations(array).getExpectations(), hasSize(2));
    }

    @Test
    public void decodesBase64Body() {
        // base64 of "hello world"
        String stub = """
            { "request": { "urlPath": "/b64" },
              "response": { "status": 200, "base64Body": "aGVsbG8gd29ybGQ=" } }
            """;
        HttpResponse response = importer.importExpectations(stub).getExpectations().get(0).getHttpResponse();
        assertThat(response.getBodyAsString(), is("hello world"));
    }

    @Test
    public void dropsAnyMethodMatcher() {
        String stub = """
            { "request": { "method": "ANY", "urlPath": "/any" }, "response": { "status": 200 } }
            """;
        HttpRequest request = (HttpRequest) importer.importExpectations(stub).getExpectations().get(0).getHttpRequest();
        assertThat(request.getMethod().getValue(), is(""));
    }

    @Test
    public void cleanImportHasNoWarnings() {
        String stub = """
            { "request": { "method": "GET", "urlPath": "/ok" }, "response": { "status": 200, "body": "ok" } }
            """;
        assertThat(importer.importExpectations(stub).getWarnings(), is(empty()));
    }

    @Test
    public void rejectsBlankAndInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> importer.importExpectations(""));
        assertThrows(IllegalArgumentException.class, () -> importer.importExpectations("{ \"nonsense\": true }"));
        assertThrows(IllegalArgumentException.class, () -> importer.importExpectations("not json"));
    }

    @Test
    public void jsonBodyMatchersKeepTheirMatchingThroughRedaction() {
        String stubs = """
            [ { "request": { "method": "POST", "urlPath": "/orders",
                             "bodyPatterns": [ { "equalToJson": { "item": "book" }, "ignoreExtraElements": true } ] },
                "response": { "status": 201 } },
              { "request": { "method": "POST", "urlPath": "/paths",
                             "bodyPatterns": [ { "matchesJsonPath": "$.items[?(@.qty > 1)]" } ] },
                "response": { "status": 201 } } ]
            """;
        ImportResult result = importer.importExpectations(stubs);
        assertThat(matches(result, 0, request().withMethod("POST").withPath("/orders").withBody("{ \"qty\": 2,  \"item\": \"book\" }")), is(true));
        assertThat(matches(result, 0, request().withMethod("POST").withPath("/orders").withBody("{\"item\":\"pen\"}")), is(false));
        assertThat(matches(result, 1, request().withMethod("POST").withPath("/paths").withBody("{\"items\":[{\"qty\":3}]}")), is(true));
        assertThat(matches(result, 1, request().withMethod("POST").withPath("/paths").withBody("{\"items\":[{\"qty\":1}]}")), is(false));
    }

    // ---- delays ----------------------------------------------------------------------------------

    @Test
    public void mapsLognormalDelayDistributionToTheSameMedianAndP99() {
        String stub = """
            { "request": { "urlPath": "/slow" },
              "response": { "status": 200, "delayDistribution": { "type": "lognormal", "median": 80, "sigma": 0.4 } } }
            """;
        ImportResult result = importer.importExpectations(stub);
        Delay delay = result.getExpectations().get(0).getHttpResponse().getDelay();
        DelayDistribution distribution = delay.getDistribution();
        assertThat(distribution.getType(), is(DelayDistribution.Type.LOG_NORMAL));
        assertThat(distribution.getMedian(), is(80L));
        // WireMock's 99th percentile is median * e^(sigma * z99) = 80 * e^(0.4 * 2.326) ~= 203
        assertThat(distribution.getP99(), is(203L));
        assertThat(delay.sampleValueMillis(), greaterThan(0L));
        assertThat(result.getWarnings(), is(empty()));
    }

    @Test
    public void foldsFixedDelayIntoUniformDistributionBounds() {
        String stub = """
            { "request": { "urlPath": "/jitter" },
              "response": { "status": 200, "fixedDelayMilliseconds": 100,
                            "delayDistribution": { "type": "uniform", "lower": 15, "upper": 25 } } }
            """;
        Delay delay = importer.importExpectations(stub).getExpectations().get(0).getHttpResponse().getDelay();
        assertThat(delay.getDistribution().getType(), is(DelayDistribution.Type.UNIFORM));
        assertThat(delay.getDistribution().getMin(), is(115L));
        assertThat(delay.getDistribution().getMax(), is(125L));
        for (int i = 0; i < 10; i++) {
            long sampled = delay.sampleValueMillis();
            assertTrue("sampled " + sampled, sampled >= 115 && sampled <= 125);
        }
    }

    @Test
    public void mapsFixedDistributionAndFallsBackToFixedDelayOnAnInvalidDistribution() {
        String stubs = """
            [ { "request": { "urlPath": "/fixed" },
                "response": { "status": 200, "delayDistribution": { "type": "fixed", "milliseconds": 300 } } },
              { "request": { "urlPath": "/invalid" },
                "response": { "status": 200, "fixedDelayMilliseconds": 50,
                              "delayDistribution": { "type": "uniform", "lower": 30, "upper": 10 } } } ]
            """;
        ImportResult result = importer.importExpectations(stubs);
        assertThat(result.getExpectations().get(0).getHttpResponse().getDelay().sampleValueMillis(), is(300L));
        Delay fallback = result.getExpectations().get(1).getHttpResponse().getDelay();
        assertThat(fallback.getDistribution(), is(nullValue()));
        assertThat(fallback.getValue(), is(50L));
        assertThat(result.getWarnings(), hasSize(1));
        assertThat(result.getWarnings().get(0).getConstruct(), is("response.delayDistribution"));
    }

    @Test
    public void mapsChunkedDribbleDelayToChunkedConnectionOptions() {
        String body = "x".repeat(100);
        String stub = """
            { "request": { "urlPath": "/dribble" },
              "response": { "status": 200, "body": "%s",
                            "chunkedDribbleDelay": { "numberOfChunks": 5, "totalDuration": 1000 } } }
            """.formatted(body);
        HttpResponse response = importer.importExpectations(stub).getExpectations().get(0).getHttpResponse();
        ConnectionOptions options = response.getConnectionOptions();
        assertThat(options.getChunkSize(), is(20));
        assertThat(options.getChunkDelay().sampleValueMillis(), is(200L));

        // the response writer splits the body into exactly the requested number of chunks
        List<DefaultHttpObject> written = new MockServerHttpResponseToFullHttpResponse(new MockServerLogger()).mapMockServerResponseToNettyResponse(response);
        try {
            assertThat(written.size() - 1, is(5));
        } finally {
            written.forEach(ReferenceCountUtil::release);
        }
    }

    // ---- faults ----------------------------------------------------------------------------------

    @Test
    public void mapsEachFaultToTheClosestConnectionError() {
        String stubs = """
            [ { "request": { "urlPath": "/empty" }, "response": { "fault": "EMPTY_RESPONSE" } },
              { "request": { "urlPath": "/malformed" }, "response": { "fault": "MALFORMED_RESPONSE_CHUNK" } },
              { "request": { "urlPath": "/random" }, "response": { "fault": "RANDOM_DATA_THEN_CLOSE" } },
              { "request": { "urlPath": "/reset" }, "response": { "fault": "CONNECTION_RESET_BY_PEER" } } ]
            """;
        ImportResult result = importer.importExpectations(stubs);
        String garbage = "lskdu018973t09sylgasjkfg1][]'./.sdlv";

        HttpError empty = result.getExpectations().get(0).getHttpError();
        assertThat(empty.getDropConnection(), is(true));
        assertThat(empty.getResponseBytes(), is(nullValue()));

        HttpError malformed = result.getExpectations().get(1).getHttpError();
        assertThat(new String(malformed.getResponseBytes(), UTF_8), is("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n" + garbage));
        assertThat(malformed.getDropConnection(), is(true));

        HttpError random = result.getExpectations().get(2).getHttpError();
        assertThat(new String(random.getResponseBytes(), UTF_8), is(garbage));
        assertThat(random.getDropConnection(), is(true));

        assertThat(result.getExpectations().get(3).getHttpError().getDropConnection(), is(true));
        // only the reset, which MockServer cannot reproduce per expectation, is reported
        assertThat(result.getWarnings(), hasSize(1));
        assertThat(result.getWarnings().get(0).getConstruct(), is("response.fault.CONNECTION_RESET_BY_PEER"));
    }

    // ---- XML / XPath body matching -----------------------------------------------------------------

    @Test
    public void xpathAndXmlBodyPatternsMatchTheRequestsWireMockWouldMatch() {
        String stubs = """
            [ { "request": { "method": "POST", "urlPath": "/books",
                             "bodyPatterns": [ { "matchesXPath": "//book[@id='1']" } ] },
                "response": { "status": 200 } },
              { "request": { "method": "POST", "urlPath": "/titles",
                             "bodyPatterns": [ { "matchesXPath": { "expression": "//book/title", "equalTo": "Dune" } } ] },
                "response": { "status": 200 } },
              { "request": { "method": "POST", "urlPath": "/authors",
                             "bodyPatterns": [ { "matchesXPath": { "expression": "//book/author", "contains": "Herb" } } ] },
                "response": { "status": 200 } },
              { "request": { "method": "POST", "urlPath": "/ns",
                             "bodyPatterns": [ { "matchesXPath": "//lib:book", "xPathNamespaces": { "lib": "urn:library" } } ] },
                "response": { "status": 200 } },
              { "request": { "method": "POST", "urlPath": "/exact",
                             "bodyPatterns": [ { "equalToXml": "<book><title>Dune</title></book>" } ] },
                "response": { "status": 200 } } ]
            """;
        ImportResult result = importer.importExpectations(stubs);
        assertThat(result.getWarnings(), is(empty()));
        String book = "<library><book id=\"1\"><title>Dune</title><author>Frank Herbert</author></book></library>";
        String otherBook = "<library><book id=\"2\"><title>Emma</title><author>Jane Austen</author></book></library>";

        assertThat(matches(result, 0, xmlRequest("/books", book)), is(true));
        assertThat(matches(result, 0, xmlRequest("/books", otherBook)), is(false));
        assertThat(matches(result, 1, xmlRequest("/titles", book)), is(true));
        assertThat(matches(result, 1, xmlRequest("/titles", otherBook)), is(false));
        assertThat(matches(result, 2, xmlRequest("/authors", book)), is(true));
        assertThat(matches(result, 2, xmlRequest("/authors", otherBook)), is(false));
        assertThat(matches(result, 3, xmlRequest("/ns", "<l:library xmlns:l=\"urn:library\"><l:book/></l:library>")), is(true));
        assertThat(matches(result, 3, xmlRequest("/ns", "<library><book/></library>")), is(false));
        assertThat(matches(result, 4, xmlRequest("/exact", "<book>\n  <title>Dune</title>\n</book>")), is(true));
        assertThat(matches(result, 4, xmlRequest("/exact", "<book><title>Emma</title></book>")), is(false));
    }

    @Test
    public void warnsOnEqualToXmlPlaceholders() {
        String stub = """
            { "request": { "method": "POST", "urlPath": "/x",
                           "bodyPatterns": [ { "equalToXml": "<a>${xmlunit.ignore}</a>", "enablePlaceholders": true } ] },
              "response": { "status": 200 } }
            """;
        ImportResult result = importer.importExpectations(stub);
        assertThat(((HttpRequest) result.getExpectations().get(0).getHttpRequest()).getBody(), is(XmlBody.xml("<a>${xmlunit.ignore}</a>")));
        assertThat(result.getWarnings().get(0).getConstruct(), is("bodyPatterns.equalToXml.enablePlaceholders"));
    }

    // ---- bodyFileName ------------------------------------------------------------------------------

    @Test
    public void resolvesBodyFileNameInsideTheFilesDirectory() throws Exception {
        File files = temporaryFolder.newFolder("__files");
        Files.writeString(new File(files, "user.json").toPath(), "{\"name\":\"Alice\"}");
        String stub = """
            { "request": { "urlPath": "/user" },
              "response": { "status": 200, "headers": { "Content-Type": "application/json" }, "bodyFileName": "user.json" } }
            """;
        ImportResult result = importer.importExpectations(stub, ImportRedaction.Options.enabled(), files.getAbsolutePath());
        assertThat(result.getWarnings(), is(empty()));
        HttpResponse response = result.getExpectations().get(0).getHttpResponse();
        FileBody fileBody = (FileBody) response.getBody();
        assertThat(fileBody.getFilePath(), is(new File(files, "user.json").toPath().toRealPath().toString()));

        // the served body is the file contents
        BodyWithContentType served = new FileBodyMaterialiser(new MockServerLogger(), new Configuration()).materialise(fileBody, request().withPath("/user"));
        assertThat(served.toString(), is("{\"name\":\"Alice\"}"));
    }

    @Test
    public void warnsOnBodyFileNameWithoutFilesDirectoryOrOutsideIt() throws Exception {
        File files = temporaryFolder.newFolder("__files");
        String stubs = """
            [ { "request": { "urlPath": "/a" }, "response": { "status": 200, "bodyFileName": "../secret.txt" } },
              { "request": { "urlPath": "/b" }, "response": { "status": 200, "bodyFileName": "missing.json" } } ]
            """;
        ImportResult withDirectory = importer.importExpectations(stubs, ImportRedaction.Options.enabled(), files.getAbsolutePath());
        assertThat(withDirectory.getWarnings(), hasSize(2));
        assertThat(withDirectory.getWarnings().get(0).getDetail(), containsString("outside the files directory"));
        assertThat(withDirectory.getWarnings().get(1).getDetail(), containsString("was not found"));
        assertThat(withDirectory.getExpectations().get(0).getHttpResponse().getBody(), is(nullValue()));

        ImportResult withoutDirectory = importer.importExpectations(stubs);
        assertThat(withoutDirectory.getWarnings().get(0).getDetail(), containsString("no files directory was given"));
    }

    // ---- response templating -----------------------------------------------------------------------

    @Test
    public void translatesSimpleHandlebarsReferencesIntoAMustacheTemplate() {
        String stub = """
            { "request": { "method": "GET", "urlPath": "/api/orders/42" },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "application/json", "X-Echo": "{{request.method}}" },
                "body": "{\\"path\\":\\"{{request.path}}\\",\\"id\\":\\"{{request.pathSegments.[2]}}\\",\\"q\\":\\"{{request.query.search}}\\",\\"second\\":\\"{{request.query.search.[1]}}\\",\\"trace\\":\\"{{request.headers.X-Trace}}\\",\\"c\\":\\"{{request.cookies.session}}\\"}",
                "transformers": [ "response-template" ]
              } }
            """;
        ImportResult result = importer.importExpectations(stub);
        assertOnlyTheBackslashWarning(result);
        HttpTemplate template = result.getExpectations().get(0).getHttpResponseTemplate();
        assertThat(template.getTemplateType(), is(HttpTemplate.TemplateType.MUSTACHE));

        HttpResponse rendered = render(template, request()
            .withMethod("GET")
            .withPath("/api/orders/42")
            .withQueryStringParameter("search", "books", "films")
            .withHeader("x-trace", "abc123")
            .withCookie("session", "s1"));
        assertThat(rendered.getStatusCode(), is(200));
        assertThat(rendered.getFirstHeader("X-Echo"), is("GET"));
        assertThat(rendered.getBodyAsString(), is("{\"path\":\"/api/orders/42\",\"id\":\"42\",\"q\":\"books\",\"second\":\"films\",\"trace\":\"abc123\",\"c\":\"s1\"}"));
    }

    @Test
    public void mapsUrlPathTemplateAndItsPathParametersInTemplates() {
        String stub = """
            { "request": { "method": "GET", "urlPathTemplate": "/users/{id}",
                           "pathParameters": { "id": { "matches": "[0-9]+" } } },
              "response": { "status": 200, "body": "user {{request.path.id}}", "transformers": [ "response-template" ] } }
            """;
        ImportResult result = importer.importExpectations(stub);
        assertOnlyTheBackslashWarning(result);
        Expectation expectation = result.getExpectations().get(0);
        HttpRequest incoming = request().withMethod("GET").withPath("/users/7");
        assertThat(new MatcherBuilder(configuration, new MockServerLogger()).transformsToMatcher(expectation).matches(incoming), is(true));
        assertThat(new MatcherBuilder(configuration, new MockServerLogger()).transformsToMatcher(expectation).matches(request().withMethod("GET").withPath("/users/abc")), is(false));
        assertThat(render(expectation.getHttpResponseTemplate(), incoming).getBodyAsString(), is("user 7"));
    }

    @Test
    public void servesTemplatesWithHelpersVerbatimAndWarns() {
        String stubs = """
            [ { "request": { "urlPath": "/now" },
                "response": { "status": 200, "body": "{{now}} {{jsonPath request.body '$.id'}} {{{request.body}}}", "transformers": [ "response-template" ] } },
              { "request": { "urlPath": "/untemplated" },
                "response": { "status": 200, "body": "literal {{request.path}}" } },
              { "request": { "urlPath": "/custom" },
                "response": { "status": 200, "body": "x", "transformers": [ "my-transformer" ] } } ]
            """;
        ImportResult result = importer.importExpectations(stubs);
        Expectation helpers = result.getExpectations().get(0);
        assertThat(helpers.getHttpResponseTemplate(), is(nullValue()));
        assertThat(helpers.getHttpResponse().getBodyAsString(), is("{{now}} {{jsonPath request.body '$.id'}} {{{request.body}}}"));
        assertThat(result.getWarnings().get(0).getConstruct(), is("response.transformers.response-template"));
        assertThat(result.getWarnings().get(0).getDetail(), containsString("{{now}}"));

        // without the response-template transformer WireMock serves the braces literally, and so does the import
        assertThat(result.getExpectations().get(1).getHttpResponse().getBodyAsString(), is("literal {{request.path}}"));

        assertThat(result.getWarnings(), hasSize(2));
        assertThat(result.getWarnings().get(1).getConstruct(), is("response.transformers.my-transformer"));
    }

    @Test
    public void warnsOnReferencesThatDependOnAVaryingOrMissingPathSegment() {
        String stubs = """
            [ { "request": { "urlPathPattern": "/api/orders/[0-9]+" },
                "response": { "status": 200, "body": "{{request.pathSegments.[2]}}", "transformers": [ "response-template" ] } },
              { "request": { "urlPath": "/api" },
                "response": { "status": 200, "body": "{{request.pathSegments.[99999999999]}} {{request.}}", "transformers": [ "response-template" ] } } ]
            """;
        ImportResult result = importer.importExpectations(stubs);
        assertThat(result.getExpectations().get(0).getHttpResponse().getBodyAsString(), is("{{request.pathSegments.[2]}}"));
        assertThat(result.getExpectations().get(1).getHttpResponseTemplate(), is(nullValue()));
        assertThat(result.getWarnings(), hasSize(2));
    }

    @Test
    public void inlinesATemplatedBodyFileSoItsReferencesAreTranslated() throws Exception {
        File files = temporaryFolder.newFolder("__files");
        Files.writeString(new File(files, "greeting.txt").toPath(), "hello {{request.query.name}}");
        String stub = """
            { "request": { "urlPath": "/greet" },
              "response": { "status": 200, "bodyFileName": "greeting.txt", "transformers": [ "response-template" ] } }
            """;
        ImportResult result = importer.importExpectations(stub, ImportRedaction.Options.enabled(), files.getAbsolutePath());
        assertOnlyTheBackslashWarning(result);
        HttpResponse rendered = render(result.getExpectations().get(0).getHttpResponseTemplate(),
            request().withPath("/greet").withQueryStringParameter("name", "Ada"));
        assertThat(rendered.getBodyAsString(), is("hello Ada"));
    }

    // ---- scenarios ---------------------------------------------------------------------------------

    @Test
    public void scenarioStubsStepThroughStatesInOrder() {
        String stubs = """
            { "mappings": [
              { "scenarioName": "cart", "requiredScenarioState": "Started", "newScenarioState": "item-added",
                "request": { "method": "GET", "urlPath": "/cart" }, "response": { "status": 200, "body": "empty" } },
              { "scenarioName": "cart", "requiredScenarioState": "item-added",
                "request": { "method": "GET", "urlPath": "/cart" }, "response": { "status": 200, "body": "one item" } } ] }
            """;
        RequestMatchers requestMatchers = new RequestMatchers(configuration, new MockServerLogger(), mock(Scheduler.class), mock(WebSocketClientRegistry.class));
        importer.importExpectations(stubs).getExpectations().forEach(expectation -> requestMatchers.add(expectation, API));

        assertThat(requestMatchers.firstMatchingExpectation(request().withMethod("GET").withPath("/cart")).getHttpResponse().getBodyAsString(), is("empty"));
        assertThat(requestMatchers.firstMatchingExpectation(request().withMethod("GET").withPath("/cart")).getHttpResponse().getBodyAsString(), is("one item"));
        assertThat(requestMatchers.firstMatchingExpectation(request().withMethod("GET").withPath("/cart")).getHttpResponse().getBodyAsString(), is("one item"));
    }

    // ---- stub-level constructs ---------------------------------------------------------------------

    @Test
    public void warnsOnMetadataAndPersistentFlag() {
        String stub = """
            { "metadata": { "team": "payments" }, "persistent": true,
              "request": { "urlPath": "/m" }, "response": { "status": 200 } }
            """;
        List<String> constructs = importer.importExpectations(stub).getWarnings().stream().map(ImportWarning::getConstruct).toList();
        assertThat(constructs, contains("metadata", "persistent"));
    }

    @Test
    public void mapsWebhooksToAfterActions() {
        String stubs = """
            [ { "request": { "method": "POST", "urlPath": "/orders" },
                "response": { "status": 201 },
                "postServeActions": [ { "name": "webhook", "parameters": {
                  "method": "POST",
                  "url": "https://hooks.example.com:8443/notify?source=wm",
                  "headers": { "Content-Type": "application/json", "X-Order": "{{originalRequest.headers.X-Order-Id}}" },
                  "body": "{\\"id\\":\\"{{jsonPath originalRequest.body '$.order.id'}}\\",\\"method\\":\\"{{originalRequest.method}}\\"}",
                  "delay": { "type": "fixed", "milliseconds": 500 } } } ] },
              { "request": { "method": "GET", "urlPath": "/ping" },
                "response": { "status": 200 },
                "serveEventListeners": [
                  { "name": "webhook", "parameters": { "url": "http://audit.svc/log", "body": "{{now}}" } },
                  { "name": "slack-notifier", "parameters": {} } ] } ]
            """;
        ImportResult result = importer.importExpectations(stubs);

        AfterAction orderHook = result.getExpectations().get(0).getAfterActions().get(0);
        assertThat(orderHook.getDelay().sampleValueMillis(), is(500L));
        HttpRequest sent = OpenApiRuntimeExpressionResolver.resolve(orderHook.getHttpRequest(), request()
            .withMethod("POST")
            .withPath("/orders")
            .withHeader("X-Order-Id", "o-9")
            .withBody("{\"order\":{\"id\":\"o-9\"}}"));
        assertThat(sent.getMethod().getValue(), is("POST"));
        assertThat(sent.isSecure(), is(true));
        assertThat(sent.getFirstHeader("Host"), is("hooks.example.com:8443"));
        assertThat(sent.getPath().getValue(), is("/notify"));
        assertThat(sent.getFirstQueryStringParameter("source"), is("wm"));
        assertThat(sent.getFirstHeader("X-Order"), is("o-9"));
        assertThat(sent.getBodyAsString(), is("{\"id\":\"o-9\",\"method\":\"POST\"}"));

        AfterAction auditHook = result.getExpectations().get(1).getAfterActions().get(0);
        assertThat(auditHook.getHttpRequest().getMethod().getValue(), is("GET"));
        assertThat(auditHook.getHttpRequest().isSecure(), is(false));
        assertThat(auditHook.getHttpRequest().getFirstHeader("Host"), is("audit.svc"));
        assertThat(result.getExpectations().get(1).getAfterActions(), hasSize(1));

        List<String> constructs = result.getWarnings().stream().map(ImportWarning::getConstruct).toList();
        assertThat(constructs, contains("serveEventListeners.webhook", "serveEventListeners.slack-notifier"));
    }

    @Test
    public void importedExpectationsSurviveTheControlPlaneSerializationRoundTrip() throws Exception {
        File files = temporaryFolder.newFolder("__files");
        Files.writeString(new File(files, "body.txt").toPath(), "from file");
        String stubs = """
            [ { "request": { "urlPath": "/t" }, "response": { "status": 200, "body": "{{request.path}}", "transformers": [ "response-template" ] } },
              { "request": { "urlPath": "/f" }, "response": { "fault": "MALFORMED_RESPONSE_CHUNK" } },
              { "request": { "urlPath": "/d" }, "response": { "status": 200, "body": "abcdef",
                  "delayDistribution": { "type": "lognormal", "median": 50, "sigma": 0.2 },
                  "chunkedDribbleDelay": { "numberOfChunks": 2, "totalDuration": 10 } } },
              { "request": { "urlPath": "/file" }, "response": { "status": 200, "bodyFileName": "body.txt" } },
              { "request": { "method": "POST", "urlPath": "/x", "bodyPatterns": [ { "matchesXPath": "//a" } ] },
                "response": { "status": 200 },
                "postServeActions": [ { "name": "webhook", "parameters": { "url": "http://h/x", "method": "POST" } } ] } ]
            """;
        ExpectationSerializer serializer = new ExpectationSerializer(new MockServerLogger());
        for (Expectation expectation : importer.importExpectations(stubs, ImportRedaction.Options.enabled(), files.getAbsolutePath()).getExpectations()) {
            assertThat(serializer.deserialize(serializer.serialize(expectation)), is(expectation));
        }
    }

    @Test
    public void redactsSecretsInATemplatedResponseBeforeItBecomesTemplateText() {
        String stub = """
            { "request": { "urlPath": "/login" },
              "response": { "status": 200,
                            "headers": { "Content-Type": "application/json", "Set-Cookie": "session=SUPERSECRETCOOKIE", "Authorization": "Bearer SECRETTOKEN" },
                            "body": "{\\"api_key\\":\\"SECRETAPIKEY\\",\\"path\\":\\"{{request.path}}\\"}",
                            "transformers": [ "response-template" ] } }
            """;
        String redacted = importer.importExpectations(stub).getExpectations().get(0).getHttpResponseTemplate().getTemplate();
        assertThat(redacted, not(containsString("SUPERSECRETCOOKIE")));
        assertThat(redacted, not(containsString("SECRETTOKEN")));
        assertThat(redacted, not(containsString("SECRETAPIKEY")));
        java.util.regex.Matcher rawBytes = java.util.regex.Pattern.compile("\"rawBytes\" : \"([^\"]*)\"").matcher(redacted);
        while (rawBytes.find()) {
            assertThat(new String(java.util.Base64.getDecoder().decode(rawBytes.group(1)), UTF_8), not(containsString("SECRETAPIKEY")));
        }
        HttpResponse rendered = render(importer.importExpectations(stub).getExpectations().get(0).getHttpResponseTemplate(), request().withPath("/login"));
        assertThat(rendered.getBodyAsString(), containsString("\"path\":\"/login\""));

        String verbatim = importer.importExpectations(stub, ImportRedaction.Options.disabled(), null).getExpectations().get(0).getHttpResponseTemplate().getTemplate();
        assertThat(verbatim, containsString("SECRETAPIKEY"));
    }

    @Test
    public void urlPathTemplateWithoutPathParametersMatchesAnySegment() {
        String stub = """
            { "request": { "method": "GET", "urlPathTemplate": "/users/{id}/orders/{orderId}" },
              "response": { "status": 200, "body": "{{request.path.orderId}}", "transformers": [ "response-template" ] } }
            """;
        Expectation expectation = importer.importExpectations(stub).getExpectations().get(0);
        HttpRequest incoming = request().withMethod("GET").withPath("/users/7/orders/o-1");
        assertThat(new MatcherBuilder(configuration, new MockServerLogger()).transformsToMatcher(expectation).matches(incoming), is(true));
        assertThat(new MatcherBuilder(configuration, new MockServerLogger()).transformsToMatcher(expectation).matches(request().withMethod("GET").withPath("/users/7/orders")), is(false));
        assertThat(render(expectation.getHttpResponseTemplate(), incoming).getBodyAsString(), is("o-1"));
    }

    @Test
    public void refusesASymbolicLinkThatLeadsOutOfTheFilesDirectory() throws Exception {
        File files = temporaryFolder.newFolder("__files");
        File outside = temporaryFolder.newFile("outside.txt");
        Files.writeString(outside.toPath(), "secret");
        Files.createSymbolicLink(new File(files, "link.txt").toPath(), outside.toPath());
        String stub = """
            { "request": { "urlPath": "/l" }, "response": { "status": 200, "bodyFileName": "link.txt" } }
            """;
        ImportResult result = importer.importExpectations(stub, ImportRedaction.Options.enabled(), files.getAbsolutePath());
        assertThat(result.getExpectations().get(0).getHttpResponse().getBody(), is(nullValue()));
        assertThat(result.getWarnings().get(0).getDetail(), containsString("outside the files directory"));
    }

    @Test
    public void masksOrWarnsOnSecretsInsideBodyMatchers() {
        String binary = java.util.Base64.getEncoder().encodeToString("{\"api_key\":\"BINSECRET\"}".getBytes(UTF_8));
        String stubs = """
            [ { "request": { "method": "POST", "urlPath": "/a", "bodyPatterns": [ { "matchesXPath": { "expression": "//password/text()", "equalTo": "XPSECRET" } } ] } },
              { "request": { "method": "POST", "urlPath": "/b", "bodyPatterns": [ { "matchesXPath": "//login[password='XPSECRET2']" } ] } },
              { "request": { "method": "POST", "urlPath": "/c", "bodyPatterns": [ { "matches": ".*password=HUNTER2.*" } ] } },
              { "request": { "method": "POST", "urlPath": "/d", "bodyPatterns": [ { "contains": "\\"api_key\\":\\"FRAGSECRET\\"" } ] } },
              { "request": { "method": "POST", "urlPath": "/e", "bodyPatterns": [ { "equalToXml": "<order><secretary>Jane</secretary></order>" } ] } },
              { "request": { "method": "POST", "urlPath": "/f", "bodyPatterns": [ { "binaryEqualTo": "%s" } ] } },
              { "request": { "method": "POST", "urlPath": "/g", "bodyPatterns": [ { "matchesXPath": "//book/title" } ] } } ]
            """.formatted(binary);
        ImportResult result = importer.importExpectations(stubs);

        // the XPath comparison value is masked like a JSON field, and the matcher is reported as now matching only the placeholder
        String xpath = ((HttpRequest) result.getExpectations().get(0).getHttpRequest()).getBody().getValue().toString();
        assertThat(xpath, is("(//password/text()) = '" + FixtureRedactor.REDACTED_PLACEHOLDER + "'"));
        assertThat(warningFor(result, "stub[0]"), containsString("replaced by the redaction placeholder"));
        // expressions and fragments redaction cannot reach keep their value, with a warning naming the field
        assertThat(warningFor(result, "stub[1]"), containsString("mentions the sensitive field 'password' but is kept as written"));
        assertThat(warningFor(result, "stub[2]"), containsString("mentions the sensitive field 'password' but is kept as written"));
        assertThat(warningFor(result, "stub[3]"), containsString("mentions the sensitive field 'api_key' but is kept as written"));
        // matchers whose secrets were masked report that they now match only the placeholder
        assertThat(warningFor(result, "stub[4]"), containsString("replaced by the redaction placeholder"));
        assertThat(warningFor(result, "stub[5]"), containsString("replaced by the redaction placeholder"));
        assertThat(warningFor(result, "stub[6]"), is(nullValue()));

        ImportResult unredacted = importer.importExpectations(stubs, ImportRedaction.Options.disabled(), null);
        assertThat(((HttpRequest) unredacted.getExpectations().get(0).getHttpRequest()).getBody().getValue().toString(), is("(//password/text()) = 'XPSECRET'"));
        assertThat(unredacted.getWarnings(), is(empty()));
    }

    private static String warningFor(ImportResult result, String item) {
        return result.getWarnings().stream().filter(w -> w.getItem().equals(item) && w.getConstruct().equals("bodyPatterns"))
            .map(ImportWarning::getDetail).findFirst().orElse(null);
    }

    private static void assertOnlyTheBackslashWarning(ImportResult result) {
        assertThat(result.getWarnings(), hasSize(1));
        assertThat(result.getWarnings().get(0).getDetail(), containsString("a backslash in one is read as a JSON escape"));
    }

    private boolean matches(ImportResult result, int index, HttpRequest request) {
        return new MatcherBuilder(configuration, new MockServerLogger()).transformsToMatcher(result.getExpectations().get(index)).matches(request);
    }

    private static HttpRequest xmlRequest(String path, String xml) {
        return request().withMethod("POST").withPath(path).withBody(xml);
    }

    private HttpResponse render(HttpTemplate template, HttpRequest request) {
        return new MustacheTemplateEngine(new MockServerLogger(), configuration).executeTemplate(template.getTemplate(), request, HttpResponseDTO.class);
    }

    @Test
    public void mapsDateTimePredicatesOntoDateTimeMatchers() {
        String stub = """
            { "request": { "method": "GET", "urlPath": "/events",
                "headers": {
                  "X-Since": { "after": "now", "expectedOffset": -3, "expectedOffsetUnit": "days" },
                  "X-Until": { "before": "2026-12-31T00:00:00Z", "actualFormat": "dd/MM/yyyy" },
                  "X-Month": { "equalToDateTime": "now +15 days", "truncateExpected": "first day of month",
                               "truncateActual": "first day of month", "applyTruncationLast": true }
                },
                "queryParameters": { "from": { "after": "2026-01-01T00:00:00Z" } } },
              "response": { "status": 200 } }
            """;
        ImportResult result = importer.importExpectations(stub);
        HttpRequest request = (HttpRequest) result.getExpectations().get(0).getHttpRequest();

        assertThat(request.getHeaders().getValues(string("X-Since")).iterator().next(), is(after("now -3 days")));
        assertThat(request.getHeaders().getValues(string("X-Until")).iterator().next(), is(before("2026-12-31T00:00:00Z").withActualFormat("dd/MM/yyyy")));
        assertThat(request.getHeaders().getValues(string("X-Month")).iterator().next(), is(equalToDateTime("now +15 days").withTruncateExpected("first day of month").withTruncateActual("first day of month").withApplyTruncationLast(true)));
        assertThat(request.getQueryStringParameters().getValues(string("from")).iterator().next(), is(after("2026-01-01T00:00:00Z")));
        assertThat(request.getHeaders().getValues(string("X-Since")).iterator().next(), instanceOf(NottableDateTimeString.class));
        assertThat(result.getWarnings(), is(empty()));
    }

    @Test
    public void warnsAndOmitsAnUnmappableDateTimePredicate() {
        String stub = """
            { "request": { "urlPath": "/events",
                "headers": { "X-Since": { "after": "now", "truncateActual": "first day of week" } } },
              "response": { "status": 200 } }
            """;
        ImportResult result = importer.importExpectations(stub);
        HttpRequest request = (HttpRequest) result.getExpectations().get(0).getHttpRequest();

        assertThat(request.getFirstHeader("X-Since"), is(""));
        assertThat(result.getWarnings().stream().anyMatch(w -> w.getDetail().contains("'after' predicate could not be mapped")), is(true));
    }
}
