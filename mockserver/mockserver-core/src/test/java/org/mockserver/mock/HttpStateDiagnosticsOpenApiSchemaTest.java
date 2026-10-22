package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.NO_MATCH_RESPONSE;
import static org.mockserver.model.AnyOfRequestDefinition.requestAnyOf;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * {@code PUT /mockserver/debugMismatch} and {@code PUT /mockserver/explainUnmatched} return exactly the fields their
 * OpenAPI response schemas document, with the documented types, and every documented field is returned by one of
 * the cases here. Each fills only part of the shared {@code ExplainedExpectation}, which its description lists.
 */
public class HttpStateDiagnosticsOpenApiSchemaTest {

    private HttpState httpState;
    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @After
    public void stopHttpState() {
        if (httpState != null) {
            httpState.stop();
        }
    }

    @Test
    public void debugMismatchReturnsExactlyTheDocumentedFields() throws Exception {
        start(configuration());
        httpState.add(
            new Expectation(request().withMethod("GET").withPath("/api/orders")).withId("match").thenRespond(response()),
            new Expectation(request().withMethod("GET").withPath("/api/order")).withId("near").thenRespond(response())
        );
        OpenApiResponseSchema schema = OpenApiResponseSchema.of("/mockserver/debugMismatch", "200");
        schema.assertDocuments(debugMismatch(200));

        List<Expectation> many = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            many.add(new Expectation(request().withPath("/many/" + i)).withId("m" + i).thenRespond(response()));
        }
        httpState.add(many.toArray(new Expectation[0]));
        JsonNode truncated = debugMismatch(200);
        assertThat(truncated.path("truncated").asBoolean(), is(true));
        schema.assertDocuments(truncated);

        // anyOfBranchesAreDocumentedForEveryDiagnostic covers AnyOfBranchComparison
        assertThat("documented but never returned", schema.documentedButNeverReturned(path -> path.startsWith("ExplainedExpectation/") || path.startsWith("SuggestedFix/")
                || path.startsWith("AnyOfBranchComparison/")),
            is(Collections.emptyList()));
        assertThat(explainedExpectationFields(schema), is(fields("expectationId", "expectationMethod", "expectationPath", "matches",
            "matchedFieldCount", "totalFieldCount", "differences", "differences{}")));
    }

    @Test
    public void debugMismatchFailureReturnsExactlyTheDocumentedFields() throws Exception {
        start(configuration());
        OpenApiResponseSchema schema = OpenApiResponseSchema.of("/mockserver/debugMismatch", "400");

        HttpResponse response = httpState.debugMismatch(request().withMethod("PUT").withBody("{"));

        assertThat(response.getStatusCode(), is(400));
        schema.assertDocuments(objectMapper.readTree(response.getBodyAsString()));
        assertThat("documented but never returned", schema.documentedButNeverReturned(path -> false), is(Collections.emptyList()));
    }

    @Test
    public void explainUnmatchedReturnsExactlyTheDocumentedFields() throws Exception {
        start(configuration().redactSecretsInLog(true));
        httpState.add(
            new Expectation(request().withMethod("GET").withPath("/api/order")).withId("near").thenRespond(response()),
            new Expectation(request().withMethod("GET").withPath("/api/orders").withHeader("Authorization", "Bearer expected-value"))
                .withId("auth").thenRespond(response())
        );
        logUnmatched(request().withMethod("GET").withPath("/api/orders").withHeader("Authorization", "Bearer s3cr3t-request-token"));
        OpenApiResponseSchema schema = OpenApiResponseSchema.of("/mockserver/explainUnmatched", "200");

        JsonNode withFixes = explainUnmatched("{\"limit\":10,\"suggestFixes\":true}");
        assertThat(withFixes.path("unmatchedRequests").get(0).path("closestExpectations").size(), is(2));
        schema.assertDocuments(withFixes);
        schema.assertDocuments(explainUnmatched("{\"limit\":10}"));
        httpState.getConfiguration().llmExplainNarrationEnabled(true);
        schema.assertDocuments(narratedExplainUnmatched());
        httpState.setLlmCompletionService(new org.mockserver.llm.client.LlmCompletionService((llmRequest, timeoutMillis) -> response().withStatusCode(200)
                .withBody("{\"model\":\"gpt-4o-mini\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"summary\"}}]}")),
            org.mockserver.llm.client.LlmBackend.of(org.mockserver.model.Provider.OPENAI, "key"));
        schema.assertDocuments(narratedExplainUnmatched());

        assertThat("documented but never returned", schema.documentedButNeverReturned(path -> path.startsWith("ExplainedExpectation/")
                || path.startsWith("AnyOfBranchComparison/")),
            is(Collections.emptyList()));
        assertThat(explainedExpectationFields(schema), is(fields("expectationId", "expectationMethod", "expectationPath", "matches",
            "matchedFieldCount", "totalFieldCount", "differingFieldCount", "differences", "differences{}",
            "remediation", "remediation{}", "suggestedFix")));
    }

    @Test
    public void anyOfBranchesAreDocumentedForEveryDiagnostic() throws Exception {
        start(configuration());
        httpState.add(new Expectation(requestAnyOf(
            request().withMethod("POST").withPath("/api/other"),
            request().withMethod("GET").withPath("/api/order")
        )).withId("any-of").thenRespond(response()));
        logUnmatched(request().withMethod("GET").withPath("/api/orders"));

        OpenApiResponseSchema debugMismatch = OpenApiResponseSchema.of("/mockserver/debugMismatch", "200");
        JsonNode mismatch = debugMismatch(200);
        debugMismatch.assertDocuments(mismatch);
        assertThat(mismatch.path("results").get(0).path("anyOf").path("closestBranch").asInt(), is(1));
        OpenApiResponseSchema explainUnmatched = OpenApiResponseSchema.of("/mockserver/explainUnmatched", "200");
        explainUnmatched.assertDocuments(explainUnmatched("{\"limit\":10}"));

        for (OpenApiResponseSchema schema : new OpenApiResponseSchema[]{debugMismatch, explainUnmatched}) {
            assertThat("documented but never returned", schema.documentedButNeverReturned(path -> !path.startsWith("AnyOfBranchComparison/")),
                is(Collections.emptyList()));
            assertThat(explainedExpectationFields(schema).contains("anyOf"), is(true));
        }
    }

    private static Set<String> explainedExpectationFields(OpenApiResponseSchema schema) {
        return schema.returned().stream().filter(path -> path.startsWith("ExplainedExpectation/"))
            .map(path -> path.substring("ExplainedExpectation/".length())).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> fields(String... names) {
        return new TreeSet<>(Arrays.asList(names));
    }

    private void start(Configuration configuration) {
        MockServerLogger logger = new MockServerLogger(configuration, HttpStateDiagnosticsOpenApiSchemaTest.class);
        httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true));
    }

    private JsonNode debugMismatch(int expectedStatus) throws Exception {
        HttpResponse response = httpState.debugMismatch(request().withMethod("PUT").withBody("{\"method\":\"GET\",\"path\":\"/api/orders\"}"));
        assertThat(response.getStatusCode(), is(expectedStatus));
        return objectMapper.readTree(response.getBodyAsString());
    }

    private JsonNode explainUnmatched(String body) throws Exception {
        HttpResponse response = httpState.explainUnmatched(request().withMethod("PUT").withBody(body));
        assertThat(response.getStatusCode(), is(200));
        return objectMapper.readTree(response.getBodyAsString());
    }

    private JsonNode narratedExplainUnmatched() throws Exception {
        HttpResponse response = httpState.explainUnmatchedWithOptionalNarration(request().withMethod("PUT")
            .withQueryStringParameter("narrate", "true").withBody("{\"limit\":10}")).get(10, TimeUnit.SECONDS);
        assertThat(response.getBodyAsString(), response.getStatusCode(), is(200));
        return objectMapper.readTree(response.getBodyAsString());
    }

    private void logUnmatched(HttpRequest unmatched) throws Exception {
        httpState.log(new LogEntry()
            .setType(NO_MATCH_RESPONSE)
            .setLogLevel(org.slf4j.event.Level.INFO)
            .setHttpRequest(unmatched)
            .setHttpResponse(response().withStatusCode(404))
            .setMessageFormat("no expectation for:{}returning response:{}")
            .setArguments(unmatched, response().withStatusCode(404))
        );
        long deadline = System.currentTimeMillis() + 20_000;
        while (explainUnmatched("{\"limit\":10}").path("unmatchedRequestCount").asInt() < 1) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timed out waiting for the unmatched request to be recorded");
            }
            Thread.sleep(50);
        }
    }
}
