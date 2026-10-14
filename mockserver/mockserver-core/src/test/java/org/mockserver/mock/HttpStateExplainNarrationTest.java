package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.llm.client.LlmBackend;
import org.mockserver.llm.client.LlmCompletionService;
import org.mockserver.llm.client.LlmTransport;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.mockserver.model.Provider;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.NO_MATCH_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * {@code ?narrate=true} on {@code PUT /mockserver/explain} and {@code PUT /mockserver/explainUnmatched} adds a
 * labelled AI-generated summary from the configured LLM beside the unchanged deterministic facts. The LLM endpoint is
 * mocked at the transport, so the assertions read the exact request that would go on the wire.
 */
public class HttpStateExplainNarrationTest {

    private static final String LLM_SUMMARY = "The request reached the catch-all because its X-Tenant header was silver, not gold.";
    private static final String AUTHORIZATION_SECRET = "Bearer sk-narration-authorization-secret";
    private static final String PASSWORD_SECRET = "narration-password-secret";
    private static final String COOKIE_SECRET = "narration-session-cookie-secret";
    private static final String QUERY_SECRET = "narration-query-token-secret";
    private static final String JWT_SECRET = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJuYXJyYXRpb24ifQ.c2lnbmF0dXJlLXNlY3JldA";
    private static final String EXPECTATION_SECRET = "Bearer sk-narration-expectation-secret";

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private final List<HttpRequest> llmRequests = new CopyOnWriteArrayList<>();
    private HttpState httpState;

    private Configuration configuration;

    @Before
    public void setUp() {
        configuration = configuration().logLevel(Level.INFO).redactSecretsInLog(false).llmExplainNarrationEnabled(true);
        Scheduler scheduler = new Scheduler(configuration, new MockServerLogger(configuration, HttpStateExplainNarrationTest.class), true);
        httpState = new HttpState(configuration, new MockServerLogger(configuration, HttpStateExplainNarrationTest.class), scheduler);
    }

    @After
    public void stopHttpState() {
        if (httpState != null) {
            httpState.stop();
        }
    }

    private void useMockedOpenAiEndpoint() {
        LlmTransport mockedEndpoint = (request, timeoutMillis) -> {
            llmRequests.add(request);
            return response().withStatusCode(200).withBody("{\"model\":\"gpt-4o-mini\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"" + LLM_SUMMARY + "\"},\"finish_reason\":\"stop\"}]}", MediaType.JSON_UTF_8);
        };
        httpState.setLlmCompletionService(new LlmCompletionService(mockedEndpoint), LlmBackend.of(Provider.OPENAI, "test-key"));
    }

    @Test
    public void shouldAddALabelledNarrationBesideTheDeterministicFacts() throws Exception {
        useMockedOpenAiEndpoint();
        addTenantExpectations();
        recordMatched("n1", request().withMethod("GET").withPath("/orders").withHeader("X-Tenant", "silver"));

        JsonNode plain = explain("n1", false);
        JsonNode narrated = explain("n1", true);

        JsonNode narration = narrated.path("narration");
        assertThat(narration.path("available").asBoolean(), is(true));
        assertThat(narration.path("aiGenerated").asBoolean(), is(true));
        assertThat(narration.path("label").asText(), containsString("AI-generated"));
        assertThat(narration.path("text").asText(), is(LLM_SUMMARY));
        assertThat(narration.path("provider").asText(), is("OPENAI"));
        assertThat(narration.path("model").asText(), is("gpt-4o-mini"));
        assertThat(narrated.path("matchedExpectation").path("expectationId").asText(), is("low"));
        assertThat(narrated.path("skippedExpectations"), is(plain.path("skippedExpectations")));
        assertThat(plain.has("narration"), is(false));
        assertThat(llmRequests.size(), is(1));
        assertThat(llmRequests.get(0).getPath().getValue(), is("/v1/chat/completions"));
        assertThat(promptSent(0), containsString("\"skippedExpectations\""));
        assertThat(promptSent(0), containsString("\"high\""));
    }

    @Test
    public void shouldNotCallTheLlmWithoutNarrate() throws Exception {
        useMockedOpenAiEndpoint();
        addTenantExpectations();
        recordMatched("n2", request().withMethod("GET").withPath("/orders"));

        JsonNode plain = explain("n2", false);

        assertThat(plain.has("narration"), is(false));
        assertThat(llmRequests, is(empty()));
    }

    @Test
    public void shouldSendNothingToTheLlmWhileNarrationIsDisabled() throws Exception {
        useMockedOpenAiEndpoint();
        addTenantExpectations();
        recordMatched("d1", request().withMethod("GET").withPath("/orders").withHeader("X-Tenant", "silver"));
        configuration.llmExplainNarrationEnabled(false);

        JsonNode disabled = explain("d1", true);

        assertThat(disabled.path("narration").path("available").asBoolean(), is(false));
        assertThat(disabled.path("narration").path("reason").asText(), containsString("llmExplainNarrationEnabled"));
        assertThat(disabled.path("matchedExpectation").path("expectationId").asText(), is("low"));
        assertThat(llmRequests, is(empty()));

        configuration.llmExplainNarrationEnabled(true);

        assertThat(explain("d1", true).path("narration").path("text").asText(), is(LLM_SUMMARY));
        assertThat(llmRequests.size(), is(1));
    }

    @Test
    public void shouldSayNarrationIsUnavailableWhenNoLlmIsConfigured() throws Exception {
        addTenantExpectations();
        recordMatched("n3", request().withMethod("GET").withPath("/orders").withHeader("X-Tenant", "silver"));

        JsonNode narrated = explain("n3", true);

        assertThat(narrated.path("narration").path("available").asBoolean(), is(false));
        assertThat(narrated.path("narration").path("reason").asText(), containsString("no LLM backend is configured"));
        assertThat(narrated.path("narration").has("text"), is(false));
        assertThat(narrated.path("matchedExpectation").path("expectationId").asText(), is("low"));
    }

    @Test
    public void shouldReturnAnExplainErrorUnchangedWithoutCallingTheLlm() throws Exception {
        useMockedOpenAiEndpoint();

        HttpResponse response = httpState.explainWithOptionalNarration(request("/mockserver/explain").withMethod("PUT")
            .withQueryStringParameter("correlationId", "never-recorded").withQueryStringParameter("narrate", "true")).get(10, TimeUnit.SECONDS);

        assertThat(response.getStatusCode(), is(404));
        assertThat(response.getBodyAsString(), not(containsString("narration")));
        assertThat(llmRequests, is(empty()));
    }

    @Test
    public void shouldNarrateExplainUnmatched() throws Exception {
        useMockedOpenAiEndpoint();
        addTenantExpectations();
        HttpRequest unmatched = request().withMethod("GET").withPath("/invoices");
        httpState.log(new LogEntry().setType(NO_MATCH_RESPONSE).setLogLevel(Level.INFO).setCorrelationId("u1")
            .setHttpRequest(unmatched).setHttpResponse(response().withStatusCode(404)));

        JsonNode narrated = awaitUnmatchedNarration();

        assertThat(narrated.path("unmatchedRequests").get(0).path("path").asText(), is("/invoices"));
        assertThat(narrated.path("narration").path("available").asBoolean(), is(true));
        assertThat(narrated.path("narration").path("text").asText(), is(LLM_SUMMARY));
        assertThat(promptSent(llmRequests.size() - 1), containsString("/invoices"));
    }

    @Test
    public void shouldRedactCredentialsFromThePromptThatTheFactsQuoteVerbatim() throws Exception {
        useMockedOpenAiEndpoint();
        httpState.add(
            new Expectation(request().withMethod("POST").withPath("/login").withHeader("Authorization", EXPECTATION_SECRET))
                .withId("needs-other-token").withPriority(10).thenRespond(response()),
            new Expectation(request().withMethod("POST").withPath("/login").withHeader("X-Trace", "expected-trace"))
                .withId("needs-trace").withPriority(5).thenRespond(response()),
            new Expectation(request().withMethod("POST").withPath("/login").withBody("{\"password\":\"other-password-value\"}"))
                .withId("needs-other-password").withPriority(4).thenRespond(response())
        );
        recordMatched("s1", request().withMethod("POST").withPath("/login")
            .withHeader("Authorization", AUTHORIZATION_SECRET)
            .withHeader("Cookie", "session=" + COOKIE_SECRET)
            .withHeader("X-Trace", JWT_SECRET)
            .withQueryStringParameter("access_token", QUERY_SECRET)
            .withBody("{\"username\":\"alice\",\"password\":\"" + PASSWORD_SECRET + "\"}"));

        String facts = objectMapper.writeValueAsString(explain("s1", false));
        JsonNode narrated = explain("s1", true);
        String prompt = promptSent(0);

        // with redactSecretsInLog off the deterministic facts quote the request's credentials, so redaction is what keeps them out
        assertThat(facts, containsString("sk-narration-authorization-secret"));
        assertThat(facts, containsString(PASSWORD_SECRET));
        assertThat(facts, containsString(JWT_SECRET));
        assertThat(narrated.path("narration").path("available").asBoolean(), is(true));
        assertThat(prompt, containsString("***REDACTED***"));
        assertThat(prompt, containsString("needs-other-token"));
        for (String secret : new String[]{"sk-narration-authorization-secret", "sk-narration-expectation-secret", PASSWORD_SECRET, COOKIE_SECRET, QUERY_SECRET, JWT_SECRET, "test-key"}) {
            assertThat("prompt leaks " + secret, prompt, not(containsString(secret)));
        }
    }

    // ---- helpers ----

    private void addTenantExpectations() {
        httpState.add(
            new Expectation(request().withMethod("GET").withPath("/orders").withHeader("X-Tenant", "gold"))
                .withId("high").withPriority(10).thenRespond(response()),
            new Expectation(request().withMethod("GET").withPath("/orders"))
                .withId("low").thenRespond(response())
        );
    }

    private String promptSent(int index) throws Exception {
        JsonNode body = objectMapper.readTree(llmRequests.get(index).getBodyAsString());
        return body.path("messages").get(0).path("content").asText();
    }

    private void recordMatched(String correlationId, HttpRequest request) {
        long arrival = httpState.getRequestMatchers().lastModifiedEpochMillis() + 1;
        httpState.log(new LogEntry().setType(RECEIVED_REQUEST).setLogLevel(Level.INFO).setCorrelationId(correlationId)
            .setEpochTime(arrival).setHttpRequest(request));
        httpState.log(new LogEntry().setType(EXPECTATION_RESPONSE).setLogLevel(Level.INFO).setCorrelationId(correlationId)
            .setEpochTime(arrival).setHttpRequest(request).setHttpResponse(response()).setExpectationId("low"));
    }

    /** The log is written asynchronously: retry a 404 until the recorded entries are visible, within a deadline. */
    private JsonNode explain(String correlationId, boolean narrate) throws Exception {
        HttpRequest explainRequest = request("/mockserver/explain").withMethod("PUT").withQueryStringParameter("correlationId", correlationId);
        if (narrate) {
            explainRequest.withQueryStringParameter("narrate", "true");
        }
        long deadline = System.currentTimeMillis() + 5000;
        HttpResponse response = httpState.explainWithOptionalNarration(explainRequest).get(10, TimeUnit.SECONDS);
        while (response.getStatusCode() == 404 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            response = httpState.explainWithOptionalNarration(explainRequest).get(10, TimeUnit.SECONDS);
        }
        assertThat(response.getBodyAsString(), response.getStatusCode(), is(200));
        return objectMapper.readTree(response.getBodyAsString());
    }

    private JsonNode awaitUnmatchedNarration() throws Exception {
        HttpRequest explainRequest = request("/mockserver/explainUnmatched").withMethod("PUT").withQueryStringParameter("narrate", "true");
        long deadline = System.currentTimeMillis() + 5000;
        JsonNode result = objectMapper.readTree(httpState.explainUnmatchedWithOptionalNarration(explainRequest).get(10, TimeUnit.SECONDS).getBodyAsString());
        while (result.path("unmatchedRequestCount").asInt() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            result = objectMapper.readTree(httpState.explainUnmatchedWithOptionalNarration(explainRequest).get(10, TimeUnit.SECONDS).getBodyAsString());
        }
        return result;
    }
}
