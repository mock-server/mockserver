package mockserver

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/url"
)

// This file adds the remaining control-plane helpers to reach parity with the
// Java client (org.mockserver.client.MockServerClient): clock control, metrics,
// live configuration, Pact import/export/verify, the in-memory file store,
// HAR/Postman import, operating-mode (SIMULATE/SPY/CAPTURE) control, and WSDL
// expectation generation. Each operation mirrors the MockServer control-plane
// REST contract documented in
// docs/plans/control-plane-client-api-spec.local.md and derived from the server
// source (HttpState / HttpRequestHandler) and the Java client method names.

// -----------------------------------------------------------------------------
// 1. Clock (freeze / advance / reset / status)
//    PUT /mockserver/clock  (JSON body with an "action")
//    GET /mockserver/clock  (status)
// -----------------------------------------------------------------------------

// FreezeClock freezes the MockServer simulated clock at the given ISO-8601
// instant (e.g. "2024-01-01T00:00:00Z"). Pass an empty string to freeze at the
// current time. Mirrors the Java client freezeClock(Instant)/freezeClock().
// Sends PUT /mockserver/clock with {"action":"freeze","instant":"<instant>"}.
func (c *Client) FreezeClock(instant string) error {
	payload := map[string]string{"action": "freeze"}
	if instant != "" {
		payload["instant"] = instant
	}
	return c.clockAction(payload)
}

// AdvanceClock advances the frozen MockServer clock by the given number of
// milliseconds (must be positive). Mirrors the Java client
// advanceClock(Duration). Sends PUT /mockserver/clock with
// {"action":"advance","durationMillis":<millis>}.
func (c *Client) AdvanceClock(durationMillis int64) error {
	payload := map[string]interface{}{"action": "advance", "durationMillis": durationMillis}
	return c.clockAction(payload)
}

// ResetClock resets the MockServer clock back to the real system clock. Mirrors
// the Java client resetClock(). Sends PUT /mockserver/clock with
// {"action":"reset"}.
func (c *Client) ResetClock() error {
	return c.clockAction(map[string]string{"action": "reset"})
}

func (c *Client) clockAction(payload interface{}) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("mockserver: marshal clock action: %w", err)
	}
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/clock", body, nil)
	if err != nil {
		return err
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: clock action failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// ClockStatus returns the current clock status JSON body verbatim
// ({"currentInstant":...,"currentEpochMillis":...,"frozen":<bool>}). Mirrors the
// Java client clockStatus(). Sends GET /mockserver/clock.
func (c *Client) ClockStatus() (string, error) {
	respBody, statusCode, err := c.doRequest("GET", "/mockserver/clock", nil, nil)
	if err != nil {
		return "", err
	}
	if statusCode >= 400 {
		return "", fmt.Errorf("mockserver: clock status failed (status %d): %s", statusCode, string(respBody))
	}
	return string(respBody), nil
}

// -----------------------------------------------------------------------------
// 2. Metrics
//    PUT /mockserver/retrieve?type=METRICS  (JSON counter snapshot)
//    GET /mockserver/metrics                (Prometheus exposition text)
// -----------------------------------------------------------------------------

// RetrieveMetrics returns the MockServer internal counter snapshot as a map of
// metric name to value. Mirrors the Java client retrieveMetrics(). Sends
// PUT /mockserver/retrieve?type=METRICS. When metrics are disabled on the server
// the returned map is empty.
func (c *Client) RetrieveMetrics() (map[string]int64, error) {
	params := url.Values{}
	params.Set("type", "METRICS")
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/retrieve", nil, params)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: retrieve metrics failed (status %d): %s", statusCode, string(respBody))
	}
	result := map[string]int64{}
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &result); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal metrics: %w", err)
		}
	}
	return result, nil
}

// ScrapeMetrics returns the Prometheus exposition text from the scrape endpoint.
// Sends GET /mockserver/metrics. Returns an error if metrics are disabled on the
// server (HTTP 404).
func (c *Client) ScrapeMetrics() (string, error) {
	respBody, statusCode, err := c.doRequest("GET", "/mockserver/metrics", nil, nil)
	if err != nil {
		return "", err
	}
	if statusCode == 404 {
		return "", fmt.Errorf("mockserver: metrics scrape endpoint disabled (status 404)")
	}
	if statusCode >= 400 {
		return "", fmt.Errorf("mockserver: scrape metrics failed (status %d): %s", statusCode, string(respBody))
	}
	return string(respBody), nil
}

// -----------------------------------------------------------------------------
// 3. Configuration (read / update)
//    GET /mockserver/configuration
//    PUT /mockserver/configuration  (ConfigurationDTO JSON)
// -----------------------------------------------------------------------------

// RetrieveConfiguration returns the effective live MockServer configuration as a
// JSON string. Mirrors the Java client retrieveConfiguration(). Sends
// GET /mockserver/configuration.
func (c *Client) RetrieveConfiguration() (string, error) {
	respBody, statusCode, err := c.doRequest("GET", "/mockserver/configuration", nil, nil)
	if err != nil {
		return "", err
	}
	if statusCode >= 400 {
		return "", fmt.Errorf("mockserver: retrieve configuration failed (status %d): %s", statusCode, string(respBody))
	}
	return string(respBody), nil
}

// UpdateConfiguration applies a partial configuration update (a ConfigurationDTO
// JSON document — only the fields present are applied) and returns the updated
// configuration JSON. Mirrors the Java client updateConfiguration(String). Sends
// PUT /mockserver/configuration. Properties fixed at startup, such as
// forwardProxyBlockPrivateNetworks, forwardProxyAllowedHosts and
// forwardProxyDeniedHosts, are ignored and the server logs a warning.
func (c *Client) UpdateConfiguration(configJSON string) (string, error) {
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/configuration", []byte(configJSON), nil)
	if err != nil {
		return "", err
	}
	if statusCode >= 400 {
		return "", fmt.Errorf("mockserver: update configuration failed (status %d): %s", statusCode, string(respBody))
	}
	return string(respBody), nil
}

// -----------------------------------------------------------------------------
// Drift detection
//    GET /mockserver/drift        (recorded mock drift report)
//    PUT /mockserver/drift/clear  (clear recorded drift)
// -----------------------------------------------------------------------------

// RetrieveDrift returns the recorded mock drift report as a parsed map of the
// form {"count": <n>, "drifts": [ ... ]}, where each entry describes a
// difference detected between a mock's configured response and the live
// upstream response for the same request. Mirrors the Java client
// retrieveDrift(). Sends GET /mockserver/drift. When no drift has been recorded
// the returned map is empty.
func (c *Client) RetrieveDrift() (map[string]interface{}, error) {
	respBody, statusCode, err := c.doRequest("GET", "/mockserver/drift", nil, nil)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: retrieve drift failed (status %d): %s", statusCode, string(respBody))
	}
	result := map[string]interface{}{}
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &result); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal drift: %w", err)
		}
	}
	return result, nil
}

// ClearDrift clears all recorded mock drift. Mirrors the Java client
// clearDrift(). Sends PUT /mockserver/drift/clear.
func (c *Client) ClearDrift() error {
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/drift/clear", nil, nil)
	if err != nil {
		return err
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: clear drift failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// -----------------------------------------------------------------------------
// 4. Pact (import / export / verify)
//    PUT /mockserver/pact/import   import a Pact contract
//    PUT /mockserver/pact          export a generated Pact contract
//    PUT /mockserver/pact/verify   verify (202 pass / 406 fail)
// -----------------------------------------------------------------------------

// PactImport upserts expectations from a Pact v3 contract JSON document and
// returns the created/updated expectations. Mirrors the Java client
// pactImport(String). Sends PUT /mockserver/pact/import.
func (c *Client) PactImport(pactJSON string) ([]Expectation, error) {
	if pactJSON == "" {
		return nil, fmt.Errorf("mockserver: pact import JSON must not be empty")
	}
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/pact/import", []byte(pactJSON), nil)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: pact import failed (status %d): %s", statusCode, string(respBody))
	}
	return unmarshalExpectations(respBody, "pact import")
}

// PactExport generates a Pact v3 contract JSON document from the active
// expectations and returns it. Empty consumer/provider use the server defaults.
// Mirrors the Java client pactExport(consumer, provider). Sends
// PUT /mockserver/pact?consumer=&provider=.
func (c *Client) PactExport(consumer, provider string) (string, error) {
	params := url.Values{}
	if consumer != "" {
		params.Set("consumer", consumer)
	}
	if provider != "" {
		params.Set("provider", provider)
	}
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/pact", nil, params)
	if err != nil {
		return "", err
	}
	if statusCode >= 400 {
		return "", fmt.Errorf("mockserver: pact export failed (status %d): %s", statusCode, string(respBody))
	}
	return string(respBody), nil
}

// PactVerify verifies a Pact v3 contract JSON document against the active
// expectations. It returns passed=true when every interaction matched (HTTP 202)
// and passed=false when verification failed (HTTP 406); in both cases report is
// the verification report JSON body. Other failures (e.g. blank input → 400) are
// returned as an error. Mirrors the Java client pactVerify(String), which
// deliberately surfaces the 406 FAIL body rather than raising.
func (c *Client) PactVerify(pactJSON string) (passed bool, report string, err error) {
	if pactJSON == "" {
		return false, "", fmt.Errorf("mockserver: pact verify JSON must not be empty")
	}
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/pact/verify", []byte(pactJSON), nil)
	if err != nil {
		return false, "", err
	}
	switch statusCode {
	case 202:
		return true, string(respBody), nil
	case 406:
		return false, string(respBody), nil
	default:
		return false, string(respBody), fmt.Errorf("mockserver: pact verify failed (status %d): %s", statusCode, string(respBody))
	}
}

// -----------------------------------------------------------------------------
// 5. File store (store / retrieve / list / delete)
//    All endpoints use PUT (the verb is part of the action):
//      PUT /mockserver/files/store
//      PUT /mockserver/files/retrieve
//      PUT /mockserver/files/list
//      PUT /mockserver/files/delete
// -----------------------------------------------------------------------------

// StoreFile stores the given text content under name in the in-memory file
// store. Sends PUT /mockserver/files/store with {"name":..,"content":..}.
func (c *Client) StoreFile(name, content string) error {
	payload := map[string]interface{}{"name": name, "content": content}
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("mockserver: marshal store file: %w", err)
	}
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/files/store", body, nil)
	if err != nil {
		return err
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: store file failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// StoreFileBytes stores raw binary content under name, base64-encoding it on the
// wire. Sends PUT /mockserver/files/store with
// {"name":..,"content":<base64>,"base64":true}.
func (c *Client) StoreFileBytes(name string, content []byte) error {
	payload := map[string]interface{}{
		"name":    name,
		"content": base64.StdEncoding.EncodeToString(content),
		"base64":  true,
	}
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("mockserver: marshal store file: %w", err)
	}
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/files/store", body, nil)
	if err != nil {
		return err
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: store file failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// RetrieveFile returns the raw bytes of the named file from the in-memory file
// store. Sends PUT /mockserver/files/retrieve with {"name":..}. The raw 200 body
// is returned verbatim; a missing file (HTTP 404) is returned as an error.
func (c *Client) RetrieveFile(name string) ([]byte, error) {
	payload := map[string]string{"name": name}
	body, err := json.Marshal(payload)
	if err != nil {
		return nil, fmt.Errorf("mockserver: marshal retrieve file: %w", err)
	}
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/files/retrieve", body, nil)
	if err != nil {
		return nil, err
	}
	if statusCode == 404 {
		return nil, fmt.Errorf("mockserver: file not found: %s", name)
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: retrieve file failed (status %d): %s", statusCode, string(respBody))
	}
	return respBody, nil
}

// ListFiles returns the names of all files in the in-memory file store. Sends
// PUT /mockserver/files/list.
func (c *Client) ListFiles() ([]string, error) {
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/files/list", nil, nil)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: list files failed (status %d): %s", statusCode, string(respBody))
	}
	var names []string
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &names); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal file list: %w", err)
		}
	}
	return names, nil
}

// DeleteFile removes the named file from the in-memory file store. Sends
// PUT /mockserver/files/delete with {"name":..}. A missing file (HTTP 404) is
// returned as an error.
func (c *Client) DeleteFile(name string) error {
	payload := map[string]string{"name": name}
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("mockserver: marshal delete file: %w", err)
	}
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/files/delete", body, nil)
	if err != nil {
		return err
	}
	if statusCode == 404 {
		return fmt.Errorf("mockserver: file not found: %s", name)
	}
	if statusCode >= 400 {
		return fmt.Errorf("mockserver: delete file failed (status %d): %s", statusCode, string(respBody))
	}
	return nil
}

// -----------------------------------------------------------------------------
// 6. Import (HAR / Postman)
//    PUT /mockserver/import?format=har|postman
// -----------------------------------------------------------------------------

// ImportHar imports a HAR (HTTP Archive) JSON document, upserting an expectation
// per recorded entry, and returns the created/updated expectations. Sends
// PUT /mockserver/import?format=har.
func (c *Client) ImportHar(harJSON string) ([]Expectation, error) {
	return c.importDocument(harJSON, "har")
}

// ImportPostmanCollection imports a Postman collection JSON document, upserting
// an expectation per request, and returns the created/updated expectations.
// Sends PUT /mockserver/import?format=postman.
func (c *Client) ImportPostmanCollection(collectionJSON string) ([]Expectation, error) {
	return c.importDocument(collectionJSON, "postman")
}

func (c *Client) importDocument(doc, format string) ([]Expectation, error) {
	if doc == "" {
		return nil, fmt.Errorf("mockserver: import document must not be empty")
	}
	params := url.Values{}
	if format != "" {
		params.Set("format", format)
	}
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/import", []byte(doc), params)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: import (%s) failed (status %d): %s", format, statusCode, string(respBody))
	}
	return unmarshalExpectations(respBody, "import")
}

// -----------------------------------------------------------------------------
// 7. Operating mode (SIMULATE / SPY / CAPTURE)
//    PUT /mockserver/mode?mode=<MODE>
//    GET /mockserver/mode
// -----------------------------------------------------------------------------

// Mode is the high-level MockServer operating mode.
type Mode string

const (
	// ModeSimulate matches expectations and returns 404 for unmatched requests
	// (proxy-on-no-match disabled; this is the default).
	ModeSimulate Mode = "SIMULATE"
	// ModeSpy matches expectations; unmatched requests are forwarded to the real
	// upstream and recorded.
	ModeSpy Mode = "SPY"
	// ModeCapture forwards and records traffic (with no expectations defined it
	// captures all traffic).
	ModeCapture Mode = "CAPTURE"
)

// ModeStatus is the operating-mode status returned by the /mode endpoint.
type ModeStatus struct {
	Mode                   Mode `json:"mode"`
	ProxyUnmatchedRequests bool `json:"proxyUnmatchedRequests"`
}

// SetMode sets the high-level operating mode (SIMULATE/SPY/CAPTURE) and returns
// the resulting mode status. Sends PUT /mockserver/mode?mode=<MODE>.
func (c *Client) SetMode(mode Mode) (*ModeStatus, error) {
	params := url.Values{}
	params.Set("mode", string(mode))
	respBody, statusCode, err := c.doRequest("PUT", "/mockserver/mode", nil, params)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: set mode failed (status %d): %s", statusCode, string(respBody))
	}
	return unmarshalModeStatus(respBody)
}

// RetrieveMode returns the current operating mode status. Sends
// GET /mockserver/mode.
func (c *Client) RetrieveMode() (*ModeStatus, error) {
	respBody, statusCode, err := c.doRequest("GET", "/mockserver/mode", nil, nil)
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: retrieve mode failed (status %d): %s", statusCode, string(respBody))
	}
	return unmarshalModeStatus(respBody)
}

func unmarshalModeStatus(respBody []byte) (*ModeStatus, error) {
	var status ModeStatus
	if len(respBody) > 0 {
		if err := json.Unmarshal(respBody, &status); err != nil {
			return nil, fmt.Errorf("mockserver: unmarshal mode status: %w", err)
		}
	}
	return &status, nil
}

// -----------------------------------------------------------------------------
// 8. WSDL → expectations
//    PUT /mockserver/wsdl  (raw WSDL XML body)
// -----------------------------------------------------------------------------

// WsdlExpectation generates and upserts SOAP expectations from a WSDL XML
// document and returns the created expectations. Sends PUT /mockserver/wsdl with
// the raw WSDL XML as the body.
func (c *Client) WsdlExpectation(wsdl string) ([]Expectation, error) {
	if wsdl == "" {
		return nil, fmt.Errorf("mockserver: WSDL must not be empty")
	}
	respBody, statusCode, err := c.doRequestWithContentType("PUT", "/mockserver/wsdl", []byte(wsdl), nil, "application/xml; charset=utf-8")
	if err != nil {
		return nil, err
	}
	if statusCode >= 400 {
		return nil, fmt.Errorf("mockserver: wsdl expectation failed (status %d): %s", statusCode, string(respBody))
	}
	return unmarshalExpectations(respBody, "wsdl")
}

// unmarshalExpectations decodes a JSON array of expectations from a control-plane
// response body, returning nil for an empty body.
func unmarshalExpectations(respBody []byte, op string) ([]Expectation, error) {
	if len(respBody) == 0 {
		return nil, nil
	}
	var result []Expectation
	if err := json.Unmarshal(respBody, &result); err != nil {
		return nil, fmt.Errorf("mockserver: unmarshal %s expectations: %w", op, err)
	}
	return result, nil
}
