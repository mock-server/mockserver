package mockserver

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

// --- HttpTemplate struct JSON tests ---

func TestHttpTemplate_MarshalJSON(t *testing.T) {
	tmpl := HttpTemplate{
		TemplateType: "VELOCITY",
		Template:     "return { statusCode: 200 }",
	}

	data, err := json.Marshal(tmpl)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}

	if m["templateType"] != "VELOCITY" {
		t.Errorf("expected templateType VELOCITY, got %v", m["templateType"])
	}
	if m["template"] != "return { statusCode: 200 }" {
		t.Errorf("unexpected template: %v", m["template"])
	}
	if _, exists := m["templateFile"]; exists {
		t.Error("templateFile should be omitted when empty")
	}
	if _, exists := m["delay"]; exists {
		t.Error("delay should be omitted when nil")
	}
	if _, exists := m["primary"]; exists {
		t.Error("primary should be omitted when nil")
	}
}

func TestHttpTemplate_DelayAndPrimary_RoundTrip(t *testing.T) {
	for _, primary := range []bool{true, false} {
		tmpl := ResponseTemplate("MUSTACHE").
			Template("{\"a\":1}").
			WithDelay("SECONDS", 2).
			Primary(primary).
			Build()

		data, err := json.Marshal(tmpl)
		if err != nil {
			t.Fatal(err)
		}
		var m map[string]interface{}
		if err := json.Unmarshal(data, &m); err != nil {
			t.Fatal(err)
		}
		if m["primary"] != primary {
			t.Errorf("expected primary %v on the wire, got %v (%s)", primary, m["primary"], data)
		}
		delay, ok := m["delay"].(map[string]interface{})
		if !ok || delay["timeUnit"] != "SECONDS" || delay["value"] != float64(2) {
			t.Errorf("unexpected delay on the wire: %s", data)
		}

		var back HttpTemplate
		if err := json.Unmarshal(data, &back); err != nil {
			t.Fatal(err)
		}
		if back.Primary == nil || *back.Primary != primary {
			t.Errorf("expected primary %v after round trip, got %v", primary, back.Primary)
		}
		if back.Delay == nil || back.Delay.TimeUnit != "SECONDS" || back.Delay.Value != 2 {
			t.Errorf("unexpected delay after round trip: %+v", back.Delay)
		}
		again, err := json.Marshal(back)
		if err != nil {
			t.Fatal(err)
		}
		if string(again) != string(data) {
			t.Errorf("round trip changed the JSON: %s != %s", again, data)
		}
	}
}

func TestHttpTemplate_WithTemplateFile_MarshalJSON(t *testing.T) {
	tmpl := HttpTemplate{
		TemplateType: "MUSTACHE",
		TemplateFile: "/templates/response.mustache",
		Delay:        &Delay{TimeUnit: "SECONDS", Value: 2},
	}

	data, err := json.Marshal(tmpl)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}

	if m["templateType"] != "MUSTACHE" {
		t.Errorf("expected templateType MUSTACHE, got %v", m["templateType"])
	}
	if m["templateFile"] != "/templates/response.mustache" {
		t.Errorf("unexpected templateFile: %v", m["templateFile"])
	}
	if _, exists := m["template"]; exists {
		t.Error("template should be omitted when empty")
	}

	delay := m["delay"].(map[string]interface{})
	if delay["timeUnit"] != "SECONDS" {
		t.Errorf("expected SECONDS, got %v", delay["timeUnit"])
	}
	if delay["value"] != float64(2) {
		t.Errorf("expected 2, got %v", delay["value"])
	}
}

func TestHttpTemplate_UnmarshalJSON(t *testing.T) {
	jsonStr := `{"templateType":"JAVASCRIPT","template":"return { statusCode: 404 }","delay":{"timeUnit":"MILLISECONDS","value":100}}`

	var tmpl HttpTemplate
	if err := json.Unmarshal([]byte(jsonStr), &tmpl); err != nil {
		t.Fatal(err)
	}

	if tmpl.TemplateType != "JAVASCRIPT" {
		t.Errorf("expected JAVASCRIPT, got %s", tmpl.TemplateType)
	}
	if tmpl.Template != "return { statusCode: 404 }" {
		t.Errorf("unexpected template: %s", tmpl.Template)
	}
	if tmpl.TemplateFile != "" {
		t.Errorf("expected empty templateFile, got %s", tmpl.TemplateFile)
	}
	if tmpl.Delay == nil || tmpl.Delay.TimeUnit != "MILLISECONDS" || tmpl.Delay.Value != 100 {
		t.Errorf("unexpected delay: %+v", tmpl.Delay)
	}
}

// --- TemplateBuilder tests ---

func TestResponseTemplateBuilder(t *testing.T) {
	tmpl := ResponseTemplate("VELOCITY").
		Template("return { statusCode: 200, body: 'hello' }").
		WithDelay("SECONDS", 1).
		Build()

	if tmpl.TemplateType != "VELOCITY" {
		t.Errorf("expected VELOCITY, got %s", tmpl.TemplateType)
	}
	if tmpl.Template != "return { statusCode: 200, body: 'hello' }" {
		t.Errorf("unexpected template: %s", tmpl.Template)
	}
	if tmpl.TemplateFile != "" {
		t.Errorf("expected empty templateFile, got %s", tmpl.TemplateFile)
	}
	if tmpl.Delay == nil || tmpl.Delay.TimeUnit != "SECONDS" || tmpl.Delay.Value != 1 {
		t.Errorf("unexpected delay: %+v", tmpl.Delay)
	}
}

func TestForwardTemplateBuilder(t *testing.T) {
	tmpl := ForwardTemplate("MUSTACHE").
		TemplateFile("/templates/forward.mustache").
		Build()

	if tmpl.TemplateType != "MUSTACHE" {
		t.Errorf("expected MUSTACHE, got %s", tmpl.TemplateType)
	}
	if tmpl.TemplateFile != "/templates/forward.mustache" {
		t.Errorf("unexpected templateFile: %s", tmpl.TemplateFile)
	}
	if tmpl.Template != "" {
		t.Errorf("expected empty template, got %s", tmpl.Template)
	}
	if tmpl.Delay != nil {
		t.Errorf("expected nil delay, got %+v", tmpl.Delay)
	}
}

// --- Expectation with template actions: round-trip ---

func TestExpectation_WithHttpResponseTemplate_RoundTrip(t *testing.T) {
	req := Request().Method("GET").Path("/templated").Build()
	tmpl := HttpTemplate{
		TemplateType: "VELOCITY",
		TemplateFile: "/templates/get-response.vm",
	}
	exp := Expectation{
		HttpRequest:          &req,
		HttpResponseTemplate: &tmpl,
		Times:                Once(),
	}

	data, err := json.Marshal(exp)
	if err != nil {
		t.Fatal(err)
	}

	var decoded Expectation
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatal(err)
	}

	if decoded.HttpResponseTemplate == nil {
		t.Fatal("expected HttpResponseTemplate to be present")
	}
	if decoded.HttpResponseTemplate.TemplateType != "VELOCITY" {
		t.Errorf("expected VELOCITY, got %s", decoded.HttpResponseTemplate.TemplateType)
	}
	if decoded.HttpResponseTemplate.TemplateFile != "/templates/get-response.vm" {
		t.Errorf("unexpected templateFile: %s", decoded.HttpResponseTemplate.TemplateFile)
	}
	if decoded.HttpResponse != nil {
		t.Error("expected HttpResponse to be nil")
	}
	if decoded.HttpForwardTemplate != nil {
		t.Error("expected HttpForwardTemplate to be nil")
	}

	// Verify JSON keys
	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}
	if _, exists := m["httpResponseTemplate"]; !exists {
		t.Error("expected httpResponseTemplate key in JSON")
	}
	if _, exists := m["httpResponse"]; exists {
		t.Error("httpResponse should be omitted when nil")
	}
}

func TestExpectation_WithHttpForwardTemplate_RoundTrip(t *testing.T) {
	req := Request().Path("/forward-templated").Build()
	tmpl := HttpTemplate{
		TemplateType: "JAVASCRIPT",
		Template:     "return { host: 'localhost', port: 8080 }",
	}
	exp := Expectation{
		HttpRequest:         &req,
		HttpForwardTemplate: &tmpl,
	}

	data, err := json.Marshal(exp)
	if err != nil {
		t.Fatal(err)
	}

	var decoded Expectation
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatal(err)
	}

	if decoded.HttpForwardTemplate == nil {
		t.Fatal("expected HttpForwardTemplate to be present")
	}
	if decoded.HttpForwardTemplate.TemplateType != "JAVASCRIPT" {
		t.Errorf("expected JAVASCRIPT, got %s", decoded.HttpForwardTemplate.TemplateType)
	}
	if decoded.HttpForwardTemplate.Template != "return { host: 'localhost', port: 8080 }" {
		t.Errorf("unexpected template: %s", decoded.HttpForwardTemplate.Template)
	}
	if decoded.HttpForward != nil {
		t.Error("expected HttpForward to be nil")
	}

	// Verify JSON keys
	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}
	if _, exists := m["httpForwardTemplate"]; !exists {
		t.Error("expected httpForwardTemplate key in JSON")
	}
	if _, exists := m["httpForward"]; exists {
		t.Error("httpForward should be omitted when nil")
	}
}

// --- FILE body with templateType: round-trip ---

func TestFileBody_RoundTrip(t *testing.T) {
	resp := Response().
		StatusCode(200).
		BodyFromFile("/data/response.json", "VELOCITY", "application/json").
		Build()

	data, err := json.Marshal(resp)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}

	body := m["body"].(map[string]interface{})
	if body["type"] != "FILE" {
		t.Errorf("expected type FILE, got %v", body["type"])
	}
	if body["filePath"] != "/data/response.json" {
		t.Errorf("unexpected filePath: %v", body["filePath"])
	}
	if body["templateType"] != "VELOCITY" {
		t.Errorf("expected templateType VELOCITY, got %v", body["templateType"])
	}
	if body["contentType"] != "application/json" {
		t.Errorf("expected contentType application/json, got %v", body["contentType"])
	}
}

func TestFileBody_WithoutTemplateType(t *testing.T) {
	resp := Response().
		StatusCode(200).
		BodyFromFile("/data/static.html", "", "text/html").
		Build()

	data, err := json.Marshal(resp)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatal(err)
	}

	body := m["body"].(map[string]interface{})
	if body["type"] != "FILE" {
		t.Errorf("expected type FILE, got %v", body["type"])
	}
	if body["filePath"] != "/data/static.html" {
		t.Errorf("unexpected filePath: %v", body["filePath"])
	}
	if _, exists := body["templateType"]; exists {
		t.Error("templateType should be omitted when empty")
	}
	if body["contentType"] != "text/html" {
		t.Errorf("expected contentType text/html, got %v", body["contentType"])
	}
}

func TestFileBody_Unmarshal(t *testing.T) {
	jsonStr := `{"type":"FILE","filePath":"/tpl/resp.vm","templateType":"MUSTACHE","contentType":"text/plain"}`

	var fb FileBody
	if err := json.Unmarshal([]byte(jsonStr), &fb); err != nil {
		t.Fatal(err)
	}

	if fb.Type != "FILE" {
		t.Errorf("expected FILE, got %s", fb.Type)
	}
	if fb.FilePath != "/tpl/resp.vm" {
		t.Errorf("unexpected filePath: %s", fb.FilePath)
	}
	if fb.TemplateType != "MUSTACHE" {
		t.Errorf("expected MUSTACHE, got %s", fb.TemplateType)
	}
	if fb.ContentType != "text/plain" {
		t.Errorf("expected text/plain, got %s", fb.ContentType)
	}
}

func TestFileBody_TemplatedFilePath_RoundTrip(t *testing.T) {
	resp := Response().
		StatusCode(200).
		BodyFromTemplatedFile("responses/{{ request.pathParameters.name.0 }}.json", "MUSTACHE", "application/json").
		Build()

	data, err := json.Marshal(resp.Body)
	if err != nil {
		t.Fatal(err)
	}
	want := `{"type":"FILE","filePath":"responses/{{ request.pathParameters.name.0 }}.json","templateType":"MUSTACHE","templatedFilePath":true,"contentType":"application/json"}`
	if string(data) != want {
		t.Errorf("unexpected JSON:\n got %s\nwant %s", data, want)
	}

	var fb FileBody
	if err := json.Unmarshal(data, &fb); err != nil {
		t.Fatal(err)
	}
	if !fb.TemplatedFilePath {
		t.Error("templatedFilePath should survive unmarshal")
	}
	again, err := json.Marshal(fb)
	if err != nil {
		t.Fatal(err)
	}
	if string(again) != want {
		t.Errorf("round-trip changed JSON:\n got %s\nwant %s", again, want)
	}
}

func TestFileBody_TemplatedFilePath_OmittedWhenFalse(t *testing.T) {
	data, err := json.Marshal(FileBody{Type: "FILE", FilePath: "/tpl/resp.vm", TemplateType: "VELOCITY"})
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(data), "templatedFilePath") {
		t.Errorf("templatedFilePath should be omitted when false: %s", data)
	}
}

// --- Expectation with FILE body and responseTemplate combined ---

func TestExpectation_FileBodyAndResponseTemplate_JSON(t *testing.T) {
	// An expectation that uses httpResponseTemplate with a templateFile
	// and a separate expectation that uses a FILE body
	req := Request().Method("POST").Path("/api/items").Build()
	tmpl := HttpTemplate{
		TemplateType: "MUSTACHE",
		TemplateFile: "/templates/items-response.mustache",
		Delay:        &Delay{TimeUnit: "MILLISECONDS", Value: 200},
	}
	exp := Expectation{
		ID:                   "template-exp-1",
		Priority:             5,
		HttpRequest:          &req,
		HttpResponseTemplate: &tmpl,
		Times:                Exactly(10),
		TimeToLive:           TTL("MINUTES", 5),
	}

	data, err := json.Marshal(exp)
	if err != nil {
		t.Fatal(err)
	}

	// Round-trip
	var decoded Expectation
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatal(err)
	}

	if decoded.ID != "template-exp-1" {
		t.Errorf("expected id template-exp-1, got %s", decoded.ID)
	}
	if decoded.Priority != 5 {
		t.Errorf("expected priority 5, got %d", decoded.Priority)
	}
	if decoded.HttpResponseTemplate == nil {
		t.Fatal("expected HttpResponseTemplate")
	}
	if decoded.HttpResponseTemplate.TemplateType != "MUSTACHE" {
		t.Errorf("expected MUSTACHE, got %s", decoded.HttpResponseTemplate.TemplateType)
	}
	if decoded.HttpResponseTemplate.TemplateFile != "/templates/items-response.mustache" {
		t.Errorf("unexpected templateFile: %s", decoded.HttpResponseTemplate.TemplateFile)
	}
	if decoded.HttpResponseTemplate.Delay == nil {
		t.Fatal("expected delay")
	}
	if decoded.HttpResponseTemplate.Delay.TimeUnit != "MILLISECONDS" || decoded.HttpResponseTemplate.Delay.Value != 200 {
		t.Errorf("unexpected delay: %+v", decoded.HttpResponseTemplate.Delay)
	}
	if decoded.Times == nil || decoded.Times.RemainingTimes != 10 {
		t.Errorf("unexpected times: %+v", decoded.Times)
	}
	if decoded.TimeToLive == nil || decoded.TimeToLive.TimeUnit != "MINUTES" || decoded.TimeToLive.TimeToLive != 5 {
		t.Errorf("unexpected TTL: %+v", decoded.TimeToLive)
	}
}

// --- Fluent chain: When().RespondTemplate() ---

func TestClient_When_RespondTemplate_Fluent(t *testing.T) {
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(201)
		w.Write([]byte("[]"))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	_, err := client.When(
		Request().Method("GET").Path("/velocity"),
		WithTimes(Once()),
	).RespondTemplate(
		ResponseTemplate("VELOCITY").Template("return { statusCode: 200, body: 'hi' }"),
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
	tmpl := exp["httpResponseTemplate"].(map[string]interface{})
	if tmpl["templateType"] != "VELOCITY" {
		t.Errorf("expected VELOCITY, got %v", tmpl["templateType"])
	}
	if tmpl["template"] != "return { statusCode: 200, body: 'hi' }" {
		t.Errorf("unexpected template: %v", tmpl["template"])
	}

	// Should not have httpResponse
	if _, exists := exp["httpResponse"]; exists {
		t.Error("httpResponse should not be present")
	}
}

// --- Fluent chain: When().ForwardTemplate() ---

func TestClient_When_ForwardTemplate_Fluent(t *testing.T) {
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(201)
		w.Write([]byte("[]"))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	_, err := client.When(
		Request().Path("/dynamic-forward"),
	).ForwardTemplate(
		ForwardTemplate("JAVASCRIPT").TemplateFile("/templates/forward.js").WithDelay("SECONDS", 3),
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
	tmpl := exp["httpForwardTemplate"].(map[string]interface{})
	if tmpl["templateType"] != "JAVASCRIPT" {
		t.Errorf("expected JAVASCRIPT, got %v", tmpl["templateType"])
	}
	if tmpl["templateFile"] != "/templates/forward.js" {
		t.Errorf("unexpected templateFile: %v", tmpl["templateFile"])
	}
	delay := tmpl["delay"].(map[string]interface{})
	if delay["timeUnit"] != "SECONDS" || delay["value"] != float64(3) {
		t.Errorf("unexpected delay: %v", delay)
	}

	// Should not have httpForward
	if _, exists := exp["httpForward"]; exists {
		t.Error("httpForward should not be present")
	}
}
