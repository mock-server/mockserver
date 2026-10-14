package mockserver_test

import (
	"encoding/json"
	"net/http"
	"os"
	"testing"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

// correlationIDOf returns the correlation ID of the received request with the
// given method and path, read from the server's log entries.
func correlationIDOf(t *testing.T, client *mockserver.Client, method, path string) string {
	t.Helper()
	raw, err := client.Retrieve(nil, mockserver.RetrieveLogs, mockserver.FormatLogEntries)
	if err != nil {
		t.Fatalf("Retrieve log entries failed: %v", err)
	}
	var entries []struct {
		Type          string `json:"type"`
		CorrelationID string `json:"correlationId"`
		HTTPRequest   struct {
			Method string `json:"method"`
			Path   string `json:"path"`
		} `json:"httpRequest"`
	}
	if err := json.Unmarshal(raw, &entries); err != nil {
		t.Fatalf("log entries: %v", err)
	}
	for _, e := range entries {
		if e.Type == "RECEIVED_REQUEST" && e.HTTPRequest.Method == method && e.HTTPRequest.Path == path && e.CorrelationID != "" {
			return e.CorrelationID
		}
	}
	t.Fatalf("no received %s %s in the log", method, path)
	return ""
}

func TestIntegration_Diagnostics(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)
	defer mustReset(t, client)

	if _, err := client.Upsert(
		mockserver.Expectation{ID: "diag-gold", Priority: 10,
			HttpRequest:  mockserver.Request().Method("GET").Path("/diag-orders").Header("X-Tenant", "gold").BuildPtr(),
			HttpResponse: mockserver.Response().StatusCode(200).BuildPtr()},
		mockserver.Expectation{ID: "diag-catch-all",
			HttpRequest:  mockserver.Request().Method("GET").Path("/diag-orders").BuildPtr(),
			HttpResponse: mockserver.Response().StatusCode(200).BuildPtr()},
	); err != nil {
		t.Fatalf("Upsert failed: %v", err)
	}

	served := newReq(t, "/diag-orders")
	served.Header.Set("X-Tenant", "silver")
	if got := send(t, served); got != 200 {
		t.Fatalf("GET /diag-orders returned %d", got)
	}
	unmatched, err := http.NewRequest("POST", os.Getenv("MOCKSERVER_URL")+"/diag-order", nil)
	if err != nil {
		t.Fatal(err)
	}
	if got := send(t, unmatched); got != 404 {
		t.Fatalf("POST /diag-order returned %d", got)
	}

	explanation, err := client.Explain(correlationIDOf(t, client, "GET", "/diag-orders"))
	if err != nil {
		t.Fatalf("Explain failed: %v", err)
	}
	if explanation.MatchedExpectation == nil || explanation.MatchedExpectation.ExpectationID != "diag-catch-all" {
		t.Errorf("matchedExpectation = %+v", explanation.MatchedExpectation)
	}
	if len(explanation.SkippedExpectations) != 1 || explanation.SkippedExpectations[0].ExpectationID != "diag-gold" ||
		explanation.SkippedExpectations[0].Reason != mockserver.ExplainReasonFieldsDiffer ||
		len(explanation.SkippedExpectations[0].Differences["headers"]) == 0 {
		t.Errorf("skippedExpectations = %+v", explanation.SkippedExpectations)
	}
	if explanation.AtArrival == nil || explanation.AtArrival.Outcome != mockserver.ArrivalMatched {
		t.Errorf("atArrival = %+v", explanation.AtArrival)
	}

	if _, err := client.Explain("no-such-correlation-id"); err == nil {
		t.Error("Explain of an unknown correlation ID returned no error")
	}

	report, err := client.DebugMismatch(mockserver.Request().Method("GET").Path("/diag-orders").Header("X-Tenant", "silver"))
	if err != nil {
		t.Fatalf("DebugMismatch failed: %v", err)
	}
	if report.TotalExpectations != 2 || len(report.Results) != 2 || report.ClosestMatch == nil ||
		report.ClosestMatch.ExpectationID != "diag-gold" {
		t.Errorf("report = %+v", report)
	}

	result, err := client.ExplainUnmatched(&mockserver.ExplainUnmatchedOptions{Limit: 5, SuggestFixes: true})
	if err != nil {
		t.Fatalf("ExplainUnmatched failed: %v", err)
	}
	if len(result.UnmatchedRequests) != 1 || result.UnmatchedRequests[0].Path != "/diag-order" ||
		len(result.UnmatchedRequests[0].ClosestExpectations) != 2 {
		t.Fatalf("unmatched = %+v", result)
	}
	if fix := result.UnmatchedRequests[0].ClosestExpectations[0].SuggestedFix; fix == nil || len(fix.Patch) == 0 {
		t.Errorf("closest expectation has no suggested fix: %+v", result.UnmatchedRequests[0].ClosestExpectations[0])
	}
}
