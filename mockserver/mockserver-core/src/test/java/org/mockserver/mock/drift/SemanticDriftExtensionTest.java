package org.mockserver.mock.drift;

import org.junit.Test;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.client.LlmBackend;
import org.mockserver.llm.client.LlmCompletionService;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.model.HttpResponse.response;

public class SemanticDriftExtensionTest {

    @Test
    public void isAvailableReturnsFalseWhenNullService() {
        SemanticDriftExtension ext = new SemanticDriftExtension(null, null);
        assertThat(ext.isAvailable(), is(false));
    }

    @Test
    public void isAvailableReturnsFalseWhenNullBackend() {
        LlmCompletionService service = new StubLlmCompletionService(null);
        SemanticDriftExtension ext = new SemanticDriftExtension(service, null);
        assertThat(ext.isAvailable(), is(false));
    }

    @Test
    public void isAvailableReturnsTrueWhenBothPresent() {
        LlmCompletionService service = new StubLlmCompletionService(null);
        LlmBackend backend = LlmBackend.of(Provider.OPENAI, "test-key");
        SemanticDriftExtension ext = new SemanticDriftExtension(service, backend);
        assertThat(ext.isAvailable(), is(true));
    }

    @Test
    public void enrichIsNoOpWhenNotAvailable() {
        SemanticDriftExtension ext = new SemanticDriftExtension(null, null);
        List<DriftRecord> records = List.of(
            new DriftRecord().setDriftType(DriftType.STATUS).setField("statusCode")
        );
        // Should not throw
        ext.enrich(records, "exp1", response(), response());
        assertThat(records.get(0).getSemanticSeverity(), is(nullValue()));
    }

    @Test
    public void enrichIsNoOpWhenRecordsEmpty() {
        LlmCompletionService service = new StubLlmCompletionService("should not be called");
        LlmBackend backend = LlmBackend.of(Provider.OPENAI, "test-key");
        SemanticDriftExtension ext = new SemanticDriftExtension(service, backend);

        ext.enrich(new ArrayList<>(), "exp1", response(), response());
        // No assertion needed — just verifying no exception
    }

    @Test
    public void applySemanticResultsParsesThreeRecords() {
        LlmCompletionService service = new StubLlmCompletionService(null);
        LlmBackend backend = LlmBackend.of(Provider.OPENAI, "test-key");
        SemanticDriftExtension ext = new SemanticDriftExtension(service, backend);

        List<DriftRecord> records = new ArrayList<>();
        records.add(new DriftRecord().setDriftType(DriftType.STATUS).setField("statusCode"));
        records.add(new DriftRecord().setDriftType(DriftType.SCHEMA_FIELD_REMOVED).setField("$.role"));
        records.add(new DriftRecord().setDriftType(DriftType.HEADER_ADDED).setField("header.x-new"));

        String llmResponse = "1|BREAKING|Status code changed from 200 to 500\n" +
            "2|WARNING|Removed role field may break clients\n" +
            "3|INFORMATIONAL|New header is additive and backward compatible";

        ext.applySemanticResults(records, llmResponse);

        assertThat(records.get(0).getSemanticSeverity(), is(SemanticSeverity.BREAKING));
        assertThat(records.get(0).getSemanticExplanation(), is("Status code changed from 200 to 500"));
        assertThat(records.get(1).getSemanticSeverity(), is(SemanticSeverity.WARNING));
        assertThat(records.get(1).getSemanticExplanation(), is("Removed role field may break clients"));
        assertThat(records.get(2).getSemanticSeverity(), is(SemanticSeverity.INFORMATIONAL));
        assertThat(records.get(2).getSemanticExplanation(), is("New header is additive and backward compatible"));
    }

    @Test
    public void applySemanticResultsHandlesMalformedLines() {
        LlmCompletionService service = new StubLlmCompletionService(null);
        LlmBackend backend = LlmBackend.of(Provider.OPENAI, "test-key");
        SemanticDriftExtension ext = new SemanticDriftExtension(service, backend);

        List<DriftRecord> records = new ArrayList<>();
        records.add(new DriftRecord().setDriftType(DriftType.STATUS).setField("statusCode"));

        String llmResponse = "not a valid line\n" +
            "abc|BREAKING|bad index\n" +
            "99|BREAKING|out of range\n" +
            "1|INVALID_SEVERITY|bad severity";

        ext.applySemanticResults(records, llmResponse);

        // All lines should be skipped due to parsing issues
        assertThat(records.get(0).getSemanticSeverity(), is(nullValue()));
    }

    @Test
    public void applySemanticResultsHandlesNullAndBlankResponse() {
        LlmCompletionService service = new StubLlmCompletionService(null);
        LlmBackend backend = LlmBackend.of(Provider.OPENAI, "test-key");
        SemanticDriftExtension ext = new SemanticDriftExtension(service, backend);

        List<DriftRecord> records = new ArrayList<>();
        records.add(new DriftRecord().setDriftType(DriftType.STATUS));

        ext.applySemanticResults(records, null);
        assertThat(records.get(0).getSemanticSeverity(), is(nullValue()));

        ext.applySemanticResults(records, "   ");
        assertThat(records.get(0).getSemanticSeverity(), is(nullValue()));
    }

    @Test
    public void buildPromptIncludesDriftRecordsAndTruncatesLongBodies() {
        LlmCompletionService service = new StubLlmCompletionService(null);
        LlmBackend backend = LlmBackend.of(Provider.OPENAI, "test-key");
        SemanticDriftExtension ext = new SemanticDriftExtension(service, backend);

        List<DriftRecord> records = List.of(
            new DriftRecord().setDriftType(DriftType.STATUS).setField("statusCode")
                .setExpectedValue("200").setActualValue("500")
        );

        String longBody = "x".repeat(300);
        HttpResponse stub = response().withStatusCode(200).withBody(longBody);
        HttpResponse real = response().withStatusCode(500).withBody("{\"error\":\"fail\"}");

        String prompt = ext.buildPrompt(records, stub, real);

        assertThat(prompt, containsString("STATUS on field 'statusCode'"));
        assertThat(prompt, containsString("expected=200"));
        assertThat(prompt, containsString("actual=500"));
        // Long body should be truncated
        assertThat(prompt, containsString("..."));
        // Real body should appear fully
        assertThat(prompt, containsString("error"));
    }

    @Test
    public void enrichCallsLlmAndAppliesResults() {
        String llmResponse = "1|BREAKING|Critical status code change";
        LlmCompletionService service = new StubLlmCompletionService(llmResponse);
        LlmBackend backend = LlmBackend.of(Provider.OPENAI, "test-key");
        SemanticDriftExtension ext = new SemanticDriftExtension(service, backend);

        List<DriftRecord> records = new ArrayList<>();
        records.add(new DriftRecord().setDriftType(DriftType.STATUS).setField("statusCode")
            .setExpectedValue("200").setActualValue("500"));

        ext.enrich(records, "exp1", response().withStatusCode(200), response().withStatusCode(500));

        assertThat(records.get(0).getSemanticSeverity(), is(SemanticSeverity.BREAKING));
        assertThat(records.get(0).getSemanticExplanation(), is("Critical status code change"));
    }

    @Test
    public void enrichHandlesLlmException() {
        LlmCompletionService service = new ThrowingLlmCompletionService();
        LlmBackend backend = LlmBackend.of(Provider.OPENAI, "test-key");
        SemanticDriftExtension ext = new SemanticDriftExtension(service, backend);

        List<DriftRecord> records = new ArrayList<>();
        records.add(new DriftRecord().setDriftType(DriftType.STATUS).setField("statusCode"));

        // Should not throw
        ext.enrich(records, "exp1", response(), response());
        assertThat(records.get(0).getSemanticSeverity(), is(nullValue()));
    }

    @Test
    public void buildPromptRedactsCredentialsInBodiesAndValues() {
        LlmCompletionService service = new StubLlmCompletionService(null);
        LlmBackend backend = LlmBackend.of(Provider.OPENAI, "test-key");
        SemanticDriftExtension ext = new SemanticDriftExtension(service, backend);

        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhYmMifQ.abcDEF123_-xyzSIG";
        List<DriftRecord> records = List.of(
            new DriftRecord().setDriftType(DriftType.SCHEMA_TYPE_CHANGED).setField("access_token")
                .setExpectedValue("old-secret-token").setActualValue("new-secret-token")
        );
        HttpResponse stub = response().withStatusCode(200).withBody("{\"token\":\"stub-token-secret\"}");
        HttpResponse real = response().withStatusCode(200).withBody("{\"note\":\"" + jwt + "\"}");

        String prompt = ext.buildPrompt(records, stub, real);

        assertThat(prompt, not(containsString("stub-token-secret")));
        assertThat(prompt, not(containsString("old-secret-token")));
        assertThat(prompt, not(containsString("new-secret-token")));
        assertThat(prompt, not(containsString(jwt)));
        assertThat(prompt, containsString("***REDACTED***"));
    }

    @Test
    public void buildPromptMasksKeyValueCredentialInDriftValueOfNonCredentialField() {
        SemanticDriftExtension ext = new SemanticDriftExtension(new StubLlmCompletionService(null), LlmBackend.of(Provider.OPENAI, "test-key"));
        List<DriftRecord> records = List.of(
            new DriftRecord().setDriftType(DriftType.HEADER_CHANGED).setField("header.x-debug")
                .setExpectedValue("user=bob token=stub-kv-secret")
                .setActualValue("user=bob api_key=live-kv-secret")
        );

        String prompt = ext.buildPrompt(records, response().withStatusCode(200), response().withStatusCode(200));

        assertThat(prompt, not(containsString("stub-kv-secret")));
        assertThat(prompt, not(containsString("live-kv-secret")));
        assertThat(prompt, containsString("expected=user=bob token=***REDACTED***"));
        assertThat(prompt, containsString("actual=user=bob api_key=***REDACTED***"));
    }

    @Test
    public void buildPromptMasksDriftValuesOfSensitiveResponseHeaders() {
        SemanticDriftExtension ext = new SemanticDriftExtension(new StubLlmCompletionService(null), LlmBackend.of(Provider.OPENAI, "test-key"));
        List<DriftRecord> records = List.of(
            new DriftRecord().setDriftType(DriftType.HEADER_CHANGED).setField("header.set-cookie")
                .setExpectedValue("SESSIONID=stub-cookie-secret; Path=/")
                .setActualValue("SESSIONID=live-cookie-secret; Path=/"),
            new DriftRecord().setDriftType(DriftType.HEADER_ADDED).setField("header.X-Auth-Token")
                .setActualValue("opaque-auth-secret"),
            new DriftRecord().setDriftType(DriftType.HEADER_CHANGED).setField("header.content-type")
                .setExpectedValue("application/json").setActualValue("text/plain")
        );

        String prompt = ext.buildPrompt(records, response().withStatusCode(200), response().withStatusCode(200));

        assertThat(prompt, not(containsString("stub-cookie-secret")));
        assertThat(prompt, not(containsString("live-cookie-secret")));
        assertThat(prompt, not(containsString("opaque-auth-secret")));
        assertThat(prompt, containsString("'header.set-cookie': expected=***REDACTED***, actual=***REDACTED***"));
        assertThat(prompt, containsString("'header.content-type': expected=application/json, actual=text/plain"));
    }

    @Test
    public void buildPromptMasksAuthorizationCodeInRedirectDriftValues() {
        SemanticDriftExtension ext = new SemanticDriftExtension(new StubLlmCompletionService(null), LlmBackend.of(Provider.OPENAI, "test-key"));
        List<DriftRecord> records = List.of(
            new DriftRecord().setDriftType(DriftType.HEADER_CHANGED).setField("header.location")
                .setExpectedValue("https://app.example/callback?code=stub-code-secret&state=s1")
                .setActualValue("https://app.example/return?state=s2&code=live-code-secret"),
            new DriftRecord().setDriftType(DriftType.HEADER_CHANGED).setField("header.x-filter")
                .setExpectedValue("code=GB").setActualValue("code=FR")
        );

        String prompt = ext.buildPrompt(records, response().withStatusCode(302), response().withStatusCode(302));

        assertThat(prompt, not(containsString("stub-code-secret")));
        assertThat(prompt, not(containsString("live-code-secret")));
        assertThat(prompt, containsString("expected=https://app.example/callback?code=***REDACTED***&state=s1"));
        assertThat(prompt, containsString("actual=https://app.example/return?state=s2&code=***REDACTED***"));
        assertThat(prompt, containsString("'header.x-filter': expected=code=GB, actual=code=FR"));
    }

    @Test
    public void buildPromptMasksAuthorizationCodeInResponseBodies() {
        SemanticDriftExtension ext = new SemanticDriftExtension(new StubLlmCompletionService(null), LlmBackend.of(Provider.OPENAI, "test-key"));
        List<DriftRecord> records = List.of(
            new DriftRecord().setDriftType(DriftType.STATUS).setField("statusCode")
        );

        String prompt = ext.buildPrompt(records,
            response().withStatusCode(200).withBody("Continue to https://app.example/cb?code=stub-body-code&state=s1"),
            response().withStatusCode(200).withBody("{\"redirect_uri\":\"https://app.example/cb\",\"code\":\"live-body-code\"}"));

        assertThat(prompt, not(containsString("stub-body-code")));
        assertThat(prompt, not(containsString("live-body-code")));
    }

    @Test
    public void buildPromptMasksDriftValuesOfConfiguredDataPlaneApiKeyHeader() {
        SemanticDriftExtension ext = new SemanticDriftExtension(new StubLlmCompletionService(null), LlmBackend.of(Provider.OPENAI, "test-key"), "X-Tenant-Key");
        List<DriftRecord> records = List.of(
            new DriftRecord().setDriftType(DriftType.HEADER_ADDED).setField("header.x-tenant-key")
                .setActualValue("tenant-header-secret")
        );

        String prompt = ext.buildPrompt(records, response().withStatusCode(200), response().withStatusCode(200));

        assertThat(prompt, not(containsString("tenant-header-secret")));
        assertThat(prompt, containsString("'header.x-tenant-key': expected=null, actual=***REDACTED***"));
    }

    /**
     * Stub that returns a fixed completion text for any prompt.
     */
    private static class StubLlmCompletionService extends LlmCompletionService {
        private final String responseText;

        StubLlmCompletionService(String responseText) {
            super(null);
            this.responseText = responseText;
        }

        @Override
        public Optional<Completion> complete(LlmBackend backend, ParsedConversation prompt) {
            if (responseText == null) {
                return Optional.empty();
            }
            return Optional.of(Completion.completion().withText(responseText));
        }
    }

    /**
     * Stub that always throws on complete().
     */
    private static class ThrowingLlmCompletionService extends LlmCompletionService {
        ThrowingLlmCompletionService() {
            super(null);
        }

        @Override
        public Optional<Completion> complete(LlmBackend backend, ParsedConversation prompt) {
            throw new RuntimeException("LLM connection failed");
        }
    }
}
