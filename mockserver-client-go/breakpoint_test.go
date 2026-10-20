package mockserver

import (
	"encoding/base64"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/gorilla/websocket"
)

// --- REST endpoint tests ---

func TestRegisterBreakpointMatcher_SendsCorrectPayload(t *testing.T) {
	var receivedPath string
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedPath = r.URL.Path
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(200)
		w.Write([]byte(`{"id":"bp-123","phases":["REQUEST","RESPONSE"]}`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	// Manually set a fake breakpoint WS with a client ID
	client.breakpointWS = &breakpointWSClient{
		clientID:            "test-client-id",
		requestHandlers:     make(map[string]BreakpointRequestHandler),
		responseHandlers:    make(map[string]BreakpointResponseHandler),
		streamFrameHandlers: make(map[string]BreakpointStreamFrameHandler),
	}

	id, err := client.AddBreakpoint(
		Request().Method("GET").Path("/api/.*"),
		[]BreakpointPhase{PhaseRequest, PhaseResponse},
		func(req map[string]interface{}) interface{} { return req },
		func(req, resp map[string]interface{}) map[string]interface{} { return resp },
		nil,
	)
	if err != nil {
		t.Fatal(err)
	}

	if receivedPath != "/mockserver/breakpoint/matcher" {
		t.Errorf("expected path /mockserver/breakpoint/matcher, got %s", receivedPath)
	}

	var payload map[string]interface{}
	if err := json.Unmarshal(receivedBody, &payload); err != nil {
		t.Fatal(err)
	}

	httpReq := payload["httpRequest"].(map[string]interface{})
	if httpReq["method"] != "GET" {
		t.Errorf("expected method GET, got %v", httpReq["method"])
	}
	if httpReq["path"] != "/api/.*" {
		t.Errorf("expected path /api/.*, got %v", httpReq["path"])
	}

	phases := payload["phases"].([]interface{})
	if len(phases) != 2 {
		t.Errorf("expected 2 phases, got %d", len(phases))
	}

	clientID := payload["clientId"].(string)
	if clientID != "test-client-id" {
		t.Errorf("expected clientId test-client-id, got %s", clientID)
	}

	if id != "bp-123" {
		t.Errorf("expected id bp-123, got %s", id)
	}
}

func TestListBreakpointMatchers(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/mockserver/breakpoint/matchers" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if r.Method != "GET" {
			t.Errorf("expected GET, got %s", r.Method)
		}
		w.WriteHeader(200)
		w.Write([]byte(`{"matchers":[{"id":"bp-1","httpRequest":{"path":"/test"},"phases":["REQUEST"],"clientId":"c1"}]}`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	list, err := client.ListBreakpointMatchers()
	if err != nil {
		t.Fatal(err)
	}
	if len(list.Matchers) != 1 {
		t.Fatalf("expected 1 matcher, got %d", len(list.Matchers))
	}
	if list.Matchers[0].ID != "bp-1" {
		t.Errorf("expected id bp-1, got %s", list.Matchers[0].ID)
	}
	if list.Matchers[0].ClientID != "c1" {
		t.Errorf("expected clientId c1, got %s", list.Matchers[0].ClientID)
	}
}

func TestRemoveBreakpointMatcher(t *testing.T) {
	var receivedBody []byte

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(200)
		w.Write([]byte(`{"status":"removed","id":"bp-1"}`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.RemoveBreakpointMatcher("bp-1")
	if err != nil {
		t.Fatal(err)
	}

	var payload map[string]string
	if err := json.Unmarshal(receivedBody, &payload); err != nil {
		t.Fatal(err)
	}
	if payload["id"] != "bp-1" {
		t.Errorf("expected id bp-1, got %s", payload["id"])
	}
}

func TestRemoveBreakpointMatcher_404(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(404)
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.RemoveBreakpointMatcher("nonexistent")
	if err == nil {
		t.Fatal("expected error for 404")
	}
	if !strings.Contains(err.Error(), "not found") {
		t.Errorf("expected 'not found' in error, got: %s", err.Error())
	}
}

func TestClearBreakpointMatchers(t *testing.T) {
	var receivedPath string
	var receivedMethod string

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedPath = r.URL.Path
		receivedMethod = r.Method
		w.WriteHeader(200)
		w.Write([]byte(`{"status":"cleared","count":3}`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	err := client.ClearBreakpointMatchers()
	if err != nil {
		t.Fatal(err)
	}
	if receivedPath != "/mockserver/breakpoint/matcher/clear" {
		t.Errorf("expected path /mockserver/breakpoint/matcher/clear, got %s", receivedPath)
	}
	if receivedMethod != "PUT" {
		t.Errorf("expected PUT, got %s", receivedMethod)
	}
}

// --- Message routing tests ---

func TestBreakpointRequestHandler_Routing(t *testing.T) {
	ws := newBreakpointWSClient()

	var handled bool
	ws.requestHandlers["bp-42"] = func(req map[string]interface{}) interface{} {
		handled = true
		return req
	}

	// Simulate incoming REQUEST message
	request := map[string]interface{}{
		"method": "GET",
		"path":   "/test",
		"headers": map[string]interface{}{
			"WebSocketCorrelationId":    []interface{}{"corr-1"},
			"X-MockServer-BreakpointId": []interface{}{"bp-42"},
		},
	}
	requestJSON, _ := json.Marshal(request)

	// We need a connection to send on; set up a mock ws server
	sentMessages := make(chan string, 10)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upgrader := websocket.Upgrader{}
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer conn.Close()
		for {
			_, msg, err := conn.ReadMessage()
			if err != nil {
				return
			}
			sentMessages <- string(msg)
		}
	}))
	defer srv.Close()

	wsURL := "ws" + strings.TrimPrefix(srv.URL, "http")
	conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatal(err)
	}
	ws.conn = conn
	defer conn.Close()

	ws.handleRequest(string(requestJSON))

	if !handled {
		t.Error("request handler was not called")
	}

	select {
	case msg := <-sentMessages:
		var env wsEnvelope
		if err := json.Unmarshal([]byte(msg), &env); err != nil {
			t.Fatal(err)
		}
		if env.Type != "org.mockserver.model.HttpRequest" {
			t.Errorf("expected HttpRequest type, got %s", env.Type)
		}
		var result map[string]interface{}
		if err := json.Unmarshal([]byte(env.Value), &result); err != nil {
			t.Fatal(err)
		}
		corrID := extractHeader(result, "WebSocketCorrelationId")
		if corrID != "corr-1" {
			t.Errorf("expected correlation id corr-1, got %s", corrID)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timeout waiting for sent message")
	}
}

func TestBreakpointRequestHandler_AbortWithResponse(t *testing.T) {
	ws := newBreakpointWSClient()

	ws.requestHandlers["bp-abort"] = func(req map[string]interface{}) interface{} {
		return map[string]interface{}{
			"statusCode":   502,
			"reasonPhrase": "Bad Gateway",
		}
	}

	request := map[string]interface{}{
		"method": "POST",
		"path":   "/abort-me",
		"headers": map[string]interface{}{
			"WebSocketCorrelationId":    []interface{}{"corr-abort"},
			"X-MockServer-BreakpointId": []interface{}{"bp-abort"},
		},
	}
	requestJSON, _ := json.Marshal(request)

	sentMessages := make(chan string, 10)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upgrader := websocket.Upgrader{}
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer conn.Close()
		for {
			_, msg, err := conn.ReadMessage()
			if err != nil {
				return
			}
			sentMessages <- string(msg)
		}
	}))
	defer srv.Close()

	wsURL := "ws" + strings.TrimPrefix(srv.URL, "http")
	conn, _, _ := websocket.DefaultDialer.Dial(wsURL, nil)
	ws.conn = conn
	defer conn.Close()

	ws.handleRequest(string(requestJSON))

	select {
	case msg := <-sentMessages:
		var env wsEnvelope
		json.Unmarshal([]byte(msg), &env)
		if env.Type != "org.mockserver.model.HttpResponse" {
			t.Errorf("expected HttpResponse type for abort, got %s", env.Type)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timeout")
	}
}

func TestBreakpointResponseHandler_Routing(t *testing.T) {
	ws := newBreakpointWSClient()

	var handledReq, handledResp map[string]interface{}
	ws.responseHandlers["bp-resp"] = func(req, resp map[string]interface{}) map[string]interface{} {
		handledReq = req
		handledResp = resp
		return resp
	}

	reqAndResp := map[string]interface{}{
		"httpRequest": map[string]interface{}{
			"method": "GET",
			"path":   "/api",
			"headers": map[string]interface{}{
				"WebSocketCorrelationId":    []interface{}{"corr-resp"},
				"X-MockServer-BreakpointId": []interface{}{"bp-resp"},
			},
		},
		"httpResponse": map[string]interface{}{
			"statusCode": float64(200),
			"body":       "hello",
		},
	}
	valueJSON, _ := json.Marshal(reqAndResp)

	sentMessages := make(chan string, 10)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upgrader := websocket.Upgrader{}
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer conn.Close()
		for {
			_, msg, err := conn.ReadMessage()
			if err != nil {
				return
			}
			sentMessages <- string(msg)
		}
	}))
	defer srv.Close()

	wsURL := "ws" + strings.TrimPrefix(srv.URL, "http")
	conn, _, _ := websocket.DefaultDialer.Dial(wsURL, nil)
	ws.conn = conn
	defer conn.Close()

	ws.handleResponse(string(valueJSON))

	if handledReq == nil {
		t.Error("response handler request not passed")
	}
	if handledResp == nil {
		t.Error("response handler response not passed")
	}

	select {
	case msg := <-sentMessages:
		var env wsEnvelope
		json.Unmarshal([]byte(msg), &env)
		if env.Type != "org.mockserver.model.HttpResponse" {
			t.Errorf("expected HttpResponse type, got %s", env.Type)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timeout")
	}
}

func TestBreakpointStreamFrameHandler_Routing(t *testing.T) {
	ws := newBreakpointWSClient()

	var handledFrame *PausedStreamFrame
	ws.streamFrameHandlers["bp-stream"] = func(frame *PausedStreamFrame) *StreamFrameDecision {
		handledFrame = frame
		return &StreamFrameDecision{
			CorrelationID: frame.CorrelationID,
			Action:        "MODIFY",
			Body:          base64.StdEncoding.EncodeToString([]byte("modified")),
		}
	}

	frame := PausedStreamFrame{
		CorrelationID:  "corr-frame-1",
		StreamID:       "stream-1",
		SequenceNumber: 0,
		Direction:      "OUTBOUND",
		Phase:          "RESPONSE_STREAM",
		Body:           base64.StdEncoding.EncodeToString([]byte("original")),
		RequestMethod:  "GET",
		RequestPath:    "/stream",
		BreakpointID:   "bp-stream",
	}
	frameJSON, _ := json.Marshal(frame)

	sentMessages := make(chan string, 10)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upgrader := websocket.Upgrader{}
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer conn.Close()
		for {
			_, msg, err := conn.ReadMessage()
			if err != nil {
				return
			}
			sentMessages <- string(msg)
		}
	}))
	defer srv.Close()

	wsURL := "ws" + strings.TrimPrefix(srv.URL, "http")
	conn, _, _ := websocket.DefaultDialer.Dial(wsURL, nil)
	ws.conn = conn
	defer conn.Close()

	ws.handleStreamFrame(string(frameJSON))

	if handledFrame == nil {
		t.Fatal("stream frame handler not called")
	}
	if handledFrame.CorrelationID != "corr-frame-1" {
		t.Errorf("expected correlationId corr-frame-1, got %s", handledFrame.CorrelationID)
	}
	if handledFrame.BreakpointID != "bp-stream" {
		t.Errorf("expected breakpointId bp-stream, got %s", handledFrame.BreakpointID)
	}

	select {
	case msg := <-sentMessages:
		var env wsEnvelope
		json.Unmarshal([]byte(msg), &env)
		if env.Type != "org.mockserver.serialization.model.StreamFrameDecisionDTO" {
			t.Errorf("expected StreamFrameDecisionDTO type, got %s", env.Type)
		}
		var decision StreamFrameDecision
		json.Unmarshal([]byte(env.Value), &decision)
		if decision.Action != "MODIFY" {
			t.Errorf("expected MODIFY, got %s", decision.Action)
		}
		if decision.CorrelationID != "corr-frame-1" {
			t.Errorf("expected correlationId echoed, got %s", decision.CorrelationID)
		}
		decoded, _ := base64.StdEncoding.DecodeString(decision.Body)
		if string(decoded) != "modified" {
			t.Errorf("expected body 'modified', got '%s'", string(decoded))
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timeout")
	}
}

func TestBreakpointStreamFrameHandler_AutoContinueNoHandler(t *testing.T) {
	ws := newBreakpointWSClient()
	// No handler registered

	frame := PausedStreamFrame{
		CorrelationID: "corr-no-handler",
		StreamID:      "stream-2",
		Phase:         "RESPONSE_STREAM",
		Body:          base64.StdEncoding.EncodeToString([]byte("data")),
		BreakpointID:  "unknown-bp",
	}
	frameJSON, _ := json.Marshal(frame)

	sentMessages := make(chan string, 10)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upgrader := websocket.Upgrader{}
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer conn.Close()
		for {
			_, msg, err := conn.ReadMessage()
			if err != nil {
				return
			}
			sentMessages <- string(msg)
		}
	}))
	defer srv.Close()

	wsURL := "ws" + strings.TrimPrefix(srv.URL, "http")
	conn, _, _ := websocket.DefaultDialer.Dial(wsURL, nil)
	ws.conn = conn
	defer conn.Close()

	ws.handleStreamFrame(string(frameJSON))

	select {
	case msg := <-sentMessages:
		var env wsEnvelope
		json.Unmarshal([]byte(msg), &env)
		var decision StreamFrameDecision
		json.Unmarshal([]byte(env.Value), &decision)
		if decision.Action != "CONTINUE" {
			t.Errorf("expected auto-CONTINUE, got %s", decision.Action)
		}
		if decision.CorrelationID != "corr-no-handler" {
			t.Errorf("expected correlation id echoed, got %s", decision.CorrelationID)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timeout")
	}
}

func TestBreakpointRequestHandler_AutoContinueOnPanic(t *testing.T) {
	ws := newBreakpointWSClient()

	ws.requestHandlers["bp-panic"] = func(req map[string]interface{}) interface{} {
		panic("test panic")
	}

	request := map[string]interface{}{
		"method": "GET",
		"path":   "/panic",
		"headers": map[string]interface{}{
			"WebSocketCorrelationId":    []interface{}{"corr-panic"},
			"X-MockServer-BreakpointId": []interface{}{"bp-panic"},
		},
	}
	requestJSON, _ := json.Marshal(request)

	sentMessages := make(chan string, 10)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upgrader := websocket.Upgrader{}
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer conn.Close()
		for {
			_, msg, err := conn.ReadMessage()
			if err != nil {
				return
			}
			sentMessages <- string(msg)
		}
	}))
	defer srv.Close()

	wsURL := "ws" + strings.TrimPrefix(srv.URL, "http")
	conn, _, _ := websocket.DefaultDialer.Dial(wsURL, nil)
	ws.conn = conn
	defer conn.Close()

	ws.handleRequest(string(requestJSON))

	select {
	case msg := <-sentMessages:
		var env wsEnvelope
		json.Unmarshal([]byte(msg), &env)
		// On panic, should auto-continue with original request
		if env.Type != "org.mockserver.model.HttpRequest" {
			t.Errorf("expected auto-continue with HttpRequest, got %s", env.Type)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timeout")
	}
}

func TestBreakpointPerIDSelection(t *testing.T) {
	ws := newBreakpointWSClient()

	var mu sync.Mutex
	handledIDs := make([]string, 0)

	ws.requestHandlers["bp-A"] = func(req map[string]interface{}) interface{} {
		mu.Lock()
		handledIDs = append(handledIDs, "A")
		mu.Unlock()
		return req
	}
	ws.requestHandlers["bp-B"] = func(req map[string]interface{}) interface{} {
		mu.Lock()
		handledIDs = append(handledIDs, "B")
		mu.Unlock()
		return req
	}

	sentMessages := make(chan string, 10)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upgrader := websocket.Upgrader{}
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer conn.Close()
		for {
			_, msg, err := conn.ReadMessage()
			if err != nil {
				return
			}
			sentMessages <- string(msg)
		}
	}))
	defer srv.Close()

	wsURL := "ws" + strings.TrimPrefix(srv.URL, "http")
	conn, _, _ := websocket.DefaultDialer.Dial(wsURL, nil)
	ws.conn = conn
	defer conn.Close()

	// Dispatch to bp-B
	requestB := map[string]interface{}{
		"method": "GET",
		"path":   "/b",
		"headers": map[string]interface{}{
			"WebSocketCorrelationId":    []interface{}{"corr-B"},
			"X-MockServer-BreakpointId": []interface{}{"bp-B"},
		},
	}
	requestBJSON, _ := json.Marshal(requestB)
	ws.handleRequest(string(requestBJSON))

	// Dispatch to bp-A
	requestA := map[string]interface{}{
		"method": "GET",
		"path":   "/a",
		"headers": map[string]interface{}{
			"WebSocketCorrelationId":    []interface{}{"corr-A"},
			"X-MockServer-BreakpointId": []interface{}{"bp-A"},
		},
	}
	requestAJSON, _ := json.Marshal(requestA)
	ws.handleRequest(string(requestAJSON))

	// Drain messages
	for i := 0; i < 2; i++ {
		select {
		case <-sentMessages:
		case <-time.After(2 * time.Second):
			t.Fatal("timeout")
		}
	}

	mu.Lock()
	defer mu.Unlock()
	if len(handledIDs) != 2 {
		t.Fatalf("expected 2 handled, got %d", len(handledIDs))
	}
	if handledIDs[0] != "B" {
		t.Errorf("expected first handled to be B, got %s", handledIDs[0])
	}
	if handledIDs[1] != "A" {
		t.Errorf("expected second handled to be A, got %s", handledIDs[1])
	}
}

// --- PausedStreamFrame.BodyBytes ---

func TestPausedStreamFrame_BodyBytes(t *testing.T) {
	frame := PausedStreamFrame{
		Body: base64.StdEncoding.EncodeToString([]byte("hello stream")),
	}
	decoded, err := frame.BodyBytes()
	if err != nil {
		t.Fatal(err)
	}
	if string(decoded) != "hello stream" {
		t.Errorf("expected 'hello stream', got '%s'", string(decoded))
	}
}

// --- Decision helpers ---

func TestContinueFrame(t *testing.T) {
	d := ContinueFrame("corr-1")
	if d.Action != "CONTINUE" {
		t.Errorf("expected CONTINUE, got %s", d.Action)
	}
	if d.CorrelationID != "corr-1" {
		t.Errorf("expected corr-1, got %s", d.CorrelationID)
	}
}

func TestModifyFrame(t *testing.T) {
	d := ModifyFrame("corr-2", []byte("new data"))
	if d.Action != "MODIFY" {
		t.Errorf("expected MODIFY, got %s", d.Action)
	}
	decoded, _ := base64.StdEncoding.DecodeString(d.Body)
	if string(decoded) != "new data" {
		t.Errorf("expected 'new data', got '%s'", string(decoded))
	}
}

func TestDropFrame(t *testing.T) {
	d := DropFrame("corr-3")
	if d.Action != "DROP" {
		t.Errorf("expected DROP, got %s", d.Action)
	}
}

func TestInjectFrame(t *testing.T) {
	d := InjectFrame("corr-4", []byte("extra"))
	if d.Action != "INJECT" {
		t.Errorf("expected INJECT, got %s", d.Action)
	}
}

func TestCloseFrame(t *testing.T) {
	d := CloseFrame("corr-5")
	if d.Action != "CLOSE" {
		t.Errorf("expected CLOSE, got %s", d.Action)
	}
}

// --- Envelope JSON tests ---

func TestWsEnvelope_JSON(t *testing.T) {
	env := wsEnvelope{
		Type:  "org.mockserver.model.HttpRequest",
		Value: `{"method":"GET","path":"/test"}`,
	}
	data, err := json.Marshal(env)
	if err != nil {
		t.Fatal(err)
	}

	var parsed map[string]interface{}
	json.Unmarshal(data, &parsed)

	if parsed["type"] != "org.mockserver.model.HttpRequest" {
		t.Errorf("unexpected type: %v", parsed["type"])
	}
	// Value is a JSON string (double-encoded)
	valueStr, ok := parsed["value"].(string)
	if !ok {
		t.Fatal("value should be a string (double-encoded)")
	}
	var inner map[string]interface{}
	json.Unmarshal([]byte(valueStr), &inner)
	if inner["method"] != "GET" {
		t.Errorf("unexpected inner method: %v", inner["method"])
	}
}

// --- BreakpointMatcherRegistration JSON ---

func TestBreakpointMatcherRegistration_JSON(t *testing.T) {
	req := Request().Method("POST").Path("/api/users").Build()
	reg := BreakpointMatcherRegistration{
		HttpRequest: &req,
		Phases:      []BreakpointPhase{PhaseRequest, PhaseResponseStream},
		ClientID:    "my-client",
	}

	data, err := json.Marshal(reg)
	if err != nil {
		t.Fatal(err)
	}

	var m map[string]interface{}
	json.Unmarshal(data, &m)

	if m["clientId"] != "my-client" {
		t.Errorf("expected clientId my-client, got %v", m["clientId"])
	}

	phases := m["phases"].([]interface{})
	if len(phases) != 2 {
		t.Fatalf("expected 2 phases, got %d", len(phases))
	}
	if phases[0] != "REQUEST" {
		t.Errorf("expected first phase REQUEST, got %v", phases[0])
	}
	if phases[1] != "RESPONSE_STREAM" {
		t.Errorf("expected second phase RESPONSE_STREAM, got %v", phases[1])
	}
}

// --- extractHeader ---

func TestExtractHeader_ArrayValue(t *testing.T) {
	obj := map[string]interface{}{
		"headers": map[string]interface{}{
			"X-Custom": []interface{}{"val1", "val2"},
		},
	}
	if v := extractHeader(obj, "X-Custom"); v != "val1" {
		t.Errorf("expected val1, got %s", v)
	}
}

func TestExtractHeader_StringValue(t *testing.T) {
	obj := map[string]interface{}{
		"headers": map[string]interface{}{
			"X-Custom": "single",
		},
	}
	if v := extractHeader(obj, "X-Custom"); v != "single" {
		t.Errorf("expected single, got %s", v)
	}
}

func TestExtractHeader_CaseInsensitive(t *testing.T) {
	obj := map[string]interface{}{
		"headers": map[string]interface{}{
			"websocketcorrelationid": []interface{}{"corr-123"},
		},
	}
	if v := extractHeader(obj, "WebSocketCorrelationId"); v != "corr-123" {
		t.Errorf("expected corr-123, got %s", v)
	}
}

func TestExtractHeader_NoHeaders(t *testing.T) {
	obj := map[string]interface{}{
		"method": "GET",
	}
	if v := extractHeader(obj, "X-Missing"); v != "" {
		t.Errorf("expected empty, got %s", v)
	}
}

// fakeCallbackWebSocketServer upgrades /_mockserver_callback_websocket, records each
// upgrade request's headers and registers the socket with a client id.
func fakeCallbackWebSocketServer(t *testing.T) (*httptest.Server, chan http.Header) {
	t.Helper()
	upgrades := make(chan http.Header, 10)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/_mockserver_callback_websocket" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		upgrades <- r.Header.Clone()
		conn, err := (&websocket.Upgrader{}).Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer conn.Close()
		_ = conn.WriteMessage(websocket.TextMessage, []byte(`{"type":"org.mockserver.serialization.model.WebSocketClientIdDTO","value":"{\"clientId\":\"client-one\"}"}`))
		for {
			if _, _, err := conn.ReadMessage(); err != nil {
				return
			}
		}
	}))
	return srv, upgrades
}

func callbackWebSocketUpgradeHeader(t *testing.T, opts ...Option) http.Header {
	t.Helper()
	srv, upgrades := fakeCallbackWebSocketServer(t)
	defer srv.Close()
	client := NewFromURL(srv.URL, opts...)
	if err := client.ensureBreakpointWS(); err != nil {
		t.Fatalf("callback WebSocket connect failed: %v", err)
	}
	defer client.breakpointWS.close()
	select {
	case header := <-upgrades:
		return header
	case <-time.After(2 * time.Second):
		t.Fatal("no WebSocket upgrade received")
		return nil
	}
}

func TestCallbackWebSocket_SendsControlPlaneBearerToken(t *testing.T) {
	header := callbackWebSocketUpgradeHeader(t, WithControlPlaneBearerToken("my-jwt-token"))
	if got := header.Get("Authorization"); got != "Bearer my-jwt-token" {
		t.Errorf("expected Authorization 'Bearer my-jwt-token' on the upgrade, got %q", got)
	}
}

func TestCallbackWebSocket_UsesBearerTokenSupplier(t *testing.T) {
	header := callbackWebSocketUpgradeHeader(t, WithControlPlaneBearerTokenSupplier(func() string { return "supplied" }))
	if got := header.Get("Authorization"); got != "Bearer supplied" {
		t.Errorf("expected Authorization 'Bearer supplied' on the upgrade, got %q", got)
	}
}

func TestCallbackWebSocket_NoAuthorizationWithoutToken(t *testing.T) {
	for name, opts := range map[string][]Option{
		"no token":    nil,
		"empty token": {WithControlPlaneBearerTokenSupplier(func() string { return "" })},
	} {
		t.Run(name, func(t *testing.T) {
			header := callbackWebSocketUpgradeHeader(t, opts...)
			if _, present := header["Authorization"]; present {
				t.Errorf("expected no Authorization header on the upgrade, got %q", header.Get("Authorization"))
			}
		})
	}
}
