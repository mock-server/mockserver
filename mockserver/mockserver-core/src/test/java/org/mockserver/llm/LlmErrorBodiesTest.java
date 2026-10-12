package org.mockserver.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.model.Provider;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Unit tests for {@link LlmErrorBodies} — verifies each provider produces the
 * correct provider-specific overload / rate-limit / server-error JSON envelope,
 * that the {@link LlmErrorBodies.Kind} is derived from the HTTP status, and that
 * an unknown/null provider returns {@code null} (so callers use the generic
 * fallback body). Each provider body is parsed to assert its structure rather
 * than substring-matched.
 */
public class LlmErrorBodiesTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static JsonNode parse(String body) {
        try {
            return OBJECT_MAPPER.readTree(body);
        } catch (Exception e) {
            throw new AssertionError("body is not valid JSON: " + body, e);
        }
    }

    // ---- kindForStatus ----

    @Test
    public void kindForStatusMapsKnownCodes() {
        assertThat(LlmErrorBodies.kindForStatus(429), is(LlmErrorBodies.Kind.RATE_LIMIT));
        assertThat(LlmErrorBodies.kindForStatus(529), is(LlmErrorBodies.Kind.OVERLOADED));
        assertThat(LlmErrorBodies.kindForStatus(503), is(LlmErrorBodies.Kind.SERVER_ERROR));
        assertThat(LlmErrorBodies.kindForStatus(500), is(LlmErrorBodies.Kind.SERVER_ERROR));
        assertThat(LlmErrorBodies.kindForStatus(400), is(LlmErrorBodies.Kind.SERVER_ERROR));
        assertThat(LlmErrorBodies.kindForStatus(null), is(LlmErrorBodies.Kind.SERVER_ERROR));
    }

    // ---- Anthropic ----

    @Test
    public void anthropicOverloadedBody() {
        JsonNode body = parse(LlmErrorBodies.bodyFor(Provider.ANTHROPIC, LlmErrorBodies.Kind.OVERLOADED, 529, "Overloaded"));
        assertThat(body.get("type").asText(), is("error"));
        assertThat(body.get("error").get("type").asText(), is("overloaded_error"));
        assertThat(body.get("error").get("message").asText(), is("Overloaded"));
    }

    @Test
    public void anthropicRateLimitBody() {
        JsonNode body = parse(LlmErrorBodies.bodyFor(Provider.ANTHROPIC, LlmErrorBodies.Kind.RATE_LIMIT, 429, "too many requests"));
        assertThat(body.get("type").asText(), is("error"));
        assertThat(body.get("error").get("type").asText(), is("rate_limit_error"));
    }

    @Test
    public void anthropicServerErrorBody() {
        JsonNode body = parse(LlmErrorBodies.bodyFor(Provider.ANTHROPIC, LlmErrorBodies.Kind.SERVER_ERROR, 500, "boom"));
        assertThat(body.get("error").get("type").asText(), is("api_error"));
    }

    @Test
    public void bedrockUsesTheAwsErrorShape() {
        JsonNode body = parse(LlmErrorBodies.bodyFor(Provider.BEDROCK, LlmErrorBodies.Kind.OVERLOADED, 529, "Overloaded"));
        assertThat(body.get("__type").asText(), is("ServiceUnavailableException"));
        assertThat(body.get("message").asText(), is("Overloaded"));
    }

    // ---- OpenAI family ----

    @Test
    public void openAiRateLimitBody() {
        JsonNode body = parse(LlmErrorBodies.bodyFor(Provider.OPENAI, LlmErrorBodies.Kind.RATE_LIMIT, 429, "Rate limit reached"));
        assertThat(body.get("error").get("type").asText(), is("rate_limit_exceeded"));
        assertThat(body.get("error").get("code").asText(), is("rate_limit_exceeded"));
        assertThat(body.get("error").get("message").asText(), is("Rate limit reached"));
        assertThat(body.get("error").get("param").isNull(), is(true));
    }

    @Test
    public void openAiServerErrorBodyEchoesNumericCode() {
        JsonNode body = parse(LlmErrorBodies.bodyFor(Provider.OPENAI, LlmErrorBodies.Kind.SERVER_ERROR, 503, "The server is overloaded"));
        assertThat(body.get("error").get("type").asText(), is("server_error"));
        assertThat(body.get("error").get("code").asInt(), is(503));
    }

    @Test
    public void openAiOverloadedMapsToServerError() {
        JsonNode body = parse(LlmErrorBodies.bodyFor(Provider.OPENAI, LlmErrorBodies.Kind.OVERLOADED, 503, "overloaded"));
        assertThat(body.get("error").get("type").asText(), is("server_error"));
        assertThat(body.get("error").get("code").asInt(), is(503));
    }

    @Test
    public void openAiResponsesAndAzureUseSameShape() {
        JsonNode responses = parse(LlmErrorBodies.bodyFor(Provider.OPENAI_RESPONSES, LlmErrorBodies.Kind.RATE_LIMIT, 429, "x"));
        JsonNode azure = parse(LlmErrorBodies.bodyFor(Provider.AZURE_OPENAI, LlmErrorBodies.Kind.RATE_LIMIT, 429, "x"));
        assertThat(responses.get("error").get("type").asText(), is("rate_limit_exceeded"));
        assertThat(azure.get("error").get("type").asText(), is("rate_limit_exceeded"));
    }

    // ---- Gemini ----

    @Test
    public void geminiBodies() {
        JsonNode overloaded = parse(LlmErrorBodies.bodyFor(Provider.GEMINI, LlmErrorBodies.Kind.OVERLOADED, 503, "unavailable"));
        assertThat(overloaded.get("error").get("code").asInt(), is(503));
        assertThat(overloaded.get("error").get("status").asText(), is("UNAVAILABLE"));

        JsonNode rateLimit = parse(LlmErrorBodies.bodyFor(Provider.GEMINI, LlmErrorBodies.Kind.RATE_LIMIT, 429, "exhausted"));
        assertThat(rateLimit.get("error").get("status").asText(), is("RESOURCE_EXHAUSTED"));

        JsonNode serverError = parse(LlmErrorBodies.bodyFor(Provider.GEMINI, LlmErrorBodies.Kind.SERVER_ERROR, 500, "internal"));
        assertThat(serverError.get("error").get("status").asText(), is("INTERNAL"));
    }

    // ---- Ollama ----

    @Test
    public void ollamaBodyIsPlainMessage() {
        JsonNode body = parse(LlmErrorBodies.bodyFor(Provider.OLLAMA, LlmErrorBodies.Kind.SERVER_ERROR, 500, "model not loaded"));
        assertThat(body.get("error").asText(), is("model not loaded"));
    }

    // ---- Unknown / null provider ----

    @Test
    public void nullProviderReturnsNull() {
        assertThat(LlmErrorBodies.bodyFor(null, LlmErrorBodies.Kind.OVERLOADED, 529, "x"), is(nullValue()));
    }

    // ---- Escaping ----

    @Test
    public void messageWithQuotesIsEscapedAndValidJson() {
        String body = LlmErrorBodies.bodyFor(Provider.OPENAI, LlmErrorBodies.Kind.RATE_LIMIT, 429, "say \"hi\"\nand bye");
        JsonNode parsed = parse(body); // must not throw
        assertThat(parsed.get("error").get("message").asText(), is("say \"hi\"\nand bye"));
    }
}
