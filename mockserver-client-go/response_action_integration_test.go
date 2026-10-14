package mockserver_test

import (
	"errors"
	"io"
	"net/http"
	"os"
	"testing"
	"time"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

// These tests close the response-ACTION wire gap for the Go client: the other
// integration tests only ever register a RESPOND action (a canned HttpResponse)
// or assert the expectation JSON was built. Nothing drove a FORWARD or an ERROR
// action to completion and proved the SERVER actually performed it over the
// wire. Building the expectation JSON correctly is necessary but not sufficient
// -- the server has to execute the action, and only a real request can show it.

// mockServerInternalPort is the port MockServer listens on INSIDE its own
// process/container (the MockServer default, and what the CI harness
// with-mockserver.sh and the local Docker image both use). The FORWARD test
// makes the server forward to itself, so it must target the server's internal
// port -- not the external port from MOCKSERVER_URL, which may be remapped
// (e.g. `docker run -p 2080:1080`).
const mockServerInternalPort = 1080

const forwardUpstreamBody = "GOWIRE-UPSTREAM-OK"

// TestIntegration_ForwardActionReachesUpstream proves the server ACTUALLY
// performs a FORWARD action registered via the Go client, not merely that the
// expectation was accepted.
//
// The upstream is the MockServer instance itself: a high-priority, match-once
// FORWARD expectation on /gowire-fwd forwards the request back to the server's
// own loopback address. Because the forward expectation is consumed after its
// single match, the looped-back request falls through to a second, lower-
// priority RESPOND expectation on the same path, which returns a distinctive
// body. This needs no externally-reachable upstream, so it works identically in
// the CI sibling-container harness and against a locally port-mapped server.
//
// DISCRIMINATING assertion: the caller only ever sees forwardUpstreamBody if
// the request was genuinely forwarded and looped back. If the FORWARD action is
// not performed (or the client failed to send it correctly) the caller instead
// gets a 404/502 with a different body -- see the positive-control note in the
// task report. The RESPOND expectation alone cannot serve the first request
// because the FORWARD expectation outranks it until it is consumed.
func TestIntegration_ForwardActionReachesUpstream(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	// Higher-priority, match-once FORWARD to the server's own loopback.
	if _, err := client.When(
		mockserver.Request().Method("GET").Path("/gowire-fwd"),
		mockserver.WithTimes(mockserver.Once()),
	).WithPriority(10).Forward(
		mockserver.Forward().Host("localhost").Port(mockServerInternalPort).Scheme("HTTP"),
	); err != nil {
		t.Fatalf("registering forward expectation: %v", err)
	}

	// Lower-priority upstream response the looped-back request lands on.
	if _, err := client.When(
		mockserver.Request().Method("GET").Path("/gowire-fwd"),
	).Respond(
		mockserver.Response().StatusCode(200).Body(forwardUpstreamBody),
	); err != nil {
		t.Fatalf("registering upstream expectation: %v", err)
	}

	status, body := getWithBody(t, "/gowire-fwd")
	if status != 200 {
		t.Fatalf("forwarded request returned status %d, want 200 -- the FORWARD action did not reach the upstream", status)
	}
	if body != forwardUpstreamBody {
		t.Errorf("forwarded request body = %q, want %q -- the response did not come from the forwarded-to upstream, "+
			"so the server did not actually perform the FORWARD action", body, forwardUpstreamBody)
	}
}

// TestIntegration_ErrorActionDropsConnection proves the server ACTUALLY
// performs an ERROR action (dropConnection) registered via the Go client: the
// matched request must fail at the transport level (the connection is closed
// with no HTTP response), NOT return an HTTP status.
//
// DISCRIMINATING assertion: a control request to a normal RESPOND endpoint on
// the same server succeeds cleanly (err == nil, 200). Only the error endpoint
// yields a transport error. If the ERROR action were not performed the matched
// request would instead get an ordinary HTTP response (a 404 when unmatched, or
// a 200 if some response served it) with err == nil -- which reddens this test.
func TestIntegration_ErrorActionDropsConnection(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	if _, err := client.When(
		mockserver.Request().Method("GET").Path("/gowire-err"),
	).RespondWithError(
		mockserver.Error().DropConnection(true),
	); err != nil {
		t.Fatalf("registering error expectation: %v", err)
	}

	// Control endpoint: a plain RESPOND on the same server, proving the server
	// is reachable and healthy -- so a transport error on /gowire-err can only
	// come from the ERROR action, not from an unreachable server.
	if _, err := client.When(
		mockserver.Request().Method("GET").Path("/gowire-ok"),
	).Respond(
		mockserver.Response().StatusCode(200).Body("ok"),
	); err != nil {
		t.Fatalf("registering control expectation: %v", err)
	}

	if status, _, err := getRaw("/gowire-ok"); err != nil {
		t.Fatalf("control request to /gowire-ok failed at the transport level (%v) -- "+
			"the server is not healthy, so the error-endpoint assertion would be meaningless", err)
	} else if status != 200 {
		t.Fatalf("control request to /gowire-ok returned %d, want 200", status)
	}

	// The error endpoint must NOT yield an HTTP response.
	if status, _, err := getRaw("/gowire-err"); err == nil {
		t.Errorf("request to /gowire-err returned HTTP %d with no transport error -- "+
			"the server did not perform the dropConnection ERROR action", status)
	}
}

// getRaw issues a GET to path (relative to MOCKSERVER_URL) with a client that
// does NOT reuse pooled connections, so a dropped connection surfaces as an
// error on this exact request rather than a stale-pool retry. It returns the
// status code (0 on transport error), the body, and any transport error.
func getRaw(path string) (int, string, error) {
	client := &http.Client{
		Timeout:   10 * time.Second,
		Transport: &http.Transport{DisableKeepAlives: true},
	}
	resp, err := client.Get(os.Getenv("MOCKSERVER_URL") + path)
	if err != nil {
		return 0, "", err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return resp.StatusCode, "", err
	}
	return resp.StatusCode, string(body), nil
}

// getWithBody is getRaw with a fatal on transport error, for the paths that
// must return a normal HTTP response.
func getWithBody(t *testing.T, path string) (int, string) {
	t.Helper()
	status, body, err := getRaw(path)
	if err != nil {
		if errors.Is(err, io.EOF) {
			t.Fatalf("GET %s: connection dropped unexpectedly: %v", path, err)
		}
		t.Fatalf("GET %s failed: %v", path, err)
	}
	return status, body
}
