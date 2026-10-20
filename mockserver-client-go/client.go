package mockserver

import (
	"bytes"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"sync"
	"time"
)

// Client is a MockServer control-plane client.
type Client struct {
	baseURL      string
	httpClient   *http.Client
	bpMu         sync.Mutex // guards breakpointWS lazy-init and close
	breakpointWS *breakpointWSClient

	// bearerTokenSupplier, when non-nil, is invoked for every control-plane
	// request to obtain the token attached as "Authorization: Bearer <token>".
	bearerTokenSupplier func() string

	// tlsConfig accumulates CA roots and client certificates from the
	// WithCACert*/WithClientCert* options. It is applied to the http.Client's
	// Transport (creating one if the user did not supply their own).
	tlsConfig *tls.Config
	// tlsConfigErr captures the first error encountered while building
	// tlsConfig so it can surface on the next request rather than panicking
	// inside an option.
	tlsConfigErr error
	// userSuppliedHTTPClient records that the caller provided an http.Client
	// via WithHTTPClient, in which case a user-supplied Transport.TLSClientConfig
	// takes precedence over the CA/client-cert options.
	userSuppliedHTTPClient bool
}

// Option configures a Client.
type Option func(*Client)

// WithHTTPClient sets a custom http.Client for the MockServer client.
//
// Precedence: if the supplied http.Client has a *http.Transport whose
// TLSClientConfig is already set, that configuration is left untouched and the
// WithCACert*/WithClientCert* options are NOT applied on top of it (the caller
// is assumed to be in full control of TLS). If the supplied client has no
// TLSClientConfig, the CA/client-cert options populate one on its Transport.
func WithHTTPClient(c *http.Client) Option {
	return func(client *Client) {
		client.httpClient = c
		client.userSuppliedHTTPClient = true
	}
}

// WithControlPlaneBearerToken attaches "Authorization: Bearer <token>" to every
// control-plane request the client sends. Use this when MockServer is started
// with mockserver.controlPlaneJWTAuthenticationRequired (the client does not
// generate the JWT — supply the token string yourself).
func WithControlPlaneBearerToken(token string) Option {
	return func(client *Client) {
		client.bearerTokenSupplier = func() string { return token }
	}
}

// WithControlPlaneBearerTokenSupplier attaches "Authorization: Bearer <token>"
// to every control-plane request, calling the supplied function once per
// request so a short-lived/refreshable token can be provided. If supplier
// returns an empty string, no Authorization header is attached for that request.
func WithControlPlaneBearerTokenSupplier(supplier func() string) Option {
	return func(client *Client) {
		client.bearerTokenSupplier = supplier
	}
}

// WithCACertPEMPath trusts the CA certificate(s) in the given PEM file when
// connecting to an HTTPS MockServer, so a server presenting a certificate
// signed by that CA validates. Compose with WithTLS to use HTTPS.
func WithCACertPEMPath(path string) Option {
	return func(client *Client) {
		pem, err := os.ReadFile(path)
		if err != nil {
			client.setTLSConfigErr(fmt.Errorf("mockserver: read CA cert %q: %w", path, err))
			return
		}
		WithCACertPEM(pem)(client)
	}
}

// WithCACertPEM trusts the CA certificate(s) in the given PEM bytes when
// connecting to an HTTPS MockServer. Compose with WithTLS to use HTTPS.
func WithCACertPEM(pem []byte) Option {
	return func(client *Client) {
		cfg := client.ensureTLSConfig()
		if cfg.RootCAs == nil {
			cfg.RootCAs = x509.NewCertPool()
		}
		if !cfg.RootCAs.AppendCertsFromPEM(pem) {
			client.setTLSConfigErr(fmt.Errorf("mockserver: no valid CA certificates found in PEM"))
		}
	}
}

// WithClientCertPEM presents the given client certificate + private key (PEM
// files) for mutual TLS when connecting to MockServer started with
// mockserver.controlPlaneTLSMutualAuthenticationRequired.
func WithClientCertPEM(certPath, keyPath string) Option {
	return func(client *Client) {
		cert, err := tls.LoadX509KeyPair(certPath, keyPath)
		if err != nil {
			client.setTLSConfigErr(fmt.Errorf("mockserver: load client cert/key: %w", err))
			return
		}
		cfg := client.ensureTLSConfig()
		cfg.Certificates = append(cfg.Certificates, cert)
	}
}

// WithTimeout sets the HTTP timeout for the MockServer client.
func WithTimeout(d time.Duration) Option {
	return func(client *Client) {
		client.httpClient.Timeout = d
	}
}

// WithContextPath sets a context path prefix (e.g., "/mockserver-proxy").
func WithContextPath(path string) Option {
	return func(client *Client) {
		client.baseURL = client.baseURL + path
	}
}

// WithTLS configures the client to use HTTPS.
func WithTLS() Option {
	return func(client *Client) {
		u, _ := url.Parse(client.baseURL)
		u.Scheme = "https"
		client.baseURL = u.String()
	}
}

// New creates a new MockServer client.
//
// Example:
//
//	client := mockserver.New("localhost", 1080)
func New(host string, port int, opts ...Option) *Client {
	c := &Client{
		baseURL: fmt.Sprintf("http://%s:%d", host, port),
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
	for _, opt := range opts {
		opt(c)
	}
	c.applyTLSConfig()
	return c
}

// NewFromURL creates a new MockServer client from a full base URL.
//
// Example:
//
//	client := mockserver.NewFromURL("http://mockserver.internal:1080")
func NewFromURL(baseURL string, opts ...Option) *Client {
	c := &Client{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
	for _, opt := range opts {
		opt(c)
	}
	c.applyTLSConfig()
	return c
}

// ensureTLSConfig lazily creates the accumulating tls.Config used by the
// CA/client-cert options.
func (c *Client) ensureTLSConfig() *tls.Config {
	if c.tlsConfig == nil {
		c.tlsConfig = &tls.Config{MinVersion: tls.VersionTLS12}
	}
	return c.tlsConfig
}

// setTLSConfigErr records the first TLS configuration error so it surfaces on
// the next request rather than panicking inside an option.
func (c *Client) setTLSConfigErr(err error) {
	if c.tlsConfigErr == nil {
		c.tlsConfigErr = err
	}
}

// applyTLSConfig installs the accumulated tls.Config onto the http.Client's
// Transport. If the user supplied their own http.Client whose Transport already
// has a TLSClientConfig, that configuration takes precedence and is left alone.
func (c *Client) applyTLSConfig() {
	if c.tlsConfig == nil {
		return
	}
	if c.httpClient == nil {
		c.httpClient = &http.Client{Timeout: 30 * time.Second}
	}

	transport, ok := c.httpClient.Transport.(*http.Transport)
	if !ok || transport == nil {
		if c.userSuppliedHTTPClient && c.httpClient.Transport != nil {
			// A custom non-*http.Transport RoundTripper is fully in the
			// caller's control; do not override it.
			return
		}
		transport = http.DefaultTransport.(*http.Transport).Clone()
		c.httpClient.Transport = transport
	}

	if c.userSuppliedHTTPClient && transport.TLSClientConfig != nil {
		// Caller already configured TLS; their configuration wins.
		return
	}
	transport.TLSClientConfig = c.tlsConfig
}

// webSocketTLSConfig is the TLS configuration for the callback WebSocket: the
// one the client's HTTP requests use, so both trust the same certificates and
// present the same client certificate, without ALPN because a WebSocket upgrade
// is HTTP/1.1. Nil when no TLS configuration is set.
func (c *Client) webSocketTLSConfig() *tls.Config {
	cfg := c.tlsConfig
	if transport, ok := c.httpClient.Transport.(*http.Transport); ok && transport != nil && transport.TLSClientConfig != nil {
		cfg = transport.TLSClientConfig
	}
	if cfg == nil {
		return nil
	}
	cfg = cfg.Clone()
	cfg.NextProtos = nil
	return cfg
}

// ForwardChainExpectation allows chaining a When() call with Respond/Forward/Error.
type ForwardChainExpectation struct {
	client      *Client
	expectation Expectation
}

// WithID sets a custom expectation ID for deduplication/update.
func (f *ForwardChainExpectation) WithID(id string) *ForwardChainExpectation {
	f.expectation.ID = id
	return f
}

// WithPriority sets the expectation priority.
func (f *ForwardChainExpectation) WithPriority(priority int) *ForwardChainExpectation {
	f.expectation.Priority = priority
	return f
}

// WithResponseMode sets how MockServer selects between multiple responses
// registered via RespondMultiple (SEQUENTIAL, RANDOM, WEIGHTED, or SWITCH).
func (f *ForwardChainExpectation) WithResponseMode(mode ResponseMode) *ForwardChainExpectation {
	f.expectation.ResponseMode = mode
	return f
}

// WithResponseWeights sets the relative weights (index-aligned with the
// responses passed to RespondMultiple) used when the response mode is WEIGHTED.
func (f *ForwardChainExpectation) WithResponseWeights(weights ...int) *ForwardChainExpectation {
	f.expectation.ResponseWeights = weights
	return f
}

// WithSwitchAfter sets the number of requests served per response before
// advancing to the next one when the response mode is SWITCH.
func (f *ForwardChainExpectation) WithSwitchAfter(requests int) *ForwardChainExpectation {
	f.expectation.SwitchAfter = &requests
	return f
}

// WithCrossProtocolScenario registers a cross-protocol scenario transition on
// the expectation.
func (f *ForwardChainExpectation) WithCrossProtocolScenario(scenarios ...CrossProtocolScenario) *ForwardChainExpectation {
	f.expectation.CrossProtocolScenarios = append(f.expectation.CrossProtocolScenarios, scenarios...)
	return f
}

// Respond completes the expectation with an HTTP response action.
func (f *ForwardChainExpectation) Respond(rb *ResponseBuilder) ([]Expectation, error) {
	resp := rb.Build()
	f.expectation.HttpResponse = &resp
	return f.client.Upsert(f.expectation)
}

// RespondMultiple completes the expectation with multiple HTTP responses.
// MockServer selects between them according to the response mode (set via
// WithResponseMode; defaults to SEQUENTIAL). Multiple responses take priority
// over a singular response.
func (f *ForwardChainExpectation) RespondMultiple(builders ...*ResponseBuilder) ([]Expectation, error) {
	responses := make([]*HttpResponse, len(builders))
	for i, b := range builders {
		resp := b.Build()
		responses[i] = &resp
	}
	f.expectation.HttpResponses = responses
	return f.client.Upsert(f.expectation)
}

// Forward completes the expectation with a forward action.
func (f *ForwardChainExpectation) Forward(fb *ForwardBuilder) ([]Expectation, error) {
	fwd := fb.Build()
	f.expectation.HttpForward = &fwd
	return f.client.Upsert(f.expectation)
}

// RespondTemplate completes the expectation with a response template action.
func (f *ForwardChainExpectation) RespondTemplate(tb *TemplateBuilder) ([]Expectation, error) {
	tmpl := tb.Build()
	f.expectation.HttpResponseTemplate = &tmpl
	return f.client.Upsert(f.expectation)
}

// ForwardTemplate completes the expectation with a forward template action.
func (f *ForwardChainExpectation) ForwardTemplate(tb *TemplateBuilder) ([]Expectation, error) {
	tmpl := tb.Build()
	f.expectation.HttpForwardTemplate = &tmpl
	return f.client.Upsert(f.expectation)
}

// RespondWithError completes the expectation with an error action.
func (f *ForwardChainExpectation) RespondWithError(eb *ErrorBuilder) ([]Expectation, error) {
	e := eb.Build()
	f.expectation.HttpError = &e
	return f.client.Upsert(f.expectation)
}

// RespondSse completes the expectation with a Server-Sent Events response action.
func (f *ForwardChainExpectation) RespondSse(sb *SseResponseBuilder) ([]Expectation, error) {
	resp := sb.Build()
	f.expectation.HttpSseResponse = &resp
	return f.client.Upsert(f.expectation)
}

// RespondWebSocket completes the expectation with a WebSocket response action.
func (f *ForwardChainExpectation) RespondWebSocket(wb *WebSocketResponseBuilder) ([]Expectation, error) {
	resp := wb.Build()
	f.expectation.HttpWebSocketResponse = &resp
	return f.client.Upsert(f.expectation)
}

// RespondGrpcStream completes the expectation with a gRPC streaming response action.
func (f *ForwardChainExpectation) RespondGrpcStream(gb *GrpcStreamResponseBuilder) ([]Expectation, error) {
	resp := gb.Build()
	f.expectation.GrpcStreamResponse = &resp
	return f.client.Upsert(f.expectation)
}

// RespondBinary completes the expectation with a raw binary response action.
func (f *ForwardChainExpectation) RespondBinary(bb *BinaryResponseBuilder) ([]Expectation, error) {
	resp := bb.Build()
	f.expectation.BinaryResponse = &resp
	return f.client.Upsert(f.expectation)
}

// RespondDns completes the expectation with a DNS response action.
func (f *ForwardChainExpectation) RespondDns(db *DnsResponseBuilder) ([]Expectation, error) {
	resp := db.Build()
	f.expectation.DnsResponse = &resp
	return f.client.Upsert(f.expectation)
}

// When begins building an expectation with a fluent API.
func (c *Client) When(rb *RequestBuilder, opts ...ExpectationOption) *ForwardChainExpectation {
	req := rb.Build()
	exp := Expectation{
		HttpRequest: &req,
	}
	for _, opt := range opts {
		opt(&exp)
	}
	return &ForwardChainExpectation{client: c, expectation: exp}
}

// ExpectationOption configures an expectation created via When().
type ExpectationOption func(*Expectation)

// WithTimes sets the Times on an expectation created via When().
func WithTimes(t *Times) ExpectationOption {
	return func(e *Expectation) {
		e.Times = t
	}
}

// WithTimeToLive sets the TimeToLive on an expectation created via When().
func WithTimeToLive(ttl *TimeToLive) ExpectationOption {
	return func(e *Expectation) {
		e.TimeToLive = ttl
	}
}

// Upsert creates or updates one or more expectations.
func (c *Client) Upsert(expectations ...Expectation) ([]Expectation, error) {
	body, err := json.Marshal(expectations)
	if err != nil {
		return nil, fmt.Errorf("mockserver: marshal expectations: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/expectation", body, nil)
	if err != nil {
		return nil, err
	}

	if statusCode == 400 || statusCode == 406 {
		return nil, fmt.Errorf("mockserver: invalid expectation (status %d): %s", statusCode, string(respBody))
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: create expectation failed (status %d): %s", statusCode, string(respBody))
	}

	var created []Expectation
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &created); err != nil {
			// Not all versions return JSON; fall back to returning what was sent.
			return expectations, nil
		}
	}
	return created, nil
}

// VerificationError is returned when a verification fails (HTTP 406).
type VerificationError struct {
	Message string
}

func (e *VerificationError) Error() string {
	return fmt.Sprintf("mockserver: verification failed: %s", e.Message)
}

// Verify asserts that a request matching the given builder was received
// the specified number of times. Pass nil for times to use the server default.
func (c *Client) Verify(rb *RequestBuilder, times *VerificationTimes) error {
	req := rb.Build()
	v := verification{
		HttpRequest: &req,
		Times:       times,
	}

	body, err := json.Marshal(v)
	if err != nil {
		return fmt.Errorf("mockserver: marshal verification: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/verify", body, nil)
	if err != nil {
		return err
	}

	if statusCode == 406 {
		return &VerificationError{Message: string(respBody)}
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: verify failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// VerifyResponse asserts that a request-response pair matching the given
// builders was recorded. This verifies proxied/forwarded responses rather
// than simply whether a request was received. Pass nil for request to verify
// only the response. Pass nil for times to use the server default.
func (c *Client) VerifyResponse(rb *RequestBuilder, respB *ResponseBuilder, times *VerificationTimes) error {
	v := verification{
		Times: times,
	}
	if rb != nil {
		req := rb.Build()
		v.HttpRequest = &req
	}
	if respB != nil {
		resp := respB.Build()
		v.HttpResponse = &resp
	}

	body, err := json.Marshal(v)
	if err != nil {
		return fmt.Errorf("mockserver: marshal verification: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/verify", body, nil)
	if err != nil {
		return err
	}

	if statusCode == 406 {
		return &VerificationError{Message: string(respBody)}
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: verify failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// VerifyZeroInteractions asserts that MockServer received no requests at all.
// It is a thin wrapper over Verify with an empty request matcher and an
// at-most-zero times constraint (matching the Java/Python/Ruby/Node clients).
func (c *Client) VerifyZeroInteractions() error {
	v := verification{
		HttpRequest: &HttpRequest{},
		Times:       &VerificationTimes{atMostSet: true},
	}

	body, err := json.Marshal(v)
	if err != nil {
		return fmt.Errorf("mockserver: marshal verification: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/verify", body, nil)
	if err != nil {
		return err
	}

	if statusCode == 406 {
		return &VerificationError{Message: string(respBody)}
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: verify failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// VerifySequence asserts that requests matching the given builders were received
// in the specified order.
func (c *Client) VerifySequence(builders ...*RequestBuilder) error {
	requests := make([]HttpRequest, len(builders))
	for i, b := range builders {
		requests[i] = b.Build()
	}
	v := verificationSequence{HttpRequests: requests}

	body, err := json.Marshal(v)
	if err != nil {
		return fmt.Errorf("mockserver: marshal verification sequence: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/verifySequence", body, nil)
	if err != nil {
		return err
	}

	if statusCode == 406 {
		return &VerificationError{Message: string(respBody)}
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: verify sequence failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// VerifyResponseSequence asserts that request-response pairs were recorded in
// order. Requests and responses are index-aligned — each request builder at
// position i is paired with the response builder at position i. Either slice
// may contain nil entries for positions where only the request or only the
// response should be matched.
func (c *Client) VerifyResponseSequence(requestBuilders []*RequestBuilder, responseBuilders []*ResponseBuilder) error {
	var requests []HttpRequest
	if len(requestBuilders) > 0 {
		requests = make([]HttpRequest, len(requestBuilders))
		for i, b := range requestBuilders {
			if b != nil {
				requests[i] = b.Build()
			}
		}
	}

	var responses []HttpResponse
	if len(responseBuilders) > 0 {
		responses = make([]HttpResponse, len(responseBuilders))
		for i, b := range responseBuilders {
			if b != nil {
				responses[i] = b.Build()
			}
		}
	}

	v := verificationSequence{
		HttpRequests:  requests,
		HttpResponses: responses,
	}

	body, err := json.Marshal(v)
	if err != nil {
		return fmt.Errorf("mockserver: marshal verification sequence: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/verifySequence", body, nil)
	if err != nil {
		return err
	}

	if statusCode == 406 {
		return &VerificationError{Message: string(respBody)}
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: verify sequence failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// ClearType specifies what to clear.
type ClearType string

const (
	ClearAll          ClearType = "all"
	ClearLog          ClearType = "log"
	ClearExpectations ClearType = "expectations"
)

// Clear removes expectations and/or recorded requests matching the given request.
// If rb is nil, all are cleared. clearType defaults to "all" if empty.
func (c *Client) Clear(rb *RequestBuilder, clearType ClearType) error {
	var body []byte
	var err error
	if rb != nil {
		req := rb.Build()
		body, err = json.Marshal(req)
		if err != nil {
			return fmt.Errorf("mockserver: marshal clear request: %w", err)
		}
	}

	params := make(url.Values)
	if clearType != "" {
		params.Set("type", string(clearType))
	}

	_, statusCode, err := c.doRequest("PUT", "/mockserver/clear", body, params)
	if err != nil {
		return err
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: clear failed (status %d)", statusCode)
	}
	return nil
}

// ClearByID removes the expectation with the given ID.
func (c *Client) ClearByID(expectationID string, clearType ClearType) error {
	payload := map[string]string{"id": expectationID}
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("mockserver: marshal clear by id: %w", err)
	}

	params := make(url.Values)
	if clearType != "" {
		params.Set("type", string(clearType))
	}

	_, statusCode, err := c.doRequest("PUT", "/mockserver/clear", body, params)
	if err != nil {
		return err
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: clear by id failed (status %d)", statusCode)
	}
	return nil
}

// Reset clears all expectations, recorded requests, and logs.
func (c *Client) Reset() error {
	_, statusCode, err := c.doRequest("PUT", "/mockserver/reset", nil, nil)
	if err != nil {
		return err
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: reset failed (status %d)", statusCode)
	}
	return nil
}

// RetrieveType specifies what to retrieve.
type RetrieveType string

const (
	RetrieveRequests             RetrieveType = "requests"
	RetrieveRequestResponses     RetrieveType = "request_responses"
	RetrieveRecordedExpectations RetrieveType = "recorded_expectations"
	RetrieveActiveExpectations   RetrieveType = "active_expectations"
	RetrieveLogs                 RetrieveType = "logs"
)

// RetrieveFormat specifies the response format.
type RetrieveFormat string

const (
	FormatJSON       RetrieveFormat = "json"
	FormatLogEntries RetrieveFormat = "log_entries"
	// Code-generation formats. These return MockServer SDK setup code (the
	// builder code that recreates the expectations) for the requested client
	// language, rather than JSON.
	FormatJava       RetrieveFormat = "java"
	FormatJavaScript RetrieveFormat = "javascript"
	FormatPython     RetrieveFormat = "python"
	FormatGo         RetrieveFormat = "go"
	FormatCSharp     RetrieveFormat = "csharp"
	FormatRuby       RetrieveFormat = "ruby"
	FormatRust       RetrieveFormat = "rust"
	FormatPHP        RetrieveFormat = "php"
)

// Retrieve retrieves recorded data from MockServer. The result is the raw JSON
// response body. Use the typed helpers (RetrieveActiveExpectations, etc.) for
// convenience.
func (c *Client) Retrieve(rb *RequestBuilder, retrieveType RetrieveType, format RetrieveFormat) ([]byte, error) {
	var body []byte
	var err error
	if rb != nil {
		req := rb.Build()
		body, err = json.Marshal(req)
		if err != nil {
			return nil, fmt.Errorf("mockserver: marshal retrieve filter: %w", err)
		}
	}

	params := make(url.Values)
	if retrieveType != "" {
		params.Set("type", string(retrieveType))
	}
	if format != "" {
		params.Set("format", string(format))
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/retrieve", body, params)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: retrieve failed (status %d): %s", statusCode, string(respBody))
	}
	return respBody, nil
}

// RetrieveActiveExpectations retrieves active expectations, optionally filtered.
func (c *Client) RetrieveActiveExpectations(rb *RequestBuilder) ([]Expectation, error) {
	data, err := c.Retrieve(rb, RetrieveActiveExpectations, FormatJSON)
	if err != nil {
		return nil, err
	}
	if len(data) == 0 {
		return nil, nil
	}
	var result []Expectation
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("mockserver: unmarshal active expectations: %w", err)
	}
	return result, nil
}

// RetrieveRecordedRequests retrieves recorded requests, optionally filtered.
func (c *Client) RetrieveRecordedRequests(rb *RequestBuilder) ([]HttpRequest, error) {
	data, err := c.Retrieve(rb, RetrieveRequests, FormatJSON)
	if err != nil {
		return nil, err
	}
	if len(data) == 0 {
		return nil, nil
	}
	var result []HttpRequest
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("mockserver: unmarshal recorded requests: %w", err)
	}
	return result, nil
}

// RetrieveRecordedExpectations retrieves recorded expectations, optionally filtered.
func (c *Client) RetrieveRecordedExpectations(rb *RequestBuilder) ([]Expectation, error) {
	data, err := c.Retrieve(rb, RetrieveRecordedExpectations, FormatJSON)
	if err != nil {
		return nil, err
	}
	if len(data) == 0 {
		return nil, nil
	}
	var result []Expectation
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("mockserver: unmarshal recorded expectations: %w", err)
	}
	return result, nil
}

// RetrieveLogMessages retrieves log messages, optionally filtered.
func (c *Client) RetrieveLogMessages(rb *RequestBuilder) ([]byte, error) {
	return c.Retrieve(rb, RetrieveLogs, FormatJSON)
}

// RetrieveByID retrieves recorded data from MockServer filtered by expectation ID
// rather than by a request matcher. The result is the raw response body.
//
// For RetrieveActiveExpectations only the expectation with that ID is returned; for
// every other type the entries matching that expectation's request are returned,
// exactly as verify by expectation ID matches them. An unknown ID is rejected with a
// 400.
func (c *Client) RetrieveByID(expectationID string, retrieveType RetrieveType, format RetrieveFormat) ([]byte, error) {
	body, err := json.Marshal(map[string]string{"id": expectationID})
	if err != nil {
		return nil, fmt.Errorf("mockserver: marshal retrieve by id: %w", err)
	}

	params := make(url.Values)
	if retrieveType != "" {
		params.Set("type", string(retrieveType))
	}
	if format != "" {
		params.Set("format", string(format))
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/retrieve", body, params)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: retrieve by id failed (status %d): %s", statusCode, string(respBody))
	}
	return respBody, nil
}

// RetrieveActiveExpectationsByID retrieves the active expectation with the given ID.
func (c *Client) RetrieveActiveExpectationsByID(expectationID string) ([]Expectation, error) {
	data, err := c.RetrieveByID(expectationID, RetrieveActiveExpectations, FormatJSON)
	if err != nil {
		return nil, err
	}
	if len(data) == 0 {
		return nil, nil
	}
	var result []Expectation
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("mockserver: unmarshal active expectations: %w", err)
	}
	return result, nil
}

// RetrieveRecordedRequestsByID retrieves the recorded requests matching the request
// of the expectation with the given ID.
func (c *Client) RetrieveRecordedRequestsByID(expectationID string) ([]HttpRequest, error) {
	data, err := c.RetrieveByID(expectationID, RetrieveRequests, FormatJSON)
	if err != nil {
		return nil, err
	}
	if len(data) == 0 {
		return nil, nil
	}
	var result []HttpRequest
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("mockserver: unmarshal recorded requests: %w", err)
	}
	return result, nil
}

// RetrieveRecordedExpectationsByID retrieves the recorded expectations matching the
// request of the expectation with the given ID.
func (c *Client) RetrieveRecordedExpectationsByID(expectationID string) ([]Expectation, error) {
	data, err := c.RetrieveByID(expectationID, RetrieveRecordedExpectations, FormatJSON)
	if err != nil {
		return nil, err
	}
	if len(data) == 0 {
		return nil, nil
	}
	var result []Expectation
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, fmt.Errorf("mockserver: unmarshal recorded expectations: %w", err)
	}
	return result, nil
}

// RetrieveLogMessagesByID retrieves the log messages matching the request of the
// expectation with the given ID.
func (c *Client) RetrieveLogMessagesByID(expectationID string) ([]byte, error) {
	return c.RetrieveByID(expectationID, RetrieveLogs, FormatJSON)
}

// RetrieveExpectationsAsCode retrieves the active expectations as MockServer SDK
// setup code (the builder code that recreates the expectations) in the requested
// language. The format is one of the code-generation formats (e.g. FormatJava,
// FormatJavaScript, FormatPython, FormatGo, FormatCSharp, FormatRuby, FormatRust,
// FormatPHP). The generated code is returned as a string.
func (c *Client) RetrieveExpectationsAsCode(rb *RequestBuilder, format RetrieveFormat) (string, error) {
	data, err := c.Retrieve(rb, RetrieveActiveExpectations, format)
	if err != nil {
		return "", err
	}
	return string(data), nil
}

// RetrieveRecordedExpectationsAsCode retrieves the recorded (proxied) request/
// response pairs as MockServer SDK setup code in the requested language. The
// format is one of the code-generation formats (e.g. FormatJava, FormatGo). The
// generated code is returned as a string.
func (c *Client) RetrieveRecordedExpectationsAsCode(rb *RequestBuilder, format RetrieveFormat) (string, error) {
	data, err := c.Retrieve(rb, RetrieveRecordedExpectations, format)
	if err != nil {
		return "", err
	}
	return string(data), nil
}

// StatusResponse contains the response from the status endpoint.
type StatusResponse struct {
	Ports []int `json:"ports"`
}

// Status checks that MockServer is running and returns its listening ports.
func (c *Client) Status() (*StatusResponse, error) {
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/status", nil, nil)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: status failed (status %d)", statusCode)
	}
	var s StatusResponse
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &s); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal status: %w", err)
		}
	}
	return &s, nil
}

// Bind binds additional ports on the running MockServer instance.
func (c *Client) Bind(ports ...int) ([]int, error) {
	payload := map[string][]int{"ports": ports}
	body, err := json.Marshal(payload)
	if err != nil {
		return nil, fmt.Errorf("mockserver: marshal bind: %w", err)
	}

	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/bind", body, nil)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: bind failed (status %d): %s", statusCode, string(respBody))
	}

	var result map[string][]int
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &result); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal bind response: %w", err)
		}
	}
	return result["ports"], nil
}

// IsRunning returns true if MockServer is reachable and responds to a status request.
func (c *Client) IsRunning() bool {
	_, err := c.Status()
	return err == nil
}

// doRequest performs an HTTP request to MockServer with a JSON content type.
func (c *Client) doRequest(method, path string, body []byte, params url.Values) ([]byte, int, error) {
	return c.doRequestWithContentType(method, path, body, params, "application/json; charset=utf-8")
}

// doRequestWithContentType performs an HTTP request to MockServer using the
// given Content-Type. The body is sent verbatim (no encoding), which is
// required for raw binary payloads such as gRPC descriptor sets.
func (c *Client) doRequestWithContentType(method, path string, body []byte, params url.Values, contentType string) ([]byte, int, error) {
	if c.tlsConfigErr != nil {
		return nil, 0, c.tlsConfigErr
	}

	u := c.baseURL + path
	if len(params) > 0 {
		u = u + "?" + params.Encode()
	}

	var bodyReader io.Reader
	if body != nil {
		bodyReader = bytes.NewReader(body)
	}

	req, err := http.NewRequest(method, u, bodyReader)
	if err != nil {
		return nil, 0, fmt.Errorf("mockserver: create request: %w", err)
	}
	req.Header.Set("Content-Type", contentType)

	// Attach the control-plane bearer token (if configured) to every
	// control-plane request. All requests this client sends are control-plane
	// (/mockserver/*); the data plane is never driven through this method.
	if c.bearerTokenSupplier != nil {
		if token := c.bearerTokenSupplier(); token != "" {
			req.Header.Set("Authorization", "Bearer "+token)
		}
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, 0, fmt.Errorf("mockserver: request failed: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, resp.StatusCode, fmt.Errorf("mockserver: read response: %w", err)
	}

	return respBody, resp.StatusCode, nil
}
