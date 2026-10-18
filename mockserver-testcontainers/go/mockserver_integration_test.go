package mockserver

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"

	mockserverclient "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
	"github.com/testcontainers/testcontainers-go"
)

// skipIfNoDocker probes the daemon the way Testcontainers itself connects (the
// Docker socket / DOCKER_HOST), NOT via a `docker` CLI: the golang CI image has
// no docker binary, so a CLI probe skipped every test while reading as green.
func skipIfNoDocker(t *testing.T) {
	t.Helper()
	testcontainers.SkipIfProviderIsNotHealthy(t)
}

func TestIntegration_RunAndURL(t *testing.T) {
	skipIfNoDocker(t)

	ctx := context.Background()
	ctr, err := Run(ctx, DefaultImage)
	if err != nil {
		t.Fatalf("Run() failed: %v", err)
	}
	defer func() {
		if err := ctr.Terminate(ctx); err != nil {
			t.Logf("failed to terminate container: %v", err)
		}
	}()

	// Verify URL returns a valid endpoint
	url, err := ctr.URL(ctx)
	if err != nil {
		t.Fatalf("URL() failed: %v", err)
	}
	if !strings.HasPrefix(url, "http://") {
		t.Errorf("URL() = %q, want http:// prefix", url)
	}

	// Verify ServerPort returns a positive port
	port, err := ctr.ServerPort(ctx)
	if err != nil {
		t.Fatalf("ServerPort() failed: %v", err)
	}
	if port <= 0 || port > 65535 {
		t.Errorf("ServerPort() = %d, want port in range 1-65535", port)
	}

	// Verify MockServer is actually responding (PUT /mockserver/status)
	client := &http.Client{Timeout: 5 * time.Second}
	req, err := http.NewRequestWithContext(ctx, http.MethodPut, url+"/mockserver/status", nil)
	if err != nil {
		t.Fatalf("failed to create request: %v", err)
	}
	resp, err := client.Do(req)
	if err != nil {
		t.Fatalf("status request failed: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("PUT /mockserver/status returned %d, want 200", resp.StatusCode)
	}

	// Evidence marker: go-testcontainers-test.sh greps for this line to fail
	// closed, because a skipped test (no usable Docker) otherwise reads as green.
	fmt.Printf("GO_TESTCONTAINERS_STATUS_OK url=%s status=%d\n", url, resp.StatusCode)
}

func TestIntegration_CreateExpectationAndVerify(t *testing.T) {
	skipIfNoDocker(t)

	ctx := context.Background()
	ctr, err := Run(ctx, DefaultImage)
	if err != nil {
		t.Fatalf("Run() failed: %v", err)
	}
	defer func() {
		if err := ctr.Terminate(ctx); err != nil {
			t.Logf("failed to terminate container: %v", err)
		}
	}()

	url, err := ctr.URL(ctx)
	if err != nil {
		t.Fatalf("URL() failed: %v", err)
	}

	client := &http.Client{Timeout: 5 * time.Second}

	// Create an expectation
	expectationJSON := `{
		"httpRequest": {"method": "GET", "path": "/hello"},
		"httpResponse": {"statusCode": 200, "body": "world"}
	}`
	req, err := http.NewRequestWithContext(ctx, http.MethodPut, url+"/mockserver/expectation",
		strings.NewReader(expectationJSON))
	if err != nil {
		t.Fatalf("failed to create expectation request: %v", err)
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := client.Do(req)
	if err != nil {
		t.Fatalf("create expectation request failed: %v", err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("PUT /mockserver/expectation returned %d, want 201", resp.StatusCode)
	}

	// Call the mocked endpoint
	getReq, err := http.NewRequestWithContext(ctx, http.MethodGet, url+"/hello", nil)
	if err != nil {
		t.Fatalf("failed to create GET request: %v", err)
	}
	getResp, err := client.Do(getReq)
	if err != nil {
		t.Fatalf("GET /hello failed: %v", err)
	}
	defer getResp.Body.Close()
	if getResp.StatusCode != http.StatusOK {
		t.Errorf("GET /hello returned %d, want 200", getResp.StatusCode)
	}

	// Verify the request was received
	verifyJSON := fmt.Sprintf(`{"httpRequest": {"method": "GET", "path": "/hello"}, "times": {"atLeast": 1}}`)
	verifyReq, err := http.NewRequestWithContext(ctx, http.MethodPut, url+"/mockserver/verify",
		strings.NewReader(verifyJSON))
	if err != nil {
		t.Fatalf("failed to create verify request: %v", err)
	}
	verifyReq.Header.Set("Content-Type", "application/json")
	verifyResp, err := client.Do(verifyReq)
	if err != nil {
		t.Fatalf("verify request failed: %v", err)
	}
	verifyResp.Body.Close()
	if verifyResp.StatusCode != http.StatusAccepted {
		t.Errorf("PUT /mockserver/verify returned %d, want 202", verifyResp.StatusCode)
	}
}

// TestIntegration_Client exercises the bundled Client() helper end-to-end: it
// creates an expectation via the mockserver-client-go client, calls the mocked
// endpoint over HTTP, and verifies the request was received — all through the
// container's mapped host/port.
func TestIntegration_Client(t *testing.T) {
	skipIfNoDocker(t)

	ctx := context.Background()
	ctr, err := Run(ctx, DefaultImage)
	if err != nil {
		t.Fatalf("Run() failed: %v", err)
	}
	defer func() {
		if err := ctr.Terminate(ctx); err != nil {
			t.Logf("failed to terminate container: %v", err)
		}
	}()

	// Obtain a configured client pointing at the container.
	client, err := ctr.Client(ctx)
	if err != nil {
		t.Fatalf("Client() failed: %v", err)
	}

	// Create an expectation using the client's fluent API.
	if _, err := client.When(
		mockserverclient.Request().Method("GET").Path("/tc-client"),
	).Respond(
		mockserverclient.Response().StatusCode(200).Body("client-ok"),
	); err != nil {
		t.Fatalf("When/Respond failed: %v", err)
	}

	// Call the mocked endpoint over HTTP.
	url, err := ctr.URL(ctx)
	if err != nil {
		t.Fatalf("URL() failed: %v", err)
	}
	httpClient := &http.Client{Timeout: 5 * time.Second}
	getReq, err := http.NewRequestWithContext(ctx, http.MethodGet, url+"/tc-client", nil)
	if err != nil {
		t.Fatalf("failed to create GET request: %v", err)
	}
	getResp, err := httpClient.Do(getReq)
	if err != nil {
		t.Fatalf("GET /tc-client failed: %v", err)
	}
	defer getResp.Body.Close()
	if getResp.StatusCode != http.StatusOK {
		t.Errorf("GET /tc-client returned %d, want 200", getResp.StatusCode)
	}
	body, _ := io.ReadAll(getResp.Body)
	if string(body) != "client-ok" {
		t.Errorf("GET /tc-client body = %q, want %q", string(body), "client-ok")
	}

	// Verify the request was received at least once, again via the client.
	if err := client.Verify(
		mockserverclient.Request().Path("/tc-client"),
		mockserverclient.AtLeast(1),
	); err != nil {
		t.Errorf("Verify failed: %v", err)
	}
}
