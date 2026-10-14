package mockserver

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
)

// --- Request Builder JSON Tests ---

func TestRequestBuilder_Method(t *testing.T) {
	req := Request().Method("POST").Build()
	if req.Method != "POST" {
		t.Errorf("expected method POST, got %s", req.Method)
	}
}

func TestRequestBuilder_Path(t *testing.T) {
	req := Request().Path("/api/users").Build()
	if req.Path != "/api/users" {
		t.Errorf("expected path /api/users, got %s", req.Path)
	}
}

func TestRequestBuilder_QueryStringParameter(t *testing.T) {
	req := Request().QueryStringParameter("q", "search", "term").Build()
	vals := req.QueryStringParams["q"]
	if len(vals) != 2 || vals[0] != "search" || vals[1] != "term" {
		t.Errorf("unexpected query params: %v", vals)
	}
}

func TestRequestBuilder_Header(t *testing.T) {
	req := Request().Header("Accept", "application/json").Build()
	vals := req.Headers["Accept"]
	if len(vals) != 1 || vals[0] != "application/json" {
		t.Errorf("unexpected headers: %v", vals)
	}
}

func TestRequestBuilder_Cookie(t *testing.T) {
	req := Request().Cookie("session", "abc123").Build()
	if req.Cookies["session"] != "abc123" {
		t.Errorf("unexpected cookie: %v", req.Cookies)
	}
}

func TestRequestBuilder_Body(t *testing.T) {
	req := Request().Body("hello world").Build()
	if req.Body != "hello world" {
		t.Errorf("unexpected body: %v", req.Body)
	}
}

func TestRequestBuilder_JSONBody(t *testing.T) {
	req := Request().JSONBody(`{"key":"value"}`).Build()
	tb, ok := req.Body.(*TypedBody)
	if !ok {
		t.Fatalf("expected *TypedBody, got %T", req.Body)
	}
	if tb.Type != "JSON" {
		t.Errorf("expected type JSON, got %s", tb.Type)
	}
	if tb.JSON != `{"key":"value"}` {
		t.Errorf("unexpected json body: %s", tb.JSON)
	}
}

func TestRequestBuilder_JSON(t *testing.T) {
	req := Request().
		Method("GET").
		Path("/hello").
		QueryStringParameter("q", "x").
		Header("Authorization", "Bearer token").
		Build()

	data, err := json.Marshal(req)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}

	if m["method"] != "GET" {
		t.Errorf("json method: %v", m["method"])
	}
	if m["path"] != "/hello" {
		t.Errorf("json path: %v", m["path"])
	}
	qsp := m["queryStringParameters"].(map[string]interface{})
	if qsp["q"] == nil {
		t.Error("missing queryStringParameters.q")
	}
}

// --- Response Builder JSON Tests ---

func TestResponseBuilder_StatusCode(t *testing.T) {
	resp := Response().StatusCode(201).Build()
	if resp.StatusCode != 201 {
		t.Errorf("expected 201, got %d", resp.StatusCode)
	}
}

func TestResponseBuilder_Body(t *testing.T) {
	resp := Response().Body("hello").Build()
	if resp.Body != "hello" {
		t.Errorf("expected body hello, got %v", resp.Body)
	}
}

func TestResponseBuilder_Header(t *testing.T) {
	resp := Response().Header("X-Custom", "val1", "val2").Build()
	vals := resp.Headers["X-Custom"]
	if len(vals) != 2 || vals[0] != "val1" || vals[1] != "val2" {
		t.Errorf("unexpected headers: %v", vals)
	}
}

func TestResponseBuilder_JSONBody(t *testing.T) {
	resp := Response().JSONBody(`{"result":true}`).Build()
	if resp.Body != `{"result":true}` {
		t.Errorf("unexpected body: %v", resp.Body)
	}
	ct := resp.Headers["Content-Type"]
	if len(ct) != 1 || ct[0] != "application/json" {
		t.Errorf("expected application/json header, got %v", ct)
	}
}

func TestResponseBuilder_Delay(t *testing.T) {
	resp := Response().WithDelay("MILLISECONDS", 500).Build()
	if resp.Delay == nil {
		t.Fatal("expected delay to be set")
	}
	if resp.Delay.TimeUnit != "MILLISECONDS" || resp.Delay.Value != 500 {
		t.Errorf("unexpected delay: %+v", resp.Delay)
	}
}

// --- Forward Builder Tests ---

func TestForwardBuilder_JSON(t *testing.T) {
	fwd := Forward().Host("backend.local").Port(8080).Scheme("HTTPS").Build()
	data, err := json.Marshal(fwd)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}

	if m["host"] != "backend.local" {
		t.Errorf("unexpected host: %v", m["host"])
	}
	if m["port"] != float64(8080) {
		t.Errorf("unexpected port: %v", m["port"])
	}
	if m["scheme"] != "HTTPS" {
		t.Errorf("unexpected scheme: %v", m["scheme"])
	}
}

// --- Expectation JSON Tests ---

func TestExpectation_JSON(t *testing.T) {
	req := Request().Method("GET").Path("/hello").Build()
	resp := Response().StatusCode(200).Body("world").Build()
	exp := Expectation{
		HttpRequest:  &req,
		HttpResponse: &resp,
		Times:        Once(),
		TimeToLive:   UnlimitedTTL(),
		Priority:     5,
	}

	data, err := json.Marshal(exp)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}

	if m["priority"] != float64(5) {
		t.Errorf("expected priority 5, got %v", m["priority"])
	}

	times := m["times"].(map[string]interface{})
	if times["remainingTimes"] != float64(1) {
		t.Errorf("unexpected remainingTimes: %v", times["remainingTimes"])
	}
	if times["unlimited"] != false {
		t.Errorf("unexpected unlimited: %v", times["unlimited"])
	}

	ttl := m["timeToLive"].(map[string]interface{})
	if ttl["unlimited"] != true {
		t.Errorf("unexpected ttl unlimited: %v", ttl["unlimited"])
	}
}

// --- Verification JSON Tests ---

func TestVerification_JSON(t *testing.T) {
	req := Request().Method("GET").Path("/hello").Build()
	v := verification{
		HttpRequest: &req,
		Times:       AtLeast(2),
	}

	data, err := json.Marshal(v)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}

	times := m["times"].(map[string]interface{})
	if times["atLeast"] != float64(2) {
		t.Errorf("expected atLeast 2, got %v", times["atLeast"])
	}
}

func TestVerificationSequence_JSON(t *testing.T) {
	r1 := Request().Path("/first").Build()
	r2 := Request().Path("/second").Build()
	vs := verificationSequence{HttpRequests: []HttpRequest{r1, r2}}

	data, err := json.Marshal(vs)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}

	reqs := m["httpRequests"].([]interface{})
	if len(reqs) != 2 {
		t.Errorf("expected 2 requests, got %d", len(reqs))
	}
}

// --- Client URL Shaping Tests (httptest mock server) ---

func TestClient_Upsert_URLAndBody(t *testing.T) {
	var receivedPath string
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedPath = r.URL.Path
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(201)
		w.Write([]byte("[]"))
	}))
	defer ts.Close()

	u, _ := url.Parse(ts.URL)
	client := NewFromURL(ts.URL)
	_ = u

	req := Request().Method("GET").Path("/test").Build()
	resp := Response().StatusCode(200).Build()
	exp := Expectation{HttpRequest: &req, HttpResponse: &resp}

	_, err := client.Upsert(exp)
	if err != nil {
		t.Fatal(err)
	}

	if receivedPath != "/mockserver/expectation" {
		t.Errorf("expected path /mockserver/expectation, got %s", receivedPath)
	}

	// Verify the body is valid JSON array of expectations
	var exps []map[string]interface{}
	if err := json.Unmarshal(receivedBody, &exps); err != nil {
		t.Fatalf("body is not valid JSON: %s", err)
	}
	if len(exps) != 1 {
		t.Errorf("expected 1 expectation, got %d", len(exps))
	}
}

func TestClient_Verify_URL(t *testing.T) {
	var receivedPath string
	var receivedMethod string

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedPath = r.URL.Path
		receivedMethod = r.Method
		w.WriteHeader(202)
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.Verify(Request().Path("/hello"), AtLeast(1))
	if err != nil {
		t.Fatal(err)
	}

	if receivedPath != "/mockserver/verify" {
		t.Errorf("expected path /mockserver/verify, got %s", receivedPath)
	}
	if receivedMethod != "PUT" {
		t.Errorf("expected method PUT, got %s", receivedMethod)
	}
}

func TestClient_RetrieveExpectationsAsCode_URLAndFormat(t *testing.T) {
	var receivedPath string
	var receivedQuery string
	generated := "new MockServerClient().when(request().withPath(\"/hello\"));"

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedPath = r.URL.Path
		receivedQuery = r.URL.RawQuery
		w.WriteHeader(200)
		w.Write([]byte(generated))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	code, err := client.RetrieveExpectationsAsCode(nil, FormatJava)
	if err != nil {
		t.Fatal(err)
	}

	if code != generated {
		t.Errorf("expected generated code %q, got %q", generated, code)
	}
	if receivedPath != "/mockserver/retrieve" {
		t.Errorf("expected path /mockserver/retrieve, got %s", receivedPath)
	}
	if !strings.Contains(receivedQuery, "type=active_expectations") {
		t.Errorf("expected type=active_expectations in query, got %s", receivedQuery)
	}
	if !strings.Contains(receivedQuery, "format=java") {
		t.Errorf("expected format=java in query, got %s", receivedQuery)
	}
}

func TestClient_RetrieveRecordedExpectationsAsCode_URLAndFormat(t *testing.T) {
	var receivedQuery string

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedQuery = r.URL.RawQuery
		w.WriteHeader(200)
		w.Write([]byte("# generated python"))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	code, err := client.RetrieveRecordedExpectationsAsCode(Request().Path("/hello"), FormatPython)
	if err != nil {
		t.Fatal(err)
	}

	if code != "# generated python" {
		t.Errorf("unexpected generated code %q", code)
	}
	if !strings.Contains(receivedQuery, "type=recorded_expectations") {
		t.Errorf("expected type=recorded_expectations in query, got %s", receivedQuery)
	}
	if !strings.Contains(receivedQuery, "format=python") {
		t.Errorf("expected format=python in query, got %s", receivedQuery)
	}
}

func TestClient_Verify_406ReturnsError(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(406)
		w.Write([]byte("Request not received"))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.Verify(Request().Path("/missing"), AtLeast(1))
	if err == nil {
		t.Fatal("expected error, got nil")
	}

	verErr, ok := err.(*VerificationError)
	if !ok {
		t.Fatalf("expected *VerificationError, got %T: %v", err, err)
	}
	if verErr.Message != "Request not received" {
		t.Errorf("unexpected message: %s", verErr.Message)
	}
}

func TestClient_VerifySequence_URL(t *testing.T) {
	var receivedPath string

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedPath = r.URL.Path
		w.WriteHeader(202)
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.VerifySequence(
		Request().Path("/first"),
		Request().Path("/second"),
	)
	if err != nil {
		t.Fatal(err)
	}

	if receivedPath != "/mockserver/verifySequence" {
		t.Errorf("expected path /mockserver/verifySequence, got %s", receivedPath)
	}
}

// --- Response Verification Tests ---

func TestVerification_WithResponse_JSON(t *testing.T) {
	req := Request().Method("GET").Path("/api").Build()
	resp := Response().StatusCode(200).Build()
	v := verification{
		HttpRequest:  &req,
		HttpResponse: &resp,
		Times:        AtLeast(1),
	}

	data, err := json.Marshal(v)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}

	if m["httpRequest"] == nil {
		t.Error("expected httpRequest in JSON")
	}
	if m["httpResponse"] == nil {
		t.Error("expected httpResponse in JSON")
	}
	httpResp := m["httpResponse"].(map[string]interface{})
	if httpResp["statusCode"] != float64(200) {
		t.Errorf("expected statusCode 200, got %v", httpResp["statusCode"])
	}
}

func TestVerification_ResponseOnly_JSON(t *testing.T) {
	resp := Response().StatusCode(404).Build()
	v := verification{
		HttpResponse: &resp,
		Times:        AtLeast(1),
	}

	data, err := json.Marshal(v)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}

	if _, ok := m["httpRequest"]; ok {
		t.Error("expected httpRequest to be omitted")
	}
	if m["httpResponse"] == nil {
		t.Error("expected httpResponse in JSON")
	}
}

func TestVerificationSequence_WithResponses_JSON(t *testing.T) {
	r1 := Request().Path("/first").Build()
	r2 := Request().Path("/second").Build()
	resp1 := Response().StatusCode(200).Build()
	resp2 := Response().StatusCode(201).Build()
	vs := verificationSequence{
		HttpRequests:  []HttpRequest{r1, r2},
		HttpResponses: []HttpResponse{resp1, resp2},
	}

	data, err := json.Marshal(vs)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}

	reqs := m["httpRequests"].([]interface{})
	if len(reqs) != 2 {
		t.Errorf("expected 2 requests, got %d", len(reqs))
	}
	resps := m["httpResponses"].([]interface{})
	if len(resps) != 2 {
		t.Errorf("expected 2 responses, got %d", len(resps))
	}
}

func TestVerificationSequence_NoResponses_OmitsField(t *testing.T) {
	r1 := Request().Path("/first").Build()
	vs := verificationSequence{
		HttpRequests: []HttpRequest{r1},
	}

	data, err := json.Marshal(vs)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}

	if _, ok := m["httpResponses"]; ok {
		t.Error("expected httpResponses to be omitted when nil")
	}
}

func TestClient_VerifyResponse_URLAndBody(t *testing.T) {
	var receivedPath string
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedPath = r.URL.Path
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(202)
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.VerifyResponse(
		Request().Method("GET").Path("/api"),
		Response().StatusCode(200),
		AtLeast(1),
	)
	if err != nil {
		t.Fatal(err)
	}

	if receivedPath != "/mockserver/verify" {
		t.Errorf("expected path /mockserver/verify, got %s", receivedPath)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(receivedBody, &m); err != nil {
		t.Fatalf("body is not valid JSON: %s", err)
	}

	if m["httpRequest"] == nil {
		t.Error("expected httpRequest in body")
	}
	if m["httpResponse"] == nil {
		t.Error("expected httpResponse in body")
	}
	httpResp := m["httpResponse"].(map[string]interface{})
	if httpResp["statusCode"] != float64(200) {
		t.Errorf("expected statusCode 200, got %v", httpResp["statusCode"])
	}
}

func TestClient_VerifyResponse_ResponseOnly(t *testing.T) {
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(202)
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.VerifyResponse(nil, Response().StatusCode(500), AtLeast(1))
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(receivedBody, &m); err != nil {
		t.Fatalf("body is not valid JSON: %s", err)
	}

	if _, ok := m["httpRequest"]; ok {
		t.Error("expected httpRequest to be absent for response-only verification")
	}
	if m["httpResponse"] == nil {
		t.Error("expected httpResponse in body")
	}
}

func TestClient_VerifyResponse_406ReturnsError(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(406)
		w.Write([]byte("Response not found"))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.VerifyResponse(
		Request().Path("/missing"),
		Response().StatusCode(200),
		AtLeast(1),
	)
	if err == nil {
		t.Fatal("expected error, got nil")
	}

	verErr, ok := err.(*VerificationError)
	if !ok {
		t.Fatalf("expected *VerificationError, got %T: %v", err, err)
	}
	if verErr.Message != "Response not found" {
		t.Errorf("unexpected message: %s", verErr.Message)
	}
}

func TestClient_VerifyResponseSequence_URLAndBody(t *testing.T) {
	var receivedPath string
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedPath = r.URL.Path
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(202)
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.VerifyResponseSequence(
		[]*RequestBuilder{Request().Path("/first"), Request().Path("/second")},
		[]*ResponseBuilder{Response().StatusCode(200), Response().StatusCode(201)},
	)
	if err != nil {
		t.Fatal(err)
	}

	if receivedPath != "/mockserver/verifySequence" {
		t.Errorf("expected path /mockserver/verifySequence, got %s", receivedPath)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(receivedBody, &m); err != nil {
		t.Fatalf("body is not valid JSON: %s", err)
	}

	reqs := m["httpRequests"].([]interface{})
	if len(reqs) != 2 {
		t.Errorf("expected 2 requests, got %d", len(reqs))
	}
	resps := m["httpResponses"].([]interface{})
	if len(resps) != 2 {
		t.Errorf("expected 2 responses, got %d", len(resps))
	}
}

func TestClient_VerifyResponseSequence_406ReturnsError(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(406)
		w.Write([]byte("Sequence not found"))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.VerifyResponseSequence(
		[]*RequestBuilder{Request().Path("/first")},
		[]*ResponseBuilder{Response().StatusCode(200)},
	)
	if err == nil {
		t.Fatal("expected error, got nil")
	}

	verErr, ok := err.(*VerificationError)
	if !ok {
		t.Fatalf("expected *VerificationError, got %T: %v", err, err)
	}
	if verErr.Message != "Sequence not found" {
		t.Errorf("unexpected message: %s", verErr.Message)
	}
}

func TestClient_VerifyResponse_WithResponseHeaders(t *testing.T) {
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(202)
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.VerifyResponse(
		Request().Path("/api"),
		Response().StatusCode(200).Header("Content-Type", "application/json"),
		nil,
	)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(receivedBody, &m); err != nil {
		t.Fatalf("body is not valid JSON: %s", err)
	}

	httpResp := m["httpResponse"].(map[string]interface{})
	headers := httpResp["headers"].(map[string]interface{})
	ct := headers["Content-Type"].([]interface{})
	if len(ct) != 1 || ct[0] != "application/json" {
		t.Errorf("expected Content-Type header, got %v", ct)
	}
}

func TestClient_Clear_URLWithType(t *testing.T) {
	var receivedPath string
	var receivedQuery url.Values

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedPath = r.URL.Path
		receivedQuery = r.URL.Query()
		w.WriteHeader(200)
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.Clear(Request().Path("/old"), ClearExpectations)
	if err != nil {
		t.Fatal(err)
	}

	if receivedPath != "/mockserver/clear" {
		t.Errorf("expected path /mockserver/clear, got %s", receivedPath)
	}
	if receivedQuery.Get("type") != "expectations" {
		t.Errorf("expected type=expectations, got %s", receivedQuery.Get("type"))
	}
}

func TestClient_Clear_NilRequest(t *testing.T) {
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(200)
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.Clear(nil, "")
	if err != nil {
		t.Fatal(err)
	}

	if len(receivedBody) != 0 {
		t.Errorf("expected empty body for nil request, got %s", string(receivedBody))
	}
}

func TestClient_ClearByID_URL(t *testing.T) {
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(200)
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.ClearByID("exp-123", ClearAll)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]string
	if err := json.Unmarshal(receivedBody, &m); err != nil {
		t.Fatal(err)
	}
	if m["id"] != "exp-123" {
		t.Errorf("expected id exp-123, got %s", m["id"])
	}
}

func TestClient_RetrieveByID(t *testing.T) {
	cases := []struct {
		name         string
		call         func(c *Client) error
		expectedType string
	}{
		{
			name: "active expectations",
			call: func(c *Client) error {
				_, err := c.RetrieveActiveExpectationsByID("exp-123")
				return err
			},
			expectedType: "active_expectations",
		},
		{
			name: "recorded requests",
			call: func(c *Client) error {
				_, err := c.RetrieveRecordedRequestsByID("exp-123")
				return err
			},
			expectedType: "requests",
		},
		{
			name: "recorded expectations",
			call: func(c *Client) error {
				_, err := c.RetrieveRecordedExpectationsByID("exp-123")
				return err
			},
			expectedType: "recorded_expectations",
		},
		{
			name: "log messages",
			call: func(c *Client) error {
				_, err := c.RetrieveLogMessagesByID("exp-123")
				return err
			},
			expectedType: "logs",
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			var receivedBody []byte
			var receivedType string

			ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				receivedBody, _ = io.ReadAll(r.Body)
				receivedType = r.URL.Query().Get("type")
				w.WriteHeader(200)
				_, _ = w.Write([]byte("[]"))
			}))
			defer ts.Close()

			if err := tc.call(NewFromURL(ts.URL)); err != nil {
				t.Fatal(err)
			}

			var m map[string]string
			if err := json.Unmarshal(receivedBody, &m); err != nil {
				t.Fatal(err)
			}
			if m["id"] != "exp-123" {
				t.Errorf("expected id exp-123, got %s", m["id"])
			}
			if receivedType != tc.expectedType {
				t.Errorf("expected type %s, got %s", tc.expectedType, receivedType)
			}
		})
	}
}

func TestClient_RetrieveByID_UnknownIDIsAnError(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(400)
		_, _ = w.Write([]byte("No expectation found with id exp-missing"))
	}))
	defer ts.Close()

	_, err := NewFromURL(ts.URL).RetrieveActiveExpectationsByID("exp-missing")
	if err == nil {
		t.Fatal("expected an error for an unknown expectation id")
	}
	if !strings.Contains(err.Error(), "No expectation found with id exp-missing") {
		t.Errorf("expected the server error to be surfaced, got %v", err)
	}
}

func TestClient_Reset_URL(t *testing.T) {
	var receivedPath string
	var receivedMethod string

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedPath = r.URL.Path
		receivedMethod = r.Method
		w.WriteHeader(200)
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.Reset()
	if err != nil {
		t.Fatal(err)
	}

	if receivedPath != "/mockserver/reset" {
		t.Errorf("expected path /mockserver/reset, got %s", receivedPath)
	}
	if receivedMethod != "PUT" {
		t.Errorf("expected method PUT, got %s", receivedMethod)
	}
}

func TestClient_Retrieve_URLWithParams(t *testing.T) {
	var receivedQuery url.Values

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedQuery = r.URL.Query()
		w.WriteHeader(200)
		w.Write([]byte("[]"))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	_, err := client.Retrieve(nil, RetrieveActiveExpectations, FormatJSON)
	if err != nil {
		t.Fatal(err)
	}

	if receivedQuery.Get("type") != "active_expectations" {
		t.Errorf("expected type=active_expectations, got %s", receivedQuery.Get("type"))
	}
	if receivedQuery.Get("format") != "json" {
		t.Errorf("expected format=json, got %s", receivedQuery.Get("format"))
	}
}

func TestClient_Status_URL(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(200)
		w.Write([]byte(`{"ports":[1080]}`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	status, err := client.Status()
	if err != nil {
		t.Fatal(err)
	}
	if len(status.Ports) != 1 || status.Ports[0] != 1080 {
		t.Errorf("unexpected status ports: %v", status.Ports)
	}
}

func TestClient_Bind_URL(t *testing.T) {
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(200)
		w.Write([]byte(`{"ports":[1080,1081]}`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	ports, err := client.Bind(1081)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string][]int
	if err := json.Unmarshal(receivedBody, &m); err != nil {
		t.Fatal(err)
	}
	if len(m["ports"]) != 1 || m["ports"][0] != 1081 {
		t.Errorf("unexpected bind body: %v", m)
	}
	if len(ports) != 2 {
		t.Errorf("expected 2 ports returned, got %d", len(ports))
	}
}

func TestClient_When_Respond_Fluent(t *testing.T) {
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(201)
		w.Write([]byte("[]"))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	_, err := client.When(
		Request().Method("GET").Path("/hello"),
		WithTimes(Once()),
	).Respond(
		Response().StatusCode(200).Body("world"),
	)
	if err != nil {
		t.Fatal(err)
	}

	var exps []map[string]interface{}
	if err := json.Unmarshal(receivedBody, &exps); err != nil {
		t.Fatalf("invalid JSON: %s", err)
	}
	if len(exps) != 1 {
		t.Fatalf("expected 1 expectation, got %d", len(exps))
	}

	exp := exps[0]
	httpReq := exp["httpRequest"].(map[string]interface{})
	if httpReq["method"] != "GET" {
		t.Errorf("expected GET, got %v", httpReq["method"])
	}
	if httpReq["path"] != "/hello" {
		t.Errorf("expected /hello, got %v", httpReq["path"])
	}

	httpResp := exp["httpResponse"].(map[string]interface{})
	if httpResp["statusCode"] != float64(200) {
		t.Errorf("expected 200, got %v", httpResp["statusCode"])
	}
	if httpResp["body"] != "world" {
		t.Errorf("expected world, got %v", httpResp["body"])
	}
}

func TestClient_When_Forward_Fluent(t *testing.T) {
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(201)
		w.Write([]byte("[]"))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	_, err := client.When(
		Request().Path("/proxy"),
	).Forward(
		Forward().Host("backend").Port(9090).Scheme("HTTP"),
	)
	if err != nil {
		t.Fatal(err)
	}

	var exps []map[string]interface{}
	if err := json.Unmarshal(receivedBody, &exps); err != nil {
		t.Fatalf("invalid JSON: %s", err)
	}

	fwd := exps[0]["httpForward"].(map[string]interface{})
	if fwd["host"] != "backend" {
		t.Errorf("expected host backend, got %v", fwd["host"])
	}
}

func TestClient_When_WithID(t *testing.T) {
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(201)
		w.Write([]byte("[]"))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	_, err := client.When(
		Request().Path("/dedup"),
	).WithID("my-exp-1").WithPriority(10).Respond(
		Response().StatusCode(204),
	)
	if err != nil {
		t.Fatal(err)
	}

	var exps []map[string]interface{}
	if err := json.Unmarshal(receivedBody, &exps); err != nil {
		t.Fatal(err)
	}

	if exps[0]["id"] != "my-exp-1" {
		t.Errorf("expected id my-exp-1, got %v", exps[0]["id"])
	}
	if exps[0]["priority"] != float64(10) {
		t.Errorf("expected priority 10, got %v", exps[0]["priority"])
	}
}

func TestClient_IsRunning_True(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(200)
		w.Write([]byte(`{"ports":[1080]}`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	if !client.IsRunning() {
		t.Error("expected IsRunning to be true")
	}
}

func TestClient_IsRunning_False(t *testing.T) {
	// Connect to a port nothing is listening on
	client := New("localhost", 19999)
	if client.IsRunning() {
		t.Error("expected IsRunning to be false")
	}
}

// --- Times/TTL helper tests ---

func TestOnce(t *testing.T) {
	times := Once()
	if times.RemainingTimes != 1 || times.Unlimited {
		t.Errorf("unexpected Once: %+v", times)
	}
}

func TestExactly(t *testing.T) {
	times := Exactly(5)
	if times.RemainingTimes != 5 || times.Unlimited {
		t.Errorf("unexpected Exactly(5): %+v", times)
	}
}

func TestUnlimitedTimes(t *testing.T) {
	times := Unlimited()
	if !times.Unlimited {
		t.Error("expected Unlimited to be true")
	}
}

func TestTTL(t *testing.T) {
	ttl := TTL("SECONDS", 30)
	if ttl.TimeUnit != "SECONDS" || ttl.TimeToLive != 30 || ttl.Unlimited {
		t.Errorf("unexpected TTL: %+v", ttl)
	}
}

func TestUnlimitedTTL(t *testing.T) {
	ttl := UnlimitedTTL()
	if !ttl.Unlimited {
		t.Error("expected UnlimitedTTL to be true")
	}
}

// --- VerificationTimes tests ---

func TestAtLeast(t *testing.T) {
	vt := AtLeast(3)
	if vt.AtLeast != 3 || vt.AtMost != 0 {
		t.Errorf("unexpected AtLeast: %+v", vt)
	}
}

func TestAtMost(t *testing.T) {
	vt := AtMost(5)
	if vt.AtMost != 5 || vt.AtLeast != 0 {
		t.Errorf("unexpected AtMost: %+v", vt)
	}
}

func TestBetween(t *testing.T) {
	vt := Between(1, 5)
	if vt.AtLeast != 1 || vt.AtMost != 5 {
		t.Errorf("unexpected Between: %+v", vt)
	}
}

func TestExactlyTimes(t *testing.T) {
	vt := ExactlyTimes(3)
	if vt.AtLeast != 3 || vt.AtMost != 3 {
		t.Errorf("unexpected ExactlyTimes: %+v", vt)
	}
}

// --- New() constructor tests ---

func TestNew_BaseURL(t *testing.T) {
	client := New("example.com", 9090)
	if client.baseURL != "http://example.com:9090" {
		t.Errorf("unexpected baseURL: %s", client.baseURL)
	}
}

func TestNewFromURL(t *testing.T) {
	client := NewFromURL("https://mock.internal:443/ctx")
	if client.baseURL != "https://mock.internal:443/ctx" {
		t.Errorf("unexpected baseURL: %s", client.baseURL)
	}
}

func TestNew_WithContextPath(t *testing.T) {
	client := New("localhost", 1080, WithContextPath("/myapp"))
	if client.baseURL != "http://localhost:1080/myapp" {
		t.Errorf("unexpected baseURL: %s", client.baseURL)
	}
}

// --- Content-Type header test ---

func TestClient_ContentType_Header(t *testing.T) {
	var receivedContentType string

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedContentType = r.Header.Get("Content-Type")
		w.WriteHeader(200)
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	_ = client.Reset()

	if receivedContentType != "application/json; charset=utf-8" {
		t.Errorf("unexpected Content-Type: %s", receivedContentType)
	}
}

func TestClient_RetrieveRecordedRequests_DnsFilterRoundTrip(t *testing.T) {
	var receivedQuery string
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedQuery = r.URL.RawQuery
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(200)
		w.Write([]byte(`[{"dnsName":"api.example.com","dnsType":"A","dnsClass":"IN"}]`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	// There is no fluent DNS setter on RequestBuilder, so set the DNS fields directly.
	filter := &RequestBuilder{request: HttpRequest{DnsName: "api.example.com", DnsType: "A"}}
	reqs, err := client.RetrieveRecordedRequests(filter)
	if err != nil {
		t.Fatal(err)
	}

	if !strings.Contains(receivedQuery, "type=requests") {
		t.Errorf("expected type=requests in query, got %s", receivedQuery)
	}
	var sent map[string]interface{}
	if err := json.Unmarshal(receivedBody, &sent); err != nil {
		t.Fatalf("filter body is not valid JSON: %s", err)
	}
	if sent["dnsName"] != "api.example.com" || sent["dnsType"] != "A" {
		t.Errorf("expected DNS filter in body, got %s", receivedBody)
	}

	if len(reqs) != 1 {
		t.Fatalf("expected 1 recorded request, got %d", len(reqs))
	}
	if reqs[0].DnsName != "api.example.com" || reqs[0].DnsType != "A" || reqs[0].DnsClass != "IN" {
		t.Errorf("expected DNS fields to round-trip, got %+v", reqs[0])
	}
}
