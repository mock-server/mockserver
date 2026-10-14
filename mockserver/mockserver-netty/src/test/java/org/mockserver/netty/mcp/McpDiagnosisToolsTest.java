package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * The MCP diagnosis tools: {@code explain_request} explains one received request by its correlation id, and
 * {@code diagnose_config} reports the effective configuration. Both return redacted output.
 */
public class McpDiagnosisToolsTest {

    private static final String AUTHORIZATION_SECRET = "MCP-AUTHZ-SECRET-1";
    private static final String API_KEY_SECRET = "mcp-diagnose-config-secret";

    private final ObjectMapper objectMapper = ObjectMapperFactory.buildObjectMapperWithoutRemovingEmptyValues();
    private final List<HttpState> httpStates = new ArrayList<>();

    @After
    public void stopHttpStates() {
        httpStates.forEach(HttpState::stop);
    }

    // ---- explain_request ----

    @Test
    public void shouldExplainARecordedRequestByItsCorrelationId() throws Exception {
        HttpState httpState = newHttpState(configuration().logLevel(Level.INFO));
        httpState.add(
            new Expectation(request().withMethod("GET").withPath("/orders").withHeader("X-Tenant", "gold"))
                .withId("gold-orders").withPriority(10).thenRespond(response()),
            new Expectation(request().withMethod("GET").withPath("/orders"))
                .withId("any-orders").thenRespond(response())
        );
        HttpRequest received = request().withMethod("GET").withPath("/orders").withHeader("X-Tenant", "silver");
        recordMatched(httpState, "mcp-c1", received, "any-orders");

        JsonNode result = explainRequest(new McpToolRegistry(httpState, server()), "mcp-c1");

        assertThat(result.toString(), result.path("error").asBoolean(false), is(false));
        assertThat(result.path("correlationId").asText(), is("mcp-c1"));
        assertThat(result.path("matchedExpectation").path("expectationId").asText(), is("any-orders"));
        JsonNode skipped = result.path("skippedExpectations").get(0);
        assertThat(skipped.path("expectationId").asText(), is("gold-orders"));
        assertThat(skipped.path("reason").asText(), is("FIELDS_DIFFER"));
        assertThat(skipped.path("differences").has("headers"), is(true));
        assertThat(result.path("atArrival").path("outcome").asText(), is("MATCHED"));
        assertThat(result.path("recomputed").asBoolean(), is(true));
    }

    @Test
    public void shouldReturnAToolErrorForAnUnknownCorrelationId() throws Exception {
        McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());

        JsonNode result = callTool(registry, "explain_request", objectMapper.createObjectNode().put("correlationId", "never-received"));

        assertThat(result.path("error").asBoolean(false), is(true));
        assertThat(result.path("message").asText(), containsString("no request is recorded with this correlationId"));
        assertThat(result.path("correlationId").asText(), is("never-received"));
    }

    @Test
    public void shouldRequireACorrelationId() throws Exception {
        McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());

        JsonNode missing = callTool(registry, "explain_request", objectMapper.createObjectNode());
        JsonNode blank = callTool(registry, "explain_request", objectMapper.createObjectNode().put("correlationId", " "));

        assertThat(missing.path("error").asBoolean(false), is(true));
        assertThat(missing.path("message").asText(), is("'correlationId' is required"));
        assertThat(blank.path("message").asText(), is("'correlationId' is required"));
    }

    @Test
    public void shouldRedactExplainRequestOutputWhenTheLogIsRedacted() throws Exception {
        String redacted = explainRequestWithACredential(configuration().logLevel(Level.INFO).redactSecretsInLog(true));
        String unredacted = explainRequestWithACredential(configuration().logLevel(Level.INFO).redactSecretsInLog(false));

        assertThat(redacted, containsString("differences"));
        assertThat(redacted, not(containsString(AUTHORIZATION_SECRET)));
        assertThat("the log redaction setting, not the tool, decides", unredacted, containsString(AUTHORIZATION_SECRET));
    }

    @Test
    public void shouldMarkAnUnknownCorrelationIdAsAToolErrorOverJsonRpc() throws Exception {
        HttpState httpState = newHttpState(configuration());
        McpRequestProcessor processor = new McpRequestProcessor(httpState, server(), new McpSessionManager(httpState.getMockServerLogger()));
        String sessionId = processor.handlePost("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}", null).getSessionId();
        processor.handlePost("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", sessionId);

        McpRequestProcessor.McpResult result = processor.handlePost("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"explain_request\",\"arguments\":{\"correlationId\":\"never-received\"}}}", sessionId);

        JsonNode json = objectMapper.readTree(new String(result.getBody(), StandardCharsets.UTF_8));
        assertThat(json.toString(), json.path("result").path("isError").asBoolean(false), is(true));
        assertThat(json.path("result").path("content").get(0).path("text").asText(), containsString("no request is recorded"));
    }

    @Test
    public void shouldClassifyTheDiagnosisToolsAsReads() {
        assertThat(McpToolRegistry.isMutatingTool("explain_request"), is(false));
        assertThat(McpToolRegistry.isMutatingTool("diagnose_config"), is(false));
    }

    // ---- diagnose_config ----

    @Test
    public void shouldReportOnlyThePropertiesSetAwayFromTheirDefaultWithCredentialsRedacted() throws Exception {
        int defaultMaxExpectations = withoutSystemProperty("mockserver.maxExpectations", ConfigurationProperties::maxExpectations);
        withSystemProperties(() -> {
            McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());

            JsonNode result = callTool(registry, "diagnose_config", objectMapper.createObjectNode());

            assertThat(result.toString(), result.path("error").asBoolean(false), is(false));
            assertThat(propertyNode(result, "mockserver.maxExpectations").path("value").asText(), is("4242"));
            assertThat(propertyNode(result, "mockserver.maxExpectations").path("source").asText(), is(ConfigurationProperties.SOURCE_SYSTEM_PROPERTY));
            assertThat(propertyNode(result, "mockserver.llmApiKey").path("value").asText(), is("***REDACTED***"));
            assertThat(result.toString(), not(containsString(API_KEY_SECRET)));
            // each overridden property shows its built-in default beside its value; a credential's is the mask
            assertThat(propertyNode(result, "mockserver.maxExpectations").path("default").asText(), is(String.valueOf(defaultMaxExpectations)));
            assertThat(propertyNode(result, "mockserver.llmApiKey").path("default").asText(), is("***REDACTED***"));
            for (JsonNode property : result.path("properties")) {
                assertThat(property.toString(), property.path("source").asText(), not(is(ConfigurationProperties.SOURCE_DEFAULT)));
            }
            assertThat(result.path("count").asInt(), is(result.path("properties").size()));
            assertThat(result.path("explicitlySetCount").asInt(), is(result.path("count").asInt()));
            assertThat(result.path("totalProperties").asInt(), greaterThan(result.path("count").asInt()));
        });
    }

    @Test
    public void shouldLookUpAPropertyByAnyFormOfItsNameIncludingAtItsDefault() throws Exception {
        McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());

        for (String name : new String[]{"MOCKSERVER_MAX_LOGGED_BODY_BYTES", "mockserver.maxLoggedBodyBytes", "maxloggedbodybytes", " max-logged-body-bytes "}) {
            JsonNode result = callTool(registry, "diagnose_config", objectMapper.createObjectNode().put("name", name));

            JsonNode property = propertyNode(result, "mockserver.maxLoggedBodyBytes");
            assertThat(name, property.path("name").asText(), is("mockserver.maxLoggedBodyBytes"));
            assertThat(name, result.path("count").asInt(), is(1));
        }
    }

    @Test
    public void shouldShowTheValueOfAPropertyAtItsDefaultAndMaskACredentialAtItsDefault() throws Exception {
        McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());

        JsonNode bodyBytes = propertyNode(callTool(registry, "diagnose_config", objectMapper.createObjectNode().put("name", "maxLoggedBodyBytes")), "mockserver.maxLoggedBodyBytes");
        JsonNode apiKey = propertyNode(callTool(registry, "diagnose_config", objectMapper.createObjectNode().put("name", "llmApiKey")), "mockserver.llmApiKey");

        assertThat(bodyBytes.path("source").asText(), is(ConfigurationProperties.SOURCE_DEFAULT));
        assertThat(bodyBytes.path("value").asText(), is(String.valueOf(ConfigurationProperties.maxLoggedBodyBytes())));
        assertThat("a property at its default has no separate default", bodyBytes.has("default"), is(false));
        assertThat(apiKey.path("source").asText(), is(ConfigurationProperties.SOURCE_DEFAULT));
        assertThat(apiKey.path("value").asText(), is("***REDACTED***"));
    }

    @Test
    public void shouldListUnrecognisedKeysByNameWithSuggestionsAndNeverTheirValues() throws Exception {
        String typoKey = "mockserver.maxExpectatons";
        String fileOnlyKey = "mockserver.candidateIndexThreshold";
        String typoSecret = "mcp-unrecognised-typo-secret";
        String fileSecret = "mcp-unrecognised-file-secret";
        String previousTypo = System.getProperty(typoKey);
        String previousFileOnly = System.getProperty(fileOnlyKey);
        Object previousFileEntry = ConfigurationProperties.PROPERTIES.get(fileOnlyKey);
        try {
            System.setProperty(typoKey, typoSecret);
            // a system-property-only key in the properties file is ignored unless the system property is set
            System.clearProperty(fileOnlyKey);
            ConfigurationProperties.PROPERTIES.setProperty(fileOnlyKey, fileSecret);
            McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());

            for (JsonNode result : List.of(
                callTool(registry, "diagnose_config", objectMapper.createObjectNode()),
                callTool(registry, "diagnose_config", objectMapper.createObjectNode().put("name", "maxLoggedBodyBytes")))) {
                JsonNode typo = unrecognisedNode(result, typoKey);
                assertThat(typo.path("setAs").asText(), is("system property"));
                assertThat(typo.path("suggestions").toString(), is("[\"mockserver.maxExpectations\"]"));
                assertThat(typo.path("systemPropertyOnly").asBoolean(true), is(false));
                assertThat(typo.has("value"), is(false));
                JsonNode fileOnly = unrecognisedNode(result, fileOnlyKey);
                assertThat(fileOnly.path("setAs").asText(), is("properties-file key"));
                assertThat(fileOnly.path("systemPropertyOnly").asBoolean(false), is(true));
                assertThat(result.toString(), not(containsString(typoSecret)));
                assertThat(result.toString(), not(containsString(fileSecret)));
            }
        } finally {
            if (previousTypo != null) {
                System.setProperty(typoKey, previousTypo);
            } else {
                System.clearProperty(typoKey);
            }
            if (previousFileOnly != null) {
                System.setProperty(fileOnlyKey, previousFileOnly);
            }
            if (previousFileEntry != null) {
                ConfigurationProperties.PROPERTIES.put(fileOnlyKey, previousFileEntry);
            } else {
                ConfigurationProperties.PROPERTIES.remove(fileOnlyKey);
            }
        }
    }

    @Test
    public void shouldHintWhenNoPropertyMatchesTheName() throws Exception {
        McpToolRegistry registry = new McpToolRegistry(newHttpState(configuration()), server());

        JsonNode result = callTool(registry, "diagnose_config", objectMapper.createObjectNode().put("name", "maxExpectatons"));

        assertThat(result.path("count").asInt(), is(0));
        assertThat(result.path("hint").asText(), is("no recognised configuration property name contains 'maxExpectatons'; did you mean mockserver.maxExpectations?"));
        assertThat(result.path("suggestions").toString(), is("[\"mockserver.maxExpectations\"]"));
    }

    // ---- helpers ----

    private String explainRequestWithACredential(Configuration configuration) throws Exception {
        HttpState httpState = newHttpState(configuration);
        httpState.add(new Expectation(request().withPath("/secured").withHeader("Authorization", "Bearer expected"))
            .withId("secured").thenRespond(response()));
        recordMatched(httpState, "mcp-redaction", request().withMethod("GET").withPath("/secured")
            .withHeader("Authorization", "Bearer " + AUTHORIZATION_SECRET), null);
        return explainRequest(new McpToolRegistry(httpState, server()), "mcp-redaction").toString();
    }

    private HttpState newHttpState(Configuration configuration) {
        MockServerLogger logger = new MockServerLogger(configuration, McpDiagnosisToolsTest.class);
        HttpState httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true));
        httpStates.add(httpState);
        return httpState;
    }

    private static LifeCycle server() {
        LifeCycle server = mock(LifeCycle.class);
        when(server.getScheduler()).thenReturn(mock(Scheduler.class));
        when(server.getLocalPorts()).thenReturn(List.of(1080));
        when(server.isRunning()).thenReturn(true);
        return server;
    }

    private static void recordMatched(HttpState httpState, String correlationId, HttpRequest request, String expectationId) {
        long arrival = httpState.getRequestMatchers().lastModifiedEpochMillis() + 1;
        httpState.log(new LogEntry().setType(RECEIVED_REQUEST).setLogLevel(Level.INFO).setCorrelationId(correlationId)
            .setEpochTime(arrival).setHttpRequest(request));
        if (expectationId != null) {
            httpState.log(new LogEntry().setType(EXPECTATION_RESPONSE).setLogLevel(Level.INFO).setCorrelationId(correlationId)
                .setEpochTime(arrival).setHttpRequest(request).setHttpResponse(response()).setExpectationId(expectationId));
        }
    }

    /** The event log is written asynchronously: retry while the request is not yet visible, within a deadline. */
    private JsonNode explainRequest(McpToolRegistry registry, String correlationId) throws Exception {
        ObjectNode params = objectMapper.createObjectNode().put("correlationId", correlationId);
        long deadline = System.currentTimeMillis() + 5000;
        JsonNode result = callTool(registry, "explain_request", params);
        while (result.path("message").asText().contains("no request is recorded") && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            result = callTool(registry, "explain_request", params);
        }
        return result;
    }

    private JsonNode callTool(McpToolRegistry registry, String name, JsonNode params) {
        return registry.callTool(name, params);
    }

    private static JsonNode propertyNode(JsonNode result, String name) {
        for (JsonNode property : result.path("properties")) {
            if (name.equals(property.path("name").asText())) {
                return property;
            }
        }
        throw new AssertionError("no property '" + name + "' in " + result);
    }

    private static JsonNode unrecognisedNode(JsonNode result, String name) {
        for (JsonNode property : result.path("unrecognised")) {
            if (name.equals(property.path("name").asText())) {
                return property;
            }
        }
        throw new AssertionError("no unrecognised key '" + name + "' in " + result);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Sets a plain and a credential property for the duration of {@code body}, restoring both and their cache entries. */
    private static void withSystemProperties(ThrowingRunnable body) throws Exception {
        String[] keys = {"mockserver.maxExpectations", "mockserver.llmApiKey"};
        String[] values = {"4242", API_KEY_SECRET};
        String[] previousProperties = new String[keys.length];
        String[] previousCached = new String[keys.length];
        // creates the property cache if no read has yet
        ConfigurationProperties.effectiveConfiguration();
        Map<String, String> cache = propertyCache();
        try {
            for (int i = 0; i < keys.length; i++) {
                previousProperties[i] = System.getProperty(keys[i]);
                previousCached[i] = cache.remove(keys[i]);
                System.setProperty(keys[i], values[i]);
            }
            body.run();
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (previousCached[i] != null) {
                    cache.put(keys[i], previousCached[i]);
                } else {
                    cache.remove(keys[i]);
                }
                if (previousProperties[i] != null) {
                    System.setProperty(keys[i], previousProperties[i]);
                } else {
                    System.clearProperty(keys[i]);
                }
            }
        }
    }

    /** What {@code getter} returns with {@code key} unset and uncached, restoring both afterwards. */
    private static int withoutSystemProperty(String key, java.util.function.IntSupplier getter) throws Exception {
        // creates the property cache if no read has yet
        ConfigurationProperties.effectiveConfiguration();
        Map<String, String> cache = propertyCache();
        String previousProperty = System.getProperty(key);
        String previousCached = cache.remove(key);
        try {
            System.clearProperty(key);
            return getter.getAsInt();
        } finally {
            if (previousCached != null) {
                cache.put(key, previousCached);
            } else {
                cache.remove(key);
            }
            if (previousProperty != null) {
                System.setProperty(key, previousProperty);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> propertyCache() throws Exception {
        Field cacheField = ConfigurationProperties.class.getDeclaredField("propertyCache");
        cacheField.setAccessible(true);
        return (Map<String, String>) cacheField.get(null);
    }
}
