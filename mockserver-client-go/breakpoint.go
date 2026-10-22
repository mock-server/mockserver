package mockserver

import (
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/gorilla/websocket"
)

// BreakpointPhase represents a breakpoint interception phase.
type BreakpointPhase string

const (
	PhaseRequest        BreakpointPhase = "REQUEST"
	PhaseResponse       BreakpointPhase = "RESPONSE"
	PhaseResponseStream BreakpointPhase = "RESPONSE_STREAM"
	PhaseInboundStream  BreakpointPhase = "INBOUND_STREAM"
)

// BreakpointMatcherRegistration is the request body for registering a breakpoint matcher.
type BreakpointMatcherRegistration struct {
	HttpRequest interface{}       `json:"httpRequest"`
	Phases      []BreakpointPhase `json:"phases"`
	ClientID    string            `json:"clientId,omitempty"`
}

// BreakpointMatcherResponse is the response from registering a breakpoint matcher.
type BreakpointMatcherResponse struct {
	ID     string            `json:"id"`
	Phases []BreakpointPhase `json:"phases"`
}

// BreakpointMatcherEntry is one entry in the list of registered matchers.
type BreakpointMatcherEntry struct {
	ID          string            `json:"id"`
	HttpRequest json.RawMessage   `json:"httpRequest"`
	Phases      []BreakpointPhase `json:"phases"`
	ClientID    string            `json:"clientId,omitempty"`
}

// BreakpointMatcherList is the response from listing all matchers.
type BreakpointMatcherList struct {
	Matchers []BreakpointMatcherEntry `json:"matchers"`
}

// PausedStreamFrame is the server-to-client message for a held stream frame.
type PausedStreamFrame struct {
	CorrelationID  string `json:"correlationId"`
	StreamID       string `json:"streamId"`
	SequenceNumber int    `json:"sequenceNumber"`
	Direction      string `json:"direction"`
	Phase          string `json:"phase"`
	Body           string `json:"body"`
	RequestMethod  string `json:"requestMethod,omitempty"`
	RequestPath    string `json:"requestPath,omitempty"`
	BreakpointID   string `json:"breakpointId,omitempty"`
}

// BodyBytes returns the decoded body bytes (base64 decoded).
func (f *PausedStreamFrame) BodyBytes() ([]byte, error) {
	return base64.StdEncoding.DecodeString(f.Body)
}

// StreamFrameDecision is the client-to-server reply for a stream frame.
type StreamFrameDecision struct {
	CorrelationID string `json:"correlationId"`
	Action        string `json:"action"`
	Body          string `json:"body,omitempty"`
}

// ContinueFrame creates a CONTINUE decision for the given frame.
func ContinueFrame(correlationID string) StreamFrameDecision {
	return StreamFrameDecision{CorrelationID: correlationID, Action: "CONTINUE"}
}

// ModifyFrame creates a MODIFY decision with replacement bytes.
func ModifyFrame(correlationID string, body []byte) StreamFrameDecision {
	return StreamFrameDecision{
		CorrelationID: correlationID,
		Action:        "MODIFY",
		Body:          base64.StdEncoding.EncodeToString(body),
	}
}

// DropFrame creates a DROP decision.
func DropFrame(correlationID string) StreamFrameDecision {
	return StreamFrameDecision{CorrelationID: correlationID, Action: "DROP"}
}

// InjectFrame creates an INJECT decision (original + extra frame).
func InjectFrame(correlationID string, extraBody []byte) StreamFrameDecision {
	return StreamFrameDecision{
		CorrelationID: correlationID,
		Action:        "INJECT",
		Body:          base64.StdEncoding.EncodeToString(extraBody),
	}
}

// CloseFrame creates a CLOSE decision.
func CloseFrame(correlationID string) StreamFrameDecision {
	return StreamFrameDecision{CorrelationID: correlationID, Action: "CLOSE"}
}

// BreakpointRequestHandler handles a paused REQUEST phase exchange.
// Return an HttpRequest (continue/modify) or HttpResponse (abort).
// Return nil to auto-continue with the original request.
type BreakpointRequestHandler func(request map[string]interface{}) interface{}

// BreakpointResponseHandler handles a paused RESPONSE phase exchange.
// Return an HttpResponse (continue/modify). Return nil to auto-continue.
type BreakpointResponseHandler func(request, response map[string]interface{}) map[string]interface{}

// BreakpointStreamFrameHandler handles a paused stream frame.
// Return nil to auto-continue.
type BreakpointStreamFrameHandler func(frame *PausedStreamFrame) *StreamFrameDecision

// wsEnvelope is the WebSocket message envelope used by MockServer.
type wsEnvelope struct {
	Type  string `json:"type"`
	Value string `json:"value"`
}

// wsClientIDDTO is the server's registration reply.
type wsClientIDDTO struct {
	ClientID string `json:"clientId"`
}

// breakpointWSClient manages the callback WebSocket connection for breakpoints.
type breakpointWSClient struct {
	mu       sync.RWMutex
	writeMu  sync.Mutex // serializes all conn.WriteMessage calls (gorilla forbids concurrent writers)
	conn     *websocket.Conn
	clientID string
	done     chan struct{}
	dead     int32 // atomic; set to 1 when the read loop exits (connection is no longer usable)

	requestHandlers     map[string]BreakpointRequestHandler
	responseHandlers    map[string]BreakpointResponseHandler
	streamFrameHandlers map[string]BreakpointStreamFrameHandler

	// Object/closure-callback handlers. Request frames that carry no breakpoint
	// id are object-callback frames and route here (see handleRequest). A single
	// handler per WS client is the common, correct case — callers narrow with the
	// request matcher.
	objectResponseHandler ObjectResponseCallback
	objectForwardHandler  ObjectForwardCallback
}

func newBreakpointWSClient() *breakpointWSClient {
	return &breakpointWSClient{
		done:                make(chan struct{}),
		requestHandlers:     make(map[string]BreakpointRequestHandler),
		responseHandlers:    make(map[string]BreakpointResponseHandler),
		streamFrameHandlers: make(map[string]BreakpointStreamFrameHandler),
	}
}

// connect establishes the WebSocket connection and performs registration.
// authorizationSupplier, when non-nil and returning a non-empty value, adds it
// as the Authorization header of the upgrade request (a bearer token or Basic
// credentials). tlsConfig, when non-nil, is used for a wss connection.
func (ws *breakpointWSClient) connect(baseURL string, authorizationSupplier func() string, tlsConfig *tls.Config) error {
	u, err := url.Parse(baseURL)
	if err != nil {
		return fmt.Errorf("mockserver: parse base URL: %w", err)
	}

	scheme := "ws"
	if u.Scheme == "https" {
		scheme = "wss"
	}
	path := strings.TrimRight(u.Path, "/")
	wsURL := fmt.Sprintf("%s://%s%s/_mockserver_callback_websocket", scheme, u.Host, path)

	dialer := &websocket.Dialer{
		HandshakeTimeout: 30 * time.Second,
		TLSClientConfig:  tlsConfig,
	}
	var header http.Header
	if authorizationSupplier != nil {
		if authorization := authorizationSupplier(); authorization != "" {
			header = http.Header{"Authorization": []string{authorization}}
		}
	}
	conn, _, err := dialer.Dial(wsURL, header)
	if err != nil {
		return fmt.Errorf("mockserver: websocket connect: %w", err)
	}
	ws.conn = conn

	// Read the registration reply -- the server sends a WebSocketClientIdDTO
	_, msgBytes, err := conn.ReadMessage()
	if err != nil {
		conn.Close()
		return fmt.Errorf("mockserver: read registration: %w", err)
	}

	var env wsEnvelope
	if err := json.Unmarshal(msgBytes, &env); err != nil {
		conn.Close()
		return fmt.Errorf("mockserver: parse registration envelope: %w", err)
	}

	if env.Type == "org.mockserver.serialization.model.WebSocketClientIdDTO" {
		var regDTO wsClientIDDTO
		if err := json.Unmarshal([]byte(env.Value), &regDTO); err != nil {
			conn.Close()
			return fmt.Errorf("mockserver: parse client id: %w", err)
		}
		ws.clientID = regDTO.ClientID
	} else {
		conn.Close()
		return fmt.Errorf("mockserver: unexpected registration message type: %s", env.Type)
	}

	// Start read loop
	go ws.readLoop()
	return nil
}

func (ws *breakpointWSClient) readLoop() {
	defer func() {
		atomic.StoreInt32(&ws.dead, 1)
		close(ws.done)
	}()
	for {
		_, msgBytes, err := ws.conn.ReadMessage()
		if err != nil {
			log.Printf("mockserver: breakpoint ws read loop terminated: %v", err)
			return
		}

		var env wsEnvelope
		if err := json.Unmarshal(msgBytes, &env); err != nil {
			continue
		}

		switch env.Type {
		case "org.mockserver.model.HttpRequest":
			ws.handleRequest(env.Value)
		case "org.mockserver.model.HttpRequestAndHttpResponse":
			ws.handleResponse(env.Value)
		case "org.mockserver.serialization.model.PausedStreamFrameDTO":
			ws.handleStreamFrame(env.Value)
		case "org.mockserver.serialization.model.WebSocketClientIdDTO":
			// Already handled during connect, ignore subsequent
		}
	}
}

func (ws *breakpointWSClient) handleRequest(valueJSON string) {
	var request map[string]interface{}
	if err := json.Unmarshal([]byte(valueJSON), &request); err != nil {
		return
	}

	correlationID := extractHeader(request, "WebSocketCorrelationId")
	breakpointID := extractHeader(request, "X-MockServer-BreakpointId")

	// A request frame with no breakpoint id is an object/closure-callback frame
	// (the breakpoint dispatch always tags its frames with X-MockServer-BreakpointId).
	// Route it to the registered object-callback handler rather than the
	// breakpoint request handlers.
	if breakpointID == "" {
		ws.mu.RLock()
		respHandler := ws.objectResponseHandler
		fwdHandler := ws.objectForwardHandler
		ws.mu.RUnlock()
		if respHandler != nil {
			ws.handleObjectResponseCallback(request, correlationID, respHandler)
			return
		}
		if fwdHandler != nil {
			ws.handleObjectForwardCallback(request, correlationID, fwdHandler)
			return
		}
		// No object-callback handler registered; fall through to the breakpoint
		// path which will auto-continue with the original request.
	}

	ws.mu.RLock()
	handler := ws.requestHandlers[breakpointID]
	ws.mu.RUnlock()

	var result interface{}
	if handler != nil {
		func() {
			defer func() {
				if r := recover(); r != nil {
					result = nil
				}
			}()
			result = handler(request)
		}()
	}

	// Auto-continue if no handler or nil result
	if result == nil {
		result = request
	}

	// Determine type and ensure correlation id is echoed
	resultType := "org.mockserver.model.HttpRequest"
	if resultMap, ok := result.(map[string]interface{}); ok {
		if _, hasStatus := resultMap["statusCode"]; hasStatus {
			resultType = "org.mockserver.model.HttpResponse"
		}
		setHeader(resultMap, "WebSocketCorrelationId", correlationID)
	}

	ws.sendEnvelope(resultType, result)
}

func (ws *breakpointWSClient) handleResponse(valueJSON string) {
	var reqAndResp struct {
		HttpRequest  map[string]interface{} `json:"httpRequest"`
		HttpResponse map[string]interface{} `json:"httpResponse"`
	}
	if err := json.Unmarshal([]byte(valueJSON), &reqAndResp); err != nil {
		return
	}

	correlationID := extractHeader(reqAndResp.HttpRequest, "WebSocketCorrelationId")
	breakpointID := extractHeader(reqAndResp.HttpRequest, "X-MockServer-BreakpointId")

	ws.mu.RLock()
	handler := ws.responseHandlers[breakpointID]
	ws.mu.RUnlock()

	var result map[string]interface{}
	if handler != nil {
		func() {
			defer func() {
				if r := recover(); r != nil {
					result = nil
				}
			}()
			result = handler(reqAndResp.HttpRequest, reqAndResp.HttpResponse)
		}()
	}

	// Auto-continue if no handler or nil result
	if result == nil {
		result = reqAndResp.HttpResponse
		if result == nil {
			result = make(map[string]interface{})
		}
	}

	setHeader(result, "WebSocketCorrelationId", correlationID)
	ws.sendEnvelope("org.mockserver.model.HttpResponse", result)
}

func (ws *breakpointWSClient) handleStreamFrame(valueJSON string) {
	var frame PausedStreamFrame
	if err := json.Unmarshal([]byte(valueJSON), &frame); err != nil {
		return
	}

	ws.mu.RLock()
	handler := ws.streamFrameHandlers[frame.BreakpointID]
	ws.mu.RUnlock()

	var decision *StreamFrameDecision
	if handler != nil {
		func() {
			defer func() {
				if r := recover(); r != nil {
					decision = nil
				}
			}()
			decision = handler(&frame)
		}()
	}

	// Auto-continue if no handler or nil result
	if decision == nil {
		d := ContinueFrame(frame.CorrelationID)
		decision = &d
	} else {
		decision.CorrelationID = frame.CorrelationID
	}

	ws.sendEnvelope("org.mockserver.serialization.model.StreamFrameDecisionDTO", decision)
}

func (ws *breakpointWSClient) sendEnvelope(typeName string, value interface{}) {
	valueBytes, err := json.Marshal(value)
	if err != nil {
		log.Printf("mockserver: breakpoint ws marshal error: %v", err)
		return
	}
	env := wsEnvelope{
		Type:  typeName,
		Value: string(valueBytes),
	}
	envBytes, err := json.Marshal(env)
	if err != nil {
		log.Printf("mockserver: breakpoint ws envelope marshal error: %v", err)
		return
	}
	ws.writeMu.Lock()
	defer ws.writeMu.Unlock()
	if ws.conn != nil {
		_ = ws.conn.WriteMessage(websocket.TextMessage, envBytes)
	}
}

func (ws *breakpointWSClient) close() {
	if ws.conn != nil {
		// Send a close frame under the write lock, then close the connection.
		// conn.Close() is safe to call even if the read loop is active.
		ws.writeMu.Lock()
		_ = ws.conn.WriteMessage(websocket.CloseMessage,
			websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))
		ws.writeMu.Unlock()
		ws.conn.Close()
	}
}

// isDead returns true if the read loop has exited (connection no longer usable).
func (ws *breakpointWSClient) isDead() bool {
	return atomic.LoadInt32(&ws.dead) == 1
}

// extractHeader extracts the first value of a header from a request map.
// Headers in MockServer JSON can be map[string][]string or map[string]string.
func extractHeader(obj map[string]interface{}, name string) string {
	headers, ok := obj["headers"]
	if !ok {
		return ""
	}
	headersMap, ok := headers.(map[string]interface{})
	if !ok {
		return ""
	}
	// Case-insensitive search
	for k, v := range headersMap {
		if strings.EqualFold(k, name) {
			switch val := v.(type) {
			case string:
				return val
			case []interface{}:
				if len(val) > 0 {
					if s, ok := val[0].(string); ok {
						return s
					}
				}
			}
		}
	}
	return ""
}

// setHeader sets a header value on a request/response map.
func setHeader(obj map[string]interface{}, name, value string) {
	if obj == nil || value == "" {
		return
	}
	headers, ok := obj["headers"]
	if !ok {
		obj["headers"] = map[string]interface{}{name: []interface{}{value}}
		return
	}
	headersMap, ok := headers.(map[string]interface{})
	if !ok {
		obj["headers"] = map[string]interface{}{name: []interface{}{value}}
		return
	}
	headersMap[name] = []interface{}{value}
}

// --- Client breakpoint methods ---

// ensureBreakpointWS lazily creates the WS connection.
// Uses double-checked locking to avoid leaking connections under concurrent calls.
// If the existing connection's read loop has exited, it is replaced transparently.
func (c *Client) ensureBreakpointWS() error {
	c.bpMu.Lock()
	defer c.bpMu.Unlock()

	if c.breakpointWS != nil && !c.breakpointWS.isDead() {
		return nil
	}
	// Close the old dead connection if present
	if c.breakpointWS != nil {
		c.breakpointWS.close()
		c.breakpointWS = nil
	}
	if c.tlsConfigErr != nil {
		return c.tlsConfigErr
	}
	ws := newBreakpointWSClient()
	if err := ws.connect(c.baseURL, c.authorization, c.webSocketTLSConfig()); err != nil {
		return err
	}
	c.breakpointWS = ws
	return nil
}

// AddBreakpoint registers a breakpoint matcher with the given phases and handlers.
// Returns the server-assigned breakpoint id.
func (c *Client) AddBreakpoint(
	matcher *RequestBuilder,
	phases []BreakpointPhase,
	requestHandler BreakpointRequestHandler,
	responseHandler BreakpointResponseHandler,
	streamFrameHandler BreakpointStreamFrameHandler,
) (string, error) {
	if matcher == nil {
		return "", fmt.Errorf("mockserver: AddBreakpoint requires a non-nil matcher")
	}
	if len(phases) == 0 {
		return "", fmt.Errorf("mockserver: AddBreakpoint requires at least one phase")
	}

	if err := c.ensureBreakpointWS(); err != nil {
		return "", err
	}

	req := matcher.Build()
	reg := BreakpointMatcherRegistration{
		HttpRequest: &req,
		Phases:      phases,
		ClientID:    c.breakpointWS.clientID,
	}

	body, err := json.Marshal(reg)
	if err != nil {
		return "", fmt.Errorf("mockserver: marshal breakpoint matcher: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/breakpoint/matcher", body, nil)
	if err != nil {
		return "", err
	}
	if statusCode >= 400 {
		return "", fmt.Errorf("mockserver: register breakpoint failed (status %d): %s", statusCode, string(respBody))
	}

	var result BreakpointMatcherResponse
	if err := json.Unmarshal(respBody, &result); err != nil {
		return "", fmt.Errorf("mockserver: unmarshal breakpoint response: %w", err)
	}

	// Register handlers for this breakpoint id
	c.breakpointWS.mu.Lock()
	if requestHandler != nil {
		c.breakpointWS.requestHandlers[result.ID] = requestHandler
	}
	if responseHandler != nil {
		c.breakpointWS.responseHandlers[result.ID] = responseHandler
	}
	if streamFrameHandler != nil {
		c.breakpointWS.streamFrameHandlers[result.ID] = streamFrameHandler
	}
	c.breakpointWS.mu.Unlock()

	return result.ID, nil
}

// AddRequestBreakpoint is a convenience for registering a REQUEST-only breakpoint.
func (c *Client) AddRequestBreakpoint(matcher *RequestBuilder, handler BreakpointRequestHandler) (string, error) {
	return c.AddBreakpoint(matcher, []BreakpointPhase{PhaseRequest}, handler, nil, nil)
}

// AddRequestResponseBreakpoint registers a REQUEST + RESPONSE breakpoint.
func (c *Client) AddRequestResponseBreakpoint(
	matcher *RequestBuilder,
	requestHandler BreakpointRequestHandler,
	responseHandler BreakpointResponseHandler,
) (string, error) {
	return c.AddBreakpoint(matcher, []BreakpointPhase{PhaseRequest, PhaseResponse}, requestHandler, responseHandler, nil)
}

// AddStreamBreakpoint registers a streaming-phase breakpoint.
func (c *Client) AddStreamBreakpoint(
	matcher *RequestBuilder,
	phases []BreakpointPhase,
	handler BreakpointStreamFrameHandler,
) (string, error) {
	return c.AddBreakpoint(matcher, phases, nil, nil, handler)
}

// ListBreakpointMatchers retrieves all registered breakpoint matchers.
func (c *Client) ListBreakpointMatchers() (*BreakpointMatcherList, error) {
	respBody, statusCode, err := c.doRequest("GET", "/mockserver/breakpoint/matchers", nil, nil)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: list breakpoint matchers failed (status %d): %s", statusCode, string(respBody))
	}
	var result BreakpointMatcherList
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &result); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal matcher list: %w", err)
		}
	}
	return &result, nil
}

// RemoveBreakpointMatcher removes a breakpoint matcher by id.
func (c *Client) RemoveBreakpointMatcher(id string) error {
	body, err := json.Marshal(map[string]string{"id": id})
	if err != nil {
		return fmt.Errorf("mockserver: marshal remove breakpoint: %w", err)
	}
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/breakpoint/matcher/remove", body, nil)
	if err != nil {
		return err
	}
	if statusCode == 404 {
		return fmt.Errorf("mockserver: breakpoint matcher not found: %s", id)
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: remove breakpoint failed (status %d): %s", statusCode, string(respBody))
	}

	// Remove local handlers
	if c.breakpointWS != nil {
		c.breakpointWS.mu.Lock()
		delete(c.breakpointWS.requestHandlers, id)
		delete(c.breakpointWS.responseHandlers, id)
		delete(c.breakpointWS.streamFrameHandlers, id)
		c.breakpointWS.mu.Unlock()
	}
	return nil
}

// ClearBreakpointMatchers removes all registered breakpoint matchers.
func (c *Client) ClearBreakpointMatchers() error {
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/breakpoint/matcher/clear", nil, nil)
	if err != nil {
		return err
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: clear breakpoint matchers failed (status %d): %s", statusCode, string(respBody))
	}

	// Clear local handlers
	if c.breakpointWS != nil {
		c.breakpointWS.mu.Lock()
		c.breakpointWS.requestHandlers = make(map[string]BreakpointRequestHandler)
		c.breakpointWS.responseHandlers = make(map[string]BreakpointResponseHandler)
		c.breakpointWS.streamFrameHandlers = make(map[string]BreakpointStreamFrameHandler)
		c.breakpointWS.mu.Unlock()
	}
	return nil
}

// CloseBreakpointWebSocket closes the breakpoint callback WebSocket connection.
func (c *Client) CloseBreakpointWebSocket() {
	c.bpMu.Lock()
	defer c.bpMu.Unlock()
	if c.breakpointWS != nil {
		c.breakpointWS.close()
		c.breakpointWS = nil
	}
}
