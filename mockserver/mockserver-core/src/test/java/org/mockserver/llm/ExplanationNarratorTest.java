package org.mockserver.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.llm.client.LlmBackend;
import org.mockserver.llm.client.LlmCompletionService;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.mockserver.model.Provider;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * The narration never holds up or replaces the deterministic answer: a slow, failing or saturated LLM gives a
 * "narration unavailable" reason within the timeout, and the facts are returned unchanged.
 */
public class ExplanationNarratorTest {

    private static final String FACTS = "{\"correlationId\":\"c1\",\"timestamp\":\"t\",\"matchedExpectation\":{\"expectationId\":\"low\"},\"skippedExpectations\":[]}";

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    private static HttpResponse facts() {
        return response().withStatusCode(200).withBody(FACTS, MediaType.JSON_UTF_8);
    }

    private static LlmBackend backendWithTimeout(long timeoutMillis) {
        return new LlmBackend(null, Provider.OPENAI, null, "key", null, null, timeoutMillis);
    }

    private static final class StubService extends LlmCompletionService {
        private final CountDownLatch release;
        private final String text;
        private final CountDownLatch started = new CountDownLatch(1);

        StubService(CountDownLatch release, String text) {
            super((org.mockserver.llm.client.LlmTransport) null);
            this.release = release;
            this.text = text;
        }

        @Override
        public Optional<Completion> complete(LlmBackend backend, ParsedConversation conversation) {
            started.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return text == null ? Optional.empty() : Optional.of(new Completion().withText(text));
        }
    }

    @Test
    public void shouldAnswerWithinTheTimeoutWhenTheLlmIsSlow() throws Exception {
        ExplanationNarrator narrator = new ExplanationNarrator(configuration().llmExplainNarrationEnabled(true), 100);
        CountDownLatch release = new CountDownLatch(1);
        try {
            long start = System.nanoTime();
            JsonNode result = body(narrator.narrate(facts(), Collections.emptyList(), new StubService(release, "late"), backendWithTimeout(200)));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(result.path("narration").path("available").asBoolean(), is(false));
            assertThat(result.path("narration").path("reason").asText(), containsString("did not answer within 300ms"));
            assertThat(result.path("matchedExpectation").path("expectationId").asText(), is("low"));
            assertThat(elapsedMillis, lessThan(5_000L));
        } finally {
            release.countDown();
        }
    }

    @Test
    public void shouldRefuseANarrationWhenEveryNarrationThreadIsBusy() throws Exception {
        ExplanationNarrator narrator = new ExplanationNarrator(configuration().llmExplainNarrationEnabled(true), 100);
        CountDownLatch release = new CountDownLatch(1);
        try {
            for (int i = 0; i < ExplanationNarrator.MAX_CONCURRENT_NARRATIONS; i++) {
                StubService busy = new StubService(release, "busy");
                narrator.narrate(facts(), Collections.emptyList(), busy, backendWithTimeout(20_000));
                assertThat(busy.started.await(10, TimeUnit.SECONDS), is(true));
            }

            JsonNode result = body(narrator.narrate(facts(), Collections.emptyList(), new StubService(release, "x"), backendWithTimeout(20_000)));

            assertThat(result.path("narration").path("available").asBoolean(), is(false));
            assertThat(result.path("narration").path("reason").asText(), containsString("already running"));
            assertThat(result.path("matchedExpectation").path("expectationId").asText(), is("low"));
        } finally {
            release.countDown();
        }
    }

    @Test
    public void shouldSayNarrationIsUnavailableWhenTheLlmReturnsNothing() throws Exception {
        ExplanationNarrator narrator = new ExplanationNarrator(configuration().llmExplainNarrationEnabled(true));
        CountDownLatch released = new CountDownLatch(0);

        JsonNode result = body(narrator.narrate(facts(), Collections.emptyList(), new StubService(released, null), backendWithTimeout(5_000)));

        assertThat(result.path("narration").path("available").asBoolean(), is(false));
        assertThat(result.path("narration").path("reason").asText(), containsString("returned no text"));
    }

    @Test
    public void shouldNotCallTheLlmUnlessTheOperatorEnablesNarration() throws Exception {
        ExplanationNarrator narrator = new ExplanationNarrator(configuration().llmExplainNarrationEnabled(false));
        StubService service = new StubService(new CountDownLatch(0), "should not be asked");

        JsonNode result = body(narrator.narrate(facts(), Collections.emptyList(), service, backendWithTimeout(5_000)));

        assertThat(result.path("narration").path("available").asBoolean(), is(false));
        assertThat(result.path("narration").path("reason").asText(), containsString("llmExplainNarrationEnabled"));
        assertThat(service.started.getCount(), is(1L));
    }

    @Test
    public void shouldMaskAJwtThatStraddlesTheStringCap() throws Exception {
        ExplanationNarrator narrator = new ExplanationNarrator(configuration().llmExplainNarrationEnabled(true));
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdHJhZGRsZSIsInJvbGUiOiJhZG1pbiJ9.c2lnbmF0dXJlLXN0cmFkZGxl";
        // the JWT starts 10 characters before the cut, so a cut made before redaction keeps "eyJhbGciOi" unmasked
        String prefix = "x".repeat(ExplanationNarrator.MAX_FACT_TEXT_CHARS - ExplanationNarrator.TRUNCATED.length() - 10 - " found:".length());
        com.fasterxml.jackson.databind.node.ObjectNode facts = objectMapper.createObjectNode();
        facts.putArray("skippedExpectations").addObject().put("explanation", prefix + " found:" + jwt);
        facts.putObject("matchedExpectation").put("explanation", "found:" + jwt);

        String prompt = narrator.buildPrompt(facts, Collections.emptyList());

        assertThat(prompt, containsString("found:***REDACTED***"));
        assertThat(prompt, not(containsString("eyJhbGciOi")));
    }

    @Test
    public void shouldMaskCredentialNamedFieldsAnywhereInTheFacts() {
        ExplanationNarrator narrator = new ExplanationNarrator(configuration().llmExplainNarrationEnabled(true));
        com.fasterxml.jackson.databind.node.ObjectNode facts = objectMapper.createObjectNode();
        facts.putObject("matchedExpectation").put("client_secret", "facts-field-secret-value");

        String prompt = narrator.buildPrompt(facts, Collections.emptyList());

        assertThat(prompt, containsString("\"client_secret\":\"***REDACTED***\""));
        assertThat(prompt, not(containsString("facts-field-secret-value")));
    }

    @Test
    public void shouldCapTheNarrationTextAtItsLimitIncludingTheMarker() throws Exception {
        ExplanationNarrator narrator = new ExplanationNarrator(configuration().llmExplainNarrationEnabled(true));

        JsonNode result = body(narrator.narrate(facts(), Collections.emptyList(), new StubService(new CountDownLatch(0), "y".repeat(10_000)), backendWithTimeout(5_000)));

        assertThat(result.path("narration").path("text").asText().length(), is(ExplanationNarrator.MAX_NARRATION_CHARS));
    }

    @Test
    public void shouldOnlyNarrateWhenNarrateIsTrue() {
        assertThat(ExplanationNarrator.requested(request().withQueryStringParameter("narrate", "true")), is(true));
        assertThat(ExplanationNarrator.requested(request().withQueryStringParameter("narrate", "false")), is(false));
        assertThat(ExplanationNarrator.requested(request()), is(false));
    }

    @Test
    public void shouldCapTheFactsSentToTheLlm() throws Exception {
        ExplanationNarrator narrator = new ExplanationNarrator(configuration().llmExplainNarrationEnabled(true));
        StringBuilder many = new StringBuilder("{\"skippedExpectations\":[");
        for (int i = 0; i < 2_000; i++) {
            many.append(i == 0 ? "" : ",").append("{\"expectationId\":\"e").append(i).append("\",\"explanation\":\"the request differs on: path\"}");
        }
        many.append("]}");

        String prompt = narrator.buildPrompt((com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(many.toString()), Collections.emptyList());

        assertThat(prompt.length(), lessThan(ExplanationNarrator.INSTRUCTIONS.length() + ExplanationNarrator.MAX_FACTS_CHARS + ExplanationNarrator.FACTS_TRUNCATED.length() + 1));
        assertThat(prompt, containsString(ExplanationNarrator.FACTS_TRUNCATED));
    }

    private JsonNode body(CompletableFuture<HttpResponse> narrated) throws Exception {
        HttpResponse response = narrated.get(10, TimeUnit.SECONDS);
        assertThat(response.getStatusCode(), is(200));
        return objectMapper.readTree(response.getBodyAsString());
    }
}
