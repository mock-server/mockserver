package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.LoadScenarioOrchestrator;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * End-to-end routing tests for the load-scenario REGISTRY control plane exercised through
 * {@link HttpState#handle}:
 * <ul>
 *   <li>{@code PUT /mockserver/loadScenario} loads (registers) a scenario — it does NOT run, and is
 *       allowed even when load generation is disabled.</li>
 *   <li>{@code GET /mockserver/loadScenario} lists all registered scenarios; {@code GET .../{name}}
 *       returns one (404 if absent).</li>
 *   <li>{@code PUT /mockserver/loadScenario/start} triggers registered scenarios to run (403 when
 *       disabled, 404 for an unknown name).</li>
 *   <li>{@code PUT /mockserver/loadScenario/stop} stops running scenarios.</li>
 *   <li>{@code DELETE .../{name}} and {@code DELETE} remove from the registry.</li>
 * </ul>
 *
 * <p>State-mutating: flips the static {@code loadGenerationEnabled} property, drives the process-wide
 * {@link LoadScenarioOrchestrator} singleton, and writes to the registry, so it must run in the
 * sequential Surefire phase. A synchronous fake sender is installed so no real network traffic is
 * generated.
 */
public class HttpStateLoadScenarioEndpointTest {

    private HttpState httpState;
    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    private boolean originalEnabled;

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

    @Before
    public void setUp() {
        originalEnabled = ConfigurationProperties.loadGenerationEnabled();
        LoadScenarioOrchestrator.getInstance().reset();
        // Install a synchronous fake sender so the orchestrator can start without a Netty runtime.
        LoadScenarioOrchestrator.getInstance().setSender(req -> CompletableFuture.completedFuture(response().withStatusCode(200)));
        rebuildHttpState(true);
        // start from an empty registry each test
        delete();
    }

    @After
    public void tearDown() {
        delete();
        LoadScenarioOrchestrator.getInstance().reset();
        LoadScenarioOrchestrator.getInstance().setSender(null);
        ConfigurationProperties.loadGenerationEnabled(originalEnabled);
        if (httpState != null) {
            httpState.stop();
        }
    }

    private void rebuildHttpState(boolean enabled) {
        ConfigurationProperties.loadGenerationEnabled(enabled);
        Configuration configuration = configuration().loadGenerationEnabled(enabled);
        Scheduler scheduler = new Scheduler(configuration, new MockServerLogger(configuration, HttpStateLoadScenarioEndpointTest.class), true);
        if (httpState != null) {
            httpState.stop();
        }
        httpState = new HttpState(configuration, new MockServerLogger(configuration, HttpStateLoadScenarioEndpointTest.class), scheduler);
        LoadScenarioOrchestrator.getInstance().setConfiguration(configuration);
    }

    private HttpResponse put(String body) {
        FakeResponseWriter rw = new FakeResponseWriter();
        HttpRequest req = request("/mockserver/loadScenario").withMethod("PUT");
        if (body != null) {
            req.withBody(body);
        }
        assertThat("route handled", httpState.handle(req, rw, false), is(true));
        return rw.response;
    }

    private HttpResponse start(String body) {
        FakeResponseWriter rw = new FakeResponseWriter();
        HttpRequest req = request("/mockserver/loadScenario/start").withMethod("PUT");
        if (body != null) {
            req.withBody(body);
        }
        assertThat("route handled", httpState.handle(req, rw, false), is(true));
        return rw.response;
    }

    private HttpResponse stop(String body) {
        FakeResponseWriter rw = new FakeResponseWriter();
        HttpRequest req = request("/mockserver/loadScenario/stop").withMethod("PUT");
        if (body != null) {
            req.withBody(body);
        }
        assertThat("route handled", httpState.handle(req, rw, false), is(true));
        return rw.response;
    }

    private HttpResponse get() {
        FakeResponseWriter rw = new FakeResponseWriter();
        assertThat("route handled", httpState.handle(request("/mockserver/loadScenario").withMethod("GET"), rw, false), is(true));
        return rw.response;
    }

    private HttpResponse getOne(String name) {
        FakeResponseWriter rw = new FakeResponseWriter();
        assertThat("route handled", httpState.handle(request("/mockserver/loadScenario/" + name).withMethod("GET"), rw, false), is(true));
        return rw.response;
    }

    private HttpResponse deleteOne(String name) {
        FakeResponseWriter rw = new FakeResponseWriter();
        assertThat("route handled", httpState.handle(request("/mockserver/loadScenario/" + name).withMethod("DELETE"), rw, false), is(true));
        return rw.response;
    }

    private HttpResponse delete() {
        FakeResponseWriter rw = new FakeResponseWriter();
        assertThat("route handled", httpState.handle(request("/mockserver/loadScenario").withMethod("DELETE"), rw, false), is(true));
        return rw.response;
    }

    private static String scenarioJson(String name) {
        return "{ \"name\": \"" + name + "\", " +
            "\"profile\": { \"stages\": [ { \"type\": \"VU\", \"vus\": 1, \"durationMillis\": 60000 } ] }, " +
            "\"steps\": [ { \"request\": { \"path\": \"/health\", \"headers\": { \"Host\": [\"target\"] } } } ] }";
    }

    private JsonNode body(HttpResponse response) throws Exception {
        return objectMapper.readTree(response.getBodyAsString());
    }

    // --- LOAD / register ---

    @Test
    public void putLoadsScenarioWithoutRunningIt() throws Exception {
        HttpResponse response = put(scenarioJson("smoke"));
        assertThat(response.getStatusCode(), is(200));
        JsonNode b = body(response);
        assertThat(b.get("status").asText(), is("loaded"));
        assertThat(b.get("name").asText(), is("smoke"));
        assertThat(b.get("state").asText(), is("LOADED"));
        // loading does not start a run
        assertThat(LoadScenarioOrchestrator.getInstance().isActive("smoke"), is(false));
    }

    @Test
    public void loadingIsAllowedWhenGenerationDisabled() throws Exception {
        rebuildHttpState(false);
        HttpResponse response = put(scenarioJson("disabled-load"));
        assertThat(response.getStatusCode(), is(200));
        assertThat(body(response).get("status").asText(), is("loaded"));
    }

    @Test
    public void loadingSameNameReplaces() throws Exception {
        put(scenarioJson("dup"));
        put(scenarioJson("dup"));
        JsonNode list = body(get()).get("scenarios");
        long count = 0;
        for (JsonNode s : list) {
            if ("dup".equals(s.get("name").asText())) {
                count++;
            }
        }
        assertThat("loading the same name replaces, not appends", count, is(1L));
    }

    @Test
    public void getListsAllRegisteredScenariosAsLoaded() throws Exception {
        put(scenarioJson("one"));
        put(scenarioJson("two"));
        JsonNode list = body(get()).get("scenarios");
        assertThat(list.isArray(), is(true));
        assertThat(list.size(), is(2));
        boolean foundOne = false;
        for (JsonNode s : list) {
            if ("one".equals(s.get("name").asText())) {
                foundOne = true;
                assertThat(s.get("state").asText(), is("LOADED"));
                assertThat(s.get("definition").get("name").asText(), is("one"));
            }
        }
        assertThat(foundOne, is(true));
    }

    @Test
    public void getOneReturnsScenario() throws Exception {
        put(scenarioJson("single"));
        HttpResponse response = getOne("single");
        assertThat(response.getStatusCode(), is(200));
        JsonNode b = body(response);
        assertThat(b.get("name").asText(), is("single"));
        assertThat(b.get("state").asText(), is("LOADED"));
        assertThat(b.get("definition").get("steps").get(0).get("request").get("path").asText(), is("/health"));
    }

    @Test
    public void getOneReturns404WhenUnknown() throws Exception {
        HttpResponse response = getOne("missing");
        assertThat(response.getStatusCode(), is(404));
        assertThat(body(response).get("error").asText(), containsString("missing"));
    }

    @Test
    public void deleteOneRemovesFromRegistry() throws Exception {
        put(scenarioJson("removable"));
        HttpResponse response = deleteOne("removable");
        assertThat(response.getStatusCode(), is(200));
        assertThat(body(response).get("status").asText(), is("deleted"));
        assertThat(getOne("removable").getStatusCode(), is(404));
    }

    @Test
    public void deleteAllClearsRegistry() throws Exception {
        put(scenarioJson("a"));
        put(scenarioJson("b"));
        HttpResponse response = delete();
        assertThat(response.getStatusCode(), is(200));
        assertThat(body(get()).get("scenarios").size(), is(0));
    }

    // --- TRIGGER (start) ---

    @Test
    public void startTriggersOneScenarioByName() throws Exception {
        put(scenarioJson("runnable"));
        HttpResponse response = start("{ \"name\": \"runnable\" }");
        try {
            assertThat(response.getStatusCode(), is(200));
            JsonNode b = body(response);
            assertThat(b.get("status").asText(), is("started"));
            JsonNode started = b.get("started");
            assertThat(started.get(0).get("name").asText(), is("runnable"));
            assertThat(started.get(0).get("state").asText(), is("RUNNING"));
            assertThat(LoadScenarioOrchestrator.getInstance().isActive("runnable"), is(true));
        } finally {
            stop(null);
        }
    }

    @Test
    public void startTriggersMultipleScenariosByName() throws Exception {
        put(scenarioJson("multi-a"));
        put(scenarioJson("multi-b"));
        HttpResponse response = start("{ \"names\": [ \"multi-a\", \"multi-b\" ] }");
        try {
            assertThat(response.getStatusCode(), is(200));
            assertThat(body(response).get("started").size(), is(2));
            assertThat(LoadScenarioOrchestrator.getInstance().isActive("multi-a"), is(true));
            assertThat(LoadScenarioOrchestrator.getInstance().isActive("multi-b"), is(true));
        } finally {
            stop(null);
        }
    }

    @Test
    public void startReturns403WhenGenerationDisabled() throws Exception {
        rebuildHttpState(false);
        put(scenarioJson("disabled-start"));
        HttpResponse response = start("{ \"name\": \"disabled-start\" }");
        assertThat(response.getStatusCode(), is(403));
        assertThat(body(response).get("error").asText(), containsString("load generation not enabled"));
    }

    @Test
    public void startReturns404ForUnknownName() throws Exception {
        HttpResponse response = start("{ \"name\": \"never-loaded\" }");
        assertThat(response.getStatusCode(), is(404));
        assertThat(body(response).get("error").asText(), containsString("never-loaded"));
    }

    @Test
    public void startRejectsWhenExceedingConcurrentCap() throws Exception {
        int original = ConfigurationProperties.loadGenerationMaxConcurrentScenarios();
        ConfigurationProperties.loadGenerationMaxConcurrentScenarios(1);
        try {
            rebuildHttpState(true);
            put(scenarioJson("cap-1"));
            put(scenarioJson("cap-2"));
            assertThat(start("{ \"name\": \"cap-1\" }").getStatusCode(), is(200));
            HttpResponse response = start("{ \"name\": \"cap-2\" }");
            assertThat(response.getStatusCode(), is(400));
            assertThat(body(response).get("error").asText(), containsString("maximum of 1"));
        } finally {
            stop(null);
            ConfigurationProperties.loadGenerationMaxConcurrentScenarios(original);
        }
    }

    // --- STOP ---

    @Test
    public void stopAllStopsRunningScenarios() throws Exception {
        put(scenarioJson("stoppable"));
        start("{ \"name\": \"stoppable\" }");
        assertThat(LoadScenarioOrchestrator.getInstance().isActive("stoppable"), is(true));

        HttpResponse response = stop(null);
        assertThat(response.getStatusCode(), is(200));
        assertThat(body(response).get("status").asText(), is("stopped"));
        assertThat(LoadScenarioOrchestrator.getInstance().isActive("stoppable"), is(false));
        // remains registered (STOPPED) and re-startable
        assertThat(getOne("stoppable").getStatusCode(), is(200));
        assertThat(body(getOne("stoppable")).get("state").asText(), is("STOPPED"));
    }

    @Test
    public void stoppedScenarioCanBeReStarted() throws Exception {
        put(scenarioJson("restart"));
        start("{ \"name\": \"restart\" }");
        stop("{ \"names\": [ \"restart\" ] }");
        assertThat(LoadScenarioOrchestrator.getInstance().isActive("restart"), is(false));

        HttpResponse reStart = start("{ \"name\": \"restart\" }");
        try {
            assertThat(reStart.getStatusCode(), is(200));
            assertThat(LoadScenarioOrchestrator.getInstance().isActive("restart"), is(true));
        } finally {
            stop(null);
        }
    }

    // --- validation / auth ---

    @Test
    public void putReturns400ForMalformedBody() {
        HttpResponse response = put("{ not valid json");
        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("invalid load scenario definition"));
    }

    @Test
    public void putReturns400ForBlankBody() {
        HttpResponse response = put(null);
        assertThat(response.getStatusCode(), is(400));
    }

    @Test
    public void putReturns400WhenCapExceeded() throws Exception {
        ConfigurationProperties.loadGenerationMaxVirtualUsers(2);
        try {
            rebuildHttpState(true);
            String b = "{ \"name\": \"big\", " +
                "\"profile\": { \"stages\": [ { \"type\": \"VU\", \"vus\": 100, \"durationMillis\": 1000 } ] }, " +
                "\"steps\": [ { \"request\": { \"path\": \"/x\" } } ] }";
            HttpResponse response = put(b);
            assertThat(response.getStatusCode(), is(400));
            assertThat(response.getBodyAsString(), containsString("exceeding the maximum of 2"));
        } finally {
            ConfigurationProperties.loadGenerationMaxVirtualUsers(50);
        }
    }

    @Test(timeout = 10_000)
    public void putReturns400ForStairsShapeWithHugeStepCount() {
        String b = "{ \"name\": \"huge-stairs\", " +
            "\"profile\": { \"shape\": { \"type\": \"STAIRS\", \"metric\": \"RATE\", \"start\": 1, \"step\": 1, " +
            "\"steps\": 2147483647, \"stepDurationMillis\": 1 } }, " +
            "\"steps\": [ { \"request\": { \"path\": \"/x\" } } ] }";

        HttpResponse response = put(b);

        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("exceeds the maximum of"));
    }

    @Test
    public void putReturns400ForNaNStepWeight() {
        String b = "{ \"name\": \"nan-weight\", \"stepSelection\": \"WEIGHTED\", " +
            "\"profile\": { \"stages\": [ { \"type\": \"VU\", \"vus\": 1, \"durationMillis\": 1000 } ] }, " +
            "\"steps\": [ { \"request\": { \"path\": \"/x\" }, \"weight\": NaN } ] }";

        HttpResponse response = put(b);

        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("step[0].weight must be a finite number"));
    }

    @Test
    public void putLoadsStairsShapeWithinStageCap() throws Exception {
        String b = "{ \"name\": \"stairs\", " +
            "\"profile\": { \"shape\": { \"type\": \"STAIRS\", \"metric\": \"VU\", \"start\": 1, \"step\": 1, " +
            "\"steps\": 3, \"stepDurationMillis\": 1000 } }, " +
            "\"steps\": [ { \"request\": { \"path\": \"/x\" } } ] }";

        HttpResponse response = put(b);

        assertThat(response.getStatusCode(), is(200));
        assertThat(body(response).get("status").asText(), is("loaded"));
    }

    @Test
    public void putRequiresControlPlaneAuthentication() {
        AuthenticationHandler rejectingHandler = req -> false;
        httpState.setControlPlaneAuthenticationHandler(rejectingHandler);

        HttpResponse response = put(scenarioJson("auth"));
        assertThat(response.getStatusCode(), is(401));
        assertThat(response.getBodyAsString(), containsString("Unauthorized for control plane"));
    }

    @Test
    public void startDelayIsEchoedInListing() throws Exception {
        String b = "{ \"name\": \"delayed\", \"startDelayMillis\": 2000, " +
            "\"profile\": { \"stages\": [ { \"type\": \"VU\", \"vus\": 1, \"durationMillis\": 60000 } ] }, " +
            "\"steps\": [ { \"request\": { \"path\": \"/health\", \"headers\": { \"Host\": [\"target\"] } } } ] }";
        put(b);
        JsonNode one = body(getOne("delayed"));
        assertThat(one.get("startDelayMillis").asLong(), is(2000L));
        assertThat(one.get("definition").get("startDelayMillis").asLong(), is(2000L));
    }

    // --- end-of-run report ---

    private HttpResponse report(String name, String format) {
        FakeResponseWriter rw = new FakeResponseWriter();
        HttpRequest req = request("/mockserver/loadScenario/" + name + "/report").withMethod("GET");
        if (format != null) {
            req.withQueryStringParameter("format", format);
        }
        assertThat("route handled", httpState.handle(req, rw, false), is(true));
        return rw.response;
    }

    @Test
    public void reportReturnsJsonForRunningScenario() throws Exception {
        put(scenarioJson("reportable"));
        start("{ \"name\": \"reportable\" }");
        try {
            HttpResponse response = report("reportable", null);
            assertThat(response.getStatusCode(), is(200));
            assertThat(response.getBody().getContentType(), containsString("application/json"));
            JsonNode b = body(response);
            assertThat(b.get("scenario").asText(), is("reportable"));
            assertThat(b.has("runId"), is(true));
            assertThat(b.get("counts").has("requestsSent"), is(true));
            assertThat(b.get("latencyMillis").has("p95"), is(true));
            assertThat(b.has("thresholdResults"), is(true));
        } finally {
            stop(null);
        }
    }

    @Test
    public void reportReturnsJunitXmlWhenFormatJunit() throws Exception {
        put(scenarioJson("junitable"));
        start("{ \"name\": \"junitable\" }");
        try {
            HttpResponse response = report("junitable", "junit");
            assertThat(response.getStatusCode(), is(200));
            assertThat(response.getBody().getContentType(), containsString("application/xml"));
            String xml = response.getBodyAsString();
            assertThat(xml, containsString("<testsuite name=\"load:junitable\""));
            assertThat(xml, containsString("<testcase name=\"run completed\">"));
        } finally {
            stop(null);
        }
    }

    @Test
    public void reportReturnsRetainedTerminalSnapshotForStoppedRun() throws Exception {
        put(scenarioJson("terminal-report"));
        start("{ \"name\": \"terminal-report\" }");
        stop(null);
        assertThat(LoadScenarioOrchestrator.getInstance().isActive("terminal-report"), is(false));

        HttpResponse response = report("terminal-report", null);
        assertThat(response.getStatusCode(), is(200));
        assertThat(body(response).get("state").asText(), is("STOPPED"));
    }

    @Test
    public void reportReturns404WhenScenarioNeverRan() {
        HttpResponse response = report("never-ran", null);
        assertThat(response.getStatusCode(), is(404));
        assertThat(response.getBodyAsString(), containsString("never-ran"));
    }

    // --- GENERATE FROM OPENAPI ---

    private static final String TINY_OPENAPI = "{"
        + "\\\"openapi\\\": \\\"3.0.0\\\","
        + "\\\"info\\\": { \\\"title\\\": \\\"Tiny\\\", \\\"version\\\": \\\"1.0.0\\\" },"
        + "\\\"servers\\\": [ { \\\"url\\\": \\\"http://api.example.com/v1\\\" } ],"
        + "\\\"paths\\\": {"
        + "  \\\"/pets\\\": {"
        + "    \\\"get\\\": { \\\"operationId\\\": \\\"listPets\\\", \\\"responses\\\": { \\\"200\\\": { \\\"description\\\": \\\"ok\\\" } } },"
        + "    \\\"post\\\": { \\\"operationId\\\": \\\"createPet\\\","
        + "      \\\"requestBody\\\": { \\\"required\\\": true, \\\"content\\\": { \\\"application/json\\\": { \\\"schema\\\": {"
        + "        \\\"type\\\": \\\"object\\\", \\\"properties\\\": { \\\"name\\\": { \\\"type\\\": \\\"string\\\" } } } } } },"
        + "      \\\"responses\\\": { \\\"201\\\": { \\\"description\\\": \\\"created\\\" } } }"
        + "  }"
        + "} }";

    private HttpResponse generate(String body) {
        FakeResponseWriter rw = new FakeResponseWriter();
        HttpRequest req = request("/mockserver/loadScenario/generateFromOpenAPI").withMethod("PUT");
        if (body != null) {
            req.withBody(body);
        }
        assertThat("route handled", httpState.handle(req, rw, false), is(true));
        return rw.response;
    }

    @Test
    public void generateFromOpenApiRegistersScenarioAndReturnsIt() throws Exception {
        String requestBody = "{ \"name\": \"from-spec\", \"specUrlOrPayload\": \"" + TINY_OPENAPI + "\" }";
        HttpResponse response = generate(requestBody);
        assertThat(response.getStatusCode(), is(200));
        JsonNode b = body(response);
        assertThat(b.get("status").asText(), is("loaded"));
        assertThat(b.get("name").asText(), is("from-spec"));
        assertThat(b.get("state").asText(), is("LOADED"));

        // the generated scenario is returned with one step per operation
        JsonNode scenario = b.get("scenario");
        assertThat(scenario, is(org.hamcrest.Matchers.notNullValue()));
        assertThat(scenario.get("steps").size(), is(2));
        // server prefix /v1 applied; target Host from servers[0]
        JsonNode firstRequest = scenario.get("steps").get(0).get("request");
        assertThat(firstRequest.get("path").asText(), containsString("/v1/pets"));

        // it now appears in GET /loadScenario
        JsonNode list = body(get()).get("scenarios");
        boolean found = false;
        for (JsonNode s : list) {
            if ("from-spec".equals(s.get("name").asText())) {
                found = true;
                assertThat(s.get("state").asText(), is("LOADED"));
            }
        }
        assertThat("generated scenario is registered", found, is(true));
        // generating does not start a run
        assertThat(LoadScenarioOrchestrator.getInstance().isActive("from-spec"), is(false));
    }

    @Test
    public void generateFromOpenApiAllowedWhenGenerationDisabled() throws Exception {
        rebuildHttpState(false);
        String requestBody = "{ \"name\": \"disabled-gen\", \"specUrlOrPayload\": \"" + TINY_OPENAPI + "\" }";
        HttpResponse response = generate(requestBody);
        assertThat(response.getStatusCode(), is(200));
        assertThat(body(response).get("status").asText(), is("loaded"));
    }

    @Test
    public void generateFromOpenApiAppliesExplicitTargetOverServers() throws Exception {
        String requestBody = "{ \"name\": \"targeted\", \"specUrlOrPayload\": \"" + TINY_OPENAPI + "\","
            + " \"target\": { \"host\": \"localhost\", \"port\": 1080, \"scheme\": \"http\" } }";
        HttpResponse response = generate(requestBody);
        assertThat(response.getStatusCode(), is(200));
        JsonNode firstRequest = body(response).get("scenario").get("steps").get(0).get("request");
        assertThat(firstRequest.get("headers").get("Host").get(0).asText(), is("localhost:1080"));
    }

    @Test
    public void generateFromOpenApiReturns400ForInvalidSpec() throws Exception {
        String requestBody = "{ \"name\": \"bad\", \"specUrlOrPayload\": \"{ not valid openapi\" }";
        HttpResponse response = generate(requestBody);
        assertThat(response.getStatusCode(), is(400));
        assertThat(body(response).get("error").asText(), containsString("OpenAPI"));
    }

    @Test
    public void generateFromOpenApiReturns400WhenNameMissing() throws Exception {
        String requestBody = "{ \"specUrlOrPayload\": \"" + TINY_OPENAPI + "\" }";
        HttpResponse response = generate(requestBody);
        assertThat(response.getStatusCode(), is(400));
        assertThat(body(response).get("error").asText(), containsString("name"));
    }

    @Test
    public void generateFromOpenApiReturns400WhenSpecMissing() throws Exception {
        HttpResponse response = generate("{ \"name\": \"no-spec\" }");
        assertThat(response.getStatusCode(), is(400));
        assertThat(body(response).get("error").asText(), containsString("specUrlOrPayload"));
    }

    // --- GENERATE FROM RECORDING ---

    private HttpResponse generateFromRecording(String body) {
        FakeResponseWriter rw = new FakeResponseWriter();
        HttpRequest req = request("/mockserver/loadScenario/generateFromRecording").withMethod("PUT");
        if (body != null) {
            req.withBody(body);
        }
        assertThat("route handled", httpState.handle(req, rw, false), is(true));
        return rw.response;
    }

    /** Record a request into the event log exactly as the proxy/recording path does (a RECEIVED_REQUEST entry). */
    private void record(HttpRequest recordedRequest) {
        httpState.getMockServerLog().add(
            new org.mockserver.log.model.LogEntry()
                .setType(org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST)
                .setHttpRequest(recordedRequest)
        );
    }

    @Test
    public void generateFromRecordingRegistersScenarioAndReturnsIt() throws Exception {
        record(request().withMethod("GET").withPath("/orders/1"));
        record(request().withMethod("POST").withPath("/orders").withBody("{}"));

        HttpResponse response = generateFromRecording("{ \"name\": \"from-rec\" }");
        assertThat(response.getStatusCode(), is(200));
        JsonNode b = body(response);
        assertThat(b.get("status").asText(), is("loaded"));
        assertThat(b.get("name").asText(), is("from-rec"));
        assertThat(b.get("state").asText(), is("LOADED"));

        // VERBATIM by default: one step per recorded request, in order
        JsonNode steps = b.get("scenario").get("steps");
        assertThat(steps.size(), is(2));
        assertThat(steps.get(0).get("request").get("path").asText(), is("/orders/1"));
        assertThat(steps.get(1).get("request").get("path").asText(), is("/orders"));

        // it now appears in GET /loadScenario
        boolean found = false;
        for (JsonNode s : body(get()).get("scenarios")) {
            if ("from-rec".equals(s.get("name").asText())) {
                found = true;
                assertThat(s.get("state").asText(), is("LOADED"));
            }
        }
        assertThat("generated scenario is registered", found, is(true));
        // generating does not start a run
        assertThat(LoadScenarioOrchestrator.getInstance().isActive("from-rec"), is(false));
    }

    @Test
    public void generateFromRecordingTemplatizedDedupesIdRoutes() throws Exception {
        record(request().withMethod("GET").withPath("/orders/1"));
        record(request().withMethod("GET").withPath("/orders/2"));
        record(request().withMethod("GET").withPath("/orders/3"));

        HttpResponse response = generateFromRecording("{ \"name\": \"templatized\", \"mode\": \"TEMPLATIZED\" }");
        assertThat(response.getStatusCode(), is(200));
        JsonNode steps = body(response).get("scenario").get("steps");
        assertThat(steps.size(), is(1));
        assertThat(steps.get(0).get("name").asText(), is("GET /orders/{id}"));
    }

    @Test
    public void generateFromRecordingAllowedWhenGenerationDisabled() throws Exception {
        rebuildHttpState(false);
        record(request().withMethod("GET").withPath("/health"));
        HttpResponse response = generateFromRecording("{ \"name\": \"disabled-rec\" }");
        assertThat(response.getStatusCode(), is(200));
        assertThat(body(response).get("status").asText(), is("loaded"));
    }

    @Test
    public void generateFromRecordingAppliesTargetOverride() throws Exception {
        record(request().withMethod("GET").withPath("/a"));
        HttpResponse response = generateFromRecording(
            "{ \"name\": \"targeted-rec\", \"target\": { \"host\": \"localhost\", \"port\": 1080, \"scheme\": \"http\" } }");
        assertThat(response.getStatusCode(), is(200));
        JsonNode firstRequest = body(response).get("scenario").get("steps").get(0).get("request");
        assertThat(firstRequest.get("headers").get("Host").get(0).asText(), is("localhost:1080"));
    }

    @Test
    public void generateFromRecordingHonoursRequestFilter() throws Exception {
        record(request().withMethod("GET").withPath("/orders/1"));
        record(request().withMethod("GET").withPath("/products/2"));

        HttpResponse response = generateFromRecording(
            "{ \"name\": \"filtered\", \"requestFilter\": { \"path\": \"/orders/.*\" } }");
        assertThat(response.getStatusCode(), is(200));
        JsonNode steps = body(response).get("scenario").get("steps");
        assertThat(steps.size(), is(1));
        assertThat(steps.get(0).get("request").get("path").asText(), is("/orders/1"));
    }

    @Test
    public void generateFromRecordingReturns409WhenNoRecordings() throws Exception {
        // 409, not 400: the body is well-formed and parsed - there is simply nothing recorded yet.
        // Sharing 400 with a malformed body left callers unable to tell "record some traffic first"
        // apart from "your request is wrong".
        HttpResponse response = generateFromRecording("{ \"name\": \"empty-rec\" }");
        assertThat(response.getStatusCode(), is(409));
        assertThat(body(response).get("error").asText(), containsString("no recorded requests"));
    }

    @Test
    public void generateFromRecordingStillReturns400ForAMalformedBodyWhenRecordingsExist() throws Exception {
        // the 409 above must not swallow the malformed-body case, so the two stay distinguishable
        // by status code alone even once traffic has been recorded
        record(request().withMethod("GET").withPath("/a"));
        HttpResponse response = generateFromRecording("{ \"name\": \"bad\", \"mode\": \"SIDEWAYS\" }");
        assertThat(response.getStatusCode(), is(400));
        assertThat(body(response).get("error").asText(), containsString("invalid 'mode'"));
    }

    @Test
    public void generateFromRecordingReturns400WhenNameMissing() throws Exception {
        record(request().withMethod("GET").withPath("/a"));
        HttpResponse response = generateFromRecording("{ }");
        assertThat(response.getStatusCode(), is(400));
        assertThat(body(response).get("error").asText(), containsString("name"));
    }

    @Test
    public void generateFromRecordingReturns400ForInvalidMode() throws Exception {
        record(request().withMethod("GET").withPath("/a"));
        HttpResponse response = generateFromRecording("{ \"name\": \"bad-mode\", \"mode\": \"SIDEWAYS\" }");
        assertThat(response.getStatusCode(), is(400));
        assertThat(body(response).get("error").asText(), containsString("mode"));
    }
}
