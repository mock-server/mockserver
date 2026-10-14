package mockserver_test

import (
	"bytes"
	"io"
	"net/http"
	"os"
	"testing"
	"time"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

// These tests close audit gap #47 for the Go client's ADVANCED response actions:
// the other integration tests only ever create+retrieve a template/binary
// expectation (proving schema acceptance) or drive a plain RESPOND/FORWARD/ERROR
// action. Nothing drove a request against an httpResponseTemplate or a BINARY
// response body and asserted the ACTUAL bytes the server rendered/served. A
// correctly-built expectation JSON is necessary but not sufficient — only a real
// request over the wire proves the server executed the advanced action.

// TestIntegration_ResponseTemplateRenderedOverWire proves the server ACTUALLY
// executes an httpResponseTemplate registered via the Go client: a VELOCITY
// template that echoes $!{request.path} into the body can only produce the
// asserted string if the template engine ran AND had access to the matched
// request. A client that mis-encoded the template action, or a server that
// ignored it, would serve a 404 or an unrendered body — reddening this test.
func TestIntegration_ResponseTemplateRenderedOverWire(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	if _, err := client.When(
		mockserver.Request().Method("GET").Path("/gowire-tmpl"),
	).RespondTemplate(
		mockserver.ResponseTemplate("VELOCITY").
			Template(`{"statusCode": 200, "body": "TEMPLATED path=$!{request.path}"}`),
	); err != nil {
		t.Fatalf("registering response-template expectation: %v", err)
	}

	status, body := getWithBody(t, "/gowire-tmpl")
	if status != 200 {
		t.Fatalf("template response returned status %d, want 200 -- the template action was not executed", status)
	}
	if want := "TEMPLATED path=/gowire-tmpl"; body != want {
		t.Errorf("template response body = %q, want %q -- the server did not render the VELOCITY "+
			"template over the live request", body, want)
	}
}

// TestIntegration_BinaryResponseBodyServedOverWire proves the server ACTUALLY
// decodes a BINARY response body registered via the Go client and serves the
// exact octets — including a NUL (0x00) and 0xFF, which are not valid UTF-8, so
// a path that mangled the bytes as text would fail the byte-exact comparison.
// The registered contentType must also appear on the wire.
func TestIntegration_BinaryResponseBodyServedOverWire(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	payload := []byte{0x4D, 0x53, 0x00, 0x01, 0xFF, 0x7A}
	// base64 of payload; computed inline to keep the test self-describing.
	const payloadBase64 = "TVMAAf96"

	// The ResponseBuilder has no binary-body helper, so build the expectation
	// directly with a BINARY body object and upsert it — the same wire form the
	// server serves as raw response bytes with the given content type.
	resp := mockserver.Response().StatusCode(200).Build()
	resp.Body = map[string]interface{}{
		"type":        "BINARY",
		"base64Bytes": payloadBase64,
		"contentType": "application/octet-stream",
	}
	req := mockserver.Request().Method("GET").Path("/gowire-bin").Build()
	if _, err := client.Upsert(mockserver.Expectation{
		HttpRequest:  &req,
		HttpResponse: &resp,
	}); err != nil {
		t.Fatalf("registering binary-body expectation: %v", err)
	}

	status, headers, body := getRawBytes(t, "/gowire-bin")
	if status != 200 {
		t.Fatalf("binary response returned status %d, want 200", status)
	}
	if !bytes.Equal(body, payload) {
		t.Errorf("binary response body = %v, want %v -- the server did not serve the exact BINARY octets", body, payload)
	}
	if ct := headers.Get("Content-Type"); ct != "application/octet-stream" {
		t.Errorf("binary response Content-Type = %q, want application/octet-stream", ct)
	}
}

// getRawBytes issues a GET to path (relative to MOCKSERVER_URL) and returns the
// status code, response headers, and the RAW body bytes (not decoded to a
// string), so a BINARY body can be asserted byte-for-byte. Keep-alives are
// disabled so each call is an independent connection.
func getRawBytes(t *testing.T, path string) (int, http.Header, []byte) {
	t.Helper()
	client := &http.Client{
		Timeout:   10 * time.Second,
		Transport: &http.Transport{DisableKeepAlives: true},
	}
	resp, err := client.Get(os.Getenv("MOCKSERVER_URL") + path)
	if err != nil {
		t.Fatalf("GET %s failed: %v", path, err)
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatalf("reading %s body: %v", path, err)
	}
	return resp.StatusCode, resp.Header, body
}
