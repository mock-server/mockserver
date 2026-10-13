package org.mockserver.llm;

import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;

import java.util.List;

/**
 * Builds the prompt sent to a runtime LLM backend to generate a realistic
 * MockServer expectation from an unmatched request. The prompt includes the
 * unmatched request details and a sample of existing expectations as context
 * so the LLM can infer the API style.
 * <p>
 * The prompt leaves the process to an external LLM service, so all credentials
 * are redacted from it (always on, independent of {@code redactSecretsInLog}) via
 * {@link LlmPromptRedactor}: sensitive request/context headers, JSON body fields,
 * JWTs, {@code key=value} pairs in non-JSON bodies and URL userinfo. Redaction
 * works on copies — the served request, the event log and the returned
 * expectations are never mutated.
 */
public class StubGenerationPromptBuilder {

    private static final int MAX_EXPECTATIONS_CONTEXT = 10;

    private final LlmPromptRedactor redactor;

    public StubGenerationPromptBuilder() {
        this(null);
    }

    /**
     * @param dataPlaneApiKeyHeaderName the configured data-plane API-key header name to
     *                                  redact in addition to the default sensitive set,
     *                                  or {@code null}/blank when none is configured
     */
    public StubGenerationPromptBuilder(String dataPlaneApiKeyHeaderName) {
        this.redactor = new LlmPromptRedactor(dataPlaneApiKeyHeaderName);
    }

    public String build(HttpRequest unmatchedRequest, List<Expectation> contextExpectations) {
        HttpRequest redactedRequest = redactor.redactHeadersAndQuery(unmatchedRequest);
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an API mock server assistant. Given an HTTP request that has no matching stub, ");
        prompt.append("generate a realistic MockServer expectation JSON that would be a plausible response for this request.\n\n");

        prompt.append("UNMATCHED REQUEST:\n");
        prompt.append("Method: ").append(methodOrDefault(unmatchedRequest)).append("\n");
        prompt.append("Path: ").append(redactor.maskPath(pathOrDefault(unmatchedRequest))).append("\n");
        if (redactedRequest.getQueryStringParameterList() != null && !redactedRequest.getQueryStringParameterList().isEmpty()) {
            prompt.append("Query Parameters: ").append(redactedRequest.getQueryStringParameterList()).append("\n");
        }
        if (redactedRequest.getHeaderList() != null && !redactedRequest.getHeaderList().isEmpty()) {
            prompt.append("Headers: ").append(redactedRequest.getHeaderList()).append("\n");
        }
        redactor.redactBodyForPrompt(unmatchedRequest).ifPresent(body ->
            prompt.append("Request Body: ").append(body).append("\n"));
        prompt.append("\n");

        if (contextExpectations != null && !contextExpectations.isEmpty()) {
            prompt.append("EXISTING EXPECTATIONS (for context on the API style):\n");
            int count = Math.min(contextExpectations.size(), MAX_EXPECTATIONS_CONTEXT);
            for (int i = 0; i < count; i++) {
                Expectation e = contextExpectations.get(i);
                if (e.getHttpRequest() instanceof HttpRequest httpReq) {
                    prompt.append("- ").append(methodOrDefault(httpReq))
                        .append(" ").append(redactor.maskPath(pathOrDefault(httpReq))).append("\n");
                }
            }
            prompt.append("\n");
        }

        prompt.append("Generate a valid MockServer expectation JSON. Return ONLY the JSON, no explanation.\n");
        prompt.append("The expectation should:\n");
        prompt.append("1. Match the exact method and path from the unmatched request\n");
        prompt.append("2. Return a realistic HTTP response with appropriate status code (200 for GET, 201 for POST, etc.)\n");
        prompt.append("3. Include a plausible JSON response body if it looks like a REST API\n");
        prompt.append("4. Use httpRequest/httpResponse structure per MockServer format\n\n");
        prompt.append("Return the JSON expectation object only.");

        return prompt.toString();
    }

    private static String methodOrDefault(HttpRequest request) {
        if (request.getMethod() != null && request.getMethod().getValue() != null
            && !request.getMethod().getValue().isEmpty()) {
            return request.getMethod().getValue();
        }
        return "GET";
    }

    private static String pathOrDefault(HttpRequest request) {
        if (request.getPath() != null && request.getPath().getValue() != null
            && !request.getPath().getValue().isEmpty()) {
            return request.getPath().getValue();
        }
        return "/";
    }
}
