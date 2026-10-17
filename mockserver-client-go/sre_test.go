package mockserver

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"
)

// stubServer captures the method, path and body of a single request and replies
// with the given status code and body.
func stubServer(t *testing.T, status int, respBody string, capMethod, capPath *string, capBody *[]byte) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if capMethod != nil {
			*capMethod = r.Method
		}
		if capPath != nil {
			*capPath = r.URL.Path
		}
		if capBody != nil {
			*capBody, _ = io.ReadAll(r.Body)
		}
		if respBody != "" {
			w.Header().Set("Content-Type", "application/json")
		}
		w.WriteHeader(status)
		if respBody != "" {
			_, _ = w.Write([]byte(respBody))
		}
	}))
}

// ---------------------------------------------------------------------------
// 1. Load scenario
// ---------------------------------------------------------------------------

func sampleScenario() LoadScenario {
	return LoadScenario{
		Name:             "checkout-load",
		TemplateType:     "VELOCITY",
		MaxRequests:      5000,
		StartDelayMillis: 2000,
		Labels:           map[string]string{"team": "checkout"},
		Profile: &LoadProfile{
			Stages: []LoadStage{
				RampVusStage(0, 10, 30000, RampLinear),
				ConstantVusStage(10, 60000),
				PauseStage(5000),
			},
		},
		Steps: []LoadStep{
			{
				Request: &HttpRequest{
					Method:        "GET",
					Path:          "/api/item/$iteration.index",
					SocketAddress: &SocketAddress{Host: "target.svc", Port: 8080, Scheme: "HTTP"},
				},
				ThinkTime: &Delay{TimeUnit: "MILLISECONDS", Value: 20},
				Name:      "fetch-item",
			},
		},
	}
}

func TestClient_LoadScenario_Register(t *testing.T) {
	var method, path string
	var body []byte
	ts := stubServer(t, 200, `{"name":"checkout-load","state":"LOADED"}`, &method, &path, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	ref, err := client.LoadScenario(sampleScenario())
	if err != nil {
		t.Fatal(err)
	}

	if method != "PUT" {
		t.Errorf("expected PUT, got %s", method)
	}
	if path != "/mockserver/loadScenario" {
		t.Errorf("unexpected path %s", path)
	}
	if ref.Name != "checkout-load" || ref.State != LoadScenarioStateLoaded {
		t.Errorf("unexpected ref: %+v", ref)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(body, &m); err != nil {
		t.Fatalf("body not valid JSON: %v", err)
	}
	if m["name"] != "checkout-load" || m["templateType"] != "VELOCITY" {
		t.Errorf("unexpected body fields: %v", m)
	}
	if m["maxRequests"].(float64) != 5000 {
		t.Errorf("expected maxRequests 5000, got %v", m["maxRequests"])
	}
	if m["startDelayMillis"].(float64) != 2000 {
		t.Errorf("expected startDelayMillis 2000, got %v", m["startDelayMillis"])
	}
	profile := m["profile"].(map[string]interface{})
	stages := profile["stages"].([]interface{})
	if len(stages) != 3 {
		t.Fatalf("expected 3 stages, got %d", len(stages))
	}
	stage0 := stages[0].(map[string]interface{})
	if stage0["type"] != "VU" {
		t.Errorf("expected stage 0 type VU, got %v", stage0["type"])
	}
	// A meaningful zero (startVus=0) must still be serialised (pointer + omitempty).
	if v, ok := stage0["startVus"]; !ok || v.(float64) != 0 {
		t.Errorf("expected stage 0 startVus 0 present, got %v (present=%v)", v, ok)
	}
	if stage0["endVus"].(float64) != 10 {
		t.Errorf("expected stage 0 endVus 10, got %v", stage0["endVus"])
	}
	if stage0["curve"] != "LINEAR" {
		t.Errorf("expected stage 0 curve LINEAR, got %v", stage0["curve"])
	}
	// VU-ramp stage must not leak RATE fields.
	if _, ok := stage0["rate"]; ok {
		t.Errorf("did not expect rate on a VU stage, got %v", stage0["rate"])
	}
	stage1 := stages[1].(map[string]interface{})
	if stage1["type"] != "VU" || stage1["vus"].(float64) != 10 {
		t.Errorf("expected stage 1 VU hold vus 10, got %v", stage1)
	}
	stage2 := stages[2].(map[string]interface{})
	if stage2["type"] != "PAUSE" || stage2["durationMillis"].(float64) != 5000 {
		t.Errorf("expected stage 2 PAUSE durationMillis 5000, got %v", stage2)
	}
	steps := m["steps"].([]interface{})
	step0 := steps[0].(map[string]interface{})
	req0 := step0["request"].(map[string]interface{})
	sock := req0["socketAddress"].(map[string]interface{})
	if sock["host"] != "target.svc" {
		t.Errorf("expected socketAddress.host target.svc, got %v", sock)
	}
}

func TestClient_LoadScenarios_List(t *testing.T) {
	var method, path string
	// The server emits the live status fields FLAT on each scenario entry (siblings of
	// name/state/definition), present only once the scenario has run.
	ts := stubServer(t, 200, `{"scenarios":[{"name":"checkout-load","state":"RUNNING","currentVus":10,"stageIndex":1,"stageType":"RATE","currentTarget":50,"requestsSent":120,"p95Millis":42.5,"labels":{"team":"checkout"}},{"name":"idle","state":"LOADED"}]}`, &method, &path, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	list, err := client.LoadScenarios()
	if err != nil {
		t.Fatal(err)
	}
	if method != "GET" {
		t.Errorf("expected GET, got %s", method)
	}
	if path != "/mockserver/loadScenario" {
		t.Errorf("unexpected path %s", path)
	}
	if len(list.Scenarios) != 2 {
		t.Fatalf("expected 2 scenarios, got %d", len(list.Scenarios))
	}
	if list.Scenarios[0].Name != "checkout-load" || list.Scenarios[0].State != LoadScenarioStateRunning {
		t.Errorf("unexpected scenario[0]: %+v", list.Scenarios[0])
	}
	// Flat live fields are promoted onto the entry via the embedded LoadScenarioStatus.
	if list.Scenarios[0].CurrentVus != 10 || list.Scenarios[0].P95Millis != 42.5 {
		t.Errorf("unexpected promoted status[0]: %+v", list.Scenarios[0])
	}
	if list.Scenarios[0].StageIndex != 1 || list.Scenarios[0].StageType != "RATE" || list.Scenarios[0].CurrentTarget != 50 {
		t.Errorf("unexpected stage fields[0]: %+v", list.Scenarios[0])
	}
	if list.Scenarios[0].Labels["team"] != "checkout" {
		t.Errorf("unexpected labels[0]: %+v", list.Scenarios[0].Labels)
	}
	// A never-run LOADED scenario carries no live fields (zero values).
	if list.Scenarios[1].State != LoadScenarioStateLoaded || list.Scenarios[1].RequestsSent != 0 || list.Scenarios[1].CurrentVus != 0 {
		t.Errorf("unexpected scenario[1]: %+v", list.Scenarios[1])
	}
}

func TestClient_GetLoadScenario(t *testing.T) {
	var method, decodedPath, escapedPath string
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		method = r.Method
		decodedPath = r.URL.Path
		escapedPath = r.URL.EscapedPath()
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		// A RUNNING scenario: live fields are FLAT siblings of name/state/definition.
		_, _ = w.Write([]byte(`{"name":"checkout load","state":"RUNNING","startDelayMillis":2000,"definition":{"name":"checkout load"},"currentVus":5,"requestsSent":100,"succeeded":98,"failed":2,"p95Millis":9}`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	entry, err := client.GetLoadScenario("checkout load")
	if err != nil {
		t.Fatal(err)
	}
	if method != "GET" {
		t.Errorf("expected GET, got %s", method)
	}
	if decodedPath != "/mockserver/loadScenario/checkout load" {
		t.Errorf("unexpected decoded path %s", decodedPath)
	}
	// Name with a space must be path-escaped on the wire.
	if escapedPath != "/mockserver/loadScenario/checkout%20load" {
		t.Errorf("expected escaped path, got %s", escapedPath)
	}
	if entry.Name != "checkout load" || entry.Definition == nil || entry.StartDelayMillis != 2000 {
		t.Errorf("unexpected entry: %+v", entry)
	}
	// Flat live fields are promoted onto the entry.
	if entry.CurrentVus != 5 || entry.RequestsSent != 100 || entry.Succeeded != 98 || entry.Failed != 2 || entry.P95Millis != 9 {
		t.Errorf("unexpected promoted status: %+v", entry)
	}
}

func TestClient_GetLoadScenario_NotFound(t *testing.T) {
	ts := stubServer(t, 404, `{"error":"not found"}`, nil, nil, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	_, err := client.GetLoadScenario("missing")
	if err == nil {
		t.Fatal("expected error for 404 status")
	}
}

func TestClient_DeleteLoadScenario(t *testing.T) {
	var method, path string
	ts := stubServer(t, 200, ``, &method, &path, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	if err := client.DeleteLoadScenario("checkout-load"); err != nil {
		t.Fatal(err)
	}
	if method != "DELETE" {
		t.Errorf("expected DELETE, got %s", method)
	}
	if path != "/mockserver/loadScenario/checkout-load" {
		t.Errorf("unexpected path %s", path)
	}
}

func TestClient_ClearLoadScenarios(t *testing.T) {
	var method, path string
	ts := stubServer(t, 200, ``, &method, &path, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	if err := client.ClearLoadScenarios(); err != nil {
		t.Fatal(err)
	}
	if method != "DELETE" {
		t.Errorf("expected DELETE, got %s", method)
	}
	if path != "/mockserver/loadScenario" {
		t.Errorf("unexpected path %s", path)
	}
}

func TestClient_StartLoadScenarios(t *testing.T) {
	var method, path string
	var body []byte
	ts := stubServer(t, 200, `{"started":[{"name":"a","state":"PENDING"},{"name":"b","state":"RUNNING"}],"status":"started"}`, &method, &path, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	result, err := client.StartLoadScenarios("a", "b")
	if err != nil {
		t.Fatal(err)
	}
	if method != "PUT" {
		t.Errorf("expected PUT, got %s", method)
	}
	if path != "/mockserver/loadScenario/start" {
		t.Errorf("unexpected path %s", path)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(body, &m); err != nil {
		t.Fatalf("body not valid JSON: %v", err)
	}
	names := m["names"].([]interface{})
	if len(names) != 2 || names[0] != "a" || names[1] != "b" {
		t.Errorf("expected names [a b], got %v", names)
	}
	if len(result.Started) != 2 || result.Started[0].State != LoadScenarioStatePending {
		t.Errorf("unexpected start result: %+v", result)
	}
	if result.Status != "started" {
		t.Errorf("expected status started, got %q", result.Status)
	}
}

func TestClient_StartLoadScenarios_FeatureDisabled(t *testing.T) {
	ts := stubServer(t, 403, `{"error":"load generation not enabled"}`, nil, nil, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	_, err := client.StartLoadScenarios("a")
	if err == nil {
		t.Fatal("expected error for 403 status")
	}
	var fde *FeatureDisabledError
	if !errors.As(err, &fde) {
		t.Fatalf("expected FeatureDisabledError, got %T: %v", err, err)
	}
	if fde.Status != 403 {
		t.Errorf("expected status 403, got %d", fde.Status)
	}
}

func TestClient_StopLoadScenarios_Named(t *testing.T) {
	var method, path string
	var body []byte
	ts := stubServer(t, 200, `{"stopped":[{"name":"a","state":"STOPPED"}],"status":"stopped"}`, &method, &path, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	result, err := client.StopLoadScenarios("a")
	if err != nil {
		t.Fatal(err)
	}
	if method != "PUT" {
		t.Errorf("expected PUT, got %s", method)
	}
	if path != "/mockserver/loadScenario/stop" {
		t.Errorf("unexpected path %s", path)
	}
	var m map[string]interface{}
	if err := json.Unmarshal(body, &m); err != nil {
		t.Fatalf("body not valid JSON: %v", err)
	}
	names := m["names"].([]interface{})
	if len(names) != 1 || names[0] != "a" {
		t.Errorf("expected names [a], got %v", names)
	}
	if len(result.Stopped) != 1 || result.Stopped[0].State != LoadScenarioStateStopped {
		t.Errorf("unexpected stop result: %+v", result)
	}
}

func TestClient_StopLoadScenarios_All(t *testing.T) {
	var method, path string
	var body []byte
	ts := stubServer(t, 200, `{"stopped":[],"status":"stopped"}`, &method, &path, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	if _, err := client.StopLoadScenarios(); err != nil {
		t.Fatal(err)
	}
	if method != "PUT" {
		t.Errorf("expected PUT, got %s", method)
	}
	if path != "/mockserver/loadScenario/stop" {
		t.Errorf("unexpected path %s", path)
	}
	// No names => empty body (stop all).
	if len(body) != 0 {
		t.Errorf("expected empty body for stop-all, got %q", string(body))
	}
}

func TestClient_RunLoadScenario(t *testing.T) {
	var paths []string
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		paths = append(paths, r.URL.Path)
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		if r.URL.Path == "/mockserver/loadScenario" {
			_, _ = w.Write([]byte(`{"name":"checkout-load","state":"LOADED"}`))
		} else {
			_, _ = w.Write([]byte(`{"started":[{"name":"checkout-load","state":"RUNNING"}],"status":"started"}`))
		}
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	result, err := client.RunLoadScenario(sampleScenario())
	if err != nil {
		t.Fatal(err)
	}
	if len(paths) != 2 || paths[0] != "/mockserver/loadScenario" || paths[1] != "/mockserver/loadScenario/start" {
		t.Errorf("expected register then start, got %v", paths)
	}
	if len(result.Started) != 1 || result.Started[0].Name != "checkout-load" {
		t.Errorf("unexpected run result: %+v", result)
	}
}

func TestClient_LoadScenario_AdvancedFields(t *testing.T) {
	var body []byte
	ts := stubServer(t, 200, `{"name":"adv","state":"LOADED"}`, nil, nil, &body)
	defer ts.Close()

	weight := 7.0
	baseline := 0.0
	peak := 50.0
	scenario := LoadScenario{
		Name:    "adv",
		Profile: &LoadProfile{Shape: &LoadShape{Type: LoadShapeSpike, Metric: LoadShapeMetricVU, Curve: RampLinear, Baseline: &baseline, Peak: &peak, RampUpMillis: 10000, HoldMillis: 30000, RampDownMillis: 10000}},
		Thresholds: []LoadThreshold{
			{Metric: LoadThresholdLatencyP999, Comparator: "LESS_THAN", Threshold: 500},
			{Metric: LoadThresholdErrorRate, Comparator: "LESS_THAN_OR_EQUAL", Threshold: 0.01},
		},
		AbortOnFail:      true,
		AbortGraceMillis: 5000,
		Pacing:           &LoadPacing{Mode: LoadPacingConstantThroughput, Value: 2},
		Feeder:           &LoadFeeder{Rows: []map[string]string{{"user": "alice"}, {"user": "bob"}}, Strategy: LoadFeederRandom},
		StepSelection:    StepSelectionWeighted,
		Steps: []LoadStep{
			{
				Request: &HttpRequest{Method: "GET", Path: "/api/$iteration.data.user"},
				Name:    "browse",
				Weight:  &weight,
				Captures: []LoadCapture{
					{Name: "token", Source: LoadCaptureBodyJSONPath, Expression: "$.token", DefaultValue: "none"},
				},
			},
		},
	}

	client := NewFromURL(ts.URL)
	if _, err := client.LoadScenario(scenario); err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(body, &m); err != nil {
		t.Fatalf("body not valid JSON: %v", err)
	}

	// stepSelection
	if m["stepSelection"] != "WEIGHTED" {
		t.Errorf("expected stepSelection WEIGHTED, got %v", m["stepSelection"])
	}
	// abortOnFail / abortGraceMillis
	if m["abortOnFail"] != true {
		t.Errorf("expected abortOnFail true, got %v", m["abortOnFail"])
	}
	if m["abortGraceMillis"].(float64) != 5000 {
		t.Errorf("expected abortGraceMillis 5000, got %v", m["abortGraceMillis"])
	}

	// thresholds
	thresholds := m["thresholds"].([]interface{})
	if len(thresholds) != 2 {
		t.Fatalf("expected 2 thresholds, got %d", len(thresholds))
	}
	th0 := thresholds[0].(map[string]interface{})
	if th0["metric"] != "LATENCY_P999" || th0["comparator"] != "LESS_THAN" || th0["threshold"].(float64) != 500 {
		t.Errorf("unexpected threshold[0]: %v", th0)
	}

	// pacing
	pacing := m["pacing"].(map[string]interface{})
	if pacing["mode"] != "CONSTANT_THROUGHPUT" || pacing["value"].(float64) != 2 {
		t.Errorf("unexpected pacing: %v", pacing)
	}

	// feeder
	feeder := m["feeder"].(map[string]interface{})
	if feeder["strategy"] != "RANDOM" {
		t.Errorf("unexpected feeder strategy: %v", feeder["strategy"])
	}
	rows := feeder["rows"].([]interface{})
	if len(rows) != 2 || rows[0].(map[string]interface{})["user"] != "alice" {
		t.Errorf("unexpected feeder rows: %v", rows)
	}

	// profile.shape
	profile := m["profile"].(map[string]interface{})
	shape := profile["shape"].(map[string]interface{})
	if shape["type"] != "SPIKE" || shape["metric"] != "VU" {
		t.Errorf("unexpected shape: %v", shape)
	}
	// A meaningful zero (baseline=0) must still be serialised (pointer + omitempty).
	if v, ok := shape["baseline"]; !ok || v.(float64) != 0 {
		t.Errorf("expected shape baseline 0 present, got %v (present=%v)", v, ok)
	}
	if shape["peak"].(float64) != 50 {
		t.Errorf("expected shape peak 50, got %v", shape["peak"])
	}
	// An unset profile shape field (target) must be omitted.
	if _, ok := shape["target"]; ok {
		t.Errorf("did not expect target on a SPIKE shape, got %v", shape["target"])
	}

	// step weight + captures
	steps := m["steps"].([]interface{})
	step0 := steps[0].(map[string]interface{})
	if step0["weight"].(float64) != 7 {
		t.Errorf("expected step weight 7, got %v", step0["weight"])
	}
	captures := step0["captures"].([]interface{})
	cap0 := captures[0].(map[string]interface{})
	if cap0["name"] != "token" || cap0["source"] != "BODY_JSONPATH" || cap0["expression"] != "$.token" || cap0["defaultValue"] != "none" {
		t.Errorf("unexpected capture[0]: %v", cap0)
	}
}

func TestClient_LoadScenario_OptionalAdvancedFieldsOmitted(t *testing.T) {
	// A scenario that sets none of the new fields must not emit them (backward
	// compatible — existing scenarios are unchanged on the wire).
	body, err := json.Marshal(sampleScenario())
	if err != nil {
		t.Fatal(err)
	}
	var m map[string]interface{}
	if err := json.Unmarshal(body, &m); err != nil {
		t.Fatalf("body not valid JSON: %v", err)
	}
	for _, k := range []string{"thresholds", "abortOnFail", "abortGraceMillis", "pacing", "feeder", "stepSelection"} {
		if _, ok := m[k]; ok {
			t.Errorf("expected %q to be omitted on a basic scenario, got %v", k, m[k])
		}
	}
	steps := m["steps"].([]interface{})
	step0 := steps[0].(map[string]interface{})
	for _, k := range []string{"weight", "captures", "checks"} {
		if _, ok := step0[k]; ok {
			t.Errorf("expected step %q to be omitted, got %v", k, step0[k])
		}
	}
	profile := m["profile"].(map[string]interface{})
	if _, ok := profile["shape"]; ok {
		t.Errorf("expected profile shape to be omitted, got %v", profile["shape"])
	}
}

func TestClient_LoadScenarioStatus_TerminalVerdictFields(t *testing.T) {
	// The terminal verdict / threshold fields are emitted FLAT on the entry and
	// promoted onto the entry via the embedded LoadScenarioStatus.
	ts := stubServer(t, 200, `{"name":"adv","state":"STOPPED","p999Millis":612.3,"droppedIterations":4,"verdict":"FAIL","abortedByThreshold":true,"thresholdResults":[{"metric":"LATENCY_P999","comparator":"LESS_THAN","threshold":500,"observed":612.3,"satisfied":false}]}`, nil, nil, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	entry, err := client.GetLoadScenario("adv")
	if err != nil {
		t.Fatal(err)
	}
	if entry.P999Millis != 612.3 || entry.DroppedIterations != 4 {
		t.Errorf("unexpected percentile/dropped: %+v", entry)
	}
	if entry.Verdict != "FAIL" || !entry.AbortedByThreshold {
		t.Errorf("unexpected verdict fields: %+v", entry)
	}
	if len(entry.ThresholdResults) != 1 {
		t.Fatalf("expected 1 threshold result, got %d", len(entry.ThresholdResults))
	}
	tr := entry.ThresholdResults[0]
	if tr.Metric != LoadThresholdLatencyP999 || tr.Observed != 612.3 || tr.Satisfied {
		t.Errorf("unexpected threshold result: %+v", tr)
	}
}

func TestClient_GetLoadScenarioReport_JSON(t *testing.T) {
	var method, path, query string
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		method = r.Method
		path = r.URL.Path
		query = r.URL.RawQuery
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		_, _ = w.Write([]byte(`{"scenario":"adv","verdict":"PASS"}`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	report, err := client.GetLoadScenarioReport("adv", "")
	if err != nil {
		t.Fatal(err)
	}
	if method != "GET" {
		t.Errorf("expected GET, got %s", method)
	}
	if path != "/mockserver/loadScenario/adv/report" {
		t.Errorf("unexpected path %s", path)
	}
	if query != "" {
		t.Errorf("expected no query for JSON report, got %q", query)
	}
	if report != `{"scenario":"adv","verdict":"PASS"}` {
		t.Errorf("unexpected report body: %s", report)
	}
}

func TestClient_GetLoadScenarioReport_JUnit(t *testing.T) {
	var path, query, escapedPath string
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		path = r.URL.Path
		escapedPath = r.URL.EscapedPath()
		query = r.URL.Query().Get("format")
		w.Header().Set("Content-Type", "application/xml")
		w.WriteHeader(200)
		_, _ = w.Write([]byte(`<testsuite name="adv"/>`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	report, err := client.GetLoadScenarioReport("checkout load", "junit")
	if err != nil {
		t.Fatal(err)
	}
	if path != "/mockserver/loadScenario/checkout load/report" {
		t.Errorf("unexpected decoded path %s", path)
	}
	if escapedPath != "/mockserver/loadScenario/checkout%20load/report" {
		t.Errorf("expected escaped path, got %s", escapedPath)
	}
	if query != "junit" {
		t.Errorf("expected format=junit, got %q", query)
	}
	if report != `<testsuite name="adv"/>` {
		t.Errorf("unexpected report body: %s", report)
	}
}

func TestClient_GetLoadScenarioReport_NotFound(t *testing.T) {
	ts := stubServer(t, 404, `{"error":"no run"}`, nil, nil, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	if _, err := client.GetLoadScenarioReport("never-ran", ""); err == nil {
		t.Fatal("expected error for 404 status")
	}
}

func TestClient_GenerateLoadScenarioFromOpenAPI(t *testing.T) {
	var method, path string
	var body []byte
	ts := stubServer(t, 200, `{"status":"loaded","name":"petstore-load","state":"LOADED","scenario":{"name":"petstore-load"}}`, &method, &path, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	result, err := client.GenerateLoadScenarioFromOpenAPI(map[string]interface{}{
		"name":             "petstore-load",
		"specUrlOrPayload": "https://example.com/petstore.yaml",
		"target":           map[string]interface{}{"host": "petstore.svc", "port": 8080, "scheme": "http"},
	})
	if err != nil {
		t.Fatal(err)
	}
	if method != "PUT" {
		t.Errorf("expected PUT, got %s", method)
	}
	if path != "/mockserver/loadScenario/generateFromOpenAPI" {
		t.Errorf("unexpected path %s", path)
	}
	var sent map[string]interface{}
	if err := json.Unmarshal(body, &sent); err != nil {
		t.Fatalf("body not valid JSON: %v", err)
	}
	if sent["name"] != "petstore-load" || sent["specUrlOrPayload"] != "https://example.com/petstore.yaml" {
		t.Errorf("unexpected sent body: %v", sent)
	}
	m := result.(map[string]interface{})
	if m["status"] != "loaded" || m["name"] != "petstore-load" {
		t.Errorf("unexpected result: %v", m)
	}
}

func TestClient_GenerateLoadScenarioFromRecording(t *testing.T) {
	var method, path string
	var body []byte
	ts := stubServer(t, 200, `{"status":"loaded","name":"replay","state":"LOADED","scenario":{"name":"replay"}}`, &method, &path, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	result, err := client.GenerateLoadScenarioFromRecording(map[string]interface{}{
		"name": "replay",
		"mode": "TEMPLATIZED",
	})
	if err != nil {
		t.Fatal(err)
	}
	if method != "PUT" {
		t.Errorf("expected PUT, got %s", method)
	}
	if path != "/mockserver/loadScenario/generateFromRecording" {
		t.Errorf("unexpected path %s", path)
	}
	var sent map[string]interface{}
	if err := json.Unmarshal(body, &sent); err != nil {
		t.Fatalf("body not valid JSON: %v", err)
	}
	if sent["name"] != "replay" || sent["mode"] != "TEMPLATIZED" {
		t.Errorf("unexpected sent body: %v", sent)
	}
	m := result.(map[string]interface{})
	if m["status"] != "loaded" {
		t.Errorf("unexpected result: %v", m)
	}
}

func TestClient_GenerateLoadScenarioFromOpenAPI_Error(t *testing.T) {
	ts := stubServer(t, 400, `{"error":"missing name"}`, nil, nil, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	if _, err := client.GenerateLoadScenarioFromOpenAPI(map[string]interface{}{}); err == nil {
		t.Fatal("expected error for 400 status")
	}
}

// ---------------------------------------------------------------------------
// 2. Service chaos
// ---------------------------------------------------------------------------

func TestClient_SetServiceChaos(t *testing.T) {
	var method, path string
	var body []byte
	ts := stubServer(t, 200, `{}`, &method, &path, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.SetServiceChaos("payments.internal:8443", ServiceChaosProfile{
		ErrorStatus:      503,
		ErrorProbability: 0.3,
		Latency:          &ChaosLatency{TimeUnit: "MILLISECONDS", Value: 200},
	}, 60000)
	if err != nil {
		t.Fatal(err)
	}
	if method != "PUT" {
		t.Errorf("expected PUT, got %s", method)
	}
	if path != "/mockserver/serviceChaos" {
		t.Errorf("unexpected path %s", path)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(body, &m); err != nil {
		t.Fatalf("body not valid JSON: %v", err)
	}
	if m["host"] != "payments.internal:8443" {
		t.Errorf("unexpected host: %v", m["host"])
	}
	if m["ttlMillis"].(float64) != 60000 {
		t.Errorf("expected ttlMillis 60000, got %v", m["ttlMillis"])
	}
	chaos := m["chaos"].(map[string]interface{})
	if chaos["errorStatus"].(float64) != 503 || chaos["errorProbability"].(float64) != 0.3 {
		t.Errorf("unexpected chaos: %v", chaos)
	}
	lat := chaos["latency"].(map[string]interface{})
	if lat["value"].(float64) != 200 || lat["timeUnit"] != "MILLISECONDS" {
		t.Errorf("unexpected latency: %v", lat)
	}
}

func TestClient_RemoveServiceChaos(t *testing.T) {
	var body []byte
	ts := stubServer(t, 200, `{}`, nil, nil, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	if err := client.RemoveServiceChaos("payments.internal:8443"); err != nil {
		t.Fatal(err)
	}
	var m map[string]interface{}
	if err := json.Unmarshal(body, &m); err != nil {
		t.Fatalf("body not valid JSON: %v", err)
	}
	if m["remove"] != true {
		t.Errorf("expected remove:true, got %v", m)
	}
	if m["host"] != "payments.internal:8443" {
		t.Errorf("unexpected host: %v", m["host"])
	}
}

func TestClient_ClearServiceChaos(t *testing.T) {
	var body []byte
	ts := stubServer(t, 200, `{}`, nil, nil, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	if err := client.ClearServiceChaos(); err != nil {
		t.Fatal(err)
	}
	var m map[string]interface{}
	if err := json.Unmarshal(body, &m); err != nil {
		t.Fatalf("body not valid JSON: %v", err)
	}
	if m["clear"] != true {
		t.Errorf("expected clear:true, got %v", m)
	}
}

// ---------------------------------------------------------------------------
// 3. SLO verdict
// ---------------------------------------------------------------------------

func sloCriteria() SloCriteria {
	return SloCriteria{
		Name:               "checkout-slo",
		Window:             &SloWindow{Type: "LOOKBACK", LookbackMillis: 60000},
		MinimumSampleCount: 20,
		UpstreamHosts:      []string{"payments.svc"},
		Objectives: []SloObjective{
			{Sli: "LATENCY_P95", Comparator: "LESS_THAN", Threshold: 250.0, Scope: "FORWARD"},
			{Sli: "ERROR_RATE", Comparator: "LESS_THAN_OR_EQUAL", Threshold: 0.01},
		},
	}
}

func TestClient_VerifySLO_Pass(t *testing.T) {
	var method, path string
	var body []byte
	ts := stubServer(t, 200, `{"name":"checkout-slo","result":"PASS","sampleCount":120}`, &method, &path, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	verdict, err := client.VerifySLO(sloCriteria())
	if err != nil {
		t.Fatal(err)
	}
	if method != "PUT" {
		t.Errorf("expected PUT, got %s", method)
	}
	if path != "/mockserver/verifySLO" {
		t.Errorf("unexpected path %s", path)
	}
	if verdict.Result != "PASS" || verdict.SampleCount != 120 {
		t.Errorf("unexpected verdict: %+v", verdict)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(body, &m); err != nil {
		t.Fatalf("body not valid JSON: %v", err)
	}
	objectives := m["objectives"].([]interface{})
	if len(objectives) != 2 {
		t.Fatalf("expected 2 objectives, got %d", len(objectives))
	}
	obj0 := objectives[0].(map[string]interface{})
	if obj0["sli"] != "LATENCY_P95" || obj0["comparator"] != "LESS_THAN" || obj0["threshold"].(float64) != 250.0 {
		t.Errorf("unexpected objective[0]: %v", obj0)
	}
	window := m["window"].(map[string]interface{})
	if window["lookbackMillis"].(float64) != 60000 {
		t.Errorf("unexpected window: %v", window)
	}
}

func TestClient_VerifySLO_Fail(t *testing.T) {
	ts := stubServer(t, 406, `{"name":"checkout-slo","result":"FAIL","objectiveResults":[{"sli":"LATENCY_P95","result":"FAIL","observedValue":420.0}]}`, nil, nil, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	verdict, err := client.VerifySLO(sloCriteria())
	if err == nil {
		t.Fatal("expected error for FAIL verdict (406)")
	}
	var ve *VerificationError
	if !errors.As(err, &ve) {
		t.Fatalf("expected VerificationError, got %T: %v", err, err)
	}
	// The verdict is still decoded so the caller can inspect objective results.
	if verdict.Result != "FAIL" {
		t.Errorf("expected decoded FAIL verdict, got %+v", verdict)
	}
	if len(verdict.ObjectiveResults) != 1 || verdict.ObjectiveResults[0].ObservedValue == nil {
		t.Errorf("expected objective results with observedValue, got %+v", verdict.ObjectiveResults)
	}
}

func TestClient_VerifySLO_Disabled(t *testing.T) {
	// 403 is "SLO tracking is off"; it used to share 400 with malformed criteria, so a typo in
	// the criteria was reported to callers as a disabled feature.
	ts := stubServer(t, 403, `{"error":"SLO tracking not enabled"}`, nil, nil, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	_, err := client.VerifySLO(sloCriteria())
	if err == nil {
		t.Fatal("expected error for disabled (403)")
	}
	var fde *FeatureDisabledError
	if !errors.As(err, &fde) {
		t.Fatalf("expected FeatureDisabledError, got %T: %v", err, err)
	}
}

func TestClient_VerifySLO_MalformedCriteria(t *testing.T) {
	ts := stubServer(t, 400, `{"error":"objectives must be an array"}`, nil, nil, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	_, err := client.VerifySLO(sloCriteria())
	if err == nil {
		t.Fatal("expected error for malformed criteria (400)")
	}
	var fde *FeatureDisabledError
	if errors.As(err, &fde) {
		t.Fatalf("a malformed criteria must not be reported as a disabled feature: %v", err)
	}
	if !strings.Contains(err.Error(), "invalid SLO criteria") {
		t.Errorf("expected the error to name the criteria, got %v", err)
	}
}

// ---------------------------------------------------------------------------
// 4. Preemption
// ---------------------------------------------------------------------------

func TestClient_SetPreemption(t *testing.T) {
	var method, path string
	var body []byte
	ts := stubServer(t, 200, `{"state":"draining","inFlight":3,"mode":"both"}`, &method, &path, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	lastStream := int64(3)
	err := client.SetPreemption(PreemptionRequest{
		Mode:         "both",
		DrainMillis:  10000,
		TTLMillis:    60000,
		LastStreamID: &lastStream,
	})
	if err != nil {
		t.Fatal(err)
	}
	if method != "PUT" {
		t.Errorf("expected PUT, got %s", method)
	}
	if path != "/mockserver/preemption" {
		t.Errorf("unexpected path %s", path)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(body, &m); err != nil {
		t.Fatalf("body not valid JSON: %v", err)
	}
	if m["mode"] != "both" || m["drainMillis"].(float64) != 10000 || m["ttlMillis"].(float64) != 60000 {
		t.Errorf("unexpected body: %v", m)
	}
	if m["lastStreamId"].(float64) != 3 {
		t.Errorf("expected lastStreamId 3, got %v", m["lastStreamId"])
	}
}

func TestClient_PreemptionStatus(t *testing.T) {
	var method, path string
	ts := stubServer(t, 200, `{"state":"draining","inFlight":2,"drainRemainingMillis":5000,"mode":"goaway"}`, &method, &path, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	status, err := client.PreemptionStatus()
	if err != nil {
		t.Fatal(err)
	}
	if method != "GET" {
		t.Errorf("expected GET, got %s", method)
	}
	if path != "/mockserver/preemption" {
		t.Errorf("unexpected path %s", path)
	}
	if status.State != "draining" || status.InFlight != 2 || status.DrainRemainingMillis != 5000 || status.Mode != "goaway" {
		t.Errorf("unexpected status: %+v", status)
	}
}

func TestClient_ClearPreemption(t *testing.T) {
	var method, path string
	ts := stubServer(t, 200, `{"state":"inactive"}`, &method, &path, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	if err := client.ClearPreemption(); err != nil {
		t.Fatal(err)
	}
	if method != "DELETE" {
		t.Errorf("expected DELETE, got %s", method)
	}
	if path != "/mockserver/preemption" {
		t.Errorf("unexpected path %s", path)
	}
}

// ---------------------------------------------------------------------------
// 5. Chaos experiment
// ---------------------------------------------------------------------------

func TestClient_StartChaosExperiment(t *testing.T) {
	var method, path string
	var body []byte
	ts := stubServer(t, 200, `{"status":"started","name":"gradual-degradation"}`, &method, &path, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.StartChaosExperiment(ChaosExperiment{
		Name: "gradual-degradation",
		Loop: false,
		Stages: []ChaosExperimentStage{
			{
				DurationMillis: 10000,
				Profiles: map[string]ServiceChaosProfile{
					"api.example.com": {ErrorStatus: 500, ErrorProbability: 0.1},
				},
			},
			{
				DurationMillis: 10000,
				Profiles: map[string]ServiceChaosProfile{
					"api.example.com": {ErrorStatus: 500, ErrorProbability: 0.5},
				},
			},
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	if method != "PUT" {
		t.Errorf("expected PUT, got %s", method)
	}
	if path != "/mockserver/chaosExperiment" {
		t.Errorf("unexpected path %s", path)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(body, &m); err != nil {
		t.Fatalf("body not valid JSON: %v", err)
	}
	if m["name"] != "gradual-degradation" {
		t.Errorf("unexpected name: %v", m["name"])
	}
	stages := m["stages"].([]interface{})
	if len(stages) != 2 {
		t.Fatalf("expected 2 stages, got %d", len(stages))
	}
	stage0 := stages[0].(map[string]interface{})
	if stage0["durationMillis"].(float64) != 10000 {
		t.Errorf("unexpected durationMillis: %v", stage0["durationMillis"])
	}
	profiles := stage0["profiles"].(map[string]interface{})
	api := profiles["api.example.com"].(map[string]interface{})
	if api["errorStatus"].(float64) != 500 || api["errorProbability"].(float64) != 0.1 {
		t.Errorf("unexpected profile: %v", api)
	}
}

func TestClient_ChaosForbidden_IsNotAFeatureDisabledError(t *testing.T) {
	ts := stubServer(t, 403, "Forbidden for control plane", nil, nil, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	for name, err := range map[string]error{
		"StartChaosExperiment": client.StartChaosExperiment(ChaosExperiment{Name: "x"}),
		"SetServiceChaos":      client.SetServiceChaos("payments.internal:8443", ServiceChaosProfile{}, 0),
	} {
		if err == nil {
			t.Fatalf("%s: expected error for 403 status", name)
		}
		var fde *FeatureDisabledError
		if errors.As(err, &fde) {
			t.Errorf("%s: a 403 is control-plane authorization, not a disabled feature: %v", name, err)
		}
		if !strings.Contains(err.Error(), "control-plane authorization") || !strings.Contains(err.Error(), "Forbidden for control plane") {
			t.Errorf("%s: expected the error to name control-plane authorization and carry the body, got %v", name, err)
		}
	}
}

func TestClient_StartChaosExperiment_Invalid(t *testing.T) {
	ts := stubServer(t, 400, `{"error":"empty stages"}`, nil, nil, nil)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.StartChaosExperiment(ChaosExperiment{Name: "x"})
	if err == nil {
		t.Fatal("expected error for 400 status")
	}
}

// TestLoadStep_ChecksWebsiteExample builds exactly what the Go tab of
// load_injection.html (button_load_step_checks) builds and asserts the register
// body equals that example's REST API JSON.
func TestLoadStep_ChecksWebsiteExample(t *testing.T) {
	var body []byte
	ts := stubServer(t, 200, `{"name":"checked-scenario","state":"LOADED"}`, nil, nil, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)

	order := Request().Method("GET").Path("/api/orders/123").Build()
	order.SocketAddress = &SocketAddress{Host: "target", Port: 8080}

	scenario := LoadScenario{
		Name: "checked-scenario",
		Profile: &LoadProfile{
			Stages: []LoadStage{
				ConstantVusStage(5, 60000),
			},
		},
		Thresholds: []LoadThreshold{
			{Metric: LoadThresholdCheckFailureRate, Comparator: "LESS_THAN", Threshold: 0.01},
		},
		Steps: []LoadStep{
			{Request: &order,
				Checks: []LoadCheck{
					{Source: LoadCheckStatus, Comparator: LoadCheckEquals, Value: "200"},
					{Source: LoadCheckHeader, HeaderName: "Content-Type", Comparator: LoadCheckContains, Value: "application/json"},
					{Source: LoadCheckBodyJSONPath, JSONPath: "$.status", Comparator: LoadCheckEquals, Value: "CONFIRMED"},
				}},
		},
	}

	if _, err := client.LoadScenario(scenario); err != nil {
		t.Fatal(err)
	}

	want := decodeJSON(t, []byte(`{
    "name": "checked-scenario",
    "profile": { "stages": [ { "type": "VU", "vus": 5, "durationMillis": 60000 } ] },
    "thresholds": [
      { "metric": "CHECK_FAILURE_RATE", "comparator": "LESS_THAN", "threshold": 0.01 }
    ],
    "steps": [
      {
        "request": { "method": "GET", "path": "/api/orders/123",
                     "socketAddress": { "host": "target", "port": 8080 } },
        "checks": [
          { "source": "STATUS", "comparator": "EQUALS", "value": "200" },
          { "source": "HEADER", "headerName": "Content-Type", "comparator": "CONTAINS", "value": "application/json" },
          { "source": "BODY_JSONPATH", "jsonPath": "$.status", "comparator": "EQUALS", "value": "CONFIRMED" }
        ]
      }
    ]
  }`))
	if got := decodeJSON(t, body); !reflect.DeepEqual(want, got) {
		t.Errorf("website example JSON mismatch\ngot: %s", body)
	}
}

// TestLoadStep_EmptyChecksOmitted proves an empty (non-nil) Checks slice is
// left off the wire, as an unset one is.
func TestLoadStep_EmptyChecksOmitted(t *testing.T) {
	out, err := json.Marshal(LoadStep{Request: &HttpRequest{Path: "/x"}, Checks: []LoadCheck{}})
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(out), "checks") {
		t.Errorf("expected checks to be omitted, got %s", out)
	}
}

// TestClient_GetLoadScenario_ReadsChecks reads checks back from a server-style
// registry entry whose step carries a key the model does not declare ("valid",
// which the server echoes); decoding must not fail and the checks must
// re-marshal unchanged.
func TestClient_GetLoadScenario_ReadsChecks(t *testing.T) {
	step := `{
      "request": {"method": "GET", "path": "/api/orders/123"},
      "checks": [
        {"source": "STATUS", "comparator": "EQUALS", "value": "200"},
        {"source": "HEADER", "headerName": "Content-Type", "comparator": "CONTAINS", "value": "application/json"},
        {"source": "BODY_JSONPATH", "jsonPath": "$.total", "comparator": "GTE", "value": "10"}
      ]
    }`
	var withValid map[string]interface{}
	if err := json.Unmarshal([]byte(step), &withValid); err != nil {
		t.Fatal(err)
	}
	withValid["valid"] = true
	serverStep, _ := json.Marshal(withValid)

	ts := stubServer(t, 200, `{"name":"checked-scenario","state":"LOADED","definition":{"name":"checked-scenario","steps":[`+string(serverStep)+`]}}`, nil, nil, nil)
	defer ts.Close()

	entry, err := NewFromURL(ts.URL).GetLoadScenario("checked-scenario")
	if err != nil {
		t.Fatal(err)
	}
	if entry.Definition == nil || len(entry.Definition.Steps) != 1 {
		t.Fatalf("unexpected definition: %+v", entry.Definition)
	}
	checks := entry.Definition.Steps[0].Checks
	if len(checks) != 3 || checks[1].HeaderName != "Content-Type" || checks[2].JSONPath != "$.total" || checks[2].Comparator != LoadCheckGTE {
		t.Fatalf("checks not read: %+v", checks)
	}

	out, err := json.Marshal(entry.Definition.Steps[0])
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(decodeJSON(t, []byte(step)), decodeJSON(t, out)) {
		t.Errorf("step did not round-trip\nwant: %s\ngot : %s", step, out)
	}
}
