package mockserver

import (
	"encoding/json"
	"strings"
	"testing"
)

// Responses below are what the server returned for these calls, with the
// long per-field difference text shortened.

const explainMatchedJSON = `{
  "correlationId" : "9d3a95f5-1b5c-4f73-8d4a-98096f4950ab",
  "timestamp" : "2026-10-13T23:31:57.947590Z",
  "recomputed" : true,
  "note" : "recomputed now against the current expectations",
  "request" : { "timestamp" : "2026-10-14 00:31:48.871", "method" : "GET", "path" : "/orders" },
  "atArrival" : { "outcome" : "MATCHED", "expectationId" : "catch-all", "expectationStillStored" : true },
  "expectationsChangedSince" : false,
  "expectationsLastChanged" : "2026-10-13T23:31:48.848Z",
  "sameOutcomeAsAtArrival" : true,
  "matchedExpectation" : {
    "expectationId" : "catch-all", "expectationPath" : "/orders", "expectationMethod" : "GET",
    "matches" : true, "matchedFieldCount" : 18, "totalFieldCount" : 18, "differingFieldCount" : 0,
    "position" : 2, "priority" : 0
  },
  "skippedExpectations" : [ {
    "expectationId" : "gold-tenant", "expectationPath" : "/orders", "expectationMethod" : "GET",
    "matches" : false, "matchedFieldCount" : 17, "totalFieldCount" : 18, "differingFieldCount" : 1,
    "differences" : { "headers" : [ "expected gold found silver", "multimap is not a subset" ] },
    "remediation" : { "headers" : "check request headers" },
    "position" : 1, "priority" : 10, "reason" : "FIELDS_DIFFER",
    "explanation" : "the request differs on: headers"
  } ],
  "shadowedExpectations" : [ ],
  "totalExpectations" : 3,
  "evaluatedExpectations" : 3,
  "truncated" : false
}`

func TestClient_Explain_SendsCorrelationIDAndParsesExplanation(t *testing.T) {
	var cap captured
	ts := cpStub(t, 200, explainMatchedJSON, &cap)
	defer ts.Close()

	explanation, err := NewFromURL(ts.URL).Explain("a b&c")
	if err != nil {
		t.Fatal(err)
	}
	if cap.method != "PUT" || cap.path != "/mockserver/explain" {
		t.Fatalf("sent %s %s", cap.method, cap.path)
	}
	if got := mustQuery(t, cap.rawQuery).Get("correlationId"); got != "a b&c" {
		t.Errorf("correlationId query = %q", got)
	}
	if len(cap.body) != 0 {
		t.Errorf("explain sent a body: %q", cap.body)
	}

	if explanation.CorrelationID != "9d3a95f5-1b5c-4f73-8d4a-98096f4950ab" || !explanation.Recomputed {
		t.Errorf("header fields = %+v", explanation)
	}
	if explanation.Request == nil || explanation.Request.Method != "GET" || explanation.Request.Path != "/orders" {
		t.Errorf("request = %+v", explanation.Request)
	}
	arrival := explanation.AtArrival
	if arrival == nil || arrival.Outcome != ArrivalMatched || arrival.ExpectationID != "catch-all" ||
		arrival.ExpectationStillStored == nil || !*arrival.ExpectationStillStored {
		t.Errorf("atArrival = %+v", arrival)
	}
	if explanation.SameOutcomeAsAtArrival == nil || !*explanation.SameOutcomeAsAtArrival {
		t.Errorf("sameOutcomeAsAtArrival = %v", explanation.SameOutcomeAsAtArrival)
	}
	matched := explanation.MatchedExpectation
	if matched == nil || matched.ExpectationID != "catch-all" || matched.Position != 2 || matched.Reason != "" ||
		matched.Matches == nil || !*matched.Matches {
		t.Errorf("matchedExpectation = %+v", matched)
	}
	if len(explanation.SkippedExpectations) != 1 {
		t.Fatalf("skippedExpectations = %+v", explanation.SkippedExpectations)
	}
	skipped := explanation.SkippedExpectations[0]
	if skipped.ExpectationID != "gold-tenant" || skipped.Priority != 10 || skipped.Reason != ExplainReasonFieldsDiffer ||
		skipped.Explanation != "the request differs on: headers" || *skipped.DifferingFieldCount != 1 {
		t.Errorf("skipped = %+v", skipped)
	}
	if got := skipped.Differences["headers"]; len(got) != 2 || got[1] != "multimap is not a subset" {
		t.Errorf("differences = %v", skipped.Differences)
	}
	if skipped.Remediation["headers"] != "check request headers" {
		t.Errorf("remediation = %v", skipped.Remediation)
	}
	if len(explanation.ShadowedExpectations) != 0 {
		t.Errorf("shadowed = %v", explanation.ShadowedExpectations)
	}
	if explanation.TotalExpectations != 3 || explanation.EvaluatedExpectations != 3 || explanation.Truncated ||
		explanation.MaxExpectationsEvaluated != nil {
		t.Errorf("counts = %+v", explanation)
	}
}

func TestClient_Explain_KeepsAReasonThisClientDoesNotList(t *testing.T) {
	var cap captured
	ts := cpStub(t, 200, `{"skippedExpectations":[{"expectationId":"x","reason":"SOMETHING_NEW"}],
		"atArrival":{"outcome":"LATER_OUTCOME"},"truncated":true,"maxExpectationsEvaluated":500}`, &cap)
	defer ts.Close()

	explanation, err := NewFromURL(ts.URL).Explain("id")
	if err != nil {
		t.Fatal(err)
	}
	if explanation.SkippedExpectations[0].Reason != "SOMETHING_NEW" || explanation.AtArrival.Outcome != "LATER_OUTCOME" {
		t.Errorf("unknown enum values not kept: %+v", explanation)
	}
	if !explanation.Truncated || explanation.MaxExpectationsEvaluated == nil || *explanation.MaxExpectationsEvaluated != 500 {
		t.Errorf("truncation = %v %v", explanation.Truncated, explanation.MaxExpectationsEvaluated)
	}
}

func TestClient_Explain_BlankCorrelationIDSendsNothing(t *testing.T) {
	var cap captured
	ts := cpStub(t, 200, explainMatchedJSON, &cap)
	defer ts.Close()

	if _, err := NewFromURL(ts.URL).Explain("  "); err == nil {
		t.Fatal("expected an error for a blank correlation ID")
	}
	if cap.method != "" {
		t.Errorf("a request was sent: %s %s", cap.method, cap.path)
	}
}

func TestClient_Explain_UnknownCorrelationIDReturnsServerReason(t *testing.T) {
	var cap captured
	ts := cpStub(t, 404, `{"error":"no request is recorded with this correlationId","correlationId":"nope"}`, &cap)
	defer ts.Close()

	explanation, err := NewFromURL(ts.URL).Explain("nope")
	if err == nil || explanation != nil {
		t.Fatalf("expected an error, got %+v", explanation)
	}
	if !strings.Contains(err.Error(), "status 404") || !strings.Contains(err.Error(), "no request is recorded with this correlationId") ||
		strings.Contains(err.Error(), `"error"`) {
		t.Errorf("error = %v", err)
	}
}

const debugMismatchJSON = `{
  "correlationId" : "9d9509ef-e568-4add-9a7d-b5544ade6703",
  "timestamp" : "2026-10-13T23:31:58.028874Z",
  "totalExpectations" : 3,
  "evaluatedExpectations" : 3,
  "closestMatch" : { "expectationId" : "gold-tenant", "matchedFields" : 17, "totalFields" : 18 },
  "results" : [ {
    "expectationId" : "gold-tenant", "expectationPath" : "/orders", "expectationMethod" : "GET",
    "matches" : false, "matchedFieldCount" : 17, "totalFieldCount" : 18,
    "differences" : { "headers" : [ "expected gold found silver" ] }
  }, {
    "expectationId" : "catch-all", "expectationPath" : "/orders", "expectationMethod" : "GET",
    "matches" : true, "matchedFieldCount" : 18, "totalFieldCount" : 18
  } ]
}`

func TestClient_DebugMismatch_SendsRequestAndParsesReport(t *testing.T) {
	var cap captured
	ts := cpStub(t, 200, debugMismatchJSON, &cap)
	defer ts.Close()

	report, err := NewFromURL(ts.URL).DebugMismatch(Request().Method("GET").Path("/orders").Header("X-Tenant", "silver"))
	if err != nil {
		t.Fatal(err)
	}
	if cap.method != "PUT" || cap.path != "/mockserver/debugMismatch" || cap.rawQuery != "" {
		t.Fatalf("sent %s %s?%s", cap.method, cap.path, cap.rawQuery)
	}
	var sent map[string]interface{}
	if err := json.Unmarshal(cap.body, &sent); err != nil {
		t.Fatalf("body %q: %v", cap.body, err)
	}
	if sent["method"] != "GET" || sent["path"] != "/orders" || sent["headers"] == nil {
		t.Errorf("body = %s", cap.body)
	}

	if report.TotalExpectations != 3 || report.EvaluatedExpectations != 3 || report.Truncated {
		t.Errorf("counts = %+v", report)
	}
	if report.ClosestMatch == nil || report.ClosestMatch.ExpectationID != "gold-tenant" ||
		report.ClosestMatch.MatchedFields != 17 || report.ClosestMatch.TotalFields != 18 {
		t.Errorf("closestMatch = %+v", report.ClosestMatch)
	}
	if len(report.Results) != 2 {
		t.Fatalf("results = %+v", report.Results)
	}
	first, second := report.Results[0], report.Results[1]
	if first.Matches == nil || *first.Matches || *first.MatchedFieldCount != 17 || first.Differences["headers"][0] != "expected gold found silver" {
		t.Errorf("first = %+v", first)
	}
	if second.Matches == nil || !*second.Matches || second.Differences != nil {
		t.Errorf("second = %+v", second)
	}
}

func TestClient_DebugMismatch_RejectedDefinitionReturnsServerReason(t *testing.T) {
	var cap captured
	ts := cpStub(t, 400, `{"error":"debugMismatch only supports HttpRequest definitions"}`, &cap)
	defer ts.Close()

	if _, err := NewFromURL(ts.URL).DebugMismatch(nil); err == nil ||
		!strings.Contains(err.Error(), "debugMismatch only supports HttpRequest definitions") {
		t.Fatalf("error = %v", err)
	}
	if len(cap.body) != 0 {
		t.Errorf("a nil request sent a body: %q", cap.body)
	}
}

const explainUnmatchedJSON = `{
  "correlationId" : "39ee8eb8-13d7-4c36-8826-cc909088f775",
  "timestamp" : "2026-10-13T23:32:11.645636Z",
  "unmatchedRequestCount" : 1,
  "truncated" : false,
  "unmatchedRequests" : [ {
    "timestamp" : "2026-10-14 00:32:11.573",
    "method" : "POST",
    "path" : "/api/order",
    "closestExpectations" : [ {
      "expectationId" : "create-order", "expectationPath" : "/api/orders", "expectationMethod" : "POST",
      "matches" : false, "matchedFieldCount" : 16, "totalFieldCount" : 18, "differingFieldCount" : 2,
      "differences" : { "path" : [ "expected /api/orders found /api/order" ], "headers" : [ "expected blue found green" ] },
      "remediation" : { "path" : "check the request path", "headers" : "check request headers" },
      "suggestedFix" : {
        "patch" : [
          { "op" : "test", "path" : "/httpRequest/path", "value" : "/api/orders" },
          { "op" : "replace", "path" : "/httpRequest/path", "value" : "/api/order" },
          { "op" : "test", "path" : "/httpRequest/headers/X-Tenant", "value" : [ "blue" ] },
          { "op" : "replace", "path" : "/httpRequest/headers/X-Tenant", "value" : [ "green" ] }
        ],
        "patchedFields" : [ "path", "headers" ],
        "unpatchedFields" : [ { "field" : "jwt", "reason" : "edit it by hand" } ],
        "matchesAfterPatch" : true
      }
    } ],
    "totalExpectationsEvaluated" : 3
  } ]
}`

func TestClient_ExplainUnmatched_SendsOptionsAndParsesSuggestedFixes(t *testing.T) {
	var cap captured
	ts := cpStub(t, 200, explainUnmatchedJSON, &cap)
	defer ts.Close()

	result, err := NewFromURL(ts.URL).ExplainUnmatched(&ExplainUnmatchedOptions{Limit: 5, SuggestFixes: true})
	if err != nil {
		t.Fatal(err)
	}
	if cap.method != "PUT" || cap.path != "/mockserver/explainUnmatched" {
		t.Fatalf("sent %s %s", cap.method, cap.path)
	}
	if string(cap.body) != `{"limit":5,"suggestFixes":true}` {
		t.Errorf("body = %s", cap.body)
	}

	if result.UnmatchedRequestCount != 1 || result.Truncated || len(result.UnmatchedRequests) != 1 {
		t.Fatalf("result = %+v", result)
	}
	unmatched := result.UnmatchedRequests[0]
	if unmatched.Method != "POST" || unmatched.Path != "/api/order" || unmatched.TotalExpectationsEvaluated != 3 ||
		len(unmatched.ClosestExpectations) != 1 {
		t.Fatalf("unmatched = %+v", unmatched)
	}
	closest := unmatched.ClosestExpectations[0]
	if closest.ExpectationID != "create-order" || *closest.DifferingFieldCount != 2 ||
		closest.Remediation["path"] != "check the request path" {
		t.Errorf("closest = %+v", closest.ExplainedExpectation)
	}
	fix := closest.SuggestedFix
	if fix == nil || !fix.MatchesAfterPatch || len(fix.Patch) != 4 || strings.Join(fix.PatchedFields, ",") != "path,headers" {
		t.Fatalf("suggestedFix = %+v", fix)
	}
	if fix.Patch[3].Op != "replace" || fix.Patch[3].Path != "/httpRequest/headers/X-Tenant" {
		t.Errorf("patch[3] = %+v", fix.Patch[3])
	}
	var value []string
	if err := json.Unmarshal(fix.Patch[3].Value, &value); err != nil || len(value) != 1 || value[0] != "green" {
		t.Errorf("patch[3].value = %s (%v)", fix.Patch[3].Value, err)
	}
	if len(fix.UnpatchedFields) != 1 || fix.UnpatchedFields[0].Field != "jwt" || fix.UnpatchedFields[0].Reason != "edit it by hand" {
		t.Errorf("unpatchedFields = %+v", fix.UnpatchedFields)
	}
}

func TestClient_ExplainUnmatched_NilOptionsSendNoBody(t *testing.T) {
	var cap captured
	ts := cpStub(t, 200, `{"unmatchedRequestCount":0,"truncated":false,"unmatchedRequests":[]}`, &cap)
	defer ts.Close()

	result, err := NewFromURL(ts.URL).ExplainUnmatched(nil)
	if err != nil {
		t.Fatal(err)
	}
	if len(cap.body) != 0 {
		t.Errorf("body = %q", cap.body)
	}
	if result.UnmatchedRequestCount != 0 || len(result.UnmatchedRequests) != 0 {
		t.Errorf("result = %+v", result)
	}
}

func TestClient_ExplainUnmatched_ServerFailureIsAnError(t *testing.T) {
	var cap captured
	ts := cpStub(t, 500, `{"error":"boom"}`, &cap)
	defer ts.Close()

	if _, err := NewFromURL(ts.URL).ExplainUnmatched(nil); err == nil || !strings.Contains(err.Error(), "status 500") {
		t.Fatalf("error = %v", err)
	}
}
