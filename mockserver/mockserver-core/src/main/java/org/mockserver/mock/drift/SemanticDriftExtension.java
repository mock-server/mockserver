package org.mockserver.mock.drift;

import org.mockserver.llm.LlmPromptRedactor;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.llm.client.LlmBackend;
import org.mockserver.llm.client.LlmCompletionService;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Optional LLM-powered extension to {@link DriftAnalyzer}. When a configured
 * LLM backend is available, classifies each structural drift record as
 * BREAKING / WARNING / INFORMATIONAL and adds a one-sentence explanation.
 * <p>
 * Called asynchronously on the drift-analysis scheduler thread — never blocks
 * the HTTP response path. Enrichment is best-effort: any LLM failure leaves
 * the drift records with their original structural-only data.
 */
public class SemanticDriftExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(SemanticDriftExtension.class);
    private static final int MAX_BODY_LENGTH = 200;

    private final LlmCompletionService completionService;
    private final LlmBackend backend;
    // Redacts credentials before the drift prompt (response bodies + drift values)
    // leaves the process to the external LLM.
    private final LlmPromptRedactor redactor;

    public SemanticDriftExtension(LlmCompletionService completionService, LlmBackend backend) {
        this(completionService, backend, null);
    }

    /**
     * @param dataPlaneApiKeyHeaderName the configured data-plane API-key header name, whose
     *                                  drift values are masked in addition to the default
     *                                  sensitive headers, or {@code null}/blank when none is configured
     */
    public SemanticDriftExtension(LlmCompletionService completionService, LlmBackend backend, String dataPlaneApiKeyHeaderName) {
        this.completionService = completionService;
        this.backend = backend;
        this.redactor = new LlmPromptRedactor(dataPlaneApiKeyHeaderName);
    }

    /**
     * @return true if both the completion service and backend are configured.
     */
    public boolean isAvailable() {
        return completionService != null && backend != null;
    }

    /**
     * Enriches drift records with semantic severity and explanation using the LLM.
     * Must not block the response path — called asynchronously.
     */
    public void enrich(List<DriftRecord> records, String expectationId,
                       HttpResponse stubResponse, HttpResponse realResponse) {
        if (!isAvailable() || records.isEmpty()) {
            return;
        }
        try {
            String prompt = buildPrompt(records, stubResponse, realResponse);
            ParsedConversation conversation = ParsedConversation.of(Collections.singletonList(
                new ParsedMessage(ParsedMessage.Role.USER, prompt, null, null)
            ));
            Optional<Completion> result = completionService.complete(backend, conversation);
            if (result.isPresent() && result.get().getText() != null) {
                applySemanticResults(records, result.get().getText());
            }
        } catch (Exception e) {
            // Semantic enrichment is best-effort — never fail the drift pipeline
            LOGGER.debug("semantic drift enrichment failed for expectation {}: {}", expectationId, e.getMessage());
        }
    }

    // visible for testing
    String buildPrompt(List<DriftRecord> records, HttpResponse stub, HttpResponse real) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an API compatibility expert. Classify each API drift as BREAKING, WARNING, or INFORMATIONAL.\n\n");
        sb.append("STUB RESPONSE: status=").append(stub != null ? stub.getStatusCode() : "unknown")
            .append(", body=").append(truncate(redactor.redactText(stub != null ? stub.getBodyAsText() : null))).append("\n");
        sb.append("REAL RESPONSE: status=").append(real != null ? real.getStatusCode() : "unknown")
            .append(", body=").append(truncate(redactor.redactText(real != null ? real.getBodyAsText() : null))).append("\n\n");
        sb.append("DRIFT RECORDS:\n");
        for (int i = 0; i < records.size(); i++) {
            DriftRecord r = records.get(i);
            sb.append(i + 1).append(". ").append(r.getDriftType()).append(" on field '").append(r.getField())
                .append("': expected=").append(redactor.maskDriftValue(r.getField(), r.getExpectedValue()))
                .append(", actual=").append(redactor.maskDriftValue(r.getField(), r.getActualValue())).append("\n");
        }
        sb.append("\nFor each numbered drift, reply with: <number>|<BREAKING|WARNING|INFORMATIONAL>|<one sentence explanation>\n");
        sb.append("Return exactly one line per drift.");
        return sb.toString();
    }

    private String truncate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() > MAX_BODY_LENGTH ? s.substring(0, MAX_BODY_LENGTH) + "..." : s;
    }

    // visible for testing
    void applySemanticResults(List<DriftRecord> records, String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return;
        }
        String[] lines = llmResponse.strip().split("\n");
        for (String line : lines) {
            String[] parts = line.split("\\|", 3);
            if (parts.length < 2) {
                continue;
            }
            try {
                int idx = Integer.parseInt(parts[0].trim()) - 1;
                if (idx < 0 || idx >= records.size()) {
                    continue;
                }
                SemanticSeverity severity = SemanticSeverity.valueOf(parts[1].trim().toUpperCase());
                records.get(idx).setSemanticSeverity(severity);
                if (parts.length > 2 && !parts[2].isBlank()) {
                    records.get(idx).setSemanticExplanation(parts[2].trim());
                }
            } catch (IllegalArgumentException ignored) {
                // skip lines with unparseable index or unknown severity
            }
        }
    }
}
