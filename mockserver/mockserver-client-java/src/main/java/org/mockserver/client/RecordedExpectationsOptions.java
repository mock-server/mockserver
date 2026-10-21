package org.mockserver.client;

import org.mockserver.model.HttpRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Options that shape the expectations returned by
 * {@link MockServerClient#retrieveRecordedExpectations(org.mockserver.model.RequestDefinition, RecordedExpectationsOptions)}.
 * Each option is off unless set, so an empty options object returns the recorded expectations unchanged.
 */
public class RecordedExpectationsOptions {

    private Boolean consolidate;
    private Boolean parameterize;
    private Boolean repeatsAsScenarios;
    private List<String> captureHeaders;
    private Long extractBodiesLargerThan;

    public static RecordedExpectationsOptions recordedExpectationsOptions() {
        return new RecordedExpectationsOptions();
    }

    /**
     * Collapse recorded exchanges that share a request shape into one reusable, unlimited-times expectation.
     */
    public RecordedExpectationsOptions withConsolidate(Boolean consolidate) {
        this.consolidate = consolidate;
        return this;
    }

    /**
     * Also generalise volatile path, query, header and body values into matchers (implies consolidate).
     */
    public RecordedExpectationsOptions withParameterize(Boolean parameterize) {
        this.parameterize = parameterize;
        return this;
    }

    /**
     * When the same request was recorded several times with different responses, return one expectation that
     * replays those responses in the recorded order and then keeps returning the last one.
     */
    public RecordedExpectationsOptions withRepeatsAsScenarios(Boolean repeatsAsScenarios) {
        this.repeatsAsScenarios = repeatsAsScenarios;
        return this;
    }

    /**
     * Keep only these request headers (case-insensitive) in each recorded request matcher; no names keeps none,
     * {@code null} leaves the option unset.
     */
    public RecordedExpectationsOptions withCaptureHeaders(String... headerNames) {
        this.captureHeaders = headerNames != null ? new ArrayList<>(Arrays.asList(headerNames)) : null;
        return this;
    }

    /**
     * Write each response body larger than this many bytes to the server's configured
     * {@code recordedBodyFilesDirectory} and reference it as a FILE body.
     */
    public RecordedExpectationsOptions withExtractBodiesLargerThan(Long thresholdBytes) {
        this.extractBodiesLargerThan = thresholdBytes;
        return this;
    }

    public Boolean getConsolidate() {
        return consolidate;
    }

    public Boolean getParameterize() {
        return parameterize;
    }

    public Boolean getRepeatsAsScenarios() {
        return repeatsAsScenarios;
    }

    public List<String> getCaptureHeaders() {
        return captureHeaders;
    }

    public Long getExtractBodiesLargerThan() {
        return extractBodiesLargerThan;
    }

    HttpRequest addTo(HttpRequest request) {
        if (Boolean.TRUE.equals(consolidate)) {
            request.withQueryStringParameter("consolidate", "true");
        }
        if (Boolean.TRUE.equals(parameterize)) {
            request.withQueryStringParameter("parameterize", "true");
        }
        if (Boolean.TRUE.equals(repeatsAsScenarios)) {
            request.withQueryStringParameter("repeatsAsScenarios", "true");
        }
        if (captureHeaders != null) {
            request.withQueryStringParameter("captureHeaders", String.join(",", captureHeaders));
        }
        if (extractBodiesLargerThan != null) {
            request.withQueryStringParameter("extractBodiesLargerThan", String.valueOf(extractBodiesLargerThan));
        }
        return request;
    }
}
